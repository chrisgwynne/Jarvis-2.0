package ai.openclaw.jarvis.executor

import android.content.Context
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.capabilities.CapabilityRegistry
import ai.openclaw.jarvis.capabilities.base.CapabilityResult
import ai.openclaw.jarvis.capabilities.impl.MediaAction
import ai.openclaw.jarvis.capture.CaptureOrchestrator
import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.data.local.TrustedContactsStore
import ai.openclaw.jarvis.router.*
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
    private val trustedContacts: TrustedContactsStore,
    private val captureOrchestrator: CaptureOrchestrator,
) {
    // ─── Rich intent entry point (new) ────────────────────────────────────────

    suspend fun executeIntent(parsed: ParsedIntent): ActionOutcome = when (parsed.type) {
        IntentType.CANCEL_STOP    -> ActionOutcome(true, "Okay, stopping.")
        IntentType.DEVICE_CONTROL -> executeDeviceControl(parsed)
        IntentType.APP_OPEN       -> executeOpenApp(parsed.appName ?: "")
        IntentType.LOCATION_QUERY -> executeLocationQuery()
        IntentType.CAMERA_ACTION  -> executeCameraCapture(parsed.cameraAction == CameraSubAction.SELFIE)
        IntentType.SCREEN_CAPTURE -> executeScreenCapture()
        IntentType.TIME_ACTION    -> executeTimeAction(parsed)
        IntentType.COMMUNICATION_CALL -> executeCallWithTrust(parsed.contact ?: "")
        IntentType.COMMUNICATION_SEND -> executeMessageWithTrust(parsed)
        IntentType.OPENCLAW_REQUEST,
        IntentType.MIXED_ACTION   -> ActionOutcome(false, "Routed to OpenClaw.", "OPENCLAW_ROUTE")
        IntentType.ENROL_VOICE,
        IntentType.RECORDING_START,
        IntentType.RECORDING_STOP -> ActionOutcome(false, "Handled by voice layer.", "VOICE_LAYER_ONLY")
        IntentType.CALENDAR_QUERY -> executeCalendarQuery(parsed.calendarLookaheadDays ?: 1)
        IntentType.SMS_READ       -> executeSmsRead()
        IntentType.CONTACT_LOOKUP -> executeContactLookup(parsed.contact ?: "")
    }

    private suspend fun executeDeviceControl(parsed: ParsedIntent): ActionOutcome = when (parsed.deviceAction) {
        DeviceControlAction.TORCH_ON           -> executeTorch(true)
        DeviceControlAction.TORCH_OFF          -> executeTorch(false)
        DeviceControlAction.VOLUME_UP          -> executeVolume(AudioManager.ADJUST_RAISE)
        DeviceControlAction.VOLUME_DOWN        -> executeVolume(AudioManager.ADJUST_LOWER)
        DeviceControlAction.MUTE               -> executeMute(true)
        DeviceControlAction.UNMUTE             -> executeMute(false)
        DeviceControlAction.MEDIA_PLAY_PAUSE   -> executeMediaControl("play_pause")
        DeviceControlAction.MEDIA_NEXT         -> executeMediaControl("next")
        DeviceControlAction.MEDIA_PREVIOUS     -> executeMediaControl("previous")
        DeviceControlAction.MEDIA_STOP         -> executeMediaControl("stop")
        null                                   -> ActionOutcome(false, "Unknown device action.", "UNKNOWN_DEVICE_ACTION")
    }

    private suspend fun executeTimeAction(parsed: ParsedIntent): ActionOutcome {
        val params = mapOf(
            "minutes" to (parsed.durationMinutes?.toString() ?: ""),
            "label"   to (parsed.alarmLabel ?: "Jarvis Timer"),
        )
        return when (parsed.timeAction) {
            TimeSubAction.ALARM    -> executeSetAlarm(params)
            TimeSubAction.REMINDER -> executeSetAlarm(params.toMutableMap().apply { put("label", parsed.alarmLabel ?: "Reminder") })
            else                   -> executeTimerAlarm(params)
        }
    }

    private fun executeSetAlarm(params: Map<String, String>): ActionOutcome {
        val minutes = params["minutes"]?.toIntOrNull()
        val label   = params["label"] ?: "Jarvis Alarm"
        val intent  = android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
            if (minutes != null) {
                // Convert minutes from now into hour/minute absolute time
                val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, minutes) }
                putExtra(android.provider.AlarmClock.EXTRA_HOUR,   cal.get(java.util.Calendar.HOUR_OF_DAY))
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, cal.get(java.util.Calendar.MINUTE))
            }
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionOutcome(true, "Alarm set: $label.")
    }

    // ─── Trusted-contact-aware call ───────────────────────────────────────────

    private suspend fun executeCallWithTrust(contactOrNumber: String): ActionOutcome {
        if (contactOrNumber.isBlank()) return ActionOutcome(false, "Who should I call?", "MISSING_CONTACT")
        val prefs = settings.settings.first()
        val isTrusted = trustedContacts.isTrustedForCall(contactOrNumber)
        if (!isTrusted && !prefs.trustedMode && prefs.confirmDestructive) {
            return ActionOutcome(false, "Confirm calling $contactOrNumber?", "NEEDS_CONFIRM")
        }
        return executeCallRequest(contactOrNumber)
    }

    // ─── Trusted-contact-aware message ────────────────────────────────────────

    private suspend fun executeMessageWithTrust(parsed: ParsedIntent): ActionOutcome {
        val contact = parsed.contact ?: return ActionOutcome(false, "Who should I message?", "MISSING_CONTACT")
        val body    = parsed.messageBody ?: return ActionOutcome(false, "What should I say?", "MISSING_MESSAGE")
        val prefs   = settings.settings.first()

        val isTrustedSms = trustedContacts.isTrustedForSms(contact)
        val isTrustedWa  = trustedContacts.isTrustedForWhatsApp(contact)

        return when (parsed.channel) {
            MessageChannel.WHATSAPP -> {
                if (!isTrustedWa && !prefs.trustedMode && prefs.confirmDestructive) {
                    return ActionOutcome(false, "Confirm WhatsApp to $contact: \"$body\"?", "NEEDS_CONFIRM")
                }
                executeWhatsApp(contact, body)
            }
            MessageChannel.EMAIL -> {
                // Email always routes to OpenClaw; this should not be reached
                ActionOutcome(false, "Email should be routed to OpenClaw.", "OPENCLAW_ROUTE")
            }
            else -> {
                // SMS or best available
                if (!isTrustedSms && !prefs.trustedMode && prefs.confirmDestructive) {
                    return ActionOutcome(false, "Confirm SMS to $contact: \"$body\"?", "NEEDS_CONFIRM")
                }
                executeSmsRequest(contact, body)
            }
        }
    }

    private suspend fun executeWhatsApp(contactOrNumber: String, message: String): ActionOutcome {
        if (!caps.whatsApp.isAvailable()) {
            return ActionOutcome(false, "WhatsApp is not installed.", "WHATSAPP_NOT_INSTALLED")
        }
        val number = if (contactOrNumber.any { it.isLetter() }) {
            when (val r = caps.contacts.findContact(contactOrNumber)) {
                is CapabilityResult.Success -> r.value.firstOrNull()?.phone
                    ?: return ActionOutcome(false, "Couldn't find contact $contactOrNumber.", "CONTACT_NOT_FOUND")
                is CapabilityResult.Failure -> return ActionOutcome(false, r.error.message, r.error.code)
            }
        } else contactOrNumber

        return when (val r = caps.whatsApp.buildOpenChatIntent(number, message)) {
            is CapabilityResult.Success -> {
                context.startActivity(r.value)
                ActionOutcome(true, "Opening WhatsApp for $contactOrNumber.")
            }
            is CapabilityResult.Failure -> ActionOutcome(false, "Couldn't open WhatsApp.", r.error.message)
        }
    }

    // ─── Legacy string-key entry point (kept for backward compat) ────────────

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
            "take_photo"      -> executeCameraCapture(selfie = false)
            "take_selfie"     -> executeCameraCapture(selfie = true)
            "screenshot"      -> executeScreenCapture()
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

    // ─── Camera / Screenshot ─────────────────────────────────────────────────

    private suspend fun executeCameraCapture(selfie: Boolean): ActionOutcome {
        val result = captureOrchestrator.requestCamera(selfie)
        return if (result.saved) ActionOutcome(true, result.message)
        else ActionOutcome(false, result.message, "CAPTURE_FAILED")
    }

    private suspend fun executeScreenCapture(): ActionOutcome {
        val result = captureOrchestrator.requestScreenshot()
        return if (result.saved) ActionOutcome(true, result.message)
        else ActionOutcome(false, result.message, "CAPTURE_FAILED")
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

    // ─── Calendar query ──────────────────────────────────────────────────────

    private suspend fun executeCalendarQuery(lookaheadDays: Int): ActionOutcome {
        val lookaheadMs = lookaheadDays * 24L * 60 * 60 * 1000
        return when (val r = caps.calendar.getUpcomingEvents(lookaheadMs)) {
            is CapabilityResult.Success -> {
                val events = r.value
                if (events.isEmpty()) {
                    val label = if (lookaheadDays <= 1) "today" else "in the next $lookaheadDays days"
                    ActionOutcome(true, "You have nothing scheduled $label.")
                } else {
                    val fmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                    val summary = events.take(5).joinToString(". ") { ev ->
                        "${ev.title} at ${fmt.format(java.util.Date(ev.startMs))}"
                    }
                    val suffix = if (events.size > 5) " and ${events.size - 5} more." else "."
                    ActionOutcome(true, "You have ${events.size} event${if (events.size != 1) "s" else ""}: $summary$suffix")
                }
            }
            is CapabilityResult.Failure -> ActionOutcome(
                false,
                if (r.error.requiresPermission) "Calendar permission is needed." else "Couldn't read calendar.",
                r.error.message,
            )
        }
    }

    // ─── SMS inbox read ───────────────────────────────────────────────────────

    private suspend fun executeSmsRead(): ActionOutcome {
        return when (val r = caps.sms.readUnread(limit = 5)) {
            is CapabilityResult.Success -> {
                val messages = r.value
                if (messages.isEmpty()) {
                    ActionOutcome(true, "No unread messages.")
                } else {
                    val summary = messages.joinToString(". ") { msg ->
                        "From ${msg.sender}: ${msg.body.take(100)}"
                    }
                    ActionOutcome(true, "You have ${messages.size} unread message${if (messages.size != 1) "s" else ""}. $summary")
                }
            }
            is CapabilityResult.Failure -> ActionOutcome(
                false,
                if (r.error.requiresPermission) "SMS permission is needed." else "Couldn't read messages.",
                r.error.message,
            )
        }
    }

    // ─── Contact lookup ───────────────────────────────────────────────────────

    private suspend fun executeContactLookup(query: String): ActionOutcome {
        if (query.isBlank()) return ActionOutcome(false, "Who are you looking for?", "MISSING_CONTACT")
        return when (val r = caps.contacts.findContact(query)) {
            is CapabilityResult.Success -> {
                val contacts = r.value
                if (contacts.isEmpty()) {
                    ActionOutcome(true, "No contacts found matching \"$query\".")
                } else {
                    val top = contacts.take(3).joinToString(". ") { c ->
                        "${c.name}: ${c.phone ?: "no number"}"
                    }
                    ActionOutcome(true, top)
                }
            }
            is CapabilityResult.Failure -> ActionOutcome(
                false,
                if (r.error.requiresPermission) "Contacts permission is needed." else "Couldn't search contacts.",
                r.error.message,
            )
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
