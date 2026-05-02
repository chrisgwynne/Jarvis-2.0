package ai.openclaw.jarvis.githubissues.integration

import ai.openclaw.jarvis.audio.AudioRouteManager
import ai.openclaw.jarvis.githubissues.model.CapabilitySnapshot
import ai.openclaw.jarvis.githubissues.model.DeviceSnapshot
import ai.openclaw.jarvis.githubissues.model.ErrorSnapshot
import ai.openclaw.jarvis.githubissues.model.IssueContext
import ai.openclaw.jarvis.githubissues.model.SessionSnapshot
import ai.openclaw.jarvis.githubissues.model.StateSnapshot
import ai.openclaw.jarvis.network.OpenClawClient
import ai.openclaw.jarvis.data.models.GatewayState
import ai.openclaw.jarvis.statemachine.AssistantStateMachine
import ai.openclaw.jarvis.statemachine.CapabilitySnapshotBuilder
import ai.openclaw.jarvis.trust.TrustManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds an [IssueContext] from live app state. Wired with everything the
 * spec lists in `## State` / `## Device` / `## Capability snapshot` /
 * `## Session` so the rest of the issue-logging code can stay UI- and
 * subsystem-agnostic.
 */
@Singleton
class IssueContextBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateMachine: AssistantStateMachine,
    private val capabilityBuilder: CapabilitySnapshotBuilder,
    private val audioRouter: AudioRouteManager,
    private val openClawClient: OpenClawClient,
    private val trustManager: TrustManager,
) {
    /**
     * @param overrides callers can override individual fields (e.g. the route
     * the router decided on, or the previous state if you're capturing this at
     * the moment the state machine transitions).
     */
    fun build(
        previousState: String? = null,
        route: String? = null,
        intent: String? = null,
        commandId: String? = null,
        sessionId: String? = null,
        userCommand: String? = null,
        expectedBehaviour: String? = null,
        actualBehaviour: String? = null,
        error: ErrorSnapshot? = null,
    ): IssueContext = IssueContext(
        state = StateSnapshot(
            current = stateMachine.currentState.name,
            previous = previousState,
            route = route,
            intent = intent,
            speakerTrustLevel = runCatching { trustManager.currentTrustLevel().name }.getOrNull(),
            audioRoute = audioRouter.activeDevice.name.lowercase(),
            openClawConnected = openClawClient.gatewayState.value == GatewayState.CONNECTED,
        ),
        device = currentDevice(),
        capability = currentCapabilities(),
        session = SessionSnapshot(commandId = commandId, sessionId = sessionId),
        error = error,
        userCommand = userCommand,
        expectedBehaviour = expectedBehaviour,
        actualBehaviour = actualBehaviour,
    )

    private fun currentDevice(): DeviceSnapshot {
        val battery = runCatching {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val charging = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            "${if (level > 0 && scale > 0) "${(level * 100) / scale}%" else "?"}${if (charging) " charging" else ""}"
        }.getOrNull()

        val network = runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return@runCatching "offline"
            val caps = cm.getNetworkCapabilities(net) ?: return@runCatching "unknown"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
        }.getOrNull()

        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()

        return DeviceSnapshot(
            androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            appVersion = versionName,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            batteryState = battery,
            networkStatus = network,
        )
    }

    private fun currentCapabilities(): CapabilitySnapshot {
        val raw = runCatching { capabilityBuilder.build() }.getOrNull() ?: emptyMap()
        return CapabilitySnapshot(
            available = raw.mapValues { (_, status) -> status == "available" },
        )
    }
}
