package ai.openclaw.jarvis.statemachine

/** Full state machine states for the assistant session lifecycle. */
enum class AssistantState {
    /** Mic active, waiting for wake word. */
    IDLE_LISTENING,

    /** Wake phrase detected; brief pause before starting command capture. */
    WAKE_DETECTED,

    /** Actively recording the user's command (STT running). */
    CAPTURING_COMMAND,

    /** STT finalising text from captured audio. */
    TRANSCRIBING,

    /** Running voice embedding against enrolled profiles. */
    IDENTIFYING_SPEAKER,

    /** Intent parsed; deciding local vs OpenClaw. */
    ROUTING,

    /** Executing a local Android action. */
    EXECUTING_ANDROID,

    /** Request sent to OpenClaw; awaiting reply. */
    WAITING_OPENCLAW,

    /** Sensitive action staged; awaiting user yes/no. */
    AWAITING_CONFIRMATION,

    /** TTS playing the assistant's spoken reply. */
    SPEAKING,

    /** Transitioning back to idle after a completed session. */
    RETURNING_TO_LISTENING,

    /** Handling a failure; may speak an error message. */
    ERROR_RECOVERY,

    /** Always-listening disabled; no background mic use. */
    DISABLED,
}

/** States from which a cancel/interrupt command returns to IDLE. */
val INTERRUPTIBLE_STATES: Set<AssistantState> = setOf(
    AssistantState.WAKE_DETECTED,
    AssistantState.CAPTURING_COMMAND,
    AssistantState.TRANSCRIBING,
    AssistantState.IDENTIFYING_SPEAKER,
    AssistantState.ROUTING,
    AssistantState.EXECUTING_ANDROID,
    AssistantState.WAITING_OPENCLAW,
    AssistantState.AWAITING_CONFIRMATION,
    AssistantState.SPEAKING,
    AssistantState.RETURNING_TO_LISTENING,
    AssistantState.ERROR_RECOVERY,
)

/** Explicit valid transitions. Cancel-interrupt (any active → IDLE) is handled separately. */
val VALID_TRANSITIONS: Set<Pair<AssistantState, AssistantState>> = setOf(
    AssistantState.IDLE_LISTENING    to AssistantState.WAKE_DETECTED,
    AssistantState.IDLE_LISTENING    to AssistantState.CAPTURING_COMMAND,
    AssistantState.IDLE_LISTENING    to AssistantState.DISABLED,
    AssistantState.WAKE_DETECTED     to AssistantState.CAPTURING_COMMAND,
    AssistantState.WAKE_DETECTED     to AssistantState.IDLE_LISTENING,
    AssistantState.CAPTURING_COMMAND to AssistantState.TRANSCRIBING,
    AssistantState.CAPTURING_COMMAND to AssistantState.IDLE_LISTENING,
    AssistantState.TRANSCRIBING      to AssistantState.IDENTIFYING_SPEAKER,
    AssistantState.TRANSCRIBING      to AssistantState.ROUTING,
    AssistantState.TRANSCRIBING      to AssistantState.ERROR_RECOVERY,
    AssistantState.IDENTIFYING_SPEAKER to AssistantState.ROUTING,
    AssistantState.ROUTING           to AssistantState.EXECUTING_ANDROID,
    AssistantState.ROUTING           to AssistantState.WAITING_OPENCLAW,
    AssistantState.ROUTING           to AssistantState.AWAITING_CONFIRMATION,
    AssistantState.ROUTING           to AssistantState.IDLE_LISTENING,
    AssistantState.EXECUTING_ANDROID to AssistantState.SPEAKING,
    AssistantState.EXECUTING_ANDROID to AssistantState.AWAITING_CONFIRMATION,
    AssistantState.EXECUTING_ANDROID to AssistantState.ERROR_RECOVERY,
    AssistantState.WAITING_OPENCLAW  to AssistantState.SPEAKING,
    AssistantState.WAITING_OPENCLAW  to AssistantState.ERROR_RECOVERY,
    AssistantState.AWAITING_CONFIRMATION to AssistantState.EXECUTING_ANDROID,
    AssistantState.AWAITING_CONFIRMATION to AssistantState.IDLE_LISTENING,
    AssistantState.SPEAKING          to AssistantState.RETURNING_TO_LISTENING,
    AssistantState.SPEAKING          to AssistantState.IDLE_LISTENING,
    AssistantState.RETURNING_TO_LISTENING to AssistantState.IDLE_LISTENING,
    AssistantState.RETURNING_TO_LISTENING to AssistantState.DISABLED,
    AssistantState.ERROR_RECOVERY    to AssistantState.SPEAKING,
    AssistantState.ERROR_RECOVERY    to AssistantState.IDLE_LISTENING,
    AssistantState.DISABLED          to AssistantState.IDLE_LISTENING,
    AssistantState.DISABLED          to AssistantState.CAPTURING_COMMAND,
)

/** Map the rich state to the simplified 4-state VoiceState for UI consumption. */
fun AssistantState.toVoiceStateLabel(): String = when (this) {
    AssistantState.IDLE_LISTENING,
    AssistantState.DISABLED,
    AssistantState.AWAITING_CONFIRMATION,
    AssistantState.RETURNING_TO_LISTENING -> "idle"
    AssistantState.WAKE_DETECTED,
    AssistantState.CAPTURING_COMMAND,
    AssistantState.TRANSCRIBING,
    AssistantState.IDENTIFYING_SPEAKER    -> "listening"
    AssistantState.ROUTING,
    AssistantState.EXECUTING_ANDROID,
    AssistantState.WAITING_OPENCLAW,
    AssistantState.ERROR_RECOVERY         -> "processing"
    AssistantState.SPEAKING               -> "speaking"
}
