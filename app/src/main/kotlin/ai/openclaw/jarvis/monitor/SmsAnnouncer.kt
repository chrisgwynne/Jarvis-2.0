package ai.openclaw.jarvis.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import ai.openclaw.jarvis.capabilities.base.CapabilityResult
import ai.openclaw.jarvis.capabilities.impl.ContactsCapabilityImpl
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
 * BroadcastReceiver for incoming SMS.
 * When a text arrives, resolves the sender's contact name and reads
 * the message aloud via Jarvis TTS — useful for a hands-free phone.
 *
 * Registered in AndroidManifest.xml for android.provider.Telephony.SMS_RECEIVED.
 * Requires RECEIVE_SMS permission.
 */
@AndroidEntryPoint
class SmsAnnouncer : BroadcastReceiver() {

    @Inject lateinit var tts: TextToSpeechEngine
    @Inject lateinit var contacts: ContactsCapabilityImpl
    @Inject lateinit var settings: SettingsDataStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        val grouped = messages.groupBy { it.originatingAddress ?: "Unknown" }
        grouped.forEach { (sender, parts) ->
            val body = parts.joinToString("") { it.messageBody ?: "" }.trim()
            if (body.isBlank()) return@forEach
            scope.launch {
                val prefs = settings.settings.first()
                if (!prefs.ttsEnabled) return@launch
                val callerLabel = resolveContactName(sender) ?: sender
                val spoken = if (body.length > 150) {
                    "Message from $callerLabel: ${body.take(150)}…"
                } else {
                    "Message from $callerLabel: $body"
                }
                tts.speak(spoken, prefs.ttsSpeed, prefs.ttsPitch)
            }
        }
    }

    private suspend fun resolveContactName(number: String): String? {
        val normalized = number.replace(Regex("[^\\d+]"), "")
        return when (val result = contacts.findContact(normalized)) {
            is CapabilityResult.Success -> result.value.firstOrNull()?.name
            is CapabilityResult.Failure -> null
        }
    }
}
