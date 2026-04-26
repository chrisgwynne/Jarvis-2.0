package ai.openclaw.jarvis.protocol

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeOpenClawServerTest {

    @Test fun `delayed timeout never replies within window`() = runBlocking {
        val server = FakeOpenClawServer(FakeOpenClawServer.Mode.DELAYED_TIMEOUT)
        val reply = withTimeoutOrNull(50) { server.receive("""{"requestId":"r-1"}""") }
        assertNull(reply)
    }

    @Test fun `offline disconnect throws`() {
        val server = FakeOpenClawServer(FakeOpenClawServer.Mode.OFFLINE_DISCONNECT)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { server.receive("""{"requestId":"r-1"}""") }
        }
    }

    @Test fun `server captures last client payload`() = runBlocking {
        val server = FakeOpenClawServer(FakeOpenClawServer.Mode.OK)
        val raw = """{"protocolVersion":"jarvis-openclaw/v1","type":"jarvis.live_request","requestId":"r-1"}"""
        server.receive(raw)
        assertEquals(raw, server.lastClientPayload)
        assertEquals("jarvis.live_request", server.lastClientPayloadType)
    }

    @Test fun `malformed mode returns garbage that fails validation`() = runBlocking {
        val server = FakeOpenClawServer(FakeOpenClawServer.Mode.MALFORMED)
        val reply = server.receive("""{"requestId":"r-1"}""")
        assertNotNull(reply)
        assertTrue(reply!!, !reply.startsWith("{") || reply.contains(",,,"))
    }
}
