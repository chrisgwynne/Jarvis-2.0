package ai.openclaw.jarvis.streaming.interrupt

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phrase matcher that catches barge-in interrupts mid-TTS. Designed to
 * run on STT *partials* — the moment we see "stop" / "wait" / "cancel"
 * the caller should stop the streaming TTS, drop in-flight execution,
 * and return to listening.
 *
 * Match rules:
 *   - whole-word match against the canonical interrupt vocabulary
 *   - no fuzzy matching — false positives during a regular utterance
 *     would be much worse than missing a stop
 *   - the partial only has to *contain* one of the words; the user
 *     barging in over the assistant rarely says "Stop please" cleanly
 *     before the cancel needs to fire
 */
@Singleton
class InterruptPhraseDetector @Inject constructor() {

    fun isInterrupt(partial: String): Boolean {
        if (partial.isBlank()) return false
        val tokens = partial.lowercase().split(NON_WORD).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        // Single-word utterance: most reliable signal of a barge-in.
        if (tokens.size == 1 && tokens.first() in HARD_INTERRUPTS) return true
        // Multi-word: require the interrupt token to be near the start.
        return tokens.take(3).any { it in HARD_INTERRUPTS } ||
            PHRASES.any { partial.contains(it, ignoreCase = true) }
    }

    companion object {
        private val NON_WORD = Regex("[^a-zA-Z']+")
        private val HARD_INTERRUPTS = setOf("stop", "wait", "cancel", "abort", "quiet", "shut")
        private val PHRASES = listOf(
            "never mind", "nevermind", "shut up", "be quiet", "hold on",
        )
    }
}
