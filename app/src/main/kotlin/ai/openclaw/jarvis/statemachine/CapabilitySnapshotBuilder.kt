package ai.openclaw.jarvis.statemachine

import ai.openclaw.jarvis.audio.AudioRouteManager
import ai.openclaw.jarvis.capabilities.CapabilityRegistry
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CapabilitySnapshotBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: CapabilityRegistry,
    private val audioRouter: AudioRouteManager,
) {
    /** Build a map of capability_id → status string for inclusion in OpenClaw requests. */
    fun build(): Map<String, String> {
        val snapshot = mutableMapOf<String, String>()

        registry.all.forEach { cap ->
            snapshot[cap.id] = when {
                cap.isAvailable() -> "available"
                cap.requiredPermissions.any { perm ->
                    context.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED
                } -> "permission_missing"
                else -> "unavailable"
            }
        }

        // Audio routing context
        val audioState = audioRouter.state.value
        snapshot["audio.device"]         = audioRouter.activeDevice.name.lowercase()
        snapshot["audio.bluetooth_sco"]  = audioState.bluetoothScoConnected.toString()
        snapshot["audio.bluetooth_a2dp"] = audioState.bluetoothA2dpConnected.toString()
        snapshot["audio.wired_headset"]  = audioState.wiredHeadsetConnected.toString()

        return snapshot
    }
}
