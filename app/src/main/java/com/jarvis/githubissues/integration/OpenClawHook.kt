package com.jarvis.githubissues.integration

import com.jarvis.githubissues.GitHubIssueLogger
import com.jarvis.githubissues.model.IssueContext
import com.jarvis.githubissues.model.IssueEvent

/**
 * Hook for the OpenClaw bridge: offline-when-required, timeouts, malformed
 * responses, contract violations, and unknown-action requests all map to
 * an [IssueEvent.OpenClawFailure] with the appropriate `Mode`.
 */
class OpenClawHook(private val logger: GitHubIssueLogger) {

    fun onOfflineWhenRequired(context: IssueContext) =
        logger.onOpenClawFailure(IssueEvent.OpenClawFailure.Mode.OFFLINE_REQUIRED, null, null, context)

    fun onTimeout(message: String?, context: IssueContext) =
        logger.onOpenClawFailure(IssueEvent.OpenClawFailure.Mode.TIMEOUT, "timeout", message, context)

    fun onMalformedResponse(rawDigest: String?, context: IssueContext) =
        logger.onOpenClawFailure(
            IssueEvent.OpenClawFailure.Mode.MALFORMED_RESPONSE,
            "malformed",
            rawDigest,
            context
        )

    fun onContractViolation(detail: String?, context: IssueContext) =
        logger.onOpenClawFailure(
            IssueEvent.OpenClawFailure.Mode.CONTRACT_VIOLATION,
            "contract",
            detail,
            context
        )

    fun onUnknownAndroidAction(action: String, context: IssueContext) =
        logger.onOpenClawFailure(
            IssueEvent.OpenClawFailure.Mode.UNKNOWN_ANDROID_ACTION,
            "unknown_action",
            action,
            context
        )
}
