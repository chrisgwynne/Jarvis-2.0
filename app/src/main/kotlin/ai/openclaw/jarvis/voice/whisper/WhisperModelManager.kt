package ai.openclaw.jarvis.voice.whisper

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the whisper.cpp GGML model file.
 * Default model: ggml-tiny.en.bin (~75 MB) — good speed/accuracy balance for voice commands.
 * Download from Hugging Face when first enabled via the Settings screen.
 */
@Singleton
class WhisperModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val MODEL_NAME = "ggml-tiny.en.bin"
        private const val MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin"
        private const val MIN_SIZE_BYTES = 1_000_000L
    }

    private val modelsDir = File(context.filesDir, "whisper_models")

    fun modelFile(): File = File(modelsDir, MODEL_NAME)

    fun isModelReady(): Boolean {
        val f = modelFile()
        return f.exists() && f.length() > MIN_SIZE_BYTES
    }

    fun modelSizeMb(): Long = modelFile().length() / (1024L * 1024L)

    /**
     * Downloads the model if not already present.
     * [onProgress] is called with 0–100 as the download proceeds.
     * Returns true on success, false on any error.
     */
    suspend fun ensureModel(onProgress: (Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (isModelReady()) return@withContext true
        runCatching {
            modelsDir.mkdirs()
            val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
            conn.connect()
            val total = conn.contentLengthLong
            var downloaded = 0L
            conn.inputStream.use { input ->
                modelFile().outputStream().use { output ->
                    val buf = ByteArray(8 * 1024)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) onProgress((downloaded * 100L / total).toInt())
                    }
                }
            }
            conn.disconnect()
        }.isSuccess
    }

    fun deleteModel() {
        modelFile().delete()
    }
}
