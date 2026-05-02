package ai.openclaw.jarvis.protocol.executor

import ai.openclaw.jarvis.capabilities.CapabilityRegistry
import ai.openclaw.jarvis.capabilities.base.CapabilityResult
import ai.openclaw.jarvis.protocol.ProtocolVersion
import ai.openclaw.jarvis.protocol.model.ActionResultError
import ai.openclaw.jarvis.protocol.model.ActionResultStatus
import ai.openclaw.jarvis.protocol.model.JarvisActionResult
import ai.openclaw.jarvis.protocol.model.OpenClawAction
import ai.openclaw.jarvis.protocol.model.PayloadCaptureScreenshot
import ai.openclaw.jarvis.protocol.model.PayloadCreateAlarm
import ai.openclaw.jarvis.protocol.model.PayloadCreateTimer
import ai.openclaw.jarvis.protocol.model.PayloadGetLocation
import ai.openclaw.jarvis.protocol.model.PayloadMakeCall
import ai.openclaw.jarvis.protocol.model.PayloadOpenApp
import ai.openclaw.jarvis.protocol.model.PayloadSendSms
import ai.openclaw.jarvis.protocol.model.PayloadSendWhatsApp
import ai.openclaw.jarvis.protocol.model.PayloadShowNotification
import ai.openclaw.jarvis.protocol.model.PayloadSpeak
import ai.openclaw.jarvis.protocol.model.PayloadTakePhoto
import ai.openclaw.jarvis.protocol.model.ActionType
import ai.openclaw.jarvis.protocol.model.ActionRisk
import ai.openclaw.jarvis.protocol.validation.DecodedAction
import ai.openclaw.jarvis.trust.TrustLevel
import ai.openclaw.jarvis.trust.TrustManager
import ai.openclaw.jarvis.voice.AndroidTextToSpeech
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Bridge from a typed [OpenClawAction] payload to the existing capability
 * layer. Returns a [JarvisActionResult] ready to be sent back to OpenClaw.
 *
 * This intentionally bypasses the "should I confirm?" gate inside the
 * legacy [ai.openclaw.jarvis.executor.AndroidActionExecutor] —
 * confirmation is now driven by [OpenClawAction.requiresConfirmation],
 * staged in the session manager, and only reaches this executor after
 * the user has accepted (or never, for safe non-confirming actions).
 */
@Singleton
class ContractActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val caps: CapabilityRegistry,
    private val tts: AndroidTextToSpeech,
    private val trustManager: TrustManager,
) {
    /**
     * Execute one decoded action and produce its [JarvisActionResult].
     *
     * `requestId` and `sessionKey` are taken from the in-flight request so
     * OpenClaw can correlate the result back to the conversation turn.
     */
    suspend fun execute(
        decoded: DecodedAction,
        requestId: String,
        sessionKey: String,
    ): JarvisActionResult {
        val action = decoded.action
        val ts = nowIso()

        // OWNER trust gate for restricted actions.
        if (action.risk == ActionRisk.restricted &&
            trustManager.currentTrustLevel() != TrustLevel.OWNER) {
            return reject(
                action, requestId, sessionKey, ts,
                ActionResultStatus.cancelled,
                "TRUST_INSUFFICIENT",
                "Restricted action requires OWNER trust",
            )
        }

        return try {
            when (action.type) {
                ActionType.SEND_SMS -> sendSms(action, decoded.payload as PayloadSendSms, requestId, sessionKey, ts)
                ActionType.SEND_WHATSAPP -> sendWhatsApp(action, decoded.payload as PayloadSendWhatsApp, requestId, sessionKey, ts)
                ActionType.MAKE_CALL -> makeCall(action, decoded.payload as PayloadMakeCall, requestId, sessionKey, ts)
                ActionType.OPEN_APP -> openApp(action, decoded.payload as PayloadOpenApp, requestId, sessionKey, ts)
                ActionType.GET_LOCATION -> getLocation(action, decoded.payload as PayloadGetLocation, requestId, sessionKey, ts)
                ActionType.CAPTURE_SCREENSHOT -> captureScreenshot(action, decoded.payload as PayloadCaptureScreenshot, requestId, sessionKey, ts)
                ActionType.TAKE_PHOTO -> notSupportedYet(action, requestId, sessionKey, ts, "TAKE_PHOTO requires UI handling")
                ActionType.SHOW_NOTIFICATION -> showNotification(action, decoded.payload as PayloadShowNotification, requestId, sessionKey, ts)
                ActionType.SPEAK -> speak(action, decoded.payload as PayloadSpeak, requestId, sessionKey, ts)
                ActionType.CREATE_TIMER -> createTimer(action, decoded.payload as PayloadCreateTimer, requestId, sessionKey, ts)
                ActionType.CREATE_ALARM -> createAlarm(action, decoded.payload as PayloadCreateAlarm, requestId, sessionKey, ts)
            }
        } catch (t: Throwable) {
            reject(action, requestId, sessionKey, ts,
                ActionResultStatus.failed,
                "EXECUTION_THREW",
                t.message ?: t.javaClass.simpleName)
        }
    }

    // ─── Per-action handlers ─────────────────────────────────────────────────

    private suspend fun sendSms(
        action: OpenClawAction, p: PayloadSendSms,
        requestId: String, sessionKey: String, ts: String,
    ): JarvisActionResult {
        if (!caps.sms.isAvailable()) return permission(action, "sms", requestId, sessionKey, ts)
        val number = resolveContact(p.toContactName)
            ?: return reject(action, requestId, sessionKey, ts,
                ActionResultStatus.failed,
                "CONTACT_NOT_FOUND",
                "Could not resolve contact ${p.toContactName}")
        return when (val r = caps.sms.sendSms(number, p.message)) {
            is CapabilityResult.Success -> success(action, requestId, sessionKey, ts) {
                put("recipient", p.toContactName); put("phone", number)
            }
            is CapabilityResult.Failure -> reject(action, requestId, sessionKey, ts,
                if (r.error.requiresPermission) ActionResultStatus.permission_missing else ActionResultStatus.failed,
                r.error.code, r.error.message)
        }
    }

    private suspend fun sendWhatsApp(
        action: OpenClawAction, p: PayloadSendWhatsApp,
        requestId: String, sessionKey: String, ts: String,
    ): JarvisActionResult {
        if (!caps.whatsApp.isAvailable())
            return reject(action, requestId, sessionKey, ts,
                ActionResultStatus.unsupported,
                "WHATSAPP_NOT_INSTALLED",
                "WhatsApp is not installed")
        val number = resolveContact(p.toContactName)
            ?: return reject(action, requestId, sessionKey, ts,
                ActionResultStatus.failed,
                "CONTACT_NOT_FOUND",
                "Could not resolve contact ${p.toContactName}")
        return when (val r = caps.whatsApp.buildOpenChatIntent(number, p.message)) {
            is CapabilityResult.Success -> {
                context.startActivity(r.value.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                success(action, requestId, sessionKey, ts) {
                    put("recipient", p.toContactName); put("phone", number)
                }
            }
            is CapabilityResult.Failure -> reject(action, requestId, sessionKey, ts,
                ActionResultStatus.failed, r.error.code, r.error.message)
        }
    }

    private suspend fun makeCall(
        action: OpenClawAction, p: PayloadMakeCall,
        requestId: String, sessionKey: String, ts: String,
    ): JarvisActionResult {
        if (!caps.calls.isAvailable()) return permission(action, "calls", requestId, sessionKey, ts)
        val number = resolveContact(p.toContactName) ?: p.toContactName
        return when (val r = caps.calls.buildCallIntent(number)) {
            is CapabilityResult.Success -> {
                context.startActivity(r.value.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                success(action, requestId, sessionKey, ts) {
                    put("recipient", p.toContactName); put("phone", number)
                }
            }
            is CapabilityResult.Failure -> reject(action, requestId, sessionKey, ts,
                if (r.error.requiresPermission) ActionResultStatus.permission_missing else ActionResultStatus.failed,
                r.error.code, r.error.message)
        }
    }

    private fun openApp(
        action: OpenClawAction, p: PayloadOpenApp,
        requestId: String, sessionKey: String, ts: String,
    ): JarvisActionResult = when (val r = caps.apps.buildLaunchIntent(p.appName)) {
        is CapabilityResult.Success -> {
            context.startActivity(r.value.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            success(action, requestId, sessionKey, ts) { put("appName", p.appName) }
        }
        is CapabilityResult.Failure -> reject(action, requestId, sessionKey, ts,
            ActionResultStatus.failed, r.error.code, r.error.message)
    }

    private suspend fun getLocation(
        action: OpenClawAction, @Suppress("UNUSED_PARAMETER") p: PayloadGetLocation,
        requestId: String, sessionKey: String, ts: String,
    ): JarvisActionResult {
        if (!caps.location.isAvailable()) return permission(action, "location", requestId, sessionKey, ts)
        return when (val r = caps.location.getLastKnownLocation()) {
            is CapabilityResult.Success -> success(action, requestId, sessionKey, ts) {
                put("label", caps.location.getLocationLabel())
                put("latitude", r.value.latitude.toString())
                put("longitude", r.value.longitude.toString())
            }
            is CapabilityResult.Failure -> reject(action, requestId, sessionKey, ts,
                if (r.error.requiresPermission) ActionResultStatus.permission_missing else ActionResultStatus.failed,
                r.error.code, r.error.message)
        }
    }

    private fun captureScreenshot(
        action: OpenClawAction, @Suppress("UNUSED_PARAMETER") p: PayloadCaptureScreenshot,
        requestId: String, sessionKey: String, ts: String,
    ): JarvisActionResult = reject(
        action, requestId, sessionKey, ts,
        ActionResultStatus.unsupported,
        "REQUIRES_UI",
        "Screenshot capture requires the foreground UI flow",
    )

    private suspend fun showNotification(
        action: OpenClawAction, p: PayloadShowNotification,
        requestId: String, sessionKey: String, ts: String,
    ): JarvisActionResult {
        return when (val r = caps.notification.postNotification(p.title, p.body)) {
            is CapabilityResult.Success -> success(action, requestId, sessionKey, ts) {
                put("title", p.title)
            }
            is CapabilityResult.Failure -> reject(action, requestId, sessionKey, ts,
                if (r.error.requiresPermission) ActionResultStatus.permission_missing else ActionResultStatus.failed,
                r.error.code, r.error.message)
        }
    }

    private suspend fun speak(
        action: OpenClawAction, p: PayloadSpeak,
        requestId: String, sessionKey: String, ts: String,
    ): JarvisActionResult {
        return try {
            tts.speak(p.text, 1.0f, 1.0f)
            success(action, requestId, sessionKey, ts) { put("text", p.text) }
        } catch (t: Throwable) {
            reject(action, requestId, sessionKey, ts,
                ActionResultStatus.failed, "TTS_FAILED", t.message ?: "TTS error")
        }
    }

    private fun createTimer(
        action: OpenClawAction, p: PayloadCreateTimer,
        requestId: String, sessionKey: String, ts: String,
    ): JarvisActionResult {
        if (p.minutes <= 0) return reject(action, requestId, sessionKey, ts,
            ActionResultStatus.failed, "INVALID_MINUTES", "minutes must be > 0")
        val seconds = p.minutes * 60
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, p.label ?: "Jarvis Timer")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return success(action, requestId, sessionKey, ts) {
            put("minutes", p.minutes.toString()); put("label", p.label ?: "")
        }
    }

    private fun createAlarm(
        action: OpenClawAction, p: PayloadCreateAlarm,
        requestId: String, sessionKey: String, ts: String,
    ): JarvisActionResult {
        if (p.minutes <= 0) return reject(action, requestId, sessionKey, ts,
            ActionResultStatus.failed, "INVALID_MINUTES", "minutes must be > 0")
        val cal = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.MINUTE, p.minutes)
        }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, cal.get(java.util.Calendar.HOUR_OF_DAY))
            putExtra(AlarmClock.EXTRA_MINUTES, cal.get(java.util.Calendar.MINUTE))
            putExtra(AlarmClock.EXTRA_MESSAGE, p.label ?: "Jarvis Alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return success(action, requestId, sessionKey, ts) {
            put("minutes", p.minutes.toString()); put("label", p.label ?: "")
        }
    }

    private fun notSupportedYet(
        action: OpenClawAction,
        requestId: String, sessionKey: String, ts: String,
        message: String,
    ) = reject(action, requestId, sessionKey, ts,
        ActionResultStatus.unsupported, "REQUIRES_UI", message)

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun resolveContact(query: String): String? {
        if (query.none { it.isLetter() }) return query  // already a phone number
        return when (val r = caps.contacts.findContact(query)) {
            is CapabilityResult.Success -> r.value.firstOrNull()?.phone
            is CapabilityResult.Failure -> null
        }
    }

    private fun success(
        action: OpenClawAction,
        requestId: String, sessionKey: String, ts: String,
        result: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {},
    ) = JarvisActionResult(
        protocolVersion = ProtocolVersion.CURRENT,
        requestId = requestId,
        sessionKey = sessionKey,
        actionId = action.actionId,
        timestamp = ts,
        status = ActionResultStatus.success,
        result = buildJsonObject(result),
    )

    private fun reject(
        action: OpenClawAction,
        requestId: String, sessionKey: String, ts: String,
        status: ActionResultStatus,
        code: String,
        message: String,
        result: JsonObject = JsonObject(emptyMap()),
    ) = JarvisActionResult(
        protocolVersion = ProtocolVersion.CURRENT,
        requestId = requestId,
        sessionKey = sessionKey,
        actionId = action.actionId,
        timestamp = ts,
        status = status,
        result = result,
        error = ActionResultError(code = code, message = message),
    )

    private fun permission(
        action: OpenClawAction, scope: String,
        requestId: String, sessionKey: String, ts: String,
    ) = reject(
        action, requestId, sessionKey, ts,
        ActionResultStatus.permission_missing,
        "PERMISSION_MISSING",
        "Permission required for $scope",
    )

    private fun nowIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
