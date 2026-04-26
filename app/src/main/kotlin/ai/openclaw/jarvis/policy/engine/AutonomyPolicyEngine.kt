package ai.openclaw.jarvis.policy.engine

import ai.openclaw.jarvis.policy.model.ActionDescriptor
import ai.openclaw.jarvis.policy.model.ActionRisk
import ai.openclaw.jarvis.policy.model.AutonomyLevel
import ai.openclaw.jarvis.policy.model.PolicyDecision
import ai.openclaw.jarvis.policy.model.PolicyInput
import ai.openclaw.jarvis.policy.store.AutonomyMode
import ai.openclaw.jarvis.policy.store.CorrectionMemory
import ai.openclaw.jarvis.policy.store.PolicySettingsSource
import ai.openclaw.jarvis.trust.TrustLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single decision point: given an [ActionDescriptor] + [PolicyInput],
 * decide which rung of the autonomy ladder applies.
 *
 * Order of evaluation (each step appends to `reasons` so the audit log
 * shows what fired):
 *   1. RESTRICTED risk and per-action override are inspected first —
 *      they are the only short-circuits that can produce BLOCKED.
 *   2. Compute the baseline level from action risk.
 *   3. Apply user mode (CAUTIOUS bumps up, AGGRESSIVE down) within bounds.
 *   4. Apply context modifiers (unknown speaker → BLOCKED for HIGH,
 *      trusted contact downgrades EXECUTE_WITH_CONFIRMATION → EXECUTE_TRUSTED
 *      for SAFE actions only, low confidence forces confirm, recent
 *      correction forces confirm, OpenClaw recommendation can downgrade
 *      *but never below the user's settings*).
 *   5. Stage as PendingApproval if final level is PREPARE or
 *      EXECUTE_WITH_CONFIRMATION.
 *
 * OpenClaw can suggest, Jarvis policy decides, user approval wins —
 * the engine never returns a level higher than the user's settings allow.
 */
@Singleton
class AutonomyPolicyEngine @Inject constructor(
    private val settingsSource: PolicySettingsSource,
    private val corrections: CorrectionMemory,
) {
    fun decide(
        descriptor: ActionDescriptor,
        input: PolicyInput,
    ): PolicyDecision {
        val settings = settingsSource.current()
        val reasons = mutableListOf<String>()

        // ─── 1. Hard blocks ─────────────────────────────────────────────────
        if (descriptor.risk == ActionRisk.RESTRICTED) {
            reasons += "restricted-risk action"
            return finalise(AutonomyLevel.BLOCKED, descriptor, reasons, settings)
        }
        settings.perActionOverrides[descriptor.kind]?.let { overridden ->
            reasons += "per-action override → $overridden"
            return finalise(overridden, descriptor, reasons, settings)
        }

        // ─── 2. Risk → baseline level ───────────────────────────────────────
        var level = baseline(descriptor.risk)
        reasons += "baseline ${descriptor.risk} → $level"

        // ─── 3. User mode ────────────────────────────────────────────────────
        level = applyMode(level, descriptor.risk, settings.mode, reasons)

        // ─── 4. Context modifiers ────────────────────────────────────────────
        if (input.speakerTrust == TrustLevel.UNKNOWN && descriptor.risk != ActionRisk.SAFE) {
            reasons += "unknown speaker on non-SAFE action → BLOCKED"
            return finalise(AutonomyLevel.BLOCKED, descriptor, reasons, settings)
        }
        if (input.confidence < CONFIDENCE_FORCE_CONFIRM && level == AutonomyLevel.EXECUTE_TRUSTED) {
            reasons += "low confidence (${"%.2f".format(input.confidence)}) → confirm"
            level = AutonomyLevel.EXECUTE_WITH_CONFIRMATION
        }
        if (corrections.isRecent(descriptor.kind) && level.atLeast(AutonomyLevel.EXECUTE_TRUSTED)) {
            reasons += "recent user correction → force confirm"
            level = AutonomyLevel.EXECUTE_WITH_CONFIRMATION
        }
        if (settings.requireConfirmAllOutbound && descriptor.isOutbound() &&
            level == AutonomyLevel.EXECUTE_TRUSTED) {
            reasons += "settings: confirm all outbound"
            level = AutonomyLevel.EXECUTE_WITH_CONFIRMATION
        }
        if (!settings.allowAutoExecuteSafe && level == AutonomyLevel.EXECUTE_TRUSTED) {
            reasons += "settings: auto-execute-safe disabled → confirm"
            level = AutonomyLevel.EXECUTE_WITH_CONFIRMATION
        }
        if (settings.quietHoursForceConfirm && isQuiet(input.hourOfDay) &&
            level == AutonomyLevel.EXECUTE_TRUSTED) {
            reasons += "quiet hours → confirm"
            level = AutonomyLevel.EXECUTE_WITH_CONFIRMATION
        }
        if (input.contactTrusted && descriptor.risk == ActionRisk.HIGH &&
            level == AutonomyLevel.EXECUTE_WITH_CONFIRMATION) {
            // Trusted contacts get a small downgrade — but only ever to
            // EXECUTE_TRUSTED, never to EXECUTE bypassing safety checks
            // in a confirm-all-outbound configuration.
            if (!settings.requireConfirmAllOutbound) {
                reasons += "trusted contact → quick execute"
                level = AutonomyLevel.EXECUTE_TRUSTED
            }
        }
        if (input.locationTrusted && descriptor.risk == ActionRisk.LIMITED &&
            level == AutonomyLevel.EXECUTE_WITH_CONFIRMATION) {
            reasons += "trusted location → quick execute"
            level = AutonomyLevel.EXECUTE_TRUSTED
        }
        // OpenClaw can ONLY downgrade — never escalate past what the user's
        // settings allow.
        input.openClawSuggestedLevel?.let { suggested ->
            if (suggested.rank < level.rank) {
                reasons += "OpenClaw suggested $suggested (downgrade)"
                level = suggested
            } else if (suggested.rank > level.rank) {
                reasons += "OpenClaw suggested $suggested but local policy held"
            }
        }

        return finalise(level, descriptor, reasons, settings)
    }

    /**
     * Convenience for the audit log — bumps the descriptor's kind into
     * the recent-corrections set. Call this from the user-correction
     * detector when a user explicitly overrides a recent action.
     */
    fun markCorrected(descriptor: ActionDescriptor) =
        corrections.recordCorrection(descriptor.kind)

    // ─── Internals ──────────────────────────────────────────────────────────

    private fun baseline(risk: ActionRisk): AutonomyLevel = when (risk) {
        ActionRisk.SAFE -> AutonomyLevel.EXECUTE_TRUSTED
        ActionRisk.LIMITED -> AutonomyLevel.EXECUTE_WITH_CONFIRMATION
        ActionRisk.HIGH -> AutonomyLevel.EXECUTE_WITH_CONFIRMATION
        ActionRisk.RESTRICTED -> AutonomyLevel.BLOCKED
    }

    private fun applyMode(
        level: AutonomyLevel,
        risk: ActionRisk,
        mode: AutonomyMode,
        reasons: MutableList<String>,
    ): AutonomyLevel = when (mode) {
        AutonomyMode.CAUTIOUS -> {
            if (level == AutonomyLevel.EXECUTE_TRUSTED) {
                reasons += "cautious mode → confirm"
                AutonomyLevel.EXECUTE_WITH_CONFIRMATION
            } else level
        }
        AutonomyMode.BALANCED -> level
        AutonomyMode.AGGRESSIVE -> {
            // Aggressive only meaningful for LIMITED — HIGH stays confirm.
            if (risk == ActionRisk.LIMITED && level == AutonomyLevel.EXECUTE_WITH_CONFIRMATION) {
                reasons += "aggressive mode → quick execute"
                AutonomyLevel.EXECUTE_TRUSTED
            } else level
        }
    }

    private fun isQuiet(hour: Int): Boolean = hour < 7 || hour >= 22

    /**
     * Returns the decision *without* staging anything in the
     * pending-approval store — the caller does that via
     * [ai.openclaw.jarvis.policy.ApprovalCoordinator.stage] so it can
     * attach its own resume closure (capturing the decoded action /
     * requestId / etc.). For PREPARE / EXECUTE_WITH_CONFIRMATION the
     * decision carries the suggested expiry millis.
     */
    private fun finalise(
        level: AutonomyLevel,
        descriptor: ActionDescriptor,
        reasons: List<String>,
        settings: ai.openclaw.jarvis.policy.store.PolicySettings,
    ): PolicyDecision {
        val now = System.currentTimeMillis()
        val expires = if (level == AutonomyLevel.PREPARE ||
            level == AutonomyLevel.EXECUTE_WITH_CONFIRMATION)
            now + settings.approvalTimeoutMinutes * 60 * 1000L else null
        return PolicyDecision(level, reasons, descriptor, expiresAtMillis = expires)
    }

    companion object {
        private const val CONFIDENCE_FORCE_CONFIRM = 0.55f
    }
}

/** Whether this descriptor sends something out of the device. */
private fun ActionDescriptor.isOutbound(): Boolean = when (kind) {
    ai.openclaw.jarvis.policy.model.ActionKind.SEND_SMS,
    ai.openclaw.jarvis.policy.model.ActionKind.SEND_WHATSAPP,
    ai.openclaw.jarvis.policy.model.ActionKind.MAKE_CALL,
    ai.openclaw.jarvis.policy.model.ActionKind.SEND_EMAIL_DRAFT,
    ai.openclaw.jarvis.policy.model.ActionKind.SHARE_LOCATION_LIVE,
    ai.openclaw.jarvis.policy.model.ActionKind.SHARE_SINGLE_LOCATION,
    ai.openclaw.jarvis.policy.model.ActionKind.CREATE_CALENDAR_EVENT,
    ai.openclaw.jarvis.policy.model.ActionKind.CREATE_OPENCLAW_TASK -> true
    else -> false
}
