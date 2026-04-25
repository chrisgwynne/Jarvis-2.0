package ai.openclaw.jarvis.router

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Replaces IntentClassifier.
 *
 * Returns a rich ParsedIntent with extracted entities instead of bare string keys.
 * Default route is OPENCLAW_REQUEST — only obvious phone reflexes go local.
 */
@Singleton
class IntentParser @Inject constructor() {

    private val IC = setOf(RegexOption.IGNORE_CASE)

    // ─── Pattern rule ─────────────────────────────────────────────────────────

    private data class Rule(
        val patterns: List<Regex>,
        val type: IntentType,
        val confidence: Float,
        val extract: (MatchResult?, String) -> ParsedIntent.() -> ParsedIntent = { _, _ -> { this } },
    )

    // ─── Rules table ─────────────────────────────────────────────────────────

    private val rules: List<Rule> = listOf(

        // ── Cancel / Stop ──────────────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("""^(stop|cancel|abort|never mind|forget it|stop that|quiet|shut up|shush)$""", IC),
            ),
            type = IntentType.CANCEL_STOP,
            confidence = 0.99f,
        ),

        // ── Torch ─────────────────────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("""(turn on|switch on|enable|activate|put on).{0,10}(torch|flashlight|flash)\b""", IC),
                Regex("""\b(torch|flashlight|flash)\s+on\b""", IC),
            ),
            type = IntentType.DEVICE_CONTROL,
            confidence = 0.97f,
            extract = { _, _ -> { copy(deviceAction = DeviceControlAction.TORCH_ON) } },
        ),
        Rule(
            patterns = listOf(
                Regex("""(turn off|switch off|disable|deactivate).{0,10}(torch|flashlight|flash)\b""", IC),
                Regex("""\b(torch|flashlight|flash)\s+off\b""", IC),
            ),
            type = IntentType.DEVICE_CONTROL,
            confidence = 0.97f,
            extract = { _, _ -> { copy(deviceAction = DeviceControlAction.TORCH_OFF) } },
        ),

        // ── Volume ────────────────────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("""(volume up|turn.{0,6}(volume|sound).{0,6}(up|louder)|increase.{0,6}(volume|sound))""", IC),
            ),
            type = IntentType.DEVICE_CONTROL,
            confidence = 0.95f,
            extract = { _, _ -> { copy(deviceAction = DeviceControlAction.VOLUME_UP) } },
        ),
        Rule(
            patterns = listOf(
                Regex("""(volume down|turn.{0,6}(volume|sound).{0,6}(down|lower|quiet)|decrease.{0,6}(volume|sound))""", IC),
            ),
            type = IntentType.DEVICE_CONTROL,
            confidence = 0.95f,
            extract = { _, _ -> { copy(deviceAction = DeviceControlAction.VOLUME_DOWN) } },
        ),
        Rule(
            patterns = listOf(Regex("""\b(mute|silence|silent mode)\b""", IC)),
            type = IntentType.DEVICE_CONTROL,
            confidence = 0.93f,
            extract = { _, _ -> { copy(deviceAction = DeviceControlAction.MUTE) } },
        ),
        Rule(
            patterns = listOf(Regex("""\b(unmute|turn.{0,6}(sound|ringer|volume).{0,6}(back|on))\b""", IC)),
            type = IntentType.DEVICE_CONTROL,
            confidence = 0.93f,
            extract = { _, _ -> { copy(deviceAction = DeviceControlAction.UNMUTE) } },
        ),

        // ── Media ─────────────────────────────────────────────────────────────
        Rule(
            patterns = listOf(Regex("""\b(next (song|track)|skip (song|track)?)\b""", IC)),
            type = IntentType.DEVICE_CONTROL,
            confidence = 0.90f,
            extract = { _, _ -> { copy(deviceAction = DeviceControlAction.MEDIA_NEXT) } },
        ),
        Rule(
            patterns = listOf(Regex("""\b(previous (song|track)|go back (a track|a song))\b""", IC)),
            type = IntentType.DEVICE_CONTROL,
            confidence = 0.90f,
            extract = { _, _ -> { copy(deviceAction = DeviceControlAction.MEDIA_PREVIOUS) } },
        ),
        Rule(
            patterns = listOf(Regex("""\b(play music|resume music|resume playback|play)\b""", IC)),
            type = IntentType.DEVICE_CONTROL,
            confidence = 0.88f,
            extract = { _, _ -> { copy(deviceAction = DeviceControlAction.MEDIA_PLAY_PAUSE) } },
        ),
        Rule(
            patterns = listOf(Regex("""\bpause\b(?! for)""", IC)),
            type = IntentType.DEVICE_CONTROL,
            confidence = 0.90f,
            extract = { _, _ -> { copy(deviceAction = DeviceControlAction.MEDIA_PLAY_PAUSE) } },
        ),

        // ── Open App ──────────────────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("""(open|launch|start|switch to|go to)\s+(?!camera|timer|alarm)(\w[\w\s]{0,24})""", IC),
            ),
            type = IntentType.APP_OPEN,
            confidence = 0.88f,
            extract = { m, raw ->
                val app = m?.groupValues?.getOrNull(2)?.trim()
                    ?: raw.replace(Regex("""^(open|launch|start|switch to|go to)\s+""", IC), "").trim()
                { copy(appName = app.ifBlank { null }) }
            },
        ),

        // ── Camera ────────────────────────────────────────────────────────────
        Rule(
            patterns = listOf(Regex("""\b(take a |take |capture a )?(selfie)\b""", IC)),
            type = IntentType.CAMERA_ACTION,
            confidence = 0.93f,
            extract = { _, _ -> { copy(cameraAction = CameraSubAction.SELFIE) } },
        ),
        Rule(
            patterns = listOf(
                Regex("""\b(take a |take |capture a )?(photo|picture|pic)\b""", IC),
            ),
            type = IntentType.CAMERA_ACTION,
            confidence = 0.90f,
            extract = { _, _ -> { copy(cameraAction = CameraSubAction.PHOTO) } },
        ),

        // ── Screenshot ────────────────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("""\b(screenshot|screen shot|screen capture|screensnap)\b""", IC),
                Regex("""\blook at this\b""", IC),
                Regex("""\bwhat('s| is) on (my )?screen\b""", IC),
            ),
            type = IntentType.SCREEN_CAPTURE,
            confidence = 0.93f,
        ),

        // ── Location ──────────────────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("""\b(where am i|what('s| is) my (location|address|position)|current location|my location)\b""", IC),
            ),
            type = IntentType.LOCATION_QUERY,
            confidence = 0.92f,
        ),

        // ── Timer / Alarm ─────────────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("""\b(set|start|create|add).{0,6}(timer)\b""", IC),
                Regex("""\btimer\s+(for\s+)?\d+""", IC),
            ),
            type = IntentType.TIME_ACTION,
            confidence = 0.93f,
            extract = { _, raw ->
                val mins = extractMinutes(raw)
                { copy(timeAction = TimeSubAction.TIMER, durationMinutes = mins) }
            },
        ),
        Rule(
            patterns = listOf(
                Regex("""\b(set|create|add).{0,6}(alarm)\b""", IC),
                Regex("""\bwake me (up )?(at|in)\b""", IC),
            ),
            type = IntentType.TIME_ACTION,
            confidence = 0.92f,
            extract = { _, raw ->
                val mins = extractMinutes(raw)
                { copy(timeAction = TimeSubAction.ALARM, durationMinutes = mins) }
            },
        ),
        Rule(
            patterns = listOf(Regex("""\b(remind me|set a reminder)\b""", IC)),
            type = IntentType.TIME_ACTION,
            confidence = 0.90f,
            extract = { _, raw ->
                { copy(timeAction = TimeSubAction.REMINDER, alarmLabel = raw) }
            },
        ),

        // ── Communication: Call ───────────────────────────────────────────────
        Rule(
            patterns = listOf(
                Regex("""^(call|phone|ring|dial)\s+(.{1,40})$""", IC),
            ),
            type = IntentType.COMMUNICATION_CALL,
            confidence = 0.91f,
            extract = { m, raw ->
                val contact = m?.groupValues?.getOrNull(2)?.trim()
                    ?: raw.replace(Regex("""^(call|phone|ring|dial)\s+""", IC), "").trim()
                { copy(contact = contact.ifBlank { null }) }
            },
        ),

        // ── Communication: Send (WhatsApp explicit) ───────────────────────────
        Rule(
            patterns = listOf(
                Regex("""^(whatsapp|whats ?app|wa)\s+(.{1,40?})\s+(saying|:)?\s*(.{1,300})?$""", IC),
                Regex("""^send (a )?whatsapp (to\s+)?(.{1,40})\s*(saying|:)?\s*(.{1,300})?$""", IC),
            ),
            type = IntentType.COMMUNICATION_SEND,
            confidence = 0.92f,
            extract = { m, raw ->
                val (contact, body) = extractSendEntities(raw, "whatsapp|wa")
                { copy(contact = contact, messageBody = body, channel = MessageChannel.WHATSAPP) }
            },
        ),

        // ── Communication: Send (Email explicit) ─────────────────────────────
        Rule(
            patterns = listOf(
                Regex("""^(email|e-mail|send an? email)\s+(to\s+)?(.{1,40})\s*(saying|about|:)?\s*(.{1,300})?$""", IC),
            ),
            type = IntentType.COMMUNICATION_SEND,
            confidence = 0.92f,
            extract = { m, raw ->
                val (contact, body) = extractSendEntities(raw, "email|e-mail|send an? email")
                { copy(contact = contact, messageBody = body, channel = MessageChannel.EMAIL) }
            },
        ),

        // ── Communication: Send (SMS / generic) ───────────────────────────────
        Rule(
            patterns = listOf(
                // "text|sms|message [contact] [body]"
                Regex("""^(text|sms|send (a |an )?text( message)?|message)\s+(.{1,40?})\s+(saying|:)?\s*(.{1,300})?$""", IC),
                // "tell [contact] [body]"  – most natural form
                Regex("""^tell\s+(.{1,40?})\s+(that |to )?(.{1,300})$""", IC),
            ),
            type = IntentType.COMMUNICATION_SEND,
            confidence = 0.89f,
            extract = { m, raw ->
                val (contact, body) = extractSendEntities(raw, "text|sms|send (a |an )?text( message)?|message|tell")
                { copy(contact = contact, messageBody = body, channel = MessageChannel.BEST_AVAILABLE) }
            },
        ),
    )

    // ─── Public API ───────────────────────────────────────────────────────────

    fun parse(text: String): ParsedIntent {
        val trimmed = text.trim()
        for (rule in rules) {
            val match = rule.patterns.firstNotNullOfOrNull { it.find(trimmed) }
            if (match != null) {
                val base = ParsedIntent(
                    type       = rule.type,
                    confidence = rule.confidence,
                    rawText    = trimmed,
                )
                val transformer = rule.extract(match, trimmed)
                return base.transformer()
            }
        }
        return ParsedIntent(
            type       = IntentType.OPENCLAW_REQUEST,
            confidence = 1.0f,
            rawText    = trimmed,
        )
    }

    // ─── Entity helpers ───────────────────────────────────────────────────────

    companion object {

        private val DURATION_RE = Regex(
            """(\d+)\s*(hour|hr|h|minute|min|m|second|sec|s)\b""",
            RegexOption.IGNORE_CASE,
        )

        fun extractMinutes(text: String): Int? {
            val m = DURATION_RE.find(text) ?: return null
            val value = m.groupValues[1].toIntOrNull() ?: return null
            return when (m.groupValues[2].lowercase().first()) {
                'h'  -> value * 60
                's'  -> maxOf(1, value / 60)
                else -> value
            }
        }

        /**
         * Pull contact and body out of a send/tell/text utterance.
         * Strategy: everything from the first possible contact name up to
         * a verbal conjunction ("saying", "that", "to say", ":") is the
         * contact; everything after is the body.
         */
        fun extractSendEntities(raw: String, verbPattern: String): Pair<String?, String?> {
            val stripped = raw.replace(
                Regex("""^($verbPattern)\s+(to\s+)?""", RegexOption.IGNORE_CASE), ""
            ).trim()

            // Split on conjunction markers
            val conjRe = Regex("""\s+(saying|that|to say|:)\s+""", RegexOption.IGNORE_CASE)
            val split = conjRe.find(stripped)
            return if (split != null) {
                val contact = stripped.substring(0, split.range.first).trim().ifBlank { null }
                val body    = stripped.substring(split.range.last + 1).trim().ifBlank { null }
                contact to body
            } else {
                // No explicit separator — try comma or nothing
                // Take up to 3 words as contact, rest as body
                val words = stripped.split(Regex("""\s+"""))
                val contactWords = words.take(3).joinToString(" ").trimEnd(',')
                val bodyStart    = words.drop(3).joinToString(" ")
                contactWords.ifBlank { null } to bodyStart.ifBlank { null }
            }
        }
    }
}
