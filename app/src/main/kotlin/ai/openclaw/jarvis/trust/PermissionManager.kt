package ai.openclaw.jarvis.trust

import ai.openclaw.jarvis.router.IntentType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionManager @Inject constructor() {

    fun requiredFor(intentType: IntentType): ActionPermission = when (intentType) {
        // SAFE — all speakers including unknown
        IntentType.CANCEL_STOP,
        IntentType.DEVICE_CONTROL,
        IntentType.APP_OPEN,
        IntentType.TIME_ACTION,
        IntentType.CAMERA_ACTION,
        IntentType.SCREEN_CAPTURE,
        IntentType.ENROL_VOICE,
        IntentType.RECORDING_START,
        IntentType.RECORDING_STOP -> ActionPermission.SAFE

        // LIMITED — TRUSTED or OWNER
        IntentType.COMMUNICATION_SEND,
        IntentType.COMMUNICATION_CALL,
        IntentType.SMS_READ,
        IntentType.CONTACT_LOOKUP -> ActionPermission.LIMITED

        // SAFE — reads that don't expose sensitive comms
        IntentType.CALENDAR_QUERY -> ActionPermission.SAFE

        // RESTRICTED — OWNER only
        IntentType.LOCATION_QUERY,
        IntentType.OPENCLAW_REQUEST,
        IntentType.MIXED_ACTION -> ActionPermission.RESTRICTED
    }

    fun isAllowed(permission: ActionPermission, trustLevel: TrustLevel): Boolean = when (permission) {
        ActionPermission.SAFE       -> true
        ActionPermission.LIMITED    -> trustLevel == TrustLevel.OWNER || trustLevel == TrustLevel.TRUSTED
        ActionPermission.RESTRICTED -> trustLevel == TrustLevel.OWNER
    }

    fun denialMessage(permission: ActionPermission, trustLevel: TrustLevel): String = when (permission) {
        ActionPermission.LIMITED    -> "That action requires a trusted account. Current level: ${trustLevel.name.lowercase()}."
        ActionPermission.RESTRICTED -> "That's a restricted action. Owner verification required."
        ActionPermission.SAFE       -> "Action not allowed."
    }
}
