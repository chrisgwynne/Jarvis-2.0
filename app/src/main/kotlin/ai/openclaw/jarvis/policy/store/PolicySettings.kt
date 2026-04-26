package ai.openclaw.jarvis.policy.store

import ai.openclaw.jarvis.policy.model.ActionKind
import ai.openclaw.jarvis.policy.model.AutonomyLevel

/**
 * User-controlled tuning for the autonomy engine.
 *
 *   `mode` is the broad stance:
 *     - CAUTIOUS  bumps EXECUTE_TRUSTED → EXECUTE_WITH_CONFIRMATION
 *     - BALANCED  default ladder
 *     - AGGRESSIVE lets a few SAFE actions auto-execute even if the
 *                 user is unverified (still never RESTRICTED)
 *
 *   `perActionOverrides` lets the user pin a specific [ActionKind] to a
 *     specific [AutonomyLevel] regardless of mode (e.g. "always confirm
 *     OPEN_APP", or "block CREATE_CALENDAR_EVENT entirely").
 *
 *   `requireConfirmAllOutbound` forces every HIGH/RESTRICTED action with
 *     an external recipient through EXECUTE_WITH_CONFIRMATION even when
 *     mode would otherwise downgrade it.
 *
 *   `allowAutoExecuteSafe` is the dual: when off, even SAFE actions get
 *     bumped one rung up.
 *
 *   Quiet hours, trusted contacts, trusted locations are inputs supplied
 *   per-call via [ai.openclaw.jarvis.policy.model.PolicyInput] — they
 *   live elsewhere in the app, this class doesn't duplicate them.
 */
data class PolicySettings(
    val mode: AutonomyMode = AutonomyMode.BALANCED,
    val perActionOverrides: Map<ActionKind, AutonomyLevel> = emptyMap(),
    val requireConfirmAllOutbound: Boolean = true,
    val allowAutoExecuteSafe: Boolean = true,
    val quietHoursForceConfirm: Boolean = true,
    val approvalTimeoutMinutes: Int = 5,
)

enum class AutonomyMode { CAUTIOUS, BALANCED, AGGRESSIVE }

interface PolicySettingsSource {
    fun current(): PolicySettings
}
