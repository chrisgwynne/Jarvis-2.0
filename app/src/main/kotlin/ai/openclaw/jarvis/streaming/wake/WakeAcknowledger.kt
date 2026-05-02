package ai.openclaw.jarvis.streaming.wake

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sub-150ms acknowledgement on wake-word detection. Plays a short
 * notification tone synchronously — way cheaper than booting the TTS
 * engine to say "Yes?" and good enough to confirm the user that Jarvis
 * heard them while STT is spinning up.
 *
 * Spec target: <150ms. ToneGenerator.startTone() returns immediately
 * once the tone starts; Android's audio path is typically well under
 * 50ms once the AudioFocus is taken.
 *
 * Mode is configurable: `TONE_ONLY` (default), `VOICE_ONLY` (caller
 * supplies the spoken ack via TTS), or `BOTH`.
 */
@Singleton
class WakeAcknowledger @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class Mode { TONE_ONLY, VOICE_ONLY, BOTH }

    @Volatile var mode: Mode = Mode.TONE_ONLY
    @Volatile var enabled: Boolean = true

    private val tonegen by lazy {
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME_PERCENT)
        }.getOrNull()
    }

    /**
     * Fire the audible acknowledgement now. Returns immediately —
     * caller must not await; the tone plays in the background and the
     * STT pipeline can start in parallel.
     */
    fun acknowledge(): Boolean {
        if (!enabled) return false
        if (mode == Mode.VOICE_ONLY) return false
        val gen = tonegen ?: return false
        // PROP_BEEP is one of the cheapest tones — under 100ms total.
        return gen.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_DURATION_MS)
    }

    /** Dispose the underlying ToneGenerator. Safe to call multiple times. */
    fun release() {
        runCatching { tonegen?.release() }
    }

    companion object {
        private const val VOLUME_PERCENT = 60
        private const val TONE_DURATION_MS = 80
    }
}
