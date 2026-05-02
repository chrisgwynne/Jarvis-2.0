package ai.openclaw.jarvis.streaming

import ai.openclaw.jarvis.streaming.tts.ChunkSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkSplitterTest {

    @Test fun `single short delta returns nothing yet`() {
        val s = ChunkSplitter()
        assertTrue(s.feed("hello").isEmpty())
    }

    @Test fun `terminal punctuation flushes a phrase`() {
        val s = ChunkSplitter()
        assertTrue(s.feed("hello").isEmpty())
        val out = s.feed(" world.")
        assertEquals(listOf("hello world."), out)
    }

    @Test fun `multiple sentences split as they arrive`() {
        val s = ChunkSplitter()
        s.feed("First sentence.")
        val out = s.feed(" Second one!")
        assertEquals(listOf("Second one!"), out)
    }

    @Test fun `comma triggers soft break only when long enough`() {
        val s = ChunkSplitter()
        // Below threshold: comma alone shouldn't split.
        assertTrue(s.feed("hi,").isEmpty())
        // Push past threshold then introduce a comma.
        val out = s.feed(" this is now a longer thought, and it continues")
        assertEquals(1, out.size)
        assertTrue(out.first().endsWith(","))
    }

    @Test fun `flush emits trailing tail`() {
        val s = ChunkSplitter()
        s.feed("a partial without terminator")
        val tail = s.flush()
        assertEquals(listOf("a partial without terminator"), tail)
    }

    @Test fun `reset drops buffered text`() {
        val s = ChunkSplitter()
        s.feed("dropped")
        s.reset()
        assertTrue(s.flush().isEmpty())
    }
}
