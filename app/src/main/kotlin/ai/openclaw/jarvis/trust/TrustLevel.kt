package ai.openclaw.jarvis.trust

import kotlinx.serialization.Serializable

@Serializable
enum class TrustLevel {
    OWNER,    // primary user — unrestricted
    TRUSTED,  // family / household — limited actions without confirmation
    GUEST,    // known visitor — safe actions only
    UNKNOWN,  // unidentified speaker — safe actions only, no sensitive data
}

/** Minimum trust level required to execute an action category. */
enum class ActionPermission {
    SAFE,       // torch, volume, apps, timers — all speakers
    LIMITED,    // SMS, calls, reminders — TRUSTED or OWNER
    RESTRICTED, // location, memory, emails, business data — OWNER only
}
