package ai.openclaw.jarvis.proactive.engine

import ai.openclaw.jarvis.proactive.model.BtDevice
import ai.openclaw.jarvis.proactive.model.ContextSnapshot
import ai.openclaw.jarvis.proactive.model.LocationLabel
import ai.openclaw.jarvis.proactive.model.Movement
import ai.openclaw.jarvis.proactive.model.Signal
import ai.openclaw.jarvis.proactive.model.SignalType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects discrete signals from the difference between two consecutive
 * [ContextSnapshot]s. Stateful (it holds the previous snapshot) but
 * dependency-free — no I/O, no flows. The collector pushes snapshots
 * into [process], the engine returns the signals that fired.
 *
 * The rules are intentionally simple thresholds, not ML:
 *   - location label transitions for arrived/left
 *   - movement transitions for driving started/stopped
 *   - audio-route transitions for headphone connect/disconnect
 *   - "screenshot taken" when recentScreenshotMillis advances
 *   - "repeated command pattern" when the last 3 commands are identical
 *   - "calendar event approaching" when next event ≤ 15 minutes away
 *   - "idle period" when idleSinceMillis crosses 30 minutes
 *   - morning / evening when the hour first crosses 6 / 19
 *   - low battery when crossing 20% on the way down (and not charging)
 */
@Singleton
class SignalEngine @Inject constructor() {

    @Volatile private var previous: ContextSnapshot? = null
    @Volatile private var lastHourEmitted: Int = -1
    @Volatile private var lowBatteryFiredFor: Int = -1
    @Volatile private var lastScreenshotEmittedAt: Long? = null

    fun process(current: ContextSnapshot): List<Signal> {
        val prev = previous
        previous = current
        if (prev == null) {
            // First snapshot: emit only the time-of-day baseline.
            return timeOfDaySignals(current)
        }
        val out = mutableListOf<Signal>()

        // Location transitions
        if (prev.locationLabel != current.locationLabel) {
            when {
                prev.locationLabel == LocationLabel.HOME && current.locationLabel != LocationLabel.HOME ->
                    out += Signal(SignalType.LEFT_HOME, current.timestampMillis)
                current.locationLabel == LocationLabel.HOME && prev.locationLabel != LocationLabel.HOME ->
                    out += Signal(SignalType.ARRIVED_HOME, current.timestampMillis)
                current.locationLabel == LocationLabel.WORK ->
                    out += Signal(SignalType.ARRIVED_WORK, current.timestampMillis)
                else -> Unit
            }
        }

        // Movement transitions
        if (prev.movement != current.movement) {
            when (current.movement) {
                Movement.DRIVING -> out += Signal(SignalType.DRIVING_STARTED, current.timestampMillis)
                Movement.STATIONARY -> if (prev.movement == Movement.DRIVING)
                    out += Signal(SignalType.DRIVING_STOPPED, current.timestampMillis)
                else -> Unit
            }
        }

        // Headphones
        if (prev.headphonesConnected != current.headphonesConnected) {
            out += Signal(
                if (current.headphonesConnected) SignalType.HEADPHONES_CONNECTED
                else SignalType.HEADPHONES_DISCONNECTED,
                current.timestampMillis,
                payload = mapOf("device" to current.bluetoothDevice.name),
            )
        }

        // Screenshot taken
        val screenshotAt = current.recentScreenshotMillis
        if (screenshotAt != null && screenshotAt != lastScreenshotEmittedAt) {
            lastScreenshotEmittedAt = screenshotAt
            out += Signal(SignalType.SCREENSHOT_TAKEN, screenshotAt)
        }

        // Repeated commands — last 3 identical (ignoring case + whitespace).
        val cmds = current.recentCommands
        if (cmds.size >= 3) {
            val tail = cmds.takeLast(3).map { it.trim().lowercase() }
            if (tail[0] == tail[1] && tail[1] == tail[2]) {
                out += Signal(
                    SignalType.REPEATED_COMMAND_PATTERN,
                    current.timestampMillis,
                    payload = mapOf("command" to tail[0]),
                )
            }
        }

        // Frequent app — same foreground app appears in 3+ of last 5 actions.
        current.foregroundApp?.let { app ->
            val recent = current.recentActions.takeLast(5).count { it.contains(app, ignoreCase = true) }
            if (recent >= 3) {
                out += Signal(
                    SignalType.APP_OPENED_FREQUENTLY,
                    current.timestampMillis,
                    payload = mapOf("app" to app),
                )
            }
        }

        // Calendar approaching
        current.nextCalendarEventMinutes?.let { mins ->
            if (mins in 0..15)
                out += Signal(
                    SignalType.CALENDAR_EVENT_APPROACHING,
                    current.timestampMillis,
                    payload = mapOf(
                        "minutes" to mins.toString(),
                        "title" to (current.nextCalendarEventTitle ?: ""),
                    ),
                )
        }

        // Idle
        if (current.idleSinceMillis != null && prev.idleSinceMillis == null) {
            out += Signal(SignalType.IDLE_PERIOD, current.timestampMillis)
        }

        // Time-of-day morning/evening (once per hour transition)
        val hour = current.hourOfDay
        if (hour != lastHourEmitted) {
            lastHourEmitted = hour
            if (hour == 7 || hour == 8) out += Signal(SignalType.MORNING, current.timestampMillis)
            if (hour == 19 || hour == 20) out += Signal(SignalType.EVENING, current.timestampMillis)
        }

        // Low battery — fire once per crossing under 20% while not charging
        val pct = current.batteryPercent
        if (pct in 0..19 && !current.charging && lowBatteryFiredFor != pct) {
            lowBatteryFiredFor = pct
            out += Signal(SignalType.LOW_BATTERY, current.timestampMillis,
                payload = mapOf("percent" to pct.toString()))
        }
        if (pct >= 30 || current.charging) lowBatteryFiredFor = -1

        return out
    }

    private fun timeOfDaySignals(snap: ContextSnapshot): List<Signal> {
        val signals = mutableListOf<Signal>()
        if (snap.hourOfDay == 7 || snap.hourOfDay == 8) {
            lastHourEmitted = snap.hourOfDay
            signals += Signal(SignalType.MORNING, snap.timestampMillis)
        }
        if (snap.hourOfDay == 19 || snap.hourOfDay == 20) {
            lastHourEmitted = snap.hourOfDay
            signals += Signal(SignalType.EVENING, snap.timestampMillis)
        }
        return signals
    }

    /** Test seam — clears state so a fresh process() acts like the first call. */
    fun reset() {
        previous = null
        lastHourEmitted = -1
        lowBatteryFiredFor = -1
        lastScreenshotEmittedAt = null
    }
}
