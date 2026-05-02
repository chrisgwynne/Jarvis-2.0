package ai.openclaw.jarvis.network

data class ConnectionFailure(
    val stage: String,
    val reason: String,
    val errorType: String,
    val message: String,
    val closeCode: Short? = null,
    val closeReason: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
) {
    fun humanReadable(): String = buildString {
        append(reason)
        if (closeCode != null) {
            append(" (close $closeCode")
            if (!closeReason.isNullOrBlank()) append(": $closeReason")
            append(")")
        }
        if (errorType.isNotBlank() && errorType != "Exception" && errorType != reason) {
            append(" [$errorType]")
        }
    }

    fun suggestedFix(): String = when {
        closeCode == 1008.toShort() -> "Server rejected the connection (policy violation). Check pairing token or auth."
        closeCode == 1003.toShort() -> "Server rejected the frame format. Protocol mismatch."
        closeCode == 4001.toShort() -> "Auth rejected. Check pairing token."
        stage == "TCP" && message.contains("refused", ignoreCase = true) ->
            "Port is closed. Is OpenClaw running? Check the port number."
        stage == "TCP" && (message.contains("timeout", ignoreCase = true) || message.contains("timed out", ignoreCase = true)) ->
            "Host not responding. Check Tailscale is connected and the host is reachable."
        stage == "TCP" && message.contains("unreachable", ignoreCase = true) ->
            "Network unreachable. Check Tailscale is active."
        stage == "WebSocket" && message.contains("404", ignoreCase = true) ->
            "Wrong path. Try /ws, /gateway/ws, or check OpenClaw docs for the WebSocket path."
        stage == "WebSocket" && message.contains("403", ignoreCase = true) ->
            "Forbidden. Check auth token or pairing token."
        stage == "TLS" ->
            "TLS/certificate error. Try ws:// instead of wss:// for local Tailscale connections."
        else -> "Check connection settings and ensure OpenClaw is running."
    }
}

data class DiagnosticEvent(
    val level: DiagnosticLevel,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class DiagnosticLevel { INFO, SUCCESS, WARN, ERROR }

enum class TestStepState { PENDING, RUNNING, PASS, FAIL, SKIPPED }

data class TestStep(
    val name: String,
    val state: TestStepState = TestStepState.PENDING,
    val detail: String = "",
    val suggestion: String = "",
)
