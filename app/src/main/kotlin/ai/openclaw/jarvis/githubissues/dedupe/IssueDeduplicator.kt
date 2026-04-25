package ai.openclaw.jarvis.githubissues.dedupe

import ai.openclaw.jarvis.githubissues.model.IssueEvent
import ai.openclaw.jarvis.githubissues.settings.DedupeWindow
import java.security.MessageDigest

/**
 * Outcome of [IssueDeduplicator.recordAndDecide].
 *
 * - [Suppress] — within window, don't post; optionally bump occurrence count
 *   on the existing GitHub issue.
 * - [Allow]    — first time (or out-of-window), post a fresh issue.
 */
sealed class DedupeDecision {
    abstract val fingerprint: String
    abstract val occurrenceCount: Int

    data class Allow(
        override val fingerprint: String,
        override val occurrenceCount: Int = 1
    ) : DedupeDecision()

    data class Suppress(
        override val fingerprint: String,
        override val occurrenceCount: Int,
        val existingIssueNumber: Int?,
        val firstSeenAtMillis: Long
    ) : DedupeDecision()
}

/**
 * Persists a `fingerprint -> last-seen-time` table so a crashed app can still
 * remember it already filed this exact issue. Implementations should be
 * thread-safe; the in-memory default uses a synchronised map.
 */
interface DedupeStore {
    fun get(fingerprint: String): DedupeRecord?
    fun put(fingerprint: String, record: DedupeRecord)
    fun all(): Map<String, DedupeRecord>
}

data class DedupeRecord(
    val fingerprint: String,
    val firstSeenAtMillis: Long,
    val lastSeenAtMillis: Long,
    val occurrenceCount: Int,
    val issueNumber: Int? = null
)

class InMemoryDedupeStore : DedupeStore {
    private val map = mutableMapOf<String, DedupeRecord>()
    private val lock = Any()
    override fun get(fingerprint: String) = synchronized(lock) { map[fingerprint] }
    override fun put(fingerprint: String, record: DedupeRecord) =
        synchronized(lock) { map[fingerprint] = record; Unit }
    override fun all(): Map<String, DedupeRecord> = synchronized(lock) { HashMap(map) }
}

class IssueDeduplicator(
    private val store: DedupeStore = InMemoryDedupeStore(),
    private val now: () -> Long = System::currentTimeMillis
) {
    fun fingerprint(event: IssueEvent): String {
        val parts = event.fingerprintParts() + listOf(
            event.context.state.state(),
            event.context.state.intent
        )
        val joined = parts.joinToString("|") { it ?: "_" }
        return sha256(joined).take(40)
    }

    /**
     * Update the dedupe table and decide whether to post a new issue or
     * suppress in favour of the existing one.
     */
    fun recordAndDecide(event: IssueEvent, window: DedupeWindow): DedupeDecision {
        val fp = fingerprint(event)
        val current = store.get(fp)
        val nowMs = now()

        if (current != null && nowMs - current.lastSeenAtMillis <= window.millis) {
            val updated = current.copy(
                lastSeenAtMillis = nowMs,
                occurrenceCount = current.occurrenceCount + 1
            )
            store.put(fp, updated)
            return DedupeDecision.Suppress(
                fingerprint = fp,
                occurrenceCount = updated.occurrenceCount,
                existingIssueNumber = updated.issueNumber,
                firstSeenAtMillis = updated.firstSeenAtMillis
            )
        }

        // Either never seen, or last seen outside the window — start a fresh
        // count. We still keep the previous issue number around in case the
        // caller wants to comment-on instead of recreate, but the spec says a
        // new window means a new issue, so we drop it.
        val created = DedupeRecord(
            fingerprint = fp,
            firstSeenAtMillis = nowMs,
            lastSeenAtMillis = nowMs,
            occurrenceCount = 1,
            issueNumber = null
        )
        store.put(fp, created)
        return DedupeDecision.Allow(fingerprint = fp, occurrenceCount = 1)
    }

    /** Called after a successful issue creation so future suppressions can comment on it. */
    fun attachIssueNumber(fingerprint: String, issueNumber: Int) {
        val rec = store.get(fingerprint) ?: return
        store.put(fingerprint, rec.copy(issueNumber = issueNumber))
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun ai.openclaw.jarvis.githubissues.model.StateSnapshot.state(): String? = current
}
