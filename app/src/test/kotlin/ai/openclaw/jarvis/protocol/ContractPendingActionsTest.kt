package ai.openclaw.jarvis.protocol

import ai.openclaw.jarvis.protocol.executor.ContractPendingActions
import ai.openclaw.jarvis.protocol.executor.confirmSummary
import ai.openclaw.jarvis.protocol.model.ActionRisk
import ai.openclaw.jarvis.protocol.model.ActionType
import ai.openclaw.jarvis.protocol.model.OpenClawAction
import ai.openclaw.jarvis.protocol.model.PayloadSendSms
import ai.openclaw.jarvis.protocol.validation.DecodedAction
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractPendingActionsTest {

    @Test fun `stage and consume`() {
        val store = ContractPendingActions()
        val decoded = sampleDecodedAction()
        store.stage(decoded, "r-1", "jarvis:test", "Send SMS to Cath?")
        assertNotNull(store.current())
        val consumed = store.consume()
        assertNotNull(consumed)
        assertNull(store.current())
    }

    @Test fun `confirmSummary uses spec phrase`() {
        val action = sampleAction(reason = "send SMS to Cath")
        val summary = confirmSummary(action)
        assertTrue(summary, summary.startsWith("Do you want me to "))
        assertTrue(summary, summary.contains("send sms"))
        assertTrue(summary, summary.contains("send SMS to Cath"))
    }

    @Test fun `confirmSummary works with no reason`() {
        val summary = confirmSummary(sampleAction(reason = null))
        assertTrue(summary, summary.startsWith("Do you want me to "))
    }

    private fun sampleDecodedAction() = DecodedAction(
        action = sampleAction(),
        payload = PayloadSendSms(toContactName = "Cath", message = "ok"),
    )

    private fun sampleAction(reason: String? = "send SMS to Cath") = OpenClawAction(
        actionId = "a-1",
        type = ActionType.SEND_SMS,
        payload = JsonObject(emptyMap()),
        requiresConfirmation = true,
        risk = ActionRisk.limited,
        reason = reason,
    )
}
