package ai.openclaw.jarvis.ui.screens

import ai.openclaw.jarvis.awareness.AvailabilityState
import ai.openclaw.jarvis.awareness.AwarenessQuestion
import ai.openclaw.jarvis.awareness.AwarenessResponder
import ai.openclaw.jarvis.awareness.AwarenessSnapshot
import ai.openclaw.jarvis.awareness.LocalAction
import ai.openclaw.jarvis.awareness.OpenClawSkillStatus
import ai.openclaw.jarvis.ui.theme.BlueprintBackground
import ai.openclaw.jarvis.ui.theme.BlueprintBorder
import ai.openclaw.jarvis.ui.theme.CobaltBright
import ai.openclaw.jarvis.ui.theme.CobaltGlow
import ai.openclaw.jarvis.ui.theme.StatusConnected
import ai.openclaw.jarvis.ui.theme.StatusOffline
import ai.openclaw.jarvis.ui.theme.StatusQueued
import ai.openclaw.jarvis.ui.theme.TextDim
import ai.openclaw.jarvis.ui.theme.TextSecondary
import ai.openclaw.jarvis.ui.viewmodel.CapabilityDashboardViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Live "what can Jarvis do right now" dashboard. Reads the
 * [ai.openclaw.jarvis.awareness.CapabilityAwarenessManager.snapshot] and
 * surfaces:
 *   - every Android action (available / permission missing / unavailable)
 *   - every OpenClaw skill (available / offline / disabled)
 *   - Bluetooth state
 *   - current speaker trust level
 *   - missing permissions, with rationale
 *   - recommended setup steps
 *
 * The "TEST" button beside each row asks the [AwarenessResponder] for the
 * answer Jarvis would give if the user asked "Can you X?" — so the
 * dashboard mirrors what Jarvis itself would say.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapabilityDashboardScreen(
    onBack: () -> Unit,
    viewModel: CapabilityDashboardViewModel = hiltViewModel(),
) {
    val snap by viewModel.snapshot.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = BlueprintBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "CAPABILITIES", color = CobaltGlow, letterSpacing = 3.sp,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::refresh) {
                        Text(
                            "REFRESH",
                            color = CobaltGlow, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BlueprintBackground),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeaderStrip(snap)
            Section("ANDROID ACTIONS") {
                snap.androidActions.forEach { ActionRow(it) }
            }
            Section("OPENCLAW SKILLS") {
                if (snap.openClawSkills.isEmpty()) {
                    DimRow(
                        if (snap.openClawConnected) "OpenClaw connected — no skills published yet."
                        else "OpenClaw is offline."
                    )
                } else {
                    snap.openClawSkills.forEach { SkillRow(it) }
                }
            }
            Section("BLUETOOTH") {
                InfoRow("Mic", if (snap.bluetoothMicConnected) "connected" else "not connected",
                    if (snap.bluetoothMicConnected) StatusConnected else TextDim)
                InfoRow("Output", if (snap.bluetoothOutputConnected) "connected" else "not connected",
                    if (snap.bluetoothOutputConnected) StatusConnected else TextDim)
            }
            if (snap.missingPermissions.isNotEmpty()) {
                Section("MISSING PERMISSIONS") {
                    snap.missingPermissions.forEach { p ->
                        DimRow("${p.rationale} — ${p.permission}")
                    }
                }
            }
            if (snap.recommendedSetup.isNotEmpty()) {
                Section("RECOMMENDED") {
                    snap.recommendedSetup.forEach { DimRow("• $it") }
                }
            }
        }
    }
}

@Composable private fun HeaderStrip(snap: AwarenessSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CobaltGlow.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Trust level", color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(snap.trustLevel,
                color = if (snap.trustLevel == "OWNER") StatusConnected else StatusQueued,
                fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("OpenClaw", color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(if (snap.openClawConnected) "connected" else "offline",
                color = if (snap.openClawConnected) StatusConnected else StatusOffline,
                fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BlueprintBorder, RoundedCornerShape(4.dp))
            .background(BlueprintBackground.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = CobaltBright, letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable private fun ActionRow(row: LocalAction) {
    val (txt, color) = stateLabel(row.state)
    val responder = remember { AwarenessResponder() }
    var probe by remember { mutableStateOf<String?>(null) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.label, color = TextSecondary, fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace)
                row.reason?.let {
                    Text(it, color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Text(txt, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        TestButton {
            // The test button mirrors what Jarvis would say. We map the row id
            // to a Topic; rows without a matching topic just show the row's
            // own state machine.
            val topic = topicForRow(row.id)
            probe = if (topic != null) {
                responder.answer(AwarenessQuestion.CanYou(topic), fakeSnapshot(row))
            } else {
                "${row.label}: ${stateLabel(row.state).first}"
            }
        }
        probe?.let {
            Text(it, color = TextSecondary, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable private fun SkillRow(row: OpenClawSkillStatus) {
    val (txt, color) = stateLabel(row.state)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, color = TextSecondary, fontSize = 13.sp,
                fontFamily = FontFamily.Monospace)
            Text(row.description, color = TextDim, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace)
            row.reason?.let {
                Text(it, color = StatusOffline, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Text(txt, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable private fun InfoRow(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable private fun DimRow(text: String) {
    Text(text, color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
}

@Composable private fun TestButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = CobaltGlow.copy(alpha = 0.15f)),
    ) {
        Text("TEST", color = CobaltGlow, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun stateLabel(s: AvailabilityState): Pair<String, Color> = when (s) {
    AvailabilityState.AVAILABLE          -> "available"          to StatusConnected
    AvailabilityState.PERMISSION_MISSING -> "permission missing" to StatusQueued
    AvailabilityState.NOT_INSTALLED      -> "not installed"      to StatusOffline
    AvailabilityState.HARDWARE_MISSING   -> "hardware missing"   to StatusOffline
    AvailabilityState.OFFLINE            -> "offline"            to StatusOffline
    AvailabilityState.DISABLED_BY_TRUST  -> "trust required"     to StatusQueued
    AvailabilityState.UNKNOWN            -> "unknown"            to TextDim
}

private fun topicForRow(id: String): AwarenessQuestion.Topic? = when (id) {
    "sms"        -> AwarenessQuestion.Topic.SMS
    "whatsapp"   -> AwarenessQuestion.Topic.WHATSAPP
    "calls"      -> AwarenessQuestion.Topic.CALL
    "screenshot" -> AwarenessQuestion.Topic.SCREENSHOT
    "location"   -> AwarenessQuestion.Topic.LOCATION
    "open_app"   -> AwarenessQuestion.Topic.OPEN_APP
    "camera"     -> AwarenessQuestion.Topic.PHOTO
    else         -> null
}

/** Build a minimal snapshot containing just this row so the responder's
 *  per-row branches read cleanly without needing the full snapshot. */
private fun fakeSnapshot(row: LocalAction) = AwarenessSnapshot(
    androidActions = listOf(row),
    openClawSkills = emptyList(),
    openClawConnected = false,
    bluetoothMicConnected = false,
    bluetoothOutputConnected = false,
    trustLevel = "OWNER",
    missingPermissions = emptyList(),
    recommendedSetup = emptyList(),
)
