package ai.openclaw.jarvis.statemachine

import ai.openclaw.jarvis.router.ParsedIntent
import ai.openclaw.jarvis.trust.TrustLevel
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PendingActionManager"
private const val DEFAULT_TIMEOUT_MS = 30_000L

data class PendingAction(
    val intent: ParsedIntent,
    val summary: String,
    val boundSpeakerId: String,
    val boundTrustLevel: TrustLevel,
    val expiresAt: Long,
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
}

private val CONFIRM_PHRASES = setOf(
    "yes", "yeah", "yep", "yup", "sure", "ok", "okay",
    "do it", "confirm", "go ahead", "proceed", "affirmative",
)

private val DENY_PHRASES = setOf(
    "no", "nope", "cancel", "stop", "don't", "abort",
    "never mind", "nevermind", "negative",
)

@Singleton
class PendingActionManager @Inject constructor() {
    private val _pending = MutableStateFlow<PendingAction?>(null)
    val pending: StateFlow<PendingAction?> = _pending.asStateFlow()

    fun stage(
        intent: ParsedIntent,
        summary: String,
        speakerId: String,
        trustLevel: TrustLevel,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ) {
        _pending.value = PendingAction(
            intent          = intent,
            summary         = summary,
            boundSpeakerId  = speakerId,
            boundTrustLevel = trustLevel,
            expiresAt       = System.currentTimeMillis() + timeoutMs,
        )
        Log.d(TAG, "Staged: ${intent.type} for $speakerId")
    }

    /**
     * Attempt to resolve the pending action with a voice utterance.
     * Returns the resolved action if confirmed, null if denied or unrecognised.
     * Clears the pending action on confirm or deny.
     */
    fun tryResolve(utterance: String, speakerId: String): ResolveResult {
        val action = _pending.value ?: return ResolveResult.NoPending
        if (action.isExpired) {
            _pending.value = null
            Log.d(TAG, "Pending action expired")
            return ResolveResult.Expired
        }
        if (action.boundSpeakerId != "unknown" && speakerId != "unknown" &&
            action.boundSpeakerId != speakerId) {
            Log.d(TAG, "Wrong speaker: expected ${action.boundSpeakerId}, got $speakerId")
            return ResolveResult.WrongSpeaker
        }
        val normalised = utterance.trim().lowercase()
        return when {
            CONFIRM_PHRASES.any { normalised.contains(it) } -> {
                _pending.value = null
                Log.d(TAG, "Confirmed: ${action.intent.type}")
                ResolveResult.Confirmed(action)
            }
            DENY_PHRASES.any { normalised.contains(it) } -> {
                _pending.value = null
                Log.d(TAG, "Denied: ${action.intent.type}")
                ResolveResult.Denied
            }
            else -> ResolveResult.Unrecognised
        }
    }

    fun cancel() {
        _pending.value = null
    }

    fun hasPending(): Boolean = _pending.value?.let { !it.isExpired } == true
}

sealed class ResolveResult {
    data object NoPending      : ResolveResult()
    data object Expired        : ResolveResult()
    data object WrongSpeaker   : ResolveResult()
    data object Unrecognised   : ResolveResult()
    data object Denied         : ResolveResult()
    data class  Confirmed(val action: PendingAction) : ResolveResult()
}
