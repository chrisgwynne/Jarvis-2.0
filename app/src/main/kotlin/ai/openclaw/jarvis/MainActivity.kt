package ai.openclaw.jarvis

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import ai.openclaw.jarvis.capabilities.CapabilityRegistry
import ai.openclaw.jarvis.capabilities.impl.ScreenshotCapabilityImpl
import ai.openclaw.jarvis.ui.navigation.JarvisNavGraph
import ai.openclaw.jarvis.ui.theme.JarvisTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var capabilityRegistry: CapabilityRegistry

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Capabilities report availability dynamically; no crash on denial. */ }

    // Screenshot: request MediaProjection and wire result into ScreenshotCapabilityImpl
    private val screenshotProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val screenshotCap = capabilityRegistry.byId("screenshot") as? ScreenshotCapabilityImpl
        when {
            result.resultCode == RESULT_OK && result.data != null -> {
                screenshotCap?.setProjectionResult(result.resultCode, result.data!!)
            }
            else -> { /* user cancelled — capability stays unavailable */ }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestRuntimePermissions()

        setContent {
            JarvisTheme {
                JarvisNavGraph(
                    onScreenshotProjectionRequest = { intent ->
                        screenshotProjectionLauncher.launch(intent)
                    }
                )
            }
        }
    }

    private fun requestRuntimePermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CAMERA,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        )
    }
}
