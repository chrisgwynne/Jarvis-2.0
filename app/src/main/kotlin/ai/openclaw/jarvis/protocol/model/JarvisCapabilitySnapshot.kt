package ai.openclaw.jarvis.protocol.model

import kotlinx.serialization.Serializable

/**
 * Strongly-typed capability snapshot included with every [JarvisLiveRequest].
 *
 * OpenClaw uses this to decide whether to even ask Jarvis to do something.
 * A capability missing here means "do not request this action" — it is the
 * contract counterpart of [JarvisActionResult.PERMISSION_MISSING].
 *
 * All fields default to a "not available, no permission" stance so a
 * partially-populated snapshot can still be sent safely.
 */
@Serializable
data class JarvisCapabilitySnapshot(
    val sms: SimplePermissionCapability = SimplePermissionCapability(),
    val whatsapp: AppCapability = AppCapability(),
    val calls: SimplePermissionCapability = SimplePermissionCapability(),
    val location: LocationCapability = LocationCapability(),
    val camera: SimplePermissionCapability = SimplePermissionCapability(),
    val screenshot: ScreenshotCapability = ScreenshotCapability(),
    val contacts: SimplePermissionCapability = SimplePermissionCapability(),
    val bluetooth: BluetoothCapability = BluetoothCapability(),
)

/** Tri-state permission status. Mirrors the spec's "granted | missing | denied". */
@Serializable
enum class PermissionStatus { granted, missing, denied }

@Serializable
data class SimplePermissionCapability(
    val available: Boolean = false,
    val permission: PermissionStatus = PermissionStatus.missing,
)

@Serializable
data class AppCapability(
    val available: Boolean = false,
    val installed: Boolean = false,
)

@Serializable
data class LocationCapability(
    val available: Boolean = false,
    val permission: PermissionStatus = PermissionStatus.missing,
    val background: Boolean = false,
)

@Serializable
data class ScreenshotCapability(
    val available: Boolean = false,
    val requiresUserConsent: Boolean = true,
)

@Serializable
data class BluetoothCapability(
    val micConnected: Boolean = false,
    val outputConnected: Boolean = false,
    val deviceName: String? = null,
)
