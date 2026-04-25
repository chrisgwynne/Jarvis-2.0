package ai.openclaw.jarvis.router

import ai.openclaw.jarvis.data.models.RouteChoice
import ai.openclaw.jarvis.data.models.RouteDecision
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides where to route a user utterance.
 *
 * Policy:
 *   ANDROID_LOCAL  → obvious phone reflexes (torch, volume, open app, timer…)
 *   OPENCLAW       → all natural conversation, business logic, memory, planning
 *   MIXED          → local capture + forward to OpenClaw (e.g. screenshot)
 *
 * When ambiguous, route to OPENCLAW. Never block an utterance locally
 * without high confidence.
 */
@Singleton
class LocalIntentRouter @Inject constructor(
    private val classifier: IntentClassifier,
) {
    fun route(text: String): RouteDecision {
        val match = classifier.classify(text)
        return RouteDecision(
            chosen     = match.route,
            intent     = match.intent,
            confidence = match.confidence,
        )
    }

    fun shouldHandleLocally(text: String): Boolean {
        val decision = route(text)
        return decision.chosen == RouteChoice.ANDROID_LOCAL && decision.confidence >= 0.85f
    }

    fun isMixed(text: String): Boolean = route(text).chosen == RouteChoice.MIXED
}
