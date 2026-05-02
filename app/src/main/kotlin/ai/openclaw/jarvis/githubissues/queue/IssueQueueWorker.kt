package ai.openclaw.jarvis.githubissues.queue

import ai.openclaw.jarvis.githubissues.GitHubIssueLogger
import ai.openclaw.jarvis.githubissues.api.GitHubApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Drains the [IssueQueue] in the background, retrying with capped
 * exponential backoff. A failed attempt that the API marks transient
 * is left at the head of the queue; a terminal failure (4xx) is
 * dropped so we don't spin forever on a malformed token.
 */
@Singleton
class IssueQueueWorker @Inject constructor(
    private val queue: IssueQueue,
    private val client: GitHubApiClient,
    private val logger: GitHubIssueLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val maxAttempts: Int = 6
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val front = queue.peek()
                if (front == null) {
                    delay(LONG_IDLE_MS)
                    continue
                }
                if (front.attempts >= maxAttempts) {
                    queue.removeFront() // give up after capped retries
                    continue
                }
                when (val r = client.createIssue(front.draft)) {
                    is GitHubApiClient.Result.Success -> {
                        logger.onQueuedIssuePosted(front.draft, r.issueNumber, r.htmlUrl)
                        queue.removeFront()
                    }
                    is GitHubApiClient.Result.Failure -> {
                        if (r.transient) {
                            queue.bumpFrontFailure(r.message)
                            delay(backoff(front.attempts + 1))
                        } else {
                            queue.removeFront() // permanent — drop
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun backoff(attempt: Int): Long {
        val ms = (BASE_MS * (1L shl min(attempt, 6))).coerceAtMost(MAX_BACKOFF_MS)
        return ms
    }

    companion object {
        private const val LONG_IDLE_MS = 30_000L
        private const val BASE_MS = 1_000L
        private const val MAX_BACKOFF_MS = 5L * 60 * 1000
    }
}
