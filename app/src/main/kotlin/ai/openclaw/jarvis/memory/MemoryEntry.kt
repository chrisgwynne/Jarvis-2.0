package ai.openclaw.jarvis.memory

import kotlinx.serialization.Serializable

@Serializable
data class MemoryEntry(
    val role: String,           // "user" or "assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
)
