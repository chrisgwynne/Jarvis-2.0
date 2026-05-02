package ai.openclaw.jarvis.awareness

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AwarenessResponderTest {

    private val responder = AwarenessResponder()

    // ─── "what can you do?" ──────────────────────────────────────────────────

    @Test fun `all permissions and openclaw connected mentions everything`() {
        val ans = responder.answer(AwarenessQuestion.WhatCanYouDo, fullSnapshot())
        assertTrue(ans, ans.contains("send texts"))
        assertTrue(ans, ans.contains("send whatsapps"))
        assertTrue(ans, ans.contains("get your location"))
        assertTrue(ans, ans.contains("capture screenshots"))
        assertTrue(ans, ans.contains("OpenClaw"))
    }

    @Test fun `openclaw offline says skills are offline`() {
        val snap = fullSnapshot().copy(
            openClawConnected = false,
            openClawSkills = emptyList(),
        )
        val ans = responder.answer(AwarenessQuestion.WhatCanYouDo, snap)
        assertTrue(ans, ans.contains("offline"))
    }

    @Test fun `unknown speaker requires owner verification`() {
        val snap = fullSnapshot().copy(
            trustLevel = "UNKNOWN",
            androidActions = fullSnapshot().androidActions.map {
                if (it.id in setOf("sms", "whatsapp", "calls", "location", "contacts"))
                    it.copy(state = AvailabilityState.DISABLED_BY_TRUST,
                        reason = "Owner verification required",
                        restrictedByTrust = true)
                else it
            },
        )
        val ans = responder.answer(AwarenessQuestion.WhatCanYouDo, snap)
        assertTrue(ans, ans.contains("verification"))
    }

    @Test fun `missing sms permission is mentioned`() {
        val snap = fullSnapshot().copy(
            androidActions = fullSnapshot().androidActions.map {
                if (it.id == "sms") it.copy(state = AvailabilityState.PERMISSION_MISSING,
                    reason = "Permission not granted: SEND_SMS")
                else it
            },
        )
        val ans = responder.answer(AwarenessQuestion.WhatCanYouDo, snap)
        assertTrue(ans, ans.contains("SMS needs permission"))
        assertFalse(ans, ans.contains(", send texts"))
    }

    @Test fun `missing screenshot permission gets a caveat`() {
        val snap = fullSnapshot().copy(
            androidActions = fullSnapshot().androidActions.map {
                if (it.id == "screenshot") it.copy(state = AvailabilityState.PERMISSION_MISSING,
                    reason = "Screenshot permission not granted")
                else it
            },
        )
        val ans = responder.answer(AwarenessQuestion.WhatCanYouDo, snap)
        assertTrue(ans, ans.contains("Screenshot needs permission"))
    }

    @Test fun `empty skill manifest mentions no published skills`() {
        val snap = fullSnapshot().copy(openClawSkills = emptyList())
        val ans = responder.answer(AwarenessQuestion.WhatCanYouDo, snap)
        assertTrue(ans, ans.contains("hasn't published any skills"))
    }

    // ─── "can you X?" ────────────────────────────────────────────────────────

    @Test fun `can you whatsapp says yes when installed`() {
        val ans = responder.answer(
            AwarenessQuestion.CanYou(AwarenessQuestion.Topic.WHATSAPP),
            fullSnapshot(),
        )
        assertTrue(ans, ans.contains("send WhatsApps"))
        assertTrue(ans, ans.contains("ask before"))
    }

    @Test fun `can you whatsapp says no when not installed`() {
        val snap = fullSnapshot().copy(
            androidActions = fullSnapshot().androidActions.map {
                if (it.id == "whatsapp") it.copy(state = AvailabilityState.NOT_INSTALLED,
                    reason = "WhatsApp is not installed")
                else it
            },
        )
        val ans = responder.answer(
            AwarenessQuestion.CanYou(AwarenessQuestion.Topic.WHATSAPP), snap,
        )
        assertTrue(ans, ans.contains("isn't installed"))
        assertTrue(ans, ans.contains("SMS"))
    }

    @Test fun `can you email says offline when openclaw offline`() {
        val snap = fullSnapshot().copy(
            openClawConnected = false,
            openClawSkills = listOf(
                OpenClawSkillStatus("email.send", "Send Email", "via OpenClaw",
                    state = AvailabilityState.OFFLINE, reason = "OpenClaw is offline"),
            ),
        )
        val ans = responder.answer(
            AwarenessQuestion.CanYou(AwarenessQuestion.Topic.EMAIL), snap,
        )
        assertTrue(ans, ans.contains("offline"))
    }

    @Test fun `can you screenshot says permission needed`() {
        val snap = fullSnapshot().copy(
            androidActions = fullSnapshot().androidActions.map {
                if (it.id == "screenshot") it.copy(state = AvailabilityState.PERMISSION_MISSING,
                    reason = "Screenshot permission not granted")
                else it
            },
        )
        val ans = responder.answer(
            AwarenessQuestion.CanYou(AwarenessQuestion.Topic.SCREENSHOT), snap,
        )
        assertTrue(ans, ans.contains("after screenshot permission is granted"))
    }

    // ─── "what permissions are missing?" ─────────────────────────────────────

    @Test fun `missing permissions answer lists them`() {
        val snap = fullSnapshot().copy(
            missingPermissions = listOf(
                MissingPermission("sms", "android.permission.SEND_SMS", "Send texts"),
                MissingPermission("location", "android.permission.ACCESS_FINE_LOCATION", "Get your location"),
            ),
        )
        val ans = responder.answer(AwarenessQuestion.MissingPermissions, snap)
        assertTrue(ans, ans.contains("send texts"))
        assertTrue(ans, ans.contains("get your location"))
    }

    @Test fun `missing permissions empty is reassuring`() {
        val ans = responder.answer(AwarenessQuestion.MissingPermissions, fullSnapshot())
        assertTrue(ans, ans.contains("All Android permissions"))
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun fullSnapshot() = AwarenessSnapshot(
        androidActions = listOf(
            LocalAction("phone_control", "Control your phone", AvailabilityState.AVAILABLE),
            LocalAction("sms", "Send texts", AvailabilityState.AVAILABLE),
            LocalAction("whatsapp", "Send WhatsApps", AvailabilityState.AVAILABLE),
            LocalAction("calls", "Make calls", AvailabilityState.AVAILABLE),
            LocalAction("open_app", "Open apps", AvailabilityState.AVAILABLE),
            LocalAction("location", "Get your location", AvailabilityState.AVAILABLE),
            LocalAction("camera", "Take photos", AvailabilityState.AVAILABLE),
            LocalAction("screenshot", "Capture screenshots", AvailabilityState.AVAILABLE),
            LocalAction("contacts", "Look up contacts", AvailabilityState.AVAILABLE),
            LocalAction("notification", "Show notifications", AvailabilityState.AVAILABLE),
        ),
        openClawSkills = listOf(
            OpenClawSkillStatus("email.send", "Send Email", "Send email through OpenClaw",
                state = AvailabilityState.AVAILABLE),
            OpenClawSkillStatus("memory.search", "Memory Search", "Search OpenClaw memory",
                state = AvailabilityState.AVAILABLE),
        ),
        openClawConnected = true,
        bluetoothMicConnected = false,
        bluetoothOutputConnected = false,
        trustLevel = "OWNER",
        missingPermissions = emptyList(),
        recommendedSetup = emptyList(),
    )
}
