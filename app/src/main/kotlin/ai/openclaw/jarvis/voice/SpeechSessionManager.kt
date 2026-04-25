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
import ai.openclaw.jarvis.statemachine.AssistantState
import ai.openclaw.jarvis.statemachine.AssistantStateMachine
import ai.openclaw.jarvis.statemachine.CapabilitySnapshotBuilder
import ai.openclaw.jarvis.statemachine.ErrorRecoveryManager
import ai.openclaw.jarvis.statemachine.PendingActionManager
import ai.openclaw.jarvis.statemachine.ResolveResult
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
 * Owns the full session lifecycle: routing → identity → STT → parse → permission → execute → TTS → log.
 * All state transitions are tracked by [AssistantStateMachine].
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
    private val stateMachine: AssistantStateMachine,
    private val pendingActionManager: PendingActionManager,
    private val capabilitySnapshot: CapabilitySnapshotBuilder,
    private val errorRecovery: ErrorRecoveryManager,
) {
    companion object {
        private const val TAG = "SpeechSessionManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Voice state is derived from the state machine for consistency
    val voiceState: StateFlow<VoiceState> = stateMachine.state.map { it.toVoiceState() }
        .stateIn(scope, SharingStarted.Eagerly, VoiceState.IDLE)

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _transcript  = MutableStateFlow<List<TranscriptEntry>>(emptyList())
    val transcript: StateFlow<List<TranscriptEntry>> = _transcript.asStateFlow()

    private val _lastParsedIntent = MutableStateFlow<ParsedIntent?>(null)
    val lastParsedIntent: StateFlow<ParsedIntent?> = _lastParsedIntent.asStateFlow()

    // Expose legacy confirmation for UI compat — fed from PendingActionManager
    private val _pendingConfirmation = MutableStateFlow<ConfirmationRequest?>(null)
    val pendingConfirmation: StateFlow<ConfirmationRequest?> = _pendingConfirmation.asStateFlow()

    val sessionTrust = trustManager.sessionTrust

    private var activeSessionJob: Job? = null
    private var interrupted = false

    init {
        stateMachine.transition(AssistantState.IDLE_LISTENING, "init")

        scope.launch {
            client.events.filterIsInstance<GatewayEvent.AssistantReply>().collect { event ->
                val reply = event.frame.spokenReply ?: event.frame.text ?: return@collect
                addTranscript("jarvis", reply, "OpenClaw")
                stateMachine.transition(AssistantState.SPEAKING, "openclaw reply")
                speakIfEnabled(reply)
                stateMachine.transition(AssistantState.IDLE_LISTENING, "reply spoken")
                event.frame.eventId?.let { logger.completePending(it, reply) }
            }
        }

        // Mirror pending action → UI confirmation dialog
        scope.launch {
            pendingActionManager.pending.collect { action ->
                _pendingConfirmation.value = action?.let {
                    ConfirmationRequest(intent = it.intent, summary = it.summary)
                }
            }
        }
    }

    // ─── Session control ──────────────────────────────────────────────────────

    fun startSession(trigger: SessionTrigger = SessionTrigger.PTT) {
        if (stateMachine.isActive()) return
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
        stateMachine.interrupt("user cancel")
        _partialText.value = ""
    }

    fun confirmPending() {
        val req = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        pendingActionManager.cancel()
        scope.launch { executeConfirmed(req) }
    }

    fun dismissConfirmation() {
        _pendingConfirmation.value = null
        pendingActionManager.cancel()
        scope.launch { speakIfEnabled("Okay, cancelled.") }
    }

    // ─── Core session loop ────────────────────────────────────────────────────

    private suspend fun runSession(trigger: SessionTrigger) {
        val prefs = settings.settings.first()

        stateMachine.transition(AssistantState.WAKE_DETECTED, trigger.name)
        audioRouter.prepareForCapture()

        // 1. Identity check — only when trust session has expired and profiles exist
        if (!trustManager.hasActiveSession() && identityManager.hasProfiles()) {
            stateMachine.transition(AssistantState.IDENTIFYING_SPEAKER, "identity check")
            performIdentityCheck(prefs)
        }

        // 2. STT
        stateMachine.transition(AssistantState.CAPTURING_COMMAND, "stt start")
        var finalText = ""
        try {
            stt.listen().collect { event ->
                if (interrupted) return@collect
                when (event) {
                    is SttEvent.Partial -> _partialText.value = event.text
                    is SttEvent.Final   -> { finalText = event.text; _partialText.value = "" }
                    is SttEvent.Error   -> {
                        Log.w(TAG, "STT error ${event.code}: ${event.message}")
                        val strategy = errorRecovery.strategyFor(
                            errorRecovery.errorCodeFromSttCode(event.code ?: 0)
                        )
                        stateMachine.transition(AssistantState.ERROR_RECOVERY, "stt error ${event.code}")
                        if (strategy.shouldSpeak) speakIfEnabled(strategy.userMessage)
                        stateMachine.transition(AssistantState.IDLE_LISTENING, "error handled")
                        audioRouter.releaseAudioFocus()
                        return@collect
                    }
                    else -> Unit
                }
            }
        } catch (e: CancellationException) {
            stateMachine.interrupt("cancelled")
            audioRouter.releaseAudioFocus()
            return
        }

        if (interrupted || finalText.isBlank()) {
            stateMachine.transition(AssistantState.IDLE_LISTENING, "no input")
            audioRouter.releaseAudioFocus()
            return
        }

        // 3. Check if this utterance resolves a pending action (voice yes/no)
        if (pendingActionManager.hasPending()) {
            val resolution = pendingActionManager.tryResolve(
                finalText, trustManager.currentSpeakerId()
            )
            when (resolution) {
                is ResolveResult.Confirmed -> {
                    addTranscript("user", finalText, "confirm")
                    stateMachine.transition(AssistantState.EXECUTING_ANDROID, "voice confirm")
                    executeConfirmed(ConfirmationRequest(resolution.action.intent, resolution.action.summary))
                    audioRouter.releaseAudioFocus()
                    stateMachine.transition(AssistantState.IDLE_LISTENING, "confirmed done")
                    return
                }
                is ResolveResult.Denied -> {
                    addTranscript("user", finalText, "deny")
                    speakIfEnabled("Okay, cancelled.")
                    audioRouter.releaseAudioFocus()
                    stateMachine.transition(AssistantState.IDLE_LISTENING, "denied")
                    return
                }
                is ResolveResult.Expired -> {
                    speakIfEnabled("The confirmation timed out.")
                    audioRouter.releaseAudioFocus()
                    stateMachine.transition(AssistantState.IDLE_LISTENING, "expired")
                    return
                }
                is ResolveResult.WrongSpeaker -> {
                    speakIfEnabled("This action was requested by a different speaker.")
                    audioRouter.releaseAudioFocus()
                    stateMachine.transition(AssistantState.IDLE_LISTENING, "wrong speaker")
                    return
                }
                else -> { /* NoPending / Unrecognised — fall through to normal parse */ }
            }
        }

        // 4. Parse intent
        stateMachine.transition(AssistantState.TRANSCRIBING, "parse")
        val parsed = intentParser.parse(finalText)
        _lastParsedIntent.value = parsed
        Log.d(TAG, "Parsed: ${parsed.type} confidence=${parsed.confidence} trust=${trustManager.currentTrustLevel()}")

        // 5. CANCEL_STOP — immediate
        if (parsed.type == IntentType.CANCEL_STOP) {
            tts.stop()
            addTranscript("user", finalText, "Android")
            addTranscript("jarvis", "Okay, stopping.", "Android")
            audioRouter.releaseAudioFocus()
            stateMachine.transition(AssistantState.IDLE_LISTENING, "cancel")
            return
        }

        // 6. Enrolment commands
        if (parsed.type == IntentType.ENROL_VOICE) {
            addTranscript("user", finalText, "Android")
            audioRouter.releaseAudioFocus()
            runEnrolment(prefs)
            if (!interrupted) stateMachine.transition(AssistantState.IDLE_LISTENING, "enrolment done")
            return
        }

        // 7. Recording commands
        if (parsed.type == IntentType.RECORDING_START || parsed.type == IntentType.RECORDING_STOP) {
            addTranscript("user", finalText, "Android")
            val reply = handleRecordingCommand(parsed.type, prefs)
            addTranscript("jarvis", reply, "Android")
            audioRouter.releaseAudioFocus()
            speakIfEnabled(reply)
            if (!interrupted) stateMachine.transition(AssistantState.IDLE_LISTENING, "recording cmd")
            return
        }

        // 8. Permission check
        stateMachine.transition(AssistantState.ROUTING, "permission check")
        val trust    = trustManager.currentTrustLevel()
        val required = permissionManager.requiredFor(parsed.type)
        if (!permissionManager.isAllowed(required, trust)) {
            val denial = permissionManager.denialMessage(required, trust)
            addTranscript("user", finalText, parsed.type.name)
            addTranscript("jarvis", denial, "Android")
            speakIfEnabled(denial)
            audioRouter.releaseAudioFocus()
            stateMachine.transition(AssistantState.IDLE_LISTENING, "permission denied")
            return
        }

        addTranscript("user", finalText, parsed.type.name)

        // 9. Dispatch
        dispatch(parsed, prefs)

        audioRouter.releaseAudioFocus()
        if (!interrupted && stateMachine.currentState != AssistantState.IDLE_LISTENING) {
            stateMachine.transition(AssistantState.RETURNING_TO_LISTENING, "session complete")
            stateMachine.transition(AssistantState.IDLE_LISTENING, "idle")
        }
    }

    // ─── Identity check ───────────────────────────────────────────────────────

    private suspend fun performIdentityCheck(prefs: JarvisSettings) {
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
                    stateMachine.transition(AssistantState.CAPTURING_COMMAND, "enrolment phrase")
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
                    stateMachine.transition(AssistantState.CAPTURING_COMMAND, "enrolment name/trust")
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

    private suspend fun handleRecordingCommand(type: IntentType, prefs: JarvisSettings): String = when (type) {
        IntentType.RECORDING_START -> {
            if (!prefs.conversationRecordingEnabled) {
                "Conversation recording is disabled in settings."
            } else {
                recordingManager.startSession(trustManager.currentSpeakerId())
            }
        }
        IntentType.RECORDING_STOP -> {
            recordingManager.stopSession()
            "Recording saved."
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
            IntentType.TIME_ACTION -> {
                stateMachine.transition(AssistantState.EXECUTING_ANDROID, parsed.type.name)
                executeLocally(parsed, prefs)
            }

            IntentType.SCREEN_CAPTURE -> {
                val eventId = UUID.randomUUID().toString()
                logger.logPending(parsed.rawText, parsed.toRouteDecision(), eventId, prefs)
                stateMachine.transition(AssistantState.WAITING_OPENCLAW, "screenshot")
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
                    stateMachine.transition(AssistantState.IDLE_LISTENING, "offline queued")
                }
            }

            IntentType.COMMUNICATION_SEND -> {
                val commRoute = commRouter.routeSend(parsed)
                if (commRoute.chosen == ai.openclaw.jarvis.data.models.RouteChoice.OPENCLAW) {
                    stateMachine.transition(AssistantState.WAITING_OPENCLAW, "comm send openclaw")
                    forwardToOpenClaw(parsed, prefs)
                } else {
                    stateMachine.transition(AssistantState.EXECUTING_ANDROID, "comm send local")
                    executeLocally(parsed.copy(channel = commRoute.resolvedChannel), prefs)
                }
            }

            IntentType.COMMUNICATION_CALL -> {
                stateMachine.transition(AssistantState.EXECUTING_ANDROID, "call")
                executeLocally(parsed, prefs)
            }

            IntentType.OPENCLAW_REQUEST,
            IntentType.MIXED_ACTION -> {
                stateMachine.transition(AssistantState.WAITING_OPENCLAW, parsed.type.name)
                forwardToOpenClaw(parsed, prefs)
            }

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
            stateMachine.transition(AssistantState.AWAITING_CONFIRMATION, "needs confirm")
            pendingActionManager.stage(
                intent     = parsed,
                summary    = buildConfirmSummary(parsed),
                speakerId  = trustManager.currentSpeakerId(),
                trustLevel = trustManager.currentTrustLevel(),
            )
            speakIfEnabled(buildConfirmSummary(parsed) + ". Say yes to confirm, or no to cancel.")
            return
        }

        val reply = outcome.spokenReply
        addTranscript("jarvis", reply, "Android")
        stateMachine.transition(AssistantState.SPEAKING, "local reply")
        if (!interrupted && prefs.ttsEnabled) {
            audioRouter.prepareForPlayback()
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
            stateMachine.transition(AssistantState.ERROR_RECOVERY, "gateway offline")
            val strategy = errorRecovery.strategyFor(
                ai.openclaw.jarvis.statemachine.ErrorCode.GATEWAY_OFFLINE
            )
            addTranscript("jarvis", strategy.userMessage, "Offline")
            if (!interrupted && prefs.ttsEnabled) {
                audioRouter.prepareForPlayback()
                tts.speak(strategy.userMessage, prefs.ttsSpeed, prefs.ttsPitch)
            }
            logger.logOffline(parsed.rawText, parsed.toRouteDecision(), prefs)
            stateMachine.transition(AssistantState.IDLE_LISTENING, "offline")
        }
    }

    // ─── Confirm/execute pending ──────────────────────────────────────────────

    private suspend fun executeConfirmed(req: ConfirmationRequest) {
        val prefs = settings.settings.first()
        stateMachine.transition(AssistantState.EXECUTING_ANDROID, "confirmed")
        val outcome = executor.executeIntent(req.intent)
        val reply = outcome.spokenReply
        addTranscript("jarvis", reply, "Android")
        stateMachine.transition(AssistantState.SPEAKING, "confirmed reply")
        if (!interrupted && prefs.ttsEnabled) {
            audioRouter.prepareForPlayback()
            tts.speak(reply, prefs.ttsSpeed, prefs.ttsPitch)
        }
        stateMachine.transition(AssistantState.IDLE_LISTENING, "confirmed done")
        logger.log(req.intent.rawText, req.intent.toRouteDecision(), outcome, prefs)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

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
        tts.speak(text, prefs.ttsSpeed, prefs.ttsPitch)
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

/** Map the rich AssistantState to the simplified 4-state VoiceState for UI consumption. */
private fun AssistantState.toVoiceState(): VoiceState = when (this) {
    AssistantState.IDLE_LISTENING,
    AssistantState.DISABLED,
    AssistantState.AWAITING_CONFIRMATION,
    AssistantState.RETURNING_TO_LISTENING -> VoiceState.IDLE
    AssistantState.WAKE_DETECTED,
    AssistantState.CAPTURING_COMMAND,
    AssistantState.TRANSCRIBING,
    AssistantState.IDENTIFYING_SPEAKER    -> VoiceState.LISTENING
    AssistantState.ROUTING,
    AssistantState.EXECUTING_ANDROID,
    AssistantState.WAITING_OPENCLAW,
    AssistantState.ERROR_RECOVERY         -> VoiceState.PROCESSING
    AssistantState.SPEAKING               -> VoiceState.SPEAKING
}

enum class SessionTrigger { PTT, WAKE_WORD, MANUAL }

data class ConfirmationRequest(
    val intent: ParsedIntent,
    val summary: String,
)
