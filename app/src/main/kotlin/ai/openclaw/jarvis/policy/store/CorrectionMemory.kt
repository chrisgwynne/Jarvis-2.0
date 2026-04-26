package ai.openclaw.jarvis.policy.store

import ai.openclaw.jarvis.policy.model.ActionKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks "the user just told me they didn't want this kind of action".
 *
 * Used as a hot input to the policy engine — when an action kind is in
 * the recent-corrections set, the engine forces EXECUTE_WITH_CONFIRMATION
 * (or PREPARE) regardless of mode for [WINDOW_MS] afterwards.
 *
 * Pure in-memory: a session-scoped cool-down, intentionally not persisted.
 * Long-term per-action distrust belongs in `perActionOverrides` instead.
 */
@Singleton
class CorrectionMemory @Inject constructor() {
    private val lastCorrection = mutableMapOf<ActionKind, Long>()
    private val lock = Any()

    fun recordCorrection(kind: ActionKind, nowMillis: Long = System.currentTimeMillis()) =
        synchronized(lock) { lastCorrection[kind] = nowMillis }

    fun isRecent(kind: ActionKind, nowMillis: Long = System.currentTimeMillis()): Boolean =
        synchronized(lock) {
            val last = lastCorrection[kind] ?: return false
            (nowMillis - last) < WINDOW_MS
        }

    fun reset() = synchronized(lock) { lastCorrection.clear() }

    companion object {
        private const val WINDOW_MS = 10L * 60 * 1000   // 10 minutes
    }
}
