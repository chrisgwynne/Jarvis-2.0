package ai.openclaw.jarvis.audio

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AudioDevice {
    PHONE_SPEAKER,
    PHONE_EARPIECE,
    WIRED_HEADSET,
    BLUETOOTH_SCO,     // BT mic+headset (calls/voice)
    BLUETOOTH_A2DP,    // BT output only (media/TTS)
}

data class AudioRouteState(
    val activeDevice: AudioDevice = AudioDevice.PHONE_SPEAKER,
    val bluetoothScoConnected: Boolean = false,
    val bluetoothA2dpConnected: Boolean = false,
    val wiredHeadsetConnected: Boolean = false,
    val scoStarted: Boolean = false,
)

/**
 * Manages audio routing for microphone capture and TTS output.
 *
 * Responsibilities:
 *   - Detects active audio device (BT SCO, A2DP, wired, phone)
 *   - Starts/stops Bluetooth SCO when voice capture needs it
 *   - Routes TTS through the active device
 *   - Handles device connect/disconnect gracefully
 *
 * Bluetooth is ONLY an audio transport layer — it does not affect intent routing.
 */
@Singleton
class AudioRouteManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val btManager    = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val _state = MutableStateFlow(AudioRouteState())
    val state: StateFlow<AudioRouteState> = _state.asStateFlow()

    val activeDevice: AudioDevice get() = _state.value.activeDevice

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED
                    )
                    val connected = state == BluetoothProfile.STATE_CONNECTED
                    _state.value = _state.value.copy(bluetoothScoConnected = connected)
                    updateActiveDevice()
                    if (!connected) stopBluetoothSco()
                }
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val scoState = intent.getIntExtra(
                        AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED
                    )
                    val active = scoState == AudioManager.SCO_AUDIO_STATE_CONNECTED
                    _state.value = _state.value.copy(scoStarted = active)
                    if (active) _state.value = _state.value.copy(activeDevice = AudioDevice.BLUETOOTH_SCO)
                    else updateActiveDevice()
                }
                Intent.ACTION_HEADSET_PLUG -> {
                    val plugged = intent.getIntExtra("state", 0) == 1
                    _state.value = _state.value.copy(wiredHeadsetConnected = plugged)
                    updateActiveDevice()
                }
                "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED" -> {
                    val state = intent.getIntExtra(
                        BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED
                    )
                    val connected = state == BluetoothProfile.STATE_CONNECTED
                    _state.value = _state.value.copy(bluetoothA2dpConnected = connected)
                    updateActiveDevice()
                }
            }
        }
    }

    init {
        registerReceiver()
        refreshState()
    }

    // ─── Capture preparation ──────────────────────────────────────────────────

    /**
     * Call before starting STT.
     * Starts Bluetooth SCO if a BT headset is connected so the BT mic is used.
     * Returns the device that will be used for capture.
     */
    suspend fun prepareForCapture(): AudioDevice {
        if (_state.value.bluetoothScoConnected && !_state.value.scoStarted) {
            startBluetoothSco()
            // Wait up to 2s for SCO to connect
            var waited = 0
            while (!_state.value.scoStarted && waited < 2000) {
                delay(100)
                waited += 100
            }
        }
        audioManager.mode = if (_state.value.scoStarted) {
            AudioManager.MODE_IN_COMMUNICATION
        } else {
            AudioManager.MODE_NORMAL
        }
        return activeDevice
    }

    /**
     * Call before TTS playback.
     * Stops SCO (which blocks A2DP) and enables A2DP or speaker output.
     */
    suspend fun prepareForPlayback() {
        if (_state.value.scoStarted) stopBluetoothSco()
        audioManager.mode = AudioManager.MODE_NORMAL
        if (_state.value.bluetoothA2dpConnected || _state.value.bluetoothScoConnected) {
            audioManager.isBluetoothA2dpOn.let { /* already routes via A2DP */ }
        }
    }

    /** Call after a voice session ends to restore normal audio state. */
    fun releaseAudioFocus() {
        if (_state.value.scoStarted) stopBluetoothSco()
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    // ─── SCO management ───────────────────────────────────────────────────────

    private fun startBluetoothSco() {
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true
    }

    private fun stopBluetoothSco() {
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
        _state.value = _state.value.copy(scoStarted = false)
        updateActiveDevice()
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun updateActiveDevice() {
        val s = _state.value
        val device = when {
            s.scoStarted               -> AudioDevice.BLUETOOTH_SCO
            s.bluetoothScoConnected    -> AudioDevice.BLUETOOTH_SCO
            s.bluetoothA2dpConnected   -> AudioDevice.BLUETOOTH_A2DP
            s.wiredHeadsetConnected    -> AudioDevice.WIRED_HEADSET
            audioManager.isSpeakerphoneOn -> AudioDevice.PHONE_SPEAKER
            else                       -> AudioDevice.PHONE_EARPIECE
        }
        _state.value = s.copy(activeDevice = device)
    }

    private fun hasBluetoothConnect(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED

    private fun refreshState() {
        val scoConnected = if (hasBluetoothConnect()) {
            btManager?.adapter?.getProfileConnectionState(BluetoothProfile.HEADSET) ==
                BluetoothProfile.STATE_CONNECTED
        } else {
            false
        }
        _state.value = _state.value.copy(
            bluetoothScoConnected  = scoConnected,
            wiredHeadsetConnected  = audioManager.isWiredHeadsetOn,
        )
        updateActiveDevice()
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    fun destroy() {
        runCatching { context.unregisterReceiver(receiver) }
        scope.cancel()
    }
}
