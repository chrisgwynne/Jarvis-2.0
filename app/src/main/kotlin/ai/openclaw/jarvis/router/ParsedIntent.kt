package ai.openclaw.jarvis.router

import ai.openclaw.jarvis.data.models.RouteChoice
import ai.openclaw.jarvis.data.models.RouteDecision

/**
 * Rich result of parsing a user utterance.
 * Replaces the old string-keyed IntentMatch.
 */
data class ParsedIntent(
    val type: IntentType,
    val confidence: Float,
    val rawText: String,

    // Extracted entities
    val contact: String? = null,
    val messageBody: String? = null,
    val appName: String? = null,
    val durationMinutes: Int? = null,
    val channel: MessageChannel? = null,
    val deviceAction: DeviceControlAction? = null,
    val cameraAction: CameraSubAction? = null,
    val timeAction: TimeSubAction? = null,
    val alarmLabel: String? = null,
)

/** Map to the legacy RouteDecision shape for backward compat with SessionEventLogger. */
fun ParsedIntent.toRouteDecision(): RouteDecision = RouteDecision(
    chosen     = when (type) {
        IntentType.CANCEL_STOP,
        IntentType.DEVICE_CONTROL,
        IntentType.APP_OPEN,
        IntentType.LOCATION_QUERY,
        IntentType.CAMERA_ACTION,
        IntentType.TIME_ACTION,
        IntentType.COMMUNICATION_SEND,
        IntentType.COMMUNICATION_CALL -> RouteChoice.ANDROID_LOCAL
        IntentType.OPENCLAW_REQUEST   -> RouteChoice.OPENCLAW
        IntentType.SCREEN_CAPTURE,
        IntentType.MIXED_ACTION       -> RouteChoice.MIXED
    },
    intent     = type.name.lowercase(),
    confidence = confidence,
)

/** Convert to the string intent key the old AndroidActionExecutor.execute() expects. */
fun ParsedIntent.toLegacyIntent(): String = when (type) {
    IntentType.CANCEL_STOP        -> "stop"
    IntentType.DEVICE_CONTROL     -> when (deviceAction) {
        DeviceControlAction.TORCH_ON           -> "torch_on"
        DeviceControlAction.TORCH_OFF          -> "torch_off"
        DeviceControlAction.VOLUME_UP          -> "volume_up"
        DeviceControlAction.VOLUME_DOWN        -> "volume_down"
        DeviceControlAction.MUTE               -> "mute"
        DeviceControlAction.UNMUTE             -> "unmute"
        DeviceControlAction.MEDIA_PLAY_PAUSE   -> "media_control"
        DeviceControlAction.MEDIA_NEXT         -> "media_control"
        DeviceControlAction.MEDIA_PREVIOUS     -> "media_control"
        DeviceControlAction.MEDIA_STOP         -> "media_control"
        null                                   -> "device_control"
    }
    IntentType.APP_OPEN           -> "open_app"
    IntentType.LOCATION_QUERY     -> "location_query"
    IntentType.CAMERA_ACTION      -> when (cameraAction) {
        CameraSubAction.SELFIE -> "take_selfie"
        else                   -> "take_photo"
    }
    IntentType.SCREEN_CAPTURE     -> "screenshot"
    IntentType.TIME_ACTION        -> "set_timer_alarm"
    IntentType.COMMUNICATION_SEND -> "send_message"
    IntentType.COMMUNICATION_CALL -> "call"
    IntentType.OPENCLAW_REQUEST   -> "openclaw_request"
    IntentType.MIXED_ACTION       -> "mixed_action"
}

/** Build the params map the old AndroidActionExecutor.execute() expects. */
fun ParsedIntent.toLegacyParams(): Map<String, String> = buildMap {
    contact?.let         { put("contact", it) }
    messageBody?.let     { put("message", it) }
    appName?.let         { put("app", it) }
    durationMinutes?.let { put("minutes", it.toString()) }
    alarmLabel?.let      { put("label", it) }
    channel?.let         { put("channel", it.name.lowercase()) }
    deviceAction?.let    { put("action", it.name.lowercase()) }
}
