package ai.openclaw.jarvis.githubissues.ui

import ai.openclaw.jarvis.githubissues.settings.DedupeWindow
import ai.openclaw.jarvis.githubissues.settings.FailureCategory
import ai.openclaw.jarvis.githubissues.settings.Severity
import ai.openclaw.jarvis.ui.theme.BlueprintBackground
import ai.openclaw.jarvis.ui.theme.CobaltGlow
import ai.openclaw.jarvis.ui.theme.StatusConnected
import ai.openclaw.jarvis.ui.theme.StatusOffline
import ai.openclaw.jarvis.ui.theme.StatusQueued
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
 * Settings section for GitHub Issue Logging, designed to be dropped inside
 * the existing [ai.openclaw.jarvis.ui.screens.SettingsScreen] beneath the
 * other `SettingsSection` blocks. The visual styling matches the host
 * (BlueprintBackground / CobaltGlow / monospace labels).
 */
@Composable
fun GitHubIssueLoggingSection(
    viewModel: GitHubIssueLoggingViewModel = hiltViewModel(),
) {
    val s by viewModel.settings.collectAsState()
    val queued by viewModel.queuedCount.collectAsState()
    val connStatus by viewModel.connectionStatus.collectAsState()
    val testStatus by viewModel.testIssueStatus.collectAsState()
    val log by viewModel.recentLog.entries.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CobaltGlow.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(16.dp),
    ) {
        Text(
            "GITHUB ISSUE LOGGING",
            color = CobaltGlow,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )

        ToggleRow("Enable issue logging", s.enabled, viewModel::setEnabled)

        OutlinedTextField(
            value = s.owner,
            onValueChange = viewModel::setOwner,
            label = { Text("GitHub owner") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = monoTextFieldColors(),
        )
        OutlinedTextField(
            value = s.repo,
            onValueChange = viewModel::setRepo,
            label = { Text("GitHub repo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = monoTextFieldColors(),
        )

        TokenField(s.tokenConfigured, viewModel::setToken)
        Text(
            text = if (s.tokenConfigured) "Token configured (encrypted on device)"
                   else "No token saved",
            color = if (s.tokenConfigured) StatusConnected else TextDim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = viewModel::testConnection,
                modifier = Modifier.weight(1f),
            ) { Text("Test connection") }
            Button(
                onClick = viewModel::createTestIssue,
                modifier = Modifier.weight(1f),
            ) { Text("Create test issue") }
        }
        StatusRow("Connection", connStatus)
        StatusRow("Test issue", testStatus)
        InfoRow("Queued issues", queued.toString())

        SubHeader("Auto-create issue for")
        FailureCategory.values().forEach { cat ->
            ToggleRow(catLabel(cat), s.categoryEnabled(cat)) { enabled ->
                viewModel.setCategoryEnabled(cat, enabled)
            }
        }

        SubHeader("Severity & dedupe")
        EnumPicker(
            label = "Min severity",
            current = s.minSeverity.tag,
            options = Severity.values().map { it.tag },
            onSelect = { viewModel.setMinSeverity(Severity.fromTag(it)) },
        )
        EnumPicker(
            label = "Dedupe window",
            current = s.dedupeWindow.label,
            options = DedupeWindow.values().map { it.label },
            onSelect = { viewModel.setDedupeWindow(DedupeWindow.fromLabel(it)) },
        )
        ToggleRow("Include debug context", s.includeDebugContext, viewModel::setIncludeDebugContext)

        SubHeader("Redaction")
        ToggleRow("Phone numbers", s.redaction.redactPhoneNumbers) { v ->
            viewModel.setRedaction { it.copy(redactPhoneNumbers = v) }
        }
        ToggleRow("Emails", s.redaction.redactEmails) { v ->
            viewModel.setRedaction { it.copy(redactEmails = v) }
        }
        ToggleRow("Precise location", s.redaction.redactPreciseLocation) { v ->
            viewModel.setRedaction { it.copy(redactPreciseLocation = v) }
        }
        ToggleRow("Message bodies", s.redaction.redactMessageBody) { v ->
            viewModel.setRedaction { it.copy(redactMessageBody = v) }
        }
        ToggleRow("Contact names", s.redaction.redactContactNames) { v ->
            viewModel.setRedaction { it.copy(redactContactNames = v) }
        }
        ToggleRow("Restricted transcripts", s.redaction.redactRestrictedTranscripts) { v ->
            viewModel.setRedaction { it.copy(redactRestrictedTranscripts = v) }
        }
        ToggleRow("Tokens", s.redaction.redactTokens) { v ->
            viewModel.setRedaction { it.copy(redactTokens = v) }
        }
        ToggleRow("OpenClaw keys", s.redaction.redactOpenClawKeys) { v ->
            viewModel.setRedaction { it.copy(redactOpenClawKeys = v) }
        }

        SubHeader("Recent issues")
        if (log.isEmpty()) {
            Text("None yet.", color = TextDim, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace)
        } else {
            log.forEach { entry -> RecentLogRow(entry) }
        }
    }
}

@Composable private fun SubHeader(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, color = TextDim, fontSize = 11.sp,
        letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
}

@Composable private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp,
            fontFamily = FontFamily.Monospace)
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

@Composable private fun TokenField(hasToken: Boolean, onSave: (String?) -> Unit) {
    var input by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(if (hasToken) "Replace token" else "Paste GitHub PAT") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = monoTextFieldColors(),
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSave(input); input = "" },
                enabled = input.isNotBlank(),
            ) { Text("Save token") }
            TextButton(onClick = { onSave(null); input = "" }) {
                Text("Clear", color = StatusOffline)
            }
        }
    }
}

@Composable private fun StatusRow(label: String, status: GitHubIssueLoggingViewModel.TestStatus) {
    val (text, color) = when (status) {
        is GitHubIssueLoggingViewModel.TestStatus.Idle -> "—" to TextDim
        is GitHubIssueLoggingViewModel.TestStatus.Running -> "running…" to StatusQueued
        is GitHubIssueLoggingViewModel.TestStatus.Ok -> status.message to StatusConnected
        is GitHubIssueLoggingViewModel.TestStatus.Err -> "error: ${status.message}" to StatusOffline
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(text, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable private fun EnumPicker(
    label: String,
    current: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("$label: $current", color = TextSecondary, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace)
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(containerColor = BlueprintBackground),
        ) { Text("change", color = CobaltGlow, fontSize = 11.sp) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o) },
                    onClick = { onSelect(o); expanded = false },
                )
            }
        }
    }
}

@Composable private fun RecentLogRow(entry: RecentIssueLog.Entry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlueprintBackground.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(8.dp),
    ) {
        Text(
            "[${entry.severity.tag}/${entry.category.tag}] ${entry.title}",
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            "status: ${entry.status.name.lowercase()}",
            color = when (entry.status) {
                RecentIssueLog.Status.CREATED -> StatusConnected
                RecentIssueLog.Status.QUEUED -> StatusQueued
                RecentIssueLog.Status.SUPPRESSED -> TextDim
                RecentIssueLog.Status.FAILED -> StatusOffline
            },
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        entry.htmlUrl?.let {
            Text(it, color = CobaltGlow, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable private fun monoTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CobaltGlow,
    unfocusedBorderColor = CobaltGlow.copy(alpha = 0.4f),
    focusedTextColor = TextSecondary,
    unfocusedTextColor = TextSecondary,
    focusedLabelColor = CobaltGlow,
    unfocusedLabelColor = TextDim,
    cursorColor = CobaltGlow,
)

private fun catLabel(c: FailureCategory): String = when (c) {
    FailureCategory.ERROR -> "errors"
    FailureCategory.UNSUPPORTED -> "unsupported commands"
    FailureCategory.PERMISSION -> "permission failures"
    FailureCategory.OPENCLAW_OFFLINE -> "OpenClaw offline failures"
    FailureCategory.OPENCLAW_MALFORMED -> "malformed OpenClaw responses"
    FailureCategory.OPENCLAW -> "other OpenClaw failures"
    FailureCategory.CANT_DO_THAT -> "\"can't do that\" responses"
    FailureCategory.REPEATED_STT_TTS -> "repeated STT/TTS failures"
    FailureCategory.VOICE -> "other voice/audio failures"
    FailureCategory.ACTION -> "action failures"
    FailureCategory.INTENT -> "intent failures"
    FailureCategory.ROUTING -> "routing failures"
    FailureCategory.USER_CORRECTION -> "user corrections"
    FailureCategory.ERROR_RECOVERY -> "ERROR_RECOVERY transitions"
}
