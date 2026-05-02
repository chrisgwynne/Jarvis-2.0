package ai.openclaw.jarvis.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ai.openclaw.jarvis.capabilities.CapabilityRegistry
import ai.openclaw.jarvis.data.local.JarvisSettings
import ai.openclaw.jarvis.data.local.PairingStore
import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.identity.SpeakerIdentityManager
import ai.openclaw.jarvis.identity.SpeakerProfile
import ai.openclaw.jarvis.voice.AlwaysListeningService
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: SettingsDataStore,
    private val pairingStore: PairingStore,
    private val registry: CapabilityRegistry,
    private val identityManager: SpeakerIdentityManager,
) : ViewModel() {

    val settings: StateFlow<JarvisSettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JarvisSettings())

    val deviceId: String get() = pairingStore.getDeviceId()
    val isPaired: Boolean get() = pairingStore.isPaired()

    val capabilityStatus: List<Pair<String, Boolean>> get() =
        registry.all.map { it.id to it.isAvailable() }

    val enrolledProfiles: List<SpeakerProfile> get() = identityManager.getAllProfiles()
    val enrolledProfileCount: Int get() = identityManager.getAllProfiles().size

    fun updateGatewayUrl(v: String)         = viewModelScope.launch { store.updateGatewayUrl(v) }
    fun updateGatewayEnabled(v: Boolean)    = viewModelScope.launch { store.updateGatewayEnabled(v) }
    fun updateDeviceName(v: String)         = viewModelScope.launch { store.updateDeviceName(v) }
    fun updateSessionKey(v: String)         = viewModelScope.launch { store.updateSessionKey(v) }
    fun updateSpeakerName(v: String)        = viewModelScope.launch { store.updateSpeakerName(v) }
    fun updateTtsEnabled(v: Boolean)        = viewModelScope.launch { store.updateTtsEnabled(v) }
    fun updateTtsEngine(v: String)          = viewModelScope.launch { store.updateTtsEngine(v) }
    fun updateSttEngine(v: String)          = viewModelScope.launch { store.updateSttEngine(v) }
    fun updateTtsSpeed(v: Float)            = viewModelScope.launch { store.updateTtsSpeed(v) }
    fun updateTtsPitch(v: Float)            = viewModelScope.launch { store.updateTtsPitch(v) }
    fun updateWakeWord(v: Boolean)            = viewModelScope.launch { store.updateWakeWord(v) }
    fun updateWakePhrase(v: String)           = viewModelScope.launch { store.updateWakePhrase(v) }
    fun updateAlwaysListening(v: Boolean) {
        viewModelScope.launch { store.updateAlwaysListening(v) }
        if (v) AlwaysListeningService.start(context) else AlwaysListeningService.stop(context)
    }
    fun updatePtt(v: Boolean)               = viewModelScope.launch { store.updatePtt(v) }
    fun updateBluetoothAudio(v: Boolean)    = viewModelScope.launch { store.updateBluetoothAudio(v) }
    fun updateTrustedMode(v: Boolean)       = viewModelScope.launch { store.updateTrustedMode(v) }
    fun updateConfirmDestructive(v: Boolean)= viewModelScope.launch { store.updateConfirmDestructive(v) }
    fun updateSendLocation(v: Boolean)      = viewModelScope.launch { store.updateSendLocation(v) }
    fun updateSendScreen(v: Boolean)        = viewModelScope.launch { store.updateSendScreen(v) }
    fun updateDebugLogs(v: Boolean)          = viewModelScope.launch { store.updateDebugLogs(v) }
    fun updateSessionTimeout(v: Int)         = viewModelScope.launch { store.updateSessionTimeout(v) }
    fun updateRecordingEnabled(v: Boolean)   = viewModelScope.launch { store.updateRecordingEnabled(v) }
    fun updateRecordingRetention(v: Int)     = viewModelScope.launch { store.updateRecordingRetention(v) }
    fun updateNotificationStyle(v: String)   = viewModelScope.launch { store.updateNotificationStyle(v) }
    fun updateWakeSensitivity(v: Float)      = viewModelScope.launch { store.updateWakeSensitivity(v) }
    fun updateWakeConfirmSound(v: Boolean)   = viewModelScope.launch { store.updateWakeConfirmSound(v) }
    fun updateWakeSuppressTts(v: Boolean)    = viewModelScope.launch { store.updateWakeSuppressTts(v) }
    fun updateNodeSecret(v: String)          = viewModelScope.launch { store.updateNodeSecret(v) }
    fun clearPairing()                       = pairingStore.clearPairing()
}
