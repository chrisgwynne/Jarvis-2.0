package com.jarvis.githubissues.model

import com.jarvis.githubissues.settings.FailureCategory
import com.jarvis.githubissues.settings.Severity

/**
 * A failure / refusal / unsupported-capability event that the rest of the app
 * raises and that may eventually become a GitHub issue.
 *
 * Each variant carries the data needed to (a) build the issue body and
 * (b) generate a stable dedupe fingerprint.
 */
sealed class IssueEvent {
    abstract val severity: Severity
    abstract val category: FailureCategory
    abstract val shortDescription: String
    abstract val context: IssueContext

    /** Used by [com.jarvis.githubissues.dedupe.IssueDeduplicator] for fingerprinting. */
    abstract fun fingerprintParts(): List<String?>

    data class ErrorRecovery(
        val fromState: String?,
        val toState: String = "ERROR_RECOVERY",
        val triggerReason: String?,
        override val context: IssueContext,
        override val severity: Severity = Severity.ERROR,
        override val category: FailureCategory = FailureCategory.ERROR_RECOVERY,
        override val shortDescription: String = "Entered ERROR_RECOVERY${triggerReason?.let { ": $it" } ?: ""}"
    ) : IssueEvent() {
        override fun fingerprintParts() =
            listOf(category.tag, fromState, toState, triggerReason)
    }

    data class Unsupported(
        val capability: String,
        val reason: String?,
        val userPhrase: String?,
        override val context: IssueContext,
        override val severity: Severity = Severity.WARNING,
        override val category: FailureCategory = FailureCategory.UNSUPPORTED,
        override val shortDescription: String = "Unsupported: $capability"
    ) : IssueEvent() {
        override fun fingerprintParts() =
            listOf(category.tag, capability, reason)
    }

    data class CantDoThat(
        val userPhrase: String?,
        val reason: String?,
        override val context: IssueContext,
        override val severity: Severity = Severity.WARNING,
        override val category: FailureCategory = FailureCategory.CANT_DO_THAT,
        override val shortDescription: String = "Refused / can't do that${reason?.let { " — $it" } ?: ""}"
    ) : IssueEvent() {
        override fun fingerprintParts() =
            listOf(category.tag, reason, context.state.intent)
    }

    data class PermissionDenied(
        val permission: String,
        val action: String?,
        override val context: IssueContext,
        override val severity: Severity = Severity.ERROR,
        override val category: FailureCategory = FailureCategory.PERMISSION,
        override val shortDescription: String = "Permission missing: $permission${action?.let { " for $it" } ?: ""}"
    ) : IssueEvent() {
        override fun fingerprintParts() =
            listOf(category.tag, permission, action)
    }

    data class ActionFailure(
        val actionType: String,            // "sms", "whatsapp", "call", "screenshot", "location", "open_app", "contact_lookup"
        val errorCode: String?,
        val message: String?,
        override val context: IssueContext,
        override val severity: Severity = Severity.ERROR,
        override val category: FailureCategory = FailureCategory.ACTION,
        override val shortDescription: String = "$actionType failed${message?.let { ": $it" } ?: ""}"
    ) : IssueEvent() {
        override fun fingerprintParts() =
            listOf(category.tag, actionType, errorCode)
    }

    data class OpenClawFailure(
        val mode: Mode,
        val errorCode: String?,
        val message: String?,
        override val context: IssueContext,
        override val severity: Severity =
            if (mode == Mode.MALFORMED_RESPONSE || mode == Mode.CONTRACT_VIOLATION) Severity.ERROR
            else Severity.WARNING,
        override val category: FailureCategory = when (mode) {
            Mode.OFFLINE_REQUIRED -> FailureCategory.OPENCLAW_OFFLINE
            Mode.MALFORMED_RESPONSE -> FailureCategory.OPENCLAW_MALFORMED
            else -> FailureCategory.OPENCLAW
        },
        override val shortDescription: String =
            "OpenClaw ${mode.tag}${message?.let { ": $it" } ?: ""}"
    ) : IssueEvent() {
        enum class Mode(val tag: String) {
            OFFLINE_REQUIRED("offline"),
            TIMEOUT("timeout"),
            MALFORMED_RESPONSE("malformed response"),
            CONTRACT_VIOLATION("contract violation"),
            UNKNOWN_ANDROID_ACTION("unknown action")
        }

        override fun fingerprintParts() =
            listOf(category.tag, mode.tag, errorCode)
    }

    data class VoiceFailure(
        val mode: Mode,
        val detail: String?,
        override val context: IssueContext,
        override val severity: Severity = Severity.WARNING,
        override val category: FailureCategory =
            if (mode == Mode.STT_REPEATED_FAIL || mode == Mode.TTS_FAIL) FailureCategory.REPEATED_STT_TTS
            else FailureCategory.VOICE,
        override val shortDescription: String = "Voice: ${mode.tag}${detail?.let { " ($it)" } ?: ""}"
    ) : IssueEvent() {
        enum class Mode(val tag: String) {
            STT_REPEATED_FAIL("STT repeated failure"),
            TTS_FAIL("TTS failure"),
            BLUETOOTH_ROUTE_FAIL("Bluetooth route failure"),
            WAKE_FALSE_TRIGGER_PATTERN("wake-word false-trigger pattern"),
            EMPTY_TRANSCRIPT("captured but empty transcript")
        }

        override fun fingerprintParts() =
            listOf(category.tag, mode.tag)
    }

    data class RoutingFailure(
        val mode: Mode,
        val route: String?,
        val intent: String?,
        val confidence: Double?,
        override val context: IssueContext,
        override val severity: Severity = Severity.WARNING,
        override val category: FailureCategory =
            if (mode == Mode.USER_CORRECTION) FailureCategory.USER_CORRECTION
            else FailureCategory.ROUTING,
        override val shortDescription: String = "Routing: ${mode.tag}${route?.let { " → $it" } ?: ""}"
    ) : IssueEvent() {
        enum class Mode(val tag: String) {
            LOW_CONFIDENCE("low-confidence route"),
            ROUTE_CHANGED_AFTER_EXEC("route changed after execution"),
            USER_CORRECTION("user correction"),
            UNKNOWN_INTENT("unknown intent")
        }

        override fun fingerprintParts() =
            listOf(category.tag, mode.tag, route, intent)
    }

    /** Specialised correction event with extra context fields. */
    data class UserCorrection(
        val correctionPhrase: String,
        val previousCommand: String?,
        val previousRoute: String?,
        val previousResult: String?,
        override val context: IssueContext,
        override val severity: Severity = Severity.WARNING,
        override val category: FailureCategory = FailureCategory.USER_CORRECTION,
        override val shortDescription: String = "User correction: \"$correctionPhrase\""
    ) : IssueEvent() {
        override fun fingerprintParts() =
            listOf(category.tag, previousRoute, previousCommand)
    }
}
