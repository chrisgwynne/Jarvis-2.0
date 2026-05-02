package ai.openclaw.jarvis.ui.screens

import ai.openclaw.jarvis.data.models.GatewayState
import ai.openclaw.jarvis.network.ConnectionFailure
import ai.openclaw.jarvis.trust.TrustLevel
import ai.openclaw.jarvis.ui.components.BlueprintCard
import ai.openclaw.jarvis.ui.components.JarvisOrb
import ai.openclaw.jarvis.ui.components.ListeningControlsSheet
import ai.openclaw.jarvis.ui.components.ListeningModeChip
import ai.openclaw.jarvis.ui.components.OrbMood
import ai.openclaw.jarvis.ui.components.PrimaryButton
import ai.openclaw.jarvis.ui.components.routeWord
import ai.openclaw.jarvis.voice.ListeningMode
import ai.openclaw.jarvis.ui.theme.BlueprintBackground
import ai.openclaw.jarvis.ui.theme.BlueprintBorder
import ai.openclaw.jarvis.ui.theme.CobaltBright
import ai.openclaw.jarvis.ui.theme.CobaltGlow
import ai.openclaw.jarvis.ui.theme.StatusConnected
import ai.openclaw.jarvis.ui.theme.StatusOffline
import ai.openclaw.jarvis.ui.theme.StatusWarning
import ai.openclaw.jarvis.ui.theme.TextDim
import ai.openclaw.jarvis.ui.theme.TextPrimary
import ai.openclaw.jarvis.ui.theme.TextSecondary
import ai.openclaw.jarvis.ui.viewmodel.MainViewModel
import ai.openclaw.jarvis.voice.VoiceState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeScreen(
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onOpenSuggestions: () -> Unit,
    onApproveCurrentAction: () -> Unit,
    onOpenDiagnostics: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    val voiceState          by viewModel.voiceState.collectAsStateWithLifecycle()
    val gatewayState        by viewModel.gatewayState.collectAsStateWithLifecycle()
    val transcript          by viewModel.transcript.collectAsStateWithLifecycle()
    val partialText         by viewModel.partialText.collectAsStateWithLifecycle()
    val sessionTrust        by viewModel.sessionTrust.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val lastFailure         by viewModel.lastFailure.collectAsStateWithLifecycle()
    val listeningMode       by viewModel.listeningMode.collectAsStateWithLifecycle()

    var showListeningSheet by remember { mutableStateOf(false) }

    val mood = pickMood(voiceState, gatewayState)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BlueprintBackground),
    ) {
        if (showListeningSheet) {
            ListeningControlsSheet(
                currentMode  = listeningMode,
                onDismiss    = { showListeningSheet = false },
                onSetActive  = viewModel::setListeningActive,
                onSetSilent  = viewModel::setListeningSilent,
                onSetStopped = viewModel::setListeningStopped,
                onPause10Min = viewModel::pauseListening10Min,
                onPause1Hour = viewModel::pauseListening1Hour,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Top bar: Menu | JARVIS (centred) | Settings ────────────────────
            HomeTopBar(
                onMenu     = onMenuClick,
                onSettings = onSettingsClick,
            )

            Spacer(Modifier.height(4.dp))

            // ── Speaker identity pill ──────────────────────────────────────────
            sessionTrust?.let { trust ->
                val dotColor = when (trust.trustLevel) {
                    TrustLevel.OWNER, TrustLevel.TRUSTED -> StatusConnected
                    TrustLevel.GUEST                     -> StatusWarning
                    TrustLevel.UNKNOWN                   -> TextDim
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    Text(
                        text = "${trust.speakerId} (${trust.trustLevel.name
                            .lowercase()
                            .replaceFirstChar { it.uppercase() }})",
                        color = dotColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.3.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Orb (full-width canvas with side waveform bars) ───────────────
            JarvisOrb(
                mood      = mood,
                onPress   = viewModel::onPttPress,
                onRelease = viewModel::onPttRelease,
                size      = 210.dp,
            )

            Spacer(Modifier.height(20.dp))

            // ── State title (animated) ────────────────────────────────────────
            AnimatedContent(
                targetState = stateTitle(mood),
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "stateTitle",
            ) { title ->
                Text(
                    text      = title,
                    color     = TextPrimary,
                    fontSize  = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(6.dp))

            // ── Hint / partial transcript ─────────────────────────────────────
            AnimatedContent(
                targetState = if (partialText.isNotBlank() && voiceState == VoiceState.LISTENING)
                    "“$partialText”"
                else
                    stateHint(mood),
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                label = "stateHint",
            ) { hint ->
                Text(
                    text      = hint,
                    color     = TextSecondary,
                    fontSize  = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Connection status dots ────────────────────────────────────────
            ConnectionStatusRow(gatewayState = gatewayState)

            Spacer(Modifier.height(10.dp))

            // ── Listening mode chip ───────────────────────────────────────────
            ListeningModeChip(
                mode    = listeningMode,
                onClick = { showListeningSheet = true },
            )

            Spacer(Modifier.height(18.dp))

            // ── Single primary card ───────────────────────────────────────────
            when {
                pendingConfirmation != null -> PendingPrimaryCard(
                    summary   = pendingConfirmation!!.summary,
                    onApprove = onApproveCurrentAction,
                )
                mood == OrbMood.OFFLINE -> LocalModeCard(
                    lastFailure = lastFailure,
                    onOpenDiagnostics = onOpenDiagnostics,
                    onOpenSettings = onSettingsClick,
                    onRetry = viewModel::retryConnection,
                )
                else -> LatestInteractionCard(
                    user   = transcript.lastOrNull { it.speaker == "user" }?.text,
                    jarvis = transcript.lastOrNull { it.speaker == "jarvis" }?.text,
                    route  = transcript.lastOrNull { it.speaker == "jarvis" }?.route,
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

// ─── Top bar ──────────────────────────────────────────────────────────────────

@Composable
private fun HomeTopBar(
    onMenu: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        IconButton(
            onClick  = onMenu,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = TextSecondary)
        }

        Text(
            text      = "JARVIS",
            modifier  = Modifier.align(Alignment.Center),
            color     = CobaltGlow,
            fontSize  = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 6.sp,
        )

        IconButton(
            onClick  = onSettings,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextSecondary)
        }
    }
}

// ─── Connection status dots ───────────────────────────────────────────────────

@Composable
private fun ConnectionStatusRow(gatewayState: GatewayState) {
    val (label, color) = when (gatewayState) {
        GatewayState.CONNECTED   -> "Connected" to StatusConnected
        GatewayState.CONNECTING  -> "Connecting…" to StatusWarning
        GatewayState.DISCONNECTED -> "Offline" to StatusOffline
        else                     -> "Offline" to StatusOffline
    }
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        StatusPill(label = label, color = color)
        if (gatewayState == GatewayState.CONNECTED) {
            Spacer(Modifier.width(14.dp))
            StatusPill(label = "OpenClaw", color = CobaltBright)
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text      = label,
            color     = color.copy(alpha = 0.85f),
            fontSize  = 12.sp,
            letterSpacing = 0.2.sp,
        )
    }
}

// ─── Cards ────────────────────────────────────────────────────────────────────

@Composable
private fun PendingPrimaryCard(summary: String, onApprove: () -> Unit) {
    BlueprintCard(
        modifier = Modifier.fillMaxWidth(),
        glowing  = true,
    ) {
        Column {
            Text(
                text      = "PENDING ACTION",
                color     = CobaltBright,
                fontSize  = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(summary, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Open", onClick = onApprove, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LatestInteractionCard(
    user: String?,
    jarvis: String?,
    route: String?,
) {
    if (user == null && jarvis == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0E1620))
                .border(1.dp, BlueprintBorder, RoundedCornerShape(16.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text      = "Tap and hold the orb, or say the wake word.",
                color     = TextDim,
                fontSize  = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    BlueprintCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text      = "LATEST",
                color     = TextDim,
                fontSize  = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(10.dp))
            user?.let { userText ->
                Text(
                    text  = "You",
                    color = CobaltBright,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = userText,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(12.dp))
            }
            jarvis?.let { jarvisText ->
                Text(
                    text  = "Jarvis",
                    color = CobaltGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = jarvisText,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
            }
            val word = routeWord(route)
            if (word.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text  = word,
                    color = TextDim,
                    fontSize = 11.sp,
                    letterSpacing = 0.3.sp,
                )
            }
        }
    }
}

@Composable
private fun LocalModeCard(
    lastFailure: ConnectionFailure?,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    BlueprintCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text       = "OFFLINE",
                color      = StatusOffline,
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(6.dp))

            if (lastFailure != null) {
                Text(
                    text      = lastFailure.humanReadable(),
                    color     = TextPrimary,
                    fontSize  = 14.sp,
                    lineHeight = 20.sp,
                )
                val fix = lastFailure.suggestedFix()
                if (fix.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = fix,
                        color = StatusWarning,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            } else {
                Text(
                    text  = "OpenClaw is unreachable. Local phone actions still work.",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text  = "Torch • Apps • Timers • Calls • Volume • Location",
                color = TextSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRetry,
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = CobaltBright),
                    border  = BorderStroke(1.dp, CobaltBright.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text("Retry", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onOpenDiagnostics,
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border  = BorderStroke(1.dp, BlueprintBorder),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text("Diagnose", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border  = BorderStroke(1.dp, BlueprintBorder),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text("Settings", fontSize = 12.sp)
                }
            }
        }
    }
}

// ─── State helpers ────────────────────────────────────────────────────────────

private fun pickMood(state: VoiceState, gateway: GatewayState): OrbMood = when {
    gateway != GatewayState.CONNECTED && gateway != GatewayState.CONNECTING -> OrbMood.OFFLINE
    state == VoiceState.LISTENING  -> OrbMood.LISTENING
    state == VoiceState.PROCESSING -> OrbMood.THINKING
    state == VoiceState.SPEAKING   -> OrbMood.SPEAKING
    else                           -> OrbMood.IDLE
}

private fun stateTitle(mood: OrbMood): String = when (mood) {
    OrbMood.IDLE                  -> "Ready"
    OrbMood.LISTENING             -> "Listening"
    OrbMood.THINKING              -> "Thinking"
    OrbMood.SPEAKING              -> "Speaking"
    OrbMood.AWAITING_CONFIRMATION -> "Awaiting Confirmation"
    OrbMood.ERROR                 -> "Something went wrong"
    OrbMood.OFFLINE               -> "Offline"
}

private fun stateHint(mood: OrbMood): String = when (mood) {
    OrbMood.IDLE                  -> "What’s on your mind?"
    OrbMood.LISTENING             -> "Go ahead."
    OrbMood.THINKING              -> "Analysing with OpenClaw…"
    OrbMood.SPEAKING              -> "Here’s what I found."
    OrbMood.AWAITING_CONFIRMATION -> "Yes or no — your call."
    OrbMood.ERROR                 -> "Tap to try again."
    OrbMood.OFFLINE               -> "Local actions still work."
}
