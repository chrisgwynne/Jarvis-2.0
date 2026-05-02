package ai.openclaw.jarvis.network

import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.data.models.GatewayResponseFrame
import ai.openclaw.jarvis.data.models.GatewayState
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val POLL_INTERVAL_MS = 500L
private const val TIMEOUT_MS       = 30_000L

/**
 * Client for the Hermes relay server running on the Linux machine.
 *
 * Protocol:
 *   POST /session  { "text": "...", "timestamp": "ISO" }  → { "session_id": "uuid" }
 *   GET  /session/{id}  202 → { "status": "processing" }
 *                        200 → { "status": "ready", "response": "..." }
 *   GET  /health                                           → { "status": "ok" }
 *
 * The relay URL (Tailscale hostname + port 8765) is user-configured in Settings → Connection.
 */
@Singleton
class HermesAgentClient @Inject constructor(
    private val settingsStore: SettingsDataStore,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _gatewayState = MutableStateFlow(GatewayState.DISCONNECTED)
    val gatewayState: StateFlow<GatewayState> = _gatewayState.asStateFlow()

    private val _events = MutableSharedFlow<GatewayEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GatewayEvent> = _events.asSharedFlow()

    private val _rawFrames = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val rawFrames: SharedFlow<String> = _rawFrames.asSharedFlow()

    private val httpClient = HttpClient(CIO) {
        install(Logging) { level = LogLevel.INFO }
        engine { requestTimeout = 35_000 }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var healthJob: Job? = null

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    fun connect() {
        healthJob?.cancel()
        _gatewayState.value = GatewayState.CONNECTING
        healthJob = scope.launch {
            checkHealth()
            while (isActive) {
                delay(30_000)
                checkHealth()
            }
        }
    }

    fun disconnect() {
        healthJob?.cancel()
        _gatewayState.value = GatewayState.DISCONNECTED
        _events.tryEmit(GatewayEvent.Disconnected("User disconnected"))
    }

    fun isConnected(): Boolean = _gatewayState.value == GatewayState.CONNECTED

    // ─── Health check ─────────────────────────────────────────────────────────

    private suspend fun checkHealth() {
        val settings = settingsStore.settings.first()
        if (!settings.gatewayEnabled) {
            _gatewayState.value = GatewayState.DISCONNECTED
            return
        }
        val baseUrl = settings.hermesUrl.trimEnd('/')
        try {
            val resp = httpClient.get("$baseUrl/health")
            if (resp.status.isSuccess()) {
                if (_gatewayState.value != GatewayState.CONNECTED) {
                    _gatewayState.value = GatewayState.CONNECTED
                    _events.tryEmit(GatewayEvent.Connected("hermes-relay", null))
                }
            } else {
                markDisconnected()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            markDisconnected()
        }
    }

    private fun markDisconnected() {
        if (_gatewayState.value == GatewayState.CONNECTED) {
            _events.tryEmit(GatewayEvent.Disconnected("Relay unreachable"))
        }
        _gatewayState.value = GatewayState.DISCONNECTED
    }

    // ─── Send message → relay → poll → TTS ───────────────────────────────────

    suspend fun sendMessage(text: String, eventId: String) {
        val settings = settingsStore.settings.first()
        val baseUrl  = settings.hermesUrl.trimEnd('/')

        // 1. POST /session
        val body = buildJsonObject {
            put("text", text)
            put("timestamp", Instant.now().toString())
        }.toString()

        _rawFrames.tryEmit(body)

        val sessionId = try {
            val resp    = httpClient.post("$baseUrl/session") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            val respText = resp.bodyAsText()
            _rawFrames.tryEmit(respText)
            json.parseToJsonElement(respText).jsonObject["session_id"]
                ?.jsonPrimitive?.contentOrNull
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.tryEmit(GatewayEvent.Error("Relay POST failed: ${e.message}"))
            return
        }

        if (sessionId.isNullOrBlank()) {
            _events.tryEmit(GatewayEvent.Error("Relay returned no session_id"))
            return
        }

        // 2. Poll GET /session/{id} until ready or timeout
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            try {
                val poll     = httpClient.get("$baseUrl/session/$sessionId")
                val pollText = poll.bodyAsText()
                _rawFrames.tryEmit(pollText)
                val pollJson = json.parseToJsonElement(pollText).jsonObject
                val status   = pollJson["status"]?.jsonPrimitive?.contentOrNull

                if (status == "ready") {
                    val response = pollJson["response"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (response.isNotBlank()) {
                        _events.tryEmit(GatewayEvent.AssistantReply(
                            GatewayResponseFrame(
                                type        = "chat.response",
                                text        = response,
                                spokenReply = response,
                                eventId     = eventId,
                            )
                        ))
                    }
                    return
                }
                // status == "processing" → keep polling
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // transient error — keep polling until deadline
            }
        }

        _events.tryEmit(GatewayEvent.Error("Relay timeout: no response after 30s (session $sessionId)"))
    }
}
