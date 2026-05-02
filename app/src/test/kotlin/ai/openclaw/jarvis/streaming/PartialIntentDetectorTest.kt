package ai.openclaw.jarvis.streaming

import ai.openclaw.jarvis.streaming.stt.PartialIntentDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartialIntentDetectorTest {

    private val d = PartialIntentDetector()

    @Test fun `tiny partial returns nothing`() {
        assertNull(d.guess("h"))
    }

    @Test fun `text Cath is a send-message guess with contact`() {
        val g = d.guess("text Cath I'm leaving")
        assertEquals(PartialIntentDetector.Guess.SendMessage("Cath"), g)
    }

    @Test fun `whatsapp Cath is a send-message guess`() {
        val g = d.guess("whatsapp Cath that I'm on the way")
        assertEquals(PartialIntentDetector.Guess.SendMessage("Cath that"), g)
    }

    @Test fun `call Mum is a make-call guess`() {
        val g = d.guess("call Mum")
        assertEquals(PartialIntentDetector.Guess.MakeCall("Mum"), g)
    }

    @Test fun `open Spotify is open-app guess`() {
        val g = d.guess("open Spotify")
        assertEquals(PartialIntentDetector.Guess.OpenApp("spotify"), g)
    }

    @Test fun `screenshot phrase`() {
        assertEquals(PartialIntentDetector.Guess.Screenshot, d.guess("take a screenshot"))
    }

    @Test fun `where am i is location`() {
        assertEquals(PartialIntentDetector.Guess.Location, d.guess("where am i right now"))
    }

    @Test fun `cancel words win`() {
        assertEquals(PartialIntentDetector.Guess.Cancel, d.guess("stop"))
        assertEquals(PartialIntentDetector.Guess.Cancel, d.guess("cancel"))
        assertEquals(PartialIntentDetector.Guess.Cancel, d.guess("never mind"))
    }

    @Test fun `unrelated chatter returns null`() {
        assertNull(d.guess("just thinking out loud here"))
    }
}
