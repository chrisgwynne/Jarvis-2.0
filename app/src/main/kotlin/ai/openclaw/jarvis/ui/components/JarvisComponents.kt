package ai.openclaw.jarvis.ui.components

import ai.openclaw.jarvis.ui.theme.BlueprintBorder
import ai.openclaw.jarvis.ui.theme.BlueprintCard
import ai.openclaw.jarvis.ui.theme.CobaltBright
import ai.openclaw.jarvis.ui.theme.CobaltGlow
import ai.openclaw.jarvis.ui.theme.RouteAndroid
import ai.openclaw.jarvis.ui.theme.RouteMixed
import ai.openclaw.jarvis.ui.theme.RouteOpenClaw
import ai.openclaw.jarvis.ui.theme.StatusConnected
import ai.openclaw.jarvis.ui.theme.StatusInfo
import ai.openclaw.jarvis.ui.theme.StatusOffline
import ai.openclaw.jarvis.ui.theme.StatusWarning
import ai.openclaw.jarvis.ui.theme.TextDim
import ai.openclaw.jarvis.ui.theme.TextPrimary
import ai.openclaw.jarvis.ui.theme.TextSecondary
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Section header — used inside a [BlueprintCard] or directly on a screen
 * to introduce a group of rows. Caps the section title to the spec's
 * 14sp semibold + 0.4 letter-spacing.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = TextSecondary,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
    )
}

/**
 * Cobalt-bordered surface used as the visual building block for every
 * card on the home / suggestions / history / system screens.
 */
@Composable
fun BlueprintCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    glowing: Boolean = false,
    content: @Composable () -> Unit,
) {
    val borderColor = if (glowing) CobaltGlow.copy(alpha = 0.65f)
                      else BlueprintBorder
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BlueprintCard)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(contentPadding),
    ) { content() }
}

/**
 * Small status pill used in top bars / cards — single character of
 * status meaning, e.g. "Connected" / "Offline" / "Pairing".
 */
@Composable
fun StatusChip(
    label: String,
    status: ChipStatus,
    modifier: Modifier = Modifier,
) {
    val (fg, bg) = when (status) {
        ChipStatus.SUCCESS -> StatusConnected to StatusConnected.copy(alpha = 0.12f)
        ChipStatus.WARNING -> StatusWarning   to StatusWarning.copy(alpha = 0.14f)
        ChipStatus.DANGER  -> StatusOffline   to StatusOffline.copy(alpha = 0.14f)
        ChipStatus.INFO    -> StatusInfo      to StatusInfo.copy(alpha = 0.12f)
        ChipStatus.NEUTRAL -> TextDim         to BlueprintBorder
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

enum class ChipStatus { SUCCESS, WARNING, DANGER, INFO, NEUTRAL }

/**
 * Trust-level chip for the top bar — the same shape as [StatusChip]
 * but with the trust palette (OWNER / TRUSTED green, GUEST amber,
 * UNKNOWN dim).
 */
@Composable
fun TrustChip(
    speakerName: String,
    trustLevel: String,
    modifier: Modifier = Modifier,
) {
    val s = when (trustLevel.uppercase()) {
        "OWNER", "TRUSTED" -> ChipStatus.SUCCESS
        "GUEST" -> ChipStatus.WARNING
        else -> ChipStatus.NEUTRAL
    }
    StatusChip("$speakerName • $trustLevel".uppercase(), s, modifier)
}

/**
 * Tiny chip Debug uses to mark a row's route. Hidden on user-facing
 * screens — see the home screen's `routeWord(...)` helper for the
 * spec's "Handled on your phone" / "Using OpenClaw" / "Mixed action"
 * phrasing instead.
 */
@Composable
fun RouteChip(route: String, modifier: Modifier = Modifier) {
    val (fg, bg) = when (route.uppercase()) {
        "ANDROID", "ANDROID_LOCAL" -> RouteAndroid to RouteAndroid.copy(alpha = 0.12f)
        "OPENCLAW" -> RouteOpenClaw to RouteOpenClaw.copy(alpha = 0.12f)
        "MIXED", "MIXED_ACTION" -> RouteMixed to RouteMixed.copy(alpha = 0.14f)
        else -> TextDim to BlueprintBorder
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = route.uppercase(),
            color = fg,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * Small dot indicator for the top-bar connection status. Pure cosmetic;
 * pair with a `contentDescription` for the screen-reader path.
 */
@Composable
fun ConnectionDot(connected: Boolean, modifier: Modifier = Modifier) {
    val color = if (connected) StatusConnected else StatusOffline
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = if (connected) "Connected" else "Offline" },
    )
}

/**
 * Primary action button — solid cobalt, used for the "Approve",
 * "Send", "Save" affirmative actions.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CobaltBright,
            contentColor = TextPrimary,
            disabledContainerColor = BlueprintBorder,
            disabledContentColor = TextDim,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Outlined neutral button — secondary actions like "View",
 * "Manage Identity".
 */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CobaltBright.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = CobaltBright,
            disabledContentColor = TextDim,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Destructive action button — used for "Cancel" / "Reject" /
 * "Clear data". Visual weight pulls the eye but stays the same shape
 * as PrimaryButton so the layout feels balanced.
 */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = StatusOffline,
            contentColor = TextPrimary,
            disabledContainerColor = BlueprintBorder,
            disabledContentColor = TextDim,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Settings row — single-line label + optional trailing slot. Used by
 * Settings sub-screens. Tap target is at least 56 dp tall to honour
 * the accessibility rule.
 */
@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val rowMod = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
        .padding(horizontal = 4.dp, vertical = 8.dp)
    val clickable = if (onClick != null)
        rowMod.padding(end = 4.dp) else rowMod
    Row(
        modifier = clickable,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextDim)
            }
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

/**
 * Convert the raw route string the session manager uses into the
 * spec's user-facing phrasing — "Handled on your phone" / "Using
 * OpenClaw" / "Mixed action".
 */
fun routeWord(route: String?): String = when (route?.uppercase()) {
    "ANDROID", "ANDROID_LOCAL" -> "Handled on your phone"
    "OPENCLAW" -> "Using OpenClaw"
    "MIXED", "MIXED_ACTION" -> "Mixed action"
    null, "" -> ""
    else -> route
}

@Suppress("unused")
internal val DesignDebugColor: Color = CobaltGlow // referenced from previews; suppress unused warn
