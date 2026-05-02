package ai.openclaw.jarvis.proactive.model

/**
 * A meaningful change derived from comparing two consecutive
 * [ContextSnapshot]s. Signals are the only thing the rules engine
 * consumes — it never reads raw context.
 *
 * Every signal carries a stable [type] so cooldowns and the per-signal
 * settings toggle can key off it.
 */
data class Signal(
    val type: SignalType,
    val timestampMillis: Long = System.currentTimeMillis(),
    val confidence: Float = 1.0f,
    val payload: Map<String, String> = emptyMap(),
)

enum class SignalType {
    LEFT_HOME,
    ARRIVED_HOME,
    ARRIVED_WORK,
    DRIVING_STARTED,
    DRIVING_STOPPED,
    HEADPHONES_CONNECTED,
    HEADPHONES_DISCONNECTED,
    REPEATED_COMMAND_PATTERN,
    SCREENSHOT_TAKEN,
    APP_OPENED_FREQUENTLY,
    CALENDAR_EVENT_APPROACHING,
    IDLE_PERIOD,
    MORNING,
    EVENING,
    LOW_BATTERY,
}
