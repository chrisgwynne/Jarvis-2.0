package ai.openclaw.jarvis.policy.model

/**
 * Source-agnostic description of an action that's about to be evaluated.
 *
 * Built by adapters that turn an [ai.openclaw.jarvis.protocol.model.OpenClawAction]
 * or a local intent / proactive suggestion into something the policy
 * engine can reason about without coupling to either source format.
 *
 * `kind` is the policy-side enum (so OpenClaw can extend its action
 * vocabulary without changing the policy ladder); `id` is a stable id
 * used for the pending-approval store.
 */
data class ActionDescriptor(
    val id: String,
    val kind: ActionKind,
    val summary: String,
    val risk: ActionRisk = kind.defaultRisk,
    val openClawHinted: Boolean = false,
    val openClawSuggestedLevel: AutonomyLevel? = null,
    val params: Map<String, String> = emptyMap(),
)

/**
 * The complete vocabulary the policy engine understands. New action
 * types added here are picked up automatically by the rule book in
 * [ai.openclaw.jarvis.policy.engine.AutonomyPolicyEngine] via
 * [defaultRisk].
 */
enum class ActionKind(val defaultRisk: ActionRisk) {
    // SAFE — local, undoable, no external recipient
    OPEN_APP(ActionRisk.SAFE),
    TORCH(ActionRisk.SAFE),
    CREATE_TIMER(ActionRisk.SAFE),
    CREATE_LOCAL_REMINDER(ActionRisk.SAFE),
    SHOW_NOTIFICATION(ActionRisk.SAFE),
    SUMMARISE_SCREEN(ActionRisk.SAFE),
    SHOW_PLAN(ActionRisk.SAFE),
    SPEAK(ActionRisk.SAFE),
    ENABLE_VOICE_MODE(ActionRisk.SAFE),
    ENABLE_POWER_SAVE(ActionRisk.SAFE),

    // LIMITED — small external footprint
    OPEN_CONTACT_THREAD(ActionRisk.LIMITED),
    SHARE_SINGLE_LOCATION(ActionRisk.LIMITED),
    CAPTURE_SCREENSHOT(ActionRisk.LIMITED),
    TAKE_PHOTO(ActionRisk.LIMITED),

    // HIGH — confirm-first
    SEND_SMS(ActionRisk.HIGH),
    SEND_WHATSAPP(ActionRisk.HIGH),
    MAKE_CALL(ActionRisk.HIGH),
    SHARE_LOCATION_LIVE(ActionRisk.HIGH),
    CREATE_CALENDAR_EVENT(ActionRisk.HIGH),
    CREATE_OPENCLAW_TASK(ActionRisk.HIGH),
    SEND_EMAIL_DRAFT(ActionRisk.HIGH),
    CREATE_ALARM(ActionRisk.HIGH),

    // RESTRICTED — never automatic
    DELETE_FILES(ActionRisk.RESTRICTED),
    SPEND_MONEY(ActionRisk.RESTRICTED),
    POST_PUBLICLY(ActionRisk.RESTRICTED),
    SEND_BUSINESS_EMAIL(ActionRisk.RESTRICTED),
    CHANGE_SECURITY_SETTINGS(ActionRisk.RESTRICTED),
    DESTRUCTIVE_OPENCLAW(ActionRisk.RESTRICTED),
}
