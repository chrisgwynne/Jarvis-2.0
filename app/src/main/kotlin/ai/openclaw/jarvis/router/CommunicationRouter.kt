package ai.openclaw.jarvis.router

import ai.openclaw.jarvis.data.models.RouteChoice
import javax.inject.Inject
import javax.inject.Singleton

data class CommunicationRoute(
    val chosen: RouteChoice,
    val resolvedChannel: MessageChannel,
    val reason: String,
)

/**
 * Decides the final route for COMMUNICATION_SEND intents.
 *
 * Rules:
 *   EMAIL  → OPENCLAW  (email goes through the OpenClaw email skill)
 *   SMS    → ANDROID_LOCAL
 *   WHATSAPP → ANDROID_LOCAL (WhatsApp intent-based)
 *   BEST_AVAILABLE / null → ANDROID_LOCAL (SMS or WhatsApp, checked at execute time)
 *
 * COMMUNICATION_CALL always routes ANDROID_LOCAL.
 */
@Singleton
class CommunicationRouter @Inject constructor() {

    fun routeSend(intent: ParsedIntent): CommunicationRoute {
        return when (intent.channel) {
            MessageChannel.EMAIL -> CommunicationRoute(
                chosen          = RouteChoice.OPENCLAW,
                resolvedChannel = MessageChannel.EMAIL,
                reason          = "Email is handled by OpenClaw email skill",
            )
            MessageChannel.WHATSAPP -> CommunicationRoute(
                chosen          = RouteChoice.ANDROID_LOCAL,
                resolvedChannel = MessageChannel.WHATSAPP,
                reason          = "WhatsApp sent via Android intent",
            )
            MessageChannel.SMS -> CommunicationRoute(
                chosen          = RouteChoice.ANDROID_LOCAL,
                resolvedChannel = MessageChannel.SMS,
                reason          = "SMS sent via Android SmsManager",
            )
            MessageChannel.BEST_AVAILABLE, null -> CommunicationRoute(
                chosen          = RouteChoice.ANDROID_LOCAL,
                resolvedChannel = MessageChannel.BEST_AVAILABLE,
                reason          = "Best available channel: SMS or WhatsApp",
            )
        }
    }

    fun routeCall(intent: ParsedIntent): CommunicationRoute = CommunicationRoute(
        chosen          = RouteChoice.ANDROID_LOCAL,
        resolvedChannel = MessageChannel.BEST_AVAILABLE,
        reason          = "Phone call via Android telephony",
    )
}
