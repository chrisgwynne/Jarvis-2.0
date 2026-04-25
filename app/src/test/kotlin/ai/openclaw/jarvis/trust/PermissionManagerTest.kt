package ai.openclaw.jarvis.trust

import ai.openclaw.jarvis.router.IntentType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PermissionManagerTest {

    private lateinit var manager: PermissionManager

    @Before
    fun setUp() {
        manager = PermissionManager()
    }

    // ─── OWNER ───────────────────────────────────────────────────────────────

    @Test
    fun `OWNER allowed all SAFE actions`() {
        assertTrue(manager.isAllowed(ActionPermission.SAFE, TrustLevel.OWNER))
    }

    @Test
    fun `OWNER allowed all LIMITED actions`() {
        assertTrue(manager.isAllowed(ActionPermission.LIMITED, TrustLevel.OWNER))
    }

    @Test
    fun `OWNER allowed all RESTRICTED actions`() {
        assertTrue(manager.isAllowed(ActionPermission.RESTRICTED, TrustLevel.OWNER))
    }

    // ─── TRUSTED ─────────────────────────────────────────────────────────────

    @Test
    fun `TRUSTED allowed SAFE actions`() {
        assertTrue(manager.isAllowed(ActionPermission.SAFE, TrustLevel.TRUSTED))
    }

    @Test
    fun `TRUSTED allowed LIMITED actions`() {
        assertTrue(manager.isAllowed(ActionPermission.LIMITED, TrustLevel.TRUSTED))
    }

    @Test
    fun `TRUSTED denied RESTRICTED actions`() {
        assertFalse(manager.isAllowed(ActionPermission.RESTRICTED, TrustLevel.TRUSTED))
    }

    // ─── GUEST ───────────────────────────────────────────────────────────────

    @Test
    fun `GUEST allowed SAFE actions`() {
        assertTrue(manager.isAllowed(ActionPermission.SAFE, TrustLevel.GUEST))
    }

    @Test
    fun `GUEST denied LIMITED actions`() {
        assertFalse(manager.isAllowed(ActionPermission.LIMITED, TrustLevel.GUEST))
    }

    @Test
    fun `GUEST denied RESTRICTED actions`() {
        assertFalse(manager.isAllowed(ActionPermission.RESTRICTED, TrustLevel.GUEST))
    }

    // ─── UNKNOWN ─────────────────────────────────────────────────────────────

    @Test
    fun `UNKNOWN allowed SAFE actions`() {
        assertTrue(manager.isAllowed(ActionPermission.SAFE, TrustLevel.UNKNOWN))
    }

    @Test
    fun `UNKNOWN denied LIMITED actions`() {
        assertFalse(manager.isAllowed(ActionPermission.LIMITED, TrustLevel.UNKNOWN))
    }

    @Test
    fun `UNKNOWN denied RESTRICTED actions`() {
        assertFalse(manager.isAllowed(ActionPermission.RESTRICTED, TrustLevel.UNKNOWN))
    }

    // ─── Intent mapping ──────────────────────────────────────────────────────

    @Test
    fun `DEVICE_CONTROL requires SAFE permission`() {
        assertEquals(ActionPermission.SAFE, manager.requiredFor(IntentType.DEVICE_CONTROL))
    }

    @Test
    fun `COMMUNICATION_SEND requires LIMITED permission`() {
        assertEquals(ActionPermission.LIMITED, manager.requiredFor(IntentType.COMMUNICATION_SEND))
    }

    @Test
    fun `OPENCLAW_REQUEST requires RESTRICTED permission`() {
        assertEquals(ActionPermission.RESTRICTED, manager.requiredFor(IntentType.OPENCLAW_REQUEST))
    }

    // ─── Denial messages ─────────────────────────────────────────────────────

    @Test
    fun `denial message for RESTRICTED + UNKNOWN is non-blank`() {
        val msg = manager.denialMessage(ActionPermission.RESTRICTED, TrustLevel.UNKNOWN)
        assertTrue(msg.isNotBlank())
    }
}
