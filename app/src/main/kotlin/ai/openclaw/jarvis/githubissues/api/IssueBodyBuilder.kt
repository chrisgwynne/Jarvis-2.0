package ai.openclaw.jarvis.githubissues.api

import ai.openclaw.jarvis.githubissues.model.IssueContext
import ai.openclaw.jarvis.githubissues.model.IssueDraft
import ai.openclaw.jarvis.githubissues.model.IssueEvent
import ai.openclaw.jarvis.githubissues.redaction.Redactor
import ai.openclaw.jarvis.githubissues.settings.GitHubIssueLoggingSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Renders an [IssueEvent] (+ context) into the markdown body the spec lays
 * out: ## Summary / ## User command / ## Expected / ## Actual / ## State /
 * ## Error / ## Device / ## Capability snapshot / ## Session / ## Labels.
 *
 * All free-text fields pass through [Redactor] before being inserted, and
 * the Error section is omitted entirely when `includeDebugContext` is off.
 */
class IssueBodyBuilder(
    private val redactor: Redactor,
    private val settings: () -> GitHubIssueLoggingSettings,
    private val knownContactNames: () -> Collection<String> = { emptyList() }
) {
    fun build(event: IssueEvent, fingerprint: String, occurrenceCount: Int): IssueDraft {
        val s = settings()
        val title = IssueTitleFormatter.format(event)
        val body = renderBody(event, occurrenceCount)
        val labels = (s.labels + event.severity.tag + event.category.tag).distinct()
        return IssueDraft(
            fingerprint = fingerprint,
            title = title,
            body = body,
            labels = labels,
            severity = event.severity,
            category = event.category,
            commandId = event.context.session.commandId,
            sessionId = event.context.session.sessionId,
            occurrenceCount = occurrenceCount
        )
    }

    fun renderOccurrenceComment(event: IssueEvent, occurrenceCount: Int): String {
        val ts = formatTs(event.context.session.timestampMillis)
        return "Re-occurred (#$occurrenceCount) at $ts — " +
                "state=`${event.context.state.current ?: "?"}`, " +
                "intent=`${event.context.state.intent ?: "?"}`."
    }

    private fun renderBody(event: IssueEvent, occurrenceCount: Int): IssueBody = buildString {
        val ctx = event.context
        appendLine("## Summary")
        appendLine(redact(event.shortDescription))
        appendLine()

        appendLine("## User command")
        appendLine(redact(ctx.userCommand) ?: "_not captured_")
        appendLine()

        appendLine("## Expected behaviour")
        appendLine(redact(ctx.expectedBehaviour) ?: describeExpected(event))
        appendLine()

        appendLine("## Actual behaviour")
        appendLine(redact(ctx.actualBehaviour) ?: describeActual(event))
        appendLine()

        appendLine("## State")
        appendLine("- current state: `${ctx.state.current ?: "?"}`")
        appendLine("- previous state: `${ctx.state.previous ?: "?"}`")
        appendLine("- route: `${ctx.state.route ?: "?"}`")
        appendLine("- intent: `${ctx.state.intent ?: "?"}`")
        appendLine("- speaker trust level: `${ctx.state.speakerTrustLevel ?: "?"}`")
        appendLine("- audio route: `${ctx.state.audioRoute ?: "?"}`")
        appendLine("- OpenClaw connected: `${ctx.state.openClawConnected?.toString() ?: "?"}`")
        appendLine()

        if (settings().includeDebugContext && ctx.error != null) {
            appendLine("## Error")
            ctx.error.errorCode?.let { appendLine("- code: `$it`") }
            ctx.error.message?.let { appendLine("- message: ${redact(it)}") }
            ctx.error.cause?.let { appendLine("- cause: ${redact(it)}") }
            ctx.error.stackTrace?.let {
                appendLine()
                appendLine("```")
                appendLine(redact(it))
                appendLine("```")
            }
            appendLine()
        }

        appendLine("## Device")
        appendLine("- Android version: `${ctx.device.androidVersion ?: "?"}`")
        appendLine("- app version: `${ctx.device.appVersion ?: "?"}`")
        appendLine("- device model: `${ctx.device.deviceModel ?: "?"}`")
        appendLine("- battery state: `${ctx.device.batteryState ?: "?"}`")
        appendLine("- network status: `${ctx.device.networkStatus ?: "?"}`")
        appendLine()

        appendLine("## Capability snapshot")
        if (ctx.capability.available.isEmpty()) {
            appendLine("_no capability snapshot supplied_")
        } else {
            for ((cap, ok) in ctx.capability.available.toSortedMap()) {
                appendLine("- $cap: `${if (ok) "available" else "unavailable"}`")
            }
        }
        ctx.capability.notes?.let { notes ->
            appendLine()
            appendLine(redact(notes))
        }
        appendLine()

        appendLine("## Session")
        appendLine("- commandId: `${ctx.session.commandId ?: "?"}`")
        appendLine("- sessionId: `${ctx.session.sessionId ?: "?"}`")
        appendLine("- timestamp: `${formatTs(ctx.session.timestampMillis)}`")
        if (occurrenceCount > 1) appendLine("- occurrence: `$occurrenceCount`")
        appendLine()

        renderEventSpecific(event, this)

        val labels = (settings().labels + event.severity.tag + event.category.tag).distinct()
        appendLine("## Labels")
        appendLine(labels.joinToString(", ") { "`$it`" })
    }

    private fun renderEventSpecific(event: IssueEvent, sb: StringBuilder) {
        when (event) {
            is IssueEvent.UserCorrection -> with(sb) {
                appendLine("## User correction")
                appendLine("- correction phrase: ${redact(event.correctionPhrase)}")
                appendLine("- previous command: ${redact(event.previousCommand) ?: "_n/a_"}")
                appendLine("- previous route: `${event.previousRoute ?: "?"}`")
                appendLine("- previous result: ${redact(event.previousResult) ?: "_n/a_"}")
                appendLine()
            }
            is IssueEvent.RoutingFailure -> with(sb) {
                appendLine("## Routing")
                appendLine("- mode: `${event.mode.tag}`")
                appendLine("- route: `${event.route ?: "?"}`")
                appendLine("- intent: `${event.intent ?: "?"}`")
                event.confidence?.let { appendLine("- confidence: `${"%.2f".format(it)}`") }
                appendLine()
            }
            is IssueEvent.OpenClawFailure -> with(sb) {
                appendLine("## OpenClaw")
                appendLine("- mode: `${event.mode.tag}`")
                event.errorCode?.let { appendLine("- error code: `$it`") }
                event.message?.let { appendLine("- message: ${redact(it)}") }
                appendLine()
                // Always render the diagnostic log — this is the primary debugging signal
                // for connection failures and must not be guarded by includeDebugContext.
                event.context.error?.stackTrace?.let { diagLog ->
                    appendLine("## Diagnostic log")
                    appendLine("```")
                    appendLine(diagLog)
                    appendLine("```")
                    appendLine()
                }
            }
            else -> { /* baseline body is sufficient */ }
        }
    }

    private fun describeExpected(event: IssueEvent): String = when (event) {
        is IssueEvent.ErrorRecovery -> "Continue handling user command without entering ERROR_RECOVERY."
        is IssueEvent.Unsupported -> "Execute capability `${event.capability}` for the user."
        is IssueEvent.CantDoThat -> "Recognise and execute the user's request."
        is IssueEvent.PermissionDenied -> "Hold the `${event.permission}` permission and complete the action."
        is IssueEvent.ActionFailure -> "Complete `${event.actionType}` action successfully."
        is IssueEvent.OpenClawFailure -> "Receive a well-formed OpenClaw response within timeout."
        is IssueEvent.VoiceFailure -> "Capture / play audio successfully."
        is IssueEvent.RoutingFailure -> "Route the command to the correct intent."
        is IssueEvent.UserCorrection -> "Have already routed the command correctly."
    }

    private fun describeActual(event: IssueEvent): String = when (event) {
        is IssueEvent.ErrorRecovery -> "Transitioned into ERROR_RECOVERY${event.triggerReason?.let { " ($it)" } ?: ""}."
        is IssueEvent.Unsupported -> "Capability unavailable${event.reason?.let { " — $it" } ?: ""}."
        is IssueEvent.CantDoThat -> "Jarvis told the user it could not do that${event.reason?.let { " ($it)" } ?: ""}."
        is IssueEvent.PermissionDenied -> "`${event.permission}` permission was missing."
        is IssueEvent.ActionFailure -> "Action `${event.actionType}` failed${event.message?.let { ": $it" } ?: ""}."
        is IssueEvent.OpenClawFailure -> "OpenClaw ${event.mode.tag}${event.message?.let { ": $it" } ?: ""}."
        is IssueEvent.VoiceFailure -> "${event.mode.tag}${event.detail?.let { " — $it" } ?: ""}."
        is IssueEvent.RoutingFailure -> "${event.mode.tag} (route=${event.route ?: "?"})."
        is IssueEvent.UserCorrection -> "User said \"${event.correctionPhrase}\" after the previous response."
    }

    private fun redact(text: String?): String? =
        redactor.redact(text, knownContactNames())

    private fun formatTs(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }
}

private typealias IssueBody = String
