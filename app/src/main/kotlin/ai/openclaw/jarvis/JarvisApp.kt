package ai.openclaw.jarvis

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import ai.openclaw.jarvis.capabilities.CapabilityRegistry
import ai.openclaw.jarvis.githubissues.integration.IssueLoggingWiring
import ai.openclaw.jarvis.githubissues.queue.IssueQueueWorker
import ai.openclaw.jarvis.network.OpenClawClient
import javax.inject.Inject

@HiltAndroidApp
class JarvisApp : Application() {

    @Inject lateinit var gatewayClient: OpenClawClient
    @Inject lateinit var capabilityRegistry: CapabilityRegistry
    @Inject lateinit var issueQueueWorker: IssueQueueWorker
    @Inject lateinit var issueLoggingWiring: IssueLoggingWiring

    override fun onCreate() {
        super.onCreate()
        // Advertise capabilities to Gateway before connecting
        gatewayClient.advertisedCapabilities = capabilityRegistry.toAdvertisements()
        gatewayClient.connect()

        // GitHub Issue Logging: subscribe to ERROR_RECOVERY transitions
        // and start draining any issues queued while offline.
        issueLoggingWiring.start()
        issueQueueWorker.start()
    }
}
