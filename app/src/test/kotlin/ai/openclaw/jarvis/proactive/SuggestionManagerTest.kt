package ai.openclaw.jarvis.proactive

import ai.openclaw.jarvis.proactive.engine.ProactiveEngine
import ai.openclaw.jarvis.proactive.engine.SignalEngine
import ai.openclaw.jarvis.proactive.integration.ProactiveLogger
import ai.openclaw.jarvis.proactive.model.Aggressiveness
import ai.openclaw.jarvis.proactive.model.ContextSnapshot
import ai.openclaw.jarvis.proactive.model.LocationLabel
import ai.openclaw.jarvis.proactive.model.ProactiveSettings
import ai.openclaw.jarvis.proactive.model.QuietHours
import ai.openclaw.jarvis.proactive.model.SignalType
import ai.openclaw.jarvis.proactive.model.SuggestionFormat
import ai.openclaw.jarvis.proactive.store.CooldownTracker
import ai.openclaw.jarvis.proactive.store.ProactiveSettingsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionManagerTest {

    @Test fun `leaving home produces a voice suggestion`() {
        val mgr = newManager()
        // baseline at home
        mgr.ingest(ctx(loc = LocationLabel.HOME, trust = "OWNER"))
        // step away
        mgr.ingest(ctx(loc = LocationLabel.AWAY, trust = "OWNER"))
        val s = mgr.active.value
        assertNotNull(s)
        assertEquals(SignalType.LEFT_HOME, s!!.signalType)
        assertEquals(SuggestionFormat.VOICE, s.format)
    }

    @Test fun `unknown speaker blocks all suggestions`() {
        val mgr = newManager()
        mgr.ingest(ctx(loc = LocationLabel.HOME, trust = "UNKNOWN"))
        mgr.ingest(ctx(loc = LocationLabel.AWAY, trust = "UNKNOWN"))
        assertNull(mgr.active.value)
    }

    @Test fun `dismissed suggestion clears active and ignored on cooldown`() {
        val mgr = newManager()
        mgr.ingest(ctx(loc = LocationLabel.HOME, trust = "OWNER"))
        mgr.ingest(ctx(loc = LocationLabel.AWAY, trust = "OWNER"))
        val first = mgr.active.value!!
        mgr.dismiss(first)
        assertNull(mgr.active.value)
        // Within cooldown the same signal does not re-trigger.
        mgr.ingest(ctx(loc = LocationLabel.HOME, trust = "OWNER"))
        mgr.ingest(ctx(loc = LocationLabel.AWAY, trust = "OWNER"))
        assertNull(mgr.active.value)
    }

    @Test fun `accept clears active`() {
        val mgr = newManager()
        mgr.ingest(ctx(loc = LocationLabel.HOME, trust = "OWNER"))
        mgr.ingest(ctx(loc = LocationLabel.AWAY, trust = "OWNER"))
        val first = mgr.active.value!!
        mgr.accept(first)
        assertNull(mgr.active.value)
    }

    @Test fun `headphones connect does not spam after second connect`() {
        val mgr = newManager()
        mgr.ingest(ctx(loc = LocationLabel.HOME, trust = "OWNER", headphones = false))
        mgr.ingest(ctx(loc = LocationLabel.HOME, trust = "OWNER", headphones = true))
        val first = mgr.active.value
        assertNotNull(first)
        mgr.dismiss(first!!)
        // Headphones-disconnected has no suggestion mapped, so this is fine.
        mgr.ingest(ctx(loc = LocationLabel.HOME, trust = "OWNER", headphones = false))
        // Re-connect immediately — cooldown blocks the second show.
        mgr.ingest(ctx(loc = LocationLabel.HOME, trust = "OWNER", headphones = true))
        assertNull(mgr.active.value)
    }

    @Test fun `quiet hours block suggestions`() {
        val source = StubSource(
            ProactiveSettings(
                quietHours = QuietHours(enabled = true, startHour = 22, endHour = 7),
            )
        )
        val mgr = newManager(source = source)
        // 23:00 local → quiet
        mgr.ingest(ctx(loc = LocationLabel.HOME, trust = "OWNER", hour = 23))
        mgr.ingest(ctx(loc = LocationLabel.AWAY, trust = "OWNER", hour = 23))
        assertNull(mgr.active.value)
    }

    @Test fun `per-signal disable blocks suggestion`() {
        val source = StubSource(
            ProactiveSettings(
                perSignal = mapOf(SignalType.LEFT_HOME to false),
            )
        )
        val mgr = newManager(source = source)
        mgr.ingest(ctx(loc = LocationLabel.HOME, trust = "OWNER"))
        mgr.ingest(ctx(loc = LocationLabel.AWAY, trust = "OWNER"))
        assertNull(mgr.active.value)
    }

    @Test fun `dont-suggest-again persists via source suppress`() {
        val source = StubSource(ProactiveSettings())
        val mgr = newManager(source = source)
        mgr.ingest(ctx(loc = LocationLabel.HOME, trust = "OWNER"))
        mgr.ingest(ctx(loc = LocationLabel.AWAY, trust = "OWNER"))
        val s = mgr.active.value!!
        mgr.dismiss(s, dontSuggestAgain = true)
        assertTrue(s.id in source.current().suppressedSuggestionIds)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun ctx(
        loc: LocationLabel,
        trust: String,
        hour: Int = 10,
        headphones: Boolean = false,
    ) = ContextSnapshot(
        hourOfDay = hour,
        dayOfWeek = 1,
        locationLabel = loc,
        headphonesConnected = headphones,
        speakerTrustLevel = trust,
    )

    private fun newManager(
        source: ProactiveSettingsSource = StubSource(ProactiveSettings()),
    ): SuggestionManager {
        // Pure JVM construction: real signal/proactive engines and cooldown
        // tracker, no Hilt, no Android.
        return SuggestionManager(
            signalEngine = SignalEngine(),
            proactiveEngine = ProactiveEngine(),
            cooldowns = CooldownTracker(),
            settingsRepo = source,
            logger = ProactiveLogger.NoOp,
        )
    }

    private class StubSource(private var settings: ProactiveSettings) : ProactiveSettingsSource {
        override fun current(): ProactiveSettings = settings
        override fun suppress(suggestionId: String) {
            settings = settings.copy(suppressedSuggestionIds = settings.suppressedSuggestionIds + suggestionId)
        }
    }
}
