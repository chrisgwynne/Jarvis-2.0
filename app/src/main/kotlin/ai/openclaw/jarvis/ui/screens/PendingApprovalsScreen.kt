package ai.openclaw.jarvis.ui.screens

import ai.openclaw.jarvis.policy.store.PendingApproval
import ai.openclaw.jarvis.policy.ui.PendingApprovalsViewModel
import ai.openclaw.jarvis.ui.theme.BlueprintBackground
import ai.openclaw.jarvis.ui.theme.BlueprintBorder
import ai.openclaw.jarvis.ui.theme.CobaltBright
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-app approval card list — the third approval surface alongside
 * voice-confirmation and notification action buttons.
 *
 * Each row shows the action descriptor's summary, its risk + decided
 * level, the time until expiry, and APPROVE / REJECT buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingApprovalsScreen(
    onBack: () -> Unit,
    viewModel: PendingApprovalsViewModel = hiltViewModel(),
) {
    val items by viewModel.approvals.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = BlueprintBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text("APPROVALS", color = CobaltGlow, letterSpacing = 3.sp,
                        style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BlueprintBackground),
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No actions waiting on approval.",
                    color = TextDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { approval ->
                ApprovalRow(approval, viewModel::approve, viewModel::reject)
            }
        }
    }
}

@Composable private fun ApprovalRow(
    p: PendingApproval,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BlueprintBorder, RoundedCornerShape(8.dp))
            .background(BlueprintBackground.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(p.descriptor.summary,
            color = TextSecondary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${p.descriptor.kind.name} • ${p.descriptor.risk.name}",
                color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("expires ${formatExpiry(p.expiresAtMillis)}",
                color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("level: ${p.decisionLevel.name}",
                color = CobaltBright, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Row {
                TextButton(onClick = { onReject(p.id) }) {
                    Text("REJECT", color = StatusOffline, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace)
                }
                TextButton(onClick = { onApprove(p.id) }) {
                    Text("APPROVE", color = StatusConnected, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }
        if (p.descriptor.params.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            p.descriptor.params.forEach { (k, v) ->
                Text("$k: $v", color = TextDim, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private val TIME = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
private fun formatExpiry(epochMillis: Long): String =
    runCatching { TIME.format(Date(epochMillis)) }.getOrDefault("?")
