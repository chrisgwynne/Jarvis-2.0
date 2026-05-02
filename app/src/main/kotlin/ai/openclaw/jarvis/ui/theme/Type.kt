package ai.openclaw.jarvis.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Spec-aligned typography. Body uses [FontFamily.Default] (Roboto on
 * Android), which the spec asks for as the fallback when Inter isn't
 * bundled. Monospace is reserved for technical / debug surfaces only —
 * it's exposed through the labelSmall + bodySmall styles so the
 * blueprint feel survives in the right places (status chips, debug
 * rows) without overrunning the rest of the app.
 */
val JarvisTypography = Typography(
    displayLarge = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Light,
        fontSize    = 48.sp,
        lineHeight  = 56.sp,
        color       = TextPrimary,
    ),
    headlineLarge = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 28.sp,
        lineHeight  = 36.sp,
        color       = TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 22.sp,
        lineHeight  = 28.sp,
        color       = TextPrimary,
    ),
    titleLarge = TextStyle(    // App title — 22sp bold
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Bold,
        fontSize    = 22.sp,
        lineHeight  = 28.sp,
        color       = TextPrimary,
    ),
    titleMedium = TextStyle(   // Screen title — 18sp semibold
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 18.sp,
        lineHeight  = 24.sp,
        color       = TextPrimary,
    ),
    titleSmall = TextStyle(    // Section title — 14sp semibold
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 14.sp,
        lineHeight  = 20.sp,
        letterSpacing = 0.4.sp,
        color       = TextSecondary,
    ),
    bodyLarge = TextStyle(     // Body — 15sp regular (spec)
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Normal,
        fontSize    = 15.sp,
        lineHeight  = 22.sp,
        color       = TextPrimary,
    ),
    bodyMedium = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Normal,
        fontSize    = 14.sp,
        lineHeight  = 20.sp,
        color       = TextSecondary,
    ),
    bodySmall = TextStyle(     // Caption — 12sp regular
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Normal,
        fontSize    = 12.sp,
        lineHeight  = 18.sp,
        color       = TextDim,
    ),
    labelLarge = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Medium,
        fontSize    = 13.sp,
        lineHeight  = 18.sp,
        letterSpacing = 0.4.sp,
        color       = CobaltBright,
    ),
    labelMedium = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Medium,
        fontSize    = 12.sp,
        lineHeight  = 16.sp,
        letterSpacing = 0.5.sp,
        color       = TextSecondary,
    ),
    labelSmall = TextStyle(    // Tiny status — 11sp, monospaced for chips
        fontFamily  = FontFamily.Monospace,
        fontWeight  = FontWeight.Medium,
        fontSize    = 11.sp,
        lineHeight  = 14.sp,
        letterSpacing = 0.6.sp,
        color       = TextDim,
    ),
)
