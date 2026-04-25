package com.jarvis.githubissues.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.jarvis.githubissues.settings.DedupeWindow
import com.jarvis.githubissues.settings.FailureCategory
import com.jarvis.githubissues.settings.Severity

/**
 * Compose UI for the GitHub Issue Logging settings page. Renders every
 * control the spec lists: enable toggle, repo / token, labels, categories,
 * severity floor, dedupe window, redaction options, test buttons, queued
 * issue count and the recent issue log panel.
 */
@Composable
fun GitHubIssueLoggingScreen(viewModel: GitHubIssueLoggingViewModel) {
    val s by viewModel.settings.collectAsState()
    val queued by viewModel.queuedCount.collectAsState()
    val connStatus by viewModel.connectionStatus.collectAsState()
    val testStatus by viewModel.testIssueStatus.collectAsState()
    val log by viewModel.recentLog.entries.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        item {
            SectionHeader("GitHub Issue Logging")
            ToggleRow(
                label = "Enable GitHub issue logging",
                checked = s.enabled,
                onChange = viewModel::setEnabled
            )
        }
        item {
            SectionHeader("Repository")
            OutlinedTextField(
                value = s.owner, onValueChange = viewModel::setOwner,
                label = { Text("GitHub owner") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = s.repo, onValueChange = viewModel::setRepo,
                label = { Text("GitHub repo") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            SectionHeader("Token")
            TokenField(
                hasToken = s.tokenConfigured,
                onSave = viewModel::setToken
            )
            Text(
                text = if (s.tokenConfigured) "Token configured (encrypted on device)"
                       else "No token saved",
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        item {
            SectionHeader("Diagnostics")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = viewModel::testConnection) { Text("Test connection") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = viewModel::createTestIssue) { Text("Create test issue") }
            }
            StatusRow("Connection", connStatus)
            StatusRow("Test issue", testStatus)
            Text("Queued issues: $queued")
        }
        item {
            SectionHeader("Auto-create issue for")
            for (cat in FailureCategory.values()) {
                ToggleRow(
                    label = catLabel(cat),
                    checked = s.categoryEnabled(cat),
                    onChange = { enabled -> viewModel.setCategoryEnabled(cat, enabled) }
                )
            }
        }
        item {
            SectionHeader("Severity & dedupe")
            SeverityPicker(s.minSeverity, viewModel::setMinSeverity)
            Spacer(Modifier.height(8.dp))
            DedupeWindowPicker(s.dedupeWindow, viewModel::setDedupeWindow)
            Spacer(Modifier.height(8.dp))
            ToggleRow("Include debug context", s.includeDebugContext, viewModel::setIncludeDebugContext)
        }
        item {
            SectionHeader("Redaction")
            ToggleRow("Redact phone numbers", s.redaction.redactPhoneNumbers) { v ->
                viewModel.setRedaction { it.copy(redactPhoneNumbers = v) }
            }
            ToggleRow("Redact emails", s.redaction.redactEmails) { v ->
                viewModel.setRedaction { it.copy(redactEmails = v) }
            }
            ToggleRow("Redact precise location", s.redaction.redactPreciseLocation) { v ->
                viewModel.setRedaction { it.copy(redactPreciseLocation = v) }
            }
            ToggleRow("Redact message bodies", s.redaction.redactMessageBody) { v ->
                viewModel.setRedaction { it.copy(redactMessageBody = v) }
            }
            ToggleRow("Redact contact names", s.redaction.redactContactNames) { v ->
                viewModel.setRedaction { it.copy(redactContactNames = v) }
            }
            ToggleRow("Redact restricted transcripts", s.redaction.redactRestrictedTranscripts) { v ->
                viewModel.setRedaction { it.copy(redactRestrictedTranscripts = v) }
            }
            ToggleRow("Redact tokens", s.redaction.redactTokens) { v ->
                viewModel.setRedaction { it.copy(redactTokens = v) }
            }
            ToggleRow("Redact OpenClaw keys", s.redaction.redactOpenClawKeys) { v ->
                viewModel.setRedaction { it.copy(redactOpenClawKeys = v) }
            }
        }
        item {
            SectionHeader("Recent issue log")
            if (log.isEmpty()) Text("No recent issues.")
        }
        items(log) { entry -> RecentLogRow(entry) }
    }
}

@Composable private fun SectionHeader(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text)
    Divider()
    Spacer(Modifier.height(8.dp))
}

@Composable private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(label, modifier = Modifier.padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable private fun TokenField(hasToken: Boolean, onSave: (String?) -> Unit) {
    var input by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(if (hasToken) "Replace token" else "Paste GitHub PAT") },
            modifier = Modifier.fillMaxWidth()
        )
        Row {
            Button(
                enabled = input.isNotBlank(),
                onClick = { onSave(input); input = "" }
            ) { Text("Save token") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onSave(null); input = "" }) { Text("Clear") }
        }
    }
}

@Composable private fun StatusRow(label: String, status: GitHubIssueLoggingViewModel.TestStatus) {
    val text = when (status) {
        is GitHubIssueLoggingViewModel.TestStatus.Idle -> "—"
        is GitHubIssueLoggingViewModel.TestStatus.Running -> "running…"
        is GitHubIssueLoggingViewModel.TestStatus.Ok -> status.message
        is GitHubIssueLoggingViewModel.TestStatus.Err -> "error: ${status.message}"
    }
    Text("$label: $text")
}

@Composable private fun SeverityPicker(current: Severity, onChange: (Severity) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Min severity: ${current.tag}", modifier = Modifier.padding(end = 8.dp))
        Button(onClick = { expanded = true }) { Text("change") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (s in Severity.values()) {
                DropdownMenuItem(
                    text = { Text(s.tag) },
                    onClick = { onChange(s); expanded = false }
                )
            }
        }
    }
}

@Composable private fun DedupeWindowPicker(current: DedupeWindow, onChange: (DedupeWindow) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Dedupe window: ${current.label}", modifier = Modifier.padding(end = 8.dp))
        Button(onClick = { expanded = true }) { Text("change") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (w in DedupeWindow.values()) {
                DropdownMenuItem(
                    text = { Text(w.label) },
                    onClick = { onChange(w); expanded = false }
                )
            }
        }
    }
}

@Composable private fun RecentLogRow(entry: RecentIssueLog.Entry) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("[${entry.severity.tag}/${entry.category.tag}] ${entry.title}")
            Text("status: ${entry.status.name.lowercase()}")
            entry.htmlUrl?.let { Text(it) }
            entry.message?.let { Text(it) }
        }
    }
}

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
