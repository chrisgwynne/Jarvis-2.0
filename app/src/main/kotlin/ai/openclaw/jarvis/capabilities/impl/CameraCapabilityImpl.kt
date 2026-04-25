package ai.openclaw.jarvis.capabilities.impl

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.capabilities.base.*
import javax.inject.Inject
import javax.inject.Singleton

interface CameraCapability : Capability {
    fun buildTakePhotoIntent(outputUri: Uri): CapabilityResult<Intent>
    fun buildTakeSelfieIntent(outputUri: Uri): CapabilityResult<Intent>
}

@Singleton
class CameraCapabilityImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : CameraCapability {

    override val id = "camera"
    override val description = "Capture photo or selfie via system camera"
    override val requiredPermissions = listOf(Manifest.permission.CAMERA)

    override fun isAvailable(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun buildTakePhotoIntent(outputUri: Uri): CapabilityResult<Intent> {
        if (!isAvailable()) {
            return capabilityFailure("CAMERA_UNAVAILABLE", "Camera permission not granted", true)
        }
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        return capabilitySuccess(intent)
    }

    override fun buildTakeSelfieIntent(outputUri: Uri): CapabilityResult<Intent> {
        if (!isAvailable()) {
            return capabilityFailure("CAMERA_UNAVAILABLE", "Camera permission not granted", true)
        }
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
            putExtra("android.intent.extras.CAMERA_FACING",
                android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        return capabilitySuccess(intent)
    }
}
