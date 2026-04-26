package ai.openclaw.jarvis.screen.di

import ai.openclaw.jarvis.screen.store.ScreenAwarenessSettingsRepository
import ai.openclaw.jarvis.screen.store.ScreenAwarenessSettingsSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds [ScreenAwarenessSettingsSource] to its concrete repository so
 * the rest of the screen-awareness layer can be unit-tested without
 * pulling SharedPreferences in.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScreenAwarenessModule {

    @Binds @Singleton
    abstract fun bindScreenAwarenessSettingsSource(
        repo: ScreenAwarenessSettingsRepository,
    ): ScreenAwarenessSettingsSource
}
