package ai.openclaw.jarvis.githubissues.dedupe

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * File-backed [DedupeStore] so dedupe survives app restart.
 * Format: a single JSON array of records under
 * `<filesDir>/jarvis_github_dedupe.json`.
 *
 * Writes are coarsely synchronised — the volume here is "one row per
 * distinct failure" so a debounce/batching layer is unnecessary.
 */
class PersistedDedupeStore(private val file: File) : DedupeStore {
    private val cache = mutableMapOf<String, DedupeRecord>()
    private val lock = Any()
    @Volatile private var loaded = false

    override fun get(fingerprint: String): DedupeRecord? = synchronized(lock) {
        ensureLoaded()
        cache[fingerprint]
    }

    override fun put(fingerprint: String, record: DedupeRecord) = synchronized(lock) {
        ensureLoaded()
        cache[fingerprint] = record
        flush()
    }

    override fun all(): Map<String, DedupeRecord> = synchronized(lock) {
        ensureLoaded()
        HashMap(cache)
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        val raw = runCatching { file.readText() }.getOrNull().orEmpty()
        if (raw.isBlank()) return
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val rec = DedupeRecord(
                    fingerprint = o.getString("fp"),
                    firstSeenAtMillis = o.getLong("first"),
                    lastSeenAtMillis = o.getLong("last"),
                    occurrenceCount = o.getInt("count"),
                    issueNumber = if (o.has("issue") && !o.isNull("issue")) o.getInt("issue") else null
                )
                cache[rec.fingerprint] = rec
            }
        }
    }

    private fun flush() {
        val arr = JSONArray()
        for (rec in cache.values) {
            val o = JSONObject()
                .put("fp", rec.fingerprint)
                .put("first", rec.firstSeenAtMillis)
                .put("last", rec.lastSeenAtMillis)
                .put("count", rec.occurrenceCount)
            rec.issueNumber?.let { o.put("issue", it) }
            arr.put(o)
        }
        file.parentFile?.mkdirs()
        file.writeText(arr.toString())
    }
}
