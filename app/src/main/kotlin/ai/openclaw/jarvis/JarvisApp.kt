package ai.openclaw.jarvis

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import ai.openclaw.jarvis.capabilities.CapabilityRegistry
import ai.openclaw.jarvis.network.GatewayConnectionService
import ai.openclaw.jarvis.voice.AlwaysListeningService
import ai.openclaw.jarvis.githubissues.integration.IssueLoggingWiring
import ai.openclaw.jarvis.githubissues.queue.IssueQueueWorker
import ai.openclaw.jarvis.monitor.IncomingCallMonitor
import ai.openclaw.jarvis.network.NodeInvokeDispatcher
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
    @Inject lateinit var nodeInvokeDispatcher: NodeInvokeDispatcher
    @Inject lateinit var incomingCallMonitor: IncomingCallMonitor

    override fun onCreate() {
        // Must run before super.onCreate() so Hilt-injected singletons
        // (PairingStore) find the full BC provider rather than Android's
        // stripped version which lacks Ed25519.
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        super.onCreate()
        // Advertise capabilities to Gateway before connecting
        gatewayClient.advertisedCapabilities = capabilityRegistry.toAdvertisements()
        gatewayClient.connect()

        // Subscribe to OpenClaw `node.invoke` frames so we can answer back
        // with `node.invoke.result`. Without this the legacy invocation
        // channel is silently dropped on the floor.
        nodeInvokeDispatcher.start()

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

        // Announce incoming callers via TTS — requires READ_PHONE_STATE permission.
        incomingCallMonitor.start()

        // Wake word: start the always-listening service immediately so
        // "hey jarvis" works without the user needing to visit settings.
        // The service reads the setting on start and handles missing mic
        // permission gracefully (retries on ERROR_INSUFFICIENT_PERMISSIONS).
        try { AlwaysListeningService.start(this) } catch (_: Exception) {}

        // Gateway connection keepalive: foreground service that holds a Wi-Fi
        // lock and registers a network callback so the WebSocket stays connected
        // through phone lock, Doze, and brief network blips.
        GatewayConnectionService.start(this)
    }
}
