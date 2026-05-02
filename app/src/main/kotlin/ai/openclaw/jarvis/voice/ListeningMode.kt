package ai.openclaw.jarvis.voice

sealed class ListeningMode {
    object Active  : ListeningMode()
    object Silent  : ListeningMode()
    data class Paused(val resumeAt: Long) : ListeningMode()
    object Stopped : ListeningMode()

    val label: String get() = when (this) {
        Active       -> "Listening"
        Silent       -> "Silent"
        is Paused    -> "Paused"
        Stopped      -> "Stopped"
    }

    val notificationText: String get() = when (this) {
        Active       -> "Listening"
        Silent       -> "Silent"
        is Paused    -> "Paused"
        Stopped      -> "Stopped"
    }

    val diagnosticText: String get() = when (this) {
        Active       -> "Listening for wake word (basic STT)"
        Silent       -> "Silent — wake word still active"
        is Paused    -> "Paused — listening resumes later"
        Stopped      -> "Stopped — microphone released"
    }

    /** Serialise to a single string for DataStore persistence. */
    val key: String get() = when (this) {
        Active       -> "active"
        Silent       -> "silent"
        Stopped      -> "stopped"
        is Paused    -> "paused:$resumeAt"
    }

    companion object {
        fun fromKey(raw: String): ListeningMode = when {
            raw == "active"          -> Active
            raw == "silent"          -> Silent
            raw == "stopped"         -> Stopped
            raw.startsWith("paused:") -> {
                val ts = raw.removePrefix("paused:").toLongOrNull() ?: 0L
                if (ts > System.currentTimeMillis()) Paused(ts) else Active
            }
            else -> Active
        }
    }
}
