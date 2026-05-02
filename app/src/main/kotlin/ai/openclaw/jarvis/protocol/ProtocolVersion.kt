package ai.openclaw.jarvis.protocol

/**
 * Protocol version exchanged on every Jarvis ↔ OpenClaw payload.
 *
 * Bumped only when the wire schema changes in a non-backward-compatible
 * way; receivers reject anything they don't recognise rather than guess.
 */
object ProtocolVersion {
    const val CURRENT = "jarvis-openclaw/v1"

    /** Versions this build can speak. Includes [CURRENT] always. */
    val SUPPORTED: Set<String> = setOf(CURRENT)

    fun isSupported(version: String?): Boolean = version != null && version in SUPPORTED
}
