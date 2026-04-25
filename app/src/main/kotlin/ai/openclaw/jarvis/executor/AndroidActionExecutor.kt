package ai.openclaw.jarvis.executor

import android.content.Context
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.capabilities.CapabilityRegistry
import ai.openclaw.jarvis.capabilities.base.CapabilityResult
import ai.openclaw.jarvis.capabilities.impl.MediaAction
import ai.openclaw.jarvis.data.local.SettingsDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class ActionOutcome(
    val success: Boolean,
    val spokenReply: String,
    val error: String? = null,
)

/**
 * Executes resolved local Android intents via the capability layer.
 *
 * Each action method:
 *   - never fakes success
 *   - returns a human-readable spoken reply
 *   - enforces confirmation for destructive/sensitive actions
 */
@Singleton
class AndroidActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val caps: CapabilityRegistry,
    private val settings: SettingsDataStore,
) {
    suspend fun execute(intent: String, params: Map<String, String> = emptyMap()): ActionOutcome {
        return when (intent) {
            "stop"            -> ActionOutcome(true, "Okay, stopping.")
            "torch_on"        -> executeTorch(true)
            "torch_off"       -> executeTorch(false)
            "volume_up"       -> executeVolume(AudioManager.ADJUST_RAISE)
            "volume_down"     -> executeVolume(AudioManager.ADJUST_LOWER)
            "mute"            -> executeMute(true)
            "unmute"          -> executeMute(false)
            "media_control"   -> executeMediaControl(params["action"] ?: "play_pause")
            "open_app"        -> executeOpenApp(params["app"] ?: "")
            "take_photo"      -> ActionOutcome(false, "Camera capture requires the app UI. Tap the camera button.", "REQUIRES_UI")
            "screenshot"      -> ActionOutcome(false, "Screenshot requires the app UI. Use the screenshot button.", "REQUIRES_UI")
            "location_query"  -> executeLocationQuery()
            "set_timer_alarm" -> executeTimerAlarm(params)
            "call"            -> executeCallRequest(params["contact"] ?: params["number"] ?: "")
            "send_message"    -> executeSmsRequest(
                params["contact"] ?: params["number"] ?: "",
                params["message"] ?: "",
            )
            else -> ActionOutcome(false, "I don't know how to do that locally.", "UNKNOWN_INTENT")
        }
    }

    // ─── Torch ───────────────────────────────────────────────────────────────

    private suspend fun executeTorch(on: Boolean): ActionOutcome {
        return when (val r = caps.device.setTorch(on)) {
            is CapabilityResult.Success -> ActionOutcome(true, if (on) "Torch on." else "Torch off.")
            is CapabilityResult.Failure -> ActionOutcome(false, "Couldn't ${if (on) "enable" else "disable"} torch.", r.error.message)
        }
    }

    // ─── Volume ───────────────────────────────────────────────────────────────

    private suspend fun executeVolume(direction: Int): ActionOutcome {
        return when (val r = caps.device.setVolume(AudioManager.STREAM_MUSIC, direction)) {
            is CapabilityResult.Success -> ActionOutcome(true, "Volume adjusted.")
            is CapabilityResult.Failure -> ActionOutcome(false, "Couldn't adjust volume.", r.error.message)
        }
    }

    private suspend fun executeMute(mute: Boolean): ActionOutcome {
        return when (val r = caps.device.mute(mute)) {
            is CapabilityResult.Success -> ActionOutcome(true, if (mute) "Phone muted." else "Phone unmuted.")
            is CapabilityResult.Failure -> ActionOutcome(false, "Couldn't change mute state.", r.error.message)
        }
    }

    // ─── Media ────────────────────────────────────────────────────────────────

    private suspend fun executeMediaControl(action: String): ActionOutcome {
        val mediaAction = when (action.lowercase()) {
            "play", "resume", "play_pause" -> MediaAction.PLAY_PAUSE
            "pause"                         -> MediaAction.PLAY_PAUSE
            "next", "skip"                  -> MediaAction.NEXT
            "previous", "back"              -> MediaAction.PREVIOUS
            "stop"                          -> MediaAction.STOP
            else                            -> MediaAction.PLAY_PAUSE
        }
        return when (val r = caps.media.sendMediaAction(mediaAction)) {
            is CapabilityResult.Success -> ActionOutcome(true, "Done.")
            is CapabilityResult.Failure -> ActionOutcome(false, "Media control failed.", r.error.message)
        }
    }

    // ─── Open App ────────────────────────────────────────────────────────────

    private fun executeOpenApp(appName: String): ActionOutcome {
        if (appName.isBlank()) return ActionOutcome(false, "Which app should I open?", "MISSING_APP")
        return when (val r = caps.apps.buildLaunchIntent(appName)) {
            is CapabilityResult.Success -> {
                context.startActivity(r.value)
                ActionOutcome(true, "Opening $appName.")
            }
            is CapabilityResult.Failure -> ActionOutcome(false, "Couldn't find $appName.", r.error.message)
        }
    }

    // ─── Location ────────────────────────────────────────────────────────────

    private suspend fun executeLocationQuery(): ActionOutcome {
        return when (val r = caps.location.getLastKnownLocation()) {
            is CapabilityResult.Success -> {
                val loc = r.value
                ActionOutcome(true, "You're near ${loc.label}.")
            }
            is CapabilityResult.Failure -> ActionOutcome(
                false,
                if (r.error.requiresPermission) "Location permission is needed." else "Location unavailable.",
                r.error.message,
            )
        }
    }

    // ─── Timer / Alarm ───────────────────────────────────────────────────────

    private fun executeTimerAlarm(params: Map<String, String>): ActionOutcome {
        val minutes = params["minutes"]?.toIntOrNull()
        val label   = params["label"] ?: "Jarvis Timer"
        val intent  = android.content.Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
            if (minutes != null) putExtra(android.provider.AlarmClock.EXTRA_LENGTH, minutes * 60)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        val duration = if (minutes != null) "$minutes minute${if (minutes != 1) "s" else ""}" else "timer"
        return ActionOutcome(true, "Timer set for $duration.")
    }

    // ─── Call (confirmation required) ────────────────────────────────────────

    private suspend fun executeCallRequest(contactOrNumber: String): ActionOutcome {
        if (contactOrNumber.isBlank()) {
            return ActionOutcome(false, "Who should I call?", "MISSING_CONTACT")
        }
        val prefs = settings.settings.first()
        if (!prefs.trustedMode && prefs.confirmDestructive) {
            // Caller is responsible for showing confirmation UI before calling execute("call", ...)
            return ActionOutcome(false, "Call confirmation required.", "NEEDS_CONFIRM")
        }
        // Resolve number from contacts if needed
        val number = if (contactOrNumber.any { it.isLetter() }) {
            when (val r = caps.contacts.findContact(contactOrNumber)) {
                is CapabilityResult.Success -> r.value.firstOrNull()?.phone
                    ?: return ActionOutcome(false, "Couldn't find contact $contactOrNumber.", "CONTACT_NOT_FOUND")
                is CapabilityResult.Failure -> return ActionOutcome(false, r.error.message, r.error.code)
            }
        } else contactOrNumber

        return when (val r = caps.calls.buildCallIntent(number)) {
            is CapabilityResult.Success -> {
                context.startActivity(r.value)
                ActionOutcome(true, "Calling $contactOrNumber.")
            }
            is CapabilityResult.Failure -> ActionOutcome(false, "Couldn't place call.", r.error.message)
        }
    }

    // ─── SMS (confirmation required) ─────────────────────────────────────────

    private suspend fun executeSmsRequest(contactOrNumber: String, message: String): ActionOutcome {
        if (contactOrNumber.isBlank()) {
            return ActionOutcome(false, "Who should I text?", "MISSING_CONTACT")
        }
        if (message.isBlank()) {
            return ActionOutcome(false, "What message should I send?", "MISSING_MESSAGE")
        }
        val prefs = settings.settings.first()
        if (!prefs.trustedMode && prefs.confirmDestructive) {
            return ActionOutcome(false, "SMS confirmation required.", "NEEDS_CONFIRM")
        }
        val number = if (contactOrNumber.any { it.isLetter() }) {
            when (val r = caps.contacts.findContact(contactOrNumber)) {
                is CapabilityResult.Success -> r.value.firstOrNull()?.phone
                    ?: return ActionOutcome(false, "Couldn't find contact $contactOrNumber.", "CONTACT_NOT_FOUND")
                is CapabilityResult.Failure -> return ActionOutcome(false, r.error.message, r.error.code)
            }
        } else contactOrNumber

        return when (val r = caps.sms.sendSms(number, message)) {
            is CapabilityResult.Success -> ActionOutcome(true, "Message sent to $contactOrNumber.")
            is CapabilityResult.Failure -> ActionOutcome(false, "Couldn't send message.", r.error.message)
        }
    }
}
