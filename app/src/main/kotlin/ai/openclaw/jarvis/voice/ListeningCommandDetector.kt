package ai.openclaw.jarvis.voice

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight text-pattern detector for listening-control commands.
 * Runs BEFORE the full intent parser so these are always handled at
 * the highest priority, regardless of OpenClaw routing.
 */
@Singleton
class ListeningCommandDetector @Inject constructor() {

    enum class Command {
        SILENCE,           // stop TTS, keep wake word active
        STOP_LISTENING,    // mic off, wake word off, service stops
        PAUSE_10_MIN,
        PAUSE_1_HOUR,
        RESUME_LISTENING,
    }

    fun detect(text: String): Command? {
        val t = text.lowercase().trim()
        return when {
            matchesStop(t)    -> Command.STOP_LISTENING
            matchesPause1h(t) -> Command.PAUSE_1_HOUR
            matchesPause10(t) -> Command.PAUSE_10_MIN
            matchesResume(t)  -> Command.RESUME_LISTENING
            matchesSilence(t) -> Command.SILENCE
            else              -> null
        }
    }

    // ─── Pattern helpers ──────────────────────────────────────────────────────

    private fun matchesSilence(t: String) =
        t == "silence" || t == "shush" || t == "hush" ||
        t.contains("stop talking") || t.contains("be quiet") ||
        t.contains("shut up") || t.contains("stop speaking")

    private fun matchesStop(t: String) =
        t.contains("stop listening") || t.contains("stop jarvis") ||
        t.contains("turn yourself off") || t.contains("disable listening") ||
        t.contains("go offline") || t.contains("turn off jarvis")

    private fun matchesPause10(t: String) =
        (t.contains("pause") && (t.contains("10") || t.contains("ten")))

    private fun matchesPause1h(t: String) =
        t.contains("pause") && (t.contains("hour") || t.contains("60 min"))

    private fun matchesResume(t: String) =
        t.contains("start listening") || t.contains("resume listening") ||
        t.contains("wake up jarvis") || t == "resume" ||
        t.contains("turn on jarvis") || t.contains("enable listening")
}
