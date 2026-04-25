package ai.openclaw.jarvis.githubissues.queue

import ai.openclaw.jarvis.githubissues.model.IssueDraft
import ai.openclaw.jarvis.githubissues.settings.FailureCategory
import ai.openclaw.jarvis.githubissues.settings.Severity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistent FIFO queue for issue drafts that couldn't be POSTed
 * (offline / 5xx / rate-limit). Survives app restart by serialising every
 * pending draft to `<filesDir>/jarvis_github_issue_queue.json`.
 *
 * The queue exposes [size] as a flow so the settings UI can render the
 * "queued issue count" pill without polling.
 */
class IssueQueue(private val file: File) {

    private val items = mutableListOf<QueuedDraft>()
    private val lock = Any()
    @Volatile private var loaded = false

    private val _size = MutableStateFlow(0)
    val size: StateFlow<Int> = _size.asStateFlow()

    fun enqueue(draft: IssueDraft, attempt: Int = 0, lastError: String? = null) = synchronized(lock) {
        ensureLoaded()
        items += QueuedDraft(draft, attempt, lastError, System.currentTimeMillis())
        flush()
        _size.value = items.size
    }

    fun peek(): QueuedDraft? = synchronized(lock) {
        ensureLoaded()
        items.firstOrNull()
    }

    fun snapshot(): List<QueuedDraft> = synchronized(lock) {
        ensureLoaded()
        ArrayList(items)
    }

    fun removeFront() = synchronized(lock) {
        ensureLoaded()
        if (items.isNotEmpty()) items.removeAt(0)
        flush()
        _size.value = items.size
    }

    fun bumpFrontFailure(error: String) = synchronized(lock) {
        ensureLoaded()
        if (items.isEmpty()) return@synchronized
        val front = items[0]
        items[0] = front.copy(attempts = front.attempts + 1, lastError = error)
        flush()
    }

    fun count(): Int = synchronized(lock) { ensureLoaded(); items.size }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        val raw = runCatching { file.readText() }.getOrNull().orEmpty()
        if (raw.isBlank()) return
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                items += QueuedDraft.fromJson(arr.getJSONObject(i))
            }
            _size.value = items.size
        }
    }

    private fun flush() {
        val arr = JSONArray()
        for (q in items) arr.put(q.toJson())
        file.parentFile?.mkdirs()
        file.writeText(arr.toString())
    }
}

data class QueuedDraft(
    val draft: IssueDraft,
    val attempts: Int,
    val lastError: String?,
    val queuedAtMillis: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("fingerprint", draft.fingerprint)
        .put("title", draft.title)
        .put("body", draft.body)
        .put("labels", JSONArray(draft.labels))
        .put("severity", draft.severity.tag)
        .put("category", draft.category.tag)
        .put("createdAt", draft.createdAtMillis)
        .put("commandId", draft.commandId ?: JSONObject.NULL)
        .put("sessionId", draft.sessionId ?: JSONObject.NULL)
        .put("occurrenceCount", draft.occurrenceCount)
        .put("attempts", attempts)
        .put("lastError", lastError ?: JSONObject.NULL)
        .put("queuedAt", queuedAtMillis)

    companion object {
        fun fromJson(o: JSONObject): QueuedDraft {
            val labels = o.optJSONArray("labels")?.let { arr ->
                List(arr.length()) { arr.getString(it) }
            } ?: emptyList()
            return QueuedDraft(
                draft = IssueDraft(
                    fingerprint = o.getString("fingerprint"),
                    title = o.getString("title"),
                    body = o.getString("body"),
                    labels = labels,
                    severity = Severity.fromTag(o.optString("severity", "warning")),
                    category = FailureCategory.fromTag(o.optString("category", "error")),
                    createdAtMillis = o.optLong("createdAt", System.currentTimeMillis()),
                    commandId = o.optString("commandId").takeIf { it.isNotBlank() && it != "null" },
                    sessionId = o.optString("sessionId").takeIf { it.isNotBlank() && it != "null" },
                    occurrenceCount = o.optInt("occurrenceCount", 1)
                ),
                attempts = o.optInt("attempts", 0),
                lastError = o.optString("lastError").takeIf { it.isNotBlank() && it != "null" },
                queuedAtMillis = o.optLong("queuedAt", System.currentTimeMillis())
            )
        }
    }
}
