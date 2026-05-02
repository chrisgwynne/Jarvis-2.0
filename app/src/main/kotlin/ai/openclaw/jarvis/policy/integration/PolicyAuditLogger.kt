package ai.openclaw.jarvis.policy.integration

import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.policy.model.PolicyDecision
import ai.openclaw.jarvis.policy.store.ApprovalOutcome
import ai.openclaw.jarvis.policy.store.PendingApproval
import ai.openclaw.jarvis.protocol.client.OpenClawProtocolClient
import ai.openclaw.jarvis.protocol.model.JarvisSessionEvent
import ai.openclaw.jarvis.protocol.util.IsoTimestamp
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Audit trail for the autonomy engine. Every decision and every
 * approval outcome flows through here as a `jarvis.policy_*`
 * JarvisSessionEvent so OpenClaw can see exactly why an action was
 * permitted, deferred or blocked.
 *
 * Fire-and-forget — the engine never waits on the audit log.
 */
interface PolicyAuditLogger {
    fun logDecision(decision: PolicyDecision)
    fun logApproved(approval: PendingApproval)
    fun logRejected(approval: PendingApproval)
    fun logExpired(approval: PendingApproval)
    fun logOutcome(approval: PendingApproval, outcome: ApprovalOutcome)

    object NoOp : PolicyAuditLogger {
        override fun logDecision(decision: PolicyDecision) {}
        override fun logApproved(approval: PendingApproval) {}
        override fun logRejected(approval: PendingApproval) {}
        override fun logExpired(approval: PendingApproval) {}
        override fun logOutcome(approval: PendingApproval, outcome: ApprovalOutcome) {}
    }
}

@Singleton
class OpenClawPolicyAuditLogger @Inject constructor(
    private val protocolClient: OpenClawProtocolClient,
    private val settings: SettingsDataStore,
) : PolicyAuditLogger {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun logDecision(decision: PolicyDecision) {
        emit("jarvis.policy_decision") {
            put("actionId", JsonPrimitive(decision.descriptor.id))
            put("kind", JsonPrimitive(decision.descriptor.kind.name))
            put("risk", JsonPrimitive(decision.descriptor.risk.name))
            put("level", JsonPrimitive(decision.level.name))
            decision.pendingApprovalId?.let { put("pendingApprovalId", JsonPrimitive(it)) }
            decision.expiresAtMillis?.let { put("expiresAt", JsonPrimitive(it)) }
            put("reasons", buildJsonArray { decision.reasons.forEach { add(it) } })
        }
    }

    override fun logApproved(approval: PendingApproval) =
        logOutcome(approval, ApprovalOutcome.APPROVED)
    override fun logRejected(approval: PendingApproval) =
        logOutcome(approval, ApprovalOutcome.REJECTED)
    override fun logExpired(approval: PendingApproval) =
        logOutcome(approval, ApprovalOutcome.EXPIRED)

    override fun logOutcome(approval: PendingApproval, outcome: ApprovalOutcome) {
        emit("jarvis.policy_outcome") {
            put("actionId", JsonPrimitive(approval.id))
            put("kind", JsonPrimitive(approval.descriptor.kind.name))
            put("risk", JsonPrimitive(approval.descriptor.risk.name))
            put("decisionLevel", JsonPrimitive(approval.decisionLevel.name))
            put("outcome", JsonPrimitive(outcome.name))
            approval.originRequestId?.let { put("originRequestId", JsonPrimitive(it)) }
        }
    }

    private fun emit(name: String, body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        scope.launch {
            val sessionKey = runCatching { settings.settings.first().sessionKey }
                .getOrDefault("jarvis:user:android")
            protocolClient.sendSessionEvent(
                JarvisSessionEvent(
                    requestId = java.util.UUID.randomUUID().toString(),
                    sessionKey = sessionKey,
                    timestamp = IsoTimestamp.now(),
                    name = name,
                    body = buildJsonObject(body),
                )
            )
        }
    }
}
