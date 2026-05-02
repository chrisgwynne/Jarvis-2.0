package ai.openclaw.jarvis.identity

import ai.openclaw.jarvis.trust.TrustLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State machine for the voice enrolment flow.
 *
 * SpeechSessionManager drives I/O (audio capture + STT).
 * EnrolmentSession manages state transitions and profile creation.
 *
 * Flow:
 *   IDLE → COLLECTING_PHRASES (×4) → AWAITING_NAME → AWAITING_TRUST → COMPLETE
 */
@Singleton
class EnrolmentSession @Inject constructor(
    private val identityManager: SpeakerIdentityManager,
) {
    private val _state = MutableStateFlow(EnrolmentState())
    val state: StateFlow<EnrolmentState> = _state.asStateFlow()

    private val collectedPcm = mutableListOf<ShortArray>()
    private var pendingName = ""
    private var pendingTrust = TrustLevel.TRUSTED

    private val phrases = listOf(
        "Hey Jarvis, what time is it?",
        "Turn on the torch and set a timer for five minutes.",
        "Send a message to Chris saying I'm on my way.",
        "What's the weather like today?",
    )

    val isActive: Boolean
        get() = _state.value.phase != EnrolmentPhase.IDLE
             && _state.value.phase != EnrolmentPhase.COMPLETE
             && _state.value.phase != EnrolmentPhase.FAILED

    // ─── State transitions ────────────────────────────────────────────────────

    /** Begin a new enrolment. Returns the first prompt to speak. */
    fun start(): String {
        collectedPcm.clear()
        pendingName = ""
        _state.value = EnrolmentState(phase = EnrolmentPhase.COLLECTING_PHRASES)
        return "Starting voice enrolment. I'll record ${_state.value.totalPhrases} phrases. " +
               "Say this phrase: \"${phrases[0]}\""
    }

    /** Add a PCM phrase sample. Returns the next prompt to speak. */
    fun addPhraseSample(pcm: ShortArray): String {
        if (_state.value.phase != EnrolmentPhase.COLLECTING_PHRASES) return "Enrolment not active."
        collectedPcm.add(pcm.copyOf())
        val cur = _state.value.copy(phrasesCollected = collectedPcm.size)
        return if (collectedPcm.size >= cur.totalPhrases) {
            _state.value = cur.copy(phase = EnrolmentPhase.AWAITING_NAME)
            "All phrases recorded. What name should I use for this voice profile?"
        } else {
            _state.value = cur
            "Got it. Now say: \"${phrases[collectedPcm.size]}\""
        }
    }

    /** Set the speaker name. Returns the next prompt. */
    fun setName(text: String): String {
        if (_state.value.phase != EnrolmentPhase.AWAITING_NAME) return "Unexpected state."
        pendingName = text.trim().split(" ").first().replaceFirstChar { it.uppercase() }
        _state.value = _state.value.copy(phase = EnrolmentPhase.AWAITING_TRUST, pendingName = pendingName)
        return "What trust level for $pendingName? Say owner, trusted, or guest."
    }

    /** Set trust level and finalise enrolment. Returns completion message. */
    suspend fun setTrust(text: String): String {
        if (_state.value.phase != EnrolmentPhase.AWAITING_TRUST) return "Unexpected state."
        pendingTrust = when {
            text.contains("owner",   ignoreCase = true) -> TrustLevel.OWNER
            text.contains("trusted", ignoreCase = true) -> TrustLevel.TRUSTED
            text.contains("guest",   ignoreCase = true) -> TrustLevel.GUEST
            else -> return "Please say owner, trusted, or guest."
        }
        val speakerId = pendingName.lowercase().replace(Regex("[^a-z0-9]"), "_")
        identityManager.enrolProfile(speakerId, pendingName, pendingTrust, collectedPcm)
        _state.value = _state.value.copy(phase = EnrolmentPhase.COMPLETE)
        return "$pendingName enrolled as ${pendingTrust.name.lowercase()}. " +
               "Identity is now active for this session."
    }

    fun cancel(): String {
        collectedPcm.clear()
        _state.value = EnrolmentState(phase = EnrolmentPhase.FAILED)
        return "Enrolment cancelled."
    }

    fun reset() {
        collectedPcm.clear()
        _state.value = EnrolmentState()
    }
}
