package ai.openclaw.jarvis.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRedactionTest {

    @Test fun `null and empty inputs are explicit`() {
        assertEquals("<null>", LogRedaction.redactedText(null))
        assertEquals("<empty>", LogRedaction.redactedText(""))
        assertEquals("<null>", LogRedaction.redactedPhone(null))
        assertEquals("<empty>", LogRedaction.redactedPhone(""))
    }

    @Test fun `redactedText never reveals the original`() {
        val secret = "send Cath I'm leaving now"
        val out = LogRedaction.redactedText(secret)
        assertFalse(out, out.contains("Cath"))
        assertFalse(out, out.contains("leaving"))
        assertTrue(out, out.contains("len=${secret.length}"))
    }

    @Test fun `redactedText flags digits and emails`() {
        val out1 = LogRedaction.redactedText("call +447700900123")
        assertTrue(out1, out1.contains("digits="))
        val out2 = LogRedaction.redactedText("ping me at jarvis@example.com")
        assertTrue(out2, out2.contains("email-shape"))
    }

    @Test fun `redactedPhone keeps last three only`() {
        assertEquals("***123", LogRedaction.redactedPhone("+447700900123"))
        assertEquals("***456", LogRedaction.redactedPhone("0123456"))
    }

    @Test fun `redactedMessage strips bearer tokens and emails`() {
        val raw = "auth=Bearer abcdef0123456789xyz fail for ping@example.com"
        val out = LogRedaction.redactedMessage(raw)
        assertFalse(out, out.contains("abcdef0123456789xyz"))
        assertFalse(out, out.contains("ping@example.com"))
        assertTrue(out, out.contains("Bearer <redacted>"))
        assertTrue(out, out.contains("<email>"))
    }

    @Test fun `redactedMessage strips long hex blobs`() {
        val raw = "fingerprint=" + "a".repeat(40)
        val out = LogRedaction.redactedMessage(raw)
        assertFalse(out, out.contains("a".repeat(40)))
        assertTrue(out, out.contains("<hex>"))
    }

    @Test fun `redactedMessage caps length`() {
        val long = "x".repeat(1_000)
        val out = LogRedaction.redactedMessage(long)
        assertTrue(out, out.length <= 240)
    }
}
