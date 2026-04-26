package ai.openclaw.jarvis.ui.screens

import ai.openclaw.jarvis.awareness.AvailabilityState
import ai.openclaw.jarvis.awareness.LocalAction
import ai.openclaw.jarvis.data.models.GatewayState
import ai.openclaw.jarvis.ui.components.BlueprintCard
import ai.openclaw.jarvis.ui.components.ChipStatus
import ai.openclaw.jarvis.ui.components.ConnectionDot
import ai.openclaw.jarvis.ui.components.OutlineButton
import ai.openclaw.jarvis.ui.components.SectionHeader
import ai.openclaw.jarvis.ui.components.StatusChip
import ai.openclaw.jarvis.ui.theme.BlueprintBackground
import ai.openclaw.jarvis.ui.theme.CobaltBright
import ai.openclaw.jarvis.ui.theme.TextDim
import ai.openclaw.jarvis.ui.theme.TextPrimary
import ai.openclaw.jarvis.ui.theme.TextSecondary
import ai.openclaw.jarvis.ui.viewmodel.CapabilityDashboardViewModel
import ai.openclaw.jarvis.ui.viewmodel.MainViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Power-user system overview. Six sections per the spec:
 *   1. Capability overview
 *   2. OpenClaw status
 *   3. Speaker & trust
 *   4. Audio route
 *   5. Pending queues
 *   6. Settings shortcuts
 *
 * Pulls every value from existing view-models / managers — no new
 * data sources introduced. Settings-shortcut row taps call into
 * [onOpenSettings] which the host screen maps to the Settings nav
 * route.
 */
@Composable
fun SystemScreen(
    onOpenSettings: () -> Unit,
    onOpenApprovals: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenProtocol: () -> Unit,
    main: MainViewModel = hiltViewModel(),
    capability: CapabilityDashboardViewModel = hiltViewModel(),
) {
    val gateway by main.gatewayState.collectAsStateWithLifecycle()
    val trust by main.sessionTrust.collectAsStateWithLifecycle()
    val queueSize by main.queueSize.collectAsStateWithLifecycle()
    val snap by capability.snapshot.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlueprintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("System", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

        // ── Capability overview ──────────────────────────────────────
        BlueprintCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionHeader("Capabilities")
                Spacer(Modifier.height(4.dp))
                snap.androidActions.forEach { row -> CapabilityRow(row) }
            }
        }

        // ── OpenClaw ──────────────────────────────────────────────────
        BlueprintCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionHeader("OpenClaw")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConnectionDot(connected = gateway == GatewayState.CONNECTED)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (gateway == GatewayState.CONNECTED) "Connected" else "Offline",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (gateway == GatewayState.CONNECTED) TextPrimary else TextDim,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Skills: ${snap.openClawSkills.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }

        // ── Speaker & trust ──────────────────────────────────────────
        BlueprintCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionHeader("Speaker & trust")
                Text(
                    text = trust?.speakerId ?: "guest",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(
                        label = (trust?.trustLevel?.name ?: "UNKNOWN").uppercase(),
                        status = when (trust?.trustLevel?.name) {
                            "OWNER", "TRUSTED" -> ChipStatus.SUCCESS
                            "GUEST" -> ChipStatus.WARNING
                            else -> ChipStatus.NEUTRAL
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Confidence: ${"%.0f".format((trust?.confidence ?: 0f) * 100)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDim,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlineButton(
                    "Manage identity",
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ── Audio route + Bluetooth ──────────────────────────────────
        BlueprintCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionHeader("Audio route")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Bluetooth output",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    StatusChip(
                        if (snap.bluetoothOutputConnected) "ACTIVE" else "OFF",
                        if (snap.bluetoothOutputConnected) ChipStatus.SUCCESS else ChipStatus.NEUTRAL,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Bluetooth mic",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    StatusChip(
                        if (snap.bluetoothMicConnected) "ACTIVE" else "OFF",
                        if (snap.bluetoothMicConnected) ChipStatus.SUCCESS else ChipStatus.NEUTRAL,
                    )
                }
            }
        }

        // ── Pending queues ───────────────────────────────────────────
        BlueprintCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionHeader("Pending queues")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Offline session events",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    StatusChip(
                        label = queueSize.toString(),
                        status = if (queueSize > 0) ChipStatus.WARNING else ChipStatus.NEUTRAL,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlineButton(
                    "Pending approvals",
                    onClick = onOpenApprovals,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ── Settings shortcuts ───────────────────────────────────────
        BlueprintCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionHeader("Settings")
                ShortcutRow("OpenClaw", onOpenSettings)
                ShortcutRow("Voice & audio", onOpenSettings)
                ShortcutRow("Identity & trust", onOpenSettings)
                ShortcutRow("Permissions", onOpenSettings)
                ShortcutRow("Android capabilities", onOpenSettings)
                ShortcutRow("Proactive suggestions", onOpenSettings)
                ShortcutRow("Screen awareness", onOpenSettings)
                ShortcutRow("Autonomy & approvals", onOpenSettings)
                ShortcutRow("GitHub issue logging", onOpenSettings)
                ShortcutRow("Privacy", onOpenSettings)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton("Debug", onClick = onOpenDebug, modifier = Modifier.weight(1f))
                    OutlineButton("Protocol", onClick = onOpenProtocol, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CapabilityRow(row: LocalAction) {
    val (label, status) = when (row.state) {
        AvailabilityState.AVAILABLE -> "AVAILABLE" to ChipStatus.SUCCESS
        AvailabilityState.PERMISSION_MISSING -> "PERMISSION" to ChipStatus.WARNING
        AvailabilityState.NOT_INSTALLED -> "NOT INSTALLED" to ChipStatus.DANGER
        AvailabilityState.OFFLINE -> "OFFLINE" to ChipStatus.DANGER
        AvailabilityState.DISABLED_BY_TRUST -> "TRUST" to ChipStatus.WARNING
        AvailabilityState.HARDWARE_MISSING -> "UNAVAILABLE" to ChipStatus.DANGER
        AvailabilityState.UNKNOWN -> "UNKNOWN" to ChipStatus.NEUTRAL
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            row.reason?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TextDim)
            }
        }
        StatusChip(label, status)
    }
}

@Composable
private fun ShortcutRow(title: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text("›", style = MaterialTheme.typography.titleMedium, color = CobaltBright)
    }
}

