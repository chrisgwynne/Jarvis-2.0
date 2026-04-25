package ai.openclaw.jarvis.protocol.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Typed action requested by OpenClaw. The `payload` is left as a generic
 * [JsonObject] on the wire and decoded into one of the concrete payload
 * data classes by the validator (see [ai.openclaw.jarvis.protocol.validation.ActionPayloads]).
 *
 * The validator rejects unknown [type] values, missing required fields,
 * and restricted actions executed under non-OWNER trust.
 */
@Serializable
data class OpenClawAction(
    val actionId: String,
    val type: ActionType,
    val payload: JsonObject = JsonObject(emptyMap()),
    val requiresConfirmation: Boolean = false,
    val risk: ActionRisk = ActionRisk.safe,
    val reason: String? = null,
)

/** Strict allow-list of action types Jarvis recognises. */
@Serializable
enum class ActionType {
    SEND_SMS,
    SEND_WHATSAPP,
    MAKE_CALL,
    OPEN_APP,
    GET_LOCATION,
    CAPTURE_SCREENSHOT,
    TAKE_PHOTO,
    SHOW_NOTIFICATION,
    SPEAK,
    CREATE_TIMER,
    CREATE_ALARM,
}

@Serializable
enum class ActionRisk { safe, limited, restricted }

// ─── Concrete typed payloads ─────────────────────────────────────────────────
//
// Each `Payload*` type is the parsed shape of `OpenClawAction.payload` for a
// given `ActionType`. They are NOT serialised on the wire as the variant —
// the wire format keeps `payload: JsonObject` so OpenClaw can extend without
// breaking older Jarvis clients. The validator decodes the JsonObject into
// the right Payload* class, or rejects with MISSING_FIELDS / UNKNOWN_TYPE.

@Serializable
data class PayloadSendSms(
    val toContactName: String,
    val message: String,
)

@Serializable
data class PayloadSendWhatsApp(
    val toContactName: String,
    val message: String,
)

@Serializable
data class PayloadMakeCall(
    val toContactName: String,
)

@Serializable
data class PayloadOpenApp(
    val appName: String,
)

@Serializable
data class PayloadGetLocation(
    val unused: String? = null, // empty payload allowed
)

@Serializable
data class PayloadCaptureScreenshot(
    val includeForegroundApp: Boolean = true,
)

@Serializable
data class PayloadTakePhoto(
    val frontCamera: Boolean = false,
)

@Serializable
data class PayloadShowNotification(
    val title: String,
    val body: String,
)

@Serializable
data class PayloadSpeak(
    val text: String,
)

@Serializable
data class PayloadCreateTimer(
    val minutes: Int,
    val label: String? = null,
)

@Serializable
data class PayloadCreateAlarm(
    val minutes: Int,
    val label: String? = null,
)
