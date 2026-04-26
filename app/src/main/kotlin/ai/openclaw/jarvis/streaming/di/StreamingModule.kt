package ai.openclaw.jarvis.streaming.di

import ai.openclaw.jarvis.voice.AndroidTextToSpeech
import ai.openclaw.jarvis.voice.TextToSpeechEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds [TextToSpeechEngine] to [AndroidTextToSpeech] so the new
 * [ai.openclaw.jarvis.streaming.tts.StreamingTtsController] resolves
 * cleanly under Hilt without forcing every other consumer to know
 * about the concrete class.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StreamingModule {

    @Binds @Singleton
    abstract fun bindTtsEngine(impl: AndroidTextToSpeech): TextToSpeechEngine
}
