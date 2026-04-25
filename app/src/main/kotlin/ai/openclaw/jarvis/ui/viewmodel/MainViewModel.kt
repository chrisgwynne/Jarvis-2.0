package ai.openclaw.jarvis.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ai.openclaw.jarvis.data.local.OfflineQueueStore
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
) : ViewModel() {

    val voiceState: StateFlow<VoiceState>                     = voiceFrontend.voiceState
    val transcript: StateFlow<List<TranscriptEntry>>          = voiceFrontend.transcript
    val partialText: StateFlow<String>                        = voiceFrontend.partialText
    val gatewayState: StateFlow<GatewayState>                 = gatewayClient.gatewayState
    val pendingConfirmation: StateFlow<ConfirmationRequest?>  = voiceFrontend.pendingConfirmation
    val sessionTrust: StateFlow<SessionTrust?>                = voiceFrontend.sessionTrust

    val queueSize: StateFlow<Int> = offlineQueue.queueSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _lastRoute  = MutableStateFlow<RouteChoice?>(null)
    val lastRoute: StateFlow<RouteChoice?> = _lastRoute.asStateFlow()

    private val _lastResult = MutableStateFlow<String?>(null)
    val lastResult: StateFlow<String?> = _lastResult.asStateFlow()

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
                if (event is GatewayEvent.AssistantReply) {
                    val reply = event.frame.spokenReply ?: event.frame.text ?: return@collect
                    val eventId = event.frame.eventId ?: return@collect
                    sessionLogger.completePending(eventId, reply)
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
