package ai.openclaw.jarvis.policy.store

import ai.openclaw.jarvis.policy.model.ActionDescriptor
import ai.openclaw.jarvis.policy.model.ActionKind
import ai.openclaw.jarvis.policy.model.ActionRisk
import ai.openclaw.jarvis.policy.model.AutonomyLevel
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON-on-disk FIFO of pending approvals. Survives app restart so a
 * "draft email + waiting for approval" doesn't quietly disappear when
 * the phone reboots.
 *
 * Read paths return only non-expired entries; expired ones are pruned
 * lazily on read and reported through [drainExpired] for the audit log.
 */
@Singleton
class PendingApprovalStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val items = mutableListOf<PendingApproval>()
    private val lock = Any()
    @Volatile private var loaded = false

    private val _live = MutableStateFlow<List<PendingApproval>>(emptyList())
    /** Live, non-expired pending approvals — UI subscribes here. */
    val live: StateFlow<List<PendingApproval>> = _live.asStateFlow()

    fun add(item: PendingApproval) = synchronized(lock) {
        ensureLoaded()
        // Replace by id so re-entering the same approval doesn't duplicate it.
        val replaced = items.indexOfFirst { it.id == item.id }
        if (replaced >= 0) items[replaced] = item else items += item
        flush()
        publishLive()
    }

    fun get(id: String): PendingApproval? = synchronized(lock) {
        ensureLoaded()
        items.firstOrNull { it.id == id && !it.isExpired }
    }

    fun all(): List<PendingApproval> = synchronized(lock) {
        ensureLoaded()
        items.filterNot { it.isExpired }
    }

    /** Remove and return any items that have expired since the last call. */
    fun drainExpired(): List<PendingApproval> = synchronized(lock) {
        ensureLoaded()
        val (expired, alive) = items.partition { it.isExpired }
        if (expired.isEmpty()) return emptyList()
        items.clear()
        items.addAll(alive)
        flush()
        publishLive()
        expired
    }

    fun remove(id: String): PendingApproval? = synchronized(lock) {
        ensureLoaded()
        val idx = items.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val removed = items.removeAt(idx)
        flush()
        publishLive()
        removed
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
                items += fromJson(arr.getJSONObject(i))
            }
            publishLive()
        }
    }

    private fun publishLive() {
        _live.value = items.filterNot { it.isExpired }
    }

    private fun flush() {
        val arr = JSONArray()
        for (it in items) arr.put(toJson(it))
        file.parentFile?.mkdirs()
        file.writeText(arr.toString())
    }

    // ─── JSON ────────────────────────────────────────────────────────────────

    private fun toJson(p: PendingApproval): JSONObject = JSONObject()
        .put("id", p.id)
        .put("kind", p.descriptor.kind.name)
        .put("summary", p.descriptor.summary)
        .put("risk", p.descriptor.risk.name)
        .put("openClawHinted", p.descriptor.openClawHinted)
        .put("openClawSuggestedLevel", p.descriptor.openClawSuggestedLevel?.name ?: JSONObject.NULL)
        .put("params", JSONObject(p.descriptor.params as Map<String, Any?>))
        .put("decisionLevel", p.decisionLevel.name)
        .put("createdAt", p.createdAtMillis)
        .put("expiresAt", p.expiresAtMillis)
        .put("originRequestId", p.originRequestId ?: JSONObject.NULL)
        .put("originSessionKey", p.originSessionKey ?: JSONObject.NULL)

    private fun fromJson(o: JSONObject): PendingApproval {
        val paramsObj = o.optJSONObject("params") ?: JSONObject()
        val params = paramsObj.keys().asSequence()
            .associateWith { paramsObj.optString(it) }
        return PendingApproval(
            id = o.getString("id"),
            descriptor = ActionDescriptor(
                id = o.getString("id"),
                kind = ActionKind.valueOf(o.getString("kind")),
                summary = o.optString("summary"),
                risk = runCatching { ActionRisk.valueOf(o.optString("risk")) }
                    .getOrDefault(ActionRisk.LIMITED),
                openClawHinted = o.optBoolean("openClawHinted", false),
                openClawSuggestedLevel = o.optString("openClawSuggestedLevel")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.let { runCatching { AutonomyLevel.valueOf(it) }.getOrNull() },
                params = params,
            ),
            decisionLevel = AutonomyLevel.valueOf(o.getString("decisionLevel")),
            createdAtMillis = o.optLong("createdAt", System.currentTimeMillis()),
            expiresAtMillis = o.optLong("expiresAt", System.currentTimeMillis() + 5 * 60 * 1000),
            originRequestId = o.optString("originRequestId").takeIf { it.isNotBlank() && it != "null" },
            originSessionKey = o.optString("originSessionKey").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    companion object {
        private const val FILE_NAME = "jarvis_pending_approvals.json"
    }
}
