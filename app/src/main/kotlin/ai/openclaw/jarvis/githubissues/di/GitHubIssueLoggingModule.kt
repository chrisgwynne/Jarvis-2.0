package ai.openclaw.jarvis.githubissues.di

import ai.openclaw.jarvis.githubissues.api.IssueBodyBuilder
import ai.openclaw.jarvis.githubissues.dedupe.DedupeStore
import ai.openclaw.jarvis.githubissues.dedupe.IssueDeduplicator
import ai.openclaw.jarvis.githubissues.dedupe.PersistedDedupeStore
import ai.openclaw.jarvis.githubissues.integration.OpenClawSessionBridge
import ai.openclaw.jarvis.githubissues.integration.OpenClawSessionBridgeImpl
import ai.openclaw.jarvis.githubissues.queue.IssueQueue
import ai.openclaw.jarvis.githubissues.redaction.RedactionPolicy
import ai.openclaw.jarvis.githubissues.redaction.Redactor
import ai.openclaw.jarvis.githubissues.settings.GitHubIssueSettingsRepository
import ai.openclaw.jarvis.githubissues.settings.SettingsSource
import ai.openclaw.jarvis.githubissues.ui.RecentIssueLog
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * Hilt bindings for the GitHub Issue Logging subsystem. The orchestrator
 * (`GitHubIssueLogger`), API client, settings repo and secure token store
 * use `@Inject` constructors directly; this module supplies the bits Hilt
 * can't infer:
 *
 *   - the [SettingsSource] interface binding (→ [GitHubIssueSettingsRepository])
 *   - [DedupeStore] backed by a JSON file on internal storage
 *   - the persistent [IssueQueue]
 *   - [Redactor] (its policy comes from the live settings)
 *   - [IssueBodyBuilder] (depends on the live settings + redactor)
 *   - [RecentIssueLog] for the settings UI panel
 */
@Module
@InstallIn(SingletonComponent::class)
object GitHubIssueLoggingModule {

    @Provides @Singleton
    fun provideSettingsSource(
        repo: GitHubIssueSettingsRepository,
    ): SettingsSource = repo

    @Provides @Singleton
    fun provideDedupeStore(@ApplicationContext context: Context): DedupeStore =
        PersistedDedupeStore(File(context.filesDir, "jarvis_github_dedupe.json"))

    @Provides @Singleton
    fun provideDeduplicator(store: DedupeStore): IssueDeduplicator =
        IssueDeduplicator(store)

    @Provides @Singleton
    fun provideIssueQueue(@ApplicationContext context: Context): IssueQueue =
        IssueQueue(File(context.filesDir, "jarvis_github_issue_queue.json"))

    @Provides @Singleton
    fun provideRedactor(repo: GitHubIssueSettingsRepository): Redactor =
        Redactor(RedactionPolicy(repo.current().redaction))

    @Provides @Singleton
    fun provideBodyBuilder(
        redactor: Redactor,
        repo: GitHubIssueSettingsRepository,
    ): IssueBodyBuilder = IssueBodyBuilder(
        redactor = redactor,
        settings = { repo.current() },
    )

    @Provides @Singleton
    fun provideRecentIssueLog(): RecentIssueLog = RecentIssueLog()

    @Provides @Singleton
    fun provideOpenClawBridge(impl: OpenClawSessionBridgeImpl): OpenClawSessionBridge = impl
}
