package ai.openclaw.jarvis.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import ai.openclaw.jarvis.capabilities.base.CapabilityResult
import ai.openclaw.jarvis.capabilities.impl.ContactsCapabilityImpl
import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.voice.TextToSpeechEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listens for incoming phone calls and announces the caller's name (or number)
 * via Jarvis TTS so the user knows who's calling without looking at the screen.
 *
 * Uses TelephonyCallback on API 31+ and the deprecated PhoneStateListener on
 * API 26–30, both producing the same behaviour.
 */
@Singleton
class IncomingCallMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tts: TextToSpeechEngine,
    private val contacts: ContactsCapabilityImpl,
    private val settings: SettingsDataStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    @Volatile private var running = false

    fun start() {
        if (running) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) return
        running = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val executor = Executor { scope.launch { it.run() } }
            telephony.registerTelephonyCallback(executor, object : TelephonyCallback(),
                TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    // incomingNumber is not directly available via TelephonyCallback —
                    // use the last ringing number stored by PhoneStateListener approach,
                    // or query call log. Here we just announce "incoming call".
                    if (state == TelephonyManager.CALL_STATE_RINGING) {
                        announceIncomingCall(null)
                    }
                }
            })
        } else {
            @Suppress("DEPRECATION")
            telephony.listen(object : PhoneStateListener() {
                @Deprecated("Deprecated in API 31")
                override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                    if (state == TelephonyManager.CALL_STATE_RINGING) {
                        announceIncomingCall(incomingNumber)
                    }
                }
            }, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun announceIncomingCall(incomingNumber: String?) {
        scope.launch {
            val prefs = settings.settings.first()
            if (!prefs.ttsEnabled) return@launch

            val callerLabel = if (!incomingNumber.isNullOrBlank()) {
                resolveContactName(incomingNumber) ?: incomingNumber
            } else {
                "someone"
            }
            tts.speak("Incoming call from $callerLabel.", prefs.ttsSpeed, prefs.ttsPitch)
        }
    }

    private suspend fun resolveContactName(number: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return null
        val normalized = number.replace(Regex("[^\\d+]"), "")
        return when (val result = contacts.findContact(normalized)) {
            is CapabilityResult.Success -> result.value.firstOrNull()?.name
            is CapabilityResult.Failure -> null
        }
    }
}
