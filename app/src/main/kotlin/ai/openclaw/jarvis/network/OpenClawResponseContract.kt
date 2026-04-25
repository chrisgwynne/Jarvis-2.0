package ai.openclaw.jarvis.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient         = true
    coerceInputValues = true
}

/**
 * Strict model for replies from OpenClaw.
 *
 * The gateway wraps this inside a GatewayResponseFrame; callers
 * parse it from `frame.payload` or `frame.text` as needed.
 */
@Serializable
data class OpenClawResponse(
    val reply: String = "",
    val actions: List<OpenClawAction> = emptyList(),
    val requiresConfirmation: Boolean = false,
    val memoryCandidate: String? = null,
    val sessionId: String? = null,
    val error: String? = null,
) {
    val hasError: Boolean get() = error != null
    val spokenReply: String get() = reply.ifBlank { error ?: "No response." }
}

@Serializable
data class OpenClawAction(
    val type: String,
    val payload: JsonObject? = null,
    val requiresConfirmation: Boolean = false,
)

object OpenClawResponseContract {
    /** Parse a raw JSON string, falling back to an error response on failure. */
    fun parse(json: String): OpenClawResponse = runCatching {
        lenientJson.decodeFromString<OpenClawResponse>(json)
    }.getOrElse { e ->
        OpenClawResponse(
            reply = "",
            error = "Malformed response: ${e.message?.take(120)}",
        )
    }

    /** Build a minimal error response without JSON parsing. */
    fun error(message: String, sessionId: String? = null) = OpenClawResponse(
        reply     = "",
        error     = message,
        sessionId = sessionId,
    )
}
