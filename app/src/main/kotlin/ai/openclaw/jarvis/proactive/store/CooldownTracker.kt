package ai.openclaw.jarvis.proactive.store

import ai.openclaw.jarvis.proactive.model.Aggressiveness
import ai.openclaw.jarvis.proactive.model.SignalType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Throttles proactive suggestions per the spec's fatigue rules:
 *   - per-signal cooldown (default 30 mins, tuned by Aggressiveness)
 *   - global cap of N suggestions per rolling hour
 *   - dismiss tracking (last-dismissed timestamp per id)
 *
 * Pure in-memory: the per-signal "last shown" map and the global window
 * are reset on app restart, which is the desired behaviour — fatigue
 * accumulates within a session, not across days.
 */
@Singleton
class CooldownTracker @Inject constructor() {

    private val perSignalLastShown = mutableMapOf<SignalType, Long>()
    private val recentShowTimestamps = ArrayDeque<Long>()
    private val perIdLastDismissed = mutableMapOf<String, Long>()
    private val lock = Any()

    /**
     * Should we show a suggestion of [signalType] right now?
     *
     * Returns true if BOTH the per-signal cooldown AND the global hourly
     * cap allow it. Caller must invoke [recordShown] when the suggestion
     * actually surfaces — [allow] is a query, not a side-effect.
     */
    fun allow(
        signalType: SignalType,
        aggressiveness: Aggressiveness,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(lock) {
        // Trim any timestamps older than 1h.
        val cutoff = nowMillis - 60L * 60 * 1000
        while (recentShowTimestamps.isNotEmpty() && recentShowTimestamps.first() < cutoff) {
            recentShowTimestamps.removeFirst()
        }
        if (recentShowTimestamps.size >= aggressiveness.maxSuggestionsPerHour()) return false

        val last = perSignalLastShown[signalType] ?: return true
        return nowMillis - last >= aggressiveness.perSignalCooldownMillis()
    }

    fun recordShown(signalType: SignalType, nowMillis: Long = System.currentTimeMillis()) =
        synchronized(lock) {
            perSignalLastShown[signalType] = nowMillis
            recentShowTimestamps.addLast(nowMillis)
        }

    fun recordDismissed(suggestionId: String, nowMillis: Long = System.currentTimeMillis()) =
        synchronized(lock) { perIdLastDismissed[suggestionId] = nowMillis }

    /** When did the user last dismiss this exact suggestion id? null = never. */
    fun lastDismissedAt(suggestionId: String): Long? =
        synchronized(lock) { perIdLastDismissed[suggestionId] }

    /** Test seam — clears all cooldowns. */
    fun reset() = synchronized(lock) {
        perSignalLastShown.clear()
        recentShowTimestamps.clear()
        perIdLastDismissed.clear()
    }
}
