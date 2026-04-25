package com.jarvis.githubissues.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jarvis.githubissues.GitHubIssueLogger
import com.jarvis.githubissues.model.IssueEvent

/**
 * "Create issue from this event" button used on the in-app debug screen.
 * Wraps a single [IssueEvent] and reports it via [GitHubIssueLogger.report]
 * with `force = true` so the user can intentionally bypass severity /
 * category gating and the dedupe window for a one-off bug report.
 */
@Composable
fun DebugIssueButton(
    logger: GitHubIssueLogger,
    event: IssueEvent,
    label: String = "Create issue from this event"
) {
    var status by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<IssueEvent?>(null) }

    Button(onClick = { pending = event }) { Text(label) }
    status?.let { Text(it) }

    LaunchedEffect(pending) {
        val toFire = pending ?: return@LaunchedEffect
        status = "creating…"
        val outcome = logger.report(toFire, force = true)
        status = when (outcome) {
            is GitHubIssueLogger.Outcome.Created -> "Created issue #${outcome.issueNumber}"
            is GitHubIssueLogger.Outcome.Queued -> "Queued (${outcome.reason})"
            is GitHubIssueLogger.Outcome.Suppressed -> "Suppressed (#${outcome.existingIssueNumber ?: "?"})"
            is GitHubIssueLogger.Outcome.Skipped -> "Skipped: ${outcome.reason}"
            is GitHubIssueLogger.Outcome.Failed -> "Failed: ${outcome.reason}"
        }
        pending = null
    }
}
