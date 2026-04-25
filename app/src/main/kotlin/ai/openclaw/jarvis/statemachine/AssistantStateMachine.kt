package ai.openclaw.jarvis.statemachine

import ai.openclaw.jarvis.debug.AssistantEvent
import ai.openclaw.jarvis.debug.AssistantEventLog
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Emitted for every successful transition. */
data class StateTransition(
    val from: AssistantState,
    val to: AssistantState,
    val reason: String?,
)

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

    private val _transitions = MutableSharedFlow<StateTransition>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Stream of every accepted transition (including interrupts). Subscribed
     * by the GitHub Issue Logging hook so ERROR_RECOVERY transitions can be
     * filed automatically without coupling the state machine to that module.
     */
    val transitions: SharedFlow<StateTransition> = _transitions.asSharedFlow()

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
        _transitions.tryEmit(StateTransition(from, to, reason.ifBlank { null }))
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
        _transitions.tryEmit(StateTransition(from, AssistantState.IDLE_LISTENING, reason))
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
