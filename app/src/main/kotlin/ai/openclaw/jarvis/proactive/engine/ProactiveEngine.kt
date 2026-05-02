package ai.openclaw.jarvis.proactive.engine

import ai.openclaw.jarvis.proactive.model.ContextSnapshot
import ai.openclaw.jarvis.proactive.model.ProposedAction
import ai.openclaw.jarvis.proactive.model.Signal
import ai.openclaw.jarvis.proactive.model.SignalType
import ai.openclaw.jarvis.proactive.model.Suggestion
import ai.openclaw.jarvis.proactive.model.SuggestionFormat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stateless rule book — turns one (signal, snapshot) pair into 0..1
 * suggestions. The rules are small, on purpose:
 *   - location → message / suggest tasks
 *   - time → plan / wrap-up
 *   - behaviour → repeated whatsapp shortcut, frequent screenshot analysis
 *   - calendar → reminder, leave-now nudge
 *   - device → headphones voice mode, low-battery power save
 *
 * Suggestion ids are stable for the (signal, contextual key) pair so the
 * "don't suggest this again" memory is meaningful.
 */
@Singleton
class ProactiveEngine @Inject constructor() {

    fun suggestionFor(signal: Signal, ctx: ContextSnapshot): Suggestion? = when (signal.type) {
        SignalType.LEFT_HOME -> Suggestion(
            id = "left_home.message",
            signalType = signal.type,
            format = SuggestionFormat.VOICE,
            voicePrompt = "Do you want me to message Cath you're on your way?",
            title = "On your way?",
            body = "Send a quick heads-up that you've left.",
            proposedAction = ProposedAction(
                kind = ProposedAction.Kind.SEND_MESSAGE,
                payload = mapOf("to" to "Cath", "message" to "On my way"),
            ),
        )
        SignalType.ARRIVED_WORK -> Suggestion(
            id = "arrived_work.tasks",
            signalType = signal.type,
            format = SuggestionFormat.SILENT_CHIP,
            voicePrompt = "Want me to read out today's tasks?",
            title = "At work",
            body = "Open today's plan and tasks.",
            proposedAction = ProposedAction(kind = ProposedAction.Kind.SHOW_PLAN),
        )
        SignalType.MORNING -> Suggestion(
            id = "morning.plan",
            signalType = signal.type,
            format = SuggestionFormat.SILENT_CHIP,
            voicePrompt = "Want a quick plan for the day?",
            title = "Good morning",
            body = "Today's plan and calendar at a glance.",
            proposedAction = ProposedAction(kind = ProposedAction.Kind.SHOW_PLAN),
        )
        SignalType.EVENING -> Suggestion(
            id = "evening.wrapup",
            signalType = signal.type,
            format = SuggestionFormat.SILENT_CHIP,
            voicePrompt = "Want to wrap up the day?",
            title = "Wrap up",
            body = "Summarise progress and set tomorrow's intentions.",
            proposedAction = ProposedAction(kind = ProposedAction.Kind.WRAP_UP_DAY),
        )
        SignalType.REPEATED_COMMAND_PATTERN -> {
            val cmd = signal.payload["command"].orEmpty()
            if (cmd.contains("whatsapp", ignoreCase = true)) Suggestion(
                id = "repeated.whatsapp.shortcut",
                signalType = signal.type,
                format = SuggestionFormat.SILENT_CHIP,
                voicePrompt = "Want me to add a WhatsApp shortcut?",
                title = "Frequent WhatsApp",
                body = "Add a one-tap shortcut for this contact.",
                proposedAction = ProposedAction(kind = ProposedAction.Kind.OPEN_APP_SHORTCUT,
                    payload = mapOf("hint" to cmd)),
            ) else null
        }
        SignalType.SCREENSHOT_TAKEN -> Suggestion(
            id = "screenshot.analyse",
            signalType = signal.type,
            format = SuggestionFormat.SILENT_CHIP,
            voicePrompt = "Want me to analyse the last screenshot?",
            title = "Analyse last screenshot?",
            body = "Send to OpenClaw for a quick analysis.",
            proposedAction = ProposedAction(kind = ProposedAction.Kind.ANALYSE_LAST_SCREENSHOT),
        )
        SignalType.APP_OPENED_FREQUENTLY -> {
            val app = signal.payload["app"].orEmpty().ifBlank { return@suggestionFor null }
            Suggestion(
                id = "app.frequent.$app",
                signalType = signal.type,
                format = SuggestionFormat.SILENT_CHIP,
                voicePrompt = "Want a shortcut for $app?",
                title = "$app shortcut?",
                body = "Add a one-tap shortcut to $app.",
                proposedAction = ProposedAction(kind = ProposedAction.Kind.OPEN_APP_SHORTCUT,
                    payload = mapOf("appName" to app)),
            )
        }
        SignalType.CALENDAR_EVENT_APPROACHING -> {
            val title = signal.payload["title"].orEmpty()
            val mins = signal.payload["minutes"]?.toIntOrNull() ?: 10
            Suggestion(
                id = "calendar.approaching.${title.hashCode()}",
                signalType = signal.type,
                format = SuggestionFormat.NOTIFICATION,
                voicePrompt = "Meeting in $mins minutes${if (title.isNotBlank()) ": $title" else ""}.",
                title = "Meeting in $mins minutes",
                body = title.ifBlank { "Upcoming calendar event" },
                proposedAction = null,
            )
        }
        SignalType.HEADPHONES_CONNECTED -> Suggestion(
            id = "headphones.voice_mode",
            signalType = signal.type,
            format = SuggestionFormat.SILENT_CHIP,
            voicePrompt = "Switch to voice mode?",
            title = "Voice mode?",
            body = "Headphones connected — Jarvis can listen continuously.",
            proposedAction = ProposedAction(kind = ProposedAction.Kind.ENABLE_VOICE_MODE),
        )
        SignalType.LOW_BATTERY -> Suggestion(
            id = "battery.power_save",
            signalType = signal.type,
            format = SuggestionFormat.NOTIFICATION,
            voicePrompt = "Battery is getting low — want me to enable power saving?",
            title = "Low battery",
            body = "Enable Android battery saver to extend usage.",
            proposedAction = ProposedAction(kind = ProposedAction.Kind.ENABLE_POWER_SAVE),
        )
        SignalType.IDLE_PERIOD,
        SignalType.HEADPHONES_DISCONNECTED,
        SignalType.DRIVING_STOPPED,
        SignalType.ARRIVED_HOME,
        SignalType.DRIVING_STARTED -> null   // intentionally no automatic prompt
    }
}
