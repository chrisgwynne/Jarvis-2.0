package ai.openclaw.jarvis.proactive

import ai.openclaw.jarvis.audio.AudioRouteManager
import ai.openclaw.jarvis.capabilities.CapabilityRegistry
import ai.openclaw.jarvis.data.models.GatewayState
import ai.openclaw.jarvis.network.OpenClawClient
import ai.openclaw.jarvis.proactive.model.BtDevice
import ai.openclaw.jarvis.proactive.model.ContextSnapshot
import ai.openclaw.jarvis.proactive.model.LocationLabel
import ai.openclaw.jarvis.proactive.model.Movement
import ai.openclaw.jarvis.proactive.model.NetworkState
import ai.openclaw.jarvis.protocol.DeviceContextBuilder
import ai.openclaw.jarvis.trust.TrustManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Builds [ContextSnapshot]s from existing app subsystems and emits them
 * on a fixed cadence + on-demand. The collector intentionally does NOT
 * derive signals or trigger anything — that's [SignalEngine]'s job.
 *
 * Recent commands / actions / screenshots are pushed in by other parts
 * of the app via [recordCommand] / [recordAction] / [recordScreenshot]
 * because they're not observable from a single source today.
 */
@Singleton
class ContextCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRouter: AudioRouteManager,
    private val openClawClient: OpenClawClient,
    private val caps: CapabilityRegistry,
    private val trustManager: TrustManager,
    private val deviceContextBuilder: DeviceContextBuilder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val recentCommands = ArrayDeque<String>()
    private val recentActions = ArrayDeque<String>()
    @Volatile private var recentScreenshotMillis: Long? = null
    @Volatile private var idleSinceMillis: Long? = null
    @Volatile private var lastInteractionMillis: Long = System.currentTimeMillis()

    private val _snapshots = MutableSharedFlow<ContextSnapshot>(extraBufferCapacity = 16)
    val snapshots: SharedFlow<ContextSnapshot> = _snapshots.asSharedFlow()

    private val _latest = MutableStateFlow(buildSnapshot())
    val latest: StateFlow<ContextSnapshot> = _latest.asStateFlow()

    /** Start the periodic snapshot loop. Idempotent. */
    fun start() {
        if (running) return
        running = true
        scope.launch {
            while (isActive) {
                emit()
                delay(SAMPLE_PERIOD_MS)
            }
        }
    }

    @Volatile private var running = false

    /** Force a fresh snapshot — called from state-machine transitions. */
    fun emit() {
        val snap = buildSnapshot()
        _latest.value = snap
        _snapshots.tryEmit(snap)
    }

    // ─── External hooks ─────────────────────────────────────────────────────

    fun recordCommand(text: String) {
        synchronized(recentCommands) {
            recentCommands.addLast(text)
            while (recentCommands.size > MAX_RECENT) recentCommands.removeFirst()
        }
        lastInteractionMillis = System.currentTimeMillis()
        idleSinceMillis = null
    }

    fun recordAction(actionType: String) {
        synchronized(recentActions) {
            recentActions.addLast(actionType)
            while (recentActions.size > MAX_RECENT) recentActions.removeFirst()
        }
        lastInteractionMillis = System.currentTimeMillis()
        idleSinceMillis = null
    }

    fun recordScreenshot(timestampMillis: Long = System.currentTimeMillis()) {
        recentScreenshotMillis = timestampMillis
    }

    fun markIdleSince(timestampMillis: Long) { idleSinceMillis = timestampMillis }

    fun recentCommandsSnapshot(): List<String> = synchronized(recentCommands) { recentCommands.toList() }
    fun recentActionsSnapshot(): List<String> = synchronized(recentActions) { recentActions.toList() }

    // ─── Build ──────────────────────────────────────────────────────────────

    private fun buildSnapshot(): ContextSnapshot {
        val cal = Calendar.getInstance()
        val hourOfDay = cal.get(Calendar.HOUR_OF_DAY)
        val dow = cal.get(Calendar.DAY_OF_WEEK).let { isoDayOfWeek(it) }

        val device = deviceContextBuilder.build()
        val locLabel = locationLabelFromDeviceLabel(device.locationLabel)
        val network = when (device.network) {
            "wifi" -> NetworkState.WIFI
            "mobile", "cellular" -> NetworkState.MOBILE
            "offline" -> NetworkState.OFFLINE
            else -> NetworkState.UNKNOWN
        }

        val battery = device.battery
        val charging = device.charging

        val audioState = audioRouter.state.value
        val bt = when {
            audioState.bluetoothScoConnected || audioState.bluetoothA2dpConnected -> BtDevice.OTHER
            else -> BtDevice.NONE
        }
        val headphones = audioState.wiredHeadsetConnected ||
            audioState.bluetoothA2dpConnected ||
            audioState.bluetoothScoConnected

        val now = System.currentTimeMillis()
        val idleSince = if (now - lastInteractionMillis > IDLE_THRESHOLD_MS) lastInteractionMillis else null

        return ContextSnapshot(
            timestampMillis = now,
            hourOfDay = hourOfDay,
            dayOfWeek = dow,
            locationLabel = locLabel,
            movement = movementFromAudio(bt, audioState.bluetoothA2dpConnected),
            bluetoothDevice = bt,
            headphonesConnected = headphones,
            foregroundApp = device.foregroundApp,
            recentCommands = recentCommandsSnapshot(),
            recentActions = recentActionsSnapshot(),
            recentScreenshotMillis = recentScreenshotMillis,
            nextCalendarEventMinutes = null, // Calendar-cap doesn't expose timing today; left for future wiring
            nextCalendarEventTitle = null,
            batteryPercent = battery,
            charging = charging,
            network = network,
            screenOn = device.screenState != "off",
            idleSinceMillis = idleSince,
            speakerTrustLevel = runCatching { trustManager.currentTrustLevel().name }.getOrDefault("UNKNOWN"),
        )
    }

    private fun locationLabelFromDeviceLabel(label: String): LocationLabel = when (label.lowercase()) {
        "home" -> LocationLabel.HOME
        "work" -> LocationLabel.WORK
        "away" -> LocationLabel.AWAY
        else -> LocationLabel.UNKNOWN
    }

    /** Crude heuristic: car BT route → DRIVING; else STATIONARY. Real motion
     *  classification is out of scope until we wire ActivityRecognition. */
    private fun movementFromAudio(bt: BtDevice, a2dp: Boolean): Movement {
        if (bt == BtDevice.CAR) return Movement.DRIVING
        if (a2dp) return Movement.WALKING
        return Movement.STATIONARY
    }

    private fun isoDayOfWeek(calendarDow: Int): Int = when (calendarDow) {
        Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
        else -> 7
    }

    companion object {
        private const val SAMPLE_PERIOD_MS = 30_000L     // 30s
        private const val MAX_RECENT = 20
        private const val IDLE_THRESHOLD_MS = 5L * 60 * 1000  // 5m without interaction
    }
}
