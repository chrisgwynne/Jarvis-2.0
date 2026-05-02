package ai.openclaw.jarvis.githubissues.settings

import ai.openclaw.jarvis.util.LazyHydrate
import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the (non-sensitive) parts of [GitHubIssueLoggingSettings] in a
 * regular SharedPreferences file. The token itself is delegated to
 * [SecureTokenStore] so this file never holds plaintext credentials.
 *
 * Lazy hydration: the SharedPreferences file open + first read happen on
 * a background coroutine after construction. Until that completes
 * `current()` returns the safe default (`enabled = false`) — strictly
 * stricter than any persisted state, so the brief window is harmless.
 */
@Singleton
class GitHubIssueSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val tokenStore: SecureTokenStore,
) : SettingsSource {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(GitHubIssueLoggingSettings())
    val settings: StateFlow<GitHubIssueLoggingSettings> = _settings.asStateFlow()

    private val hydrate = LazyHydrate(_settings) {
        load().copy(tokenConfigured = tokenStore.hasToken())
    }

    init { hydrate.start() }

    override fun current(): GitHubIssueLoggingSettings = _settings.value

    fun update(transform: (GitHubIssueLoggingSettings) -> GitHubIssueLoggingSettings) {
        hydrate.markUpdated()
        val next = transform(_settings.value).copy(tokenConfigured = tokenStore.hasToken())
        save(next)
        _settings.value = next
    }

    fun setToken(token: String?) {
        if (token.isNullOrBlank()) tokenStore.clear() else tokenStore.saveToken(token)
        update { it }
    }

    override fun token(): String? = tokenStore.getToken()

    private fun load(): GitHubIssueLoggingSettings {
        val redaction = RedactionSettings(
            redactPhoneNumbers = prefs.getBoolean(K_R_PHONE, true),
            redactEmails = prefs.getBoolean(K_R_EMAIL, true),
            redactPreciseLocation = prefs.getBoolean(K_R_LOCATION, true),
            redactMessageBody = prefs.getBoolean(K_R_MSG_BODY, true),
            redactContactNames = prefs.getBoolean(K_R_CONTACTS, true),
            redactRestrictedTranscripts = prefs.getBoolean(K_R_TRANSCRIPTS, true),
            redactTokens = prefs.getBoolean(K_R_TOKENS, true),
            redactOpenClawKeys = prefs.getBoolean(K_R_OC_KEYS, true)
        )

        val enabledCategories = prefs.getStringSet(K_CATEGORIES, null)
            ?.mapNotNull { tag -> runCatching { FailureCategory.fromTag(tag) }.getOrNull() }
            ?.toSet()
            ?: FailureCategory.values().toSet()

        return GitHubIssueLoggingSettings(
            enabled = prefs.getBoolean(K_ENABLED, false),
            owner = prefs.getString(K_OWNER, "") ?: "",
            repo = prefs.getString(K_REPO, "") ?: "",
            tokenConfigured = tokenStore.hasToken(),
            labels = prefs.getStringSet(K_LABELS, null)?.toList()
                ?: listOf("jarvis", "auto-reported"),
            enabledCategories = enabledCategories,
            minSeverity = Severity.fromTag(
                prefs.getString(K_SEVERITY, Severity.WARNING.tag) ?: Severity.WARNING.tag
            ),
            dedupeWindow = DedupeWindow.fromLabel(
                prefs.getString(K_DEDUPE, DedupeWindow.ONE_DAY.label) ?: DedupeWindow.ONE_DAY.label
            ),
            includeDebugContext = prefs.getBoolean(K_DEBUG_CTX, true),
            redaction = redaction
        )
    }

    private fun save(s: GitHubIssueLoggingSettings) {
        prefs.edit()
            .putBoolean(K_ENABLED, s.enabled)
            .putString(K_OWNER, s.owner)
            .putString(K_REPO, s.repo)
            .putStringSet(K_LABELS, s.labels.toSet())
            .putStringSet(K_CATEGORIES, s.enabledCategories.map { it.tag }.toSet())
            .putString(K_SEVERITY, s.minSeverity.tag)
            .putString(K_DEDUPE, s.dedupeWindow.label)
            .putBoolean(K_DEBUG_CTX, s.includeDebugContext)
            .putBoolean(K_R_PHONE, s.redaction.redactPhoneNumbers)
            .putBoolean(K_R_EMAIL, s.redaction.redactEmails)
            .putBoolean(K_R_LOCATION, s.redaction.redactPreciseLocation)
            .putBoolean(K_R_MSG_BODY, s.redaction.redactMessageBody)
            .putBoolean(K_R_CONTACTS, s.redaction.redactContactNames)
            .putBoolean(K_R_TRANSCRIPTS, s.redaction.redactRestrictedTranscripts)
            .putBoolean(K_R_TOKENS, s.redaction.redactTokens)
            .putBoolean(K_R_OC_KEYS, s.redaction.redactOpenClawKeys)
            .apply()
    }

    companion object {
        private const val FILE_NAME = "jarvis_github_issue_settings"
        private const val K_ENABLED = "enabled"
        private const val K_OWNER = "owner"
        private const val K_REPO = "repo"
        private const val K_LABELS = "labels"
        private const val K_CATEGORIES = "categories"
        private const val K_SEVERITY = "min_severity"
        private const val K_DEDUPE = "dedupe_window"
        private const val K_DEBUG_CTX = "include_debug"
        private const val K_R_PHONE = "redact_phone"
        private const val K_R_EMAIL = "redact_email"
        private const val K_R_LOCATION = "redact_location"
        private const val K_R_MSG_BODY = "redact_msg_body"
        private const val K_R_CONTACTS = "redact_contacts"
        private const val K_R_TRANSCRIPTS = "redact_transcripts"
        private const val K_R_TOKENS = "redact_tokens"
        private const val K_R_OC_KEYS = "redact_oc_keys"
    }
}
