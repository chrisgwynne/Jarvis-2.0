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
data class ConnectChallengeResponseFrame(
    val type: String = "connect.challenge.response",
    val nonce: String,
    val deviceId: String,
    val ts: Long,
    val pairingToken: String? = null,
    val signature: String? = null,
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

// ─── Protocol v3 — req/res frames ────────────────────────────────────────────

@Serializable
data class ReqFrame(
    val type: String = "req",
    val id: String,
    val method: String,
    val params: JsonObject? = null,
)

@Serializable
data class ConnectReqFrame(
    val type: String = "req",
    val id: String,
    val method: String = "connect",
    val params: ConnectParams,
)

@Serializable
data class ConnectParams(
    val minProtocol: Int = 3,
    val maxProtocol: Int = 3,
    val client: ClientInfo,
    val role: String = "node",
    val scopes: List<String> = listOf("node.read", "node.write"),
    val auth: AuthInfo,
    val device: DeviceAuthInfo,
    val caps: List<String> = emptyList(),
    val commands: List<String> = emptyList(),
)

@Serializable
data class ClientInfo(
    val id: String = "jarvis-android",
    val displayName: String,
    val version: String = "1.0.0",
    val platform: String = "android",
    val deviceFamily: String = "android",
    val mode: String = "node",
    val instanceId: String,
)

@Serializable
data class AuthInfo(
    val token: String? = null,
    val deviceToken: String? = null,
)

@Serializable
data class DeviceAuthInfo(
    val id: String,
    val publicKey: String,
    val signature: String,
    val signedAt: Long,
    val nonce: String,
)

@Serializable
data class ResFrame(
    val type: String,
    val id: String,
    val ok: Boolean,
    val payload: JsonObject? = null,
    val error: JsonObject? = null,
)

// ─── chat.send / sessions.subscribe ──────────────────────────────────────────

@Serializable
data class ChatSendFrame(
    val type: String = "req",
    val id: String,
    val method: String = "chat.send",
    val params: ChatSendParams,
)

@Serializable
data class ChatSendParams(
    val sessionKey: String,
    val message: String,
    val idempotencyKey: String,
    val systemInputProvenance: SystemInputProvenance? = null,
    val imageBase64: String? = null,
)

@Serializable
data class SystemInputProvenance(
    val kind: String = "external_user",
    val sourceChannel: String = "voice",
    val sourceTool: String = "gateway.voice.transcript",
)

@Serializable
data class SessionsSubscribeFrame(
    val type: String = "req",
    val id: String,
    val method: String = "sessions.subscribe",
    val params: SessionsSubscribeParams,
)

@Serializable
data class SessionsSubscribeParams(
    val sessionKey: String,
)

// ─── Gateway connection state ─────────────────────────────────────────────────

enum class GatewayState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    PAIRING,
    OFFLINE_QUEUED,
}
