package ai.openclaw.jarvis.protocol.model

import ai.openclaw.jarvis.protocol.ProtocolVersion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Reply Jarvis sends to OpenClaw after every executed (or rejected) action.
 *
 * `status` is the spec's exact set: success | failed | cancelled |
 * permission_missing | unsupported. `result` is action-specific extras
 * (e.g. SMS recipient resolved, screenshot URI). `error` is filled when
 * status != success.
 */
@Serializable
data class JarvisActionResult(
    val protocolVersion: String = ProtocolVersion.CURRENT,
    val requestId: String,
    val sessionKey: String,
    val actionId: String,
    val timestamp: String,
    val type: String = TYPE,
    val status: ActionResultStatus,
    val result: JsonObject = JsonObject(emptyMap()),
    val error: ActionResultError? = null,
) {
    companion object { const val TYPE = "jarvis.action_result" }
}

@Serializable
enum class ActionResultStatus { success, failed, cancelled, permission_missing, unsupported }

@Serializable
data class ActionResultError(
    val code: String,
    val message: String,
)
