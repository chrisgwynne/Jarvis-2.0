package ai.openclaw.jarvis

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import ai.openclaw.jarvis.capabilities.CapabilityRegistry
import ai.openclaw.jarvis.githubissues.integration.IssueLoggingWiring
import ai.openclaw.jarvis.githubissues.queue.IssueQueueWorker
import ai.openclaw.jarvis.network.OpenClawClient
import ai.openclaw.jarvis.policy.ApprovalCoordinator
import ai.openclaw.jarvis.proactive.ContextCollector
import ai.openclaw.jarvis.proactive.SuggestionManager
import ai.openclaw.jarvis.screen.ForegroundAppTracker
import ai.openclaw.jarvis.screen.PassiveAssistManager
import ai.openclaw.jarvis.screen.ScreenshotObserver
import ai.openclaw.jarvis.screen.integration.ScreenContextLogger
import javax.inject.Inject

@HiltAndroidApp
class JarvisApp : Application() {

    @Inject lateinit var gatewayClient: OpenClawClient
    @Inject lateinit var capabilityRegistry: CapabilityRegistry
    @Inject lateinit var issueQueueWorker: IssueQueueWorker
    @Inject lateinit var issueLoggingWiring: IssueLoggingWiring
    @Inject lateinit var suggestionManager: SuggestionManager
    @Inject lateinit var contextCollector: ContextCollector
    @Inject lateinit var foregroundAppTracker: ForegroundAppTracker
    @Inject lateinit var screenshotObserver: ScreenshotObserver
    @Inject lateinit var passiveAssistManager: PassiveAssistManager
    @Inject lateinit var screenContextLogger: ScreenContextLogger
    @Inject lateinit var approvalCoordinator: ApprovalCoordinator

    override fun onCreate() {
        super.onCreate()
        // Advertise capabilities to Gateway before connecting
        gatewayClient.advertisedCapabilities = capabilityRegistry.toAdvertisements()
        gatewayClient.connect()

        // GitHub Issue Logging: subscribe to ERROR_RECOVERY transitions
        // and start draining any issues queued while offline.
        issueLoggingWiring.start()
        issueQueueWorker.start()

        // Proactive context awareness: starts the snapshot loop and
        // lets the suggestion manager subscribe to it.
        suggestionManager.start(contextCollector)

        // Screen awareness — every component is a no-op when the user has
        // disabled screen-awareness in settings, so it's safe to call
        // .start() unconditionally here.
        foregroundAppTracker.start()
        screenshotObserver.start()
        passiveAssistManager.start()
        screenContextLogger.start()

        // Autonomy / approval engine: starts the expiry pruner so any
        // approvals saved on disk that have already lapsed are surfaced
        // through the audit log on the first poll.
        approvalCoordinator.start()
    }
}
