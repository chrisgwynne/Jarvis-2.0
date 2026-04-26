package ai.openclaw.jarvis.ui.screens

import ai.openclaw.jarvis.data.models.GatewayState
import ai.openclaw.jarvis.ui.components.BlueprintCard
import ai.openclaw.jarvis.ui.components.ConnectionDot
import ai.openclaw.jarvis.ui.components.JarvisOrb
import ai.openclaw.jarvis.ui.components.OrbMood
import ai.openclaw.jarvis.ui.components.OutlineButton
import ai.openclaw.jarvis.ui.components.PrimaryButton
import ai.openclaw.jarvis.ui.components.SectionHeader
import ai.openclaw.jarvis.ui.components.StatusChip
import ai.openclaw.jarvis.ui.components.ChipStatus
import ai.openclaw.jarvis.ui.components.TrustChip
import ai.openclaw.jarvis.ui.components.routeWord
import ai.openclaw.jarvis.ui.theme.BlueprintBackground
import ai.openclaw.jarvis.ui.theme.CobaltBright
import ai.openclaw.jarvis.ui.theme.CobaltGlow
import ai.openclaw.jarvis.ui.theme.StatusOffline
import ai.openclaw.jarvis.ui.theme.TextDim
import ai.openclaw.jarvis.ui.theme.TextPrimary
import ai.openclaw.jarvis.ui.theme.TextSecondary
import ai.openclaw.jarvis.ui.viewmodel.MainViewModel
import ai.openclaw.jarvis.voice.VoiceState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Calm, assistant-first home. The spec is explicit: only one active
 * primary card, no capability dashboard, no debug. The screen reacts
 * to assistant state by changing the orb's mood, the title text, and
 * the status colour.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenSuggestions: () -> Unit,
    onApproveCurrentAction: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val gatewayState by viewModel.gatewayState.collectAsStateWithLifecycle()
    val transcript by viewModel.transcript.collectAsStateWithLifecycle()
    val partialText by viewModel.partialText.collectAsStateWithLifecycle()
    val sessionTrust by viewModel.sessionTrust.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()

    val mood = pickMood(voiceState, gatewayState)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BlueprintBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeTopBar(
                onMenu = onOpenSettings,
                connected = gatewayState == GatewayState.CONNECTED,
                speakerName = sessionTrust?.speakerId ?: "guest",
                trustLevel = sessionTrust?.trustLevel?.name ?: "UNKNOWN",
            )

            Spacer(Modifier.height(24.dp))

            // ─── State title ───────────────────────────────────────────
            Text(
                text = stateTitle(mood),
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = contextLine(gatewayState, sessionTrust),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )

            Spacer(Modifier.height(28.dp))

            // ─── Orb ────────────────────────────────────────────────────
            JarvisOrb(
                mood = mood,
                onPress = viewModel::onPttPress,
                onRelease = viewModel::onPttRelease,
                size = 200.dp,
            )

            Spacer(Modifier.height(16.dp))

            // ─── State sub-line / partial transcript ───────────────────
            if (partialText.isNotBlank() && voiceState == VoiceState.LISTENING) {
                Text(
                    text = "“$partialText”",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
            } else {
                Text(
                    text = stateHint(mood),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextDim,
                )
            }

            Spacer(Modifier.height(28.dp))

            // ─── One active primary card ───────────────────────────────
            //     Either the pending confirmation summary OR the latest
            //     interaction. Never both at once — keeps Home calm.
            if (pendingConfirmation != null) {
                PendingPrimaryCard(
                    summary = pendingConfirmation!!.summary,
                    onApprove = onApproveCurrentAction,
                )
            } else {
                LatestInteractionCard(
                    user = transcript.lastOrNull { it.speaker == "user" }?.text,
                    jarvis = transcript.lastOrNull { it.speaker == "jarvis" }?.text,
                    route = transcript.lastOrNull { it.speaker == "jarvis" }?.route,
                )
            }

            Spacer(Modifier.weight(1f))
        }

        // ─── Mic FAB pinned bottom-centre ──────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingActionButton(
                onClick = viewModel::onPttPress,
                containerColor = CobaltBright,
                contentColor = TextPrimary,
                shape = CircleShape,
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "Tap to speak")
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    onMenu: () -> Unit,
    connected: Boolean,
    speakerName: String,
    trustLevel: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = TextSecondary)
        }
        Spacer(Modifier.width(4.dp))
        Text("JARVIS", style = MaterialTheme.typography.titleLarge,
            color = TextPrimary)
        Spacer(Modifier.width(8.dp))
        ConnectionDot(connected)
        Spacer(Modifier.weight(1f))
        TrustChip(speakerName = speakerName, trustLevel = trustLevel)
    }
}

@Composable
private fun PendingPrimaryCard(summary: String, onApprove: () -> Unit) {
    BlueprintCard(
        modifier = Modifier.fillMaxWidth(),
        glowing = true,
    ) {
        Column {
            SectionHeader("Pending action")
            Spacer(Modifier.height(4.dp))
            Text(summary, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
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
        BlueprintCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Tap and hold the mic, or say the wake word to start.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextDim,
            )
        }
        return
    }
    BlueprintCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            SectionHeader("Latest")
            user?.let {
                Text("You", style = MaterialTheme.typography.labelMedium, color = CobaltBright)
                Text(it, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
            }
            jarvis?.let {
                Text("Jarvis", style = MaterialTheme.typography.labelMedium, color = CobaltGlow)
                Text(it, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            }
            val word = routeWord(route)
            if (word.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(word, style = MaterialTheme.typography.bodySmall, color = TextDim)
            }
        }
    }
}

private fun pickMood(state: VoiceState, gateway: GatewayState): OrbMood = when {
    gateway != GatewayState.CONNECTED && gateway != GatewayState.CONNECTING -> OrbMood.OFFLINE
    state == VoiceState.LISTENING -> OrbMood.LISTENING
    state == VoiceState.PROCESSING -> OrbMood.THINKING
    state == VoiceState.SPEAKING -> OrbMood.SPEAKING
    else -> OrbMood.IDLE
}

private fun stateTitle(mood: OrbMood): String = when (mood) {
    OrbMood.IDLE -> "Ready"
    OrbMood.LISTENING -> "Listening"
    OrbMood.THINKING -> "Thinking"
    OrbMood.SPEAKING -> "Speaking"
    OrbMood.AWAITING_CONFIRMATION -> "Awaiting confirmation"
    OrbMood.ERROR -> "Something went wrong"
    OrbMood.OFFLINE -> "Offline"
}

private fun stateHint(mood: OrbMood): String = when (mood) {
    OrbMood.IDLE -> "What's on your mind?"
    OrbMood.LISTENING -> "Go ahead."
    OrbMood.THINKING -> "Analysing with OpenClaw…"
    OrbMood.SPEAKING -> "Here's what I found."
    OrbMood.AWAITING_CONFIRMATION -> "Yes or no — your call."
    OrbMood.ERROR -> "Tap to try again."
    OrbMood.OFFLINE -> "Local actions still work."
}

private fun contextLine(
    gateway: GatewayState,
    trust: ai.openclaw.jarvis.trust.SessionTrust?,
): String {
    val parts = mutableListOf<String>()
    parts += if (gateway == GatewayState.CONNECTED) "Connected" else "Offline"
    trust?.let { parts += "${it.speakerId} (${it.trustLevel.name})" }
    return parts.joinToString(" • ")
}

@Suppress("unused")
private val UnusedColorRef = StatusOffline // kept for potential error-card composition
