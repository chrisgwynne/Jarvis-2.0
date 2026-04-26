package ai.openclaw.jarvis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.openclaw.jarvis.debug.AssistantEvent
import ai.openclaw.jarvis.debug.AssistantDebugState
import ai.openclaw.jarvis.githubissues.model.IssueEvent
import ai.openclaw.jarvis.githubissues.ui.DebugIssueButton
import ai.openclaw.jarvis.ui.theme.*
import ai.openclaw.jarvis.ui.viewmodel.DebugViewModel
import java.text.SimpleDateFormat
import java.util.*

private val TIME_FMT = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    onNavigateToProtocol: () -> Unit = {},
    viewModel: DebugViewModel = hiltViewModel(),
) {
    val state by viewModel.debugState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = BlueprintBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text("DEBUG", color = CobaltGlow, letterSpacing = 3.sp,
                        style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::clearLog) {
                        Text("CLEAR", color = StatusOffline,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp)
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DebugStatusGrid(state)
            state.lastError?.let { error ->
                DebugErrorBanner(error)
            }
            OutlinedButton(
                onClick = onNavigateToProtocol,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CobaltGlow),
                border = androidx.compose.foundation.BorderStroke(1.dp, CobaltGlow.copy(alpha = 0.5f)),
            ) {
                Text(
                    "PROTOCOL INSPECTOR",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 1.sp,
                )
            }
            // "Create issue from this event" — file the most recent error
            // (or current state) as a GitHub issue, bypassing severity /
            // category gating.
            DebugIssueButton(
                logger = viewModel.issueLogger,
                contextBuilder = viewModel.issueContextBuilder,
                eventFactory = { ctx ->
                    val errorMsg = state.lastError
                    if (errorMsg != null) {
                        IssueEvent.ErrorRecovery(
                            fromState = state.assistantState.name,
                            triggerReason = errorMsg,
                            context = ctx.copy(actualBehaviour = errorMsg),
                        )
                    } else {
                        IssueEvent.Unsupported(
                            capability = "debug-snapshot",
                            reason = "Manual debug-screen capture",
                            userPhrase = state.lastTranscript.takeIf { it.isNotBlank() },
                            context = ctx,
                        )
                    }
                },
            )
            Text(
                "EVENT LOG",
                color = CobaltBright, fontSize = 11.sp, letterSpacing = 2.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
            EventLogList(events = state.eventLog, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DebugStatusGrid(state: AssistantDebugState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlueprintSurface, RoundedCornerShape(10.dp))
            .border(1.dp, BlueprintBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DebugRow("STATE",      state.assistantState.name)
        DebugRow("SPEAKER",    state.speakerId)
        DebugRow("TRUST",      "${state.trustLevel.name.lowercase()} (${(state.identityConfidence * 100).toInt()}%)")
        DebugRow("GATEWAY",    state.gatewayState.name.lowercase())
        DebugRow("AUDIO",      state.activeAudioDevice.name.lowercase())
        if (state.lastTranscript.isNotBlank()) {
            DebugRow("LAST",   state.lastTranscript.take(60))
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextDim, fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.weight(0.35f))
        Text(value, color = TextPrimary, fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.weight(0.65f))
    }
}

@Composable
private fun DebugErrorBanner(error: String) {
    Surface(
        color = StatusOffline.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StatusOffline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "ERR: $error",
            color = StatusOffline,
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
private fun EventLogList(events: List<AssistantEvent>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val reversed  = events.reversed()

    LazyColumn(
        state  = listState,
        modifier = modifier
            .background(BlueprintSurface, RoundedCornerShape(10.dp))
            .border(1.dp, BlueprintBorder, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (reversed.isEmpty()) {
            item {
                Text("No events yet.", color = TextDim, fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(4.dp))
            }
        }
        items(reversed) { event ->
            EventLogRow(event)
        }
    }
}

@Composable
private fun EventLogRow(event: AssistantEvent) {
    val color = if (event.isError) StatusOffline else TextSecondary
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${event.stateFrom.name} → ${event.stateTo.name}",
                color = color,
                fontSize = 10.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = TIME_FMT.format(Date(event.timestamp)),
                color = TextDim,
                fontSize = 9.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
        val detail = event.error ?: event.action
        if (detail != null) {
            Text(
                text = "  $detail",
                color = if (event.isError) StatusOffline.copy(alpha = 0.8f) else CobaltBright,
                fontSize = 9.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
    }
}
