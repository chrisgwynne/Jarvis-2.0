package ai.openclaw.jarvis.proactive

import ai.openclaw.jarvis.proactive.engine.SignalEngine
import ai.openclaw.jarvis.proactive.model.BtDevice
import ai.openclaw.jarvis.proactive.model.ContextSnapshot
import ai.openclaw.jarvis.proactive.model.LocationLabel
import ai.openclaw.jarvis.proactive.model.Movement
import ai.openclaw.jarvis.proactive.model.SignalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalEngineTest {

    @Test fun `leaving home fires LEFT_HOME`() {
        val engine = SignalEngine()
        engine.process(ContextSnapshot(locationLabel = LocationLabel.HOME, hourOfDay = 10))
        val signals = engine.process(ContextSnapshot(locationLabel = LocationLabel.AWAY, hourOfDay = 10))
        assertTrue(signals.any { it.type == SignalType.LEFT_HOME })
    }

    @Test fun `arriving home fires ARRIVED_HOME`() {
        val engine = SignalEngine()
        engine.process(ContextSnapshot(locationLabel = LocationLabel.AWAY, hourOfDay = 10))
        val signals = engine.process(ContextSnapshot(locationLabel = LocationLabel.HOME, hourOfDay = 10))
        assertTrue(signals.any { it.type == SignalType.ARRIVED_HOME })
    }

    @Test fun `headphones connecting fires once`() {
        val engine = SignalEngine()
        engine.process(ContextSnapshot(headphonesConnected = false, hourOfDay = 10))
        val first = engine.process(ContextSnapshot(headphonesConnected = true, hourOfDay = 10))
        val second = engine.process(ContextSnapshot(headphonesConnected = true, hourOfDay = 10))
        assertEquals(1, first.count { it.type == SignalType.HEADPHONES_CONNECTED })
        assertEquals(0, second.count { it.type == SignalType.HEADPHONES_CONNECTED })
    }

    @Test fun `repeated identical commands fire pattern signal`() {
        val engine = SignalEngine()
        engine.process(ContextSnapshot(hourOfDay = 10))
        val sigs = engine.process(
            ContextSnapshot(
                hourOfDay = 10,
                recentCommands = listOf("send whatsapp to cath", "send whatsapp to cath", "send whatsapp to cath"),
            )
        )
        assertTrue(sigs.any { it.type == SignalType.REPEATED_COMMAND_PATTERN })
    }

    @Test fun `screenshot taken fires once per timestamp`() {
        val engine = SignalEngine()
        engine.process(ContextSnapshot(hourOfDay = 10))
        val ts = 12345L
        val a = engine.process(ContextSnapshot(hourOfDay = 10, recentScreenshotMillis = ts))
        val b = engine.process(ContextSnapshot(hourOfDay = 10, recentScreenshotMillis = ts))
        assertEquals(1, a.count { it.type == SignalType.SCREENSHOT_TAKEN })
        assertEquals(0, b.count { it.type == SignalType.SCREENSHOT_TAKEN })
    }

    @Test fun `low battery fires when crossing under 20`() {
        val engine = SignalEngine()
        engine.process(ContextSnapshot(hourOfDay = 10, batteryPercent = 50, charging = false))
        val sigs = engine.process(ContextSnapshot(hourOfDay = 10, batteryPercent = 15, charging = false))
        assertTrue(sigs.any { it.type == SignalType.LOW_BATTERY })
    }

    @Test fun `low battery does not fire while charging`() {
        val engine = SignalEngine()
        engine.process(ContextSnapshot(hourOfDay = 10, batteryPercent = 50, charging = false))
        val sigs = engine.process(ContextSnapshot(hourOfDay = 10, batteryPercent = 15, charging = true))
        assertTrue(sigs.none { it.type == SignalType.LOW_BATTERY })
    }

    @Test fun `driving started transitions emit signal`() {
        val engine = SignalEngine()
        engine.process(ContextSnapshot(hourOfDay = 10, movement = Movement.STATIONARY))
        val sigs = engine.process(ContextSnapshot(hourOfDay = 10, movement = Movement.DRIVING,
            bluetoothDevice = BtDevice.CAR))
        assertTrue(sigs.any { it.type == SignalType.DRIVING_STARTED })
    }
}
