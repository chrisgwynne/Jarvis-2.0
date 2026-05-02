package ai.openclaw.jarvis.trust

/**
 * Read-only seam exposing just the current speaker trust level, so
 * narrow consumers (passive-assist, awareness logger, etc.) don't have
 * to take the full [TrustManager] just to read one field.
 *
 * The default implementation forwards to [TrustManager.currentTrustLevel].
 */
interface TrustLevelProvider {
    fun current(): TrustLevel
}
