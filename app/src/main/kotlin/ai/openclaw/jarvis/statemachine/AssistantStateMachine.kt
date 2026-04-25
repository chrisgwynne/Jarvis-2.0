package ai.openclaw.jarvis.statemachine

import ai.openclaw.jarvis.debug.AssistantEvent
import ai.openclaw.jarvis.debug.AssistantEventLog
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AssistantStateMachine"

/**
 * Enforces explicit state transitions for the voice assistant session lifecycle.
 *
 * Rules:
 * - Every transition is validated against [VALID_TRANSITIONS].
 * - Cancel/interrupt can transition from any active state to IDLE_LISTENING.
 * - Invalid transitions are rejected and logged — they never fail silently.
 * - All transitions are written to [AssistantEventLog] for debug inspection.
 */
@Singleton
class AssistantStateMachine @Inject constructor(
    private val eventLog: AssistantEventLog,
) {
    private val _state = MutableStateFlow(AssistantState.DISABLED)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    val currentState: AssistantState get() = _state.value

    // ─── Transitions ──────────────────────────────────────────────────────────

    /**
     * Attempt to transition to [to].
     * Returns true on success; logs and returns false on invalid transition.
     */
    fun transition(to: AssistantState, reason: String = ""): Boolean {
        val from = _state.value
        if (!isValid(from, to)) {
            val msg = "INVALID: $from → $to${if (reason.isNotBlank()) " ($reason)" else ""}"
            Log.w(TAG, msg)
            eventLog.log(AssistantEvent(stateFrom = from, stateTo = to, error = msg))
            return false
        }
        _state.value = to
        val msg = "$from → $to${if (reason.isNotBlank()) " [$reason]" else ""}"
        Log.d(TAG, msg)
        eventLog.log(AssistantEvent(stateFrom = from, stateTo = to, action = reason.ifBlank { null }))
        return true
    }

    /**
     * Interrupt any active session and return to IDLE_LISTENING.
     * No-op if already IDLE or DISABLED.
     */
    fun interrupt(reason: String = "CANCEL"): Boolean {
        val from = _state.value
        if (from !in INTERRUPTIBLE_STATES) return false
        _state.value = AssistantState.IDLE_LISTENING
        Log.d(TAG, "INTERRUPT: $from → IDLE_LISTENING [$reason]")
        eventLog.log(AssistantEvent(
            stateFrom = from,
            stateTo   = AssistantState.IDLE_LISTENING,
            action    = reason,
        ))
        return true
    }

    fun isActive(): Boolean = _state.value in INTERRUPTIBLE_STATES

    // ─── Validation ───────────────────────────────────────────────────────────

    private fun isValid(from: AssistantState, to: AssistantState): Boolean {
        // Cancel-interrupt from any active state
        if (to == AssistantState.IDLE_LISTENING && from in INTERRUPTIBLE_STATES) return true
        return (from to to) in VALID_TRANSITIONS
    }
}
