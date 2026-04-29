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
    private val actionHook: ai.openclaw.jarvis.githubissues.integration.ActionExecutorHook,
    private val openClawHook: ai.openclaw.jarvis.githubissues.integration.OpenClawHook,
    private val voiceHook: ai.openclaw.jarvis.githubissues.integration.VoicePipelineHook,
    private val routingHook: ai.openclaw.jarvis.githubissues.integration.RoutingHook,
    private val correctionDetector: ai.openclaw.jarvis.githubissues.detect.UserCorrectionDetector,
    private val issueContextBuilder: ai.openclaw.jarvis.githubissues.integration.IssueContextBuilder,
    private val protocolClient: ai.openclaw.jarvis.protocol.client.OpenClawProtocolClient,
    private val typedCapabilities: ai.openclaw.jarvis.protocol.TypedCapabilitySnapshotBuilder,
    private val contractActions: ai.openclaw.jarvis.protocol.executor.ContractActionExecutor,
    private val pendingTyped: ai.openclaw.jarvis.protocol.executor.ContractPendingActions,
    private val protocolValidator: ai.openclaw.jarvis.protocol.validation.ProtocolValidator,
    private val deviceContextBuilder: ai.openclaw.jarvis.protocol.DeviceContextBuilder,
    private val awarenessManager: ai.openclaw.jarvis.awareness.CapabilityAwarenessManager,
    private val awarenessResponder: ai.openclaw.jarvis.awareness.AwarenessResponder,
    private val contextCollector: ai.openclaw.jarvis.proactive.ContextCollector,
    private val suggestionManager: ai.openclaw.jarvis.proactive.SuggestionManager,
    private val policyEngine: ai.openclaw.jarvis.policy.engine.AutonomyPolicyEngine,
    private val approvalCoordinator: ai.openclaw.jarvis.policy.ApprovalCoordinator,
    private val descriptorMapper: ai.openclaw.jarvis.policy.integration.TypedActionDescriptorMapper,
    private val auditLogger: ai.openclaw.jarvis.policy.integration.PolicyAuditLogger,
    private val streamingTts: ai.openclaw.jarvis.streaming.tts.StreamingTtsController,
    private val wakeAcknowledger: ai.openclaw.jarvis.streaming.wake.WakeAcknowledger,
    private val partialIntentDetector: ai.openclaw.jarvis.streaming.stt.PartialIntentDetector,
    private val predictiveResolver: ai.openclaw.jarvis.streaming.stt.PredictiveResolver,
    private val interruptDetector: ai.openclaw.jarvis.streaming.interrupt.InterruptPhraseDetector,
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
                val reply = event.frame.spokenReply ?: event.frame.text
                if (reply.isNullOrBlank()) {
                    openClawHook.onMalformedResponse(
                        rawDigest = "type=${event.frame.type} eventId=${event.frame.eventId}",
                        context = issueContextBuilder.build(),
                    )
                    return@collect
                }
                addTranscript("jarvis", reply, "OpenClaw")
                stateMachine.transition(AssistantState.SPEAKING, "openclaw reply")
                speakIfEnabled(reply)
                stateMachine.transition(AssistantState.IDLE_LISTENING, "reply spoken")
                event.frame.eventId?.let { logger.completePending(it, reply) }
            }
        }

        // Surface gateway-level errors (timeouts, contract violations, etc.)
        scope.launch {
            client.events.filterIsInstance<GatewayEvent.Error>().collect { event ->
                openClawHook.onContractViolation(
                    detail = event.message,
                    context = issueContextBuilder.build(),
                )
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

        // Wire the typed-protocol response / malformed / skill manifest handlers.
        registerTypedProtocolHandlers()

        // Speak proactive VOICE-format suggestions when they land,
        // unless the assistant is already speaking / awaiting confirmation.
        scope.launch {
            suggestionManager.voice.collect { suggestion ->
                val prefs = settings.settings.first()
                if (interrupted || !prefs.ttsEnabled) return@collect
                if (stateMachine.currentState in setOf(
                        AssistantState.SPEAKING,
                        AssistantState.AWAITING_CONFIRMATION,
                        AssistantState.CAPTURING_COMMAND,
                    )
                ) return@collect
                addTranscript("jarvis", suggestion.voicePrompt, "Proactive")
                audioRouter.prepareForPlayback()
                tts.speak(suggestion.voicePrompt, prefs.ttsSpeed, prefs.ttsPitch)
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
        // Sub-150ms acknowledgement — fires in the same coroutine before
        // STT spins up so the user hears something the moment the wake
        // word lands.
        wakeAcknowledger.acknowledge()
        audioRouter.prepareForCapture()

        // 1. Identity check — only when trust session has expired and profiles exist
        if (!trustManager.hasActiveSession() && identityManager.hasProfiles()) {
            stateMachine.transition(AssistantState.IDENTIFYING_SPEAKER, "identity check")
            performIdentityCheck(prefs)
        }

        // 2. STT — must run on main thread (SpeechRecognizer API requirement)
        stateMachine.transition(AssistantState.CAPTURING_COMMAND, "stt start")
        var finalText = ""
        try {
            withContext(Dispatchers.Main) {
                stt.listen().collect { event ->
                    if (interrupted) return@collect
                    when (event) {
                        is SttEvent.Partial -> {
                        _partialText.value = event.text
                        // Barge-in: if the assistant is currently speaking
                        // and the user says stop / wait / cancel, kill TTS
                        // and any in-flight execution immediately.
                        if (streamingTts.speaking.value &&
                            interruptDetector.isInterrupt(event.text)) {
                            handleBargeInInterrupt()
                        }
                        // Predictive routing: pre-resolve contacts / apps
                        // while the partial grows. Never executes anything.
                        predictiveResolver.prepare(partialIntentDetector.guess(event.text))
                    }
                    is SttEvent.Final   -> {
                        finalText = event.text
                        _partialText.value = ""
                        predictiveResolver.reset()
                    }
                    is SttEvent.Error   -> {
                        Log.w(TAG, "STT error ${event.code}: ${ai.openclaw.jarvis.util.LogRedaction.redactedMessage(event.message)}")
                        voiceHook.onSttFailure(
                            "${event.code}: ${ai.openclaw.jarvis.util.LogRedaction.redactedMessage(event.message)}",
                            issueContextBuilder.build(),
                        )
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
            if (!interrupted) {
                voiceHook.onEmptyTranscript("captured but empty", issueContextBuilder.build())
            }
            stateMachine.transition(AssistantState.IDLE_LISTENING, "no input")
            audioRouter.releaseAudioFocus()
            return
        }

        // 3a. First, see if this utterance resolves a pending typed contract
        //     action. The typed path runs its own action executor and sends
        //     the JarvisActionResult back to OpenClaw.
        if (maybeResolveTypedConfirmation(finalText)) {
            addTranscript("user", finalText, "confirm")
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

        // 3b. Awareness questions ("What can you do?", "Can you X?", etc.)
        //     are always answered locally from the live snapshot, even
        //     when OpenClaw is online — they're about Jarvis itself.
        if (handleAwarenessQuestionIfAny(finalText)) {
            audioRouter.releaseAudioFocus()
            return
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
        // Surface low-confidence route decisions to the issue logger.
        routingHook.onRouteResolved(
            route = parsed.toRouteDecision().chosen.name,
            intent = parsed.type.name,
            confidence = parsed.confidence.toDouble(),
            context = issueContextBuilder.build(
                route = parsed.toRouteDecision().chosen.name,
                intent = parsed.type.name,
                userCommand = parsed.rawText,
            ),
        )
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
        reportActionOutcome(parsed, outcome)
    }

    // ─── OpenClaw forwarding ──────────────────────────────────────────────────

    private suspend fun forwardToOpenClaw(parsed: ParsedIntent, prefs: JarvisSettings) {
        val eventId = UUID.randomUUID().toString()
        logger.logPending(parsed.rawText, parsed.toRouteDecision(), eventId, prefs)
        if (client.isConnected()) {
            // 1. Legacy path — kept so backends that still expect
            //    `user.message` and `assistant.reply` keep working.
            client.sendUserMessage(
                text               = parsed.rawText,
                sessionKey         = prefs.sessionKey,
                eventId            = eventId,
                speaker            = trustManager.currentSpeakerId(),
                trustLevel         = trustManager.currentTrustLevel().name,
                identityConfidence = trustManager.currentConfidence(),
            )
            // 2. Typed contract path — also send the strict JarvisLiveRequest
            //    so OpenClaw can reply with a strict OpenClawResponse the
            //    OpenClawProtocolClient parses and routes through the typed
            //    action executor.
            sendTypedLiveRequest(parsed, prefs, eventId)
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
            openClawHook.onOfflineWhenRequired(
                issueContextBuilder.build(
                    route = parsed.toRouteDecision().chosen.name,
                    intent = parsed.type.name,
                    userCommand = parsed.rawText,
                ),
            )
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
        reportActionOutcome(req.intent, outcome)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun captureUtterance(): String? {
        var result: String? = null
        try {
            withContext(Dispatchers.Main) {
                stt.listen().collect { event ->
                    when (event) {
                        is SttEvent.Final -> result = event.text
                        is SttEvent.Error -> return@collect
                        else -> Unit
                    }
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
        if (speaker == "user") {
            // Feed the proactive context collector so signal/suggestion
            // generation can spot patterns in recent commands.
            contextCollector.recordCommand(text)
            // User correction detection: if the user says "that's wrong" /
            // "you misunderstood" / etc. shortly after a command, file a
            // routing/user-correction issue with the previous command's
            // route + result attached.
            correctionDetector.maybeReport(
                transcript = text,
                context = issueContextBuilder.build(userCommand = text),
            )
        }
    }

    // ─── GitHub Issue Logging hooks ──────────────────────────────────────────

    /**
     * Map an [ActionOutcome] into the right [ActionExecutorHook] call.
     * Soft errors (`NEEDS_CONFIRM`, `MISSING_*`, `OPENCLAW_ROUTE`,
     * `REQUIRES_UI`) are not real failures and are skipped; hard failures
     * become per-action issues.
     */
    private fun reportActionOutcome(parsed: ParsedIntent, outcome: ActionOutcome) {
        // Feed the proactive context collector so frequent-app /
        // repeated-command signals can fire.
        contextCollector.recordAction(parsed.type.name)
        if (parsed.type == IntentType.SCREEN_CAPTURE) {
            contextCollector.recordScreenshot()
        }
        // Remember the command for the user-correction detector.
        correctionDetector.rememberLastCommand(
            command = parsed.rawText,
            route = parsed.toRouteDecision().chosen.name,
            result = outcome.spokenReply,
        )
        if (outcome.success) return
        when (outcome.error) {
            null,
            "NEEDS_CONFIRM",
            "MISSING_CONTACT", "MISSING_MESSAGE", "MISSING_APP",
            "OPENCLAW_ROUTE", "REQUIRES_UI" -> return
        }
        val ctx = issueContextBuilder.build(
            route = parsed.toRouteDecision().chosen.name,
            intent = parsed.type.name,
            userCommand = parsed.rawText,
            actualBehaviour = outcome.spokenReply,
        )
        val code = outcome.error
        when (parsed.type) {
            IntentType.COMMUNICATION_SEND ->
                if (parsed.messageBody != null) {
                    actionHook.onSmsFailure(code, outcome.spokenReply, ctx)
                } else {
                    actionHook.onWhatsAppFailure(code, outcome.spokenReply, ctx)
                }
            IntentType.COMMUNICATION_CALL ->
                actionHook.onCallFailure(code, outcome.spokenReply, ctx)
            IntentType.APP_OPEN ->
                if (code == "WHATSAPP_NOT_INSTALLED" || code?.contains("not", ignoreCase = true) == true) {
                    actionHook.onAppNotFound(parsed.appName ?: "(unknown)", ctx)
                } else {
                    actionHook.onSmsFailure(code, outcome.spokenReply, ctx)
                }
            IntentType.SCREEN_CAPTURE ->
                actionHook.onScreenshotFailure(code, outcome.spokenReply, ctx)
            IntentType.LOCATION_QUERY ->
                actionHook.onLocationFailure(code, outcome.spokenReply, ctx)
            else ->
                if (code == "UNKNOWN_INTENT") {
                    routingHook.onUnknownIntent(parsed.rawText, ctx)
                } else {
                    actionHook.onSmsFailure(code, outcome.spokenReply, ctx)
                }
        }
    }

    // ─── Awareness questions ─────────────────────────────────────────────────

    /**
     * If [transcript] is one of the recognised awareness questions
     * ("What can you do?", "Can you X?", "What permissions are missing?",
     * "Why can't you do that?"), answer it locally from
     * [awarenessManager.snapshot] and emit a `jarvis.awareness_answer`
     * session event so OpenClaw can see what we said. Returns true when
     * the utterance was handled.
     */
    private suspend fun handleAwarenessQuestionIfAny(transcript: String): Boolean {
        val q = ai.openclaw.jarvis.awareness.AwarenessQuestionDetector.detect(transcript)
            ?: return false
        val snapshot = awarenessManager.snapshot()
        val answer = awarenessResponder.answer(q, snapshot)
        addTranscript("user", transcript, "Awareness")
        addTranscript("jarvis", answer, "Awareness")
        val prefs = settings.settings.first()
        if (!interrupted && prefs.ttsEnabled) {
            stateMachine.transition(AssistantState.SPEAKING, "awareness")
            audioRouter.prepareForPlayback()
            tts.speak(answer, prefs.ttsSpeed, prefs.ttsPitch)
        }
        // Log the Q + A to OpenClaw via a session event so the backend
        // can observe what Jarvis is telling the user about itself.
        runCatching {
            protocolClient.sendSessionEvent(
                ai.openclaw.jarvis.protocol.model.JarvisSessionEvent(
                    requestId = java.util.UUID.randomUUID().toString(),
                    sessionKey = prefs.sessionKey,
                    timestamp = ai.openclaw.jarvis.protocol.util.IsoTimestamp.now(),
                    name = "jarvis.awareness_answer",
                    body = kotlinx.serialization.json.buildJsonObject {
                        put("question", kotlinx.serialization.json.JsonPrimitive(transcript))
                        put("answer", kotlinx.serialization.json.JsonPrimitive(answer))
                        put("trustLevel", kotlinx.serialization.json.JsonPrimitive(snapshot.trustLevel))
                        put("openClawConnected", kotlinx.serialization.json.JsonPrimitive(snapshot.openClawConnected))
                    },
                )
            )
        }
        stateMachine.transition(AssistantState.IDLE_LISTENING, "awareness done")
        return true
    }

    // ─── Typed protocol path ─────────────────────────────────────────────────

    /**
     * Build and send a strict [ai.openclaw.jarvis.protocol.model.JarvisLiveRequest]
     * for the same utterance. Runs alongside the legacy `user.message`
     * frame; OpenClaw replies on the typed channel via
     * `OpenClawResponse` (parsed by [protocolClient]).
     */
    private suspend fun sendTypedLiveRequest(
        parsed: ParsedIntent,
        prefs: JarvisSettings,
        requestId: String,
    ) {
        val now = ai.openclaw.jarvis.protocol.util.IsoTimestamp.now()
        val request = ai.openclaw.jarvis.protocol.model.JarvisLiveRequest(
            requestId = requestId,
            sessionKey = prefs.sessionKey,
            timestamp = now,
            speaker = ai.openclaw.jarvis.protocol.model.SpeakerInfo(
                id = trustManager.currentSpeakerId(),
                trustLevel = trustManager.currentTrustLevel().name,
                confidence = trustManager.currentConfidence(),
            ),
            input = ai.openclaw.jarvis.protocol.model.InputInfo(
                mode = "voice",
                text = parsed.rawText,
                audioSource = audioRouter.activeDevice.name.lowercase(),
            ),
            route = ai.openclaw.jarvis.protocol.model.RouteInfo(
                chosen = parsed.toRouteDecision().chosen.name,
                localIntent = parsed.type.name,
                confidence = parsed.confidence,
            ),
            deviceContext = deviceContextBuilder.build(),
            capabilities = typedCapabilities.build(),
        )
        protocolClient.sendLiveRequest(request)
    }

    /**
     * Subscribe to typed [OpenClawProtocolClient.responses] (registered from
     * [registerTypedProtocolHandlers], called once from `init`). For each
     * typed response:
     *   - speak / display reply.text per the directive
     *   - dispatch each action: confirm-first if the action requires it,
     *     otherwise execute immediately and post the [JarvisActionResult].
     *
     * Malformed responses are routed via [openClawHook.onMalformedResponse]
     * which feeds the GitHub Issue Logger and trips ERROR_RECOVERY.
     */
    fun registerTypedProtocolHandlers() {
        scope.launch {
            protocolClient.responses.collect { response -> handleTypedResponse(response) }
        }
        scope.launch {
            protocolClient.malformed.collect { err ->
                openClawHook.onMalformedResponse(
                    rawDigest = err.message,
                    context = issueContextBuilder.build(),
                )
                stateMachine.transition(AssistantState.ERROR_RECOVERY, "malformed response")
                stateMachine.transition(AssistantState.IDLE_LISTENING, "recovered")
            }
        }
        // Streaming OpenClawResponseChunk path — start the streaming TTS
        // on the first chunk, feed deltas as they arrive, finish on
        // chunk.final. Errors / actions on the final chunk get folded
        // into the existing typed-response handler via a synthesised
        // OpenClawResponse so policy / contract executor still apply.
        scope.launch {
            var streamingRequestId: String? = null
            val agg = StringBuilder()
            protocolClient.chunks.collect { chunk ->
                val prefs = settings.settings.first()
                if (streamingRequestId != chunk.requestId) {
                    streamingRequestId = chunk.requestId
                    agg.setLength(0)
                    streamingTts.begin(prefs.ttsSpeed, prefs.ttsPitch)
                    if (!interrupted && prefs.ttsEnabled) {
                        audioRouter.prepareForPlayback()
                        stateMachine.transition(AssistantState.SPEAKING, "openclaw stream")
                    }
                }
                if (chunk.replyDelta.isNotEmpty()) {
                    agg.append(chunk.replyDelta)
                    if (prefs.ttsEnabled && !interrupted) streamingTts.feedDelta(chunk.replyDelta)
                }
                if (chunk.final) {
                    streamingTts.finish()
                    if (agg.isNotEmpty()) addTranscript("jarvis", agg.toString(), "OpenClaw")
                    streamingRequestId = null
                    // Fold into the existing typed-response path so the
                    // ResponseStatus + actions[] still flow through policy
                    // + the contract executor.
                    val finalResponse = ai.openclaw.jarvis.protocol.model.OpenClawResponse(
                        requestId = chunk.requestId,
                        sessionId = chunk.sessionId,
                        timestamp = chunk.timestamp,
                        reply = ai.openclaw.jarvis.protocol.model.ReplyDirective(
                            text = agg.toString(),
                            speak = false,   // already streamed
                            display = false, // already added to transcript
                        ),
                        actions = chunk.actions,
                        requiresConfirmation = false,
                        memoryCandidate = false,
                        status = chunk.status,
                        error = chunk.error,
                    )
                    handleTypedResponse(finalResponse)
                    stateMachine.transition(AssistantState.IDLE_LISTENING, "stream done")
                }
            }
        }
        // Pull a fresh skill manifest on every gateway reconnect.
        scope.launch {
            client.gatewayState.collect { gs ->
                if (gs == ai.openclaw.jarvis.data.models.GatewayState.CONNECTED) {
                    val prefs = settings.settings.first()
                    protocolClient.requestSkillManifest(
                        requestId = java.util.UUID.randomUUID().toString(),
                        sessionKey = prefs.sessionKey,
                        timestamp = ai.openclaw.jarvis.protocol.util.IsoTimestamp.now(),
                    )
                }
            }
        }
    }

    /**
     * Stop in-flight TTS + return to listening. Used by the partial-STT
     * barge-in detector when the user says stop / wait / cancel while
     * the assistant is mid-speech.
     */
    private fun handleBargeInInterrupt() {
        streamingTts.interrupt()
        // tts.stop() also covers the legacy non-streaming path so a single
        // interrupt covers both.
        runCatching { tts.stop() }
        stateMachine.interrupt("user barge-in")
        addTranscript("jarvis", "(stopped)", "Interrupt")
    }

    private suspend fun handleTypedResponse(
        response: ai.openclaw.jarvis.protocol.model.OpenClawResponse,
    ) {
        val prefs = settings.settings.first()

        if (response.status == ai.openclaw.jarvis.protocol.model.ResponseStatus.error) {
            openClawHook.onContractViolation(
                detail = response.error?.let { "${it.code}: ${it.message}" } ?: "OpenClaw error",
                context = issueContextBuilder.build(),
            )
            stateMachine.transition(AssistantState.ERROR_RECOVERY, "openclaw error")
            stateMachine.transition(AssistantState.IDLE_LISTENING, "recovered")
            return
        }

        // Speak / display the textual part if directed.
        val text = response.reply.text
        if (text.isNotBlank()) {
            if (response.reply.display) addTranscript("jarvis", text, "OpenClaw")
            if (response.reply.speak && !interrupted && prefs.ttsEnabled) {
                stateMachine.transition(AssistantState.SPEAKING, "openclaw reply")
                audioRouter.prepareForPlayback()
                tts.speak(text, prefs.ttsSpeed, prefs.ttsPitch)
                stateMachine.transition(AssistantState.IDLE_LISTENING, "reply spoken")
            }
        }

        // Then process each action through the typed contract path.
        for (action in response.actions) {
            val decoded = protocolValidator.decodeAction(action)
            when (decoded) {
                is ai.openclaw.jarvis.protocol.validation.ProtocolResult.Rejected -> {
                    val result = ai.openclaw.jarvis.protocol.model.JarvisActionResult(
                        requestId = response.requestId,
                        sessionKey = prefs.sessionKey,
                        actionId = action.actionId,
                        timestamp = ai.openclaw.jarvis.protocol.util.IsoTimestamp.now(),
                        status = when (decoded.error.code) {
                            ai.openclaw.jarvis.protocol.validation.ProtocolError.Code.MISSING_FIELDS ->
                                ai.openclaw.jarvis.protocol.model.ActionResultStatus.failed
                            ai.openclaw.jarvis.protocol.validation.ProtocolError.Code.UNKNOWN_ACTION_TYPE ->
                                ai.openclaw.jarvis.protocol.model.ActionResultStatus.unsupported
                            else -> ai.openclaw.jarvis.protocol.model.ActionResultStatus.failed
                        },
                        error = ai.openclaw.jarvis.protocol.model.ActionResultError(
                            code = decoded.error.code.name,
                            message = decoded.error.message,
                        ),
                    )
                    protocolClient.sendActionResult(result)
                    openClawHook.onContractViolation(
                        detail = "Action ${action.type}: ${decoded.error.message}",
                        context = issueContextBuilder.build(),
                    )
                }
                is ai.openclaw.jarvis.protocol.validation.ProtocolResult.Ok -> {
                    handleDecodedAction(decoded.value, response.requestId, prefs.sessionKey)
                }
            }
        }
    }

    /**
     * Run a single decoded typed action through the autonomy policy
     * engine, then act on the resulting [PolicyDecision]:
     *
     *  - BLOCKED                     send a cancelled JarvisActionResult
     *                                ("policy blocked: <reasons>"), do NOT execute
     *  - PREPARE                     stage as PendingApproval (already done by
     *                                the engine), speak the prep prompt,
     *                                send a needs-confirmation result back
     *  - EXECUTE_WITH_CONFIRMATION   stage + speak "Do you want me to …?",
     *                                wait for the existing yes/no path
     *  - EXECUTE_TRUSTED             execute now, post the result
     *
     * In every case, the [auditLogger] gets a `jarvis.policy_decision`
     * event so OpenClaw sees what the engine ruled and why.
     */
    private suspend fun handleDecodedAction(
        decoded: ai.openclaw.jarvis.protocol.validation.DecodedAction,
        requestId: String,
        sessionKey: String,
    ) {
        val descriptor = descriptorMapper.toDescriptor(decoded)
        val input = ai.openclaw.jarvis.policy.model.PolicyInput(
            speakerTrust = trustManager.currentTrustLevel(),
            hourOfDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
            confidence = trustManager.currentConfidence(),
            openClawConnected = client.isConnected(),
            openClawSuggestedLevel = if (decoded.action.requiresConfirmation)
                ai.openclaw.jarvis.policy.model.AutonomyLevel.EXECUTE_WITH_CONFIRMATION else null,
        )
        val decision = policyEngine.decide(descriptor, input)
        auditLogger.logDecision(decision)

        when (decision.level) {
            ai.openclaw.jarvis.policy.model.AutonomyLevel.OBSERVE,
            ai.openclaw.jarvis.policy.model.AutonomyLevel.SUGGEST -> {
                // Engine says don't act — surface a chip via the proactive
                // pipeline instead of executing.
                speakIfEnabled("Noted — I won't act on that without your say-so.")
                protocolClient.sendActionResult(buildCancelled(decoded, requestId, sessionKey,
                    code = "POLICY_OBSERVE", message = decision.reasons.joinToString("; ")))
            }
            ai.openclaw.jarvis.policy.model.AutonomyLevel.BLOCKED -> {
                speakIfEnabled("That action is blocked by your settings.")
                protocolClient.sendActionResult(buildCancelled(decoded, requestId, sessionKey,
                    code = "POLICY_BLOCKED", message = decision.reasons.joinToString("; ")))
            }
            ai.openclaw.jarvis.policy.model.AutonomyLevel.PREPARE,
            ai.openclaw.jarvis.policy.model.AutonomyLevel.EXECUTE_WITH_CONFIRMATION -> {
                val summary = ai.openclaw.jarvis.protocol.executor.confirmSummary(decoded.action)
                // Stage in the persistent approval store so the in-app
                // approvals screen + notification surfaces show this too.
                approvalCoordinator.stage(
                    descriptor = decision.descriptor,
                    level = decision.level,
                    expiresAtMillis = decision.expiresAtMillis ?: (System.currentTimeMillis() + 5 * 60 * 1000L),
                    originRequestId = requestId,
                    originSessionKey = sessionKey,
                ) { _ ->
                    val result = contractActions.execute(decoded, requestId, sessionKey)
                    protocolClient.sendActionResult(result)
                }
                // Also stage in the legacy typed-pending store so the
                // existing voice yes/no path keeps working unchanged.
                pendingTyped.stage(
                    decoded = decoded,
                    requestId = requestId,
                    sessionKey = sessionKey,
                    summary = summary,
                )
                stateMachine.transition(AssistantState.AWAITING_CONFIRMATION, "policy confirm")
                speakIfEnabled(summary)
            }
            ai.openclaw.jarvis.policy.model.AutonomyLevel.EXECUTE_TRUSTED -> {
                val result = contractActions.execute(decoded, requestId, sessionKey)
                protocolClient.sendActionResult(result)
            }
        }
    }

    private fun buildCancelled(
        decoded: ai.openclaw.jarvis.protocol.validation.DecodedAction,
        requestId: String,
        sessionKey: String,
        code: String,
        message: String,
    ) = ai.openclaw.jarvis.protocol.model.JarvisActionResult(
        requestId = requestId,
        sessionKey = sessionKey,
        actionId = decoded.action.actionId,
        timestamp = ai.openclaw.jarvis.protocol.util.IsoTimestamp.now(),
        status = ai.openclaw.jarvis.protocol.model.ActionResultStatus.cancelled,
        error = ai.openclaw.jarvis.protocol.model.ActionResultError(code = code, message = message),
    )

    /**
     * Resolve any pending typed action against the user's confirmation
     * utterance. Called from [runSession] alongside the legacy
     * [pendingActionManager] resolution. Returns true when the utterance
     * matched a typed pending action (so the caller skips the legacy
     * resolver).
     */
    suspend fun maybeResolveTypedConfirmation(utterance: String): Boolean {
        val pending = pendingTyped.current() ?: return false
        if (pending.isExpired) {
            pendingTyped.clear()
            sendCancelledResult(pending, code = "EXPIRED", message = "Confirmation expired")
            return true
        }
        val n = utterance.trim().lowercase()
        val confirm = listOf("yes", "yeah", "yep", "yup", "sure", "ok", "okay", "do it", "confirm", "go ahead")
        val deny = listOf("no", "nope", "cancel", "stop", "don't", "abort", "never mind", "nevermind")
        return when {
            confirm.any { n.contains(it) } -> {
                pendingTyped.consume()
                val result = contractActions.execute(
                    decoded = pending.decoded,
                    requestId = pending.requestId,
                    sessionKey = pending.sessionKey,
                )
                protocolClient.sendActionResult(result)
                stateMachine.transition(AssistantState.IDLE_LISTENING, "typed confirmed")
                true
            }
            deny.any { n.contains(it) } -> {
                pendingTyped.consume()
                sendCancelledResult(pending, code = "USER_CANCELLED", message = "User declined")
                stateMachine.transition(AssistantState.IDLE_LISTENING, "typed denied")
                true
            }
            else -> false
        }
    }

    private suspend fun sendCancelledResult(
        pending: ai.openclaw.jarvis.protocol.executor.ContractPendingActions.PendingTyped,
        code: String,
        message: String,
    ) {
        protocolClient.sendActionResult(
            ai.openclaw.jarvis.protocol.model.JarvisActionResult(
                requestId = pending.requestId,
                sessionKey = pending.sessionKey,
                actionId = pending.decoded.action.actionId,
                timestamp = ai.openclaw.jarvis.protocol.util.IsoTimestamp.now(),
                status = ai.openclaw.jarvis.protocol.model.ActionResultStatus.cancelled,
                error = ai.openclaw.jarvis.protocol.model.ActionResultError(code = code, message = message),
            )
        )
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
