package ai.openclaw.jarvis.policy.model

/**
 * The single ladder of autonomy Jarvis ever uses.
 *
 *   0 OBSERVE                  collect context, log signals, don't bother the user
 *   1 SUGGEST                  surface a suggestion / chip; never act
 *   2 PREPARE                  draft / stage the action, wait for explicit approval
 *   3 EXECUTE_TRUSTED          run immediately for low-risk local actions only
 *   4 EXECUTE_WITH_CONFIRMATION ask first, then run
 *   5 BLOCKED                  refuse to even prepare without manual override
 *
 * Higher number = more disruptive. The [AutonomyPolicyEngine] never returns
 * a higher level than the user's autonomy mode + per-action overrides allow.
 */
enum class AutonomyLevel(val rank: Int) {
    OBSERVE(0),
    SUGGEST(1),
    PREPARE(2),
    EXECUTE_TRUSTED(3),
    EXECUTE_WITH_CONFIRMATION(4),
    BLOCKED(5);

    fun atLeast(other: AutonomyLevel): Boolean = rank >= other.rank
}
