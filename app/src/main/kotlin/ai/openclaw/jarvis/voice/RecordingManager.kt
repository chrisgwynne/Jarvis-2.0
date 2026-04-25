package ai.openclaw.jarvis.voice

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RecordingManager"
private const val SAMPLE_RATE = 16_000

@Singleton
class RecordingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore,
) {
    private val TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        .withZone(ZoneId.systemDefault())

    private data class Session(
        val file: File,
        val startedAt: Instant,
        val speakerId: String,
        val pcmChunks: MutableList<ShortArray> = mutableListOf(),
    )

    private var active: Session? = null

    val isRecording: Boolean get() = active != null

    private val recordingsDir: File
        get() = File(context.getExternalFilesDir(null), "recordings").also { it.mkdirs() }

    // ─── Session lifecycle ────────────────────────────────────────────────────

    fun startSession(speakerId: String): String {
        if (active != null) return "Recording already in progress."
        val ts   = TS_FMT.format(Instant.now())
        val file = File(recordingsDir, "${speakerId}_$ts.wav")
        active = Session(file = file, startedAt = Instant.now(), speakerId = speakerId)
        Log.i(TAG, "Recording session started: ${file.name}")
        return "Recording conversation. Say 'stop recording' to finish."
    }

    /** Append a PCM chunk to the current recording session. */
    fun addChunk(pcm: ShortArray) {
        active?.pcmChunks?.add(pcm.copyOf())
    }

    suspend fun stopSession(): String {
        val session = active ?: return "No active recording."
        active = null
        return withContext(Dispatchers.IO) {
            try {
                val samples = session.pcmChunks.flatMap { it.toList() }.toShortArray()
                writeWav(session.file, samples)
                writeMetadata(session)
                Log.i(TAG, "Recording saved: ${session.file.name}")
                "Recording saved: ${session.file.name} (${samples.size / SAMPLE_RATE}s)"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save recording: ${e.message}")
                "Recording failed: ${e.message}"
            }
        }
    }

    // ─── Maintenance ──────────────────────────────────────────────────────────

    suspend fun deleteExpired() {
        val retentionMs = settings.settings.first().recordingRetentionHours * 3_600_000L
        val cutoff = System.currentTimeMillis() - retentionMs
        withContext(Dispatchers.IO) {
            recordingsDir.listFiles()?.forEach { f ->
                if (f.lastModified() < cutoff) {
                    f.delete()
                    Log.d(TAG, "Deleted expired recording: ${f.name}")
                }
            }
        }
    }

    fun listRecordings(): List<File> =
        recordingsDir.listFiles()
            ?.filter { it.name.endsWith(".wav") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    // ─── WAV writing ──────────────────────────────────────────────────────────

    private fun writeWav(file: File, pcm: ShortArray) {
        val dataBytes = pcm.size * 2
        DataOutputStream(FileOutputStream(file)).use { out ->
            out.writeBytes("RIFF")
            out.writeIntLE(36 + dataBytes)
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            out.writeIntLE(16)
            out.writeShortLE(1)            // PCM
            out.writeShortLE(1)            // mono
            out.writeIntLE(SAMPLE_RATE)
            out.writeIntLE(SAMPLE_RATE * 2) // byte rate
            out.writeShortLE(2)            // block align
            out.writeShortLE(16)           // bits per sample
            out.writeBytes("data")
            out.writeIntLE(dataBytes)
            for (s in pcm) out.writeShortLE(s.toInt())
        }
    }

    private fun writeMetadata(session: Session) {
        File(session.file.parent, session.file.nameWithoutExtension + ".txt").writeText(
            "speaker: ${session.speakerId}\n" +
            "started_at: ${session.startedAt}\n" +
            "file: ${session.file.name}\n"
        )
    }

    private fun DataOutputStream.writeIntLE(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF)
        write((v shr 16) and 0xFF); write((v shr 24) and 0xFF)
    }
    private fun DataOutputStream.writeShortLE(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF)
    }
}
