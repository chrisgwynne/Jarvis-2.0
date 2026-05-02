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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class OrbMood {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    AWAITING_CONFIRMATION,
    ERROR,
    OFFLINE,
}

@Composable
fun JarvisOrb(
    mood: OrbMood,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    val orbColor by animateColorAsState(
        targetValue = when (mood) {
            OrbMood.IDLE                  -> OrbIdle
            OrbMood.LISTENING             -> OrbListening
            OrbMood.THINKING              -> OrbProcessing
            OrbMood.SPEAKING              -> OrbSpeaking
            OrbMood.AWAITING_CONFIRMATION -> CobaltDeep
            OrbMood.ERROR                 -> OrbError
            OrbMood.OFFLINE               -> OrbOffline
        },
        animationSpec = tween(320),
        label = "orbColor",
    )

    val transition = rememberInfiniteTransition(label = "orb")

    val pulse by transition.animateFloat(
        initialValue = when (mood) {
            OrbMood.AWAITING_CONFIRMATION -> 0.86f
            OrbMood.OFFLINE               -> 0.90f
            else                          -> 0.94f
        },
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mood) {
                    OrbMood.LISTENING             -> 1000
                    OrbMood.AWAITING_CONFIRMATION -> 2400
                    OrbMood.ERROR                 -> 280
                    OrbMood.OFFLINE               -> 1800
                    else                          -> 1400
                },
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
        ),
        label = "rotation",
    )

    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mood) {
                    OrbMood.SPEAKING  -> 550
                    OrbMood.LISTENING -> 850
                    else              -> 1400
                },
                easing = LinearEasing,
            ),
        ),
        label = "wavePhase",
    )

    val glowAlpha = when (mood) {
        OrbMood.IDLE                  -> 0.18f
        OrbMood.LISTENING             -> 0.44f
        OrbMood.THINKING              -> 0.26f
        OrbMood.SPEAKING              -> 0.38f
        OrbMood.AWAITING_CONFIRMATION -> 0.50f
        OrbMood.ERROR                 -> 0.42f
        OrbMood.OFFLINE               -> 0.12f
    }

    val waveAmplitude = when (mood) {
        OrbMood.SPEAKING  -> 1.0f
        OrbMood.LISTENING -> 0.70f
        OrbMood.IDLE      -> 0.12f
        OrbMood.OFFLINE   -> 0.04f
        else              -> 0.18f
    }

    // Canvas spans full width so the side waveform bars have room to breathe.
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(size)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { _ ->
                    onPress()
                    tryAwaitRelease()
                    onRelease()
                })
            }
            .semantics { contentDescription = mood.semanticDescription() },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(size),
        ) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            // Orb radius is derived from the height (not width) so it stays circular.
            val orbRadius = (this.size.height / 2f) * 0.62f

            // ── Outer ambient glow ─────────────────────────────────────────────
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        orbColor.copy(alpha = glowAlpha),
                        orbColor.copy(alpha = glowAlpha * 0.35f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = orbRadius * 2.6f,
                ),
                radius = orbRadius * 2.6f,
                center = Offset(cx, cy),
            )

            // ── Side waveform bars (the signature Jarvis visual) ───────────────
            if (mood != OrbMood.THINKING) {
                drawSideWaveforms(
                    cx = cx,
                    cy = cy,
                    orbRadius = orbRadius,
                    phase = wavePhase,
                    amplitude = waveAmplitude,
                    color = orbColor,
                )
            }

            // ── Thinking: rotating segmented ring ─────────────────────────────
            if (mood == OrbMood.THINKING) {
                drawSegmentedRing(cx, cy, orbRadius + 30f, rotation, orbColor)
            }

            // ── Outer ring: cobalt normally, thin amber warning ring offline ──
            when (mood) {
                OrbMood.OFFLINE -> {
                    // Dark cobalt base ring
                    drawCircle(
                        color = OrbIdle.copy(alpha = 0.18f),
                        radius = orbRadius * 1.10f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                    // Thin amber accent ring — the only amber on offline state
                    drawCircle(
                        color = OrbOffline.copy(alpha = 0.50f),
                        radius = orbRadius * 1.22f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.8.dp.toPx()),
                    )
                }
                else -> drawCircle(
                    color = orbColor.copy(alpha = 0.28f),
                    radius = orbRadius * 1.10f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            }

            // ── Core orb (layered radial gradient) ────────────────────────────
            // Offline: dark navy core so the orb is never a yellow sphere.
            // Error: dark red tint. Other states: full cobalt glow.
            val coreCenter = when (mood) {
                OrbMood.OFFLINE -> Color(0xFF0B1520)          // very dark navy
                OrbMood.ERROR   -> Color(0xFF1C0A0A)          // very dark red
                else            -> CobaltGlow.copy(alpha = 0.92f)
            }
            val coreMid = when (mood) {
                OrbMood.OFFLINE -> OrbIdle.copy(alpha = 0.28f) // dim cobalt — no amber fill
                OrbMood.ERROR   -> OrbError.copy(alpha = 0.55f)
                else            -> orbColor.copy(alpha = 0.78f)
            }
            val coreEdge = when (mood) {
                OrbMood.OFFLINE -> OrbIdle.copy(alpha = 0.10f)
                OrbMood.ERROR   -> OrbError.copy(alpha = 0.30f)
                else            -> orbColor.copy(alpha = 0.42f)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreCenter, coreMid, coreEdge),
                    center = Offset(cx * 0.88f, cy * 0.80f),
                    radius = orbRadius,
                ),
                radius = orbRadius * pulse,
                center = Offset(cx, cy),
            )

            // ── Specular highlight (top-left glass effect) ────────────────────
            // Dimmed on offline/error states
            val highlightAlpha = when (mood) {
                OrbMood.OFFLINE -> 0.10f
                OrbMood.ERROR   -> 0.12f
                else            -> 0.30f
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = highlightAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(cx * 0.82f, cy * 0.74f),
                    radius = orbRadius * 0.40f,
                ),
                radius = orbRadius * 0.40f,
                center = Offset(cx * 0.82f, cy * 0.74f),
            )
        }
    }
}

/**
 * Animated vertical bars extending left and right from the orb edge.
 * Each bar is offset in phase so they ripple outward like a waveform.
 */
private fun DrawScope.drawSideWaveforms(
    cx: Float,
    cy: Float,
    orbRadius: Float,
    phase: Float,
    amplitude: Float,
    color: Color,
) {
    val barCount = 5
    val barWidth = 3.2.dp.toPx()
    val maxBarHeight = orbRadius * 0.60f
    val barSpacing = 9.dp.toPx()
    val gapFromOrb = 14.dp.toPx()

    for (side in listOf(-1f, 1f)) {
        for (i in 0 until barCount) {
            // Phase offset per bar so they animate as a travelling wave
            val barPhase = phase + i * 0.65f
            val waveVal = (sin(barPhase.toDouble()) * 0.5 + 0.5).toFloat()
            // Taper: bars closest to the orb are tallest
            val taper = 1f - (i.toFloat() / barCount) * 0.55f
            val barHeight = maxOf(
                3.dp.toPx(),
                maxBarHeight * amplitude * waveVal * taper,
            )
            val alphaFade = (0.88f - i * 0.14f).coerceIn(0.18f, 0.90f)
            val barX = if (side < 0) {
                cx - orbRadius - gapFromOrb - (i * barSpacing)
            } else {
                cx + orbRadius + gapFromOrb + (i * barSpacing)
            }
            drawLine(
                color = color.copy(alpha = alphaFade),
                start = Offset(barX, cy - barHeight / 2f),
                end = Offset(barX, cy + barHeight / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawSegmentedRing(
    cx: Float,
    cy: Float,
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
        val alpha = (0.25f + 0.65f * (i.toFloat() / segments)).coerceIn(0f, 1f)
        drawLine(
            color = color.copy(alpha = alpha),
            start = Offset(sx, sy),
            end = Offset(ex, ey),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun OrbMood.semanticDescription(): String = when (this) {
    OrbMood.IDLE                  -> "Idle"
    OrbMood.LISTENING             -> "Listening"
    OrbMood.THINKING              -> "Thinking"
    OrbMood.SPEAKING              -> "Speaking"
    OrbMood.AWAITING_CONFIRMATION -> "Awaiting confirmation"
    OrbMood.ERROR                 -> "Error"
    OrbMood.OFFLINE               -> "Offline"
}
