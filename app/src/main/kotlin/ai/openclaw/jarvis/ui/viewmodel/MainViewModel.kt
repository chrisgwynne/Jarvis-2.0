package ai.openclaw.jarvis.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ai.openclaw.jarvis.data.local.JarvisSettings
import ai.openclaw.jarvis.data.local.OfflineQueueStore
import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.data.models.GatewayState
import ai.openclaw.jarvis.data.models.RouteChoice
import ai.openclaw.jarvis.network.GatewayEvent
import ai.openclaw.jarvis.network.OpenClawClient
import ai.openclaw.jarvis.session.SessionEventLogger
import ai.openclaw.jarvis.trust.SessionTrust
import ai.openclaw.jarvis.voice.ConfirmationRequest
import ai.openclaw.jarvis.voice.TranscriptEntry
import ai.openclaw.jarvis.voice.VoiceFrontend
import ai.openclaw.jarvis.voice.VoiceState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val voiceFrontend: VoiceFrontend,
    private val gatewayClient: OpenClawClient,
    private val offlineQueue: OfflineQueueStore,
    private val sessionLogger: SessionEventLogger,
    private val settingsStore: SettingsDataStore,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState>                     = voiceFrontend.voiceState
    val transcript: StateFlow<List<TranscriptEntry>>          = voiceFrontend.transcript
    val partialText: StateFlow<String>                        = voiceFrontend.partialText
    val gatewayState: StateFlow<GatewayState>                 = gatewayClient.gatewayState
    val pendingConfirmation: StateFlow<ConfirmationRequest?>  = voiceFrontend.pendingConfirmation
    val sessionTrust: StateFlow<SessionTrust?>                = voiceFrontend.sessionTrust

    val queueSize: StateFlow<Int> = offlineQueue.queueSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val debugLogsEnabled: StateFlow<Boolean> = settingsStore.settings
        .map { it.debugLogsEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _lastRoute  = MutableStateFlow<RouteChoice?>(null)
    val lastRoute: StateFlow<RouteChoice?> = _lastRoute.asStateFlow()

    private val _lastResult = MutableStateFlow<String?>(null)
    val lastResult: StateFlow<String?> = _lastResult.asStateFlow()

    /**
     * Pairing challenge surfaced from `OpenClawClient` so a user can
     * read the code off OpenClaw and confirm it on the phone. Cleared
     * by [acknowledgePairingChallenge] when the user dismisses the
     * dialog, or replaced by a fresh challenge if the timer expires.
     */
    private val _pairingChallenge = MutableStateFlow<PairingChallenge?>(null)
    val pairingChallenge: StateFlow<PairingChallenge?> = _pairingChallenge.asStateFlow()

    fun acknowledgePairingChallenge() { _pairingChallenge.value = null }

    init {
        // Mirror latest route from transcript
        viewModelScope.launch {
            transcript.collect { entries ->
                entries.lastOrNull { it.speaker == "user" }?.let { entry ->
                    _lastRoute.value = when (entry.route) {
                        "ANDROID_LOCAL" -> RouteChoice.ANDROID_LOCAL
                        "OpenClaw"      -> RouteChoice.OPENCLAW
                        "OPENCLAW"      -> RouteChoice.OPENCLAW
                        "MIXED"         -> RouteChoice.MIXED
                        else            -> null
                    }
                }
                entries.lastOrNull { it.speaker == "jarvis" }?.let { entry ->
                    _lastResult.value = entry.text.take(120)
                }
            }
        }

        // Mirror assistant replies to complete pending log entries
        viewModelScope.launch {
            gatewayClient.events.collect { event ->
                when (event) {
                    is GatewayEvent.AssistantReply -> {
                        val reply = event.frame.spokenReply ?: event.frame.text
                        val eventId = event.frame.eventId
                        if (reply != null && eventId != null) {
                            sessionLogger.completePending(eventId, reply)
                        }
                    }
                    is GatewayEvent.PairingChallenge -> {
                        _pairingChallenge.value = PairingChallenge(
                            code = event.code,
                            expiresAtMillis = System.currentTimeMillis() +
                                event.expiresIn * 1000L,
                        )
                    }
                    else -> Unit
                }
            }
        }
    }

    // ─── PTT ─────────────────────────────────────────────────────────────────

    fun onPttPress() = voiceFrontend.startListening()
    fun onPttRelease() = voiceFrontend.stopListening()

    // ─── Confirmation ─────────────────────────────────────────────────────────

    fun confirmPending() = voiceFrontend.confirmPending()
    fun dismissConfirmation() = voiceFrontend.dismissConfirmation()
}

/**
 * UI-side projection of [GatewayEvent.PairingChallenge] — the code the
 * user has to confirm to finish device pairing, plus when the code
 * expires so the dialog can show a countdown.
 */
data class PairingChallenge(
    val code: String,
    val expiresAtMillis: Long,
)
