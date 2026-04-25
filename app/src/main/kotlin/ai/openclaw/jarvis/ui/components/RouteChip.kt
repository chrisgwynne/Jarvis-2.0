package ai.openclaw.jarvis.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openclaw.jarvis.data.models.RouteChoice
import ai.openclaw.jarvis.ui.theme.*

@Composable
fun RouteChip(
    route: RouteChoice?,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when (route) {
        RouteChoice.ANDROID_LOCAL -> "Android" to RouteAndroid
        RouteChoice.OPENCLAW      -> "OpenClaw" to RouteOpenClaw
        RouteChoice.MIXED         -> "Mixed" to RouteMixed
        null                      -> "—" to TextDim
    }

    val animatedColor by animateColorAsState(color, label = "routeColor")

    Row(
        modifier = modifier
            .background(BlueprintCard, RoundedCornerShape(20.dp))
            .border(1.dp, animatedColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text     = "route:",
            color    = TextDim,
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
        Text(
            text     = label,
            color    = animatedColor,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}
