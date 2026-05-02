package ai.openclaw.jarvis.protocol.model

import ai.openclaw.jarvis.protocol.ProtocolVersion
import kotlinx.serialization.Serializable

/**
 * Strict, versioned reply schema OpenClaw must send back. Replaces the
 * old free-form `OpenClawResponse(reply: String)` model.
 *
 * Status values mirror the spec exactly: `ok | needs_confirmation | error`.
 * Anything outside that set is rejected by [ai.openclaw.jarvis.protocol.validation.ProtocolValidator].
 */
@Serializable
data class OpenClawResponse(
    val protocolVersion: String = ProtocolVersion.CURRENT,
    val requestId: String,
    val sessionId: String? = null,
    val timestamp: String? = null,
    val type: String = TYPE,
    val reply: ReplyDirective = ReplyDirective(),
    val actions: List<OpenClawAction> = emptyList(),
    val requiresConfirmation: Boolean = false,
    val memoryCandidate: Boolean = false,
    val status: ResponseStatus = ResponseStatus.ok,
    val error: ResponseError? = null,
) {
    companion object { const val TYPE = "openclaw.response" }
}

/**
 * Tells Jarvis what to do with the textual part of the reply.
 * `speak` = run TTS. `display` = show in transcript / UI.
 */
@Serializable
data class ReplyDirective(
    val text: String = "",
    val speak: Boolean = true,
    val display: Boolean = true,
)

@Serializable
enum class ResponseStatus { ok, needs_confirmation, error }

@Serializable
data class ResponseError(
    val code: String,
    val message: String,
)
