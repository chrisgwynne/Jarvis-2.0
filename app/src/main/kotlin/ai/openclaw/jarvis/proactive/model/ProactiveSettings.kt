package ai.openclaw.jarvis.proactive.model

/**
 * User-controlled tuning for the proactive engine.
 *
 * `aggressiveness` controls how strict the cooldown / quality gates are:
 * LOW issues only the highest-priority suggestions and lengthens
 * cooldowns; HIGH lets more borderline suggestions through but never
 * disables cooldowns entirely.
 */
data class ProactiveSettings(
    val enabled: Boolean = true,
    val aggressiveness: Aggressiveness = Aggressiveness.MEDIUM,
    val quietHours: QuietHours = QuietHours(),
    val perSignal: Map<SignalType, Boolean> = SignalType.values().associateWith { true },
    val suppressedSuggestionIds: Set<String> = emptySet(),
)

enum class Aggressiveness {
    LOW,    // long cooldowns, tight per-hour cap, only high-priority signals
    MEDIUM, // default
    HIGH;   // short cooldowns, larger per-hour cap

    fun perSignalCooldownMillis(): Long = when (this) {
        LOW -> 60L * 60 * 1000      // 60m
        MEDIUM -> 30L * 60 * 1000   // 30m
        HIGH -> 10L * 60 * 1000     // 10m
    }

    fun maxSuggestionsPerHour(): Int = when (this) {
        LOW -> 2
        MEDIUM -> 3
        HIGH -> 6
    }
}

data class QuietHours(
    val enabled: Boolean = false,
    val startHour: Int = 22,    // 22:00
    val endHour: Int = 7,       // 07:00 next day
) {
    /** Inclusive range check; handles wrap across midnight. */
    fun isQuiet(hourOfDay: Int): Boolean {
        if (!enabled) return false
        return if (startHour <= endHour) hourOfDay in startHour until endHour
        else hourOfDay >= startHour || hourOfDay < endHour
    }
}
