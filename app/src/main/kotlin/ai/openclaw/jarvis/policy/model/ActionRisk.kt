package ai.openclaw.jarvis.policy.model

/**
 * Coarse risk class used by the policy engine to map ActionKind →
 * baseline AutonomyLevel before user / context modifiers apply.
 *
 *   SAFE        local-only, no external side effects, easy to undo
 *   LIMITED     external-but-reversible (open app, share location once)
 *   HIGH        external + hard to undo (send SMS / WhatsApp / call,
 *               create calendar event, draft+send email)
 *   RESTRICTED  destructive / financial / public — never automatic,
 *               always blocked without explicit manual approval
 */
enum class ActionRisk { SAFE, LIMITED, HIGH, RESTRICTED }
