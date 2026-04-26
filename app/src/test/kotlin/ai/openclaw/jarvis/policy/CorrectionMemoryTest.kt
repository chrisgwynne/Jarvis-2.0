package ai.openclaw.jarvis.policy

import ai.openclaw.jarvis.policy.model.ActionKind
import ai.openclaw.jarvis.policy.store.CorrectionMemory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionMemoryTest {

    @Test fun `unrecorded kind is not recent`() =
        assertFalse(CorrectionMemory().isRecent(ActionKind.SEND_SMS))

    @Test fun `recent within window`() {
        val mem = CorrectionMemory()
        mem.recordCorrection(ActionKind.SEND_SMS, nowMillis = 0L)
        assertTrue(mem.isRecent(ActionKind.SEND_SMS, nowMillis = 5_000L))
    }

    @Test fun `not recent past window`() {
        val mem = CorrectionMemory()
        mem.recordCorrection(ActionKind.SEND_SMS, nowMillis = 0L)
        // Window is 10 minutes — 11 minutes later it's stale.
        assertFalse(mem.isRecent(ActionKind.SEND_SMS, nowMillis = 11L * 60 * 1000))
    }

    @Test fun `reset clears`() {
        val mem = CorrectionMemory()
        mem.recordCorrection(ActionKind.SEND_SMS)
        mem.reset()
        assertFalse(mem.isRecent(ActionKind.SEND_SMS))
    }
}
