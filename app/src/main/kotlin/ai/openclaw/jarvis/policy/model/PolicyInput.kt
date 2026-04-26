package ai.openclaw.jarvis.policy.model

import ai.openclaw.jarvis.trust.TrustLevel

/**
 * Snapshot of the inputs the engine actually consumes. Built by the
 * caller right before [ai.openclaw.jarvis.policy.engine.AutonomyPolicyEngine.decide]
 * so the engine itself stays pure.
 *
 * Fields:
 *   - speakerTrust          spec input #1
 *   - hourOfDay             spec input #5 (time of day)
 *   - confidence            spec input #5 (action confidence — STT/router)
 *   - locationTrusted       spec input "trusted locations"
 *   - contactTrusted        spec input "trusted contacts"
 *   - openClawConnected     for actions that depend on OpenClaw
 *   - openClawSuggestedLevel  spec input "OpenClaw recommendation"
 *   - recentlyCorrected     spec input "previous corrections" — when the
 *                           user has corrected this action kind in the
 *                           last few minutes, force confirmation.
 */
data class PolicyInput(
    val speakerTrust: TrustLevel = TrustLevel.UNKNOWN,
    val hourOfDay: Int = 12,
    val confidence: Float = 1.0f,
    val locationTrusted: Boolean = false,
    val contactTrusted: Boolean = false,
    val openClawConnected: Boolean = true,
    val openClawSuggestedLevel: AutonomyLevel? = null,
    val recentlyCorrected: Boolean = false,
)
