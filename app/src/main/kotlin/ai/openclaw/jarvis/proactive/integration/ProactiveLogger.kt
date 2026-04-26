package ai.openclaw.jarvis.proactive.integration

import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.proactive.model.Signal
import ai.openclaw.jarvis.proactive.model.Suggestion
import ai.openclaw.jarvis.protocol.client.OpenClawProtocolClient
import ai.openclaw.jarvis.protocol.model.JarvisSessionEvent
import ai.openclaw.jarvis.protocol.util.IsoTimestamp
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Sends `jarvis.proactive_*` JarvisSessionEvents to OpenClaw so the
 * backend can see the full lifecycle of every suggestion: triggered
 * signal, surfaced suggestion, user acceptance / dismissal.
 *
 * Sending happens on a background scope — when OpenClaw is offline the
 * frame is dropped and the suggestion still works locally.
 */
@Singleton
class ProactiveLogger @Inject constructor(
    private val protocolClient: OpenClawProtocolClient,
    private val settings: SettingsDataStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun logSuggestionShown(suggestion: Suggestion, signal: Signal) =
        emit("jarvis.proactive_suggestion_shown") {
            put("suggestionId", JsonPrimitive(suggestion.id))
            put("signalType", JsonPrimitive(signal.type.name))
            put("format", JsonPrimitive(suggestion.format.name))
            put("title", JsonPrimitive(suggestion.title))
        }

    fun logSuggestionAccepted(suggestion: Suggestion) =
        emit("jarvis.proactive_suggestion_accepted") {
            put("suggestionId", JsonPrimitive(suggestion.id))
            put("signalType", JsonPrimitive(suggestion.signalType.name))
        }

    fun logSuggestionDismissed(suggestion: Suggestion, dontSuggestAgain: Boolean) =
        emit("jarvis.proactive_suggestion_dismissed") {
            put("suggestionId", JsonPrimitive(suggestion.id))
            put("signalType", JsonPrimitive(suggestion.signalType.name))
            put("dontSuggestAgain", JsonPrimitive(dontSuggestAgain))
        }

    fun logSuggestionSkipped(signal: Signal, reason: String) =
        emit("jarvis.proactive_suggestion_skipped") {
            put("signalType", JsonPrimitive(signal.type.name))
            put("reason", JsonPrimitive(reason))
        }

    private fun emit(name: String, body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        scope.launch {
            val sessionKey = runCatching { settings.settings.first().sessionKey }
                .getOrDefault("jarvis:user:android")
            protocolClient.sendSessionEvent(
                JarvisSessionEvent(
                    requestId = java.util.UUID.randomUUID().toString(),
                    sessionKey = sessionKey,
                    timestamp = IsoTimestamp.now(),
                    name = name,
                    body = buildJsonObject(body),
                )
            )
        }
    }
}
