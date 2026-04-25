package com.jarvis.githubissues.integration

import com.jarvis.githubissues.GitHubIssueLogger
import com.jarvis.githubissues.model.IssueContext

/**
 * Drop-in for whatever Jarvis uses today to listen to state-machine
 * transitions. Call [onTransition] from the existing transition emitter
 * — when the destination is `ERROR_RECOVERY` we file the issue.
 */
class StateMachineHook(private val logger: GitHubIssueLogger) {

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
