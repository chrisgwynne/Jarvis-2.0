package ai.openclaw.jarvis.protocol.model

import ai.openclaw.jarvis.protocol.ProtocolVersion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Side-channel observability event Jarvis can publish over the same
 * WebSocket — e.g. ERROR_RECOVERY entries, github_issue_created notices,
 * voice-pipeline failures. The body is opaque [JsonObject] because event
 * shapes vary; only the envelope is constrained.
 */
@Serializable
data class JarvisSessionEvent(
    val protocolVersion: String = ProtocolVersion.CURRENT,
    val requestId: String,
    val sessionKey: String,
    val timestamp: String,
    val type: String = TYPE,
    val name: String,                  // jarvis.error_recovery, jarvis.github_issue_created, ...
    val body: JsonObject = JsonObject(emptyMap()),
) {
    companion object { const val TYPE = "jarvis.session_event" }
}
