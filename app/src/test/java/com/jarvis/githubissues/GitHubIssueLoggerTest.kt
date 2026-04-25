package com.jarvis.githubissues

import com.jarvis.githubissues.api.GitHubApiClient
import com.jarvis.githubissues.api.IssueBodyBuilder
import com.jarvis.githubissues.dedupe.InMemoryDedupeStore
import com.jarvis.githubissues.dedupe.IssueDeduplicator
import com.jarvis.githubissues.integration.OpenClawSessionBridge
import com.jarvis.githubissues.model.IssueContext
import com.jarvis.githubissues.model.IssueDraft
import com.jarvis.githubissues.model.IssueEvent
import com.jarvis.githubissues.model.StateSnapshot
import com.jarvis.githubissues.queue.IssueQueue
import com.jarvis.githubissues.redaction.RedactionPolicy
import com.jarvis.githubissues.redaction.Redactor
import com.jarvis.githubissues.settings.FailureCategory
import com.jarvis.githubissues.settings.GitHubIssueLoggingSettings
import com.jarvis.githubissues.settings.RedactionSettings
import com.jarvis.githubissues.settings.Severity
import com.jarvis.githubissues.settings.SettingsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GitHubIssueLoggerTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun newLogger(
        settings: GitHubIssueLoggingSettings,
        client: FakeClient = FakeClient()
    ): Triple<GitHubIssueLogger, FakeClient, IssueQueue> {
        val source = StubSettings(settings, "fake-token")
        val queue = IssueQueue(File(tmp.newFolder(), "queue.json"))
        val deduper = IssueDeduplicator(InMemoryDedupeStore())
        val builder = IssueBodyBuilder(
            redactor = Redactor(RedactionPolicy(settings.redaction)),
            settings = { settings }
        )
        val logger = GitHubIssueLogger(
            settingsRepo = source,
            deduper = deduper,
            bodyBuilder = builder,
            client = client,
            queue = queue,
            openClaw = OpenClawSessionBridge.NoOp
        )
        return Triple(logger, client, queue)
    }

    @Test fun `disabled logger skips`() {
        val (logger, _, _) = newLogger(GitHubIssueLoggingSettings(enabled = false))
        val out = logger.onCantDoThat("answer call", "no API", IssueContext())
        assertTrue(out is GitHubIssueLogger.Outcome.Skipped)
    }

    @Test fun `below min severity skips`() {
        val s = configured().copy(minSeverity = Severity.CRITICAL)
        val (logger, client, _) = newLogger(s)
        val out = logger.onCantDoThat("x", "y", IssueContext())
        assertTrue(out is GitHubIssueLogger.Outcome.Skipped)
        assertEquals(0, client.created.size)
    }

    @Test fun `disabled category skips`() {
        val s = configured().copy(enabledCategories = setOf(FailureCategory.ERROR))
        val (logger, client, _) = newLogger(s)
        val out = logger.onCantDoThat("x", "y", IssueContext())
        assertTrue(out is GitHubIssueLogger.Outcome.Skipped)
        assertEquals(0, client.created.size)
    }

    @Test fun `creates issue end to end`() {
        val (logger, client, _) = newLogger(configured())
        val out = logger.onCantDoThat("answer call", "Android restriction", IssueContext())
        assertTrue(out is GitHubIssueLogger.Outcome.Created)
        assertEquals(1, client.created.size)
        val draft = client.created.single()
        assertTrue(draft.title.startsWith("[Jarvis][warning][cant_do_that] "))
    }

    @Test fun `transient failure enqueues`() {
        val client = FakeClient(failOnce = GitHubApiClient.Result.Failure(503, "down", transient = true))
        val (logger, _, queue) = newLogger(configured(), client)
        val out = logger.onCantDoThat("x", "y", IssueContext())
        assertTrue(out is GitHubIssueLogger.Outcome.Queued)
        assertEquals(1, queue.count())
    }

    @Test fun `terminal failure does not enqueue`() {
        val client = FakeClient(failOnce = GitHubApiClient.Result.Failure(401, "bad token", transient = false))
        val (logger, _, queue) = newLogger(configured(), client)
        val out = logger.onCantDoThat("x", "y", IssueContext())
        assertTrue(out is GitHubIssueLogger.Outcome.Failed)
        assertEquals(0, queue.count())
    }

    @Test fun `repeat suppresses and comments on existing issue`() {
        val client = FakeClient()
        val (logger, c, _) = newLogger(configured(), client)
        val ctx = IssueContext(state = StateSnapshot(current = "ROUTE", intent = "x"))
        val first = logger.onActionFailure("sms", "E1", "boom", ctx)
        assertTrue(first is GitHubIssueLogger.Outcome.Created)
        val second = logger.onActionFailure("sms", "E1", "boom", ctx)
        assertTrue(second is GitHubIssueLogger.Outcome.Suppressed)
        assertEquals(1, c.created.size)
        assertEquals(1, c.comments.size)
    }

    @Test fun `force flag bypasses gating`() {
        val (logger, c, _) = newLogger(configured().copy(enabled = false))
        val out = logger.report(
            IssueEvent.CantDoThat("x", "y", IssueContext()),
            force = true
        )
        assertTrue(out is GitHubIssueLogger.Outcome.Created)
        assertEquals(1, c.created.size)
    }

    private fun configured() = GitHubIssueLoggingSettings(
        enabled = true,
        owner = "octocat",
        repo = "jarvis",
        tokenConfigured = true,
        labels = listOf("jarvis"),
        minSeverity = Severity.INFO,
        redaction = RedactionSettings()
    )

    // ---- fakes -----------------------------------------------------------

    private class StubSettings(
        private val s: GitHubIssueLoggingSettings,
        private val t: String?
    ) : SettingsSource {
        override fun current(): GitHubIssueLoggingSettings = s
        override fun token(): String? = t
    }

    private class FakeClient(
        private val failOnce: GitHubApiClient.Result.Failure? = null
    ) : GitHubApiClient(StubSettings(GitHubIssueLoggingSettings(), null)) {
        var created = mutableListOf<IssueDraft>()
        var comments = mutableListOf<Pair<Int, String>>()
        private var nextNumber = 100
        private var failed = false

        override fun createIssue(draft: IssueDraft): Result {
            if (failOnce != null && !failed) { failed = true; return failOnce }
            created += draft
            return Result.Success(nextNumber++, "https://example.com/issues/${nextNumber - 1}")
        }

        override fun commentOnIssue(issueNumber: Int, body: String): Result {
            comments += issueNumber to body
            return Result.Success(issueNumber, "https://example.com/issues/$issueNumber")
        }

        override fun testConnection(): Result = Result.Success(0, "")
    }
}
