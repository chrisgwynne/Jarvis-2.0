package ai.openclaw.jarvis.memory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages a rolling conversation history injected as context into every
 * chat.send request. The last N exchanges give the AI model continuity
 * across separate voice utterances.
 *
 * Usage in SpeechSessionManager:
 *   1. Call [buildContextualMessage] when sending to OpenClaw — prepends history to the text.
 *   2. Call [recordUser] after receiving a final transcript.
 *   3. Call [recordAssistant] when an assistant reply arrives.
 */
@Singleton
class ConversationMemoryManager @Inject constructor(
    private val store: ConversationMemoryStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Hot copy updated from the store so buildContextualMessage is synchronous
    @Volatile private var recent: List<MemoryEntry> = emptyList()

    init {
        scope.launch {
            store.entries.collect { recent = it }
        }
    }

    fun recordUser(text: String) {
        scope.launch { store.add(MemoryEntry("user", text)) }
    }

    fun recordAssistant(text: String) {
        scope.launch { store.add(MemoryEntry("assistant", text)) }
    }

    /**
     * Prepends the last [maxEntries] exchanges to [userText] as a context block,
     * or returns [userText] unchanged if the history is empty.
     */
    fun buildContextualMessage(userText: String, maxEntries: Int = 10): String {
        val context = recent.takeLast(maxEntries)
        if (context.isEmpty()) return userText
        return buildString {
            appendLine("[Context from recent conversation:]")
            context.forEach { e -> appendLine("${e.role}: ${e.text}") }
            appendLine("[End context]")
            append(userText)
        }
    }

    fun clearHistory() {
        scope.launch { store.clear() }
    }
}
