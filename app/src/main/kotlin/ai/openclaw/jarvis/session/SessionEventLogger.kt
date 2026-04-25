package ai.openclaw.jarvis.session

import android.os.Build
import ai.openclaw.jarvis.capabilities.impl.LocationCapabilityImpl
import ai.openclaw.jarvis.capabilities.impl.DeviceCapabilityImpl
import ai.openclaw.jarvis.data.local.JarvisSettings
import ai.openclaw.jarvis.data.local.OfflineQueueStore
import ai.openclaw.jarvis.data.models.*
import ai.openclaw.jarvis.executor.ActionOutcome
import ai.openclaw.jarvis.network.OpenClawClient
import ai.openclaw.jarvis.router.LocalIntentRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logs every voice interaction to OpenClaw.
 *
 * Rule: routing decides who acts. Logging ALWAYS goes to OpenClaw.
 * If the Gateway is offline, events are queued locally and flushed on reconnect.
 */
@Singleton
class SessionEventLogger @Inject constructor(
    private val client: OpenClawClient,
    private val offlineQueue: OfflineQueueStore,
    private val memoryDetector: MemoryCandidateDetector,
    private val deviceCap: DeviceCapabilityImpl,
    private val locationCap: LocationCapabilityImpl,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Map of eventId → pending event (waiting for assistant reply)
    private val pending = mutableMapOf<String, SessionEvent>()

    init {
        // Observe gateway connection; flush queue when we go CONNECTED
        scope.launch {
            client.gatewayState
                .onEach { state ->
                    if (state == GatewayState.CONNECTED) flushQueue()
                }
                .launchIn(this)
        }
    }

    // ─── Called after a local Android action completes ────────────────────────

    suspend fun log(
        userText: String,
        decision: RouteDecision,
        outcome: ActionOutcome,
        prefs: JarvisSettings,
    ) {
        val event = buildEvent(
            eventId      = UUID.randomUUID().toString(),
            userText     = userText,
            decision     = decision,
            prefs        = prefs,
            status       = if (outcome.success) "success" else "error",
            spokenReply  = outcome.spokenReply,
            error        = outcome.error,
        )
        dispatch(event)
    }

    // ─── Called before waiting for an OpenClaw reply ──────────────────────────

    suspend fun logPending(
        userText: String,
        decision: RouteDecision,
        eventId: String,
        prefs: JarvisSettings,
    ) {
        val event = buildEvent(
            eventId     = eventId,
            userText    = userText,
            decision    = decision,
            prefs       = prefs,
            status      = "pending",
            spokenReply = "",
        )
        pending[eventId] = event
    }

    suspend fun completePending(eventId: String, spokenReply: String) {
        val base = pending.remove(eventId) ?: return
        val completed = base.copy(
            result = base.result.copy(status = "success", spokenReply = spokenReply)
        )
        dispatch(completed)
    }

    // ─── Called when OpenClaw is offline ─────────────────────────────────────

    suspend fun logOffline(
        userText: String,
        decision: RouteDecision,
        prefs: JarvisSettings,
    ) {
        val event = buildEvent(
            eventId     = UUID.randomUUID().toString(),
            userText    = userText,
            decision    = decision,
            prefs       = prefs,
            status      = "queued",
            spokenReply = "",
        )
        offlineQueue.enqueue(event)
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private suspend fun dispatch(event: SessionEvent) {
        if (client.isConnected()) {
            client.sendSessionEvent(event)
        } else {
            offlineQueue.enqueue(event)
        }
    }

    private suspend fun flushQueue() {
        val queued = offlineQueue.dequeueAll()
        queued.forEach { q -> client.sendSessionEvent(q.event) }
    }

    private fun buildEvent(
        eventId: String,
        userText: String,
        decision: RouteDecision,
        prefs: JarvisSettings,
        status: String,
        spokenReply: String,
        error: String? = null,
    ): SessionEvent {
        return SessionEvent(
            eventId    = eventId,
            sessionKey = prefs.sessionKey,
            timestamp  = Instant.now().toString(),
            speaker    = prefs.speakerName,
            input      = SessionInput(mode = "voice", text = userText),
            route      = decision,
            androidContext = buildAndroidContext(prefs),
            result = SessionResult(
                status      = status,
                spokenReply = spokenReply,
                error       = error,
            ),
            memoryCandidate = memoryDetector.isMemoryCandidate(userText),
        )
    }

    private fun buildAndroidContext(prefs: JarvisSettings): AndroidContext {
        val battery  = deviceCap.getBatteryPercent().let { if (it >= 0) "$it%" else "unknown" }
        val screen   = deviceCap.getScreenState()
        val location = if (prefs.sendLocationContext) locationCap.getLocationLabel() else "disabled"
        return AndroidContext(
            device         = "${Build.MANUFACTURER} ${Build.MODEL}",
            battery        = battery,
            screenState    = screen,
            foregroundApp  = "jarvis",
            locationLabel  = location,
        )
    }
}
