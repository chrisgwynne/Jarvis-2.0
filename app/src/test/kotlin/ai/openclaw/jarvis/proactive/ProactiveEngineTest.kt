package ai.openclaw.jarvis.proactive

import ai.openclaw.jarvis.proactive.engine.ProactiveEngine
import ai.openclaw.jarvis.proactive.model.ContextSnapshot
import ai.openclaw.jarvis.proactive.model.LocationLabel
import ai.openclaw.jarvis.proactive.model.ProposedAction
import ai.openclaw.jarvis.proactive.model.Signal
import ai.openclaw.jarvis.proactive.model.SignalType
import ai.openclaw.jarvis.proactive.model.SuggestionFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveEngineTest {

    private val engine = ProactiveEngine()
    private val ctx = ContextSnapshot(speakerTrustLevel = "OWNER", locationLabel = LocationLabel.HOME)

    @Test fun `left home suggests message`() {
        val s = engine.suggestionFor(Signal(SignalType.LEFT_HOME), ctx)
        assertNotNull(s)
        assertEquals(SuggestionFormat.VOICE, s!!.format)
        assertEquals(ProposedAction.Kind.SEND_MESSAGE, s.proposedAction?.kind)
    }

    @Test fun `morning suggests plan`() {
        val s = engine.suggestionFor(Signal(SignalType.MORNING), ctx)
        assertNotNull(s)
        assertTrue(s!!.body.contains("plan", ignoreCase = true))
    }

    @Test fun `evening suggests wrap up`() {
        val s = engine.suggestionFor(Signal(SignalType.EVENING), ctx)
        assertNotNull(s)
        assertEquals(ProposedAction.Kind.WRAP_UP_DAY, s!!.proposedAction?.kind)
    }

    @Test fun `repeated whatsapp suggests shortcut`() {
        val sig = Signal(SignalType.REPEATED_COMMAND_PATTERN,
            payload = mapOf("command" to "send whatsapp to Cath"))
        val s = engine.suggestionFor(sig, ctx)
        assertNotNull(s)
        assertEquals(ProposedAction.Kind.OPEN_APP_SHORTCUT, s!!.proposedAction?.kind)
    }

    @Test fun `screenshot taken offers analysis`() {
        val s = engine.suggestionFor(Signal(SignalType.SCREENSHOT_TAKEN), ctx)
        assertNotNull(s)
        assertEquals(ProposedAction.Kind.ANALYSE_LAST_SCREENSHOT, s!!.proposedAction?.kind)
    }

    @Test fun `headphones connected suggests voice mode`() {
        val s = engine.suggestionFor(Signal(SignalType.HEADPHONES_CONNECTED), ctx)
        assertNotNull(s)
        assertEquals(ProposedAction.Kind.ENABLE_VOICE_MODE, s!!.proposedAction?.kind)
    }

    @Test fun `low battery suggests power saving`() {
        val s = engine.suggestionFor(
            Signal(SignalType.LOW_BATTERY, payload = mapOf("percent" to "15")), ctx)
        assertNotNull(s)
        assertEquals(ProposedAction.Kind.ENABLE_POWER_SAVE, s!!.proposedAction?.kind)
    }

    @Test fun `signals with no rule return null`() {
        assertNull(engine.suggestionFor(Signal(SignalType.IDLE_PERIOD), ctx))
        assertNull(engine.suggestionFor(Signal(SignalType.HEADPHONES_DISCONNECTED), ctx))
        assertNull(engine.suggestionFor(Signal(SignalType.DRIVING_STOPPED), ctx))
        assertNull(engine.suggestionFor(Signal(SignalType.ARRIVED_HOME), ctx))
    }
}
