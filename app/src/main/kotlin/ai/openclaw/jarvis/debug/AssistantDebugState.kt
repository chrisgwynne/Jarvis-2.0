package ai.openclaw.jarvis.debug

import ai.openclaw.jarvis.audio.AudioDevice
import ai.openclaw.jarvis.data.models.GatewayState
import ai.openclaw.jarvis.statemachine.AssistantState
import ai.openclaw.jarvis.trust.TrustLevel

data class AssistantDebugState(
    val assistantState: AssistantState = AssistantState.DISABLED,
    val lastTranscript: String = "",
    val speakerId: String = "unknown",
    val trustLevel: TrustLevel = TrustLevel.UNKNOWN,
    val identityConfidence: Float = 0f,
    val pendingAction: String? = null,
    val gatewayState: GatewayState = GatewayState.DISCONNECTED,
    val activeAudioDevice: AudioDevice = AudioDevice.PHONE_SPEAKER,
    val lastError: String? = null,
    val eventLog: List<AssistantEvent> = emptyList(),
)
