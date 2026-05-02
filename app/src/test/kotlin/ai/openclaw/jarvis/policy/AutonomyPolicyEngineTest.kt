package ai.openclaw.jarvis.policy

import ai.openclaw.jarvis.policy.engine.AutonomyPolicyEngine
import ai.openclaw.jarvis.policy.model.ActionDescriptor
import ai.openclaw.jarvis.policy.model.ActionKind
import ai.openclaw.jarvis.policy.model.AutonomyLevel
import ai.openclaw.jarvis.policy.model.PolicyInput
import ai.openclaw.jarvis.policy.store.AutonomyMode
import ai.openclaw.jarvis.policy.store.CorrectionMemory
import ai.openclaw.jarvis.policy.store.PolicySettings
import ai.openclaw.jarvis.policy.store.PolicySettingsSource
import ai.openclaw.jarvis.trust.TrustLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomyPolicyEngineTest {

    @Test fun `torch maps to execute trusted`() {
        val engine = newEngine()
        val d = engine.decide(descriptor(ActionKind.TORCH), input(TrustLevel.OWNER))
        assertEquals(AutonomyLevel.EXECUTE_TRUSTED, d.level)
    }

    @Test fun `text Cath maps to execute with confirmation`() {
        val engine = newEngine()
        val d = engine.decide(
            descriptor(ActionKind.SEND_SMS),
            input(TrustLevel.OWNER),
        )
        assertEquals(AutonomyLevel.EXECUTE_WITH_CONFIRMATION, d.level)
    }

    @Test fun `business email kind is restricted and blocked`() {
        val engine = newEngine()
        val d = engine.decide(descriptor(ActionKind.SEND_BUSINESS_EMAIL), input(TrustLevel.OWNER))
        assertEquals(AutonomyLevel.BLOCKED, d.level)
    }

    @Test fun `delete files is blocked`() {
        val engine = newEngine()
        val d = engine.decide(descriptor(ActionKind.DELETE_FILES), input(TrustLevel.OWNER))
        assertEquals(AutonomyLevel.BLOCKED, d.level)
    }

    @Test fun `unknown speaker blocks non-safe action`() {
        val engine = newEngine()
        val d = engine.decide(descriptor(ActionKind.SEND_SMS), input(TrustLevel.UNKNOWN))
        assertEquals(AutonomyLevel.BLOCKED, d.level)
    }

    @Test fun `unknown speaker still allows SAFE action`() {
        val engine = newEngine()
        val d = engine.decide(descriptor(ActionKind.TORCH), input(TrustLevel.UNKNOWN))
        assertEquals(AutonomyLevel.EXECUTE_TRUSTED, d.level)
    }

    @Test fun `cautious mode bumps EXECUTE_TRUSTED to confirmation`() {
        val engine = newEngine(PolicySettings(mode = AutonomyMode.CAUTIOUS))
        val d = engine.decide(descriptor(ActionKind.TORCH), input(TrustLevel.OWNER))
        assertEquals(AutonomyLevel.EXECUTE_WITH_CONFIRMATION, d.level)
    }

    @Test fun `aggressive mode downgrades LIMITED confirm to trusted`() {
        val engine = newEngine(PolicySettings(
            mode = AutonomyMode.AGGRESSIVE,
            requireConfirmAllOutbound = false,
        ))
        val d = engine.decide(descriptor(ActionKind.SHARE_SINGLE_LOCATION), input(TrustLevel.OWNER))
        assertEquals(AutonomyLevel.EXECUTE_TRUSTED, d.level)
    }

    @Test fun `per-action override wins over mode`() {
        val engine = newEngine(PolicySettings(
            perActionOverrides = mapOf(ActionKind.TORCH to AutonomyLevel.BLOCKED),
        ))
        val d = engine.decide(descriptor(ActionKind.TORCH), input(TrustLevel.OWNER))
        assertEquals(AutonomyLevel.BLOCKED, d.level)
    }

    @Test fun `OpenClaw can downgrade but not escalate`() {
        val engine = newEngine()
        val downgrade = engine.decide(
            descriptor(ActionKind.SEND_SMS),
            input(TrustLevel.OWNER, openClawSuggestedLevel = AutonomyLevel.EXECUTE_TRUSTED),
        )
        // OpenClaw says trusted, but settings.requireConfirmAllOutbound=true holds
        // → engine still ends at EXECUTE_WITH_CONFIRMATION.
        assertEquals(AutonomyLevel.EXECUTE_WITH_CONFIRMATION, downgrade.level)

        val escalate = engine.decide(
            descriptor(ActionKind.TORCH),
            input(TrustLevel.OWNER, openClawSuggestedLevel = AutonomyLevel.BLOCKED),
        )
        // Engine never lets OpenClaw escalate past local user policy.
        assertEquals(AutonomyLevel.EXECUTE_TRUSTED, escalate.level)
    }

    @Test fun `recent correction forces confirm on a SAFE action`() {
        val mem = CorrectionMemory()
        mem.recordCorrection(ActionKind.TORCH)
        val engine = AutonomyPolicyEngine(StubSettings(PolicySettings()), mem)
        val d = engine.decide(descriptor(ActionKind.TORCH), input(TrustLevel.OWNER))
        assertEquals(AutonomyLevel.EXECUTE_WITH_CONFIRMATION, d.level)
    }

    @Test fun `low confidence forces confirm`() {
        val engine = newEngine()
        val d = engine.decide(descriptor(ActionKind.TORCH),
            input(TrustLevel.OWNER, confidence = 0.3f))
        assertEquals(AutonomyLevel.EXECUTE_WITH_CONFIRMATION, d.level)
    }

    @Test fun `quiet hours bump SAFE to confirm`() {
        val engine = newEngine()
        val d = engine.decide(descriptor(ActionKind.TORCH),
            input(TrustLevel.OWNER, hourOfDay = 23))
        assertEquals(AutonomyLevel.EXECUTE_WITH_CONFIRMATION, d.level)
    }

    @Test fun `disabling auto-execute-safe forces confirm`() {
        val engine = newEngine(PolicySettings(allowAutoExecuteSafe = false))
        val d = engine.decide(descriptor(ActionKind.TORCH), input(TrustLevel.OWNER))
        assertEquals(AutonomyLevel.EXECUTE_WITH_CONFIRMATION, d.level)
    }

    @Test fun `decision carries reasons`() {
        val engine = newEngine()
        val d = engine.decide(descriptor(ActionKind.SEND_SMS), input(TrustLevel.OWNER))
        assertTrue(d.reasons.toString(), d.reasons.any { it.contains("baseline") })
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun descriptor(kind: ActionKind) = ActionDescriptor(
        id = "ap-test",
        kind = kind,
        summary = kind.name,
    )

    private fun input(
        trust: TrustLevel,
        confidence: Float = 1.0f,
        hourOfDay: Int = 12,
        openClawSuggestedLevel: AutonomyLevel? = null,
    ) = PolicyInput(
        speakerTrust = trust,
        confidence = confidence,
        hourOfDay = hourOfDay,
        openClawSuggestedLevel = openClawSuggestedLevel,
    )

    private fun newEngine(settings: PolicySettings = PolicySettings()): AutonomyPolicyEngine =
        AutonomyPolicyEngine(StubSettings(settings), CorrectionMemory())

    private class StubSettings(private val s: PolicySettings) : PolicySettingsSource {
        override fun current(): PolicySettings = s
    }
}
