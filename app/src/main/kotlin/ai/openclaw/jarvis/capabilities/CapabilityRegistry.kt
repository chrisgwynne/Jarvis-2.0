package ai.openclaw.jarvis.capabilities

import ai.openclaw.jarvis.capabilities.base.Capability
import ai.openclaw.jarvis.capabilities.impl.*
import ai.openclaw.jarvis.data.models.NodeCapabilityAd
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CapabilityRegistry @Inject constructor(
    val device: DeviceCapabilityImpl,
    val location: LocationCapabilityImpl,
    val camera: CameraCapabilityImpl,
    val screenshot: ScreenshotCapabilityImpl,
    val contacts: ContactsCapabilityImpl,
    val calendar: CalendarCapabilityImpl,
    val sms: SmsCapabilityImpl,
    val calls: CallsCapabilityImpl,
    val apps: AppsCapabilityImpl,
    val media: MediaCapabilityImpl,
    val notification: NotificationCapabilityImpl,
    val whatsApp: WhatsAppCapabilityImpl,
) {
    val all: List<Capability> = listOf(
        device, location, camera, screenshot,
        contacts, calendar, sms, calls, apps, media, notification, whatsApp,
    )

    fun toAdvertisements(): List<NodeCapabilityAd> = all.map { cap ->
        NodeCapabilityAd(
            id                 = cap.id,
            description        = cap.description,
            available          = cap.isAvailable(),
            requiresPermission = cap.requiredPermissions.isNotEmpty(),
        )
    }

    fun byId(id: String): Capability? = all.firstOrNull { it.id == id }
}
