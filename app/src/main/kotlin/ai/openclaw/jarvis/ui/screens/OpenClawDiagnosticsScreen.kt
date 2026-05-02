package ai.openclaw.jarvis.ui.screens

import ai.openclaw.jarvis.data.models.GatewayState
import ai.openclaw.jarvis.network.ConnectionFailure
import ai.openclaw.jarvis.network.DiagnosticEvent
import ai.openclaw.jarvis.network.DiagnosticLevel
import ai.openclaw.jarvis.network.TestStep
import ai.openclaw.jarvis.network.TestStepState
import ai.openclaw.jarvis.ui.theme.*
import ai.openclaw.jarvis.ui.viewmodel.ConnectionDiagnosticsViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenClawDiagnosticsScreen(
    onBack: () -> Unit,
    onEditConnection: () -> Unit,
    viewModel: ConnectionDiagnosticsViewModel = hiltViewModel(),
) {
    val settings      by viewModel.settings.collectAsStateWithLifecycle()
    val gatewayState  by viewModel.gatewayState.collectAsStateWithLifecycle()
    val lastFailure   by viewModel.lastFailure.collectAsStateWithLifecycle()
    val diagLog       by viewModel.diagLog.collectAsStateWithLifecycle()
    val normalizedUrl by viewModel.normalizedUrl.collectAsStateWithLifecycle()
    val testStatus    by viewModel.testStatus.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = BlueprintBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text("OpenClaw Diagnostics",
                        color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BlueprintBackground),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Connection config ─────────────────────────────────────────────
            DiagSection("Connection Config") {
                DiagRow("Gateway URL (entered)", settings.gatewayUrl.ifBlank { "— not set —" })
                DiagRow("Normalized URL", normalizedUrl.ifBlank { "—" })
                DiagRow("Connection mode", if (settings.gatewayUrl.startsWith("wss")) "wss (TLS)" else "ws (plaintext)")
                DiagRow("Device ID (Ed25519)", viewModel.deviceId.take(16) + "…")
                DiagRow("Device token", if (viewModel.pairingToken != null) "Present" else "Not yet issued")
                DiagRow("Node approved", if (viewModel.isApproved) "Yes" else "No — run approve command")
                DiagRow("Node ID", viewModel.nodeId ?: "Not assigned yet")
                DiagRow("Device name", settings.deviceName)
                DiagRow("Session key", settings.sessionKey)
                DiagRow("Gateway enabled", if (settings.gatewayEnabled) "Yes" else "No")
            }

            // ── Pairing instructions ──────────────────────────────────────────
            if (!viewModel.isApproved) {
                DiagSection("Pairing — Action Required") {
                    if (viewModel.pairRequestId != null) {
                        Text(
                            "Your device has sent a pairing request to the gateway. " +
                            "Run the following command on your OpenClaw server to approve it:",
                            color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "openclaw nodes approve ${viewModel.pairRequestId}",
                            color = CobaltBright,
                            fontSize = 13.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                    } else {
                        Text(
                            "Connect to the gateway to initiate pairing. " +
                            "A pairing request will be sent automatically after the Ed25519 " +
                            "handshake succeeds. The request ID will appear here.",
                            color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                        )
                    }
                }
            }

            // ── Connection state ──────────────────────────────────────────────
            DiagSection("Connection State") {
                val (stateLabel, stateColor) = gatewayStateInfo(gatewayState)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(stateColor)
                    )
                    Text(stateLabel, color = stateColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Last failure ──────────────────────────────────────────────────
            if (lastFailure != null) {
                DiagSection("Last Failure") {
                    FailureCard(lastFailure!!)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = viewModel::retryConnection,
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = CobaltBright),
                            border  = androidx.compose.foundation.BorderStroke(1.dp, CobaltBright.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f),
                        ) { Text("Retry", fontSize = 13.sp) }
                        OutlinedButton(
                            onClick = onEditConnection,
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            border  = androidx.compose.foundation.BorderStroke(1.dp, BlueprintBorder),
                            modifier = Modifier.weight(1f),
                        ) { Text("Edit URL", fontSize = 13.sp) }
                    }
                }
            }

            // ── Connection test ───────────────────────────────────────────────
            DiagSection("Connection Test") {
                Text(
                    "Tests URL, TCP reachability, HTTP health, WebSocket handshake, and protocol.",
                    color = TextDim, fontSize = 12.sp, lineHeight = 16.sp,
                )
                Spacer(Modifier.height(8.dp))

                when (val ts = testStatus) {
                    is ConnectionDiagnosticsViewModel.TestStatus.Idle -> {
                        Button(
                            onClick = { viewModel.runConnectionTest(settings.gatewayUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = CobaltBright),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Run Connection Test") }
                    }
                    is ConnectionDiagnosticsViewModel.TestStatus.Running -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = CobaltBright,
                                strokeWidth = 2.dp,
                            )
                            Text("Running tests…", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                    is ConnectionDiagnosticsViewModel.TestStatus.Done -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ts.steps.forEach { step -> TestStepRow(step) }
                            if (ts.workingPath != null && ts.workingPath != settings.gatewayUrl.substringAfter(":").substringAfter("/").let { "/$it".replace("//", "/") }) {
                                Spacer(Modifier.height(4.dp))
                                InfoBox("Working WebSocket path found: ${ts.workingPath}", StatusConnected)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = viewModel::resetTest,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BlueprintBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Clear", fontSize = 13.sp) }
                    }
                }
            }

            // ── URL format help ───────────────────────────────────────────────
            DiagSection("URL Format Help") {
                Text(
                    "Enter the full WebSocket URL to your OpenClaw gateway.",
                    color = TextDim, fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                UrlExample("Local / LAN", "ws://192.168.1.50:8765", "ws://192.168.1.50:8765/ws")
                Spacer(Modifier.height(4.dp))
                UrlExample("Tailscale", "ws://100.x.x.x:8765", "ws://your-machine.tailnet.ts.net:8765/ws")
                Spacer(Modifier.height(4.dp))
                UrlExample("HTTPS/TLS", "wss://your-domain.com:8765/ws", null)
                Spacer(Modifier.height(8.dp))
                Text(
                    "The path (/ws, /gateway/ws, etc.) depends on how OpenClaw is configured. " +
                    "Use the Connection Test above to find the right path automatically.",
                    color = TextDim, fontSize = 12.sp, lineHeight = 16.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "For Tailscale: use ws:// not wss:// unless you have a valid certificate. " +
                    "TLS errors are the most common cause of Tailscale connection failures.",
                    color = StatusWarning.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp,
                )
            }

            // ── Diagnostic log ────────────────────────────────────────────────
            if (diagLog.isNotEmpty()) {
                DiagSection("Connection Log (latest first)") {
                    diagLog.take(30).forEach { event ->
                        DiagLogRow(event)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Section wrapper ──────────────────────────────────────────────────────────

@Composable
private fun DiagSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BlueprintSurface)
            .border(1.dp, BlueprintBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title.uppercase(),
            color = CobaltBright,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(2.dp))
        content()
    }
}

// ─── Row types ────────────────────────────────────────────────────────────────

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = TextDim, fontSize = 12.sp, modifier = Modifier.weight(0.45f))
        Text(
            value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.55f),
        )
    }
}

@Composable
private fun FailureCard(failure: ConnectionFailure) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A0A0A))
            .border(1.dp, StatusOffline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(failure.reason, color = StatusOffline, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(failure.message, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        if (failure.closeCode != null) {
            Text(
                "Close code: ${failure.closeCode}" + if (!failure.closeReason.isNullOrBlank()) "  \"${failure.closeReason}\"" else "",
                color = TextDim, fontSize = 11.sp,
            )
        }
        Text(
            "Stage: ${failure.stage}  •  ${failure.errorType}",
            color = TextDim, fontSize = 11.sp,
        )
        val fix = failure.suggestedFix()
        if (fix.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("→ $fix", color = StatusWarning, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Text(
            formatTimestamp(failure.timestamp),
            color = TextDim, fontSize = 11.sp,
        )
    }
}

@Composable
private fun TestStepRow(step: TestStep) {
    val (icon, color) = when (step.state) {
        TestStepState.PENDING  -> "○" to TextDim
        TestStepState.RUNNING  -> "◌" to CobaltBright
        TestStepState.PASS     -> "✓" to StatusConnected
        TestStepState.FAIL     -> "✗" to StatusOffline
        TestStepState.SKIPPED  -> "–" to TextDim
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (step.state == TestStepState.FAIL) Color(0xFF160808) else BlueprintBackground)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(icon, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(step.name, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        if (step.detail.isNotBlank()) {
            Text(
                step.detail,
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 20.dp),
            )
        }
        if (step.suggestion.isNotBlank()) {
            Text(
                "→ ${step.suggestion}",
                color = StatusWarning,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(start = 20.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun DiagLogRow(event: DiagnosticEvent) {
    val color = when (event.level) {
        DiagnosticLevel.SUCCESS -> StatusConnected
        DiagnosticLevel.ERROR   -> StatusOffline
        DiagnosticLevel.WARN    -> StatusWarning
        DiagnosticLevel.INFO    -> TextDim
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            formatTimestamp(event.timestamp),
            color = TextDim, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            event.message,
            color = color, fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun UrlExample(label: String, vararg examples: String?) {
    Column {
        Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        examples.filterNotNull().forEach { ex ->
            Text(
                ex,
                color = CobaltBright.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun InfoBox(text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(10.dp),
    ) {
        Text(text, color = color, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun gatewayStateInfo(state: GatewayState): Pair<String, Color> = when (state) {
    GatewayState.CONNECTED     -> "Connected" to StatusConnected
    GatewayState.CONNECTING    -> "Connecting…" to StatusWarning
    GatewayState.PAIRING       -> "Pairing…" to StatusWarning
    GatewayState.OFFLINE_QUEUED -> "Offline (queued)" to StatusWarning
    GatewayState.DISCONNECTED  -> "Disconnected" to StatusOffline
}

private val tsFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
private fun formatTimestamp(ts: Long): String = tsFormat.format(Date(ts))
