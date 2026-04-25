package com.jarvis.githubissues.model

import com.jarvis.githubissues.settings.FailureCategory
import com.jarvis.githubissues.settings.Severity

/**
 * Output of [com.jarvis.githubissues.api.IssueBodyBuilder] — a fully-rendered
 * issue ready to POST to GitHub or to persist to the offline queue.
 *
 * `fingerprint` is the deduplication key (see [com.jarvis.githubissues.dedupe.IssueDeduplicator]).
 */
data class IssueDraft(
    val fingerprint: String,
    val title: String,
    val body: String,
    val labels: List<String>,
    val severity: Severity,
    val category: FailureCategory,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val commandId: String? = null,
    val sessionId: String? = null,
    val occurrenceCount: Int = 1
)
