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
    val verificationMethod: String,  // "voice" | "passphrase" | "auto" | "none"
    val confidence: Float,
    val expiresAt: Long,             // epoch millis; Long.MAX_VALUE = never expires
) {
    val isActive: Boolean get() = verified &&
        (expiresAt == Long.MAX_VALUE || System.currentTimeMillis() < expiresAt)
}

@Singleton
class TrustManager @Inject constructor(
    private val identityManager: SpeakerIdentityManager,
    private val passphraseStore: PassphraseStore,
) : TrustLevelProvider {
    private val _sessionTrust = MutableStateFlow<SessionTrust?>(null)
    val sessionTrust: StateFlow<SessionTrust?> = _sessionTrust.asStateFlow()

    override fun current(): TrustLevel = currentTrustLevel()

    // ─── Session activation ───────────────────────────────────────────────────

    /**
     * Automatically activate the owner's session on app start.
     * Prefers the OWNER-trust profile; falls back to the first enrolled profile.
     * Uses an infinite session (never expires) — identity is only re-checked
     * when the session is explicitly reset or a different voice is detected.
     */
    fun activateOwnerSession() {
        val profile = identityManager.getAllProfiles()
            .firstOrNull { it.trustLevel == TrustLevel.OWNER }
            ?: identityManager.getAllProfiles().firstOrNull()
            ?: return
        _sessionTrust.value = SessionTrust(
            speakerId          = profile.speakerId,
            trustLevel         = profile.trustLevel,
            verified           = true,
            verificationMethod = "auto",
            confidence         = 1.0f,
            expiresAt          = Long.MAX_VALUE,
        )
    }

    fun activateVoiceSession(result: IdentityResult, timeoutMinutes: Int) {
        _sessionTrust.value = SessionTrust(
            speakerId          = result.speakerId,
            trustLevel         = result.trustLevel,
            verified           = result.isConfident,
            verificationMethod = "voice",
            confidence         = result.confidence,
            expiresAt          = expiresAt(timeoutMinutes),
        )
    }

    fun elevateWithPassphrase(speakerId: String, passphrase: String, timeoutMinutes: Int): Boolean {
        if (!passphraseStore.verify(speakerId, passphrase)) return false
        val profile    = identityManager.getAllProfiles().firstOrNull { it.speakerId == speakerId }
        val trustLevel = profile?.trustLevel ?: TrustLevel.GUEST
        _sessionTrust.value = SessionTrust(
            speakerId          = speakerId,
            trustLevel         = trustLevel,
            verified           = true,
            verificationMethod = "passphrase",
            confidence         = 1.0f,
            expiresAt          = expiresAt(timeoutMinutes),
        )
        return true
    }

    /** Match passphrase against all enrolled speakers — for unknown-identity fallback. */
    fun elevateUnknownWithPassphrase(passphrase: String, timeoutMinutes: Int): String? {
        val speakerId = passphraseStore.verifyAny(passphrase) ?: return null
        elevateWithPassphrase(speakerId, passphrase, timeoutMinutes)
        return speakerId
    }

    // timeoutMinutes == 0 means never expire
    private fun expiresAt(timeoutMinutes: Int): Long =
        if (timeoutMinutes <= 0) Long.MAX_VALUE
        else System.currentTimeMillis() + timeoutMinutes * 60_000L

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
