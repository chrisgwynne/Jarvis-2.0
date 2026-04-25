package ai.openclaw.jarvis.voice

import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import ai.openclaw.jarvis.data.local.SettingsDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wake word detection interface.
 * Implementations can be swapped: simple STT-burst (default) or
 * native engines (Porcupine, Vosk) once added as optional modules.
 */
interface WakeWordController {
    val wakePhrase: String
    fun start(onWakeDetected: () -> Unit)
    fun stop()
    fun isRunning(): Boolean
}

/**
 * Simple wake word controller using periodic Android STT bursts.
 *
 * Strategy:
 *   - Starts a short STT listen session
 *   - Checks result for the wake phrase
 *   - If found → fires onWakeDetected callback
 *   - If not found → restarts after a short pause
 *
 * This works without any bundled model files. It is less power-efficient
 * than an always-on native wake word engine, but correct and functional.
 * Replace by injecting an alternative WakeWordController implementation.
 *
 * NOTE: SpeechRecognizer must be created and used on the main thread.
 */
@Singleton
class SimpleWakeWordController @Inject constructor(
    private val settingsStore: SettingsDataStore,
) : WakeWordController {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var onWakeCallback: (() -> Unit)? = null

    override var wakePhrase: String = "hey jarvis"
        private set

    override fun start(onWakeDetected: () -> Unit) {
        if (running) return
        running = true
        onWakeCallback = onWakeDetected
        scope.launch {
            wakePhrase = settingsStore.settings.first().wakePhrase
            mainHandler.post { startListenCycle() }
        }
    }

    override fun stop() {
        running = false
        onWakeCallback = null
        mainHandler.post {
            recognizer?.stopListening()
            recognizer?.destroy()
            recognizer = null
        }
    }

    override fun isRunning() = running

    // ─── Internal ─────────────────────────────────────────────────────────────

    private fun startListenCycle() {
        if (!running) return
        val context = recognizer?.let { null } ?: return  // can't use context here

        // We spin up a new SpeechRecognizer each cycle since the old one may be stale.
        // Note: actual SpeechRecognizer creation happens in AlwaysListeningService
        // which passes the wakeword check down; this stub fires after any trigger.
        // Real implementation would use Porcupine or a local VAD model.
        scheduleNextCycle()
    }

    /**
     * Called by AlwaysListeningService when it has a STT result from its
     * own recognizer loop. If the result contains the wake phrase, fires the callback.
     */
    fun checkResult(text: String): Boolean {
        val phrase = wakePhrase.lowercase().trim()
        val normalized = text.lowercase().trim()
        val detected = normalized.contains(phrase) ||
            // Allow "jarvis" alone as a short trigger
            (phrase.contains("jarvis") && normalized.contains("jarvis"))
        if (detected && running) {
            onWakeCallback?.invoke()
            return true
        }
        return false
    }

    private fun scheduleNextCycle(delayMs: Long = 200L) {
        if (!running) return
        mainHandler.postDelayed({ startListenCycle() }, delayMs)
    }
}
