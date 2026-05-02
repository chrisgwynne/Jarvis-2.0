package ai.openclaw.jarvis.protocol

import ai.openclaw.jarvis.protocol.model.ActionRisk
import ai.openclaw.jarvis.protocol.model.ActionType
import ai.openclaw.jarvis.protocol.model.DeviceContext
import ai.openclaw.jarvis.protocol.model.InputInfo
import ai.openclaw.jarvis.protocol.model.JarvisCapabilitySnapshot
import ai.openclaw.jarvis.protocol.model.JarvisLiveRequest
import ai.openclaw.jarvis.protocol.model.OpenClawAction
import ai.openclaw.jarvis.protocol.model.OpenClawResponse
import ai.openclaw.jarvis.protocol.model.OpenClawSkillManifest
import ai.openclaw.jarvis.protocol.model.PayloadSendSms
import ai.openclaw.jarvis.protocol.model.ReplyDirective
import ai.openclaw.jarvis.protocol.model.ResponseStatus
import ai.openclaw.jarvis.protocol.model.RouteInfo
import ai.openclaw.jarvis.protocol.model.SpeakerInfo
import ai.openclaw.jarvis.protocol.validation.ProtocolError
import ai.openclaw.jarvis.protocol.validation.ProtocolResult
import ai.openclaw.jarvis.protocol.validation.ProtocolValidator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolContractTest {

    private val validator = ProtocolValidator()

    // ─── LiveRequest ─────────────────────────────────────────────────────────

    @Test fun `live request serialises with envelope`() {
        val req = sampleLiveRequest()
        val raw = validator.json.encodeToString(JarvisLiveRequest.serializer(), req)
        assertTrue(raw, raw.contains("\"protocolVersion\":\"jarvis-openclaw/v1\""))
        assertTrue(raw, raw.contains("\"requestId\":\"r-1\""))
        assertTrue(raw, raw.contains("\"type\":\"jarvis.live_request\""))
    }

    @Test fun `live request includes typed capability snapshot`() {
        val req = sampleLiveRequest()
        val raw = validator.json.encodeToString(JarvisLiveRequest.serializer(), req)
        assertTrue(raw, raw.contains("\"capabilities\""))
        assertTrue(raw, raw.contains("\"sms\""))
        assertTrue(raw, raw.contains("\"bluetooth\""))
    }

    // ─── OpenClawResponse parsing ────────────────────────────────────────────

    @Test fun `valid response parses`() = runBlocking {
        val raw = FakeOpenClawServer(FakeOpenClawServer.Mode.OK).receive(sampleEnvelope("r-1"))!!
        val r = validator.parseResponse(raw)
        assertTrue(r is ProtocolResult.Ok)
        assertEquals("Done.", (r as ProtocolResult.Ok).value.reply.text)
    }

    @Test fun `unsupported protocol version rejected`() = runBlocking {
        val raw = FakeOpenClawServer(FakeOpenClawServer.Mode.UNSUPPORTED_VERSION)
            .receive(sampleEnvelope("r-1"))!!
        val r = validator.parseResponse(raw)
        assertTrue(r is ProtocolResult.Rejected)
        assertEquals(
            ProtocolError.Code.UNSUPPORTED_PROTOCOL_VERSION,
            (r as ProtocolResult.Rejected).error.code,
        )
    }

    @Test fun `malformed json rejected`() {
        val r = validator.parseResponse("{not really json")
        assertTrue(r is ProtocolResult.Rejected)
        assertEquals(
            ProtocolError.Code.MALFORMED_JSON,
            (r as ProtocolResult.Rejected).error.code,
        )
    }

    // ─── Action decode ───────────────────────────────────────────────────────

    @Test fun `unknown action type rejected`() = runBlocking {
        val raw = FakeOpenClawServer(FakeOpenClawServer.Mode.UNKNOWN_ACTION)
            .receive(sampleEnvelope("r-1"))!!
        val r = validator.parseResponse(raw)
        // Top-level parse fails because the enum decoder can't handle the
        // bogus type string — that's the expected, fail-loud behaviour.
        assertTrue(r is ProtocolResult.Rejected)
    }

    @Test fun `missing required payload fields rejected`() {
        val action = OpenClawAction(
            actionId = "a-1",
            type = ActionType.SEND_SMS,
            payload = buildJsonObject {
                put("toContactName", JsonPrimitive("Cath"))
                // message missing
            },
        )
        val r = validator.decodeAction(action)
        assertTrue(r is ProtocolResult.Rejected)
        assertEquals(
            ProtocolError.Code.MISSING_FIELDS,
            (r as ProtocolResult.Rejected).error.code,
        )
    }

    @Test fun `complete payload decodes`() {
        val action = OpenClawAction(
            actionId = "a-1",
            type = ActionType.SEND_SMS,
            payload = buildJsonObject {
                put("toContactName", JsonPrimitive("Cath"))
                put("message", JsonPrimitive("I'm leaving now"))
            },
        )
        val r = validator.decodeAction(action)
        assertTrue(r is ProtocolResult.Ok)
        val payload = (r as ProtocolResult.Ok).value.payload as PayloadSendSms
        assertEquals("Cath", payload.toContactName)
        assertEquals("I'm leaving now", payload.message)
    }

    // ─── Confirmation ────────────────────────────────────────────────────────

    @Test fun `requires confirmation flag flows through response`() = runBlocking {
        val raw = FakeOpenClawServer(FakeOpenClawServer.Mode.REQUIRES_CONFIRMATION)
            .receive(sampleEnvelope("r-1"))!!
        val r = validator.parseResponse(raw) as ProtocolResult.Ok
        assertTrue(r.value.requiresConfirmation)
        assertEquals(ResponseStatus.needs_confirmation, r.value.status)
        assertEquals(1, r.value.actions.size)
        assertTrue(r.value.actions.first().requiresConfirmation)
    }

    // ─── Skill manifest ──────────────────────────────────────────────────────

    @Test fun `skill manifest parses`() {
        val raw = FakeOpenClawServer().replySkillManifest()
        val r = validator.parseSkillManifest(raw)
        assertTrue(r is ProtocolResult.Ok)
        val manifest = (r as ProtocolResult.Ok).value
        assertEquals(1, manifest.skills.size)
        assertEquals("email.send", manifest.skills.first().id)
    }

    // ─── Error response ──────────────────────────────────────────────────────

    @Test fun `error status preserves error block`() = runBlocking {
        val raw = FakeOpenClawServer(FakeOpenClawServer.Mode.ERROR_STATUS)
            .receive(sampleEnvelope("r-1"))!!
        val r = validator.parseResponse(raw) as ProtocolResult.Ok
        assertEquals(ResponseStatus.error, r.value.status)
        assertNotNull(r.value.error)
        assertEquals("BACKEND_DOWN", r.value.error?.code)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun sampleLiveRequest() = JarvisLiveRequest(
        requestId = "r-1",
        sessionKey = "jarvis:test",
        timestamp = "2026-01-01T00:00:00Z",
        speaker = SpeakerInfo("chris", "OWNER", 0.92f),
        input = InputInfo(mode = "voice", text = "send Cath a message", audioSource = "phone"),
        route = RouteInfo(chosen = "OPENCLAW", localIntent = "COMMUNICATION_SEND", confidence = 0.4f),
        deviceContext = DeviceContext(
            battery = 88, charging = true,
            screenState = "unlocked", foregroundApp = "ai.openclaw.jarvis",
            network = "wifi", locationLabel = "home",
        ),
        capabilities = JarvisCapabilitySnapshot(),
    )

    private fun sampleEnvelope(requestId: String) = """
        {"protocolVersion":"jarvis-openclaw/v1","requestId":"$requestId","sessionKey":"jarvis:test",
         "timestamp":"2026-01-01T00:00:00Z","type":"jarvis.live_request",
         "speaker":{"id":"chris","trustLevel":"OWNER","confidence":0.92},
         "input":{"mode":"voice","text":"hi","audioSource":"phone"},
         "route":{"chosen":"OPENCLAW","confidence":0.5},
         "deviceContext":{"battery":80,"charging":true,"screenState":"unlocked","foregroundApp":"x","network":"wifi","locationLabel":"home"},
         "capabilities":{}}
    """.trimIndent()
}
