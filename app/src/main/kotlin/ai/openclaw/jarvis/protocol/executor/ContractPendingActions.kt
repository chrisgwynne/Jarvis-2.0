package ai.openclaw.jarvis.protocol.executor

import ai.openclaw.jarvis.protocol.model.OpenClawAction
import ai.openclaw.jarvis.protocol.validation.DecodedAction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds a single pending typed [OpenClawAction] awaiting yes/no
 * confirmation. Mirrors [ai.openclaw.jarvis.statemachine.PendingActionManager]
 * but for the typed contract path — we deliberately keep the two stores
 * parallel rather than retrofitting the legacy one, so the existing
 * ParsedIntent confirmation flow keeps working unchanged.
 *
 * Only one typed pending action at a time. A new staging clears the
 * previous (which is logged as cancelled at the call site).
 */
@Singleton
class ContractPendingActions @Inject constructor() {

    data class PendingTyped(
        val decoded: DecodedAction,
        val requestId: String,
        val sessionKey: String,
        val summary: String,
        val expiresAtMillis: Long = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS,
    ) {
        val isExpired: Boolean get() = System.currentTimeMillis() > expiresAtMillis
    }

    private val _pending = MutableStateFlow<PendingTyped?>(null)
    val pending: StateFlow<PendingTyped?> = _pending.asStateFlow()

    fun stage(
        decoded: DecodedAction,
        requestId: String,
        sessionKey: String,
        summary: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): PendingTyped {
        val staged = PendingTyped(
            decoded = decoded,
            requestId = requestId,
            sessionKey = sessionKey,
            summary = summary,
            expiresAtMillis = System.currentTimeMillis() + timeoutMs,
        )
        _pending.value = staged
        return staged
    }

    fun current(): PendingTyped? = _pending.value

    fun clear() { _pending.value = null }

    fun consume(): PendingTyped? {
        val v = _pending.value
        _pending.value = null
        return v
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 30_000L
    }
}

/**
 * Builds the canonical "Do you want me to …?" question per spec.
 */
fun confirmSummary(action: OpenClawAction): String {
    val type = action.type.name.lowercase().replace('_', ' ')
    val reasonSuffix = action.reason?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
    return "Do you want me to $type$reasonSuffix?"
}
