package ai.openclaw.jarvis.protocol.client

import ai.openclaw.jarvis.network.OpenClawClient
import ai.openclaw.jarvis.protocol.ProtocolVersion
import ai.openclaw.jarvis.protocol.debug.ProtocolDebugBus
import ai.openclaw.jarvis.protocol.model.JarvisActionResult
import ai.openclaw.jarvis.protocol.model.JarvisLiveRequest
import ai.openclaw.jarvis.protocol.model.JarvisSessionEvent
import ai.openclaw.jarvis.protocol.model.OpenClawResponse
import ai.openclaw.jarvis.protocol.model.OpenClawSkillManifest
import ai.openclaw.jarvis.protocol.model.OpenClawSkillManifestRequest
import ai.openclaw.jarvis.protocol.validation.ProtocolError
import ai.openclaw.jarvis.protocol.validation.ProtocolResult
import ai.openclaw.jarvis.protocol.validation.ProtocolValidator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Typed I/O over [OpenClawClient]. Replaces the legacy free-form path:
 *
 *   - serialises and sends [JarvisLiveRequest] / [JarvisActionResult] /
 *     [JarvisSessionEvent] / [OpenClawSkillManifestRequest]
 *   - parses inbound raw frames into [OpenClawResponse] /
 *     [OpenClawSkillManifest], validating envelope + version
 *   - emits a typed [responses] / [skillManifests] / [malformed] flow
 *     so callers never see raw JSON
 *
 * This class owns *only* protocol concerns. Deciding what to do with a
 * response (TTS, action execution, ERROR_RECOVERY) is the session
 * manager's job.
 */
@Singleton
class OpenClawProtocolClient @Inject constructor(
    private val client: OpenClawClient,
    private val validator: ProtocolValidator,
    private val debugBus: ProtocolDebugBus,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _responses = MutableSharedFlow<OpenClawResponse>(extraBufferCapacity = 32)
    val responses: SharedFlow<OpenClawResponse> = _responses.asSharedFlow()

    private val _malformed = MutableSharedFlow<ProtocolError>(extraBufferCapacity = 32)
    val malformed: SharedFlow<ProtocolError> = _malformed.asSharedFlow()

    private val _skillManifest = MutableStateFlow<OpenClawSkillManifest?>(null)
    val skillManifest: StateFlow<OpenClawSkillManifest?> = _skillManifest.asStateFlow()

    /**
     * Streamed chunks for in-flight requests. Each [OpenClawResponseChunk]
     * carries a `replyDelta` and a `final` flag — consumers concatenate
     * deltas in order and stop on `final`. Independent of [responses],
     * which still surfaces the single full response when the backend
     * doesn't stream.
     */
    private val _chunks = MutableSharedFlow<ai.openclaw.jarvis.protocol.model.OpenClawResponseChunk>(
        extraBufferCapacity = 64,
    )
    val chunks: SharedFlow<ai.openclaw.jarvis.protocol.model.OpenClawResponseChunk> = _chunks.asSharedFlow()

    init {
        client.rawFrames.onEach { raw -> dispatchInbound(raw) }.launchIn(scope)
    }

    // ─── Outbound ────────────────────────────────────────────────────────────

    suspend fun sendLiveRequest(request: JarvisLiveRequest) {
        val text = validator.json.encodeToString(JarvisLiveRequest.serializer(), request)
        debugBus.lastRequest.value = request
        client.sendCustomFrame(text)
    }

    suspend fun sendActionResult(result: JarvisActionResult) {
        val text = validator.json.encodeToString(JarvisActionResult.serializer(), result)
        debugBus.lastActionResult.value = result
        client.sendCustomFrame(text)
    }

    suspend fun sendSessionEvent(event: JarvisSessionEvent) {
        val text = validator.json.encodeToString(JarvisSessionEvent.serializer(), event)
        client.sendCustomFrame(text)
    }

    suspend fun requestSkillManifest(requestId: String, sessionKey: String, timestamp: String) {
        val req = OpenClawSkillManifestRequest(
            requestId = requestId,
            sessionKey = sessionKey,
            timestamp = timestamp,
        )
        val text = validator.json.encodeToString(OpenClawSkillManifestRequest.serializer(), req)
        client.sendCustomFrame(text)
    }

    // ─── Inbound ─────────────────────────────────────────────────────────────

    /**
     * Inspects a raw text frame; if its `type` field is one of ours, parses
     * it strictly and emits onto [responses] / [skillManifest] / [malformed].
     *
     * Frames not addressed to the typed protocol (heartbeats, pairing,
     * legacy `assistant.reply`) are ignored — the legacy path handles them.
     */
    private fun dispatchInbound(raw: String) {
        val envelope = peekEnvelope(raw) ?: return
        when (envelope.type) {
            OpenClawResponse.TYPE -> handleResponse(raw)
            OpenClawSkillManifest.TYPE -> handleSkillManifest(raw)
            ai.openclaw.jarvis.protocol.model.OpenClawResponseChunk.TYPE -> handleChunk(raw)
            else -> Unit
        }
    }

    private fun handleChunk(raw: String) {
        when (val r = validator.parseResponseChunk(raw)) {
            is ProtocolResult.Ok -> _chunks.tryEmit(r.value)
            is ProtocolResult.Rejected -> _malformed.tryEmit(r.error)
        }
    }

    private fun handleResponse(raw: String) {
        when (val r = validator.parseResponse(raw)) {
            is ProtocolResult.Ok -> {
                debugBus.lastResponse.value = r.value
                _responses.tryEmit(r.value)
            }
            is ProtocolResult.Rejected -> _malformed.tryEmit(r.error)
        }
    }

    private fun handleSkillManifest(raw: String) {
        when (val r = validator.parseSkillManifest(raw)) {
            is ProtocolResult.Ok -> {
                debugBus.lastSkillManifest.value = r.value
                _skillManifest.value = r.value
            }
            is ProtocolResult.Rejected -> _malformed.tryEmit(r.error)
        }
    }

    /** Pull just the `type` and `protocolVersion` from a frame without a full decode. */
    private fun peekEnvelope(raw: String): EnvelopePeek? {
        return try {
            val obj = validator.json.parseToJsonElement(raw) as? JsonObject ?: return null
            val type = obj["type"]?.jsonPrimitive?.content ?: return null
            EnvelopePeek(
                type = type,
                protocolVersion = obj["protocolVersion"]?.jsonPrimitive?.content,
            )
        } catch (_: Throwable) { null }
    }

    private data class EnvelopePeek(val type: String, val protocolVersion: String?)

    /** Convenience for callers that need the envelope CURRENT version constant. */
    val currentProtocolVersion: String get() = ProtocolVersion.CURRENT
}
