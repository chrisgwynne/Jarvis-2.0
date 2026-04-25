package ai.openclaw.jarvis.statemachine

import ai.openclaw.jarvis.router.IntentType
import ai.openclaw.jarvis.router.ParsedIntent
import ai.openclaw.jarvis.trust.TrustLevel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PendingActionManagerTest {

    private lateinit var manager: PendingActionManager

    private val dummyIntent = ParsedIntent(
        type       = IntentType.COMMUNICATION_SEND,
        confidence = 0.95f,
        rawText    = "send message to Chris",
        contact    = "Chris",
        messageBody = "hello",
    )

    @Before
    fun setUp() {
        manager = PendingActionManager()
    }

    @Test
    fun `hasPending returns false when nothing staged`() {
        assertFalse(manager.hasPending())
    }

    @Test
    fun `stage creates a pending action`() {
        manager.stage(dummyIntent, "Send SMS to Chris", "chris", TrustLevel.OWNER)
        assertTrue(manager.hasPending())
    }

    @Test
    fun `confirm utterance resolves to Confirmed`() {
        manager.stage(dummyIntent, "Send SMS to Chris", "chris", TrustLevel.OWNER)
        val result = manager.tryResolve("yes", "chris")
        assertTrue(result is ResolveResult.Confirmed)
        assertFalse(manager.hasPending())
    }

    @Test
    fun `deny utterance resolves to Denied`() {
        manager.stage(dummyIntent, "Send SMS to Chris", "chris", TrustLevel.OWNER)
        val result = manager.tryResolve("cancel", "chris")
        assertTrue(result is ResolveResult.Denied)
        assertFalse(manager.hasPending())
    }

    @Test
    fun `unrecognised utterance leaves pending intact`() {
        manager.stage(dummyIntent, "Send SMS to Chris", "chris", TrustLevel.OWNER)
        val result = manager.tryResolve("what time is it", "chris")
        assertTrue(result is ResolveResult.Unrecognised)
        assertTrue(manager.hasPending())
    }

    @Test
    fun `wrong speaker resolves to WrongSpeaker`() {
        manager.stage(dummyIntent, "Send SMS to Chris", "chris", TrustLevel.OWNER)
        val result = manager.tryResolve("yes", "alex")
        assertTrue(result is ResolveResult.WrongSpeaker)
        assertTrue(manager.hasPending())
    }

    @Test
    fun `NoPending returned when nothing staged`() {
        val result = manager.tryResolve("yes", "chris")
        assertTrue(result is ResolveResult.NoPending)
    }

    @Test
    fun `cancel clears pending action`() {
        manager.stage(dummyIntent, "Send SMS to Chris", "chris", TrustLevel.OWNER)
        manager.cancel()
        assertFalse(manager.hasPending())
    }

    @Test
    fun `expired action returns Expired`() {
        manager.stage(dummyIntent, "Send SMS to Chris", "chris", TrustLevel.OWNER, timeoutMs = -1L)
        val result = manager.tryResolve("yes", "chris")
        assertTrue(result is ResolveResult.Expired)
    }
}
