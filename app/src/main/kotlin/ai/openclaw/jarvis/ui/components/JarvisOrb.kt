package ai.openclaw.jarvis.ui.components

import ai.openclaw.jarvis.ui.theme.CobaltDeep
import ai.openclaw.jarvis.ui.theme.CobaltGlow
import ai.openclaw.jarvis.ui.theme.OrbError
import ai.openclaw.jarvis.ui.theme.OrbIdle
import ai.openclaw.jarvis.ui.theme.OrbListening
import ai.openclaw.jarvis.ui.theme.OrbOffline
import ai.openclaw.jarvis.ui.theme.OrbProcessing
import ai.openclaw.jarvis.ui.theme.OrbSpeaking
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-level orb mood — drives the orb's colour + which animation overlay
 * runs. Mapped from `AssistantState` by callers so the orb stays
 * decoupled from the state-machine package.
 */
enum class OrbMood {
    IDLE,                  // calm cobalt, soft pulse
    LISTENING,             // bright pulse + waveform ring
    THINKING,              // rotating segmented ring
    SPEAKING,              // active waveform inside orb
    AWAITING_CONFIRMATION, // slow breathing glow
    ERROR,                 // brief red pulse
    OFFLINE,               // dim amber
}

/**
 * State-driven orb. Replaces the older `VoiceOrb` once callers migrate;
 * the legacy orb is kept for the existing MainScreen until that migrates
 * over. Animations are intentionally low-cost — a single
 * `rememberInfiniteTransition` with two animated floats; everything else
 * is a colour interpolation.
 */
@Composable
fun JarvisOrb(
    mood: OrbMood,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
) {
    val orbColor by animateColorAsState(
        targetValue = when (mood) {
            OrbMood.IDLE -> OrbIdle
            OrbMood.LISTENING -> OrbListening
            OrbMood.THINKING -> OrbProcessing
            OrbMood.SPEAKING -> OrbSpeaking
            OrbMood.AWAITING_CONFIRMATION -> CobaltDeep
            OrbMood.ERROR -> OrbError
            OrbMood.OFFLINE -> OrbOffline
        },
        animationSpec = tween(durationMillis = 320),
        label = "orbColor",
    )

    val transition = rememberInfiniteTransition(label = "orbTransition")
    val pulse by transition.animateFloat(
        initialValue = if (mood == OrbMood.AWAITING_CONFIRMATION) 0.86f else 0.92f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mood) {
                    OrbMood.LISTENING -> 1100
                    OrbMood.AWAITING_CONFIRMATION -> 2400
                    OrbMood.ERROR -> 280
                    OrbMood.OFFLINE -> 1600
                    else -> 1300
                },
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
        ),
        label = "rotation",
    )
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing)),
        label = "wavePhase",
    )

    val glowAlpha = when (mood) {
        OrbMood.IDLE -> 0.16f
        OrbMood.LISTENING -> 0.38f
        OrbMood.THINKING -> 0.22f
        OrbMood.SPEAKING -> 0.30f
        OrbMood.AWAITING_CONFIRMATION -> 0.45f
        OrbMood.ERROR -> 0.35f
        OrbMood.OFFLINE -> 0.10f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { _ ->
                    onPress()
                    tryAwaitRelease()
                    onRelease()
                })
            }
            .semantics { contentDescription = mood.semanticDescription() },
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val radius = (this.size.minDimension / 2f) * 0.62f

            // Outer glow halo.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orbColor.copy(alpha = glowAlpha), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius * 2.2f,
                ),
                radius = radius * 2.2f,
                center = Offset(cx, cy),
            )

            when (mood) {
                OrbMood.LISTENING -> drawWaveformRing(cx, cy, radius + 20f, 12f, wavePhase, orbColor.copy(alpha = 0.65f))
                OrbMood.SPEAKING -> drawWaveformRing(cx, cy, radius + 14f, 16f, wavePhase, orbColor.copy(alpha = 0.7f))
                OrbMood.THINKING -> drawSegmentedRing(cx, cy, radius + 26f, rotation, orbColor)
                OrbMood.AWAITING_CONFIRMATION,
                OrbMood.ERROR,
                OrbMood.OFFLINE,
                OrbMood.IDLE -> Unit
            }

            // Core orb.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CobaltGlow.copy(alpha = 0.85f),
                        orbColor.copy(alpha = 0.7f),
                        orbColor.copy(alpha = 0.35f),
                    ),
                    center = Offset(cx * 0.9f, cy * 0.82f),
                    radius = radius,
                ),
                radius = radius * pulse,
                center = Offset(cx, cy),
            )

            // Highlight.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(cx * 0.85f, cy * 0.78f),
                    radius = radius * 0.45f,
                ),
                radius = radius * 0.45f,
                center = Offset(cx * 0.85f, cy * 0.78f),
            )
        }
    }
}

private fun OrbMood.semanticDescription(): String = when (this) {
    OrbMood.IDLE -> "Idle"
    OrbMood.LISTENING -> "Listening"
    OrbMood.THINKING -> "Thinking"
    OrbMood.SPEAKING -> "Speaking"
    OrbMood.AWAITING_CONFIRMATION -> "Awaiting confirmation"
    OrbMood.ERROR -> "Error"
    OrbMood.OFFLINE -> "Offline"
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
        val wave = amplitude * sin(angle * 6 + phase)
        val r = baseRadius + wave
        val x = cx + r * cos(angle.toDouble()).toFloat()
        val y = cy + r * sin(angle.toDouble()).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = color, style = Stroke(width = 2.dp.toPx()))
}

/** Eight rotating dashes around the orb — the spec's "thinking" cue. */
private fun DrawScope.drawSegmentedRing(
    cx: Float, cy: Float,
    radius: Float,
    rotationDegrees: Float,
    color: Color,
    segments: Int = 12,
) {
    val rad = Math.toRadians(rotationDegrees.toDouble())
    val arcStep = (2 * Math.PI) / segments
    val segLength = arcStep * 0.55
    for (i in 0 until segments) {
        val start = i * arcStep + rad
        val end = start + segLength
        val sx = cx + radius * cos(start).toFloat()
        val sy = cy + radius * sin(start).toFloat()
        val ex = cx + radius * cos(end).toFloat()
        val ey = cy + radius * sin(end).toFloat()
        // Fade trailing segments so the spinning direction is clear.
        val alpha = (0.25f + 0.65f * (i.toFloat() / segments)).coerceIn(0f, 1f)
        drawLine(
            color = color.copy(alpha = alpha),
            start = Offset(sx, sy),
            end = Offset(ex, ey),
            strokeWidth = 3.dp.toPx(),
        )
    }
}
