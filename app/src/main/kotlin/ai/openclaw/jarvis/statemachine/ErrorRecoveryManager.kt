package ai.openclaw.jarvis.statemachine

import javax.inject.Inject
import javax.inject.Singleton

enum class ErrorCode {
    GATEWAY_OFFLINE,
    BLUETOOTH_DISCONNECTED,
    STT_FAILURE,
    TTS_FAILURE,
    PERMISSION_MISSING,
    CONTACT_AMBIGUOUS,
    WHATSAPP_NOT_INSTALLED,
    MALFORMED_RESPONSE,
    IDENTITY_LOW_CONFIDENCE,
    SESSION_EXPIRED,
    UNKNOWN,
}

data class RecoveryStrategy(
    val userMessage: String,
    val shouldSpeak: Boolean = true,
    val shouldRetry: Boolean = false,
    val fallbackAction: FallbackAction = FallbackAction.RETURN_TO_IDLE,
)

enum class FallbackAction {
    RETURN_TO_IDLE,
    RETRY_STT,
    QUEUE_OFFLINE,
    REQUEST_PERMISSION,
    SPEAK_ERROR,
}

@Singleton
class ErrorRecoveryManager @Inject constructor() {

    fun strategyFor(code: ErrorCode, detail: String? = null): RecoveryStrategy = when (code) {
        ErrorCode.GATEWAY_OFFLINE -> RecoveryStrategy(
            userMessage    = "OpenClaw is offline. Your message has been queued.",
            fallbackAction = FallbackAction.QUEUE_OFFLINE,
        )
        ErrorCode.BLUETOOTH_DISCONNECTED -> RecoveryStrategy(
            userMessage    = "Bluetooth headset disconnected. Switching to phone speaker.",
            fallbackAction = FallbackAction.RETURN_TO_IDLE,
        )
        ErrorCode.STT_FAILURE -> RecoveryStrategy(
            userMessage    = "I didn't catch that. Please try again.",
            shouldRetry    = true,
            fallbackAction = FallbackAction.RETRY_STT,
        )
        ErrorCode.TTS_FAILURE -> RecoveryStrategy(
            userMessage    = "Speech output failed.",
            shouldSpeak    = false,
            fallbackAction = FallbackAction.RETURN_TO_IDLE,
        )
        ErrorCode.PERMISSION_MISSING -> RecoveryStrategy(
            userMessage    = detail ?: "A required permission is missing. Please check settings.",
            fallbackAction = FallbackAction.REQUEST_PERMISSION,
        )
        ErrorCode.CONTACT_AMBIGUOUS -> RecoveryStrategy(
            userMessage    = detail ?: "I found multiple contacts with that name. Please be more specific.",
            fallbackAction = FallbackAction.RETURN_TO_IDLE,
        )
        ErrorCode.WHATSAPP_NOT_INSTALLED -> RecoveryStrategy(
            userMessage    = "WhatsApp is not installed. Sending via SMS instead.",
            fallbackAction = FallbackAction.RETURN_TO_IDLE,
        )
        ErrorCode.MALFORMED_RESPONSE -> RecoveryStrategy(
            userMessage    = "I received an unexpected response. Please try again.",
            fallbackAction = FallbackAction.RETURN_TO_IDLE,
        )
        ErrorCode.IDENTITY_LOW_CONFIDENCE -> RecoveryStrategy(
            userMessage    = "I couldn't verify your identity. Some actions may be restricted.",
            shouldSpeak    = false,
            fallbackAction = FallbackAction.RETURN_TO_IDLE,
        )
        ErrorCode.SESSION_EXPIRED -> RecoveryStrategy(
            userMessage    = "Your session has expired. Please say your name to continue.",
            fallbackAction = FallbackAction.RETURN_TO_IDLE,
        )
        ErrorCode.UNKNOWN -> RecoveryStrategy(
            userMessage    = detail ?: "Something went wrong. Please try again.",
            fallbackAction = FallbackAction.RETURN_TO_IDLE,
        )
    }

    fun errorCodeFromSttCode(sttErrorCode: Int): ErrorCode = when (sttErrorCode) {
        1, 2  -> ErrorCode.STT_FAILURE   // NETWORK_TIMEOUT, NETWORK
        3     -> ErrorCode.GATEWAY_OFFLINE
        5, 6  -> ErrorCode.STT_FAILURE   // NO_MATCH, SPEECH_TIMEOUT
        7, 8  -> ErrorCode.STT_FAILURE   // RECOGNIZER_BUSY, INSUFFICIENT_PERMISSIONS
        else  -> ErrorCode.STT_FAILURE
    }
}
