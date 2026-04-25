package ai.openclaw.jarvis.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

interface TextToSpeechEngine {
    suspend fun speak(text: String, speed: Float = 1.0f, pitch: Float = 1.0f)
    fun stop()
    fun isReady(): Boolean
    fun shutdown()
}

@Singleton
class AndroidTextToSpeech @Inject constructor(
    @ApplicationContext private val context: Context,
) : TextToSpeechEngine {

    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ready = true
            }
        }
    }

    override fun isReady() = ready

    override suspend fun speak(text: String, speed: Float, pitch: Float): Unit =
        suspendCancellableCoroutine { cont ->
            val engine = tts ?: run { cont.resume(Unit); return@suspendCancellableCoroutine }
            if (!ready) { cont.resume(Unit); return@suspendCancellableCoroutine }

            val utteranceId = UUID.randomUUID().toString()
            engine.setSpeechRate(speed)
            engine.setPitch(pitch)

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) {
                    if (id == utteranceId) cont.resume(Unit)
                }
                @Deprecated("Use onError(String, Int)")
                override fun onError(id: String?) {
                    if (id == utteranceId) cont.resume(Unit)
                }
            })

            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            cont.invokeOnCancellation { engine.stop() }
        }

    override fun stop() = tts?.stop().let {}

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
