package ai.openclaw.jarvis.proactive

import ai.openclaw.jarvis.proactive.model.QuietHours
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveSettingsTest {

    @Test fun `quiet hours disabled is never quiet`() {
        val q = QuietHours(enabled = false, startHour = 22, endHour = 7)
        assertFalse(q.isQuiet(2))
        assertFalse(q.isQuiet(23))
    }

    @Test fun `quiet hours wrapping midnight covers night`() {
        val q = QuietHours(enabled = true, startHour = 22, endHour = 7)
        assertTrue(q.isQuiet(23))
        assertTrue(q.isQuiet(2))
        assertTrue(q.isQuiet(6))
        assertFalse(q.isQuiet(7))
        assertFalse(q.isQuiet(15))
    }

    @Test fun `quiet hours daytime window`() {
        val q = QuietHours(enabled = true, startHour = 13, endHour = 17)
        assertTrue(q.isQuiet(13))
        assertTrue(q.isQuiet(16))
        assertFalse(q.isQuiet(17))
        assertFalse(q.isQuiet(12))
    }
}
