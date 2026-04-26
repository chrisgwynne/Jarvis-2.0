package ai.openclaw.jarvis.proactive.model

/**
 * A proactive suggestion built from one or more [Signal]s.
 *
 * `id` is a stable string used for the "don't suggest this again" memory
 * — same signal in the same context produces the same id, so the user's
 * dismissal sticks across reboots.
 *
 * `format` decides how Jarvis presents the suggestion. Per the spec:
 *   - VOICE: short spoken question, awaits yes/no
 *   - NOTIFICATION: low-priority Android notification
 *   - SILENT_CHIP: passive UI affordance only
 *
 * Suggestions never carry an action that auto-executes — the suggestion
 * is the *invitation* to act; the action runs only if the user says yes
 * (or taps the chip).
 */
data class Suggestion(
    val id: String,
    val signalType: SignalType,
    val format: SuggestionFormat,
    val voicePrompt: String,
    val title: String,
    val body: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val proposedAction: ProposedAction? = null,
)

enum class SuggestionFormat { VOICE, NOTIFICATION, SILENT_CHIP }

/**
 * Optional structured shadow of the action a confirmed suggestion would
 * trigger. Mapped through to OpenClaw / the contract executor by the
 * acceptance handler — never executed automatically.
 */
data class ProposedAction(
    val kind: Kind,
    val payload: Map<String, String> = emptyMap(),
) {
    enum class Kind {
        SEND_MESSAGE,            // SMS / WhatsApp — must confirm
        MAKE_CALL,               // must confirm
        SHARE_LOCATION,          // must confirm
        CREATE_TASK,             // must confirm
        ANALYSE_LAST_SCREENSHOT, // routes to OpenClaw
        ENABLE_VOICE_MODE,       // local toggle
        ENABLE_POWER_SAVE,       // local toggle
        SHOW_PLAN,               // local panel
        WRAP_UP_DAY,             // local panel
        OPEN_APP_SHORTCUT,       // local
    }
}
