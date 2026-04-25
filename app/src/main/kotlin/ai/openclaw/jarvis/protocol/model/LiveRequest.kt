package ai.openclaw.jarvis.protocol.model

import ai.openclaw.jarvis.protocol.ProtocolVersion
import kotlinx.serialization.Serializable

// ─── Top-level request envelope ──────────────────────────────────────────────

/**
 * The single typed payload Jarvis sends to OpenClaw on every voice / text /
 * button command. The envelope (`protocolVersion`, `requestId`,
 * `sessionKey`, `timestamp`) is identical on every protocol payload —
 * see [JarvisSessionEvent] and [ai.openclaw.jarvis.protocol.model.ActionResult].
 */
@Serializable
data class JarvisLiveRequest(
    val protocolVersion: String = ProtocolVersion.CURRENT,
    val requestId: String,
    val sessionKey: String,
    val timestamp: String,
    val type: String = TYPE,
    val speaker: SpeakerInfo,
    val input: InputInfo,
    val route: RouteInfo,
    val deviceContext: DeviceContext,
    val capabilities: JarvisCapabilitySnapshot,
    val attachments: List<Attachment> = emptyList(),
    val pendingContext: PendingContext? = null,
) {
    companion object { const val TYPE = "jarvis.live_request" }
}

@Serializable
data class SpeakerInfo(
    val id: String,                   // "chris" / "cath" / "unknown"
    val trustLevel: String,           // OWNER | TRUSTED | GUEST | UNKNOWN
    val confidence: Float,            // 0.0 .. 1.0
)

@Serializable
data class InputInfo(
    val mode: String,                 // voice | text | button
    val text: String,
    val audioSource: String? = null,  // phone | bluetooth | car | wired | unknown
)

@Serializable
data class RouteInfo(
    val chosen: String,               // OPENCLAW | MIXED
    val localIntent: String? = null,
    val confidence: Float = 0f,
)

@Serializable
data class DeviceContext(
    val battery: Int,                 // 0..100, -1 if unknown
    val charging: Boolean,
    val screenState: String,          // locked | unlocked | off
    val foregroundApp: String,
    val network: String,              // wifi | mobile | offline | unknown
    val locationLabel: String,        // home | away | unknown
)

@Serializable
data class Attachment(
    val type: String,                 // screenshot | photo | location | text
    val mimeType: String? = null,
    val uri: String? = null,
    val description: String? = null,
)

@Serializable
data class PendingContext(
    val lastCommandId: String? = null,
    val pendingActionId: String? = null,
)
