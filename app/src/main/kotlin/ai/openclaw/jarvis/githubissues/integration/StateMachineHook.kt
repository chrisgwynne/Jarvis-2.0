package ai.openclaw.jarvis.githubissues.integration

import ai.openclaw.jarvis.githubissues.GitHubIssueLogger
import ai.openclaw.jarvis.githubissues.model.IssueContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drop-in for whatever Jarvis uses today to listen to state-machine
 * transitions. Call [onTransition] from the existing transition emitter
 * — when the destination is `ERROR_RECOVERY` we file the issue.
 */
@Singleton
class StateMachineHook @Inject constructor(
    private val logger: GitHubIssueLogger,
) {

    fun onTransition(
        fromState: String?,
        toState: String,
        triggerReason: String?,
        context: IssueContext
    ) {
        if (toState != ERROR_RECOVERY) return
        logger.onErrorRecovery(fromState, triggerReason, context)
    }

    companion object {
        const val ERROR_RECOVERY = "ERROR_RECOVERY"
    }
}
