package ai.openclaw.jarvis.githubissues.ui

import ai.openclaw.jarvis.githubissues.GitHubIssueLogger
import ai.openclaw.jarvis.githubissues.integration.IssueContextBuilder
import ai.openclaw.jarvis.githubissues.model.IssueEvent
import ai.openclaw.jarvis.ui.theme.CobaltGlow
import ai.openclaw.jarvis.ui.theme.StatusConnected
import ai.openclaw.jarvis.ui.theme.StatusOffline
import ai.openclaw.jarvis.ui.theme.StatusQueued
import ai.openclaw.jarvis.ui.theme.TextDim
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Create issue from this event" button shown on the in-app debug screen.
 * Reports the supplied [IssueEvent] via [GitHubIssueLogger.report] with
 * `force = true`, so the user can intentionally bypass severity / category
 * gating and the dedupe window for a one-off bug report.
 */
@Composable
fun DebugIssueButton(
    logger: GitHubIssueLogger,
    contextBuilder: IssueContextBuilder,
    eventFactory: (ai.openclaw.jarvis.githubissues.model.IssueContext) -> IssueEvent,
    label: String = "Create GitHub issue from this event",
) {
    var status by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Button(
            onClick = { pending = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CobaltGlow.copy(alpha = 0.2f)),
        ) { Text(label, color = CobaltGlow, fontFamily = FontFamily.Monospace) }
        status?.let {
            val color = when {
                it.startsWith("Created") -> StatusConnected
                it.startsWith("Queued") || it.startsWith("Suppressed") -> StatusQueued
                it.startsWith("Skipped") || it.startsWith("Failed") -> StatusOffline
                else -> TextDim
            }
            Text(it, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }

    LaunchedEffect(pending) {
        if (!pending) return@LaunchedEffect
        status = "creating…"
        val event = eventFactory(contextBuilder.build())
        val outcome = logger.report(event, force = true)
        status = when (outcome) {
            is GitHubIssueLogger.Outcome.Created -> "Created issue #${outcome.issueNumber}"
            is GitHubIssueLogger.Outcome.Queued -> "Queued (${outcome.reason})"
            is GitHubIssueLogger.Outcome.Suppressed -> "Suppressed (#${outcome.existingIssueNumber ?: "?"})"
            is GitHubIssueLogger.Outcome.Skipped -> "Skipped: ${outcome.reason}"
            is GitHubIssueLogger.Outcome.Failed -> "Failed: ${outcome.reason}"
        }
        pending = false
    }
}
