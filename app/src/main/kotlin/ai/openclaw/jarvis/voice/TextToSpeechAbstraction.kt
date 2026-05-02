package ai.openclaw.jarvis.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import ai.openclaw.jarvis.data.local.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
    private val settingsStore: SettingsDataStore,
) : TextToSpeechEngine {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Observe the chosen engine and reinitialise whenever it changes.
        // distinctUntilChanged prevents a spurious reinit on every settings write.
        scope.launch {
            settingsStore.settings
                .map { it.ttsEngine }
                .distinctUntilChanged()
                .collect { engine ->
                    val pkg = if (engine == "google") "com.google.android.tts" else null
                    withContext(Dispatchers.Main) { initTts(pkg) }
                }
        }
    }

    private fun initTts(enginePackage: String?) {
        val old = tts
        tts = null
        ready = false
        old?.stop()
        old?.shutdown()
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ready = true
            }
        }
        tts = if (enginePackage != null) {
            TextToSpeech(context, listener, enginePackage)
        } else {
            TextToSpeech(context, listener)
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

    override fun stop() { tts?.stop() }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
