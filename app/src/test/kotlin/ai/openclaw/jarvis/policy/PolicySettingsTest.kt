package ai.openclaw.jarvis.policy

import ai.openclaw.jarvis.policy.store.AutonomyMode
import ai.openclaw.jarvis.policy.store.PolicySettings
import org.junit.Assert.assertEquals
import org.junit.Test

class PolicySettingsTest {

    @Test fun `defaults match the spec`() {
        val s = PolicySettings()
        assertEquals(AutonomyMode.BALANCED, s.mode)
        assertEquals(true, s.requireConfirmAllOutbound)
        assertEquals(true, s.allowAutoExecuteSafe)
        assertEquals(true, s.quietHoursForceConfirm)
        assertEquals(5, s.approvalTimeoutMinutes)
    }

    @Test fun `cautious mode has the longest implied cooldowns`() {
        // Sanity-check the verbal description of CAUTIOUS — it doesn't add
        // cooldowns directly but should keep the user from accidentally
        // disabling outbound confirmation when they pick it.
        val s = PolicySettings(mode = AutonomyMode.CAUTIOUS)
        assertEquals(true, s.requireConfirmAllOutbound)
    }

    @Test fun `aggressive mode is opt-in`() {
        val s = PolicySettings()
        assertEquals(false, s.mode == AutonomyMode.AGGRESSIVE)
    }
}
