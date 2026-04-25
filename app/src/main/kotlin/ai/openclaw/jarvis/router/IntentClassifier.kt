package ai.openclaw.jarvis.router

import ai.openclaw.jarvis.data.models.RouteChoice
import ai.openclaw.jarvis.data.models.RouteDecision
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keyword-based intent classifier.
 *
 * Default route is OPENCLAW. Only patterns with high confidence
 * and obvious phone-reflex semantics are routed locally.
 */
@Singleton
class IntentClassifier @Inject constructor() {

    data class IntentMatch(
        val intent: String,
        val route: RouteChoice,
        val confidence: Float,
    )

    private data class Rule(
        val patterns: List<Regex>,
        val intent: String,
        val route: RouteChoice,
        val confidence: Float,
    )

    private val rules: List<Rule> = listOf(

        // ── Cancel / Stop ──────────────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("^(stop|cancel|abort|never mind|forget it|stop that|quiet|shut up)$", IGNORECASE),
            ),
            intent = "stop",
            route = RouteChoice.ANDROID_LOCAL,
            confidence = 0.98f,
        ),

        // ── Torch ─────────────────────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("(turn on|switch on|enable|activate).*(torch|flashlight|flash)", IGNORECASE),
                Regex("(torch|flashlight|flash).*(on)", IGNORECASE),
            ),
            intent = "torch_on",
            route = RouteChoice.ANDROID_LOCAL,
            confidence = 0.97f,
        ),
        Rule(
            patterns = listOf(
                Regex("(turn off|switch off|disable|deactivate).*(torch|flashlight|flash)", IGNORECASE),
                Regex("(torch|flashlight|flash).*(off)", IGNORECASE),
            ),
            intent = "torch_off",
            route = RouteChoice.ANDROID_LOCAL,
            confidence = 0.97f,
        ),

        // ── Volume / Media ────────────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("(volume up|turn.*(volume|sound).*(up|louder)|increase.*(volume|sound))", IGNORECASE),
            ),
            intent = "volume_up",
            route = RouteChoice.ANDROID_LOCAL,
            confidence = 0.95f,
        ),
        Rule(
            patterns = listOf(
                Regex("(volume down|turn.*(volume|sound).*(down|lower|quiet)|decrease.*(volume|sound))", IGNORECASE),
            ),
            intent = "volume_down",
            route = RouteChoice.ANDROID_LOCAL,
            confidence = 0.95f,
        ),
        Rule(
            patterns = listOf(
                Regex("(mute|silent mode|silence)", IGNORECASE),
            ),
            intent = "mute",
            route = RouteChoice.ANDROID_LOCAL,
            confidence = 0.93f,
        ),
        Rule(
            patterns = listOf(
                Regex("(unmute|turn.*(sound|volume).*(back|on)|turn.*(ringer).*(on))", IGNORECASE),
            ),
            intent = "unmute",
            route = RouteChoice.ANDROID_LOCAL,
            confidence = 0.93f,
        ),
        Rule(
            patterns = listOf(
                Regex("(pause|resume|play|next (song|track)|previous (song|track)|skip (song|track))", IGNORECASE),
            ),
            intent = "media_control",
            route = RouteChoice.ANDROID_LOCAL,
            confidence = 0.90f,
        ),

        // ── Open App ──────────────────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("(open|launch|start|switch to)\\s+(\\w+)", IGNORECASE),
            ),
            intent = "open_app",
            route = RouteChoice.ANDROID_LOCAL,
            confidence = 0.88f,
        ),

        // ── Camera / Photo ────────────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("(take a |take |capture a )?(photo|picture|selfie|pic)", IGNORECASE),
            ),
            intent = "take_photo",
            route = RouteChoice.ANDROID_LOCAL,
            confidence = 0.90f,
        ),

        // ── Screenshot ────────────────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("(take a |capture a )?(screenshot|screen shot|screen capture)", IGNORECASE),
                Regex("(look at this|what('s| is) on (my )?screen)", IGNORECASE),
            ),
            intent = "screenshot",
            route = RouteChoice.MIXED,    // capture locally, send to OpenClaw
            confidence = 0.92f,
        ),

        // ── Location ──────────────────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("(where am i|what('s| is) my (location|address|position)|current location)", IGNORECASE),
            ),
            intent = "location_query",
            route = RouteChoice.ANDROID_LOCAL,
            confidence = 0.91f,
        ),

        // ── Timer / Alarm ─────────────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("(set|start|create).*(timer|alarm)", IGNORECASE),
                Regex("(timer|alarm).*(\\d+)", IGNORECASE),
            ),
            intent = "set_timer_alarm",
            route = RouteChoice.ANDROID_LOCAL,
            confidence = 0.92f,
        ),

        // ── Call ──────────────────────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("(call|phone|ring|dial)\\s+(\\w+)", IGNORECASE),
            ),
            intent = "call",
            route = RouteChoice.ANDROID_LOCAL,
            confidence = 0.89f,
        ),

        // ── SMS / WhatsApp ────────────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("(text|send (a )?text|send (an? )?sms|message)\\s+", IGNORECASE),
                Regex("(whatsapp|send (a )?whatsapp)\\s+", IGNORECASE),
            ),
            intent = "send_message",
            route = RouteChoice.ANDROID_LOCAL,
            confidence = 0.87f,
        ),
    )

    private val IGNORECASE = setOf(RegexOption.IGNORE_CASE)

    fun classify(text: String): IntentMatch {
        val trimmed = text.trim()
        for (rule in rules) {
            if (rule.patterns.any { it.containsMatchIn(trimmed) }) {
                return IntentMatch(rule.intent, rule.route, rule.confidence)
            }
        }
        // Default: send everything else to OpenClaw
        return IntentMatch("openclaw_request", RouteChoice.OPENCLAW, 1.0f)
    }
}
