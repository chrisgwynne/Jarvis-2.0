package ai.openclaw.jarvis.protocol

import ai.openclaw.jarvis.audio.AudioRouteManager
import ai.openclaw.jarvis.capabilities.CapabilityRegistry
import ai.openclaw.jarvis.capabilities.base.Capability
import ai.openclaw.jarvis.capabilities.impl.WhatsAppCapabilityImpl
import ai.openclaw.jarvis.protocol.model.AppCapability
import ai.openclaw.jarvis.protocol.model.BluetoothCapability
import ai.openclaw.jarvis.protocol.model.JarvisCapabilitySnapshot
import ai.openclaw.jarvis.protocol.model.LocationCapability
import ai.openclaw.jarvis.protocol.model.PermissionStatus
import ai.openclaw.jarvis.protocol.model.ScreenshotCapability
import ai.openclaw.jarvis.protocol.model.SimplePermissionCapability
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the strict [JarvisCapabilitySnapshot] sent on every [JarvisLiveRequest].
 *
 * Replaces the loose `Map<String, String>` snapshot — but does NOT remove
 * the legacy [ai.openclaw.jarvis.statemachine.CapabilitySnapshotBuilder],
 * which other parts of the app still consume for their own purposes.
 */
@Singleton
class TypedCapabilitySnapshotBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: CapabilityRegistry,
    private val audioRouter: AudioRouteManager,
    private val whatsApp: WhatsAppCapabilityImpl,
) {
    fun build(): JarvisCapabilitySnapshot {
        val audioState = audioRouter.state.value

        return JarvisCapabilitySnapshot(
            sms = simple(registry.sms),
            whatsapp = AppCapability(
                available = whatsApp.isAvailable(),
                installed = whatsApp.isWhatsAppInstalled(),
            ),
            calls = simple(registry.calls),
            location = LocationCapability(
                available = registry.location.isAvailable(),
                permission = permissionStatus(registry.location),
                background = registry.location.isAvailable(),
            ),
            camera = simple(registry.camera),
            screenshot = ScreenshotCapability(
                available = registry.screenshot.isAvailable(),
                requiresUserConsent = true,
            ),
            contacts = simple(registry.contacts),
            bluetooth = BluetoothCapability(
                micConnected = audioState.bluetoothScoConnected,
                outputConnected = audioState.bluetoothA2dpConnected || audioState.bluetoothScoConnected,
                deviceName = null,
            ),
        )
    }

    private fun simple(cap: Capability) = SimplePermissionCapability(
        available = cap.isAvailable(),
        permission = permissionStatus(cap),
    )

    private fun permissionStatus(cap: Capability): PermissionStatus {
        if (cap.requiredPermissions.isEmpty()) return PermissionStatus.granted
        val anyMissing = cap.requiredPermissions.any { perm ->
            context.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED
        }
        return if (anyMissing) PermissionStatus.missing else PermissionStatus.granted
    }
}
