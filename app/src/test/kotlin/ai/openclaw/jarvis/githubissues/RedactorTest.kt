package ai.openclaw.jarvis.githubissues

import ai.openclaw.jarvis.githubissues.redaction.RedactionPolicy
import ai.openclaw.jarvis.githubissues.redaction.Redactor
import ai.openclaw.jarvis.githubissues.settings.RedactionSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {

    private fun redactor(settings: RedactionSettings = RedactionSettings()) =
        Redactor(RedactionPolicy(settings))

    @Test fun `redacts phone numbers`() {
        val out = redactor().redact("call me on +44 7700 900 123 please")!!
        assertTrue(out, out.contains("<redacted-phone>"))
        assertFalse(out, out.contains("7700"))
    }

    @Test fun `redacts emails`() {
        val out = redactor().redact("ping me at jarvis.user@example.com today")!!
        assertTrue(out, out.contains("<redacted-email>"))
        assertFalse(out, out.contains("example.com"))
    }

    @Test fun `redacts github PATs and bearer tokens`() {
        val pat = "ghp_" + "a".repeat(36)
        val out = redactor().redact("token ghp -> $pat and Bearer abcdef0123456789xyz")!!
        assertTrue(out, out.contains("<redacted-token>"))
        assertFalse(out, out.contains(pat))
    }

    @Test fun `redacts openclaw key pairs`() {
        val out = redactor().redact("openclaw_token: SECRET-VALUE-1234")!!
        assertTrue(out, out.contains("<redacted-openclaw-key>"))
    }

    @Test fun `redacts lat long coordinates`() {
        val out = redactor().redact("you were at 51.5074, -0.1278")!!
        assertTrue(out, out.contains("<redacted-location>"))
    }

    @Test fun `respects contact name list`() {
        val out = redactor().redact("send Mum a message", listOf("Mum"))!!
        assertTrue(out, out.contains("<redacted-contact>"))
        assertFalse(out, out.contains("Mum"))
    }

    @Test fun `does not redact when disabled`() {
        val settings = RedactionSettings(
            redactPhoneNumbers = false,
            redactEmails = false
        )
        val out = redactor(settings).redact("call +447700900123 or jarvis@example.com")!!
        assertTrue(out, out.contains("447700900123"))
        assertTrue(out, out.contains("jarvis@example.com"))
    }

    @Test fun `redactMessageBody collapses to length when policy disallows`() {
        val out = redactor().redactMessageBody("hello there general kenobi")
        assertEquals("<redacted 26 chars>", out)
    }

    @Test fun `redactMessageBody passes through when allowed`() {
        val r = redactor(RedactionSettings(redactMessageBody = false))
        assertEquals("hello", r.redactMessageBody("hello"))
    }

    @Test fun `null and empty inputs are safe`() {
        val r = redactor()
        assertEquals(null, r.redact(null))
        assertEquals("", r.redact(""))
        assertNotNull(r.redact("plain text"))
    }
}
