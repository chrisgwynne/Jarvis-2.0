package com.jarvis.githubissues.settings

/**
 * User-facing configuration for the GitHub Issue Logging subsystem.
 *
 * The token itself is **never** stored in this object — it lives in
 * [com.jarvis.githubissues.settings.SecureTokenStore]. The presence of a
 * stored token is reflected via [tokenConfigured] for UI status display.
 */
data class GitHubIssueLoggingSettings(
    val enabled: Boolean = false,
    val owner: String = "",
    val repo: String = "",
    val tokenConfigured: Boolean = false,
    val labels: List<String> = listOf("jarvis", "auto-reported"),
    val enabledCategories: Set<FailureCategory> = FailureCategory.values().toSet(),
    val minSeverity: Severity = Severity.WARNING,
    val dedupeWindow: DedupeWindow = DedupeWindow.ONE_DAY,
    val includeDebugContext: Boolean = true,
    val redaction: RedactionSettings = RedactionSettings()
) {
    val isFullyConfigured: Boolean
        get() = enabled && owner.isNotBlank() && repo.isNotBlank() && tokenConfigured

    fun categoryEnabled(category: FailureCategory): Boolean =
        enabledCategories.contains(category)
}

data class RedactionSettings(
    val redactPhoneNumbers: Boolean = true,
    val redactEmails: Boolean = true,
    val redactPreciseLocation: Boolean = true,
    val redactMessageBody: Boolean = true,
    val redactContactNames: Boolean = true,
    val redactRestrictedTranscripts: Boolean = true,
    val redactTokens: Boolean = true,
    val redactOpenClawKeys: Boolean = true
)
