package ai.openclaw.jarvis.receiver

import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.voice.AlwaysListeningService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-arms the always-listening service after a reboot when the user
 * had it enabled.
 *
 * BroadcastReceivers run on the main thread and ANR after ~10s. The
 * naive `runBlocking { settings.first() }` here used to read DataStore
 * synchronously — fast in practice but a latent ANR risk on cold boot.
 *
 * The fix: take a goAsync() PendingResult, hand the rest of the work
 * to a background coroutine, and call `finish()` when done. The system
 * keeps the receiver alive until finish().
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: SettingsDataStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val prefs = settings.settings.first()
                if (prefs.alwaysListeningEnabled) {
                    AlwaysListeningService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
