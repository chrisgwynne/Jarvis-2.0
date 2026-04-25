package ai.openclaw.jarvis.statemachine

import ai.openclaw.jarvis.router.ParsedIntent
import ai.openclaw.jarvis.trust.TrustLevel
import java.util.UUID

/** Snapshot of a single spoken-command lifecycle from start to completion. */
data class CommandSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val transcript: String = "",
    val speakerId: String = "unknown",
    val trustLevel: TrustLevel = TrustLevel.UNKNOWN,
    val identityConfidence: Float = 0f,
    val intent: ParsedIntent? = null,
    val route: String = "",
    val result: String? = null,
    val error: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
) {
    val isComplete: Boolean get() = completedAt != null
    val durationMs: Long? get() = completedAt?.let { it - startedAt }
}
