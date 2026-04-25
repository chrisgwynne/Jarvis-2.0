package ai.openclaw.jarvis.voice

import android.util.Log
import ai.openclaw.jarvis.audio.AudioRouteManager
import ai.openclaw.jarvis.data.local.JarvisSettings
import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.data.models.SpeakerContext
import ai.openclaw.jarvis.executor.ActionOutcome
import ai.openclaw.jarvis.executor.AndroidActionExecutor
import ai.openclaw.jarvis.identity.EnrolmentPhase
import ai.openclaw.jarvis.identity.EnrolmentSession
import ai.openclaw.jarvis.identity.SpeakerIdentityManager
import ai.openclaw.jarvis.network.GatewayEvent
import ai.openclaw.jarvis.network.OpenClawClient
import ai.openclaw.jarvis.router.*
import ai.openclaw.jarvis.session.SessionEventLogger
import ai.openclaw.jarvis.trust.PermissionManager
import ai.openclaw.jarvis.trust.TrustManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central coordinator for all voice sessions.
 *
 * Both VoiceFrontend (PTT) and AlwaysListeningService (wake word) delegate here.
 * Owns the full state machine: audio routing → identity → STT → parse → permission → execute → TTS → log.
 */
@Singleton
class SpeechSessionManager @Inject constructor(
    private val stt: AndroidSpeechToText,
    private val tts: AndroidTextToSpeech,
    private val intentParser: IntentParser,
    private val commRouter: CommunicationRouter,
    private val executor: AndroidActionExecutor,
    private val client: OpenClawClient,
    private val logger: SessionEventLogger,
    private val audioRouter: AudioRouteManager,
    private val settings: SettingsDataStore,
    private val identityManager: SpeakerIdentityManager,
    private val trustManager: TrustManager,
    private val permissionManager: PermissionManager,
    private val voiceBuffer: VoiceBufferManager,
    private val recordingManager: RecordingManager,
    private val enrolmentSession: EnrolmentSession,
) {
    companion object {
        private const val TAG = "SpeechSessionManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _voiceState  = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _transcript  = MutableStateFlow<List<TranscriptEntry>>(emptyList())
    val transcript: StateFlow<List<TranscriptEntry>> = _transcript.asStateFlow()

    private val _lastParsedIntent = MutableStateFlow<ParsedIntent?>(null)
    val lastParsedIntent: StateFlow<ParsedIntent?> = _lastParsedIntent.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<ConfirmationRequest?>(null)
    val pendingConfirmation: StateFlow<ConfirmationRequest?> = _pendingConfirmation.asStateFlow()

    // Expose identity state for UI
    val sessionTrust = trustManager.sessionTrust

    private var activeSessionJob: Job? = null
    private var interrupted = false

    init {
        scope.launch {
            client.events.filterIsInstance<GatewayEvent.AssistantReply>().collect { event ->
                val reply = event.frame.spokenReply ?: event.frame.text ?: return@collect
                addTranscript("jarvis", reply, "OpenClaw")
                speakIfEnabled(reply)
                event.frame.eventId?.let { logger.completePending(it, reply) }
            }
        }
    }

    // ─── Session control ──────────────────────────────────────────────────────

    fun startSession(trigger: SessionTrigger = SessionTrigger.PTT) {
        if (_voiceState.value != VoiceState.IDLE) return
        if (!stt.isAvailable()) {
            scope.launch { speakIfEnabled("Speech recognition is not available.") }
            return
        }
        interrupted = false
        activeSessionJob = scope.launch { runSession(trigger) }
    }

    fun stopCapture() { stt.cancel() }

    fun cancelAll() {
        interrupted = true
        tts.stop()
        stt.cancel()
        activeSessionJob?.cancel()
        audioRouter.releaseAudioFocus()
        _voiceState.value = VoiceState.IDLE
        _partialText.value = ""
    }

    fun confirmPending() {
        val req = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        scope.launch { executeConfirmed(req) }
    }

    fun dismissConfirmation() {
        _pendingConfirmation.value = null
        scope.launch { speakIfEnabled("Okay, cancelled.") }
    }

    // ─── Core session loop ────────────────────────────────────────────────────

    private suspend fun runSession(trigger: SessionTrigger) {
        val prefs = settings.settings.first()

        // 1. Prepare audio routing
        _voiceState.value = VoiceState.LISTENING
        audioRouter.prepareForCapture()

        // 2. Identity check — only when trust session has expired and profiles exist
        if (!trustManager.hasActiveSession() && identityManager.hasProfiles()) {
            performIdentityCheck(prefs)
        }

        // 3. STT
        var finalText = ""
        try {
            stt.listen().collect { event ->
                if (interrupted) return@collect
                when (event) {
                    is SttEvent.Partial -> _partialText.value = event.text
                    is SttEvent.Final   -> { finalText = event.text; _partialText.value = "" }
                    is SttEvent.Error   -> {
                        Log.w(TAG, "STT error ${event.code}: ${event.message}")
                        _voiceState.value = VoiceState.IDLE
                        audioRouter.releaseAudioFocus()
                        return@collect
                    }
                    else -> Unit
                }
            }
        } catch (e: CancellationException) {
            _voiceState.value = VoiceState.IDLE
            audioRouter.releaseAudioFocus()
            return
        }

        if (interrupted || finalText.isBlank()) {
            _voiceState.value = VoiceState.IDLE
            audioRouter.releaseAudioFocus()
            return
        }

        // 4. Parse intent
        _voiceState.value = VoiceState.PROCESSING
        val parsed = intentParser.parse(finalText)
        _lastParsedIntent.value = parsed
        Log.d(TAG, "Parsed: ${parsed.type} confidence=${parsed.confidence} trust=${trustManager.currentTrustLevel()}")

        // 5. CANCEL_STOP — immediate
        if (parsed.type == IntentType.CANCEL_STOP) {
            tts.stop()
            addTranscript("user", finalText, "Android")
            addTranscript("jarvis", "Okay, stopping.", "Android")
            audioRouter.releaseAudioFocus()
            _voiceState.value = VoiceState.IDLE
            return
        }

        // 6. Enrolment commands
        if (parsed.type == IntentType.ENROL_VOICE) {
            addTranscript("user", finalText, "Android")
            audioRouter.releaseAudioFocus()
            runEnrolment(prefs)
            if (!interrupted) _voiceState.value = VoiceState.IDLE
            return
        }

        // 7. Recording commands
        if (parsed.type == IntentType.RECORDING_START || parsed.type == IntentType.RECORDING_STOP) {
            addTranscript("user", finalText, "Android")
            val reply = handleRecordingCommand(parsed.type, prefs)
            addTranscript("jarvis", reply, "Android")
            audioRouter.releaseAudioFocus()
            speakIfEnabled(reply)
            if (!interrupted) _voiceState.value = VoiceState.IDLE
            return
        }

        // 8. Permission check
        val trust      = trustManager.currentTrustLevel()
        val required   = permissionManager.requiredFor(parsed.type)
        if (!permissionManager.isAllowed(required, trust)) {
            val denial = permissionManager.denialMessage(required, trust)
            addTranscript("user", finalText, parsed.type.name)
            addTranscript("jarvis", denial, "Android")
            speakIfEnabled(denial)
            audioRouter.releaseAudioFocus()
            if (!interrupted) _voiceState.value = VoiceState.IDLE
            return
        }

        addTranscript("user", finalText, parsed.type.name)

        // 9. Dispatch
        dispatch(parsed, prefs)

        audioRouter.releaseAudioFocus()
        if (!interrupted) _voiceState.value = VoiceState.IDLE
    }

    // ─── Identity check ───────────────────────────────────────────────────────

    private suspend fun performIdentityCheck(prefs: JarvisSettings) {
        // Capture a brief PCM sample while the mic is free (before STT starts)
        val pcm = voiceBuffer.capturePcm(1.5f)
        if (pcm == null) {
            Log.w(TAG, "Identity check: could not capture PCM, proceeding as UNKNOWN")
            return
        }
        val result = identityManager.identify(pcm)
        Log.d(TAG, "Identity: ${result.speakerId} confidence=${result.confidence}")
        if (result.isConfident) {
            trustManager.activateVoiceSession(result, prefs.sessionTimeoutMinutes)
        }
        // Low confidence → proceed as UNKNOWN (restricted to SAFE actions)
    }

    // ─── Voice enrolment loop ─────────────────────────────────────────────────

    private suspend fun runEnrolment(prefs: JarvisSettings) {
        val opening = enrolmentSession.start()
        speakIfEnabled(opening)

        while (enrolmentSession.isActive) {
            if (interrupted) { enrolmentSession.cancel(); break }
            audioRouter.prepareForCapture()

            when (enrolmentSession.state.value.phase) {
                EnrolmentPhase.COLLECTING_PHRASES -> {
                    _voiceState.value = VoiceState.LISTENING
                    val pcm = voiceBuffer.capturePcm(3f)
                    audioRouter.releaseAudioFocus()
                    val prompt = if (pcm != null) {
                        enrolmentSession.addPhraseSample(pcm)
                    } else {
                        "Couldn't capture audio. Please try again."
                    }
                    speakIfEnabled(prompt)
                }
                EnrolmentPhase.AWAITING_NAME, EnrolmentPhase.AWAITING_TRUST -> {
                    _voiceState.value = VoiceState.LISTENING
                    val text = captureUtterance()
                    audioRouter.releaseAudioFocus()
                    if (text == null) { enrolmentSession.cancel(); break }
                    val prompt = when (enrolmentSession.state.value.phase) {
                        EnrolmentPhase.AWAITING_NAME  -> enrolmentSession.setName(text)
                        EnrolmentPhase.AWAITING_TRUST -> enrolmentSession.setTrust(text)
                        else -> break
                    }
                    addTranscript("jarvis", prompt, "Android")
                    speakIfEnabled(prompt)
                }
                else -> break
            }
        }

        enrolmentSession.reset()
    }

    // ─── Recording commands ───────────────────────────────────────────────────

    private fun handleRecordingCommand(type: IntentType, prefs: JarvisSettings): String = when (type) {
        IntentType.RECORDING_START -> {
            if (!prefs.conversationRecordingEnabled) {
                "Conversation recording is disabled in settings."
            } else {
                recordingManager.startSession(trustManager.currentSpeakerId())
            }
        }
        IntentType.RECORDING_STOP -> {
            // stopSession is suspend but we need String; run in scope
            scope.launch { recordingManager.stopSession() }
            "Stopping recording."
        }
        else -> "Unknown recording command."
    }

    // ─── Dispatch by intent type ──────────────────────────────────────────────

    private suspend fun dispatch(parsed: ParsedIntent, prefs: JarvisSettings) {
        when (parsed.type) {
            IntentType.DEVICE_CONTROL,
            IntentType.APP_OPEN,
            IntentType.LOCATION_QUERY,
            IntentType.CAMERA_ACTION,
            IntentType.TIME_ACTION -> executeLocally(parsed, prefs)

            IntentType.SCREEN_CAPTURE -> {
                val eventId = UUID.randomUUID().toString()
                logger.logPending(parsed.rawText, parsed.toRouteDecision(), eventId, prefs)
                if (client.isConnected()) {
                    client.sendUserMessage(
                        text               = "${parsed.rawText} [screenshot pending]",
                        sessionKey         = prefs.sessionKey,
                        eventId            = eventId,
                        speaker            = trustManager.currentSpeakerId(),
                        trustLevel         = trustManager.currentTrustLevel().name,
                        identityConfidence = trustManager.currentConfidence(),
                    )
                } else {
                    logger.logOffline(parsed.rawText, parsed.toRouteDecision(), prefs)
                    speakIfEnabled("Screenshot queued — OpenClaw is offline.")
                }
            }

            IntentType.COMMUNICATION_SEND -> {
                val commRoute = commRouter.routeSend(parsed)
                if (commRoute.chosen == ai.openclaw.jarvis.data.models.RouteChoice.OPENCLAW) {
                    forwardToOpenClaw(parsed, prefs)
                } else {
                    executeLocally(parsed.copy(channel = commRoute.resolvedChannel), prefs)
                }
            }

            IntentType.COMMUNICATION_CALL  -> executeLocally(parsed, prefs)
            IntentType.OPENCLAW_REQUEST,
            IntentType.MIXED_ACTION        -> forwardToOpenClaw(parsed, prefs)

            // Handled before dispatch
            IntentType.CANCEL_STOP,
            IntentType.ENROL_VOICE,
            IntentType.RECORDING_START,
            IntentType.RECORDING_STOP -> Unit
        }
    }

    // ─── Local execution ──────────────────────────────────────────────────────

    private suspend fun executeLocally(parsed: ParsedIntent, prefs: JarvisSettings) {
        val outcome = executor.executeIntent(parsed)

        if (outcome.error == "NEEDS_CONFIRM") {
            _pendingConfirmation.value = ConfirmationRequest(
                intent  = parsed,
                summary = buildConfirmSummary(parsed),
            )
            return
        }

        val reply = outcome.spokenReply
        addTranscript("jarvis", reply, "Android")
        if (!interrupted && prefs.ttsEnabled) {
            audioRouter.prepareForPlayback()
            _voiceState.value = VoiceState.SPEAKING
            tts.speak(reply, prefs.ttsSpeed, prefs.ttsPitch)
        }
        logger.log(parsed.rawText, parsed.toRouteDecision(), outcome, prefs)
    }

    // ─── OpenClaw forwarding ──────────────────────────────────────────────────

    private suspend fun forwardToOpenClaw(parsed: ParsedIntent, prefs: JarvisSettings) {
        val eventId = UUID.randomUUID().toString()
        logger.logPending(parsed.rawText, parsed.toRouteDecision(), eventId, prefs)
        if (client.isConnected()) {
            client.sendUserMessage(
                text               = parsed.rawText,
                sessionKey         = prefs.sessionKey,
                eventId            = eventId,
                speaker            = trustManager.currentSpeakerId(),
                trustLevel         = trustManager.currentTrustLevel().name,
                identityConfidence = trustManager.currentConfidence(),
            )
        } else {
            val offline = "OpenClaw is offline. Your message has been queued."
            addTranscript("jarvis", offline, "Offline")
            if (!interrupted && prefs.ttsEnabled) {
                audioRouter.prepareForPlayback()
                _voiceState.value = VoiceState.SPEAKING
                tts.speak(offline, prefs.ttsSpeed, prefs.ttsPitch)
            }
            logger.logOffline(parsed.rawText, parsed.toRouteDecision(), prefs)
        }
    }

    // ─── Confirm/execute pending ──────────────────────────────────────────────

    private suspend fun executeConfirmed(req: ConfirmationRequest) {
        val prefs = settings.settings.first()
        val outcome = executor.executeIntent(req.intent)
        val reply = outcome.spokenReply
        addTranscript("jarvis", reply, "Android")
        if (!interrupted && prefs.ttsEnabled) {
            audioRouter.prepareForPlayback()
            _voiceState.value = VoiceState.SPEAKING
            tts.speak(reply, prefs.ttsSpeed, prefs.ttsPitch)
            _voiceState.value = VoiceState.IDLE
        }
        logger.log(req.intent.rawText, req.intent.toRouteDecision(), outcome, prefs)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Run a single STT capture and return the final transcript, or null on failure. */
    private suspend fun captureUtterance(): String? {
        var result: String? = null
        try {
            stt.listen().collect { event ->
                when (event) {
                    is SttEvent.Final -> result = event.text
                    is SttEvent.Error -> return@collect
                    else -> Unit
                }
            }
        } catch (_: CancellationException) {}
        return result?.takeIf { it.isNotBlank() }
    }

    private suspend fun speakIfEnabled(text: String) {
        val prefs = settings.settings.first()
        if (!prefs.ttsEnabled || interrupted) return
        audioRouter.prepareForPlayback()
        _voiceState.value = VoiceState.SPEAKING
        tts.speak(text, prefs.ttsSpeed, prefs.ttsPitch)
        if (_voiceState.value == VoiceState.SPEAKING) _voiceState.value = VoiceState.IDLE
    }

    private fun buildConfirmSummary(parsed: ParsedIntent): String = when {
        parsed.channel == MessageChannel.WHATSAPP ->
            "Send WhatsApp to ${parsed.contact}: \"${parsed.messageBody}\""
        parsed.type == IntentType.COMMUNICATION_SEND ->
            "Send SMS to ${parsed.contact}: \"${parsed.messageBody}\""
        parsed.type == IntentType.COMMUNICATION_CALL ->
            "Call ${parsed.contact}"
        else -> parsed.rawText
    }

    fun addTranscript(speaker: String, text: String, route: String) {
        _transcript.value = (_transcript.value + TranscriptEntry(
            speaker = speaker, text = text, route = route,
        )).takeLast(50)
    }
}

enum class SessionTrigger { PTT, WAKE_WORD, MANUAL }

data class ConfirmationRequest(
    val intent: ParsedIntent,
    val summary: String,
)
