package ai.openclaw.jarvis

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import ai.openclaw.jarvis.capabilities.CapabilityRegistry
import ai.openclaw.jarvis.network.OpenClawClient
import javax.inject.Inject

@HiltAndroidApp
class JarvisApp : Application() {

    @Inject lateinit var gatewayClient: OpenClawClient
    @Inject lateinit var capabilityRegistry: CapabilityRegistry

    override fun onCreate() {
        super.onCreate()
        // Advertise capabilities to Gateway before connecting
        gatewayClient.advertisedCapabilities = capabilityRegistry.toAdvertisements()
        gatewayClient.connect()
    }
}
