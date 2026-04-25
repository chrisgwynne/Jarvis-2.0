package ai.openclaw.jarvis.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ai.openclaw.jarvis.audio.AudioRouteManager
import ai.openclaw.jarvis.debug.AssistantDebugState
import ai.openclaw.jarvis.debug.AssistantEventLog
import ai.openclaw.jarvis.network.OpenClawClient
import ai.openclaw.jarvis.statemachine.AssistantStateMachine
import ai.openclaw.jarvis.trust.TrustManager
import ai.openclaw.jarvis.voice.SpeechSessionManager
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val stateMachine: AssistantStateMachine,
    private val trustManager: TrustManager,
    private val gatewayClient: OpenClawClient,
    private val audioRouter: AudioRouteManager,
    private val eventLog: AssistantEventLog,
    private val sessionManager: SpeechSessionManager,
) : ViewModel() {

    val debugState: StateFlow<AssistantDebugState> = combine(
        stateMachine.state,
        trustManager.sessionTrust,
        gatewayClient.gatewayState,
        audioRouter.state,
        eventLog.events,
        sessionManager.transcript,
    ) { flows ->
        val machineState = flows[0] as ai.openclaw.jarvis.statemachine.AssistantState
        val trust        = flows[1] as? ai.openclaw.jarvis.trust.SessionTrust
        val gateway      = flows[2] as ai.openclaw.jarvis.data.models.GatewayState
        val audio        = flows[3] as ai.openclaw.jarvis.audio.AudioRouteState
        @Suppress("UNCHECKED_CAST")
        val log          = flows[4] as List<ai.openclaw.jarvis.debug.AssistantEvent>
        @Suppress("UNCHECKED_CAST")
        val transcript   = flows[5] as List<ai.openclaw.jarvis.voice.TranscriptEntry>

        AssistantDebugState(
            assistantState      = machineState,
            lastTranscript      = transcript.lastOrNull()?.text ?: "",
            speakerId           = trust?.speakerId ?: "unknown",
            trustLevel          = trust?.trustLevel ?: ai.openclaw.jarvis.trust.TrustLevel.UNKNOWN,
            identityConfidence  = trust?.confidence ?: 0f,
            gatewayState        = gateway,
            activeAudioDevice   = audio.activeDevice,
            lastError           = log.lastOrNull { it.isError }?.error,
            eventLog            = log,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AssistantDebugState(),
    )

    fun clearLog() = eventLog.clear()
}
