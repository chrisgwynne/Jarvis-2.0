package ai.openclaw.jarvis.streaming.stt

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cheap, deterministic early classifier that runs on partial STT
 * transcripts. Returns null when the partial isn't clear enough yet —
 * the caller waits for more text before pre-resolving anything.
 *
 * Distinct from [ai.openclaw.jarvis.router.IntentParser]: that one
 * picks a strict [ai.openclaw.jarvis.router.IntentType] and is treated
 * as the source of truth. This one outputs a low-stakes "what does it
 * look like so far?" guess so the predictive resolver can start
 * prefetching contacts / app metadata.
 */
@Singleton
class PartialIntentDetector @Inject constructor() {

    sealed class Guess {
        data class SendMessage(val contactHint: String?) : Guess()
        data class MakeCall(val contactHint: String?) : Guess()
        data class OpenApp(val appHint: String?) : Guess()
        object Screenshot : Guess()
        object Location : Guess()
        object Cancel : Guess()
    }

    fun guess(partial: String): Guess? {
        val s = partial.trim().lowercase()
        if (s.length < MIN_LENGTH) return null

        // Cancel words come first — they should win even if other tokens match.
        if (s == "stop" || s == "cancel" || s.startsWith("never mind") ||
            s == "wait" || s == "abort") return Guess.Cancel

        // Send / message / text / whatsapp <name>
        SEND_VERBS.forEach { v ->
            val idx = s.indexOf("$v ")
            if (idx >= 0) {
                val tail = s.substring(idx + v.length + 1).trim()
                val name = takeName(tail)
                return Guess.SendMessage(name)
            }
        }
        // Call / ring / phone <name>
        CALL_VERBS.forEach { v ->
            if (s.startsWith("$v ")) {
                val name = takeName(s.removePrefix("$v ").trim())
                return Guess.MakeCall(name)
            }
        }
        // Open / launch <app>
        OPEN_VERBS.forEach { v ->
            if (s.startsWith("$v ")) {
                val name = takeName(s.removePrefix("$v ").trim())
                return Guess.OpenApp(name)
            }
        }
        if (s.contains("screenshot") || s.contains("capture screen")) return Guess.Screenshot
        if (s.contains("where am i") || s.contains("my location")) return Guess.Location
        return null
    }

    /** Take the first 1–2 token "name" from a tail. Stops at filler words. */
    private fun takeName(tail: String): String? {
        if (tail.isBlank()) return null
        val tokens = tail.split(Regex("\\s+"))
        val out = mutableListOf<String>()
        for (t in tokens) {
            if (t in STOP_TOKENS) break
            out += t
            if (out.size >= 2) break
        }
        return out.joinToString(" ").takeIf { it.isNotBlank() }
    }

    companion object {
        private const val MIN_LENGTH = 3
        private val SEND_VERBS = listOf("send", "message", "text", "whatsapp")
        private val CALL_VERBS = listOf("call", "ring", "phone", "dial")
        private val OPEN_VERBS = listOf("open", "launch", "start")
        private val STOP_TOKENS = setOf(
            "saying", "to", "that", "and", "i'm", "im", "i", "with", "about",
        )
    }
}
