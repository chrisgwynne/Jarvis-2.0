package ai.openclaw.jarvis.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openclaw.jarvis.data.models.GatewayState
import ai.openclaw.jarvis.ui.theme.*

@Composable
fun ConnectionStatusBadge(
    state: GatewayState,
    queueSize: Int = 0,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when (state) {
        GatewayState.CONNECTED      -> "Connected" to StatusConnected
        GatewayState.CONNECTING     -> "Connecting…" to StatusPairing
        GatewayState.PAIRING        -> "Pairing…" to StatusPairing
        GatewayState.OFFLINE_QUEUED -> "Offline ($queueSize queued)" to StatusQueued
        GatewayState.DISCONNECTED   -> "Offline" to StatusOffline
    }

    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(400),
        label = "statusColor",
    )

    val isPulsing = state == GatewayState.CONNECTING || state == GatewayState.PAIRING
    val alpha by if (isPulsing) {
        rememberInfiniteTransition(label = "statusPulse")
            .animateFloat(
                initialValue = 0.4f,
                targetValue  = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pulseAlpha",
            )
    } else remember { mutableStateOf(1.0f) }

    Row(
        modifier = modifier
            .background(BlueprintCard, RoundedCornerShape(20.dp))
            .border(1.dp, animatedColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(alpha)
                .background(animatedColor, CircleShape)
        )
        Text(
            text  = label,
            color = animatedColor,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}
