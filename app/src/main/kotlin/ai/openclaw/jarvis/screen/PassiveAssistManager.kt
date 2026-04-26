package ai.openclaw.jarvis.screen

import ai.openclaw.jarvis.proactive.SuggestionManager
import ai.openclaw.jarvis.proactive.model.ContextSnapshot
import ai.openclaw.jarvis.proactive.model.ProposedAction
import ai.openclaw.jarvis.proactive.model.Signal
import ai.openclaw.jarvis.proactive.model.SignalType
import ai.openclaw.jarvis.proactive.model.Suggestion
import ai.openclaw.jarvis.proactive.model.SuggestionFormat
import ai.openclaw.jarvis.screen.model.AppCategory
import ai.openclaw.jarvis.screen.model.InterpretedContext
import ai.openclaw.jarvis.screen.model.PageType
import ai.openclaw.jarvis.screen.model.ScreenContextEvent
import ai.openclaw.jarvis.screen.model.ScreenshotCaptured
import ai.openclaw.jarvis.screen.store.ScreenAwarenessSettingsSource
import ai.openclaw.jarvis.trust.TrustLevel
import ai.openclaw.jarvis.trust.TrustManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Subscribes to the [ScreenContextBus] and turns screen / screenshot
 * events into proactive [Suggestion]s, then hands them to the existing
 * [SuggestionManager] so cooldowns / quiet-hours / format-safety all
 * apply uniformly.
 *
 * Per-app rules:
 *   - Etsy → "Check performance?"
 *   - Gmail → "Summarise inbox?"
 *   - Spotify → no suggestion (intentional — listening is not a task)
 *
 * Screenshot rule:
 *   - Settings.screenshotAutoAnalyse = true → fire SCREENSHOT_TAKEN
 *     into the proactive pipeline; the suggestion's proposed action is
 *     ANALYSE_LAST_SCREENSHOT, picked up by whoever wires the action.
 *
 * Safety:
 *   - Unknown speaker → no screen-based suggestions
 *   - SENSITIVE category → no suggestion
 *   - Sensitive blacklist / non-whitelisted → already filtered upstream
 *   - All suggestions are SILENT_CHIP unless voicePromptOnAnalysis is
 *     explicitly on, which only flips screenshot suggestions to VOICE.
 */
@Singleton
class PassiveAssistManager @Inject constructor(
    private val bus: ScreenContextBus,
    private val interpreter: ContextInterpreter,
    private val suggestionManager: SuggestionManager,
    private val settingsSource: ScreenAwarenessSettingsSource,
    private val trustManager: TrustManager,
    private val openClawAnalyser: ScreenshotAutoAnalyser,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        bus.events.onEach(::handleScreen).launchIn(scope)
        bus.screenshots.onEach(::handleScreenshot).launchIn(scope)
    }

    /** Test seam — synchronously process one event without the flow loop. */
    fun ingestForTest(event: ScreenContextEvent) = handleScreen(event)
    fun ingestForTest(shot: ScreenshotCaptured) = handleScreenshot(shot)

    private fun handleScreen(event: ScreenContextEvent) {
        val s = settingsSource.current()
        if (!s.enabled) return
        if (event.category in s.excludeCategories) return
        if (!ownerVerified()) return

        val ctx = interpreter.interpret(event)
        val suggestion = ruleFor(ctx) ?: return
        // Rather than directly emitting to the chip, fund it through the
        // proactive pipeline so cooldowns + safety + per-signal toggles apply.
        // We synthesise a "fake" signal whose type is the closest match.
        val signalType = signalTypeFor(ctx)
        val payloadCtx = bridgeContext(ctx)
        suggestionManager.ingestExternalSuggestion(
            signal = Signal(signalType, payload = mapOf("source" to "screen")),
            ctx = payloadCtx,
            suggestion = suggestion,
        )
    }

    private fun handleScreenshot(shot: ScreenshotCaptured) {
        val s = settingsSource.current()
        if (!s.enabled) return
        if (!ownerVerified()) return
        if (!s.screenshotAutoAnalyse) return

        // Ask OpenClaw to analyse, fire-and-forget. The proactive chip
        // simultaneously surfaces "Want me to analyse this?" as a fallback
        // for when OpenClaw is offline (the analyser silently no-ops then).
        openClawAnalyser.analyse(shot)

        val suggestion = Suggestion(
            id = "screen.screenshot_analysis",
            signalType = SignalType.SCREENSHOT_TAKEN,
            format = if (s.voicePromptOnAnalysis) SuggestionFormat.VOICE
                     else SuggestionFormat.SILENT_CHIP,
            voicePrompt = "Want me to analyse the last screenshot?",
            title = "Analyse last screenshot?",
            body = "Send to OpenClaw for a quick read-through.",
            proposedAction = ProposedAction(kind = ProposedAction.Kind.ANALYSE_LAST_SCREENSHOT,
                payload = mapOf("uri" to shot.uri)),
        )
        suggestionManager.ingestExternalSuggestion(
            signal = Signal(SignalType.SCREENSHOT_TAKEN),
            ctx = bridgeContextScreenshot(),
            suggestion = suggestion,
        )
    }

    // ─── Rule book ───────────────────────────────────────────────────────────

    private fun ruleFor(ctx: InterpretedContext): Suggestion? = when {
        ctx.packageName == "com.etsy.android" || ctx.url?.contains("etsy.com", true) == true ->
            Suggestion(
                id = "screen.etsy.performance",
                signalType = SignalType.APP_OPENED_FREQUENTLY,
                format = SuggestionFormat.SILENT_CHIP,
                voicePrompt = "Want me to check Etsy performance?",
                title = "Check Etsy performance?",
                body = "Pull today's listing stats and orders.",
                proposedAction = ProposedAction(kind = ProposedAction.Kind.SHOW_PLAN,
                    payload = mapOf("topic" to "etsy.performance")),
            )
        ctx.packageName == "com.google.android.gm" ->
            Suggestion(
                id = "screen.gmail.summary",
                signalType = SignalType.APP_OPENED_FREQUENTLY,
                format = SuggestionFormat.SILENT_CHIP,
                voicePrompt = "Want me to summarise your inbox?",
                title = "Summarise inbox?",
                body = "Get a quick read of unread email.",
                proposedAction = ProposedAction(kind = ProposedAction.Kind.SHOW_PLAN,
                    payload = mapOf("topic" to "gmail.summary")),
            )
        ctx.pageType == PageType.READING_ARTICLE && ctx.category == AppCategory.BROWSER ->
            Suggestion(
                id = "screen.browser.summarise",
                signalType = SignalType.APP_OPENED_FREQUENTLY,
                format = SuggestionFormat.SILENT_CHIP,
                voicePrompt = "Want me to summarise this page?",
                title = "Summarise this page?",
                body = "Send the page text to OpenClaw for a quick read.",
                proposedAction = ProposedAction(kind = ProposedAction.Kind.ANALYSE_LAST_SCREENSHOT,
                    payload = mapOf("source" to "screen-text")),
            )
        else -> null
    }

    private fun signalTypeFor(ctx: InterpretedContext): SignalType = when (ctx.category) {
        AppCategory.SHOPPING, AppCategory.EMAIL,
        AppCategory.BROWSER, AppCategory.PRODUCTIVITY -> SignalType.APP_OPENED_FREQUENTLY
        else -> SignalType.IDLE_PERIOD
    }

    /**
     * Bridge an InterpretedContext into the lightweight ContextSnapshot
     * the proactive pipeline expects. Only the fields the pipeline reads
     * (speakerTrustLevel, hourOfDay, foregroundApp) need to be valid.
     */
    private fun bridgeContext(ctx: InterpretedContext): ContextSnapshot = ContextSnapshot(
        timestampMillis = ctx.timestampMillis,
        hourOfDay = currentHour(),
        foregroundApp = ctx.packageName,
        speakerTrustLevel = currentTrustName(),
    )

    private fun bridgeContextScreenshot(): ContextSnapshot = ContextSnapshot(
        hourOfDay = currentHour(),
        speakerTrustLevel = currentTrustName(),
    )

    private fun currentHour(): Int = java.util.Calendar.getInstance()
        .get(java.util.Calendar.HOUR_OF_DAY)

    private fun currentTrustName(): String =
        runCatching { trustManager.currentTrustLevel().name }.getOrDefault("UNKNOWN")

    private fun ownerVerified(): Boolean =
        runCatching { trustManager.currentTrustLevel() }.getOrDefault(TrustLevel.UNKNOWN) != TrustLevel.UNKNOWN
}
