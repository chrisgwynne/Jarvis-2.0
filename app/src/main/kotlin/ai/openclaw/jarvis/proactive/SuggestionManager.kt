package ai.openclaw.jarvis.proactive

import ai.openclaw.jarvis.proactive.engine.ProactiveEngine
import ai.openclaw.jarvis.proactive.engine.SignalEngine
import ai.openclaw.jarvis.proactive.integration.ProactiveLogger
import ai.openclaw.jarvis.proactive.model.ContextSnapshot
import ai.openclaw.jarvis.proactive.model.ProposedAction
import ai.openclaw.jarvis.proactive.model.Signal
import ai.openclaw.jarvis.proactive.model.Suggestion
import ai.openclaw.jarvis.proactive.model.SuggestionFormat
import ai.openclaw.jarvis.proactive.store.CooldownTracker
import ai.openclaw.jarvis.proactive.store.ProactiveSettingsSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

/**
 * Glues the proactive subsystem together:
 *
 *   ContextCollector → SignalEngine → ProactiveEngine → (gates) → UI / TTS
 *
 * Gates applied here, in order, before a suggestion surfaces:
 *   1. master enable flag (settings)
 *   2. unknown speaker — no proactive suggestions, ever
 *   3. quiet hours
 *   4. per-signal toggle
 *   5. "don't suggest this again" memory
 *   6. CooldownTracker (per-signal + global hourly cap)
 *
 * Suggestions are emitted to:
 *   - [active]: a StateFlow used by the suggestion-chip composable
 *   - [voice]: a SharedFlow the voice-frontend pipes through TTS
 *
 * Acceptance / dismissal feed back through [accept] / [dismiss] which
 * also log the outcome to OpenClaw.
 */
@Singleton
class SuggestionManager @Inject constructor(
    private val signalEngine: SignalEngine,
    private val proactiveEngine: ProactiveEngine,
    private val cooldowns: CooldownTracker,
    private val settingsRepo: ProactiveSettingsSource,
    private val logger: ProactiveLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _active = MutableStateFlow<Suggestion?>(null)
    /** The current chip-style suggestion (or null when none). */
    val active: StateFlow<Suggestion?> = _active.asStateFlow()

    private val _voice = MutableSharedFlow<Suggestion>(extraBufferCapacity = 8)
    /** Suggestions that should be spoken via TTS. */
    val voice: SharedFlow<Suggestion> = _voice.asSharedFlow()

    private val _notification = MutableSharedFlow<Suggestion>(extraBufferCapacity = 8)
    /** Suggestions that should surface as Android notifications. */
    val notification: SharedFlow<Suggestion> = _notification.asSharedFlow()

    @Volatile private var lastSnapshot: ContextSnapshot? = null

    /** Idempotent. Starts the snapshot subscription against the supplied collector. */
    fun start(collector: ContextCollector) {
        if (running) return
        running = true
        collector.start()
        collector.snapshots.onEach(::ingest).launchIn(scope)
    }

    @Volatile private var running = false

    /** Test seam — process a snapshot synchronously without the flow loop. */
    fun ingest(snapshot: ContextSnapshot) {
        lastSnapshot = snapshot
        val signals = signalEngine.process(snapshot)
        signals.forEach { sig -> handleSignal(sig, snapshot) }
    }

    private fun handleSignal(signal: Signal, ctx: ContextSnapshot) {
        val s = settingsRepo.current()
        if (!s.enabled) return
        if (ctx.speakerTrustLevel == "UNKNOWN") {
            logger.logSuggestionSkipped(signal, "unknown_speaker")
            return
        }
        if (s.quietHours.isQuiet(ctx.hourOfDay)) {
            logger.logSuggestionSkipped(signal, "quiet_hours")
            return
        }
        if (s.perSignal[signal.type] == false) {
            logger.logSuggestionSkipped(signal, "per_signal_disabled")
            return
        }
        val suggestion = proactiveEngine.suggestionFor(signal, ctx) ?: return
        if (suggestion.id in s.suppressedSuggestionIds) {
            logger.logSuggestionSkipped(signal, "user_suppressed:${suggestion.id}")
            return
        }
        if (!cooldowns.allow(signal.type, s.aggressiveness)) {
            logger.logSuggestionSkipped(signal, "cooldown")
            return
        }

        // Trust + safety: messaging / calling / location-sharing / external
        // task creation always force VOICE format so the user has to confirm.
        val safe = enforceSafetyFormat(suggestion)

        cooldowns.recordShown(signal.type)
        deliver(safe, signal)
    }

    private fun deliver(suggestion: Suggestion, signal: Signal) {
        when (suggestion.format) {
            SuggestionFormat.SILENT_CHIP -> _active.value = suggestion
            SuggestionFormat.VOICE -> {
                _active.value = suggestion
                _voice.tryEmit(suggestion)
            }
            SuggestionFormat.NOTIFICATION -> _notification.tryEmit(suggestion)
        }
        logger.logSuggestionShown(suggestion, signal)
    }

    /** User accepted the active suggestion (yes / tapped). */
    fun accept(suggestion: Suggestion) {
        _active.value = null
        logger.logSuggestionAccepted(suggestion)
    }

    /** User dismissed (no / swiped). */
    fun dismiss(suggestion: Suggestion, dontSuggestAgain: Boolean = false) {
        _active.value = null
        cooldowns.recordDismissed(suggestion.id)
        if (dontSuggestAgain) settingsRepo.suppress(suggestion.id)
        logger.logSuggestionDismissed(suggestion, dontSuggestAgain)
    }

    /** Clear the chip without recording dismiss (e.g. expired by time). */
    fun clearActive() { _active.value = null }

    /**
     * Force VOICE format (so the user must verbally consent) for any
     * suggestion whose proposed action falls in the "always ask" set.
     */
    private fun enforceSafetyFormat(s: Suggestion): Suggestion {
        val k = s.proposedAction?.kind ?: return s
        val mustAsk = k == ProposedAction.Kind.SEND_MESSAGE ||
            k == ProposedAction.Kind.MAKE_CALL ||
            k == ProposedAction.Kind.SHARE_LOCATION ||
            k == ProposedAction.Kind.CREATE_TASK
        return if (mustAsk && s.format != SuggestionFormat.VOICE) s.copy(format = SuggestionFormat.VOICE) else s
    }
}
