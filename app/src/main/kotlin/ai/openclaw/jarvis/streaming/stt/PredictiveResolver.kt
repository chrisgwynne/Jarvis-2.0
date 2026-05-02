package ai.openclaw.jarvis.streaming.stt

import ai.openclaw.jarvis.capabilities.CapabilityRegistry
import ai.openclaw.jarvis.capabilities.base.CapabilityResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pre-resolves whatever the [PartialIntentDetector] spotted so the moment
 * the STT final lands the executor doesn't have to wait on a contacts
 * lookup or an app-metadata query.
 *
 * Critically: this class **never executes** anything. It only warms
 * caches. The spec is explicit on that line — "do NOT execute until
 * final transcript, but prepare".
 *
 * Resolved results live on a tiny per-session cache and are invalidated
 * when [reset] is called (typically when STT goes IDLE).
 */
@Singleton
class PredictiveResolver @Inject constructor(
    private val caps: CapabilityRegistry,
) {
    data class Resolved(
        val contactPhone: String? = null,
        val appLaunchIntent: android.content.Intent? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var inFlight: Job? = null
    private var lastGuessKey: String? = null
    private var cached: Resolved = Resolved()
    private val lock = Mutex()

    /** Side-effect-free; safe to call repeatedly as the partial grows. */
    fun prepare(guess: PartialIntentDetector.Guess?) {
        guess ?: return
        val key = keyFor(guess) ?: return
        if (key == lastGuessKey) return // already preparing this same guess
        lastGuessKey = key
        inFlight?.cancel()
        inFlight = scope.launch {
            when (guess) {
                is PartialIntentDetector.Guess.SendMessage ->
                    guess.contactHint?.let { resolveContact(it) }
                is PartialIntentDetector.Guess.MakeCall ->
                    guess.contactHint?.let { resolveContact(it) }
                is PartialIntentDetector.Guess.OpenApp ->
                    guess.appHint?.let { resolveApp(it) }
                else -> Unit
            }
        }
    }

    fun snapshot(): Resolved = cached

    fun reset() {
        inFlight?.cancel()
        inFlight = null
        lastGuessKey = null
        cached = Resolved()
    }

    private suspend fun resolveContact(query: String) = lock.withLock {
        val r = caps.contacts.findContact(query)
        cached = cached.copy(
            contactPhone = (r as? CapabilityResult.Success)?.value?.firstOrNull()?.phone,
        )
    }

    private suspend fun resolveApp(query: String) = lock.withLock {
        val r = caps.apps.buildLaunchIntent(query)
        cached = cached.copy(
            appLaunchIntent = (r as? CapabilityResult.Success)?.value,
        )
    }

    private fun keyFor(g: PartialIntentDetector.Guess): String? = when (g) {
        is PartialIntentDetector.Guess.SendMessage -> "msg:${g.contactHint?.lowercase() ?: ""}"
        is PartialIntentDetector.Guess.MakeCall -> "call:${g.contactHint?.lowercase() ?: ""}"
        is PartialIntentDetector.Guess.OpenApp -> "app:${g.appHint?.lowercase() ?: ""}"
        else -> null
    }
}
