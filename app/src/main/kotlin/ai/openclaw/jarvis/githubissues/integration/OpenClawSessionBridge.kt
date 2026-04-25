package ai.openclaw.jarvis.githubissues.integration

import ai.openclaw.jarvis.githubissues.model.IssueDraft
import ai.openclaw.jarvis.network.OpenClawClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seam onto Jarvis's OpenClaw session-history layer.
 *
 * Each call emits one of the following raw frames to OpenClaw, with the
 * fields the spec lists (issue URL, title, category, severity, commandId):
 *
 *   - `jarvis.github_issue_created`
 *   - `jarvis.github_issue_queued`
 *   - `jarvis.github_issue_failed`
 *
 * Sends are best-effort and fire-and-forget; if the WebSocket is offline
 * the event is dropped (the issue itself is already on the offline queue
 * for retry, and the queue worker will emit `created` once it lands).
 */
interface OpenClawSessionBridge {
    fun onIssueCreated(draft: IssueDraft, issueNumber: Int, htmlUrl: String)
    fun onIssueQueued(draft: IssueDraft, reason: String)
    fun onIssueFailed(draft: IssueDraft, reason: String)

    /** No-op; used by tests / non-DI call sites. */
    object NoOp : OpenClawSessionBridge {
        override fun onIssueCreated(draft: IssueDraft, issueNumber: Int, htmlUrl: String) {}
        override fun onIssueQueued(draft: IssueDraft, reason: String) {}
        override fun onIssueFailed(draft: IssueDraft, reason: String) {}
    }
}

@Singleton
class OpenClawSessionBridgeImpl @Inject constructor(
    private val client: OpenClawClient,
) : OpenClawSessionBridge {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { encodeDefaults = true }

    override fun onIssueCreated(draft: IssueDraft, issueNumber: Int, htmlUrl: String) {
        emit(buildPayload("jarvis.github_issue_created", draft) {
            put("issueNumber", issueNumber)
            put("htmlUrl", htmlUrl)
        })
    }

    override fun onIssueQueued(draft: IssueDraft, reason: String) {
        emit(buildPayload("jarvis.github_issue_queued", draft) {
            put("reason", reason)
        })
    }

    override fun onIssueFailed(draft: IssueDraft, reason: String) {
        emit(buildPayload("jarvis.github_issue_failed", draft) {
            put("reason", reason)
        })
    }

    private fun buildPayload(
        type: String,
        draft: IssueDraft,
        extras: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): JsonObject = buildJsonObject {
        put("type", type)
        put("title", draft.title)
        put("category", draft.category.tag)
        put("severity", draft.severity.tag)
        put("commandId", draft.commandId ?: "")
        put("sessionId", draft.sessionId ?: "")
        put("fingerprint", draft.fingerprint)
        put("occurrenceCount", draft.occurrenceCount)
        put("labels", buildJsonArray { draft.labels.forEach { add(it) } })
        extras()
    }

    private fun emit(payload: JsonObject) {
        scope.launch {
            client.sendCustomFrame(json.encodeToString(JsonObject.serializer(), payload))
        }
    }
}
