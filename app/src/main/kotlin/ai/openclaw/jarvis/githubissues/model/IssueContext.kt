package ai.openclaw.jarvis.githubissues.model

/**
 * Snapshot of Jarvis runtime state at the moment a failure occurred.
 *
 * All fields are nullable so the failure-reporting path is robust against
 * partial state (e.g. an early-startup crash where there is no current
 * intent yet).
 */
data class IssueContext(
    val state: StateSnapshot = StateSnapshot(),
    val device: DeviceSnapshot = DeviceSnapshot(),
    val capability: CapabilitySnapshot = CapabilitySnapshot(),
    val session: SessionSnapshot = SessionSnapshot(),
    val error: ErrorSnapshot? = null,
    val userCommand: String? = null,
    val expectedBehaviour: String? = null,
    val actualBehaviour: String? = null
)

data class StateSnapshot(
    val current: String? = null,
    val previous: String? = null,
    val route: String? = null,
    val intent: String? = null,
    val speakerTrustLevel: String? = null,
    val audioRoute: String? = null,
    val openClawConnected: Boolean? = null
)

data class DeviceSnapshot(
    val androidVersion: String? = null,
    val appVersion: String? = null,
    val deviceModel: String? = null,
    val batteryState: String? = null,
    val networkStatus: String? = null
)

data class CapabilitySnapshot(
    val available: Map<String, Boolean> = emptyMap(),
    val notes: String? = null
)

data class SessionSnapshot(
    val commandId: String? = null,
    val sessionId: String? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)

/**
 * Structured error info. Stack trace is collected only when
 * `includeDebugContext` is true in settings.
 */
data class ErrorSnapshot(
    val errorCode: String? = null,
    val message: String? = null,
    val stackTrace: String? = null,
    val cause: String? = null
)
