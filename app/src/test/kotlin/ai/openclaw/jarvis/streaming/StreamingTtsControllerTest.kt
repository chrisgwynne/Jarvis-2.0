package ai.openclaw.jarvis.streaming

import ai.openclaw.jarvis.streaming.tts.ChunkSplitter
import ai.openclaw.jarvis.streaming.tts.StreamingTtsController
import ai.openclaw.jarvis.voice.TextToSpeechEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingTtsControllerTest {

    @Test fun `streamed deltas are spoken phrase by phrase in order`() = runBlocking {
        val tts = RecordingTts()
        val ctl = StreamingTtsController(tts, ChunkSplitter())

        ctl.begin()
        ctl.feedDelta("Hello world.")
        ctl.feedDelta(" Second sentence?")
        ctl.feedDelta(" Third one!")
        ctl.finish()
        // Worker drains async — wait briefly.
        withTimeout(2_000) { while (tts.spoken.size < 3) delay(5) }
        assertEquals(listOf("Hello world.", "Second sentence?", "Third one!"), tts.spoken)
    }

    @Test fun `interrupt stops the engine and clears state`() = runBlocking {
        val tts = SlowTts()
        val ctl = StreamingTtsController(tts, ChunkSplitter())
        ctl.begin()
        ctl.feedDelta("First chunk. Second chunk. Third chunk.")
        // Give the worker a tick to start speaking.
        delay(20)
        ctl.interrupt()
        assertTrue(tts.stopCalls >= 1)
        // Speaking flag drops after interrupt.
        assertFalse(ctl.speaking.value)
    }

    @Test fun `finish without deltas does not crash`() = runBlocking {
        val tts = RecordingTts()
        val ctl = StreamingTtsController(tts, ChunkSplitter())
        ctl.begin()
        ctl.finish()
        assertTrue(tts.spoken.isEmpty())
    }

    // ─── Fakes ───────────────────────────────────────────────────────────────

    private class RecordingTts : TextToSpeechEngine {
        val spoken = mutableListOf<String>()
        override suspend fun speak(text: String, speed: Float, pitch: Float) {
            spoken += text
        }
        override fun stop() {}
        override fun isReady(): Boolean = true
        override fun shutdown() {}
    }

    private class SlowTts : TextToSpeechEngine {
        var stopCalls = 0
        override suspend fun speak(text: String, speed: Float, pitch: Float) {
            // Simulate engine actually taking time so interrupt can hit mid-stream.
            delay(500)
        }
        override fun stop() { stopCalls++ }
        override fun isReady(): Boolean = true
        override fun shutdown() {}
    }
}
