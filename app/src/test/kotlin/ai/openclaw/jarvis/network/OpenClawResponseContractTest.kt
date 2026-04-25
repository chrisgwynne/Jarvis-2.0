package ai.openclaw.jarvis.network

import org.junit.Assert.*
import org.junit.Test

class OpenClawResponseContractTest {

    @Test
    fun `parses valid minimal response`() {
        val json = """{"reply":"Hello!"}"""
        val response = OpenClawResponseContract.parse(json)
        assertEquals("Hello!", response.reply)
        assertFalse(response.hasError)
    }

    @Test
    fun `parses full response with actions`() {
        val json = """
            {
              "reply": "Sending message",
              "requiresConfirmation": true,
              "memoryCandidate": "Chris prefers WhatsApp",
              "sessionId": "abc-123",
              "actions": [
                {"type": "send_sms", "requiresConfirmation": false}
              ]
            }
        """.trimIndent()
        val response = OpenClawResponseContract.parse(json)
        assertEquals("Sending message", response.reply)
        assertTrue(response.requiresConfirmation)
        assertEquals("Chris prefers WhatsApp", response.memoryCandidate)
        assertEquals("abc-123", response.sessionId)
        assertEquals(1, response.actions.size)
        assertEquals("send_sms", response.actions[0].type)
    }

    @Test
    fun `malformed JSON returns error response`() {
        val json = "not valid json {"
        val response = OpenClawResponseContract.parse(json)
        assertTrue(response.hasError)
        assertTrue(response.error!!.contains("Malformed"))
    }

    @Test
    fun `empty string returns error response`() {
        val response = OpenClawResponseContract.parse("")
        assertTrue(response.hasError)
    }

    @Test
    fun `error factory builds error response`() {
        val response = OpenClawResponseContract.error("Gateway timeout", "session-999")
        assertTrue(response.hasError)
        assertEquals("Gateway timeout", response.error)
        assertEquals("session-999", response.sessionId)
    }

    @Test
    fun `spokenReply falls back to error when reply is blank`() {
        val response = OpenClawResponseContract.error("Something broke")
        assertEquals("Something broke", response.spokenReply)
    }

    @Test
    fun `unknown fields are ignored`() {
        val json = """{"reply":"hi","unknownField":"ignored","another":42}"""
        val response = OpenClawResponseContract.parse(json)
        assertEquals("hi", response.reply)
        assertFalse(response.hasError)
    }
}
