package ai.openclaw.jarvis.voice

import android.util.Log
import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.executor.ActionOutcome
import ai.openclaw.jarvis.executor.AndroidActionExecutor
import ai.openclaw.jarvis.network.GatewayEvent
import ai.openclaw.jarvis.network.OpenClawClient
import ai.openclaw.jarvis.router.LocalIntentRouter
import ai.openclaw.jarvis.session.SessionEventLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
}

data class TranscriptEntry(
    val id: String = UUID.randomUUID().toString(),
    val speaker: String,        // "user" | "jarvis"
    val text: String,
    val route: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Coordinates push-to-talk flow end-to-end:
 *
 *   1. STT → raw text
 *   2. LocalIntentRouter → decides route
 *   3a. ANDROID_LOCAL → AndroidActionExecutor → speak result → log to OpenClaw
 *   3b. OPENCLAW → send to Gateway → wait for assistant reply → speak → log
 *   3c. MIXED → local capture first → send to Gateway with context → speak → log
 */
@Singleton
class VoiceFrontend @Inject constructor(
    private val stt: AndroidSpeechToText,
    private val tts: AndroidTextToSpeech,
    private val router: LocalIntentRouter,
    private val executor: AndroidActionExecutor,
    private val client: OpenClawClient,
    private val logger: SessionEventLogger,
    private val settings: SettingsDataStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _voiceState  = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _transcript  = MutableStateFlow<List<TranscriptEntry>>(emptyList())
    val transcript: StateFlow<List<TranscriptEntry>> = _transcript.asStateFlow()

    private var activeListenJob: Job? = null

    // Collect assistant replies from OpenClaw
    init {
        scope.launch {
            client.events.filterIsInstance<GatewayEvent.AssistantReply>().collect { event ->
                val reply = event.frame.spokenReply ?: event.frame.text ?: return@collect
                addTranscript("jarvis", reply, "OpenClaw")
                speakIfEnabled(reply)
            }
        }
    }

    // ─── Push-to-talk ─────────────────────────────────────────────────────────

    fun startListening() {
        if (_voiceState.value != VoiceState.IDLE) return
        if (!stt.isAvailable()) {
            scope.launch { speakIfEnabled("Speech recognition is not available.") }
            return
        }
        activeListenJob = scope.launch {
            _voiceState.value = VoiceState.LISTENING
            _partialText.value = ""
            var finalText = ""

            stt.listen().collect { event ->
                when (event) {
                    is SttEvent.Partial -> _partialText.value = event.text
                    is SttEvent.Final   -> { finalText = event.text; _partialText.value = "" }
                    is SttEvent.Error   -> {
                        Log.w("VoiceFrontend", "STT error ${event.code}: ${event.message}")
                        _voiceState.value = VoiceState.IDLE
                        return@collect
                    }
                    else -> Unit
                }
            }

            if (finalText.isNotBlank()) {
                handleUtterance(finalText)
            } else {
                _voiceState.value = VoiceState.IDLE
            }
        }
    }

    fun stopListening() {
        stt.cancel()
        activeListenJob?.cancel()
        _voiceState.value = VoiceState.IDLE
    }

    fun cancelAll() {
        stopListening()
        tts.stop()
    }

    // ─── Core routing logic ───────────────────────────────────────────────────

    private suspend fun handleUtterance(text: String) {
        val prefs = settings.settings.first()
        val decision = router.route(text)
        addTranscript("user", text, decision.chosen.name)
        _voiceState.value = VoiceState.PROCESSING

        when (decision.chosen) {
            ai.openclaw.jarvis.data.models.RouteChoice.ANDROID_LOCAL -> {
                val outcome = executor.execute(decision.intent, extractParams(text, decision.intent))
                val reply = outcome.spokenReply
                addTranscript("jarvis", reply, "Android")
                if (prefs.ttsEnabled) {
                    _voiceState.value = VoiceState.SPEAKING
                    tts.speak(reply, prefs.ttsSpeed, prefs.ttsPitch)
                }
                logger.log(text, decision, outcome, prefs)
                _voiceState.value = VoiceState.IDLE
            }

            ai.openclaw.jarvis.data.models.RouteChoice.OPENCLAW -> {
                val eventId = UUID.randomUUID().toString()
                logger.logPending(text, decision, eventId, prefs)
                if (client.isConnected()) {
                    client.sendUserMessage(text, prefs.sessionKey, eventId)
                    // Reply arrives via events collector above; logger completes on reply
                } else {
                    val noGateway = "OpenClaw is offline. I've queued your message."
                    addTranscript("jarvis", noGateway, "Offline")
                    if (prefs.ttsEnabled) {
                        _voiceState.value = VoiceState.SPEAKING
                        tts.speak(noGateway, prefs.ttsSpeed, prefs.ttsPitch)
                    }
                    logger.logOffline(text, decision, prefs)
                }
                _voiceState.value = VoiceState.IDLE
            }

            ai.openclaw.jarvis.data.models.RouteChoice.MIXED -> {
                // For MIXED (e.g. screenshot): local capture handled by calling code via ViewModel.
                // VoiceFrontend just forwards the request to OpenClaw with a flag.
                val eventId = UUID.randomUUID().toString()
                logger.logPending(text, decision, eventId, prefs)
                if (client.isConnected()) {
                    client.sendUserMessage("$text [screenshot pending]", prefs.sessionKey, eventId)
                } else {
                    logger.logOffline(text, decision, prefs)
                }
                _voiceState.value = VoiceState.IDLE
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun speakIfEnabled(text: String) {
        val prefs = settings.settings.first()
        if (!prefs.ttsEnabled) return
        _voiceState.value = VoiceState.SPEAKING
        tts.speak(text, prefs.ttsSpeed, prefs.ttsPitch)
        if (_voiceState.value == VoiceState.SPEAKING) _voiceState.value = VoiceState.IDLE
    }

    private fun addTranscript(speaker: String, text: String, route: String) {
        _transcript.value = (_transcript.value + TranscriptEntry(
            speaker = speaker, text = text, route = route
        )).takeLast(50)
    }

    private fun extractParams(text: String, intent: String): Map<String, String> {
        // Lightweight param extraction for well-known intents
        return when (intent) {
            "open_app" -> {
                val m = Regex("(open|launch|start|switch to)\\s+(\\w+)", RegexOption.IGNORE_CASE).find(text)
                if (m != null) mapOf("app" to (m.groupValues.getOrNull(2) ?: "")) else emptyMap()
            }
            "set_timer_alarm" -> {
                val m = Regex("(\\d+)\\s*(minute|min|second|sec|hour|hr)", RegexOption.IGNORE_CASE).find(text)
                if (m != null) {
                    val value = m.groupValues[1].toIntOrNull() ?: 0
                    val unit  = m.groupValues[2].lowercase()
                    val minutes = when {
                        unit.startsWith("hour") || unit.startsWith("hr") -> value * 60
                        unit.startsWith("sec")                            -> maxOf(1, value / 60)
                        else                                              -> value
                    }
                    mapOf("minutes" to minutes.toString())
                } else emptyMap()
            }
            "call" -> {
                val m = Regex("(call|phone|ring|dial)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)
                if (m != null) mapOf("contact" to (m.groupValues.getOrNull(2)?.trim() ?: "")) else emptyMap()
            }
            "send_message" -> {
                val m = Regex("(text|send.*?sms|message)\\s+([\\w\\s]+?)\\s+(saying|that|:)?\\s*(.+)?", RegexOption.IGNORE_CASE).find(text)
                mapOf(
                    "contact" to (m?.groupValues?.getOrNull(2)?.trim() ?: ""),
                    "message" to (m?.groupValues?.getOrNull(4)?.trim() ?: ""),
                )
            }
            else -> emptyMap()
        }
    }
}
