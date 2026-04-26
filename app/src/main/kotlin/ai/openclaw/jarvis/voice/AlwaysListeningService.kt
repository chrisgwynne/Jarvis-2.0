package ai.openclaw.jarvis.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import ai.openclaw.jarvis.MainActivity
import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.util.LogRedaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject

/**
 * Always-on foreground service for wake word + continuous listening.
 *
 * Lifecycle:
 *   - START_STICKY: Android will restart this service after it is killed.
 *   - Starts a repeating STT cycle using Android SpeechRecognizer (main thread).
 *   - When the wake phrase is detected, delegates to SpeechSessionManager.startSession().
 *   - After the full command session, returns to passive wake listening.
 *
 * Toggle: Settings → Always Listening.
 * When the setting is OFF, this service still runs but stays in PAUSED mode
 * (no mic, no STT) to avoid a cold-start delay when re-enabled.
 */
@AndroidEntryPoint
class AlwaysListeningService : Service() {

    @Inject lateinit var sessionManager: SpeechSessionManager
    @Inject lateinit var wakeController: SimpleWakeWordController
    @Inject lateinit var settingsStore: SettingsDataStore

    companion object {
        const val ACTION_START = "ai.openclaw.jarvis.ACTION_START_ALWAYS_LISTENING"
        const val ACTION_STOP  = "ai.openclaw.jarvis.ACTION_STOP_ALWAYS_LISTENING"

        private const val CHANNEL_ID     = "jarvis_always_listening"
        private const val NOTIFICATION_ID = 2001
        private const val TAG             = "AlwaysListeningService"

        fun start(context: Context) {
            val intent = Intent(context, AlwaysListeningService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlwaysListeningService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningForWake = false
    private var alwaysListeningEnabled = true

    // ─── Service lifecycle ────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Listening for wake word…"))

        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            else        -> {
                scope.launch {
                    alwaysListeningEnabled = settingsStore.settings.first().alwaysListeningEnabled
                    if (alwaysListeningEnabled) {
                        mainHandler.post { startWakeLoop() }
                    }
                }
                observeSettings()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        mainHandler.post {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
        super.onDestroy()
    }

    // ─── Wake word loop ───────────────────────────────────────────────────────

    private fun startWakeLoop() {
        if (!alwaysListeningEnabled) return
        if (isListeningForWake) return
        if (sessionManager.voiceState.value != VoiceState.IDLE) {
            // A session is active; check again after it finishes
            mainHandler.postDelayed({ startWakeLoop() }, 500)
            return
        }
        Log.d(TAG, "Starting wake word listen cycle")
        isListeningForWake = true
        launchWakeRecognizer()
    }

    private fun stopWakeLoop() {
        isListeningForWake = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun launchWakeRecognizer() {
        if (!isListeningForWake) return

        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = sr

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onResults(results: Bundle?) {
                isListeningForWake = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                // Never log the raw STT text — it can contain anything
                // the user said before the wake-word match.
                Log.d(TAG, "Wake check: ${LogRedaction.redactedText(text)}")

                if (wakeController.checkResult(text)) {
                    // Wake word detected → start full session
                    Log.i(TAG, "Wake word detected! Firing session.")
                    updateNotification("Wake word detected — listening…")
                    sessionManager.startSession(SessionTrigger.WAKE_WORD)
                    // After session, re-start wake loop
                    observeSessionEnd()
                } else {
                    // Not a wake phrase — restart immediately
                    mainHandler.postDelayed({ restartWakeCycle() }, 100)
                }
            }

            override fun onPartialResults(partial: Bundle?) {
                val matches = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                // Quick check on partials for faster response
                if (text.isNotBlank() && wakeController.checkResult(text)) {
                    sr.stopListening()
                }
            }

            override fun onError(error: Int) {
                isListeningForWake = false
                Log.d(TAG, "Wake STT error $error — restarting cycle")
                // Brief pause before restart to avoid hammering the recognizer
                val delay = when (error) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 2000L
                    SpeechRecognizer.ERROR_NO_MATCH        -> 200L
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT  -> 200L
                    else                                   -> 1000L
                }
                mainHandler.postDelayed({ restartWakeCycle() }, delay)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Short window for wake detection
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }
        sr.startListening(intent)
    }

    private fun restartWakeCycle() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        if (alwaysListeningEnabled) {
            isListeningForWake = false
            startWakeLoop()
        }
    }

    private fun observeSessionEnd() {
        scope.launch {
            sessionManager.voiceState
                .first { it == VoiceState.IDLE }
            // Session ended → back to wake loop
            updateNotification("Listening for wake word…")
            mainHandler.post { startWakeLoop() }
        }
    }

    // ─── Settings observer ────────────────────────────────────────────────────

    private fun observeSettings() {
        scope.launch {
            settingsStore.settings.collect { prefs ->
                val wasEnabled = alwaysListeningEnabled
                alwaysListeningEnabled = prefs.alwaysListeningEnabled
                when {
                    alwaysListeningEnabled && !wasEnabled -> {
                        updateNotification("Listening for wake word…")
                        mainHandler.post { startWakeLoop() }
                    }
                    !alwaysListeningEnabled && wasEnabled -> {
                        updateNotification("Always listening is paused")
                        mainHandler.post { stopWakeLoop() }
                    }
                }
                wakeController.run { /* phrase updated via settingsStore */ }
            }
        }
    }

    // ─── Notification helpers ─────────────────────────────────────────────────

    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Jarvis Always Listening", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Persistent mic access for wake word detection"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(status: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Jarvis")
        .setContentText(status)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .build()

    private fun updateNotification(status: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(status))
    }
}
