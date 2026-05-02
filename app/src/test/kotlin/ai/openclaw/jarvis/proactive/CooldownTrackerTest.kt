package ai.openclaw.jarvis.proactive

import ai.openclaw.jarvis.proactive.model.Aggressiveness
import ai.openclaw.jarvis.proactive.model.SignalType
import ai.openclaw.jarvis.proactive.store.CooldownTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class CooldownTrackerTest {

    @Test fun `first allow is true and second within window is false`() {
        val t = CooldownTracker()
        val now = 1_000L
        assertTrue(t.allow(SignalType.LEFT_HOME, Aggressiveness.MEDIUM, now))
        t.recordShown(SignalType.LEFT_HOME, now)
        assertFalse(t.allow(SignalType.LEFT_HOME, Aggressiveness.MEDIUM, now + 1_000))
    }

    @Test fun `cooldown expires after per-signal window`() {
        val t = CooldownTracker()
        val now = 1_000L
        t.recordShown(SignalType.LEFT_HOME, now)
        val later = now + Aggressiveness.MEDIUM.perSignalCooldownMillis() + 1
        assertTrue(t.allow(SignalType.LEFT_HOME, Aggressiveness.MEDIUM, later))
    }

    @Test fun `global cap blocks regardless of per-signal cooldown`() {
        val t = CooldownTracker()
        var clock = 1_000L
        repeat(Aggressiveness.LOW.maxSuggestionsPerHour()) {
            assertTrue(t.allow(SignalType.values().random(), Aggressiveness.LOW, clock))
            t.recordShown(SignalType.values().random(), clock)
            clock += 1_000
        }
        // Next attempt with a fresh signal type — global cap still blocks.
        assertFalse(t.allow(SignalType.IDLE_PERIOD, Aggressiveness.LOW, clock))
    }

    @Test fun `dismiss recorded`() {
        val t = CooldownTracker()
        assertNull(t.lastDismissedAt("foo"))
        t.recordDismissed("foo", 42)
        assertEquals(42L, t.lastDismissedAt("foo"))
    }
}
