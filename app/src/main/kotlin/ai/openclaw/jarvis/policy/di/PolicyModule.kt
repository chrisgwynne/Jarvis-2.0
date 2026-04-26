package ai.openclaw.jarvis.policy.di

import ai.openclaw.jarvis.policy.integration.OpenClawPolicyAuditLogger
import ai.openclaw.jarvis.policy.integration.PolicyAuditLogger
import ai.openclaw.jarvis.policy.store.PolicySettingsRepository
import ai.openclaw.jarvis.policy.store.PolicySettingsSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PolicyModule {

    @Binds @Singleton
    abstract fun bindPolicySettingsSource(
        repo: PolicySettingsRepository,
    ): PolicySettingsSource

    @Binds @Singleton
    abstract fun bindAuditLogger(impl: OpenClawPolicyAuditLogger): PolicyAuditLogger
}
