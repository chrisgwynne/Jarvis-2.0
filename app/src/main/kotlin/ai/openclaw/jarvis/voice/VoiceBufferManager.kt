package ai.openclaw.jarvis.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VoiceBufferManager"

/**
 * Captures a brief PCM sample for speaker identification.
 *
 * Must be called when the microphone is not already held by SpeechRecognizer
 * (i.e. between STT sessions). Holds the mic for [seconds] then releases it.
 * No audio is written to disk.
 */
@Singleton
class VoiceBufferManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL  = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    /**
     * Record [seconds] of PCM from the microphone.
     * Returns the captured samples, or null if the mic was unavailable.
     */
    @SuppressLint("MissingPermission")
    suspend fun capturePcm(seconds: Float = 1.5f): ShortArray? = withContext(Dispatchers.IO) {
        val numSamples = (SAMPLE_RATE * seconds).toInt()
        val minBuf     = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) { Log.w(TAG, "AudioRecord not available"); return@withContext null }

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            maxOf(minBuf, numSamples * 2),
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            Log.w(TAG, "AudioRecord failed to initialise")
            return@withContext null
        }

        val buf = ShortArray(numSamples)
        return@withContext try {
            record.startRecording()
            var pos = 0
            while (pos < numSamples) {
                val read = record.read(buf, pos, numSamples - pos)
                if (read <= 0) break
                pos += read
            }
            record.stop()
            if (pos >= numSamples / 2) buf.copyOf(pos) else null
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed: ${e.message}")
            null
        } finally {
            record.release()
        }
    }
}
