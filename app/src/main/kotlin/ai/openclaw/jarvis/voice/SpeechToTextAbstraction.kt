package ai.openclaw.jarvis.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class SttEvent {
    object Started  : SttEvent()
    object Stopped  : SttEvent()
    data class Partial(val text: String) : SttEvent()
    data class Final(val text: String)   : SttEvent()
    data class Error(val code: Int, val message: String) : SttEvent()
}

interface SpeechToText {
    fun isAvailable(): Boolean
    fun listen(): Flow<SttEvent>
    fun cancel()
}

/**
 * Android SpeechRecognizer implementation.
 * Must be called on the main thread (SpeechRecognizer requirement).
 */
@Singleton
class AndroidSpeechToText @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechToText {

    private var recognizer: SpeechRecognizer? = null

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun listen(): Flow<SttEvent> = callbackFlow {
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { trySend(SttEvent.Started) }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { trySend(SttEvent.Stopped) }

            override fun onError(error: Int) {
                trySend(SttEvent.Error(error, errorMessage(error)))
                close()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                trySend(SttEvent.Final(text))
                close()
            }

            override fun onPartialResults(partial: Bundle?) {
                val matches = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) trySend(SttEvent.Partial(text))
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        sr.startListening(intent)

        awaitClose {
            sr.stopListening()
            sr.destroy()
            recognizer = null
        }
    }

    override fun cancel() {
        // Only stop listening — this signals the engine to finalize and deliver
        // onResults(). destroy() is called by awaitClose when the flow closes,
        // so calling it here would kill the engine before results arrive.
        recognizer?.stopListening()
    }

    private fun errorMessage(code: Int) = when (code) {
        SpeechRecognizer.ERROR_AUDIO             -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT            -> "Client side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK           -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT   -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH          -> "No speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY   -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER            -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT    -> "No speech detected"
        else                                     -> "Unknown error ($code)"
    }
}
