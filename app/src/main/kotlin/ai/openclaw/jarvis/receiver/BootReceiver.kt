package ai.openclaw.jarvis.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.voice.AlwaysListeningService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: SettingsDataStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = runBlocking { settings.settings.first() }
        if (prefs.alwaysListeningEnabled) {
            AlwaysListeningService.start(context)
        }
    }
}
