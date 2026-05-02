package ai.openclaw.jarvis.voice

import java.util.UUID

data class TranscriptEntry(
    val speaker: String,
    val text: String,
    val route: String,
    val timestamp: Long = System.currentTimeMillis(),
    val id: String = UUID.randomUUID().toString(),
)
