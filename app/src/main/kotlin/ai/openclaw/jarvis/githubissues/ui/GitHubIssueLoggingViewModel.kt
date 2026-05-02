package ai.openclaw.jarvis.githubissues.ui

import ai.openclaw.jarvis.githubissues.GitHubIssueLogger
import ai.openclaw.jarvis.githubissues.api.GitHubApiClient
import ai.openclaw.jarvis.githubissues.integration.IssueContextBuilder
import ai.openclaw.jarvis.githubissues.queue.IssueQueue
import ai.openclaw.jarvis.githubissues.settings.DedupeWindow
import ai.openclaw.jarvis.githubissues.settings.FailureCategory
import ai.openclaw.jarvis.githubissues.settings.GitHubIssueSettingsRepository
import ai.openclaw.jarvis.githubissues.settings.RedactionSettings
import ai.openclaw.jarvis.githubissues.settings.Severity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the GitHub Issue Logging settings section. Exposes:
 *   - the persisted settings ([GitHubIssueSettingsRepository.settings])
 *   - the live queued-issue count ([IssueQueue.size])
 *   - the recent-issue log ([RecentIssueLog.entries])
 *   - actions for each control on the page (toggle / repo / token / labels /
 *     categories / severity / dedupe / redaction / test buttons).
 */
@HiltViewModel
class GitHubIssueLoggingViewModel @Inject constructor(
    private val repo: GitHubIssueSettingsRepository,
    private val client: GitHubApiClient,
    private val logger: GitHubIssueLogger,
    private val queue: IssueQueue,
    private val contextBuilder: IssueContextBuilder,
    val recentLog: RecentIssueLog,
) : ViewModel() {

    val settings: StateFlow<ai.openclaw.jarvis.githubissues.settings.GitHubIssueLoggingSettings> = repo.settings
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
        viewModelScope.launch {
            _connectionStatus.value = when (val r = withContext(Dispatchers.IO) { client.testConnection() }) {
                is GitHubApiClient.Result.Success ->
                    TestStatus.Ok("Connected to ${repo.current().owner}/${repo.current().repo}")
                is GitHubApiClient.Result.Failure -> TestStatus.Err(
                    r.httpStatus?.let { "HTTP $it: ${r.message}" } ?: r.message
                )
            }
        }
    }

    fun createTestIssue() {
        _testIssueStatus.value = TestStatus.Running
        viewModelScope.launch {
            val ctx = contextBuilder.build(
                route = "settings",
                intent = "test_issue",
                commandId = UUID.randomUUID().toString(),
                userCommand = "Test issue from Jarvis settings",
                expectedBehaviour = "Create a test issue in GitHub.",
                actualBehaviour = "Test request issued by user.",
            )
            val outcome = logger.createTestIssue(ctx)
            _testIssueStatus.value = when (outcome) {
                is GitHubIssueLogger.Outcome.Created -> TestStatus.Ok("Created issue #${outcome.issueNumber}")
                is GitHubIssueLogger.Outcome.Queued -> TestStatus.Ok("Queued for retry (${outcome.reason})")
                is GitHubIssueLogger.Outcome.Suppressed ->
                    TestStatus.Ok("Suppressed by dedupe (#${outcome.existingIssueNumber ?: "?"})")
                is GitHubIssueLogger.Outcome.Skipped -> TestStatus.Err("Skipped: ${outcome.reason}")
                is GitHubIssueLogger.Outcome.Failed -> TestStatus.Err("Failed: ${outcome.reason}")
            }
        }
    }
}
