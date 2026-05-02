package ai.openclaw.jarvis.proactive.di

import ai.openclaw.jarvis.proactive.integration.OpenClawProactiveLogger
import ai.openclaw.jarvis.proactive.integration.ProactiveLogger
import ai.openclaw.jarvis.proactive.store.ProactiveSettingsRepository
import ai.openclaw.jarvis.proactive.store.ProactiveSettingsSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the proactive subsystem's seam interfaces to their concrete
 * implementations so the suggestion manager can be tested without
 * pulling SharedPreferences or the OpenClaw client into the test
 * classpath.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProactiveModule {
    @Binds @Singleton
    abstract fun bindProactiveSettingsSource(
        repo: ProactiveSettingsRepository,
    ): ProactiveSettingsSource

    @Binds @Singleton
    abstract fun bindProactiveLogger(
        impl: OpenClawProactiveLogger,
    ): ProactiveLogger
}
