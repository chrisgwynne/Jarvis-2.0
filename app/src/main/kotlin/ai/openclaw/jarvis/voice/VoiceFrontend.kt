package ai.openclaw.jarvis.voice

import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.data.models.RouteChoice
import ai.openclaw.jarvis.trust.SessionTrust
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Public facade for push-to-talk and voice UI state.
 *
 * All actual session logic now lives in SpeechSessionManager.
 * VoiceFrontend is a thin PTT wrapper that exposes the state flows
 * that the UI and ViewModels observe.
 *
 * AlwaysListeningService calls SpeechSessionManager directly for
 * wake-word-triggered sessions.
 */
@Singleton
class VoiceFrontend @Inject constructor(
    val sessionManager: SpeechSessionManager,
    private val settings: SettingsDataStore,
) {
    // Expose session state flows directly
    val voiceState: StateFlow<VoiceState>                    = sessionManager.voiceState
    val partialText: StateFlow<String>                       = sessionManager.partialText
    val transcript: StateFlow<List<TranscriptEntry>>         = sessionManager.transcript
    val pendingConfirmation: StateFlow<ConfirmationRequest?> = sessionManager.pendingConfirmation
    val sessionTrust: StateFlow<SessionTrust?>               = sessionManager.sessionTrust

    /** Start a push-to-talk session. */
    fun startListening() = sessionManager.startSession(SessionTrigger.PTT)

    /** Stop capturing (user released PTT button). */
    fun stopListening() = sessionManager.stopCapture()

    /** Interrupt everything — TTS, STT, any in-flight execution. */
    fun cancelAll() = sessionManager.cancelAll()

    /** Confirm a pending sensitive action (SMS, call, WhatsApp). */
    fun confirmPending() = sessionManager.confirmPending()

    /** Dismiss a pending confirmation dialog. */
    fun dismissConfirmation() = sessionManager.dismissConfirmation()
}
