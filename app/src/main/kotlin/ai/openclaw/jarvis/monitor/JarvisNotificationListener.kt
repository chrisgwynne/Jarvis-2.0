package ai.openclaw.jarvis.monitor

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.voice.TextToSpeechEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reads incoming notifications aloud via Jarvis TTS.
 *
 * Activated via: Settings → Apps → Special app access → Notification access → Jarvis.
 * Until the user grants access there, this service is idle — no crash or error.
 *
 * Filters out:
 *  - Ongoing / persistent notifications (navigation, music players, foreground services)
 *  - Jarvis's own notifications
 *  - System noise (low-priority, empty title+text)
 *  - Duplicate rapid-fire posts (same package + text within 5 s)
 */
@AndroidEntryPoint
class JarvisNotificationListener : NotificationListenerService() {

    @Inject lateinit var tts: TextToSpeechEngine
    @Inject lateinit var settings: SettingsDataStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Simple dedup: track last spoken text per package to avoid repeating identical toasts.
    private val recentByPackage = mutableMapOf<String, Pair<String, Long>>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (shouldSkip(sbn)) return

        val notification = sbn.notification ?: return
        val extras = notification.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim()

        if (title.isNullOrBlank() && text.isNullOrBlank()) return

        val spoken = when {
            !title.isNullOrBlank() && !text.isNullOrBlank() -> "$title: $text"
            !title.isNullOrBlank() -> title
            else -> text!!
        }.let { if (it.length > 200) it.take(200) + "…" else it }

        // Dedup: skip if same text from same app within 5 seconds.
        val now = System.currentTimeMillis()
        val last = recentByPackage[sbn.packageName]
        if (last != null && last.first == spoken && now - last.second < 5_000) return
        recentByPackage[sbn.packageName] = spoken to now

        scope.launch {
            val prefs = settings.settings.first()
            if (!prefs.ttsEnabled) return@launch
            tts.speak(spoken, prefs.ttsSpeed, prefs.ttsPitch)
        }
    }

    private fun shouldSkip(sbn: StatusBarNotification): Boolean {
        // Never read our own notifications.
        if (sbn.packageName == packageName) return true
        // Skip ongoing (foreground service, navigation, media).
        if (sbn.isOngoing) return true
        // Skip grouped children — the summary handles it.
        val notification = sbn.notification ?: return true
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
            && notification.group != null) return true
        // Skip low-priority silent notifications.
        if (notification.priority <= Notification.PRIORITY_MIN) return true
        return false
    }
}
