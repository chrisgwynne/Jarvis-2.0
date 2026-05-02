package ai.openclaw.jarvis.screen.ui

import ai.openclaw.jarvis.ui.theme.BlueprintBackground
import ai.openclaw.jarvis.ui.theme.CobaltGlow
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
 * Section composable for SettingsScreen — exposes every screen-awareness
 * privacy control the spec requires: enable, screenshot auto-analyse,
 * store-screenshots, retention, voice-prompt, plus whitelist /
 * blacklist editors.
 *
 * Sensitive-category exclusion is intentionally not exposed — the
 * SENSITIVE category is hard-coded as always-excluded for safety.
 */
@Composable
fun ScreenAwarenessSettingsSection(
    viewModel: ScreenAwarenessSettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.settings.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CobaltGlow.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("SCREEN AWARENESS", color = CobaltGlow, letterSpacing = 2.sp,
            fontSize = 13.sp, fontFamily = FontFamily.Monospace)

        Toggle("Enable screen awareness", s.enabled, viewModel::setEnabled)
        Toggle("Auto-analyse screenshots", s.screenshotAutoAnalyse, viewModel::setScreenshotAuto)
        Toggle("Store screenshots", s.storeScreenshots, viewModel::setStoreScreenshots)
        Toggle("Voice prompt on analysis", s.voicePromptOnAnalysis, viewModel::setVoicePrompt)

        InfoRow("Retention", "${s.retentionHours} h")
        Text("Sensitive apps (banking, password managers, authenticators) are always excluded.",
            color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)

        SubHeader("Whitelist (empty = watch every non-sensitive app)")
        PackageList(
            packages = s.whitelist,
            onAdd = viewModel::addToWhitelist,
            onRemove = viewModel::removeFromWhitelist,
        )

        SubHeader("Blacklist (always wins over whitelist)")
        PackageList(
            packages = s.blacklist,
            onAdd = viewModel::addToBlacklist,
            onRemove = viewModel::removeFromBlacklist,
        )
    }
}

@Composable private fun SubHeader(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, color = TextDim, fontSize = 11.sp,
        letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
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

@Composable private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable private fun PackageList(
    packages: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        label = { Text("package name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CobaltGlow,
            unfocusedBorderColor = CobaltGlow.copy(alpha = 0.4f),
            focusedTextColor = TextSecondary,
            unfocusedTextColor = TextSecondary,
            focusedLabelColor = CobaltGlow,
            unfocusedLabelColor = TextDim,
            cursorColor = CobaltGlow,
        ),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            enabled = input.isNotBlank(),
            onClick = { onAdd(input.trim()); input = "" },
        ) { Text("ADD", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
    }
    if (packages.isEmpty()) {
        Text("(none)", color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    } else {
        packages.forEach { pkg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BlueprintBackground.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(pkg, color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                TextButton(onClick = { onRemove(pkg) }) {
                    Text("REMOVE", color = StatusOffline, fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
