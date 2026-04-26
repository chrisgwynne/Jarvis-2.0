package ai.openclaw.jarvis.protocol.model

import ai.openclaw.jarvis.protocol.ProtocolVersion
import kotlinx.serialization.Serializable

/**
 * One chunk of a streamed [OpenClawResponse]. OpenClaw can either send a
 * single full response (the existing path) OR a sequence of chunks
 * sharing a `requestId`, each carrying:
 *   - `replyDelta`: the next slice of reply.text. Concatenating every
 *     `replyDelta` in order yields the final `reply.text`.
 *   - `final`: when true, this is the last chunk and no more arrive
 *     for this request.
 *   - `actions`, `status`, `error`: optional, only present on the final
 *     chunk (or a chunk that resolves the request).
 *
 * The wire `type` is `openclaw.response_chunk`. Old backends that don't
 * stream simply keep sending [OpenClawResponse] as before — clients
 * handle both shapes.
 */
@Serializable
data class OpenClawResponseChunk(
    val protocolVersion: String = ProtocolVersion.CURRENT,
    val requestId: String,
    val sessionId: String? = null,
    val timestamp: String? = null,
    val type: String = TYPE,
    val sequence: Int,                                  // 0-based
    val replyDelta: String = "",
    val final: Boolean = false,
    val actions: List<OpenClawAction> = emptyList(),
    val status: ResponseStatus = ResponseStatus.ok,
    val error: ResponseError? = null,
) {
    companion object { const val TYPE = "openclaw.response_chunk" }
}
