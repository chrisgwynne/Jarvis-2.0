package ai.openclaw.jarvis.session

import android.os.Build
import ai.openclaw.jarvis.capabilities.impl.LocationCapabilityImpl
import ai.openclaw.jarvis.capabilities.impl.DeviceCapabilityImpl
import ai.openclaw.jarvis.data.local.JarvisSettings
import ai.openclaw.jarvis.data.local.OfflineQueueStore
import ai.openclaw.jarvis.data.models.*
import ai.openclaw.jarvis.executor.ActionOutcome
import ai.openclaw.jarvis.network.OpenClawClient
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private sealed class LogWork {
    data class Dispatch(val event: SessionEvent) : LogWork()
    data class Offline(val event: SessionEvent)  : LogWork()
    object FlushQueue                             : LogWork()
}

/**
 * Non-blocking session event logger.
 *
 * All log calls are fire-and-forget from the caller's perspective.
 * A background Channel drains the work queue on the IO dispatcher.
 *
 * Rule: routing decides who acts. Logging ALWAYS goes to OpenClaw.
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
    private val workChannel = Channel<LogWork>(capacity = 128)

    // Pending OpenClaw events (waiting for assistant reply to complete them)
    private val pending = ConcurrentHashMap<String, SessionEvent>()

    init {
        // Drain work channel
        scope.launch {
            for (work in workChannel) {
                when (work) {
                    is LogWork.Dispatch   -> doDispatch(work.event)
                    is LogWork.Offline    -> offlineQueue.enqueue(work.event)
                    is LogWork.FlushQueue -> doFlushQueue()
                }
            }
        }
        // Flush offline queue on gateway reconnect
        scope.launch {
            client.gatewayState
                .onEach { if (it == GatewayState.CONNECTED) workChannel.trySend(LogWork.FlushQueue) }
                .launchIn(this)
        }
    }

    // ─── Public API (all non-blocking from caller) ────────────────────────────

    /** Log a completed local Android action. Fire-and-forget. */
    fun log(
        userText: String,
        decision: RouteDecision,
        outcome: ActionOutcome,
        prefs: JarvisSettings,
    ) {
        val event = buildEvent(
            eventId     = UUID.randomUUID().toString(),
            userText    = userText,
            decision    = decision,
            prefs       = prefs,
            status      = if (outcome.success) "success" else "error",
            spokenReply = outcome.spokenReply,
            error       = outcome.error,
        )
        workChannel.trySend(LogWork.Dispatch(event))
    }

    /** Store a pending event before OpenClaw replies. Non-blocking. */
    fun logPending(
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

    /** Complete a pending event with the assistant's reply. Non-blocking. */
    fun completePending(eventId: String, spokenReply: String) {
        val base = pending.remove(eventId) ?: return
        val completed = base.copy(
            result = base.result.copy(status = "success", spokenReply = spokenReply)
        )
        workChannel.trySend(LogWork.Dispatch(completed))
    }

    /** Queue an event when offline. Non-blocking. */
    fun logOffline(
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
        workChannel.trySend(LogWork.Offline(event))
    }

    // ─── Backward-compat suspend overloads ───────────────────────────────────

    suspend fun log(userText: String, decision: RouteDecision, outcome: ActionOutcome, prefs: JarvisSettings, @Suppress("UNUSED_PARAMETER") _compat: Unit = Unit) =
        log(userText, decision, outcome, prefs)

    suspend fun logPending(userText: String, decision: RouteDecision, eventId: String, prefs: JarvisSettings, @Suppress("UNUSED_PARAMETER") _compat: Unit = Unit) =
        logPending(userText, decision, eventId, prefs)

    suspend fun completePending(eventId: String, spokenReply: String, @Suppress("UNUSED_PARAMETER") _compat: Unit = Unit) =
        completePending(eventId, spokenReply)

    suspend fun logOffline(userText: String, decision: RouteDecision, prefs: JarvisSettings, @Suppress("UNUSED_PARAMETER") _compat: Unit = Unit) =
        logOffline(userText, decision, prefs)

    // ─── Internal ─────────────────────────────────────────────────────────────

    private suspend fun doDispatch(event: SessionEvent) {
        if (client.isConnected()) {
            runCatching { client.sendSessionEvent(event) }
                .onFailure { offlineQueue.enqueue(event) }
        } else {
            offlineQueue.enqueue(event)
        }
    }

    private suspend fun doFlushQueue() {
        val queued = offlineQueue.dequeueAll()
        queued.forEach { q -> runCatching { client.sendSessionEvent(q.event) } }
    }

    private fun buildEvent(
        eventId: String,
        userText: String,
        decision: RouteDecision,
        prefs: JarvisSettings,
        status: String,
        spokenReply: String,
        error: String? = null,
    ): SessionEvent = SessionEvent(
        eventId        = eventId,
        sessionKey     = prefs.sessionKey,
        timestamp      = Instant.now().toString(),
        speaker        = prefs.speakerName,
        input          = SessionInput(mode = "voice", text = userText),
        route          = decision,
        androidContext = buildAndroidContext(prefs),
        result         = SessionResult(status = status, spokenReply = spokenReply, error = error),
        memoryCandidate = memoryDetector.isMemoryCandidate(userText),
    )

    private fun buildAndroidContext(prefs: JarvisSettings): AndroidContext {
        val battery  = deviceCap.getBatteryPercent().let { if (it >= 0) "$it%" else "unknown" }
        val location = if (prefs.sendLocationContext) locationCap.getLocationLabel() else "disabled"
        return AndroidContext(
            device        = "${Build.MANUFACTURER} ${Build.MODEL}",
            battery       = battery,
            screenState   = deviceCap.getScreenState(),
            foregroundApp = "jarvis",
            locationLabel = location,
        )
    }
}
