package ai.openclaw.jarvis.githubissues

import ai.openclaw.jarvis.githubissues.dedupe.DedupeDecision
import ai.openclaw.jarvis.githubissues.dedupe.InMemoryDedupeStore
import ai.openclaw.jarvis.githubissues.dedupe.IssueDeduplicator
import ai.openclaw.jarvis.githubissues.model.IssueContext
import ai.openclaw.jarvis.githubissues.model.IssueEvent
import ai.openclaw.jarvis.githubissues.model.StateSnapshot
import ai.openclaw.jarvis.githubissues.settings.DedupeWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueDeduplicatorTest {

    private val ctx = IssueContext(state = StateSnapshot(current = "ROUTING", intent = "send_sms"))

    @Test fun `first event allows`() {
        val d = IssueDeduplicator(InMemoryDedupeStore())
        val decision = d.recordAndDecide(
            IssueEvent.ActionFailure("sms", "E1", "boom", ctx),
            DedupeWindow.ONE_HOUR
        )
        assertTrue(decision is DedupeDecision.Allow)
    }

    @Test fun `repeat inside window suppresses and bumps count`() {
        var time = 0L
        val d = IssueDeduplicator(InMemoryDedupeStore(), now = { time })
        val event = IssueEvent.ActionFailure("sms", "E1", "boom", ctx)

        val first = d.recordAndDecide(event, DedupeWindow.ONE_HOUR)
        assertTrue(first is DedupeDecision.Allow)

        time += 60_000 // 1 minute later
        val second = d.recordAndDecide(event, DedupeWindow.ONE_HOUR) as DedupeDecision.Suppress
        assertEquals(2, second.occurrenceCount)
    }

    @Test fun `outside window allows new issue`() {
        var time = 0L
        val d = IssueDeduplicator(InMemoryDedupeStore(), now = { time })
        val event = IssueEvent.ActionFailure("sms", "E1", "boom", ctx)
        d.recordAndDecide(event, DedupeWindow.ONE_HOUR)
        time += DedupeWindow.ONE_HOUR.millis + 1
        val again = d.recordAndDecide(event, DedupeWindow.ONE_HOUR)
        assertTrue(again is DedupeDecision.Allow)
    }

    @Test fun `different categories produce different fingerprints`() {
        val d = IssueDeduplicator(InMemoryDedupeStore())
        val a = d.fingerprint(IssueEvent.ActionFailure("sms", "E1", "boom", ctx))
        val b = d.fingerprint(IssueEvent.ActionFailure("call", "E1", "boom", ctx))
        assertNotEquals(a, b)
    }

    @Test fun `attached issue number is surfaced on suppress`() {
        var time = 0L
        val d = IssueDeduplicator(InMemoryDedupeStore(), now = { time })
        val event = IssueEvent.ActionFailure("sms", "E1", "boom", ctx)
        val first = d.recordAndDecide(event, DedupeWindow.ONE_HOUR) as DedupeDecision.Allow
        d.attachIssueNumber(first.fingerprint, 42)
        time += 1_000
        val second = d.recordAndDecide(event, DedupeWindow.ONE_HOUR) as DedupeDecision.Suppress
        assertEquals(42, second.existingIssueNumber)
    }
}
