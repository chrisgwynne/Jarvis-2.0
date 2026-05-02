package ai.openclaw.jarvis

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ai.openclaw.jarvis.capabilities.CapabilityRegistry
import ai.openclaw.jarvis.capabilities.impl.ScreenCaptureService
import ai.openclaw.jarvis.capture.CaptureOrchestrator
import ai.openclaw.jarvis.ui.navigation.JarvisNavGraph
import ai.openclaw.jarvis.ui.theme.JarvisTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.Lifecycle

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var captureOrchestrator: CaptureOrchestrator
    @Inject lateinit var caps: CapabilityRegistry

    // ─── Permission launcher ──────────────────────────────────────────────────

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Capabilities report availability dynamically; no crash on denial. */ }

    // ─── Camera launcher ──────────────────────────────────────────────────────
    // Launched with a custom ACTION_IMAGE_CAPTURE intent (supports selfie extra).
    // The camera app writes to the URI we provided in EXTRA_OUTPUT.

    private var pendingCameraRequest: CaptureOrchestrator.Request.Camera? = null
    private var pendingCameraUri: Uri? = null

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val req = pendingCameraRequest ?: return@registerForActivityResult
        pendingCameraRequest = null
        if (result.resultCode == Activity.RESULT_OK) {
            val label = if (req.selfie) "Selfie saved to your gallery." else "Photo saved to your gallery."
            req.deferred.complete(CaptureOrchestrator.Result(true, label))
        } else {
            // User cancelled — delete the empty placeholder URI we created
            pendingCameraUri?.let { contentResolver.delete(it, null, null) }
            req.deferred.complete(CaptureOrchestrator.Result(false, "Camera was cancelled."))
        }
        pendingCameraUri = null
    }

    // ─── MediaProjection permission launcher ──────────────────────────────────

    private var projectionPermissionDeferred: CompletableDeferred<Pair<Int, Intent>>? = null

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val deferred = projectionPermissionDeferred ?: return@registerForActivityResult
        projectionPermissionDeferred = null
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            deferred.complete(Pair(result.resultCode, result.data!!))
        } else {
            deferred.cancel()
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Allow the activity to appear above the lock screen for PTT.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        requestRuntimePermissions()
        observeCaptureRequests()

        setContent {
            JarvisTheme {
                JarvisNavGraph()
            }
        }
    }

    // ─── Runtime permissions ──────────────────────────────────────────────────

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.SEND_SMS,
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            perms += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    // ─── Capture request handling ─────────────────────────────────────────────

    private fun observeCaptureRequests() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                captureOrchestrator.requests.collect { req ->
                    when (req) {
                        is CaptureOrchestrator.Request.Camera     -> handleCameraRequest(req)
                        is CaptureOrchestrator.Request.Screenshot -> handleScreenshotRequest(req)
                    }
                }
            }
        }
    }

    // ─── Photo / selfie ───────────────────────────────────────────────────────

    private fun handleCameraRequest(req: CaptureOrchestrator.Request.Camera) {
        // Create a MediaStore entry so the camera app has a URI to write into.
        val filename = "jarvis_${if (req.selfie) "selfie" else "photo"}_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Jarvis")
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            req.deferred.complete(CaptureOrchestrator.Result(false, "Couldn't create file for photo."))
            return
        }

        val intentResult = if (req.selfie) caps.camera.buildTakeSelfieIntent(uri)
        else caps.camera.buildTakePhotoIntent(uri)

        when (intentResult) {
            is ai.openclaw.jarvis.capabilities.base.CapabilityResult.Success -> {
                pendingCameraRequest = req
                pendingCameraUri = uri
                cameraLauncher.launch(intentResult.value)
            }
            is ai.openclaw.jarvis.capabilities.base.CapabilityResult.Failure -> {
                contentResolver.delete(uri, null, null)
                req.deferred.complete(
                    CaptureOrchestrator.Result(false, intentResult.error.message)
                )
            }
        }
    }

    // ─── Screenshot ───────────────────────────────────────────────────────────

    private fun handleScreenshotRequest(req: CaptureOrchestrator.Request.Screenshot) {
        lifecycleScope.launch {
            // 1. Ensure the user has granted MediaProjection permission.
            if (!ensureProjectionPermission()) {
                req.deferred.complete(
                    CaptureOrchestrator.Result(false, "Screen capture permission was denied.")
                )
                return@launch
            }

            // 2. Start the required foreground service (Android 10+ / 14+ requirement).
            startForegroundService(Intent(this@MainActivity, ScreenCaptureService::class.java))
            // Small yield — lets the service's onStartCommand post startForeground() before
            // we create the VirtualDisplay, satisfying the 5-second ANR window.
            delay(250)

            // 3. Capture.
            when (val capture = caps.screenshot.captureScreen()) {
                is ai.openclaw.jarvis.capabilities.base.CapabilityResult.Success -> {
                    val uri = saveBitmapToMediaStore(capture.value)
                    capture.value.recycle()
                    if (uri != null) {
                        req.deferred.complete(
                            CaptureOrchestrator.Result(true, "Screenshot saved to your gallery.")
                        )
                    } else {
                        req.deferred.complete(
                            CaptureOrchestrator.Result(false, "Couldn't save the screenshot.")
                        )
                    }
                }
                is ai.openclaw.jarvis.capabilities.base.CapabilityResult.Failure -> {
                    req.deferred.complete(
                        CaptureOrchestrator.Result(false, capture.error.message)
                    )
                }
            }

            // 4. Stop the capture service — it's a one-shot operation.
            stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
        }
    }

    private suspend fun ensureProjectionPermission(): Boolean {
        if (caps.screenshot.hasProjectionPermission()) return true
        val deferred = CompletableDeferred<Pair<Int, Intent>>()
        projectionPermissionDeferred = deferred
        projectionLauncher.launch(caps.screenshot.buildMediaProjectionIntent())
        return try {
            val (code, data) = deferred.await()
            caps.screenshot.setProjectionResult(code, data)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun saveBitmapToMediaStore(bitmap: Bitmap): Uri? {
        val filename = "jarvis_screenshot_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Jarvis")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        return try {
            contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                contentResolver.update(uri, done, null, null)
            }
            uri
        } catch (_: Exception) {
            contentResolver.delete(uri, null, null)
            null
        }
    }
}
