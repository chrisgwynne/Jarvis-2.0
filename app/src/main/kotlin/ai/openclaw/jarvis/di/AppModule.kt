package ai.openclaw.jarvis.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Most bindings use @Inject constructors + @Singleton directly on their classes.
 * Capability advertisement wiring is done in JarvisApp.onCreate() after injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
