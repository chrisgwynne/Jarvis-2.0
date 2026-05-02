package ai.openclaw.jarvis.capabilities.impl

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.capabilities.base.*
import javax.inject.Inject
import javax.inject.Singleton

enum class MediaAction { PLAY_PAUSE, NEXT, PREVIOUS, STOP }

interface MediaCapability : Capability {
    suspend fun sendMediaAction(action: MediaAction): CapabilityResult<Unit>
    fun getCurrentVolume(): Int
    fun getMaxVolume(): Int
}

@Singleton
class MediaCapabilityImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaCapability {

    override val id = "media"
    override val description = "Media playback control and volume"
    override val requiredPermissions: List<String> = emptyList()

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun isAvailable() = true

    override suspend fun sendMediaAction(action: MediaAction): CapabilityResult<Unit> {
        val keyCode = when (action) {
            MediaAction.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MediaAction.NEXT       -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaAction.PREVIOUS   -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            MediaAction.STOP       -> KeyEvent.KEYCODE_MEDIA_STOP
        }
        return try {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            capabilitySuccess(Unit)
        } catch (e: Exception) {
            capabilityFailure("MEDIA_ERROR", e.message ?: "Media key dispatch failed")
        }
    }

    override fun getCurrentVolume(): Int =
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    override fun getMaxVolume(): Int =
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
}
