package ai.openclaw.jarvis.protocol.model

import ai.openclaw.jarvis.protocol.ProtocolVersion
import kotlinx.serialization.Serializable

/**
 * Dynamic list of OpenClaw-side skills Jarvis can offer. Pulled on connect
 * (and periodically) so "what can you do?" stays in sync with whatever
 * OpenClaw is wired up to today — Jarvis never hard-codes the set.
 *
 * Jarvis uses this for:
 *   - the conversational "what can you do?" reply
 *   - routing hints (e.g. low-confidence local route → check skills first)
 *   - the protocol debug screen
 */
@Serializable
data class OpenClawSkillManifest(
    val protocolVersion: String = ProtocolVersion.CURRENT,
    val type: String = TYPE,
    val skills: List<OpenClawSkill> = emptyList(),
) {
    companion object {
        const val TYPE = "openclaw.skill_manifest"
        /** Frame Jarvis sends to ask for the manifest. */
        const val REQUEST_TYPE = "openclaw.skill_manifest.request"
    }
}

@Serializable
data class OpenClawSkill(
    val id: String,                       // e.g. "email.send"
    val name: String,                     // human-readable
    val description: String,
    val examples: List<String> = emptyList(),
    val requiresApproval: Boolean = false,
    val available: Boolean = true,
)

/**
 * Frame Jarvis sends asking OpenClaw to publish its current manifest.
 */
@Serializable
data class OpenClawSkillManifestRequest(
    val protocolVersion: String = ProtocolVersion.CURRENT,
    val requestId: String,
    val sessionKey: String,
    val timestamp: String,
    val type: String = OpenClawSkillManifest.REQUEST_TYPE,
)
