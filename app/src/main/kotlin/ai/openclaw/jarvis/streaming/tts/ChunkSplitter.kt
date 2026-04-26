package ai.openclaw.jarvis.streaming.tts

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Buffers an incremental stream of text deltas and yields *speakable*
 * phrases — a phrase is everything up to and including a sentence
 * terminator (`.`, `?`, `!`, `:`, `;`) or a comma when the buffer is
 * already long enough that further waiting would feel laggy.
 *
 * Why this exists: TTS engines render chunk-by-chunk, but the chunks
 * have to be coherent or the prosody breaks. Splitting on a single
 * character ("h", "e", "l", "l", "o") would make the TTS engine
 * stutter; splitting only at the end of the response gives up all of
 * the latency we just won.
 *
 * Stateful: feed deltas in via [feed], call [flush] when the stream
 * ends so any tail without terminal punctuation still gets spoken.
 */
@Singleton
class ChunkSplitter @Inject constructor() {
    private val buf = StringBuilder()

    /** Append [delta] to the buffer and return any speakable phrases that
     *  are now complete. Order is preserved. Empty list = "wait for more". */
    fun feed(delta: String): List<String> {
        if (delta.isEmpty()) return emptyList()
        buf.append(delta)
        return drain(forceFlush = false)
    }

    /** End-of-stream — flush whatever's left even without terminal punctuation. */
    fun flush(): List<String> = drain(forceFlush = true)

    /** Reset state. Call when interrupting / starting a new response. */
    fun reset() { buf.clear() }

    private fun drain(forceFlush: Boolean): List<String> {
        if (buf.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        var start = 0
        for (i in buf.indices) {
            val c = buf[i]
            val terminal = c == '.' || c == '?' || c == '!' || c == ':' || c == ';'
            val softBreak = c == ',' && (i - start) >= COMMA_THRESHOLD
            if (terminal || softBreak) {
                val phrase = buf.substring(start, i + 1).trim()
                if (phrase.isNotEmpty()) out += phrase
                start = i + 1
            }
        }
        // Trim what we've consumed.
        if (start > 0) buf.delete(0, start)
        if (forceFlush && buf.isNotEmpty()) {
            val tail = buf.toString().trim()
            if (tail.isNotEmpty()) out += tail
            buf.clear()
        }
        return out
    }

    companion object {
        /** Minimum buffered chars before we'll soft-break on a comma. */
        private const val COMMA_THRESHOLD = 30
    }
}
