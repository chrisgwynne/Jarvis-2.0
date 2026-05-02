package ai.openclaw.jarvis.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Always-on foreground service for basic wake word + continuous listening.
 *
 * ENGINE: Basic STT burst detection (text matching) — not a native on-device engine.
 * Push-to-talk remains the most reliable input method.
 *
 * Lifecycle:
 *   - START_STICKY: Android restarts this service after it is killed.
 *   - Repeating STT cycle on the main thread via SpeechRecognizer.
 *   - On wake phrase match → delegates to SpeechSessionManager.startSession().
 *   - After the full session, returns to passive wake listening.
 *   - Suppresses microphone while Jarvis TTS is active (prevents self-triggering).
 *
 * Toggle: Settings → Voice & Audio → Always Listening.
 */
@AndroidEntryPoint
class AlwaysListeningService : Service() {

    @Inject lateinit var sessionManager: SpeechSessionManager
    @Inject lateinit var wakeController: SimpleWakeWordController
    @Inject lateinit var settingsStore: SettingsDataStore
    @Inject lateinit var listeningModeController: ListeningModeController

    companion object {
        const val ACTION_START           = "ai.openclaw.jarvis.ACTION_START_ALWAYS_LISTENING"
        const val ACTION_STOP            = "ai.openclaw.jarvis.ACTION_STOP_ALWAYS_LISTENING"
        const val ACTION_SILENCE         = "ai.openclaw.jarvis.ACTION_SILENCE"
        const val ACTION_STOP_LISTENING  = "ai.openclaw.jarvis.ACTION_STOP_LISTENING"
        const val ACTION_RESUME          = "ai.openclaw.jarvis.ACTION_RESUME"

        private const val CHANNEL_ID      = "jarvis_listening"
        private const val NOTIFICATION_ID = 2001
        private const val TAG             = "AlwaysListeningService"

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, AlwaysListeningService::class.java).apply { action = ACTION_START }
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AlwaysListeningService::class.java).apply { action = ACTION_STOP }
            )
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningForWake = false
    private var alwaysListeningEnabled = true
    private var notificationStyle = "minimal"
    private var currentMode: ListeningMode = ListeningMode.Active

    // Keeps the CPU alive through screen lock so SpeechRecognizer callbacks
    // (delivered to the main thread via Binder) are dispatched promptly.
    // Without this, the CPU can idle even while a foreground service is running,
    // which silently drops wake-word recognition results.
    private var wakeLock: PowerManager.WakeLock? = null

    // ─── Service lifecycle ────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val hasAudio = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasAudio && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification("Jarvis"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Jarvis"))
        }
        acquireWakeLock()

        when (intent?.action) {
            ACTION_STOP            -> { stopSelf(); return START_NOT_STICKY }
            ACTION_SILENCE         -> {
                listeningModeController.setSilent()
                sessionManager.cancelAll()
                return START_NOT_STICKY
            }
            ACTION_STOP_LISTENING  -> {
                listeningModeController.setStopped()
                return START_NOT_STICKY
            }
            ACTION_RESUME          -> {
                listeningModeController.setActive()
                return START_NOT_STICKY
            }
            else -> {
                // start() sets running=true and loads phrase/sensitivity from settings.
                // Must be called before the first checkResult() or it always returns false.
                wakeController.start {}
                scope.launch {
                    alwaysListeningEnabled = settingsStore.settings.first().alwaysListeningEnabled
                    wakeController.updateSensitivity(
                        settingsStore.settings.first().wakeSensitivity
                    )
                    if (alwaysListeningEnabled) {
                        mainHandler.post { startWakeLoop() }
                    } else {
                        updateNotification("Wake detection paused")
                    }
                }
                observeSettings()
                observeVoiceState()
                observeListeningMode()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        mainHandler.post {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "jarvis:wake_word_listening",
        ).apply { acquire() }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    // ─── Wake word loop ───────────────────────────────────────────────────────

    private fun startWakeLoop() {
        if (!alwaysListeningEnabled) return
        if (listeningModeController.mode.value != ListeningMode.Active) return
        if (isListeningForWake) return

        val voiceState = sessionManager.voiceState.value
        if (voiceState != VoiceState.IDLE) {
            // During TTS (SPEAKING) give extra clearance so we don't catch Jarvis's voice
            val delay = if (voiceState == VoiceState.SPEAKING) 1_500L else 500L
            mainHandler.postDelayed({ startWakeLoop() }, delay)
            return
        }

        Log.d(TAG, "Starting wake word listen cycle")
        updateNotification("Listening for wake word… (basic STT)")
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

        // Reuse recognizer instance across no-match cycles. Creating a new one every
        // time causes ERROR_CLIENT (11) because the speech service hasn't released
        // its resources. Only create fresh on first run or after a hard reset.
        val sr = speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also { fresh ->
            speechRecognizer = fresh
            fresh.setRecognitionListener(wakeListener)
        }

        sr.startListening(wakeIntent())
    }

    private val wakeListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onResults(results: Bundle?) {
            isListeningForWake = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: ""
            Log.d(TAG, "Wake check: ${LogRedaction.redactedText(text)}")

            if (wakeController.checkResult(text)) {
                Log.i(TAG, "Wake word detected")
                updateNotification("Wake word detected — listening…")
                sessionManager.startSession(SessionTrigger.WAKE_WORD)
                // Wake loop is stopped by observeVoiceState() when state goes non-IDLE,
                // and restarted automatically when the session ends.
            } else {
                scheduleNextCycle(300L, hardReset = false)
            }
        }

        override fun onPartialResults(partial: Bundle?) {
            val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: ""
            if (text.isNotBlank() && wakeController.checkResult(text)) {
                speechRecognizer?.stopListening()
            }
        }

        override fun onError(error: Int) {
            isListeningForWake = false
            Log.d(TAG, "Wake STT error $error")
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleNextCycle(750L, hardReset = false)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleNextCycle(2_500L, hardReset = true)
                SpeechRecognizer.ERROR_CLIENT -> scheduleNextCycle(1_500L, hardReset = true)
                else -> scheduleNextCycle(1_200L, hardReset = true)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun wakeIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
    }

    /**
     * [hardReset] = true: destroy recognizer (required after BUSY/CLIENT errors).
     * [hardReset] = false: reuse instance to avoid ERROR_CLIENT on rapid restarts.
     */
    private fun scheduleNextCycle(delayMs: Long, hardReset: Boolean) {
        if (hardReset) {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
        if (alwaysListeningEnabled) {
            mainHandler.postDelayed({
                isListeningForWake = false
                startWakeLoop()
            }, delayMs)
        }
    }

    /**
     * Permanently watches voice state so the wake-word recognizer yields the mic
     * the instant ANY session starts (PTT or wake-word) and restarts when idle again.
     *
     * This replaces the old per-session observeSessionEnd() which only fired after
     * a wake-word trigger and left the wake STT conflicting with PTT sessions.
     */
    private fun observeVoiceState() {
        scope.launch {
            var wasIdle = true
            sessionManager.voiceState.collect { state ->
                val isIdle = state == VoiceState.IDLE
                when {
                    !isIdle && wasIdle -> {
                        // Session started — release the mic immediately so the
                        // session's own STT doesn't get ERROR_RECOGNIZER_BUSY.
                        mainHandler.post { stopWakeLoop() }
                        updateNotification("Active")
                    }
                    isIdle && !wasIdle -> {
                        // Session ended — restart after a delay so we don't
                        // immediately catch TTS audio or system sounds.
                        updateNotificationForMode(currentMode)
                        if (alwaysListeningEnabled) {
                            mainHandler.postDelayed({ startWakeLoop() }, 1_500L)
                        }
                    }
                }
                wasIdle = isIdle
            }
        }
    }

    // ─── Listening mode observer ──────────────────────────────────────────────

    private fun observeListeningMode() {
        scope.launch {
            listeningModeController.mode.collect { mode ->
                currentMode = mode
                updateNotificationForMode(mode)
                when (mode) {
                    ListeningMode.Active -> {
                        if (alwaysListeningEnabled) mainHandler.post { startWakeLoop() }
                    }
                    ListeningMode.Silent,
                    is ListeningMode.Paused -> {
                        mainHandler.post { stopWakeLoop() }
                    }
                    ListeningMode.Stopped -> {
                        mainHandler.post { stopWakeLoop() }
                        stopSelf()
                    }
                }
            }
        }
    }

    // ─── Settings observer ────────────────────────────────────────────────────

    private fun observeSettings() {
        scope.launch {
            settingsStore.settings.collect { prefs ->
                val wasEnabled = alwaysListeningEnabled
                alwaysListeningEnabled = prefs.alwaysListeningEnabled
                notificationStyle = prefs.notificationStyle
                wakeController.updatePhrase(prefs.wakePhrase)
                wakeController.updateSensitivity(prefs.wakeSensitivity)
                when {
                    alwaysListeningEnabled && !wasEnabled -> {
                        updateNotificationForMode(currentMode)
                        mainHandler.post { startWakeLoop() }
                    }
                    !alwaysListeningEnabled && wasEnabled -> {
                        updateNotificationForMode(currentMode)
                        mainHandler.post { stopWakeLoop() }
                    }
                    else -> updateNotificationForMode(currentMode)
                }
            }
        }
    }

    // ─── Notification helpers ─────────────────────────────────────────────────

    // The green microphone privacy indicator is controlled by the Android OS
    // and appears automatically when any app holds the microphone. It cannot
    // replace the foreground service notification, which Android requires while
    // AlwaysListeningService is running. We make the notification as silent and
    // unobtrusive as the platform permits.

    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Delete old channel if it exists under the previous ID so users aren't
        // left with a ghost channel in notification settings.
        nm.deleteNotificationChannel("jarvis_always_listening")
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Jarvis",
                    NotificationManager.IMPORTANCE_MIN,  // no sound, no status-bar icon, no heads-up
                ).apply {
                    description = "Required foreground service indicator while Jarvis is listening."
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                }
            )
        }
    }

    private fun buildNotification(status: String): android.app.Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(tapIntent)

        when (notificationStyle) {
            "controls" -> {
                builder.addAction(android.R.drawable.ic_lock_silent_mode, "Silence",
                    pendingServiceAction(ACTION_SILENCE, 1))
                builder.addAction(android.R.drawable.ic_delete, "Stop",
                    pendingServiceAction(ACTION_STOP_LISTENING, 2))
                builder.addAction(android.R.drawable.ic_media_play, "Resume",
                    pendingServiceAction(ACTION_RESUME, 3))
            }
            else -> {  // minimal + diagnostic: one action only
                builder.addAction(android.R.drawable.ic_delete, "Stop",
                    pendingServiceAction(ACTION_STOP_LISTENING, 2))
            }
        }

        return builder.build()
    }

    private fun pendingServiceAction(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, AlwaysListeningService::class.java).apply { this.action = action },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun updateNotification(status: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun updateNotificationForMode(mode: ListeningMode) {
        val text = if (notificationStyle == "diagnostic") mode.diagnosticText else mode.notificationText
        updateNotification(text)
    }
}
