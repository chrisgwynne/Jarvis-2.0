package com.jarvis.githubissues.ui

import com.jarvis.githubissues.GitHubIssueLogger
import com.jarvis.githubissues.api.GitHubApiClient
import com.jarvis.githubissues.model.CapabilitySnapshot
import com.jarvis.githubissues.model.DeviceSnapshot
import com.jarvis.githubissues.model.IssueContext
import com.jarvis.githubissues.model.SessionSnapshot
import com.jarvis.githubissues.model.StateSnapshot
import com.jarvis.githubissues.queue.IssueQueue
import com.jarvis.githubissues.settings.DedupeWindow
import com.jarvis.githubissues.settings.FailureCategory
import com.jarvis.githubissues.settings.GitHubIssueSettingsRepository
import com.jarvis.githubissues.settings.RedactionSettings
import com.jarvis.githubissues.settings.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for the GitHub Issue Logging settings page. Exposes:
 *   - the persisted settings ([GitHubIssueSettingsRepository.settings])
 *   - the live queued-issue count ([IssueQueue.size])
 *   - the recent-issue log ([RecentIssueLog.entries])
 *   - actions for each control on the page (toggle / repo / token / labels /
 *     categories / severity / dedupe / redaction / test buttons).
 */
class GitHubIssueLoggingViewModel(
    private val repo: GitHubIssueSettingsRepository,
    private val client: GitHubApiClient,
    private val logger: GitHubIssueLogger,
    private val queue: IssueQueue,
    val recentLog: RecentIssueLog,
    private val deviceProvider: () -> DeviceSnapshot = { DeviceSnapshot() },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    val settings: StateFlow<com.jarvis.githubissues.settings.GitHubIssueLoggingSettings> = repo.settings
    val queuedCount: StateFlow<Int> = queue.size

    sealed class TestStatus {
        object Idle : TestStatus()
        object Running : TestStatus()
        data class Ok(val message: String) : TestStatus()
        data class Err(val message: String) : TestStatus()
    }

    private val _connectionStatus = MutableStateFlow<TestStatus>(TestStatus.Idle)
    val connectionStatus: StateFlow<TestStatus> = _connectionStatus.asStateFlow()

    private val _testIssueStatus = MutableStateFlow<TestStatus>(TestStatus.Idle)
    val testIssueStatus: StateFlow<TestStatus> = _testIssueStatus.asStateFlow()

    fun setEnabled(enabled: Boolean) = repo.update { it.copy(enabled = enabled) }
    fun setOwner(owner: String) = repo.update { it.copy(owner = owner.trim()) }
    fun setRepo(repoName: String) = repo.update { it.copy(repo = repoName.trim()) }
    fun setToken(token: String?) = repo.setToken(token?.trim())
    fun setLabels(labels: List<String>) = repo.update {
        it.copy(labels = labels.map { l -> l.trim() }.filter(String::isNotEmpty).distinct())
    }
    fun setCategoryEnabled(category: FailureCategory, enabled: Boolean) = repo.update {
        val next = if (enabled) it.enabledCategories + category else it.enabledCategories - category
        it.copy(enabledCategories = next)
    }
    fun setMinSeverity(severity: Severity) = repo.update { it.copy(minSeverity = severity) }
    fun setDedupeWindow(window: DedupeWindow) = repo.update { it.copy(dedupeWindow = window) }
    fun setIncludeDebugContext(include: Boolean) = repo.update { it.copy(includeDebugContext = include) }
    fun setRedaction(transform: (RedactionSettings) -> RedactionSettings) =
        repo.update { it.copy(redaction = transform(it.redaction)) }

    fun testConnection() {
        _connectionStatus.value = TestStatus.Running
        scope.launch {
            _connectionStatus.value = when (val r = client.testConnection()) {
                is GitHubApiClient.Result.Success -> TestStatus.Ok("Connected to ${repo.current().owner}/${repo.current().repo}")
                is GitHubApiClient.Result.Failure -> TestStatus.Err(
                    r.httpStatus?.let { "HTTP $it: ${r.message}" } ?: r.message
                )
            }
        }
    }

    fun createTestIssue() {
        _testIssueStatus.value = TestStatus.Running
        scope.launch {
            val ctx = IssueContext(
                state = StateSnapshot(current = "IDLE", route = "settings", intent = "test_issue"),
                device = deviceProvider(),
                capability = CapabilitySnapshot(),
                session = SessionSnapshot(commandId = UUID.randomUUID().toString())
            )
            val outcome = logger.createTestIssue(ctx)
            _testIssueStatus.value = when (outcome) {
                is GitHubIssueLogger.Outcome.Created -> TestStatus.Ok("Created issue #${outcome.issueNumber}")
                is GitHubIssueLogger.Outcome.Queued -> TestStatus.Ok("Queued for retry (${outcome.reason})")
                is GitHubIssueLogger.Outcome.Suppressed -> TestStatus.Ok("Suppressed by dedupe (#${outcome.existingIssueNumber ?: "?"})")
                is GitHubIssueLogger.Outcome.Skipped -> TestStatus.Err("Skipped: ${outcome.reason}")
                is GitHubIssueLogger.Outcome.Failed -> TestStatus.Err("Failed: ${outcome.reason}")
            }
        }
    }
}
