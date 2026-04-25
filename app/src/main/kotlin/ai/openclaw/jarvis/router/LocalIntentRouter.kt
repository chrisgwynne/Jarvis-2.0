package ai.openclaw.jarvis.router

import ai.openclaw.jarvis.data.models.RouteChoice
import ai.openclaw.jarvis.data.models.RouteDecision
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides where to route a user utterance.
 *
 * Now delegates to IntentParser for rich entity extraction,
 * while keeping the same RouteDecision-returning surface for backward compat.
 *
 * Policy:
 *   ANDROID_LOCAL  → obvious phone reflexes (torch, volume, open app, timer…)
 *   OPENCLAW       → all natural conversation, business logic, memory, planning
 *   MIXED          → local capture + forward to OpenClaw (e.g. screenshot)
 *
 * When ambiguous, default to OPENCLAW. Never block locally without high confidence.
 */
@Singleton
class LocalIntentRouter @Inject constructor(
    private val parser: IntentParser,
) {
    /** Parse and convert to legacy RouteDecision for backward compat. */
    fun route(text: String): RouteDecision = parser.parse(text).toRouteDecision()

    /** Return the rich ParsedIntent with entities. */
    fun parse(text: String): ParsedIntent = parser.parse(text)

    fun shouldHandleLocally(text: String): Boolean {
        val parsed = parser.parse(text)
        return parsed.toRouteDecision().chosen == RouteChoice.ANDROID_LOCAL &&
               parsed.confidence >= 0.85f
    }

    fun isMixed(text: String): Boolean =
        parser.parse(text).toRouteDecision().chosen == RouteChoice.MIXED
}
