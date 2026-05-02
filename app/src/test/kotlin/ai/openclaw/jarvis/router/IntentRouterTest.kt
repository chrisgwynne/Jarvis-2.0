package ai.openclaw.jarvis.router

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class IntentRouterTest {

    private lateinit var parser: IntentParser

    @Before
    fun setUp() {
        parser = IntentParser()
    }

    @Test
    fun `torch on command routes to DEVICE_CONTROL`() {
        val result = parser.parse("turn on the torch")
        assertEquals(IntentType.DEVICE_CONTROL, result.type)
        assertEquals(DeviceControlAction.TORCH_ON, result.deviceAction)
    }

    @Test
    fun `torch off command routes to DEVICE_CONTROL`() {
        val result = parser.parse("turn off the flashlight")
        assertEquals(IntentType.DEVICE_CONTROL, result.type)
        assertEquals(DeviceControlAction.TORCH_OFF, result.deviceAction)
    }

    @Test
    fun `send message command routes to COMMUNICATION_SEND`() {
        val result = parser.parse("send a message to Chris saying hello")
        assertEquals(IntentType.COMMUNICATION_SEND, result.type)
        assertNotNull(result.contact)
    }

    @Test
    fun `call command routes to COMMUNICATION_CALL`() {
        val result = parser.parse("call Mum")
        assertEquals(IntentType.COMMUNICATION_CALL, result.type)
        assertNotNull(result.contact)
    }

    @Test
    fun `open app command routes to APP_OPEN`() {
        val result = parser.parse("open Spotify")
        assertEquals(IntentType.APP_OPEN, result.type)
    }

    @Test
    fun `cancel command routes to CANCEL_STOP`() {
        val result = parser.parse("cancel")
        assertEquals(IntentType.CANCEL_STOP, result.type)
    }

    @Test
    fun `enrol voice command routes to ENROL_VOICE`() {
        val result = parser.parse("enrol my voice")
        assertEquals(IntentType.ENROL_VOICE, result.type)
    }

    @Test
    fun `free-form query routes to OPENCLAW_REQUEST`() {
        val result = parser.parse("what is the capital of France")
        assertEquals(IntentType.OPENCLAW_REQUEST, result.type)
    }
}
