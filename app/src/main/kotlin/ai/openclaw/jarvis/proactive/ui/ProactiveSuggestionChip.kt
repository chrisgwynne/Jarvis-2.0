package ai.openclaw.jarvis.proactive.ui

import ai.openclaw.jarvis.proactive.model.Suggestion
import ai.openclaw.jarvis.ui.theme.BlueprintBackground
import ai.openclaw.jarvis.ui.theme.CobaltGlow
import ai.openclaw.jarvis.ui.theme.StatusOffline
import ai.openclaw.jarvis.ui.theme.TextDim
import ai.openclaw.jarvis.ui.theme.TextSecondary
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Drop-in proactive suggestion chip. Renders nothing when there's no
 * active suggestion, so it's safe to embed at the top of any screen.
 *
 * Buttons:
 *   - YES: accept (caller acts on the proposed action)
 *   - DISMISS: dismiss with don't-suggest-again = false
 *   - "..." long-press not implemented; the secondary "NEVER" button
 *     dismisses with don't-suggest-again = true.
 */
@Composable
fun ProactiveSuggestionChip(
    onAccept: (Suggestion) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProactiveSuggestionViewModel = hiltViewModel(),
) {
    val active by viewModel.active.collectAsStateWithLifecycle()
    val s = active ?: return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CobaltGlow.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("SUGGESTION", color = CobaltGlow, fontSize = 10.sp,
            letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
        Text(s.title, color = TextSecondary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Text(s.body, color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                onAccept(s)
                viewModel.accept(s)
            }) {
                Text("YES", color = CobaltGlow, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            TextButton(onClick = { viewModel.dismiss(s, dontSuggestAgain = false) }) {
                Text("DISMISS", color = TextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            TextButton(onClick = { viewModel.dismiss(s, dontSuggestAgain = true) }) {
                Text("NEVER", color = StatusOffline, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}
