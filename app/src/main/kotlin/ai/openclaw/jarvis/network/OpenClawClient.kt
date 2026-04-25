package ai.openclaw.jarvis.network

import ai.openclaw.jarvis.data.local.PairingStore
import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.data.models.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

sealed class GatewayEvent {
    data class Connected(val sessionKey: String, val nodeId: String?) : GatewayEvent()
    data class Disconnected(val reason: String) : GatewayEvent()
    data class InvokeCommand(val frame: NodeInvokeFrame) : GatewayEvent()
    data class AssistantReply(val frame: GatewayResponseFrame) : GatewayEvent()
    data class PairingChallenge(val code: String, val expiresIn: Int) : GatewayEvent()
    data class Error(val message: String) : GatewayEvent()
}

@Singleton
class OpenClawClient @Inject constructor(
    private val settingsStore: SettingsDataStore,
    private val pairingStore: PairingStore,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _gatewayState = MutableStateFlow(GatewayState.DISCONNECTED)
    val gatewayState: StateFlow<GatewayState> = _gatewayState.asStateFlow()

    private val _events = MutableSharedFlow<GatewayEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GatewayEvent> = _events.asSharedFlow()

    /**
     * Every raw WebSocket text frame, exposed for the typed protocol layer
     * (see [ai.openclaw.jarvis.protocol.client.OpenClawProtocolClient]) so
     * it can parse OpenClawResponse / SkillManifest with strict validation
     * instead of relying on the legacy [GatewayEvent.AssistantReply] path.
     */
    private val _rawFrames = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val rawFrames: SharedFlow<String> = _rawFrames.asSharedFlow()

    private val httpClient = HttpClient(CIO) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
            pingInterval = 30_000L
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    private var wsSession: DefaultWebSocketSession? = null
    private var connectJob: Job? = null
    private val isConnecting = AtomicBoolean(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Capability advertisements sent on connect
    var advertisedCapabilities: List<NodeCapabilityAd> = emptyList()

    // ─── Public API ───────────────────────────────────────────────────────────

    fun connect() {
        if (isConnecting.getAndSet(true)) return
        connectJob?.cancel()
        connectJob = scope.launch { connectWithBackoff() }
    }

    fun disconnect() {
        connectJob?.cancel()
        scope.launch {
            wsSession?.close(CloseReason(CloseReason.Codes.NORMAL, "User disconnect"))
            _gatewayState.value = GatewayState.DISCONNECTED
        }
        isConnecting.set(false)
    }

    suspend fun sendUserMessage(
        text: String,
        sessionKey: String,
        eventId: String,
        speaker: String? = null,
        trustLevel: String? = null,
        identityConfidence: Float? = null,
    ) = sendFrame(
        UserMessageFrame(
            sessionKey          = sessionKey,
            text                = text,
            eventId             = eventId,
            speaker             = speaker,
            trustLevel          = trustLevel,
            identityConfidence  = identityConfidence,
        )
    )

    suspend fun sendSessionEvent(event: SessionEvent) = sendRaw(json.encodeToString(event))

    suspend fun sendInvokeResult(frame: NodeInvokeResultFrame) = sendFrame(frame)

    // ─── Connect loop ─────────────────────────────────────────────────────────

    private suspend fun connectWithBackoff() {
        var attempt = 0
        while (isActive) {
            val settings = settingsStore.settings.first()
            if (!settings.gatewayEnabled) {
                _gatewayState.value = GatewayState.DISCONNECTED
                delay(5_000)
                continue
            }

            _gatewayState.value = GatewayState.CONNECTING
            try {
                runWebSocket(settings.gatewayUrl, settings)
                // If runWebSocket returns cleanly, reset backoff
                attempt = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(GatewayEvent.Error("Connection failed: ${e.message}"))
                _gatewayState.value = GatewayState.DISCONNECTED
            }

            attempt++
            val delayMs = minOf(30_000L, 1_000L * (1L shl minOf(attempt, 5)))
            delay(delayMs)
        }
    }

    private suspend fun runWebSocket(url: String, settings: ai.openclaw.jarvis.data.local.JarvisSettings) {
        httpClient.webSocket(url) {
            wsSession = this

            // Send connect frame
            val connectFrame = NodeConnectFrame(
                deviceId    = pairingStore.getDeviceId(),
                deviceName  = settings.deviceName,
                pairingToken = pairingStore.getPairingToken(),
                capabilities = advertisedCapabilities,
            )
            sendFrame(connectFrame)

            // Heartbeat loop
            val heartbeatJob = launch {
                while (isActive) {
                    delay(30_000)
                    sendFrame(HeartbeatFrame())
                }
            }

            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> handleIncoming(frame.readText())
                        is Frame.Close -> {
                            _gatewayState.value = GatewayState.DISCONNECTED
                            _events.tryEmit(GatewayEvent.Disconnected("Server closed connection"))
                            break
                        }
                        else -> Unit
                    }
                }
            } finally {
                heartbeatJob.cancel()
                wsSession = null
                _gatewayState.value = GatewayState.DISCONNECTED
            }
        }
    }

    private fun handleIncoming(raw: String) {
        // Surface every text frame to the typed protocol layer first.
        _rawFrames.tryEmit(raw)
        val envelope = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        when (val type = envelope["type"]?.jsonPrimitive?.content) {
            "gateway.connected" -> {
                val frame = runCatching { json.decodeFromString<GatewayConnectedFrame>(raw) }.getOrNull()
                    ?: return
                frame.token?.let { pairingStore.savePairingToken(it) }
                frame.nodeId?.let { pairingStore.saveNodeId(it) }
                _gatewayState.value = GatewayState.CONNECTED
                _events.tryEmit(GatewayEvent.Connected(frame.sessionKey, frame.nodeId))
            }
            "node.invoke" -> {
                val frame = runCatching { json.decodeFromString<NodeInvokeFrame>(raw) }.getOrNull()
                    ?: return
                _events.tryEmit(GatewayEvent.InvokeCommand(frame))
            }
            "assistant.reply", "gateway.response" -> {
                val frame = runCatching { json.decodeFromString<GatewayResponseFrame>(raw) }.getOrNull()
                    ?: return
                _events.tryEmit(GatewayEvent.AssistantReply(frame))
            }
            "pairing.challenge" -> {
                val frame = runCatching { json.decodeFromString<PairingChallengeFrame>(raw) }.getOrNull()
                    ?: return
                _gatewayState.value = GatewayState.PAIRING
                _events.tryEmit(GatewayEvent.PairingChallenge(frame.code, frame.expiresIn))
            }
            "heartbeat.ack" -> Unit
            else -> Unit // unknown frame, ignore
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun sendRaw(text: String) {
        wsSession?.send(Frame.Text(text))
    }

    /**
     * Emit an opaque JSON frame on the existing WebSocket session.
     * Used by the GitHub Issue Logging bridge for `jarvis.github_issue_*`
     * events that don't fit the strict [SessionEvent] schema. No-op when
     * the gateway is offline — the issue itself is already on its own
     * offline queue and will retry independently.
     */
    suspend fun sendCustomFrame(text: String) = sendRaw(text)

    private suspend inline fun <reified T> sendFrame(frame: T) {
        sendRaw(json.encodeToString(frame))
    }

    fun isConnected(): Boolean = _gatewayState.value == GatewayState.CONNECTED
}
