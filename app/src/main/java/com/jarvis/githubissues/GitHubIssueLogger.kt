package com.jarvis.githubissues

import com.jarvis.githubissues.api.GitHubApiClient
import com.jarvis.githubissues.api.IssueBodyBuilder
import com.jarvis.githubissues.dedupe.DedupeDecision
import com.jarvis.githubissues.dedupe.IssueDeduplicator
import com.jarvis.githubissues.integration.OpenClawSessionBridge
import com.jarvis.githubissues.model.IssueContext
import com.jarvis.githubissues.model.IssueDraft
import com.jarvis.githubissues.model.IssueEvent
import com.jarvis.githubissues.queue.IssueQueue
import com.jarvis.githubissues.settings.SettingsSource

/**
 * Central facade. Every subsystem in Jarvis routes its failure / refusal /
 * unsupported-capability event through this class.
 *
 * The orchestrator:
 *   1. checks the master enable flag, severity floor, and per-category opt-in,
 *   2. asks [IssueDeduplicator] whether this is a repeat,
 *   3. either creates a fresh issue (online) or enqueues it (offline / 5xx /
 *      rate-limited), or comments on the existing issue when suppressed,
 *   4. emits the corresponding OpenClaw session event.
 */
class GitHubIssueLogger(
    private val settingsRepo: SettingsSource,
    private val deduper: IssueDeduplicator,
    private val bodyBuilder: IssueBodyBuilder,
    private val client: GitHubApiClient,
    private val queue: IssueQueue,
    private val openClaw: OpenClawSessionBridge = OpenClawSessionBridge.NoOp
) {
    sealed class Outcome {
        data class Created(val issueNumber: Int, val htmlUrl: String) : Outcome()
        data class Queued(val reason: String) : Outcome()
        data class Suppressed(val occurrenceCount: Int, val existingIssueNumber: Int?) : Outcome()
        data class Skipped(val reason: String) : Outcome()
        data class Failed(val reason: String) : Outcome()
    }

    // ---- public hooks (one per high-level failure source) ---------------

    fun onErrorRecovery(
        fromState: String?,
        triggerReason: String?,
        context: IssueContext
    ): Outcome = report(IssueEvent.ErrorRecovery(fromState, "ERROR_RECOVERY", triggerReason, context))

    fun onUnsupported(capability: String, reason: String?, userPhrase: String?, context: IssueContext): Outcome =
        report(IssueEvent.Unsupported(capability, reason, userPhrase, context))

    fun onCantDoThat(userPhrase: String?, reason: String?, context: IssueContext): Outcome =
        report(IssueEvent.CantDoThat(userPhrase, reason, context))

    fun onPermissionDenied(permission: String, action: String?, context: IssueContext): Outcome =
        report(IssueEvent.PermissionDenied(permission, action, context))

    fun onActionFailure(actionType: String, errorCode: String?, message: String?, context: IssueContext): Outcome =
        report(IssueEvent.ActionFailure(actionType, errorCode, message, context))

    fun onOpenClawFailure(
        mode: IssueEvent.OpenClawFailure.Mode,
        errorCode: String?,
        message: String?,
        context: IssueContext
    ): Outcome = report(IssueEvent.OpenClawFailure(mode, errorCode, message, context))

    fun onVoiceFailure(mode: IssueEvent.VoiceFailure.Mode, detail: String?, context: IssueContext): Outcome =
        report(IssueEvent.VoiceFailure(mode, detail, context))

    fun onRoutingFailure(
        mode: IssueEvent.RoutingFailure.Mode,
        route: String?,
        intent: String?,
        confidence: Double?,
        context: IssueContext
    ): Outcome = report(IssueEvent.RoutingFailure(mode, route, intent, confidence, context))

    fun onUserCorrection(
        correctionPhrase: String,
        previousCommand: String?,
        previousRoute: String?,
        previousResult: String?,
        context: IssueContext
    ): Outcome = report(
        IssueEvent.UserCorrection(correctionPhrase, previousCommand, previousRoute, previousResult, context)
    )

    /** Entry point used by the debug screen's "Create issue from this event" button. */
    fun report(event: IssueEvent, force: Boolean = false): Outcome {
        val s = settingsRepo.current()
        if (!force) {
            if (!s.enabled) return Outcome.Skipped("logging disabled")
            if (!s.isFullyConfigured) return Outcome.Skipped("repo / token not configured")
            if (!event.severity.atLeast(s.minSeverity)) return Outcome.Skipped("below min severity")
            if (!s.categoryEnabled(event.category)) return Outcome.Skipped("category disabled")
        }

        val decision = deduper.recordAndDecide(event, s.dedupeWindow)
        return when (decision) {
            is DedupeDecision.Suppress -> handleSuppressed(event, decision)
            is DedupeDecision.Allow -> handleAllow(event, decision.fingerprint, decision.occurrenceCount)
        }
    }

    // ---- internals ------------------------------------------------------

    private fun handleAllow(event: IssueEvent, fingerprint: String, occurrenceCount: Int): Outcome {
        val draft = bodyBuilder.build(event, fingerprint, occurrenceCount)
        return when (val r = client.createIssue(draft)) {
            is GitHubApiClient.Result.Success -> {
                deduper.attachIssueNumber(fingerprint, r.issueNumber)
                openClaw.onIssueCreated(draft, r.issueNumber, r.htmlUrl)
                Outcome.Created(r.issueNumber, r.htmlUrl)
            }
            is GitHubApiClient.Result.Failure -> {
                if (r.transient) {
                    queue.enqueue(draft, lastError = r.message)
                    openClaw.onIssueQueued(draft, r.message)
                    Outcome.Queued(r.message)
                } else {
                    openClaw.onIssueFailed(draft, r.message)
                    Outcome.Failed(r.message)
                }
            }
        }
    }

    private fun handleSuppressed(event: IssueEvent, decision: DedupeDecision.Suppress): Outcome {
        val number = decision.existingIssueNumber
        if (number != null) {
            // Best-effort comment on the existing issue. Failures here are
            // intentionally swallowed — suppression is a soft signal.
            val comment = bodyBuilder.renderOccurrenceComment(event, decision.occurrenceCount)
            client.commentOnIssue(number, comment)
        }
        return Outcome.Suppressed(decision.occurrenceCount, number)
    }

    /** Used by the settings page's "Create test issue" button. */
    fun createTestIssue(context: IssueContext): Outcome =
        report(
            IssueEvent.Unsupported(
                capability = "test",
                reason = "Test issue from Jarvis settings",
                userPhrase = null,
                context = context
            ),
            force = true
        )

    /** Drop a single queued draft after the worker successfully posts it,
     *  letting us tie its issue number back into the dedupe table. */
    fun onQueuedIssuePosted(draft: IssueDraft, issueNumber: Int, htmlUrl: String) {
        deduper.attachIssueNumber(draft.fingerprint, issueNumber)
        openClaw.onIssueCreated(draft, issueNumber, htmlUrl)
    }
}
