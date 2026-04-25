package ai.openclaw.jarvis.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ─── Outbound frames (Android → Gateway) ─────────────────────────────────────

@Serializable
data class NodeConnectFrame(
    val type: String = "node.connect",
    val role: String = "node",
    val deviceId: String,
    val deviceName: String,
    val pairingToken: String? = null,
    val capabilities: List<NodeCapabilityAd>,
)

@Serializable
data class NodeCapabilityAd(
    val id: String,
    val description: String,
    val available: Boolean,
    val requiresPermission: Boolean,
)

@Serializable
data class HeartbeatFrame(
    val type: String = "heartbeat",
)

@Serializable
data class UserMessageFrame(
    val type: String = "user.message",
    val sessionKey: String,
    val text: String,
    val mode: String = "voice",
    val eventId: String,
    val speaker: String? = null,
    val trustLevel: String? = null,
    val identityConfidence: Float? = null,
)

@Serializable
data class PairingRequestFrame(
    val type: String = "pairing.request",
    val deviceId: String,
    val deviceName: String,
)

@Serializable
data class NodeInvokeResultFrame(
    val type: String = "node.invoke.result",
    val correlationId: String,
    val status: String,          // "success" | "error"
    val result: JsonObject? = null,
    val error: String? = null,
)

// ─── Inbound frames (Gateway → Android) ──────────────────────────────────────

@Serializable
data class GatewayFrame(
    val type: String,
    val payload: JsonObject? = null,
)

@Serializable
data class GatewayConnectedFrame(
    val type: String,
    val sessionKey: String,
    val token: String? = null,
    val nodeId: String? = null,
)

@Serializable
data class GatewayResponseFrame(
    val type: String,
    val eventId: String? = null,
    val text: String? = null,
    val spokenReply: String? = null,
    val sessionKey: String? = null,
)

@Serializable
data class NodeInvokeFrame(
    val type: String,                 // "node.invoke"
    val correlationId: String,
    val action: String,
    val params: JsonObject? = null,
)

@Serializable
data class PairingChallengeFrame(
    val type: String,                 // "pairing.challenge"
    val code: String,
    val expiresIn: Int,               // seconds
)

@Serializable
data class HeartbeatAckFrame(
    val type: String,                 // "heartbeat.ack"
)

// ─── Gateway connection state ─────────────────────────────────────────────────

enum class GatewayState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    PAIRING,
    OFFLINE_QUEUED,
}
