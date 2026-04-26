package ai.openclaw.jarvis.protocol

import ai.openclaw.jarvis.protocol.model.JarvisActionResult
import ai.openclaw.jarvis.protocol.model.JarvisLiveRequest
import ai.openclaw.jarvis.protocol.model.OpenClawAction
import ai.openclaw.jarvis.protocol.model.OpenClawResponse
import ai.openclaw.jarvis.protocol.model.OpenClawSkill
import ai.openclaw.jarvis.protocol.model.OpenClawSkillManifest
import ai.openclaw.jarvis.protocol.model.OpenClawSkillManifestRequest
import ai.openclaw.jarvis.protocol.model.ReplyDirective
import ai.openclaw.jarvis.protocol.model.ResponseError
import ai.openclaw.jarvis.protocol.model.ResponseStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * In-process simulator for the OpenClaw side of the WebSocket. Used by
 * unit tests to drive the [ai.openclaw.jarvis.protocol.client.OpenClawProtocolClient]
 * through every contract path the spec calls out.
 *
 * It does NOT speak the real WebSocket — it speaks JSON strings, since
 * that's the only thing the protocol actually parses. Tests feed
 * `serverToClient` strings into the client (or directly into the
 * validator), and read what the client sent via `lastClientPayload`.
 */
class FakeOpenClawServer(
    private val mode: Mode = Mode.OK,
) {
    enum class Mode {
        OK,                         // valid response
        MALFORMED,                  // returns non-JSON / structurally invalid
        UNSUPPORTED_VERSION,        // returns response with wrong protocolVersion
        REQUIRES_CONFIRMATION,      // returns one action with requiresConfirmation=true
        UNKNOWN_ACTION,             // returns an action with an out-of-spec type
        DELAYED_TIMEOUT,            // never replies — caller's own timeout fires
        OFFLINE_DISCONNECT,         // throws to simulate gateway disconnect
        ERROR_STATUS,               // returns status="error"
    }

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    /** Last raw payload Jarvis sent the server. Set by [receive]. */
    var lastClientPayload: String? = null
        private set
    var lastClientPayloadType: String? = null
        private set

    /**
     * Hook invoked when the test feeds a serialized request into the
     * server. Returns the raw JSON the server would reply with, or
     * `null` for [Mode.DELAYED_TIMEOUT].
     */
    suspend fun receive(rawClientFrame: String): String? {
        lastClientPayload = rawClientFrame
        val obj = runCatching { json.parseToJsonElement(rawClientFrame) as? JsonObject }.getOrNull()
        lastClientPayloadType = obj?.get("type")?.jsonPrimitive?.content
        val requestId = obj?.get("requestId")?.jsonPrimitive?.content ?: "missing"

        return when (mode) {
            Mode.OK -> ok(requestId)
            Mode.MALFORMED -> "{not really json,,,"
            Mode.UNSUPPORTED_VERSION -> unsupportedVersion(requestId)
            Mode.REQUIRES_CONFIRMATION -> requiresConfirmation(requestId)
            Mode.UNKNOWN_ACTION -> unknownAction(requestId)
            Mode.DELAYED_TIMEOUT -> { delay(60_000); null }
            Mode.OFFLINE_DISCONNECT -> error("disconnected")
            Mode.ERROR_STATUS -> errorStatus(requestId)
        }
    }

    /** Convenience: build a manifest reply for a manifest request. */
    fun replySkillManifest(): String {
        val manifest = OpenClawSkillManifest(
            skills = listOf(
                OpenClawSkill(
                    id = "email.send",
                    name = "Send Email",
                    description = "Send email through OpenClaw",
                    examples = listOf("Send an email to Dave"),
                    requiresApproval = true,
                    available = true,
                ),
            ),
        )
        return json.encodeToString(OpenClawSkillManifest.serializer(), manifest)
    }

    fun parseLastRequest(): JarvisLiveRequest? = lastClientPayload?.let {
        runCatching { json.decodeFromString(JarvisLiveRequest.serializer(), it) }.getOrNull()
    }

    fun parseLastActionResult(): JarvisActionResult? = lastClientPayload?.let {
        runCatching { json.decodeFromString(JarvisActionResult.serializer(), it) }.getOrNull()
    }

    // ─── Reply builders ──────────────────────────────────────────────────────

    private fun ok(requestId: String): String {
        val resp = OpenClawResponse(
            requestId = requestId,
            reply = ReplyDirective(text = "Done.", speak = true, display = true),
        )
        return json.encodeToString(OpenClawResponse.serializer(), resp)
    }

    private fun unsupportedVersion(requestId: String): String {
        val resp = OpenClawResponse(
            protocolVersion = "jarvis-openclaw/v999",
            requestId = requestId,
            reply = ReplyDirective(text = "irrelevant"),
        )
        return json.encodeToString(OpenClawResponse.serializer(), resp)
    }

    private fun requiresConfirmation(requestId: String): String {
        val action = OpenClawAction(
            actionId = "act-1",
            type = ai.openclaw.jarvis.protocol.model.ActionType.SEND_SMS,
            payload = buildJsonObject {
                put("toContactName", JsonPrimitive("Cath"))
                put("message", JsonPrimitive("I'm leaving now"))
            },
            requiresConfirmation = true,
            risk = ai.openclaw.jarvis.protocol.model.ActionRisk.limited,
            reason = "send SMS to Cath",
        )
        val resp = OpenClawResponse(
            requestId = requestId,
            reply = ReplyDirective(text = "Sending SMS to Cath."),
            actions = listOf(action),
            requiresConfirmation = true,
            status = ResponseStatus.needs_confirmation,
        )
        return json.encodeToString(OpenClawResponse.serializer(), resp)
    }

    private fun unknownAction(requestId: String): String {
        // Inject an out-of-spec type via a hand-written JSON string so it
        // round-trips through ProtocolValidator's enum decode.
        return """
        {
          "protocolVersion": "jarvis-openclaw/v1",
          "requestId": "$requestId",
          "type": "openclaw.response",
          "reply": {"text": "trying", "speak": true, "display": true},
          "actions": [
            {
              "actionId": "act-1",
              "type": "DEFINITELY_NOT_A_REAL_ACTION",
              "payload": {},
              "requiresConfirmation": false,
              "risk": "safe"
            }
          ],
          "requiresConfirmation": false,
          "status": "ok"
        }
        """.trimIndent()
    }

    private fun errorStatus(requestId: String): String {
        val resp = OpenClawResponse(
            requestId = requestId,
            reply = ReplyDirective(text = ""),
            status = ResponseStatus.error,
            error = ResponseError(code = "BACKEND_DOWN", message = "Backend unavailable"),
        )
        return json.encodeToString(OpenClawResponse.serializer(), resp)
    }

    fun parseAsManifestRequest(raw: String): OpenClawSkillManifestRequest? = runCatching {
        json.decodeFromString(OpenClawSkillManifestRequest.serializer(), raw)
    }.getOrNull()
}
