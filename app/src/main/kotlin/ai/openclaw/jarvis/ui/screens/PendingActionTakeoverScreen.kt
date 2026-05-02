package ai.openclaw.jarvis.ui.screens

import ai.openclaw.jarvis.policy.store.PendingApproval
import ai.openclaw.jarvis.policy.ui.PendingApprovalsViewModel
import ai.openclaw.jarvis.ui.components.BlueprintCard
import ai.openclaw.jarvis.ui.components.ChipStatus
import ai.openclaw.jarvis.ui.components.DangerButton
import ai.openclaw.jarvis.ui.components.PrimaryButton
import ai.openclaw.jarvis.ui.components.SectionHeader
import ai.openclaw.jarvis.ui.components.StatusChip
import ai.openclaw.jarvis.ui.theme.BlueprintBackground
import ai.openclaw.jarvis.ui.theme.CobaltGlow
import ai.openclaw.jarvis.ui.theme.TextDim
import ai.openclaw.jarvis.ui.theme.TextPrimary
import ai.openclaw.jarvis.ui.theme.TextSecondary
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Full-screen takeover when a pending approval is live. The spec is
 * unambiguous: pending action must dominate the UI, no distracting
 * dashboard elements. This route is shown via `LaunchedEffect` from
 * the host scaffold whenever the live approvals list is non-empty.
 *
 * The screen always operates on the *first* pending approval — that's
 * the one Jarvis is waiting on right now. Subsequent ones queue and
 * appear after the current is resolved.
 */
@Composable
fun PendingActionTakeoverScreen(
    onResolved: () -> Unit,
    viewModel: PendingApprovalsViewModel = hiltViewModel(),
) {
    val approvals by viewModel.approvals.collectAsStateWithLifecycle()
    val approval = approvals.firstOrNull()

    if (approval == null) {
        // Queue drained — let the host pop us off.
        LaunchedDispatch(onResolved)
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BlueprintBackground.copy(alpha = 0.96f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Pending Action",
                style = MaterialTheme.typography.titleSmall,
                color = TextDim,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                approval.descriptor.kind.name.replace('_', ' '),
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
            )

            Spacer(Modifier.height(24.dp))

            BlueprintCard(modifier = Modifier.fillMaxWidth(), glowing = true) {
                Column {
                    SectionHeader("Summary")
                    Text(
                        approval.descriptor.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                    )
                    val message = approval.descriptor.params["message"]
                    if (!message.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        SectionHeader("Message")
                        Text(message, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    }
                    val to = approval.descriptor.params["to"]
                        ?: approval.descriptor.params["toContactName"]
                    if (!to.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        SectionHeader("Recipient")
                        Text(to, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    }

                    Spacer(Modifier.height(16.dp))
                    SectionHeader("Risk")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(
                            label = approval.descriptor.risk.name.uppercase(),
                            status = riskStatus(approval),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Decision: ${approval.decisionLevel.name.replace('_', ' ').lowercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextDim,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(16.dp))

            Text(
                "Speak yes or no",
                style = MaterialTheme.typography.bodyMedium,
                color = CobaltGlow,
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(
                    text = "Approve",
                    onClick = { viewModel.approve(approval.id) },
                    modifier = Modifier.weight(1f),
                )
                DangerButton(
                    text = "Cancel",
                    onClick = { viewModel.reject(approval.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LaunchedDispatch(action: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { action() }
}

private fun riskStatus(approval: PendingApproval): ChipStatus = when (approval.descriptor.risk.name) {
    "SAFE" -> ChipStatus.SUCCESS
    "LIMITED" -> ChipStatus.INFO
    "HIGH" -> ChipStatus.WARNING
    "RESTRICTED" -> ChipStatus.DANGER
    else -> ChipStatus.NEUTRAL
}
