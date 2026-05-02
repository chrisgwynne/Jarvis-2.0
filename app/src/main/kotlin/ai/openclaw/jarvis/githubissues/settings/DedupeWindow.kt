package ai.openclaw.jarvis.githubissues.settings

import java.util.concurrent.TimeUnit

enum class DedupeWindow(val millis: Long, val label: String) {
    ONE_HOUR(TimeUnit.HOURS.toMillis(1), "1h"),
    ONE_DAY(TimeUnit.DAYS.toMillis(1), "24h"),
    SEVEN_DAYS(TimeUnit.DAYS.toMillis(7), "7d");

    companion object {
        fun fromLabel(label: String): DedupeWindow =
            values().firstOrNull { it.label == label } ?: ONE_DAY
    }
}
