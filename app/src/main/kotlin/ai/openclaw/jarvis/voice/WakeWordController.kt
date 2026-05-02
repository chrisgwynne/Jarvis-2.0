package ai.openclaw.jarvis.voice

import android.os.Handler
import android.os.Looper
import ai.openclaw.jarvis.data.local.SettingsDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

interface WakeWordController {
    val wakePhrase: String
    fun start(onWakeDetected: () -> Unit)
    fun stop()
    fun isRunning(): Boolean
}

/**
 * Basic STT-burst wake word detection.
 *
 * LIMITATION: This is not a native always-on engine. It uses periodic Android
 * STT bursts and a text-match heuristic. It works without bundled model files
 * but is less power-efficient and less reliable than a dedicated on-device
 * wake word engine (e.g. Porcupine, Vosk, Sherpa-Onnx).
 *
 * The actual STT listening loop runs in [AlwaysListeningService]; this class
 * provides phrase matching, stats, and sensitivity control. Swap it by
 * injecting an alternative [WakeWordController] implementation.
 */
@Singleton
class SimpleWakeWordController @Inject constructor(
    private val settingsStore: SettingsDataStore,
) : WakeWordController {

    companion object {
        const val ENGINE_LABEL = "Basic STT (text matching)"
        const val ENGINE_DESCRIPTION =
            "Uses periodic Android speech recognition + text matching. " +
            "Not an always-on neural engine — uses more battery and has higher false-trigger rate " +
            "than dedicated wake word engines."
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var running = false
    private var onWakeCallback: (() -> Unit)? = null

    override var wakePhrase: String = "hey jarvis"
        private set

    private var sensitivity: Float = 0.7f

    // ─── Diagnostics stats ────────────────────────────────────────────────────

    private val _detectionCount = AtomicInteger(0)
    val detectionCount: Int get() = _detectionCount.get()

    private val _lastDetectionTime = AtomicLong(0L)
    val lastDetectionTime: Long get() = _lastDetectionTime.get()

    private val _lastDetectedText = MutableStateFlow("")
    val lastDetectedText: StateFlow<String> = _lastDetectedText.asStateFlow()

    private val _lastConfidence = MutableStateFlow(0f)
    val lastConfidence: StateFlow<Float> = _lastConfidence.asStateFlow()

    fun resetStats() {
        _detectionCount.set(0)
        _lastDetectionTime.set(0L)
        _lastDetectedText.value = ""
        _lastConfidence.value = 0f
    }

    // ─── WakeWordController ───────────────────────────────────────────────────

    override fun start(onWakeDetected: () -> Unit) {
        if (running) return
        running = true
        onWakeCallback = onWakeDetected
        scope.launch {
            val s = settingsStore.settings.first()
            wakePhrase  = s.wakePhrase
            sensitivity = s.wakeSensitivity
        }
    }

    override fun stop() {
        running = false
        onWakeCallback = null
    }

    override fun isRunning() = running

    fun updatePhrase(phrase: String) {
        wakePhrase = phrase.trim().lowercase()
    }

    fun updateSensitivity(s: Float) {
        sensitivity = s.coerceIn(0f, 1f)
    }

    /**
     * Called by AlwaysListeningService with each STT result.
     * Returns true if the wake phrase was detected.
     *
     * Sensitivity controls how strict the match is:
     *   1.0 — any word from the phrase (or "jarvis" alone) triggers
     *   0.7 — all words in phrase must be present (default)
     *   0.3 — exact phrase must appear as a substring
     */
    fun checkResult(text: String): Boolean {
        val phrase = wakePhrase.lowercase().trim()
        val normalized = text.lowercase().trim()

        val detected = when {
            sensitivity >= 0.9f -> {
                // High: any word in phrase, or key word alone
                val words = phrase.split(" ").filter { it.length > 2 }
                words.any { normalized.contains(it) } ||
                    (phrase.contains("jarvis") && normalized.contains("jarvis"))
            }
            sensitivity >= 0.5f -> {
                // Medium (default): all words must be present, or exact phrase
                val words = phrase.split(" ").filter { it.isNotBlank() }
                val allPresent = words.all { normalized.contains(it) }
                allPresent || normalized.contains(phrase)
            }
            else -> {
                // Low: exact phrase substring match only
                normalized.contains(phrase)
            }
        }

        if (detected && running) {
            val confidence = computeConfidence(normalized, phrase)
            _detectionCount.incrementAndGet()
            _lastDetectionTime.set(System.currentTimeMillis())
            _lastDetectedText.value = text
            _lastConfidence.value = confidence
            onWakeCallback?.invoke()
            return true
        }
        return false
    }

    private fun computeConfidence(normalized: String, phrase: String): Float {
        if (normalized.contains(phrase)) return 1.0f
        val phraseWords = phrase.split(" ").filter { it.isNotBlank() }
        if (phraseWords.isEmpty()) return 0f
        val matched = phraseWords.count { normalized.contains(it) }
        return matched.toFloat() / phraseWords.size
    }
}
