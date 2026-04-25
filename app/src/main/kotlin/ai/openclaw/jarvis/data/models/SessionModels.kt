package ai.openclaw.jarvis.data.models

import kotlinx.serialization.Serializable

// ─── Route decision ──────────────────────────────────────────────────────────

enum class RouteChoice { ANDROID_LOCAL, OPENCLAW, MIXED }

@Serializable
data class RouteDecision(
    val chosen: RouteChoice,
    val intent: String,
    val confidence: Float,
)

// ─── Android context snapshot ─────────────────────────────────────────────────

@Serializable
data class AndroidContext(
    val device: String,
    val battery: String,
    val screenState: String,
    val foregroundApp: String,
    val locationLabel: String,
)

// ─── Input ────────────────────────────────────────────────────────────────────

@Serializable
data class SessionInput(
    val mode: String = "voice",
    val text: String,
)

// ─── Result ───────────────────────────────────────────────────────────────────

@Serializable
data class SessionResult(
    val status: String,       // "success" | "error"
    val spokenReply: String,
    val error: String? = null,
)

// ─── Speaker identity context ─────────────────────────────────────────────────

@Serializable
data class SpeakerContext(
    val speakerId: String,
    val trustLevel: String,
    val confidence: Float,
    val verificationMethod: String,
)

// ─── Full session event (sent to OpenClaw) ────────────────────────────────────

@Serializable
data class SessionEvent(
    val type: String = "jarvis.session_event",
    val eventId: String,
    val sessionKey: String,
    val timestamp: String,
    val speaker: String,
    val input: SessionInput,
    val route: RouteDecision,
    val androidContext: AndroidContext,
    val result: SessionResult,
    val memoryCandidate: Boolean,
    val speakerContext: SpeakerContext? = null,
)

// ─── Queued (offline) event wrapper ───────────────────────────────────────────

@Serializable
data class QueuedEvent(
    val event: SessionEvent,
    val queuedAt: String,
    val attempts: Int = 0,
)
