package ai.openclaw.jarvis.protocol.validation

import ai.openclaw.jarvis.protocol.ProtocolVersion
import ai.openclaw.jarvis.protocol.model.OpenClawAction
import ai.openclaw.jarvis.protocol.model.OpenClawResponse
import ai.openclaw.jarvis.protocol.model.OpenClawSkillManifest
import ai.openclaw.jarvis.protocol.model.PayloadCaptureScreenshot
import ai.openclaw.jarvis.protocol.model.PayloadCreateAlarm
import ai.openclaw.jarvis.protocol.model.PayloadCreateTimer
import ai.openclaw.jarvis.protocol.model.PayloadGetLocation
import ai.openclaw.jarvis.protocol.model.PayloadMakeCall
import ai.openclaw.jarvis.protocol.model.PayloadOpenApp
import ai.openclaw.jarvis.protocol.model.PayloadSendSms
import ai.openclaw.jarvis.protocol.model.PayloadSendWhatsApp
import ai.openclaw.jarvis.protocol.model.PayloadShowNotification
import ai.openclaw.jarvis.protocol.model.PayloadSpeak
import ai.openclaw.jarvis.protocol.model.PayloadTakePhoto
import ai.openclaw.jarvis.protocol.model.ActionType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Result of protocol-level validation. Either a fully-typed payload or a
 * structured rejection — the caller never has to interpret string fields.
 */
sealed class ProtocolResult<out T> {
    data class Ok<T>(val value: T) : ProtocolResult<T>()
    data class Rejected(val error: ProtocolError) : ProtocolResult<Nothing>()
}

/** Reasons a payload can be rejected. Maps to spec error categories. */
data class ProtocolError(
    val code: Code,
    val message: String,
    val rawDigest: String? = null,
) {
    enum class Code {
        UNSUPPORTED_PROTOCOL_VERSION,
        MALFORMED_JSON,
        MISSING_FIELDS,
        UNKNOWN_ACTION_TYPE,
        UNKNOWN_FRAME_TYPE,
        CONTRACT_VIOLATION,
    }
}

/**
 * Single seam between raw wire JSON and the typed protocol model.
 *
 * Why a class with @Inject:
 *   - centralised Json instance configured for the contract
 *   - Hilt-friendly so call sites don't carry their own copy
 *   - `decodeAction` returns a typed payload OR a precise rejection
 */
@Singleton
class ProtocolValidator @Inject constructor() {

    /**
     * Strict parser — unknown keys are tolerated (forward-compat) but
     * missing required fields are not.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
        encodeDefaults = true
    }

    fun parseResponse(raw: String): ProtocolResult<OpenClawResponse> = parseOf(raw) {
        json.decodeFromString(OpenClawResponse.serializer(), it)
    }.flatMap { resp ->
        when {
            !ProtocolVersion.isSupported(resp.protocolVersion) ->
                ProtocolResult.Rejected(ProtocolError(
                    code = ProtocolError.Code.UNSUPPORTED_PROTOCOL_VERSION,
                    message = "OpenClaw response protocolVersion='${resp.protocolVersion}' not supported (expected ${ProtocolVersion.CURRENT})",
                ))
            resp.requestId.isBlank() ->
                ProtocolResult.Rejected(ProtocolError(
                    code = ProtocolError.Code.MISSING_FIELDS,
                    message = "OpenClaw response missing requestId",
                ))
            else -> ProtocolResult.Ok(resp)
        }
    }

    fun parseSkillManifest(raw: String): ProtocolResult<OpenClawSkillManifest> = parseOf(raw) {
        json.decodeFromString(OpenClawSkillManifest.serializer(), it)
    }.flatMap { manifest ->
        if (!ProtocolVersion.isSupported(manifest.protocolVersion))
            ProtocolResult.Rejected(ProtocolError(
                code = ProtocolError.Code.UNSUPPORTED_PROTOCOL_VERSION,
                message = "Skill manifest protocolVersion='${manifest.protocolVersion}' not supported",
            ))
        else ProtocolResult.Ok(manifest)
    }

    fun parseResponseChunk(raw: String): ProtocolResult<ai.openclaw.jarvis.protocol.model.OpenClawResponseChunk> = parseOf(raw) {
        json.decodeFromString(ai.openclaw.jarvis.protocol.model.OpenClawResponseChunk.serializer(), it)
    }.flatMap { chunk ->
        when {
            !ProtocolVersion.isSupported(chunk.protocolVersion) -> ProtocolResult.Rejected(
                ProtocolError(
                    code = ProtocolError.Code.UNSUPPORTED_PROTOCOL_VERSION,
                    message = "Response chunk protocolVersion='${chunk.protocolVersion}' not supported",
                )
            )
            chunk.requestId.isBlank() -> ProtocolResult.Rejected(
                ProtocolError(
                    code = ProtocolError.Code.MISSING_FIELDS,
                    message = "Response chunk missing requestId",
                )
            )
            else -> ProtocolResult.Ok(chunk)
        }
    }

    /**
     * Decode the action payload into one of the [PayloadSendSms] / etc.
     * concrete types based on [OpenClawAction.type]. Validates required
     * fields are present.
     */
    fun decodeAction(action: OpenClawAction): ProtocolResult<DecodedAction> = try {
        val payload = action.payload
        val decoded: Any = when (action.type) {
            ActionType.SEND_SMS -> require(payload, "toContactName", "message")
                ?: json.decodeFromJsonElement(PayloadSendSms.serializer(), payload)
            ActionType.SEND_WHATSAPP -> require(payload, "toContactName", "message")
                ?: json.decodeFromJsonElement(PayloadSendWhatsApp.serializer(), payload)
            ActionType.MAKE_CALL -> require(payload, "toContactName")
                ?: json.decodeFromJsonElement(PayloadMakeCall.serializer(), payload)
            ActionType.OPEN_APP -> require(payload, "appName")
                ?: json.decodeFromJsonElement(PayloadOpenApp.serializer(), payload)
            ActionType.GET_LOCATION ->
                json.decodeFromJsonElement(PayloadGetLocation.serializer(), payload)
            ActionType.CAPTURE_SCREENSHOT ->
                json.decodeFromJsonElement(PayloadCaptureScreenshot.serializer(), payload)
            ActionType.TAKE_PHOTO ->
                json.decodeFromJsonElement(PayloadTakePhoto.serializer(), payload)
            ActionType.SHOW_NOTIFICATION -> require(payload, "title", "body")
                ?: json.decodeFromJsonElement(PayloadShowNotification.serializer(), payload)
            ActionType.SPEAK -> require(payload, "text")
                ?: json.decodeFromJsonElement(PayloadSpeak.serializer(), payload)
            ActionType.CREATE_TIMER -> require(payload, "minutes")
                ?: json.decodeFromJsonElement(PayloadCreateTimer.serializer(), payload)
            ActionType.CREATE_ALARM -> require(payload, "minutes")
                ?: json.decodeFromJsonElement(PayloadCreateAlarm.serializer(), payload)
        }
        if (decoded is ProtocolError) ProtocolResult.Rejected(decoded)
        else ProtocolResult.Ok(DecodedAction(action, decoded))
    } catch (t: Throwable) {
        ProtocolResult.Rejected(ProtocolError(
            code = ProtocolError.Code.MISSING_FIELDS,
            message = "Action ${action.type} payload invalid: ${t.message ?: t.javaClass.simpleName}",
        ))
    }

    /**
     * Returns null when all [required] keys are present, else a
     * ProtocolError describing what's missing.
     */
    private fun require(payload: JsonObject, vararg required: String): ProtocolError? {
        val missing = required.filter {
            val v = payload[it] ?: return@filter true
            runCatching { v.jsonPrimitive.content.isBlank() }.getOrDefault(false)
        }
        if (missing.isEmpty()) return null
        return ProtocolError(
            code = ProtocolError.Code.MISSING_FIELDS,
            message = "Action payload missing required fields: ${missing.joinToString(",")}",
        )
    }

    private inline fun <T> parseOf(raw: String, decode: (String) -> T): ProtocolResult<T> = try {
        ProtocolResult.Ok(decode(raw))
    } catch (t: Throwable) {
        ProtocolResult.Rejected(ProtocolError(
            code = ProtocolError.Code.MALFORMED_JSON,
            message = "Could not parse protocol payload: ${t.message ?: t.javaClass.simpleName}",
            rawDigest = raw.take(200),
        ))
    }
}

/** Carrier for a parsed action plus its strongly-typed payload. */
data class DecodedAction(val action: OpenClawAction, val payload: Any)

private inline fun <T, R> ProtocolResult<T>.flatMap(transform: (T) -> ProtocolResult<R>): ProtocolResult<R> =
    when (this) {
        is ProtocolResult.Ok -> transform(value)
        is ProtocolResult.Rejected -> this
    }
