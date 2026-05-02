package ai.openclaw.jarvis.capabilities.impl

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.capabilities.base.*
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

interface ScreenshotCapability : Capability {
    fun buildMediaProjectionIntent(): Intent
    fun setProjectionResult(resultCode: Int, data: Intent)
    suspend fun captureScreen(): CapabilityResult<Bitmap>
    fun hasProjectionPermission(): Boolean
}

/**
 * Screenshot via MediaProjection.
 * The Activity must call buildMediaProjectionIntent(), wait for the result,
 * then call setProjectionResult() before captureScreen() works.
 */
@Singleton
class ScreenshotCapabilityImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ScreenshotCapability {

    override val id = "screenshot"
    override val description = "Screen capture via MediaProjection"
    override val requiredPermissions: List<String> = emptyList() // runtime prompt via MediaProjection

    private val projectionManager by lazy {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var mediaProjection: MediaProjection? = null
    private var projectionGranted = false

    // MediaProjection is available on all Android devices — no manifest permission
    // needed. The one-time user dialog fires when captureScreen() is first called.
    // hasProjectionPermission() tells callers whether the dialog has already been
    // accepted this session; isAvailable() must stay true so the capability is not
    // incorrectly reported as "missing permission" in the UI.
    override fun isAvailable() = true

    override fun buildMediaProjectionIntent(): Intent = projectionManager.createScreenCaptureIntent()

    override fun setProjectionResult(resultCode: Int, data: Intent) {
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        projectionGranted = mediaProjection != null
    }

    override fun hasProjectionPermission() = projectionGranted

    override suspend fun captureScreen(): CapabilityResult<Bitmap> {
        val mp = mediaProjection
            ?: return capabilityFailure("NO_PROJECTION", "MediaProjection not granted. Request screen capture permission first.")

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        return suspendCancellableCoroutine { cont ->
            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            var virtualDisplay: VirtualDisplay? = null

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    virtualDisplay?.release()
                    cont.resume(capabilitySuccess(cropped))
                } catch (e: Exception) {
                    virtualDisplay?.release()
                    cont.resume(capabilityFailure("CAPTURE_ERROR", e.message ?: "Capture failed"))
                } finally {
                    image.close()
                }
            }, Handler(Looper.getMainLooper()))

            virtualDisplay = mp.createVirtualDisplay(
                "JarvisCapture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface, null, null,
            )

            cont.invokeOnCancellation { virtualDisplay?.release() }
        }
    }
}
