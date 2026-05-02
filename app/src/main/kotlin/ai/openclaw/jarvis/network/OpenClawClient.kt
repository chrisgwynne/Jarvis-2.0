package ai.openclaw.jarvis.network

import ai.openclaw.jarvis.data.local.JarvisSettings
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
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Commands this node can handle via `node.invoke`, matching AndroidActionExecutor.execute(). */
private val NODE_COMMANDS: List<Pair<String, String>> = listOf(
    "torch_on"        to "Turn the device torch on",
    "torch_off"       to "Turn the device torch off",
    "volume_up"       to "Raise media volume",
    "volume_down"     to "Lower media volume",
    "mute"            to "Mute the phone",
    "unmute"          to "Unmute the phone",
    "media_control"   to "Control media playback — params: action=(play|pause|next|previous|stop)",
    "open_app"        to "Launch an installed app by name — params: app",
    "location_query"  to "Get the device's current GPS location",
    "set_timer_alarm" to "Set a countdown timer — params: minutes, label",
    "call"            to "Place a phone call — params: contact",
    "send_message"    to "Send an SMS — params: contact, message",
    "stop"               to "Cancel / stop the current action",
    // Data-read commands — return JSON payloads via node.invoke.result
    "sms.read"           to "Read SMS messages — params: limit(int), unread_only(bool)",
    "calendar.read"      to "Read upcoming calendar events — params: days(int)",
    "contacts.search"    to "Search device contacts — params: query(string)",
)

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
    private val hermesClient: HermesAgentClient,
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

    private val _rawFrames = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val rawFrames: SharedFlow<String> = _rawFrames.asSharedFlow()

    // ─── Diagnostics state ────────────────────────────────────────────────────

    private val _lastFailure = MutableStateFlow<ConnectionFailure?>(null)
    val lastFailure: StateFlow<ConnectionFailure?> = _lastFailure.asStateFlow()

    private val _diagLog = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val diagLog: StateFlow<List<DiagnosticEvent>> = _diagLog.asStateFlow()

    private val _normalizedUrl = MutableStateFlow("")
    val normalizedUrl: StateFlow<String> = _normalizedUrl.asStateFlow()

    private val _lastCloseCode = MutableStateFlow<Short?>(null)
    val lastCloseCode: StateFlow<Short?> = _lastCloseCode.asStateFlow()

    private val _lastCloseReason = MutableStateFlow<String?>(null)
    val lastCloseReason: StateFlow<String?> = _lastCloseReason.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────

    private val httpClient = HttpClient(CIO) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
            pingInterval = 30.seconds
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    private var wsSession: DefaultWebSocketSession? = null
    private var connectJob: Job? = null
    private val isConnecting = AtomicBoolean(false)

    // Per-connection state (reset on each new connection attempt)
    @Volatile private var pendingConnectReqId: String? = null
    @Volatile private var pairingRequestSent: Boolean = false
    @Volatile private var currentSettings: JarvisSettings? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var advertisedCapabilities: List<NodeCapabilityAd> = emptyList()

    init {
        // Restart the connection whenever backendMode changes so switching between
        // OpenClaw and Hermes in settings takes effect immediately.
        scope.launch {
            settingsStore.settings
                .map { it.backendMode }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    disconnect()
                    connect()
                }
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    fun connect() {
        connectJob?.cancel()
        connectJob = scope.launch {
            val mode = settingsStore.settings.first().backendMode
            if (mode == "hermes") {
                launch { hermesClient.gatewayState.collect { _gatewayState.value = it } }
                launch { hermesClient.events.collect { _events.tryEmit(it) } }
                launch { hermesClient.rawFrames.collect { _rawFrames.tryEmit(it) } }
                hermesClient.connect()
                awaitCancellation()
            } else {
                if (isConnecting.getAndSet(true)) return@launch
                connectWithBackoff()
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        scope.launch {
            val mode = settingsStore.settings.first().backendMode
            if (mode == "hermes") {
                hermesClient.disconnect()
            } else {
                wsSession?.close(CloseReason(CloseReason.Codes.NORMAL, "User disconnect"))
                _gatewayState.value = GatewayState.DISCONNECTED
            }
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
    ) {
        if (settingsStore.settings.first().backendMode == "hermes") {
            hermesClient.sendMessage(text, eventId)
            return
        }
        sendRaw(json.encodeToString(UserMessageFrame(
            sessionKey         = sessionKey,
            text               = text,
            eventId            = eventId,
            speaker            = speaker,
            trustLevel         = trustLevel,
            identityConfidence = identityConfidence,
        )))
    }

    suspend fun sendChatMessage(
        text: String,
        sessionKey: String,
        eventId: String,
        imageBase64: String? = null,
    ) {
        if (settingsStore.settings.first().backendMode == "hermes") {
            hermesClient.sendMessage(text, eventId)
            return
        }
        sendRaw(json.encodeToString(ChatSendFrame(
            id = eventId,
            params = ChatSendParams(
                sessionKey            = sessionKey,
                message               = text,
                idempotencyKey        = eventId,
                systemInputProvenance = SystemInputProvenance(),
                imageBase64           = imageBase64,
            ),
        )))
    }


    suspend fun sendSessionEvent(event: SessionEvent) = sendRaw(json.encodeToString(event))

    suspend fun sendInvokeResult(frame: NodeInvokeResultFrame) = sendRaw(json.encodeToString(frame))

    fun isConnected(): Boolean = _gatewayState.value == GatewayState.CONNECTED

    // ─── Connect loop ─────────────────────────────────────────────────────────

    private suspend fun connectWithBackoff() {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            val settings = settingsStore.settings.first()
            if (settings.backendMode == "hermes") break  // mode switched — init watcher will reconnect
            if (!settings.gatewayEnabled) {
                _gatewayState.value = GatewayState.DISCONNECTED
                delay(5_000)
                continue
            }

            val rawUrl = settings.gatewayUrl
            val wsUrl  = normalizeUrl(rawUrl)
            _normalizedUrl.value = wsUrl

            if (wsUrl.isBlank()) {
                log(DiagnosticLevel.ERROR, "Gateway URL is empty — set a URL in Settings → Connection")
                _lastFailure.value = ConnectionFailure(
                    stage     = "Config",
                    reason    = "Gateway URL is empty",
                    errorType = "ConfigError",
                    message   = "No URL configured. Go to Settings → Connection and enter the WebSocket URL.",
                )
                _gatewayState.value = GatewayState.DISCONNECTED
                delay(10_000)
                continue
            }

            log(DiagnosticLevel.INFO, "Connecting to $wsUrl (attempt ${attempt + 1})")
            _gatewayState.value = GatewayState.CONNECTING

            // Reset per-connection state
            pendingConnectReqId = null
            pairingRequestSent  = false
            currentSettings     = settings

            try {
                runWebSocket(wsUrl, settings)
                attempt = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: NullPointerException) {
                val message = "URL parse error — check format. Entered: '$rawUrl' → normalized: '$wsUrl'"
                log(DiagnosticLevel.ERROR, "NullPointerException in WebSocket client: $message")
                _lastFailure.value = ConnectionFailure(
                    stage     = "TCP/WebSocket",
                    reason    = "URL could not be parsed by the WebSocket client",
                    errorType = "NullPointerException",
                    message   = message,
                )
                _events.tryEmit(GatewayEvent.Error(message))
                _gatewayState.value = GatewayState.DISCONNECTED
            } catch (e: Exception) {
                val errorType = e.javaClass.simpleName
                val message   = e.message ?: e.cause?.message ?: errorType
                log(DiagnosticLevel.ERROR, "Connection failed [$errorType]: $message")
                _lastFailure.value = ConnectionFailure(
                    stage     = "TCP/WebSocket",
                    reason    = "Connection failed",
                    errorType = errorType,
                    message   = message,
                )
                _events.tryEmit(GatewayEvent.Error("Connection failed: $message"))
                _gatewayState.value = GatewayState.DISCONNECTED
            }

            attempt++
            val delayMs = minOf(30_000L, 1_000L * (1L shl minOf(attempt, 5)))
            log(DiagnosticLevel.INFO, "Reconnecting in ${delayMs}ms…")
            delay(delayMs)
        }
    }

    private suspend fun runWebSocket(url: String, settings: JarvisSettings) {
        log(DiagnosticLevel.INFO, "WebSocket opening → $url")
        httpClient.webSocket(url) {
            wsSession = this
            log(DiagnosticLevel.SUCCESS,
                "WebSocket open — waiting for connect.challenge (protocol v3)")
            log(DiagnosticLevel.INFO,
                "Device ID: ${pairingStore.getDeviceId()}")

            val heartbeatJob = launch {
                while (isActive) {
                    delay(30_000)
                    sendRaw(json.encodeToString(HeartbeatFrame()))
                }
            }

            var receivedHelloOk = false
            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            handleIncoming(text, settings)
                            if (!receivedHelloOk && _gatewayState.value == GatewayState.CONNECTED) {
                                receivedHelloOk = true
                            }
                        }
                        is Frame.Close -> {
                            val closeReason = frame.readReason()
                            val code        = closeReason?.code
                            val reason      = closeReason?.message ?: ""
                            _lastCloseCode.value   = code
                            _lastCloseReason.value = reason
                            val humanCode = code?.toInt()
                            log(DiagnosticLevel.WARN,
                                "Server sent Close frame: code=$humanCode reason='${reason.ifBlank { "none" }}'")
                            _lastFailure.value = ConnectionFailure(
                                stage       = "WebSocket",
                                reason      = "Server closed connection",
                                errorType   = "CloseFrame",
                                message     = reason.ifBlank { "No reason given" },
                                closeCode   = code,
                                closeReason = reason,
                            )
                            _gatewayState.value = GatewayState.DISCONNECTED
                            _events.tryEmit(GatewayEvent.Disconnected(
                                "Server closed connection" +
                                if (humanCode != null) " (code $humanCode)" else "" +
                                if (reason.isNotBlank()) ": $reason" else ""
                            ))
                            break
                        }
                        else -> Unit
                    }
                }
            } finally {
                heartbeatJob.cancel()
                wsSession = null
                if (_gatewayState.value != GatewayState.DISCONNECTED) {
                    val stage = if (receivedHelloOk)
                        "after hello-ok"
                    else
                        "before hello-ok — server rejected the connect request"
                    log(DiagnosticLevel.ERROR,
                        "WebSocket dropped without Close frame ($stage).")
                    if (!receivedHelloOk) {
                        log(DiagnosticLevel.WARN,
                            "Check: signature valid? DeviceId correct? Nonce echoed?")
                    }
                    _lastFailure.value = ConnectionFailure(
                        stage     = "WebSocket",
                        reason    = "Connection dropped without Close frame ($stage)",
                        errorType = "UnexpectedClose",
                        message   = if (!receivedHelloOk)
                            "Server dropped the WebSocket before sending hello-ok. Check that " +
                            "the Ed25519 signature is accepted and the device is not banned."
                        else
                            "Server disconnected after a successful session.",
                    )
                    _gatewayState.value = GatewayState.DISCONNECTED
                }
            }
        }
    }

    // ─── Incoming frame dispatch ──────────────────────────────────────────────

    private fun handleIncoming(raw: String, settings: JarvisSettings) {
        _rawFrames.tryEmit(raw)
        val envelope = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return

        when (val type = envelope["type"]?.jsonPrimitive?.content) {

            // ── Protocol v3: res ──────────────────────────────────────────────
            "res" -> {
                val id = envelope["id"]?.jsonPrimitive?.content
                if (id != null && id == pendingConnectReqId) {
                    pendingConnectReqId = null
                    val ok      = envelope["ok"]?.jsonPrimitive?.boolean ?: false
                    val payload = envelope["payload"]?.jsonObject

                    if (ok) {
                        // hello-ok
                        val authObj      = payload?.get("auth")?.jsonObject
                        val deviceToken  = authObj?.get("deviceToken")?.jsonPrimitive?.content
                        val role         = authObj?.get("role")?.jsonPrimitive?.content
                        val protocol     = payload?.get("protocol")?.jsonPrimitive?.intOrNull
                        val serverVer    = payload?.get("server")?.jsonObject
                            ?.get("version")?.jsonPrimitive?.content

                        if (deviceToken != null) {
                            pairingStore.saveDeviceToken(deviceToken)
                            log(DiagnosticLevel.SUCCESS,
                                "hello-ok — protocol=$protocol server=$serverVer " +
                                "role=$role — device token saved")
                        } else {
                            log(DiagnosticLevel.SUCCESS,
                                "hello-ok — protocol=$protocol server=$serverVer " +
                                "role=$role — no deviceToken yet (pairing pending)")
                        }

                        _lastFailure.value = null
                        _lastCloseCode.value   = null
                        _lastCloseReason.value = null
                        _gatewayState.value    = GatewayState.CONNECTED
                        _events.tryEmit(GatewayEvent.Connected(
                            settings.sessionKey,
                            pairingStore.getNodeId(),
                        ))

                        // Trigger node pairing if not already approved
                        if (!pairingStore.isApproved() && !pairingRequestSent) {
                            pairingRequestSent = true
                            scope.launch { sendNodePairRequest(settings) }
                        }

                    } else {
                        // error response
                        val errorObj = envelope["error"]?.jsonObject
                        val code     = errorObj?.get("code")?.jsonPrimitive?.content ?: "UNKNOWN"
                        val reason   = errorObj?.get("reason")?.jsonPrimitive?.content ?: ""
                        val msg      = "Auth rejected — code=$code reason=$reason"
                        log(DiagnosticLevel.ERROR, msg)
                        log(DiagnosticLevel.WARN,
                            diagnosticHintForAuthError(code))
                        _lastFailure.value = ConnectionFailure(
                            stage     = "Protocol",
                            reason    = "Server rejected authentication",
                            errorType = code,
                            message   = msg,
                        )
                        _events.tryEmit(GatewayEvent.Error(msg))
                    }
                } else if (id != null) {
                    // Response for a method other than connect (e.g. node.pair.request)
                    handleMethodResponse(id, envelope)
                }
            }

            // ── Protocol v3: event ────────────────────────────────────────────
            "event" -> {
                val eventName = envelope["event"]?.jsonPrimitive?.content
                    ?: envelope["name"]?.jsonPrimitive?.content
                val eventPayload = envelope["payload"]?.jsonObject

                when (eventName) {
                    "connect.challenge" -> {
                        val nonce     = eventPayload?.get("nonce")?.jsonPrimitive?.content
                        val timestamp = eventPayload?.get("ts")?.jsonPrimitive?.longOrNull
                        if (nonce == null) {
                            log(DiagnosticLevel.ERROR,
                                "connect.challenge missing nonce: $raw")
                            return
                        }
                        log(DiagnosticLevel.INFO,
                            "connect.challenge received — nonce=$nonce ts=$timestamp " +
                            "fullPayload=${eventPayload?.toString()}")
                        scope.launch {
                            sendConnectReq(nonce, timestamp, settings)
                        }
                    }

                    "node.pair.approved" -> {
                        val newToken = eventPayload?.get("token")?.jsonPrimitive?.content
                        val nodeId   = eventPayload?.get("nodeId")?.jsonPrimitive?.content
                        if (newToken != null) pairingStore.saveDeviceToken(newToken)
                        if (nodeId   != null) pairingStore.saveNodeId(nodeId)
                        pairingStore.setApproved(true)
                        log(DiagnosticLevel.SUCCESS,
                            "node.pair.approved — nodeId=$nodeId — device token updated")
                        _gatewayState.value = GatewayState.CONNECTED
                    }

                    "node.pair.rejected" -> {
                        log(DiagnosticLevel.ERROR,
                            "node.pair.rejected — admin declined this device")
                        _lastFailure.value = ConnectionFailure(
                            stage     = "Pairing",
                            reason    = "Pairing request was rejected by the gateway admin",
                            errorType = "PairingRejected",
                            message   = "The gateway admin rejected the pairing request for this device.",
                        )
                    }

                    "chat.response" -> {
                        val state   = eventPayload?.get("state")?.jsonPrimitive?.content
                        val runId   = eventPayload?.get("runId")?.jsonPrimitive?.content
                        // message.content is an array of {type, text} blocks.
                        // Concatenate all "text"-typed blocks to get the full string.
                        val content = eventPayload
                            ?.get("message")?.jsonObject
                            ?.get("content")?.jsonArray
                        val text = content
                            ?.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
                            ?.joinToString("") ?: ""

                        when (state) {
                            "final", "aborted" -> {
                                if (text.isNotBlank()) {
                                    _events.tryEmit(GatewayEvent.AssistantReply(
                                        GatewayResponseFrame(
                                            type        = "chat.response",
                                            text        = text,
                                            spokenReply = text,
                                            eventId     = runId,
                                        )
                                    ))
                                }
                            }
                            "error" -> {
                                val msg = eventPayload?.get("errorMessage")
                                    ?.jsonPrimitive?.content ?: "Gateway error."
                                _events.tryEmit(GatewayEvent.Error(msg))
                            }
                            // "delta" — partial chunks; we wait for final to speak
                        }
                    }

                    else -> {
                        val snippet = eventPayload?.toString() ?: raw.take(200)
                        log(DiagnosticLevel.INFO,
                            "Server event '$eventName': $snippet")
                    }
                }
            }

            // ── Legacy / shared frames ────────────────────────────────────────

            "node.invoke" -> {
                val frame = runCatching {
                    json.decodeFromString<NodeInvokeFrame>(raw)
                }.getOrNull() ?: return
                _events.tryEmit(GatewayEvent.InvokeCommand(frame))
            }

            "assistant.reply", "gateway.response" -> {
                val frame = runCatching {
                    json.decodeFromString<GatewayResponseFrame>(raw)
                }.getOrNull() ?: return
                _events.tryEmit(GatewayEvent.AssistantReply(frame))
            }

            "heartbeat.ack" -> Unit

            // ── Unexpected ────────────────────────────────────────────────────

            else -> {
                if (type != null) {
                    log(DiagnosticLevel.INFO,
                        "Unknown frame type='$type': ${raw.take(200)}")
                }
            }
        }
    }

    private fun handleMethodResponse(id: String, envelope: JsonObject) {
        val ok      = envelope["ok"]?.jsonPrimitive?.boolean ?: false
        val payload = envelope["payload"]?.jsonObject

        log(DiagnosticLevel.INFO,
            "Method response id=$id ok=$ok payload=${payload?.toString()?.take(300)}")

        // node.pair.request response — requestId may be at payload.requestId,
        // payload.id, or payload.data.requestId depending on gateway version.
        val requestId = payload?.get("requestId")?.jsonPrimitive?.content
            ?: payload?.get("id")?.jsonPrimitive?.content
            ?: payload?.get("data")?.jsonObject?.get("requestId")?.jsonPrimitive?.content

        if (requestId != null) {
            pairingStore.savePairRequestId(requestId)
            log(DiagnosticLevel.SUCCESS,
                "node.pair.request pending — requestId=$requestId")
            log(DiagnosticLevel.WARN,
                "ACTION REQUIRED: approve this device in the gateway control UI " +
                "or run `openclaw nodes approve $requestId` on your gateway.")
            _gatewayState.value = GatewayState.PAIRING
            return
        }

        if (ok) {
            // Accepted but no requestId — gateway may auto-approve or uses a different field.
            log(DiagnosticLevel.INFO,
                "Method response ok=true but no requestId — full payload: $payload")
        } else {
            val errorObj = envelope["error"]?.jsonObject
            val code     = errorObj?.get("code")?.jsonPrimitive?.content ?: "UNKNOWN"
            val reason   = errorObj?.get("reason")?.jsonPrimitive?.content ?: ""
            log(DiagnosticLevel.WARN,
                "Method response error (id=$id): code=$code reason=$reason")
        }
    }

    // ─── Protocol helpers ─────────────────────────────────────────────────────

    // JSON encoder that omits null fields — the server treats null ≠ absent
    // for optional auth fields and rejects requests that include null tokens.
    private val connectJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults    = false
        explicitNulls     = false
    }

    private suspend fun sendConnectReq(nonce: String, @Suppress("UNUSED_PARAMETER") challengeTimestamp: Long?, settings: JarvisSettings) {
        // signedAt is the client's clock at signing time — the server allows up to
        // 10 minutes of clock skew, so local time is correct per the protocol spec.
        val signedAt    = System.currentTimeMillis()
        val reqId       = UUID.randomUUID().toString()
        val deviceId    = pairingStore.getDeviceId()
        val pubKey      = pairingStore.getPublicKeyRawBase64Url()
        val deviceToken = pairingStore.getDeviceToken()
        // deviceToken takes priority; fall back to the user-configured auth token
        // (the bootstrap token the gateway requires on first connection).
        val authToken   = deviceToken ?: settings.nodeSecret.ifBlank { null }
        val tokenForSig = authToken ?: ""

        // v2 pipe-delimited signature payload — matches what the server reconstructs.
        // Node role uses empty scopes (nodes advertise caps/commands, not operator scopes).
        // Empty scopes = empty string in the sig → double pipe in the payload.
        // v2|deviceId|clientId|clientMode|role|scopes|signedAtMs|token|nonce
        val sigPayload = "v2|$deviceId|node-host|node|node||$signedAt|$tokenForSig|$nonce"
        val signature  = pairingStore.signPayload(sigPayload)

        log(DiagnosticLevel.INFO,
            "Sending connect req — deviceId=${deviceId.take(12)}… signedAt=$signedAt " +
            "hasDeviceToken=${deviceToken != null} hasAuthToken=${authToken != null} " +
            "sigPayload=$sigPayload")

        // Only include token fields when we actually have a token — empty string
        // fails the server schema's minLength constraint.
        val authObj = buildJsonObject {
            if (authToken != null) {
                put("token", authToken)
                if (deviceToken != null) put("deviceToken", deviceToken)
            }
        }

        val capsArray = buildJsonArray {
            advertisedCapabilities.forEach { cap -> add(cap.id) }
        }

        val commandsArray = buildJsonArray {
            NODE_COMMANDS.forEach { (id, _) -> add(id) }
        }

        val paramsObj = buildJsonObject {
            put("minProtocol", 3)
            put("maxProtocol", 3)
            putJsonObject("client") {
                put("id",       "android-node")
                put("version",  "1.0.0")
                put("platform", "android")
                put("mode",     "node")
            }
            put("role",     "node")
            put("scopes",   buildJsonArray { /* nodes declare caps/commands, not operator scopes */ })
            put("caps",     capsArray)
            put("commands", commandsArray)
            put("auth",     authObj)
            putJsonObject("device") {
                put("id",        deviceId)
                put("publicKey", pubKey)
                put("signature", signature)
                put("signedAt",  signedAt)
                put("nonce",     nonce)
            }
        }

        val frameObj = buildJsonObject {
            put("type",   "req")
            put("id",     reqId)
            put("method", "connect")
            put("params", paramsObj)
        }

        val frameJson = frameObj.toString()
        log(DiagnosticLevel.INFO, "connect req frame: $frameJson")

        pendingConnectReqId = reqId
        sendRaw(frameJson)
        log(DiagnosticLevel.INFO, "connect req sent (id=$reqId)")
    }

    private suspend fun sendNodePairRequest(settings: JarvisSettings) {
        val reqId = UUID.randomUUID().toString()
        val frame = buildJsonObject {
            put("type", "req")
            put("id", reqId)
            put("method", "node.pair.request")
            put("params", buildJsonObject {
                put("name", settings.deviceName)
            })
        }
        log(DiagnosticLevel.INFO,
            "Sending node.pair.request (id=$reqId)")
        sendRaw(frame.toString())
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun sendRaw(text: String) {
        wsSession?.send(Frame.Text(text))
    }

    suspend fun sendCustomFrame(text: String) = sendRaw(text)

    private fun log(level: DiagnosticLevel, message: String) {
        val event = DiagnosticEvent(level, message)
        _diagLog.update { list ->
            (listOf(event) + list).take(200)
        }
    }

    private fun diagnosticHintForAuthError(code: String): String = when (code) {
        "DEVICE_AUTH_NONCE_REQUIRED",
        "DEVICE_AUTH_NONCE_MISMATCH"     ->
            "Nonce was missing or didn't match — this is a bug, please report it."
        "DEVICE_AUTH_SIGNATURE_INVALID"  ->
            "Ed25519 signature was rejected. Check that BouncyCastle is correctly registered."
        "DEVICE_AUTH_SIGNATURE_EXPIRED"  ->
            "Signature timestamp was too old. Make sure device clock is correct."
        "DEVICE_AUTH_DEVICE_ID_MISMATCH" ->
            "Device ID in signature doesn't match the one sent. Clearing pairing may fix this."
        "DEVICE_AUTH_PUBLIC_KEY_INVALID" ->
            "Public key format was rejected. Try Settings → Clear Pairing to regenerate the keypair."
        else ->
            "See OpenClaw gateway logs for details."
    }

    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return when {
            trimmed.startsWith("ws://") || trimmed.startsWith("wss://") -> trimmed
            trimmed.startsWith("https://") -> trimmed.replace("https://", "wss://")
            trimmed.startsWith("http://")  -> trimmed.replace("http://", "ws://")
            trimmed.contains("://") -> trimmed
            trimmed.isBlank() -> ""
            else -> "ws://$trimmed"
        }
    }

    fun connectionMode(rawUrl: String): String {
        val url = normalizeUrl(rawUrl)
        return when {
            url.startsWith("wss://") -> "wss (TLS)"
            url.startsWith("ws://")  -> "ws (plaintext)"
            else -> "unknown"
        }
    }
}
