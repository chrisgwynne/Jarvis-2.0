package com.jarvis.githubissues.integration

import com.jarvis.githubissues.model.IssueDraft

/**
 * Seam onto Jarvis's OpenClaw session-history layer.
 *
 * Implementations should turn each call into a `jarvis.github_issue_created`
 * (or `jarvis.github_issue_queued` / `jarvis.github_issue_failed`) event in
 * the OpenClaw session log, with the fields the spec lists: issue URL,
 * title, category, severity, commandId.
 *
 * A no-op default implementation is provided so unit tests and the
 * standalone debug screen don't need OpenClaw running.
 */
interface OpenClawSessionBridge {
    fun onIssueCreated(draft: IssueDraft, issueNumber: Int, htmlUrl: String) {}
    fun onIssueQueued(draft: IssueDraft, reason: String) {}
    fun onIssueFailed(draft: IssueDraft, reason: String) {}

    object NoOp : OpenClawSessionBridge
}
