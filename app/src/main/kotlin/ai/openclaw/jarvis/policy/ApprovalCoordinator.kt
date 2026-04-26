package ai.openclaw.jarvis.policy

import ai.openclaw.jarvis.policy.integration.PolicyAuditLogger
import ai.openclaw.jarvis.policy.model.ActionDescriptor
import ai.openclaw.jarvis.policy.model.AutonomyLevel
import ai.openclaw.jarvis.policy.store.ApprovalOutcome
import ai.openclaw.jarvis.policy.store.PendingApproval
import ai.openclaw.jarvis.policy.store.PendingApprovalStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the lifecycle of a [PendingApproval]. The user resolves it via
 * [approve] / [reject]; an idle pruner expires anything past its
 * timeout. Each transition mirrors to [PolicyAuditLogger].
 *
 * Each `stage()` registers an in-memory `resume` closure keyed by
 * approval id. On approve, the resume closure runs — that's where the
 * caller (the typed-action handler, the local-action path, etc.) does
 * the actual work. The store still persists the descriptor so the user
 * sees the approval after a restart, but resume closures are
 * intentionally not persisted: re-running an action automatically across
 * a reboot is exactly the surprise the policy ladder exists to prevent.
 */
@Singleton
class ApprovalCoordinator @Inject constructor(
    private val store: PendingApprovalStore,
    private val audit: PolicyAuditLogger,
) {
    fun interface Resume {
        suspend fun run(descriptor: ActionDescriptor)
    }

    private val resumes = ConcurrentHashMap<String, Resume>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var prunerRunning = false

    /** Live, non-expired pending approvals — suitable for a UI list. */
    val live: StateFlow<List<PendingApproval>> = store.live

    /** Idempotent — starts the expiry pruner. */
    fun start() {
        if (prunerRunning) return
        prunerRunning = true
        scope.launch {
            while (isActive) {
                store.drainExpired().forEach { p ->
                    resumes.remove(p.id)
                    audit.logExpired(p)
                }
                delay(EXPIRY_POLL_MS)
            }
        }
    }

    /**
     * Stage a fresh approval. Returns the id — the caller can use it
     * (e.g. include in a "Reply yes when ready" prompt). [resume] runs
     * on approve.
     */
    fun stage(
        descriptor: ActionDescriptor,
        level: AutonomyLevel,
        expiresAtMillis: Long,
        originRequestId: String? = null,
        originSessionKey: String? = null,
        resume: Resume,
    ): String {
        require(level == AutonomyLevel.PREPARE || level == AutonomyLevel.EXECUTE_WITH_CONFIRMATION) {
            "Only PREPARE / EXECUTE_WITH_CONFIRMATION can be staged"
        }
        val id = "ap-${UUID.randomUUID()}"
        val approval = PendingApproval(
            id = id,
            descriptor = descriptor.copy(id = id),
            decisionLevel = level,
            expiresAtMillis = expiresAtMillis,
            originRequestId = originRequestId,
            originSessionKey = originSessionKey,
        )
        store.add(approval)
        resumes[id] = resume
        return id
    }

    suspend fun approve(id: String): ApprovalOutcome {
        val approval = store.remove(id) ?: return ApprovalOutcome.EXPIRED
        if (approval.isExpired) {
            resumes.remove(id)
            audit.logExpired(approval)
            return ApprovalOutcome.EXPIRED
        }
        audit.logApproved(approval)
        val resume = resumes.remove(id)
        resume?.run(approval.descriptor)
        return ApprovalOutcome.APPROVED
    }

    fun reject(id: String): ApprovalOutcome {
        val approval = store.remove(id) ?: return ApprovalOutcome.EXPIRED
        resumes.remove(id)
        if (approval.isExpired) {
            audit.logExpired(approval)
            return ApprovalOutcome.EXPIRED
        }
        audit.logRejected(approval)
        return ApprovalOutcome.REJECTED
    }

    companion object {
        private const val EXPIRY_POLL_MS = 30_000L
    }
}
