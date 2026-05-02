package ai.openclaw.jarvis.voice

import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.voice.whisper.WhisperSttEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes STT requests to either [AndroidSpeechToText] or [WhisperSttEngine]
 * based on the "stt_engine" setting. Whisper is only used if the native
 * library and model are both available; otherwise falls back to Android STT.
 *
 * Injected into [SpeechSessionManager] in place of [AndroidSpeechToText]
 * so the rest of the pipeline is engine-agnostic.
 */
@Singleton
class SpeechToTextProxy @Inject constructor(
    private val android: AndroidSpeechToText,
    private val whisper: WhisperSttEngine,
    private val settings: SettingsDataStore,
) : SpeechToText {

    override fun isAvailable(): Boolean = android.isAvailable() || whisper.isAvailable()

    override fun listen(): Flow<SttEvent> = flow {
        val prefs = settings.settings.first()
        val engine: SpeechToText =
            if (prefs.sttEngine == "whisper" && whisper.isAvailable()) whisper else android
        emitAll(engine.listen())
    }

    override fun cancel() {
        android.cancel()
        whisper.cancel()
    }
}
