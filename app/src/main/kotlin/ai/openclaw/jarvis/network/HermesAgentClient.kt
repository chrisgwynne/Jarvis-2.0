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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP client for HermesAgent (Nous Research).
 * Communicates via the OpenAI-compatible REST API at /v1/chat/completions.
 * Maintains local conversation history per session.
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
        engine { requestTimeout = 120_000 }
    }

    private val conversationHistory = mutableListOf<JsonObject>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var healthJob: Job? = null

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    fun connect() {
        healthJob?.cancel()
        _gatewayState.value = GatewayState.CONNECTING
        healthJob = scope.launch {
            checkHealth()          // immediate check on connect
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

    fun clearHistory() = conversationHistory.clear()

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
                    _events.tryEmit(GatewayEvent.Connected("hermes-session", null))
                }
            } else {
                setDisconnected()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            setDisconnected()
        }
    }

    private fun setDisconnected() {
        if (_gatewayState.value == GatewayState.CONNECTED) {
            _events.tryEmit(GatewayEvent.Disconnected("Hermes unreachable"))
        }
        _gatewayState.value = GatewayState.DISCONNECTED
    }

    // ─── Send message ─────────────────────────────────────────────────────────

    suspend fun sendMessage(text: String, eventId: String) {
        val settings = settingsStore.settings.first()
        val baseUrl  = settings.hermesUrl.trimEnd('/')
        val apiKey   = settings.hermesApiKey
        val sessionId = settings.sessionKey

        conversationHistory.add(buildJsonObject {
            put("role", "user")
            put("content", text)
        })

        val requestBody = buildJsonObject {
            put("model", "hermes-agent")
            putJsonArray("messages") { conversationHistory.forEach { add(it) } }
            put("stream", false)
        }
        _rawFrames.tryEmit(requestBody.toString())

        try {
            val response = httpClient.post("$baseUrl/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey")
                header("X-Hermes-Session-Id", sessionId)
                setBody(requestBody.toString())
            }

            val body = response.bodyAsText()
            _rawFrames.tryEmit(body)

            val content = json.parseToJsonElement(body).jsonObject["choices"]
                ?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.contentOrNull ?: ""

            if (content.isNotBlank()) {
                conversationHistory.add(buildJsonObject {
                    put("role", "assistant")
                    put("content", content)
                })
                _events.tryEmit(GatewayEvent.AssistantReply(
                    GatewayResponseFrame(
                        type        = "chat.response",
                        text        = content,
                        spokenReply = content,
                        eventId     = eventId,
                    )
                ))
            }

            if (_gatewayState.value != GatewayState.CONNECTED) {
                _gatewayState.value = GatewayState.CONNECTED
                _events.tryEmit(GatewayEvent.Connected("hermes-session", null))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.tryEmit(GatewayEvent.Error("Hermes error: ${e.message}"))
        }
    }
}
