package ai.openclaw.jarvis.capabilities.impl

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.capabilities.base.*
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceCapability : Capability {
    suspend fun setTorch(on: Boolean): CapabilityResult<Unit>
    suspend fun setVolume(streamType: Int, direction: Int): CapabilityResult<Unit>
    suspend fun mute(mute: Boolean): CapabilityResult<Unit>
    fun getBatteryPercent(): Int
    fun getScreenState(): String
}

@Singleton
class DeviceCapabilityImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceCapability {

    override val id = "device"
    override val description = "Torch, volume, mute, battery, screen state"
    override val requiredPermissions: List<String> = emptyList()

    private val cameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun isAvailable() = true

    override suspend fun setTorch(on: Boolean): CapabilityResult<Unit> {
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return capabilityFailure("TORCH_UNAVAILABLE", "No flash hardware found")
            cameraManager.setTorchMode(cameraId, on)
            capabilitySuccess(Unit)
        } catch (e: Exception) {
            capabilityFailure("TORCH_ERROR", e.message ?: "Unknown torch error")
        }
    }

    override suspend fun setVolume(streamType: Int, direction: Int): CapabilityResult<Unit> {
        return try {
            audioManager.adjustStreamVolume(streamType, direction, AudioManager.FLAG_SHOW_UI)
            capabilitySuccess(Unit)
        } catch (e: Exception) {
            capabilityFailure("VOLUME_ERROR", e.message ?: "Unknown volume error")
        }
    }

    override suspend fun mute(mute: Boolean): CapabilityResult<Unit> {
        return try {
            val mode = if (mute) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
            audioManager.ringerMode = mode
            capabilitySuccess(Unit)
        } catch (e: Exception) {
            capabilityFailure("MUTE_ERROR", e.message ?: "Unknown mute error")
        }
    }

    override fun getBatteryPercent(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    override fun getScreenState(): String {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return if (pm.isInteractive) "on" else "off"
    }
}
