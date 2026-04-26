package ai.openclaw.jarvis.policy.integration

import ai.openclaw.jarvis.policy.model.ActionDescriptor
import ai.openclaw.jarvis.policy.model.ActionKind
import ai.openclaw.jarvis.policy.model.AutonomyLevel
import ai.openclaw.jarvis.protocol.model.ActionRisk as ProtocolRisk
import ai.openclaw.jarvis.protocol.model.ActionType as ProtocolType
import ai.openclaw.jarvis.protocol.model.OpenClawAction
import ai.openclaw.jarvis.protocol.model.PayloadCreateAlarm
import ai.openclaw.jarvis.protocol.model.PayloadCreateTimer
import ai.openclaw.jarvis.protocol.model.PayloadMakeCall
import ai.openclaw.jarvis.protocol.model.PayloadOpenApp
import ai.openclaw.jarvis.protocol.model.PayloadSendSms
import ai.openclaw.jarvis.protocol.model.PayloadSendWhatsApp
import ai.openclaw.jarvis.protocol.model.PayloadShowNotification
import ai.openclaw.jarvis.protocol.model.PayloadSpeak
import ai.openclaw.jarvis.protocol.validation.DecodedAction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts a [DecodedAction] from the typed OpenClaw contract into the
 * source-agnostic [ActionDescriptor] the policy engine reads. Keeps
 * the protocol package free of any dependency on the policy package
 * (and vice versa) — only this thin adapter knows about both.
 *
 * Maps OpenClaw's `risk` (`safe / limited / restricted`) onto the
 * policy engine's `ActionRisk`, defaulting to the kind's own default
 * when the protocol doesn't pin one.
 */
@Singleton
class TypedActionDescriptorMapper @Inject constructor() {

    fun toDescriptor(decoded: DecodedAction): ActionDescriptor {
        val a = decoded.action
        val kind = kindFor(a.type)
        val risk = mapRisk(a.risk, fallback = kind.defaultRisk)
        val params = paramsOf(decoded)
        val openClawSuggested = openClawSuggestedLevel(a)
        return ActionDescriptor(
            id = a.actionId,
            kind = kind,
            summary = summary(decoded),
            risk = risk,
            openClawHinted = true,
            openClawSuggestedLevel = openClawSuggested,
            params = params,
        )
    }

    private fun kindFor(type: ProtocolType): ActionKind = when (type) {
        ProtocolType.SEND_SMS -> ActionKind.SEND_SMS
        ProtocolType.SEND_WHATSAPP -> ActionKind.SEND_WHATSAPP
        ProtocolType.MAKE_CALL -> ActionKind.MAKE_CALL
        ProtocolType.OPEN_APP -> ActionKind.OPEN_APP
        ProtocolType.GET_LOCATION -> ActionKind.SHARE_SINGLE_LOCATION
        ProtocolType.CAPTURE_SCREENSHOT -> ActionKind.CAPTURE_SCREENSHOT
        ProtocolType.TAKE_PHOTO -> ActionKind.TAKE_PHOTO
        ProtocolType.SHOW_NOTIFICATION -> ActionKind.SHOW_NOTIFICATION
        ProtocolType.SPEAK -> ActionKind.SPEAK
        ProtocolType.CREATE_TIMER -> ActionKind.CREATE_TIMER
        ProtocolType.CREATE_ALARM -> ActionKind.CREATE_ALARM
    }

    private fun mapRisk(
        risk: ProtocolRisk,
        fallback: ai.openclaw.jarvis.policy.model.ActionRisk,
    ): ai.openclaw.jarvis.policy.model.ActionRisk = when (risk) {
        ProtocolRisk.safe -> ai.openclaw.jarvis.policy.model.ActionRisk.SAFE
        ProtocolRisk.limited -> ai.openclaw.jarvis.policy.model.ActionRisk.LIMITED
        ProtocolRisk.restricted -> ai.openclaw.jarvis.policy.model.ActionRisk.RESTRICTED
    }.let {
        // OpenClaw's "safe" never wins over the kind's default RESTRICTED.
        if (fallback == ai.openclaw.jarvis.policy.model.ActionRisk.RESTRICTED &&
            it != ai.openclaw.jarvis.policy.model.ActionRisk.RESTRICTED) fallback else it
    }

    private fun openClawSuggestedLevel(a: OpenClawAction): AutonomyLevel? {
        if (!a.requiresConfirmation) return null
        return AutonomyLevel.EXECUTE_WITH_CONFIRMATION
    }

    /**
     * Build a small `params` map the policy / approval UI can show. We
     * intentionally don't dump the entire payload object — only
     * "presentable" fields like contact name and a short message
     * preview, with sensitive content redacted at this layer.
     */
    private fun paramsOf(decoded: DecodedAction): Map<String, String> {
        val out = mutableMapOf<String, String>()
        when (val p = decoded.payload) {
            is PayloadSendSms -> { out["to"] = p.toContactName; out["message"] = p.message.preview() }
            is PayloadSendWhatsApp -> { out["to"] = p.toContactName; out["message"] = p.message.preview() }
            is PayloadMakeCall -> out["to"] = p.toContactName
            is PayloadOpenApp -> out["appName"] = p.appName
            is PayloadShowNotification -> { out["title"] = p.title; out["body"] = p.body.preview() }
            is PayloadSpeak -> out["text"] = p.text.preview()
            is PayloadCreateTimer -> { out["minutes"] = p.minutes.toString(); p.label?.let { out["label"] = it } }
            is PayloadCreateAlarm -> { out["minutes"] = p.minutes.toString(); p.label?.let { out["label"] = it } }
        }
        return out
    }

    private fun summary(decoded: DecodedAction): String {
        val a = decoded.action
        return when (val p = decoded.payload) {
            is PayloadSendSms -> "Send SMS to ${p.toContactName}: \"${p.message.preview()}\""
            is PayloadSendWhatsApp -> "Send WhatsApp to ${p.toContactName}: \"${p.message.preview()}\""
            is PayloadMakeCall -> "Call ${p.toContactName}"
            is PayloadOpenApp -> "Open ${p.appName}"
            is PayloadShowNotification -> "Show notification: ${p.title}"
            is PayloadSpeak -> "Say: ${p.text.preview()}"
            is PayloadCreateTimer -> "Set timer for ${p.minutes} min${p.label?.let { " ($it)" } ?: ""}"
            is PayloadCreateAlarm -> "Set alarm in ${p.minutes} min${p.label?.let { " ($it)" } ?: ""}"
            else -> a.reason ?: a.type.name
        }
    }

    private fun String.preview(): String = if (length <= 80) this else take(77) + "..."
}
