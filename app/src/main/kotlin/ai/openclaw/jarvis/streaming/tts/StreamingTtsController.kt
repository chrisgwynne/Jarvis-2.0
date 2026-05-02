package ai.openclaw.jarvis.streaming.tts

import ai.openclaw.jarvis.voice.TextToSpeechEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Wraps [TextToSpeechEngine] with a streaming-friendly API.
 *
 * Producers call [feedDelta] as text arrives (e.g. from
 * `OpenClawProtocolClient.chunks`); the controller pipes the deltas
 * through a [ChunkSplitter] and queues each speakable phrase onto a
 * worker coroutine that drains them into the TTS engine. Calling
 * [interrupt] cancels the worker and stops the engine *immediately* —
 * essential for the spec's "stop / wait / cancel" rule.
 *
 * The state flow [speaking] tells the UI / state machine when audio is
 * actually playing so other systems can pause / suppress.
 */
@Singleton
class StreamingTtsController @Inject constructor(
    private val tts: TextToSpeechEngine,
    private val splitter: ChunkSplitter,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    private var queue: Channel<Phrase>? = null
    @Volatile private var sessionSpeed: Float = 1.0f
    @Volatile private var sessionPitch: Float = 1.0f

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    /**
     * Begin a new streaming utterance. Idempotent on a fresh session —
     * calling [begin] mid-stream resets the splitter and starts a new
     * worker. Use [interrupt] explicitly if you want to cancel, then
     * [begin] for the next utterance.
     */
    fun begin(speed: Float = 1.0f, pitch: Float = 1.0f) {
        interrupt() // cancel any in-flight stream cleanly
        sessionSpeed = speed
        sessionPitch = pitch
        splitter.reset()
        val ch = Channel<Phrase>(capacity = Channel.UNLIMITED)
        queue = ch
        _speaking.value = true
        worker = scope.launch {
            try {
                for (item in ch) {
                    if (item is Phrase.End) break
                    val text = (item as Phrase.Speak).text
                    if (text.isBlank()) continue
                    tts.speak(text, sessionSpeed, sessionPitch)
                }
            } catch (_: CancellationException) {
                runCatching { tts.stop() }
            } finally {
                _speaking.value = false
            }
        }
    }

    /** Append a delta. Speakable phrases are queued for TTS. */
    fun feedDelta(delta: String) {
        val q = queue ?: return
        for (phrase in splitter.feed(delta)) {
            q.trySend(Phrase.Speak(phrase))
        }
    }

    /** No more text is coming. Flushes any tail and lets the worker finish naturally. */
    fun finish() {
        val q = queue ?: return
        for (phrase in splitter.flush()) {
            q.trySend(Phrase.Speak(phrase))
        }
        q.trySend(Phrase.End)
        q.close()
        queue = null
    }

    /**
     * Stop everything immediately:
     *   - cancel the worker job
     *   - stop the underlying TTS engine
     *   - drop any queued phrases
     *   - reset the splitter buffer
     */
    fun interrupt() {
        worker?.cancel()
        worker = null
        queue?.close()
        queue = null
        runCatching { tts.stop() }
        splitter.reset()
        _speaking.value = false
    }

    private sealed class Phrase {
        data class Speak(val text: String) : Phrase()
        object End : Phrase()
    }
}
