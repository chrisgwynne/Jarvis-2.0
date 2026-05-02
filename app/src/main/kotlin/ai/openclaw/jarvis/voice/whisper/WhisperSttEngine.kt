package ai.openclaw.jarvis.voice.whisper

import ai.openclaw.jarvis.voice.SpeechToText
import ai.openclaw.jarvis.voice.SttEvent
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device speech recognition using whisper.cpp via JNI.
 *
 * Records PCM audio with [AudioRecord] until [cancel] is called (or 30 s max),
 * then runs Whisper inference synchronously on the IO dispatcher.
 *
 * [isAvailable] returns false until:
 *   - The native library is compiled and present (see WhisperJni)
 *   - The GGML model file is downloaded (see WhisperModelManager)
 *   - RECORD_AUDIO permission is granted
 *
 * When not available, [SpeechToTextProxy] transparently falls back to the
 * standard Android SpeechRecognizer — no app-level changes needed.
 */
@Singleton
class WhisperSttEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: WhisperModelManager,
) : SpeechToText {

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val MAX_RECORD_MS = 30_000L
    }

    private val minBufSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    ).coerceAtLeast(SAMPLE_RATE * 2)   // at least 0.5 s

    @Volatile private var cancelRequested = false
    @Volatile private var handle = 0L

    override fun isAvailable(): Boolean =
        WhisperJni.isNativeAvailable() &&
        modelManager.isModelReady() &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun listen(): Flow<SttEvent> = flow {
        if (!isAvailable()) {
            emit(SttEvent.Error(-1, "Whisper not available — build native lib or download model"))
            return@flow
        }

        cancelRequested = false
        emit(SttEvent.Started)

        val h = WhisperJni.init(modelManager.modelFile().absolutePath)
        if (h == 0L) {
            emit(SttEvent.Error(-2, "Failed to load Whisper model"))
            return@flow
        }
        handle = h

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufSize,
        )

        try {
            recorder.startRecording()
            val allPcm = mutableListOf<Short>()
            val readBuf = ShortArray(minBufSize / 2)
            val deadline = System.currentTimeMillis() + MAX_RECORD_MS

            while (!cancelRequested && currentCoroutineContext().isActive
                   && System.currentTimeMillis() < deadline) {
                val n = recorder.read(readBuf, 0, readBuf.size)
                if (n > 0) allPcm.addAll(readBuf.take(n))
            }

            recorder.stop()
            emit(SttEvent.Stopped)

            if (allPcm.isNotEmpty()) {
                val text = WhisperJni.transcribePcm16(h, allPcm.toShortArray(), SAMPLE_RATE).trim()
                if (text.isNotBlank()) emit(SttEvent.Final(text))
                else emit(SttEvent.Error(-3, "No speech detected"))
            }
        } catch (e: Exception) {
            emit(SttEvent.Error(-4, e.message ?: "Recording error"))
        } finally {
            recorder.release()
            WhisperJni.free(h)
            handle = 0L
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel() {
        cancelRequested = true
    }
}
