package ai.openclaw.jarvis.policy.model

/**
 * Output of [ai.openclaw.jarvis.policy.engine.AutonomyPolicyEngine.decide].
 *
 * `level` is the final ladder rung; `reasons` is a small audit trail of
 * the rules that fired (e.g. "owner trust upgraded", "trusted contact
 * downgraded confirmation"). The audit log mirrors `reasons` to OpenClaw
 * so the backend can see *why* a given action was permitted or blocked.
 */
data class PolicyDecision(
    val level: AutonomyLevel,
    val reasons: List<String>,
    val descriptor: ActionDescriptor,
    val pendingApprovalId: String? = null, // populated when level=PREPARE/EXECUTE_WITH_CONFIRMATION
    val expiresAtMillis: Long? = null,
)
