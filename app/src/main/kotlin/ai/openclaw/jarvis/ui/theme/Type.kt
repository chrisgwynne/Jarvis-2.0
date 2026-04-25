package ai.openclaw.jarvis.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val JarvisTypography = Typography(
    displayLarge = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontWeight  = FontWeight.Light,
        fontSize    = 57.sp,
        lineHeight  = 64.sp,
        color       = TextPrimary,
    ),
    headlineLarge = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontWeight  = FontWeight.Normal,
        fontSize    = 32.sp,
        lineHeight  = 40.sp,
        color       = TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontWeight  = FontWeight.Normal,
        fontSize    = 24.sp,
        lineHeight  = 32.sp,
        color       = TextPrimary,
    ),
    titleLarge = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontWeight  = FontWeight.Medium,
        fontSize    = 22.sp,
        lineHeight  = 28.sp,
        color       = TextPrimary,
    ),
    titleMedium = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontWeight  = FontWeight.Medium,
        fontSize    = 16.sp,
        lineHeight  = 24.sp,
        letterSpacing = 0.15.sp,
        color       = TextPrimary,
    ),
    bodyLarge = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontWeight  = FontWeight.Normal,
        fontSize    = 16.sp,
        lineHeight  = 24.sp,
        color       = TextPrimary,
    ),
    bodyMedium = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontWeight  = FontWeight.Normal,
        fontSize    = 14.sp,
        lineHeight  = 20.sp,
        color       = TextSecondary,
    ),
    bodySmall = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontWeight  = FontWeight.Normal,
        fontSize    = 12.sp,
        lineHeight  = 16.sp,
        color       = TextDim,
    ),
    labelLarge = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontWeight  = FontWeight.Medium,
        fontSize    = 14.sp,
        lineHeight  = 20.sp,
        letterSpacing = 0.1.sp,
        color       = CobaltBright,
    ),
    labelMedium = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontWeight  = FontWeight.Medium,
        fontSize    = 12.sp,
        lineHeight  = 16.sp,
        letterSpacing = 0.5.sp,
        color       = TextSecondary,
    ),
    labelSmall = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontWeight  = FontWeight.Normal,
        fontSize    = 11.sp,
        lineHeight  = 16.sp,
        letterSpacing = 0.5.sp,
        color       = TextDim,
    ),
)
