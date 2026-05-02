package ai.openclaw.jarvis.githubissues.integration

import ai.openclaw.jarvis.githubissues.GitHubIssueLogger
import ai.openclaw.jarvis.githubissues.model.IssueContext
import ai.openclaw.jarvis.githubissues.model.IssueEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hook for the OpenClaw bridge: offline-when-required, timeouts, malformed
 * responses, contract violations, and unknown-action requests all map to
 * an [IssueEvent.OpenClawFailure] with the appropriate `Mode`.
 */
@Singleton
class OpenClawHook @Inject constructor(
    private val logger: GitHubIssueLogger,
) {

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
