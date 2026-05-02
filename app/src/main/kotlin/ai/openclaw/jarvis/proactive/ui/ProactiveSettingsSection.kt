package ai.openclaw.jarvis.proactive.ui

import ai.openclaw.jarvis.proactive.model.Aggressiveness
import ai.openclaw.jarvis.proactive.model.SignalType
import ai.openclaw.jarvis.ui.theme.BlueprintBackground
import ai.openclaw.jarvis.ui.theme.CobaltGlow
import ai.openclaw.jarvis.ui.theme.StatusConnected
import ai.openclaw.jarvis.ui.theme.StatusOffline
import ai.openclaw.jarvis.ui.theme.TextDim
import ai.openclaw.jarvis.ui.theme.TextSecondary
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Section composable for the SettingsScreen — exposes every proactive
 * tunable the spec lists: master enable, aggressiveness, quiet hours,
 * per-signal toggles, and a "clear suggestion blocks" button that
 * undoes the user's "don't suggest again" choices.
 */
@Composable
fun ProactiveSettingsSection(
    viewModel: ProactiveSettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.settings.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CobaltGlow.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("PROACTIVE SUGGESTIONS", color = CobaltGlow, letterSpacing = 2.sp,
            fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Toggle("Enable proactive suggestions", s.enabled, viewModel::setEnabled)
        Picker(
            label = "Aggressiveness",
            current = s.aggressiveness.name,
            options = Aggressiveness.values().map { it.name },
            onSelect = { name -> viewModel.setAggressiveness(Aggressiveness.valueOf(name)) },
        )
        Toggle("Quiet hours", s.quietHours.enabled) { v ->
            viewModel.setQuietHours(s.quietHours.copy(enabled = v))
        }
        if (s.quietHours.enabled) {
            InfoRow("Quiet window", "${s.quietHours.startHour}:00 → ${s.quietHours.endHour}:00")
        }
        Spacer(Modifier.height(4.dp))
        Text("Per-signal toggles", color = TextDim, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
        SignalType.values().forEach { sig ->
            Toggle(humanise(sig), s.perSignal[sig] ?: true) { enabled ->
                viewModel.setSignalEnabled(sig, enabled)
            }
        }
        Spacer(Modifier.height(4.dp))
        InfoRow("Suppressed suggestions", s.suppressedSuggestionIds.size.toString())
        Button(
            onClick = viewModel::unsuppressAll,
            colors = ButtonDefaults.buttonColors(
                containerColor = StatusOffline.copy(alpha = 0.15f),
            ),
        ) {
            Text("CLEAR \"DON'T SUGGEST AGAIN\"", color = StatusOffline,
                fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CobaltGlow,
                checkedTrackColor = CobaltGlow.copy(alpha = 0.3f),
            ),
        )
    }
}

@Composable private fun Picker(
    label: String,
    current: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label: $current", color = TextSecondary, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace)
        TextButton(onClick = { expanded = true }) {
            Text("change", color = CobaltGlow, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(o); expanded = false })
            }
        }
    }
}

@Composable private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun humanise(sig: SignalType): String =
    sig.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
