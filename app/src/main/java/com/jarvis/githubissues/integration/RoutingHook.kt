package com.jarvis.githubissues.integration

import com.jarvis.githubissues.GitHubIssueLogger
import com.jarvis.githubissues.model.IssueContext
import com.jarvis.githubissues.model.IssueEvent

/**
 * Hook for the intent router. Two failure paths matter:
 *
 *  1. The router emitted a low-confidence route we still executed (or a
 *     fallback the user is unlikely to have wanted).
 *  2. After execution, the router decides retroactively that a different
 *     route would have been better — usually because of a downstream
 *     OpenClaw clarification.
 */
class RoutingHook(
    private val logger: GitHubIssueLogger,
    private val lowConfidenceThreshold: Double = 0.55
) {

    fun onRouteResolved(route: String?, intent: String?, confidence: Double?, context: IssueContext) {
        if (confidence != null && confidence < lowConfidenceThreshold) {
            logger.onRoutingFailure(
                IssueEvent.RoutingFailure.Mode.LOW_CONFIDENCE, route, intent, confidence, context
            )
        }
    }

    fun onRouteChangedAfterExecution(
        oldRoute: String?,
        newRoute: String?,
        intent: String?,
        context: IssueContext
    ) {
        logger.onRoutingFailure(
            mode = IssueEvent.RoutingFailure.Mode.ROUTE_CHANGED_AFTER_EXEC,
            route = "$oldRoute -> $newRoute",
            intent = intent,
            confidence = null,
            context = context
        )
    }

    fun onUnknownIntent(rawCommand: String?, context: IssueContext) {
        logger.onRoutingFailure(
            mode = IssueEvent.RoutingFailure.Mode.UNKNOWN_INTENT,
            route = null,
            intent = rawCommand,
            confidence = null,
            context = context
        )
    }
}
