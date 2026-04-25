package ai.openclaw.jarvis.trust

import ai.openclaw.jarvis.identity.IdentityResult
import ai.openclaw.jarvis.identity.SpeakerIdentityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SessionTrust(
    val speakerId: String,
    val trustLevel: TrustLevel,
    val verified: Boolean,
    val verificationMethod: String,  // "voice" | "passphrase" | "none"
    val confidence: Float,
    val expiresAt: Long,             // epoch millis
) {
    val isActive: Boolean get() = verified && System.currentTimeMillis() < expiresAt
}

@Singleton
class TrustManager @Inject constructor(
    private val identityManager: SpeakerIdentityManager,
    private val passphraseStore: PassphraseStore,
) {
    private val _sessionTrust = MutableStateFlow<SessionTrust?>(null)
    val sessionTrust: StateFlow<SessionTrust?> = _sessionTrust.asStateFlow()

    // ─── Session activation ───────────────────────────────────────────────────

    fun activateVoiceSession(result: IdentityResult, timeoutMinutes: Int) {
        _sessionTrust.value = SessionTrust(
            speakerId          = result.speakerId,
            trustLevel         = result.trustLevel,
            verified           = result.isConfident,
            verificationMethod = "voice",
            confidence         = result.confidence,
            expiresAt          = System.currentTimeMillis() + timeoutMinutes * 60_000L,
        )
    }

    fun elevateWithPassphrase(speakerId: String, passphrase: String, timeoutMinutes: Int): Boolean {
        if (!passphraseStore.verify(speakerId, passphrase)) return false
        val profile   = identityManager.getAllProfiles().firstOrNull { it.speakerId == speakerId }
        val trustLevel = profile?.trustLevel ?: TrustLevel.GUEST
        _sessionTrust.value = SessionTrust(
            speakerId          = speakerId,
            trustLevel         = trustLevel,
            verified           = true,
            verificationMethod = "passphrase",
            confidence         = 1.0f,
            expiresAt          = System.currentTimeMillis() + timeoutMinutes * 60_000L,
        )
        return true
    }

    /** Match passphrase against all enrolled speakers — for unknown-identity fallback. */
    fun elevateUnknownWithPassphrase(passphrase: String, timeoutMinutes: Int): String? {
        val speakerId = passphraseStore.verifyAny(passphrase) ?: return null
        elevateWithPassphrase(speakerId, passphrase, timeoutMinutes)
        return speakerId
    }

    fun resetSession() { _sessionTrust.value = null }

    // ─── Queries ──────────────────────────────────────────────────────────────

    fun hasActiveSession(): Boolean = _sessionTrust.value?.isActive == true

    fun currentTrustLevel(): TrustLevel =
        _sessionTrust.value?.takeIf { it.isActive }?.trustLevel ?: TrustLevel.UNKNOWN

    fun currentSpeakerId(): String =
        _sessionTrust.value?.takeIf { it.isActive }?.speakerId ?: "unknown"

    fun currentConfidence(): Float =
        _sessionTrust.value?.takeIf { it.isActive }?.confidence ?: 0f
}
