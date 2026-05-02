package ai.openclaw.jarvis.identity

import ai.openclaw.jarvis.trust.TrustLevel
import kotlinx.serialization.Serializable

@Serializable
data class SpeakerProfile(
    val speakerId: String,
    val displayName: String,
    val trustLevel: TrustLevel,
    val enrolledAt: String,
    val embedding: List<Float>,  // normalized mean-MFCC vector; List for serialization
)

data class IdentityResult(
    val speakerId: String,
    val confidence: Float,
    val trustLevel: TrustLevel,
) {
    val isConfident: Boolean get() = confidence >= CONFIDENT_THRESHOLD

    companion object {
        const val CONFIDENT_THRESHOLD = 0.75f
        val UNKNOWN = IdentityResult("unknown", 0f, TrustLevel.UNKNOWN)
    }
}

enum class EnrolmentPhase {
    IDLE, COLLECTING_PHRASES, AWAITING_NAME, AWAITING_TRUST, COMPLETE, FAILED
}

data class EnrolmentState(
    val phase: EnrolmentPhase = EnrolmentPhase.IDLE,
    val phrasesCollected: Int = 0,
    val totalPhrases: Int = 4,
    val pendingName: String = "",
)
