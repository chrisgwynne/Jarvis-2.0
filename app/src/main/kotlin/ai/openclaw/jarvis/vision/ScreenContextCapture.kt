package ai.openclaw.jarvis.vision

import ai.openclaw.jarvis.capabilities.base.CapabilityResult
import ai.openclaw.jarvis.capabilities.impl.ScreenshotCapabilityImpl
import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures the current screen as a base64-encoded JPEG and attaches it to
 * outbound chat.send frames, giving the AI model visual context about what
 * the user is seeing. Requires MediaProjection permission to have been
 * granted at least once this session (done automatically when the user
 * first takes a screenshot via the screenshot capability).
 */
@Singleton
class ScreenContextCapture @Inject constructor(
    private val screenshot: ScreenshotCapabilityImpl,
) {
    suspend fun captureBase64Jpeg(quality: Int = 50): String? {
        if (!screenshot.hasProjectionPermission()) return null
        return when (val result = screenshot.captureScreen()) {
            is CapabilityResult.Success -> {
                val bmp = result.value
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bmp.recycle()
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }
            is CapabilityResult.Failure -> null
        }
    }
}
