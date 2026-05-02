package ai.openclaw.jarvis.githubissues.settings

/**
 * Read-only seam over the persisted settings + token. Pulled into its own
 * interface so the logger, API client, and queue worker can be unit-tested
 * without bringing in Android's Context.
 */
interface SettingsSource {
    fun current(): GitHubIssueLoggingSettings
    fun token(): String?
}
