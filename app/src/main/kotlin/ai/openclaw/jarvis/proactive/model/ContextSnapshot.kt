package ai.openclaw.jarvis.proactive.model

/**
 * Snapshot of every signal Jarvis observes about the world at a moment in
 * time. Cheap to build (no I/O) — produced by [ai.openclaw.jarvis.proactive.ContextCollector]
 * on a fixed cadence and on every state-machine transition.
 *
 * All fields are nullable / have safe defaults so a partial snapshot
 * (e.g. no location permission) still flows through the signal engine.
 */
data class ContextSnapshot(
    val timestampMillis: Long = System.currentTimeMillis(),
    // Time
    val hourOfDay: Int = 0,                 // 0..23
    val dayOfWeek: Int = 1,                 // 1=MONDAY..7=SUNDAY
    // Location (label-based — never raw lat/long)
    val locationLabel: LocationLabel = LocationLabel.UNKNOWN,
    // Movement
    val movement: Movement = Movement.UNKNOWN,
    // Audio + accessories
    val bluetoothDevice: BtDevice = BtDevice.NONE,
    val headphonesConnected: Boolean = false,
    // Foreground / behaviour
    val foregroundApp: String? = null,
    val recentCommands: List<String> = emptyList(),
    val recentActions: List<String> = emptyList(),
    val recentScreenshotMillis: Long? = null,
    // Calendar
    val nextCalendarEventMinutes: Int? = null,    // minutes until the next event
    val nextCalendarEventTitle: String? = null,
    // Device
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val network: NetworkState = NetworkState.UNKNOWN,
    val screenOn: Boolean = true,
    // Idle
    val idleSinceMillis: Long? = null,            // last user interaction
    // Speaker / trust
    val speakerTrustLevel: String = "UNKNOWN",
)

enum class LocationLabel { HOME, WORK, AWAY, UNKNOWN }
enum class Movement { STATIONARY, WALKING, DRIVING, UNKNOWN }
enum class BtDevice { NONE, EARBUDS, CAR, OTHER }
enum class NetworkState { WIFI, MOBILE, OFFLINE, UNKNOWN }
