package ai.openclaw.jarvis.voice

import android.util.Log
import ai.openclaw.jarvis.audio.AudioRouteManager
import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.executor.ActionOutcome
import ai.openclaw.jarvis.executor.AndroidActionExecutor
import ai.openclaw.jarvis.network.GatewayEvent
import ai.openclaw.jarvis.network.OpenClawClient
import ai.openclaw.jarvis.router.*
import ai.openclaw.jarvis.session.SessionEventLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central coordinator for all voice sessions.
 *
 * Both VoiceFrontend (PTT) and AlwaysListeningService (wake word) delegate
 * to this class. It owns the state machine and all session logic.
 *
 * Flow:
 *   prepareCapture (audio routing) →
 *   STT listen →
 *   IntentParser.parse() →
 *   CommunicationRouter (if comms intent) →
 *   AndroidActionExecutor or OpenClawClient →
 *   TTS speak →
 *   SessionEventLogger.log() →
 *   releaseAudio
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

    // Pending confirmation: emit non-null to ask the UI to confirm before executing
    private val _pendingConfirmation = MutableStateFlow<ConfirmationRequest?>(null)
    val pendingConfirmation: StateFlow<ConfirmationRequest?> = _pendingConfirmation.asStateFlow()

    private var activeSessionJob: Job? = null
    private var interrupted = false

    // Collect assistant replies from OpenClaw
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

    /** Start a full capture → process → respond session. */
    fun startSession(trigger: SessionTrigger = SessionTrigger.PTT) {
        if (_voiceState.value != VoiceState.IDLE) return
        if (!stt.isAvailable()) {
            scope.launch { speakIfEnabled("Speech recognition is not available.") }
            return
        }
        interrupted = false
        activeSessionJob = scope.launch { runSession(trigger) }
    }

    /** Stop capturing audio; let any in-flight TTS finish. */
    fun stopCapture() {
        stt.cancel()
    }

    /** Interrupt everything — stop TTS, cancel session, return to IDLE. */
    fun cancelAll() {
        interrupted = true
        tts.stop()
        stt.cancel()
        activeSessionJob?.cancel()
        audioRouter.releaseAudioFocus()
        _voiceState.value = VoiceState.IDLE
        _partialText.value = ""
    }

    /** Called by UI when user confirms a pending action. */
    fun confirmPending() {
        val req = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        scope.launch { executeConfirmed(req) }
    }

    /** Called by UI when user dismisses a pending confirmation. */
    fun dismissConfirmation() {
        _pendingConfirmation.value = null
        scope.launch {
            speakIfEnabled("Okay, cancelled.")
        }
    }

    // ─── Core session loop ────────────────────────────────────────────────────

    private suspend fun runSession(trigger: SessionTrigger) {
        val prefs = settings.settings.first()

        // 1. Prepare audio routing
        _voiceState.value = VoiceState.LISTENING
        audioRouter.prepareForCapture()

        // 2. STT
        var finalText = ""
        try {
            stt.listen().collect { event ->
                if (interrupted) return@collect
                when (event) {
                    is SttEvent.Partial -> _partialText.value = event.text
                    is SttEvent.Final   -> {
                        finalText = event.text
                        _partialText.value = ""
                    }
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

        // 3. Parse intent
        _voiceState.value = VoiceState.PROCESSING
        val parsed = intentParser.parse(finalText)
        _lastParsedIntent.value = parsed
        Log.d(TAG, "Parsed: ${parsed.type} confidence=${parsed.confidence}")

        // 4. CANCEL_STOP → immediate interrupt
        if (parsed.type == IntentType.CANCEL_STOP) {
            tts.stop()
            addTranscript("user", finalText, "Android")
            addTranscript("jarvis", "Okay, stopping.", "Android")
            audioRouter.releaseAudioFocus()
            _voiceState.value = VoiceState.IDLE
            return
        }

        addTranscript("user", finalText, parsed.type.name)

        // 5. Route and execute
        dispatch(parsed, prefs)

        audioRouter.releaseAudioFocus()
        if (!interrupted) _voiceState.value = VoiceState.IDLE
    }

    // ─── Dispatch by intent type ──────────────────────────────────────────────

    private suspend fun dispatch(
        parsed: ParsedIntent,
        prefs: ai.openclaw.jarvis.data.local.JarvisSettings,
    ) {
        when (parsed.type) {

            IntentType.DEVICE_CONTROL,
            IntentType.APP_OPEN,
            IntentType.LOCATION_QUERY,
            IntentType.CAMERA_ACTION,
            IntentType.TIME_ACTION -> {
                executeLocally(parsed, prefs)
            }

            IntentType.SCREEN_CAPTURE -> {
                // Mixed: UI will trigger capture; we just log pending and notify OpenClaw
                val eventId = UUID.randomUUID().toString()
                logger.logPending(parsed.rawText, parsed.toRouteDecision(), eventId, prefs)
                if (client.isConnected()) {
                    client.sendUserMessage("${parsed.rawText} [screenshot pending]", prefs.sessionKey, eventId)
                } else {
                    logger.logOffline(parsed.rawText, parsed.toRouteDecision(), prefs)
                    speakIfEnabled("Screenshot queued — OpenClaw is offline.")
                }
            }

            IntentType.COMMUNICATION_SEND -> {
                val commRoute = commRouter.routeSend(parsed)
                if (commRoute.chosen == ai.openclaw.jarvis.data.models.RouteChoice.OPENCLAW) {
                    // Email → OpenClaw
                    forwardToOpenClaw(parsed, prefs)
                } else {
                    // SMS/WhatsApp → local, with confirmation
                    executeLocally(parsed.copy(channel = commRoute.resolvedChannel), prefs)
                }
            }

            IntentType.COMMUNICATION_CALL -> {
                executeLocally(parsed, prefs)
            }

            IntentType.OPENCLAW_REQUEST -> {
                forwardToOpenClaw(parsed, prefs)
            }

            IntentType.MIXED_ACTION -> {
                forwardToOpenClaw(parsed, prefs)
            }

            IntentType.CANCEL_STOP -> { /* handled above */ }
        }
    }

    // ─── Local execution ──────────────────────────────────────────────────────

    private suspend fun executeLocally(parsed: ParsedIntent, prefs: ai.openclaw.jarvis.data.local.JarvisSettings) {
        val outcome = executor.executeIntent(parsed)

        if (outcome.error == "NEEDS_CONFIRM") {
            // Request confirmation from UI
            _pendingConfirmation.value = ConfirmationRequest(
                intent     = parsed,
                summary    = "Send ${if (parsed.channel == MessageChannel.WHATSAPP) "WhatsApp" else "SMS"} to ${parsed.contact}: \"${parsed.messageBody}\"",
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

    // ─── OpenClaw forwarding ─────────────────────────────────────────────────

    private suspend fun forwardToOpenClaw(parsed: ParsedIntent, prefs: ai.openclaw.jarvis.data.local.JarvisSettings) {
        val eventId = UUID.randomUUID().toString()
        logger.logPending(parsed.rawText, parsed.toRouteDecision(), eventId, prefs)
        if (client.isConnected()) {
            client.sendUserMessage(parsed.rawText, prefs.sessionKey, eventId)
            // Reply arrives via the init {} collector; no need to wait here
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

    private suspend fun speakIfEnabled(text: String) {
        val prefs = settings.settings.first()
        if (!prefs.ttsEnabled || interrupted) return
        audioRouter.prepareForPlayback()
        _voiceState.value = VoiceState.SPEAKING
        tts.speak(text, prefs.ttsSpeed, prefs.ttsPitch)
        if (_voiceState.value == VoiceState.SPEAKING) _voiceState.value = VoiceState.IDLE
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
