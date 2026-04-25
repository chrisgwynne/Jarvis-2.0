package ai.openclaw.jarvis.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.openclaw.jarvis.ui.theme.*
import ai.openclaw.jarvis.voice.VoiceState
import kotlin.math.sin

@Composable
fun VoiceOrb(
    voiceState: VoiceState,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
) {
    val orbColor by animateColorAsState(
        targetValue = when (voiceState) {
            VoiceState.IDLE       -> OrbIdle
            VoiceState.LISTENING  -> OrbListening
            VoiceState.PROCESSING -> OrbProcessing
            VoiceState.SPEAKING   -> OrbSpeaking
        },
        animationSpec = tween(300),
        label = "orbColor",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
        ),
        label = "wavePhase",
    )

    val glowAlpha = when (voiceState) {
        VoiceState.IDLE       -> 0.15f
        VoiceState.LISTENING  -> 0.35f
        VoiceState.PROCESSING -> 0.20f
        VoiceState.SPEAKING   -> 0.30f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { _ ->
                        onPress()
                        tryAwaitRelease()
                        onRelease()
                    }
                )
            },
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val cx    = this.size.width / 2f
            val cy    = this.size.height / 2f
            val radius = (this.size.minDimension / 2f) * 0.72f

            // Outer glow ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orbColor.copy(alpha = glowAlpha), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius * 1.8f,
                ),
                radius = radius * 1.8f,
                center = Offset(cx, cy),
            )

            // Waveform ring (only visible when listening/speaking)
            if (voiceState == VoiceState.LISTENING || voiceState == VoiceState.SPEAKING) {
                drawWaveformRing(
                    cx = cx, cy = cy,
                    baseRadius = radius + 20f,
                    amplitude  = if (voiceState == VoiceState.LISTENING) 12f else 8f,
                    phase      = wavePhase,
                    color      = orbColor.copy(alpha = 0.6f),
                )
            }

            // Main orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CobaltGlow.copy(alpha = 0.9f),
                        orbColor.copy(alpha = 0.7f),
                        orbColor.copy(alpha = 0.4f),
                    ),
                    center = Offset(cx * 0.9f, cy * 0.8f),
                    radius = radius,
                ),
                radius = radius * pulse,
                center = Offset(cx, cy),
            )

            // Inner highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(cx * 0.85f, cy * 0.75f),
                    radius = radius * 0.45f,
                ),
                radius = radius * 0.45f,
                center = Offset(cx * 0.85f, cy * 0.75f),
            )
        }
    }
}

private fun DrawScope.drawWaveformRing(
    cx: Float, cy: Float,
    baseRadius: Float,
    amplitude: Float,
    phase: Float,
    color: Color,
    steps: Int = 120,
) {
    val path = Path()
    val step = (2 * Math.PI / steps).toFloat()
    for (i in 0..steps) {
        val angle = i * step
        val wave  = amplitude * sin(angle * 6 + phase).toFloat()
        val r     = baseRadius + wave
        val x     = cx + r * kotlin.math.cos(angle.toDouble()).toFloat()
        val y     = cy + r * kotlin.math.sin(angle.toDouble()).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(
        path   = path,
        color  = color,
        style  = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
    )
}
