package ai.openclaw.jarvis.ui.components

import ai.openclaw.jarvis.ui.theme.BlueprintBorder
import ai.openclaw.jarvis.ui.theme.CobaltBright
import ai.openclaw.jarvis.ui.theme.StatusConnected
import ai.openclaw.jarvis.ui.theme.StatusOffline
import ai.openclaw.jarvis.ui.theme.StatusWarning
import ai.openclaw.jarvis.ui.theme.TextDim
import ai.openclaw.jarvis.ui.theme.TextPrimary
import ai.openclaw.jarvis.ui.theme.TextSecondary
import ai.openclaw.jarvis.voice.ListeningMode
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ListeningModeChip(
    mode: ListeningMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when (mode) {
        ListeningMode.Active         -> "Listening" to StatusConnected
        ListeningMode.Silent         -> "Silent" to StatusWarning
        is ListeningMode.Paused      -> "Paused" to StatusWarning
        ListeningMode.Stopped        -> "Stopped" to StatusOffline
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            color = color.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningControlsSheet(
    currentMode: ListeningMode,
    onDismiss: () -> Unit,
    onSetActive: () -> Unit,
    onSetSilent: () -> Unit,
    onSetStopped: () -> Unit,
    onPause10Min: () -> Unit,
    onPause1Hour: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "LISTENING CONTROLS",
                color = TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(4.dp))

            SheetButton(
                label   = "Start Listening",
                color   = StatusConnected,
                enabled = currentMode != ListeningMode.Active,
                onClick = { onSetActive(); onDismiss() },
            )
            SheetButton(
                label   = "Silence Jarvis",
                color   = StatusWarning,
                enabled = currentMode != ListeningMode.Silent,
                onClick = { onSetSilent(); onDismiss() },
            )
            SheetButton(
                label   = "Pause 10 minutes",
                color   = CobaltBright,
                onClick = { onPause10Min(); onDismiss() },
            )
            SheetButton(
                label   = "Pause 1 hour",
                color   = CobaltBright,
                onClick = { onPause1Hour(); onDismiss() },
            )
            SheetButton(
                label   = "Stop Listening",
                color   = StatusOffline,
                enabled = currentMode != ListeningMode.Stopped,
                onClick = { onSetStopped(); onDismiss() },
            )
        }
    }
}

@Composable
private fun SheetButton(
    label: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors   = ButtonDefaults.outlinedButtonColors(contentColor = if (enabled) color else TextDim),
        border   = BorderStroke(1.dp, if (enabled) color.copy(alpha = 0.5f) else BlueprintBorder),
        shape    = RoundedCornerShape(10.dp),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
