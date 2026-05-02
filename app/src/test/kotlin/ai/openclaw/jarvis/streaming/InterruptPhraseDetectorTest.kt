package ai.openclaw.jarvis.streaming

import ai.openclaw.jarvis.streaming.interrupt.InterruptPhraseDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterruptPhraseDetectorTest {

    private val d = InterruptPhraseDetector()

    @Test fun `single stop fires`() = assertTrue(d.isInterrupt("stop"))
    @Test fun `single wait fires`() = assertTrue(d.isInterrupt("wait"))
    @Test fun `single cancel fires`() = assertTrue(d.isInterrupt("cancel"))
    @Test fun `case insensitive`() = assertTrue(d.isInterrupt("STOP"))

    @Test fun `stop near start fires`() = assertTrue(d.isInterrupt("stop please"))

    @Test fun `phrase never mind fires`() = assertTrue(d.isInterrupt("oh, never mind"))

    @Test fun `regular utterance does not fire`() {
        assertFalse(d.isInterrupt("send Cath a message"))
        assertFalse(d.isInterrupt("text my brother that I will be late"))
    }

    @Test fun `stop deep in a sentence does not fire`() {
        assertFalse(d.isInterrupt("we should be at the bus stop in five"))
    }

    @Test fun `blank input is not an interrupt`() = assertFalse(d.isInterrupt(""))
}
