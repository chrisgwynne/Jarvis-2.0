package ai.openclaw.jarvis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JarvisDarkColorScheme = darkColorScheme(
    primary          = CobaltPrimary,
    onPrimary        = TextPrimary,
    primaryContainer = BlueprintCard,
    onPrimaryContainer = CobaltGlow,

    secondary        = CobaltBright,
    onSecondary      = BlueprintBackground,
    secondaryContainer = BlueprintBorder,
    onSecondaryContainer = TextPrimary,

    background       = BlueprintBackground,
    onBackground     = TextPrimary,
    surface          = BlueprintSurface,
    onSurface        = TextPrimary,
    surfaceVariant   = BlueprintCard,
    onSurfaceVariant = TextSecondary,

    outline          = BlueprintBorder,
    outlineVariant   = GridLineColor,

    error            = StatusOffline,
    onError          = TextPrimary,
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisDarkColorScheme,
        typography  = JarvisTypography,
        content     = content,
    )
}
