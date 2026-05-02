package ai.openclaw.jarvis.screen.di

import ai.openclaw.jarvis.screen.ScreenshotAnalyser
import ai.openclaw.jarvis.screen.ScreenshotAutoAnalyser
import ai.openclaw.jarvis.screen.store.ScreenAwarenessSettingsRepository
import ai.openclaw.jarvis.screen.store.ScreenAwarenessSettingsSource
import ai.openclaw.jarvis.trust.TrustLevelProvider
import ai.openclaw.jarvis.trust.TrustManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the screen-awareness layer's seam interfaces to their concrete
 * implementations so the layer can be unit-tested without
 * SharedPreferences, the OpenClaw client, or TrustManager's full graph.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScreenAwarenessModule {

    @Binds @Singleton
    abstract fun bindScreenAwarenessSettingsSource(
        repo: ScreenAwarenessSettingsRepository,
    ): ScreenAwarenessSettingsSource

    @Binds @Singleton
    abstract fun bindScreenshotAnalyser(impl: ScreenshotAutoAnalyser): ScreenshotAnalyser

    @Binds @Singleton
    abstract fun bindTrustLevelProvider(impl: TrustManager): TrustLevelProvider
}
