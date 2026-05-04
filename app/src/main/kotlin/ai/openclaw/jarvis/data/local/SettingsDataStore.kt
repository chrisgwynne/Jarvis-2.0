package ai.openclaw.jarvis.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "jarvis_settings")

data class JarvisSettings(
    // Gateway
    val gatewayUrl: String = "ws://192.168.1.100:8765",
    val gatewayEnabled: Boolean = true,
    val deviceName: String = "Jarvis Android",
    val sessionKey: String = "jarvis:user:android",
    val speakerName: String = "user",
    // Voice
    val ttsEnabled: Boolean = true,
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val wakeWordEnabled: Boolean = false,
    val wakePhrase: String = "hey jarvis",
    val alwaysListeningEnabled: Boolean = false,
    val pushToTalkEnabled: Boolean = true,
    // Audio routing
    val bluetoothAudioEnabled: Boolean = true,
    // Behaviour
    val trustedMode: Boolean = false,
    val confirmDestructive: Boolean = true,
    val sendLocationContext: Boolean = false,
    val sendScreenContext: Boolean = false,
    val debugLogsEnabled: Boolean = false,
    // Identity / session
    val sessionTimeoutMinutes: Int = 15,
    // Recording
    val conversationRecordingEnabled: Boolean = false,
    val recordingRetentionHours: Int = 24,
    // Hermes Agent
    val hermesEnabled: Boolean = false,
    val hermesHostname: String = "",
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.settingsDataStore

    private object Keys {
        val GATEWAY_URL       = stringPreferencesKey("gateway_url")
        val GATEWAY_ENABLED   = booleanPreferencesKey("gateway_enabled")
        val DEVICE_NAME       = stringPreferencesKey("device_name")
        val SESSION_KEY       = stringPreferencesKey("session_key")
        val SPEAKER_NAME      = stringPreferencesKey("speaker_name")
        val TTS_ENABLED       = booleanPreferencesKey("tts_enabled")
        val TTS_SPEED         = stringPreferencesKey("tts_speed")
        val TTS_PITCH         = stringPreferencesKey("tts_pitch")
        val WAKE_WORD_ENABLED     = booleanPreferencesKey("wake_word_enabled")
        val WAKE_PHRASE           = stringPreferencesKey("wake_phrase")
        val ALWAYS_LISTENING      = booleanPreferencesKey("always_listening_enabled")
        val PTT_ENABLED           = booleanPreferencesKey("ptt_enabled")
        val BLUETOOTH_AUDIO       = booleanPreferencesKey("bluetooth_audio_enabled")
        val TRUSTED_MODE          = booleanPreferencesKey("trusted_mode")
        val CONFIRM_DESTRUCTIVE   = booleanPreferencesKey("confirm_destructive")
        val SEND_LOCATION         = booleanPreferencesKey("send_location_context")
        val SEND_SCREEN           = booleanPreferencesKey("send_screen_context")
        val DEBUG_LOGS            = booleanPreferencesKey("debug_logs")
        val SESSION_TIMEOUT       = stringPreferencesKey("session_timeout_minutes")
        val RECORDING_ENABLED     = booleanPreferencesKey("conversation_recording_enabled")
        val RECORDING_RETENTION   = stringPreferencesKey("recording_retention_hours")
        val HERMES_ENABLED        = booleanPreferencesKey("hermes_enabled")
        val HERMES_HOSTNAME       = stringPreferencesKey("hermes_hostname")
    }

    val settings: Flow<JarvisSettings> = store.data.map { prefs ->
        JarvisSettings(
            gatewayUrl         = prefs[Keys.GATEWAY_URL]         ?: "ws://192.168.1.100:8765",
            gatewayEnabled     = prefs[Keys.GATEWAY_ENABLED]     ?: true,
            deviceName         = prefs[Keys.DEVICE_NAME]         ?: "Jarvis Android",
            sessionKey         = prefs[Keys.SESSION_KEY]         ?: "jarvis:user:android",
            speakerName        = prefs[Keys.SPEAKER_NAME]        ?: "user",
            ttsEnabled         = prefs[Keys.TTS_ENABLED]         ?: true,
            ttsSpeed           = prefs[Keys.TTS_SPEED]?.toFloatOrNull()  ?: 1.0f,
            ttsPitch           = prefs[Keys.TTS_PITCH]?.toFloatOrNull()  ?: 1.0f,
            wakeWordEnabled        = prefs[Keys.WAKE_WORD_ENABLED]  ?: false,
            wakePhrase             = prefs[Keys.WAKE_PHRASE]         ?: "hey jarvis",
            alwaysListeningEnabled = prefs[Keys.ALWAYS_LISTENING]    ?: false,
            pushToTalkEnabled      = prefs[Keys.PTT_ENABLED]         ?: true,
            bluetoothAudioEnabled  = prefs[Keys.BLUETOOTH_AUDIO]     ?: true,
            trustedMode            = prefs[Keys.TRUSTED_MODE]        ?: false,
            confirmDestructive = prefs[Keys.CONFIRM_DESTRUCTIVE] ?: true,
            sendLocationContext        = prefs[Keys.SEND_LOCATION]          ?: false,
            sendScreenContext          = prefs[Keys.SEND_SCREEN]            ?: false,
            debugLogsEnabled          = prefs[Keys.DEBUG_LOGS]             ?: false,
            sessionTimeoutMinutes     = prefs[Keys.SESSION_TIMEOUT]?.toIntOrNull() ?: 15,
            conversationRecordingEnabled = prefs[Keys.RECORDING_ENABLED]   ?: false,
            recordingRetentionHours   = prefs[Keys.RECORDING_RETENTION]?.toIntOrNull() ?: 24,
            hermesEnabled             = prefs[Keys.HERMES_ENABLED]         ?: false,
            hermesHostname            = prefs[Keys.HERMES_HOSTNAME]        ?: "",
        )
    }

    suspend fun updateGatewayUrl(url: String)     = store.edit { it[Keys.GATEWAY_URL]       = url }
    suspend fun updateGatewayEnabled(v: Boolean)  = store.edit { it[Keys.GATEWAY_ENABLED]   = v }
    suspend fun updateDeviceName(name: String)    = store.edit { it[Keys.DEVICE_NAME]       = name }
    suspend fun updateSessionKey(key: String)     = store.edit { it[Keys.SESSION_KEY]       = key }
    suspend fun updateSpeakerName(name: String)   = store.edit { it[Keys.SPEAKER_NAME]      = name }
    suspend fun updateTtsEnabled(v: Boolean)      = store.edit { it[Keys.TTS_ENABLED]       = v }
    suspend fun updateTtsSpeed(v: Float)          = store.edit { it[Keys.TTS_SPEED]         = v.toString() }
    suspend fun updateTtsPitch(v: Float)          = store.edit { it[Keys.TTS_PITCH]         = v.toString() }
    suspend fun updateWakeWord(v: Boolean)          = store.edit { it[Keys.WAKE_WORD_ENABLED] = v }
    suspend fun updateWakePhrase(v: String)         = store.edit { it[Keys.WAKE_PHRASE]        = v }
    suspend fun updateAlwaysListening(v: Boolean)   = store.edit { it[Keys.ALWAYS_LISTENING]   = v }
    suspend fun updatePtt(v: Boolean)               = store.edit { it[Keys.PTT_ENABLED]        = v }
    suspend fun updateBluetoothAudio(v: Boolean)    = store.edit { it[Keys.BLUETOOTH_AUDIO]    = v }
    suspend fun updateTrustedMode(v: Boolean)     = store.edit { it[Keys.TRUSTED_MODE]      = v }
    suspend fun updateConfirmDestructive(v: Boolean) = store.edit { it[Keys.CONFIRM_DESTRUCTIVE] = v }
    suspend fun updateSendLocation(v: Boolean)       = store.edit { it[Keys.SEND_LOCATION]       = v }
    suspend fun updateSendScreen(v: Boolean)         = store.edit { it[Keys.SEND_SCREEN]         = v }
    suspend fun updateDebugLogs(v: Boolean)          = store.edit { it[Keys.DEBUG_LOGS]          = v }
    suspend fun updateSessionTimeout(v: Int)         = store.edit { it[Keys.SESSION_TIMEOUT]     = v.toString() }
    suspend fun updateRecordingEnabled(v: Boolean)   = store.edit { it[Keys.RECORDING_ENABLED]  = v }
    suspend fun updateRecordingRetention(v: Int)     = store.edit { it[Keys.RECORDING_RETENTION] = v.toString() }
    suspend fun updateHermesEnabled(v: Boolean)      = store.edit { it[Keys.HERMES_ENABLED]      = v }
    suspend fun updateHermesHostname(v: String)      = store.edit { it[Keys.HERMES_HOSTNAME]     = v }
}
