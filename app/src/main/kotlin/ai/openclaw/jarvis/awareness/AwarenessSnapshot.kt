package ai.openclaw.jarvis.awareness

/**
 * What Jarvis currently knows about itself: every Android-side action and
 * every OpenClaw-side skill, with reasons when something is unavailable.
 *
 * Built by [CapabilityAwarenessManager.snapshot] from the live system
 * state — never cached, never stale; recomputed on demand.
 */
data class AwarenessSnapshot(
    val androidActions: List<LocalAction>,
    val openClawSkills: List<OpenClawSkillStatus>,
    val openClawConnected: Boolean,
    val bluetoothMicConnected: Boolean,
    val bluetoothOutputConnected: Boolean,
    val trustLevel: String,         // OWNER / TRUSTED / GUEST / UNKNOWN
    val missingPermissions: List<MissingPermission>,
    val recommendedSetup: List<String>,
)

/**
 * One row in the "what Android things can I do right now?" answer.
 *
 * `id` matches [ai.openclaw.jarvis.capabilities.base.Capability.id] for the
 * underlying capability (e.g. "sms"); UI-friendly cases like "phone_control"
 * roll up multiple capabilities into one user-facing line.
 */
data class LocalAction(
    val id: String,
    val label: String,                   // human-readable
    val state: AvailabilityState,
    val reason: String? = null,          // populated when state != AVAILABLE
    val restrictedByTrust: Boolean = false,
)

enum class AvailabilityState {
    AVAILABLE,
    PERMISSION_MISSING,
    NOT_INSTALLED,
    HARDWARE_MISSING,
    OFFLINE,
    DISABLED_BY_TRUST,
    UNKNOWN,
}

data class OpenClawSkillStatus(
    val id: String,
    val name: String,
    val description: String,
    val state: AvailabilityState,
    val reason: String? = null,
)

data class MissingPermission(
    val capabilityId: String,
    val permission: String,
    val rationale: String,
)
