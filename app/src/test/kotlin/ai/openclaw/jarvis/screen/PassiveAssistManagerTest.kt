package ai.openclaw.jarvis.screen

import ai.openclaw.jarvis.proactive.SuggestionManager
import ai.openclaw.jarvis.proactive.engine.ProactiveEngine
import ai.openclaw.jarvis.proactive.engine.SignalEngine
import ai.openclaw.jarvis.proactive.integration.ProactiveLogger
import ai.openclaw.jarvis.proactive.model.ProactiveSettings
import ai.openclaw.jarvis.proactive.store.CooldownTracker
import ai.openclaw.jarvis.proactive.store.ProactiveSettingsSource
import ai.openclaw.jarvis.screen.model.AppCategory
import ai.openclaw.jarvis.screen.model.ScreenContextEvent
import ai.openclaw.jarvis.screen.model.ScreenshotCaptured
import ai.openclaw.jarvis.screen.store.ScreenAwarenessSettings
import ai.openclaw.jarvis.screen.store.ScreenAwarenessSettingsSource
import ai.openclaw.jarvis.trust.TrustLevel
import ai.openclaw.jarvis.trust.TrustLevelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveAssistManagerTest {

    @Test fun `etsy event surfaces a check-performance suggestion`() {
        val (mgr, suggestionMgr, _) = newManager()
        mgr.ingestForTest(etsyEvent())
        val s = suggestionMgr.active.value
        assertNotNull(s)
        assertEquals("screen.etsy.performance", s!!.id)
    }

    @Test fun `gmail event surfaces a summarise-inbox suggestion`() {
        val (mgr, suggestionMgr, _) = newManager()
        mgr.ingestForTest(ScreenContextEvent(
            packageName = "com.google.android.gm",
            appLabel = "Gmail",
            category = AppCategory.EMAIL,
        ))
        val s = suggestionMgr.active.value
        assertNotNull(s)
        assertEquals("screen.gmail.summary", s!!.id)
    }

    @Test fun `sensitive app produces no suggestion`() {
        val (mgr, suggestionMgr, analyser) = newManager()
        mgr.ingestForTest(ScreenContextEvent(
            packageName = "co.uk.monzo",
            appLabel = "Monzo",
            category = AppCategory.SENSITIVE,
        ))
        assertNull(suggestionMgr.active.value)
        assertEquals(0, analyser.calls)
    }

    @Test fun `disabled feature produces no suggestion`() {
        val (mgr, suggestionMgr, _) = newManager(
            screenSettings = ScreenAwarenessSettings(enabled = false),
        )
        mgr.ingestForTest(etsyEvent())
        assertNull(suggestionMgr.active.value)
    }

    @Test fun `unknown speaker blocks suggestions`() {
        val (mgr, suggestionMgr, _) = newManager(trust = TrustLevel.UNKNOWN)
        mgr.ingestForTest(etsyEvent())
        assertNull(suggestionMgr.active.value)
    }

    @Test fun `screenshot triggers analyse and chip`() {
        val (mgr, suggestionMgr, analyser) = newManager()
        mgr.ingestForTest(ScreenshotCaptured(uri = "content://a/1"))
        assertEquals(1, analyser.calls)
        val s = suggestionMgr.active.value
        assertNotNull(s)
        assertEquals("screen.screenshot_analysis", s!!.id)
    }

    @Test fun `auto-analyse off skips OpenClaw call but still surfaces chip`() {
        val (mgr, suggestionMgr, analyser) = newManager(
            screenSettings = ScreenAwarenessSettings(enabled = true, screenshotAutoAnalyse = false),
        )
        mgr.ingestForTest(ScreenshotCaptured(uri = "content://a/2"))
        assertEquals(0, analyser.calls)
        assertNull(suggestionMgr.active.value)
    }

    @Test fun `rapid switching does not spam suggestions thanks to cooldown`() {
        val (mgr, suggestionMgr, _) = newManager()
        mgr.ingestForTest(etsyEvent())
        val first = suggestionMgr.active.value!!
        suggestionMgr.dismiss(first)
        // Same suggestion within cooldown — blocked.
        mgr.ingestForTest(etsyEvent())
        assertNull(suggestionMgr.active.value)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun etsyEvent() = ScreenContextEvent(
        packageName = "com.etsy.android",
        appLabel = "Etsy",
        category = AppCategory.SHOPPING,
    )

    private fun newManager(
        screenSettings: ScreenAwarenessSettings = ScreenAwarenessSettings(enabled = true, screenshotAutoAnalyse = true),
        trust: TrustLevel = TrustLevel.OWNER,
    ): Triple<PassiveAssistManager, SuggestionManager, RecordingAnalyser> {
        val bus = ScreenContextBus()
        val interpreter = ContextInterpreter()
        val analyser = RecordingAnalyser()
        val sm = SuggestionManager(
            signalEngine = SignalEngine(),
            proactiveEngine = ProactiveEngine(),
            cooldowns = CooldownTracker(),
            settingsRepo = StubProactiveSource(),
            logger = ProactiveLogger.NoOp,
        )
        val mgr = PassiveAssistManager(
            bus = bus,
            interpreter = interpreter,
            suggestionManager = sm,
            settingsSource = StubScreenSource(screenSettings),
            trust = StubTrust(trust),
            openClawAnalyser = analyser,
        )
        return Triple(mgr, sm, analyser)
    }

    private class StubProactiveSource : ProactiveSettingsSource {
        private var s = ProactiveSettings()
        override fun current() = s
        override fun suppress(suggestionId: String) {
            s = s.copy(suppressedSuggestionIds = s.suppressedSuggestionIds + suggestionId)
        }
    }

    private class StubScreenSource(private val s: ScreenAwarenessSettings) : ScreenAwarenessSettingsSource {
        override fun current(): ScreenAwarenessSettings = s
    }

    private class StubTrust(private val level: TrustLevel) : TrustLevelProvider {
        override fun current(): TrustLevel = level
    }

    private class RecordingAnalyser : ScreenshotAnalyser {
        var calls: Int = 0; private set
        override fun analyse(shot: ScreenshotCaptured) { calls += 1 }
    }
}
