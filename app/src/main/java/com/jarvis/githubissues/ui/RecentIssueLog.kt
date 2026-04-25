package com.jarvis.githubissues.ui

import com.jarvis.githubissues.settings.FailureCategory
import com.jarvis.githubissues.settings.Severity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory ring buffer feeding the "recent issue log" panel on the
 * settings page. Persistence is intentionally out of scope — the offline
 * queue and the dedupe store already cover durable state; this is a UI aid.
 */
class RecentIssueLog(private val capacity: Int = 50) {

    enum class Status { CREATED, QUEUED, SUPPRESSED, FAILED }

    data class Entry(
        val timestampMillis: Long,
        val title: String,
        val status: Status,
        val severity: Severity,
        val category: FailureCategory,
        val htmlUrl: String? = null,
        val occurrenceCount: Int? = null,
        val message: String? = null
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun record(entry: Entry) {
        val next = (listOf(entry) + _entries.value).take(capacity)
        _entries.value = next
    }
}
