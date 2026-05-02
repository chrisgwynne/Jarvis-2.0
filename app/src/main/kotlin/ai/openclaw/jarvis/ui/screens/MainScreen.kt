package ai.openclaw.jarvis.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
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
import ai.openclaw.jarvis.proactive.ui.ProactiveSuggestionChip
import ai.openclaw.jarvis.trust.TrustLevel
import ai.openclaw.jarvis.ui.components.*
import ai.openclaw.jarvis.ui.theme.*
import ai.openclaw.jarvis.ui.viewmodel.MainViewModel
import ai.openclaw.jarvis.voice.VoiceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDebug: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    val voiceState          by viewModel.voiceState.collectAsStateWithLifecycle()
    val transcript          by viewModel.transcript.collectAsStateWithLifecycle()
    val partialText         by viewModel.partialText.collectAsStateWithLifecycle()
    val gatewayState        by viewModel.gatewayState.collectAsStateWithLifecycle()
    val lastRoute           by viewModel.lastRoute.collectAsStateWithLifecycle()
    val queueSize           by viewModel.queueSize.collectAsStateWithLifecycle()
    val lastResult          by viewModel.lastResult.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val pairingChallenge by viewModel.pairingChallenge.collectAsStateWithLifecycle()
    val sessionTrust        by viewModel.sessionTrust.collectAsStateWithLifecycle()
    val debugEnabled        by viewModel.debugLogsEnabled.collectAsStateWithLifecycle()

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
                    if (debugEnabled) {
                        IconButton(onClick = onNavigateToDebug) {
                            Icon(Icons.Default.BugReport, "Debug", tint = CobaltBright)
                        }
                    }
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

            // Speaker identity badge
            sessionTrust?.let { trust ->
                if (trust.isActive) {
                    val trustColor = when (trust.trustLevel) {
                        TrustLevel.OWNER   -> CobaltGlow
                        TrustLevel.TRUSTED -> StatusConnected
                        TrustLevel.GUEST   -> StatusQueued
                        TrustLevel.UNKNOWN -> TextDim
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${trust.speakerId} · ${trust.trustLevel.name.lowercase()}",
                            color = trustColor,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            letterSpacing = 1.sp,
                        )
                    }
                }
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

            // Active proactive suggestion (chip-style; renders nothing when none).
            ProactiveSuggestionChip(onAccept = { /* user-side act-on hook for the future */ })

            Spacer(Modifier.height(8.dp))

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

        // ── Confirmation dialog ───────────────────────────────────────────────
        pendingConfirmation?.let { req ->
            ConfirmationDialog(
                title         = "Confirm Action",
                message       = req.summary,
                isDestructive = true,
                onConfirm     = viewModel::confirmPending,
                onDismiss     = viewModel::dismissConfirmation,
            )
        }

        // ── Pairing challenge dialog ──────────────────────────────────────────
        // Surfaced when OpenClaw asks the user to confirm a freshly-issued
        // pairing code. Read-only; the user verifies the code matches what
        // OpenClaw shows, then dismisses to acknowledge.
        pairingChallenge?.let { challenge ->
            ConfirmationDialog(
                title = "Pair with OpenClaw",
                message = "Confirm this code matches what OpenClaw is showing:\n\n" +
                    "    ${challenge.code}",
                isDestructive = false,
                onConfirm = viewModel::acknowledgePairingChallenge,
                onDismiss = viewModel::acknowledgePairingChallenge,
            )
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
