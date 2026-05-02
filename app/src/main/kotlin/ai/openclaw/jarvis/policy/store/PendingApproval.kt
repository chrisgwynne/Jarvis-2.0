package ai.openclaw.jarvis.policy.store

import ai.openclaw.jarvis.policy.model.ActionDescriptor
import ai.openclaw.jarvis.policy.model.AutonomyLevel

/**
 * One action waiting on user approval. Persisted (so a phone reboot
 * doesn't silently drop a draft message), expires automatically when
 * the per-settings timeout elapses.
 *
 * `originRequestId` ties the approval back to whatever produced the
 * action (the OpenClaw requestId for typed actions, the session
 * commandId for local intents) so the audit logger can correlate.
 */
data class PendingApproval(
    val id: String,                       // == descriptor.id
    val descriptor: ActionDescriptor,
    val decisionLevel: AutonomyLevel,     // PREPARE or EXECUTE_WITH_CONFIRMATION
    val createdAtMillis: Long = System.currentTimeMillis(),
    val expiresAtMillis: Long,
    val originRequestId: String? = null,
    val originSessionKey: String? = null,
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAtMillis
}

/** Outcome of a user resolving an approval. */
enum class ApprovalOutcome { APPROVED, REJECTED, EXPIRED }
