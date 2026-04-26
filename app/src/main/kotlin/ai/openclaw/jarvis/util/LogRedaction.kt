package ai.openclaw.jarvis.util

/**
 * Redaction helpers for log lines that *might* include user content.
 *
 * The rule throughout the app: never log raw transcripts, raw message
 * bodies, raw phone numbers, raw OpenClaw frames, or anything coming
 * out of [android.speech.SpeechRecognizer]. Use [redactedText] to log
 * only a shape (length + presence of digits) that's enough to debug a
 * voice-pipeline bug without leaking what the user actually said.
 */
object LogRedaction {

    /**
     * Replace the value with a digest of its shape: length, whether it
     * contains digits, whether it contains `@`. Never returns the
     * original characters.
     *
     *   redactedText("hello world")     → "<text len=11>"
     *   redactedText("call +447700...") → "<text len=14 digits=10>"
     *   redactedText(null)              → "<null>"
     *   redactedText("")                → "<empty>"
     */
    fun redactedText(s: String?): String {
        if (s == null) return "<null>"
        if (s.isEmpty()) return "<empty>"
        val digits = s.count { it.isDigit() }
        val hasAt = '@' in s
        val parts = mutableListOf("len=${s.length}")
        if (digits > 0) parts += "digits=$digits"
        if (hasAt) parts += "email-shape"
        return "<text ${parts.joinToString(" ")}>"
    }

    /**
     * For phone numbers / contact identifiers — never log past the last
     * 2 digits.
     *
     *   redactedPhone("+447700900123") → "***123"
     *   redactedPhone(null)            → "<null>"
     *   redactedPhone("")              → "<empty>"
     */
    fun redactedPhone(s: String?): String {
        if (s == null) return "<null>"
        if (s.isEmpty()) return "<empty>"
        val tail = s.takeLast(3)
        return "***$tail"
    }

    /** Strip secrets out of a raw error / message before it goes to logcat. */
    fun redactedMessage(s: String?): String {
        if (s == null) return "<null>"
        if (s.isEmpty()) return "<empty>"
        return s
            .replace(EMAIL, "<email>")
            .replace(BEARER, "Bearer <redacted>")
            .replace(LONG_HEX, "<hex>")
            .take(MAX_MESSAGE_LENGTH)
    }

    private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val BEARER = Regex("""(?i)bearer\s+[A-Za-z0-9._\-]{8,}""")
    private val LONG_HEX = Regex("""(?<![A-Za-z0-9])[A-Fa-f0-9]{32,}(?![A-Za-z0-9])""")
    private const val MAX_MESSAGE_LENGTH = 240
}
