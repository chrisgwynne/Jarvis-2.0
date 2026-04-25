package ai.openclaw.jarvis.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.openclaw.jarvis.data.models.GatewayState
import ai.openclaw.jarvis.data.models.RouteChoice
import ai.openclaw.jarvis.ui.components.*
import ai.openclaw.jarvis.ui.theme.*
import ai.openclaw.jarvis.ui.viewmodel.MainViewModel
import ai.openclaw.jarvis.voice.VoiceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val voiceState     by viewModel.voiceState.collectAsStateWithLifecycle()
    val transcript     by viewModel.transcript.collectAsStateWithLifecycle()
    val partialText    by viewModel.partialText.collectAsStateWithLifecycle()
    val gatewayState   by viewModel.gatewayState.collectAsStateWithLifecycle()
    val lastRoute      by viewModel.lastRoute.collectAsStateWithLifecycle()
    val queueSize      by viewModel.queueSize.collectAsStateWithLifecycle()
    val lastResult     by viewModel.lastResult.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = BlueprintBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text     = "JARVIS",
                        style    = MaterialTheme.typography.titleLarge,
                        color    = CobaltGlow,
                        letterSpacing = 4.sp,
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector        = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint               = TextSecondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BlueprintBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .drawBehind { drawBlueprintGrid() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Status row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionStatusBadge(state = gatewayState, queueSize = queueSize)
                RouteChip(route = lastRoute)
            }

            Spacer(Modifier.height(16.dp))

            // Voice state label
            AnimatedContent(
                targetState = voiceState,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                },
                label = "voiceLabel",
            ) { state ->
                Text(
                    text = when (state) {
                        VoiceState.IDLE       -> "hold to speak"
                        VoiceState.LISTENING  -> "listening…"
                        VoiceState.PROCESSING -> "processing…"
                        VoiceState.SPEAKING   -> "speaking…"
                    },
                    color    = TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 1.sp,
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Voice Orb ────────────────────────────────────────────────────
            VoiceOrb(
                voiceState = voiceState,
                onPress    = viewModel::onPttPress,
                onRelease  = viewModel::onPttRelease,
                size       = 160.dp,
            )

            Spacer(Modifier.height(24.dp))

            // Last result banner
            AnimatedVisibility(visible = lastResult != null) {
                lastResult?.let { result ->
                    Surface(
                        color        = if (result.startsWith("Error") || result.startsWith("Couldn"))
                            StatusOffline.copy(alpha = 0.1f) else CobaltPrimary.copy(alpha = 0.1f),
                        shape        = MaterialTheme.shapes.small,
                        border       = BorderStroke(
                            1.dp,
                            if (result.startsWith("Error") || result.startsWith("Couldn"))
                                StatusOffline.copy(alpha = 0.4f) else BlueprintBorder,
                        ),
                        modifier     = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text     = result,
                            color    = TextSecondary,
                            fontSize = 13.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Transcript panel — fills remaining space
            TranscriptPanel(
                entries     = transcript,
                partialText = partialText,
                modifier    = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun DrawScope.drawBlueprintGrid() {
    val gridSpacing = 40f
    val color = GridLineColor
    var x = 0f
    while (x <= size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.5f)
        x += gridSpacing
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f)
        y += gridSpacing
    }
}
