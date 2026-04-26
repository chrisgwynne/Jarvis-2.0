package ai.openclaw.jarvis.ui.screens

import ai.openclaw.jarvis.proactive.SuggestionManager
import ai.openclaw.jarvis.proactive.model.SignalType
import ai.openclaw.jarvis.proactive.model.Suggestion
import ai.openclaw.jarvis.ui.components.BlueprintCard
import ai.openclaw.jarvis.ui.components.ChipStatus
import ai.openclaw.jarvis.ui.components.OutlineButton
import ai.openclaw.jarvis.ui.components.PrimaryButton
import ai.openclaw.jarvis.ui.components.StatusChip
import ai.openclaw.jarvis.ui.theme.BlueprintBackground
import ai.openclaw.jarvis.ui.theme.BlueprintBorder
import ai.openclaw.jarvis.ui.theme.CobaltBright
import ai.openclaw.jarvis.ui.theme.TextDim
import ai.openclaw.jarvis.ui.theme.TextPrimary
import ai.openclaw.jarvis.ui.theme.TextSecondary
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Proactive suggestions tab. The active-suggestion bus only holds one
 * chip at a time (by design — "fewer, higher-quality suggestions"), so
 * this screen renders that single live one when present and a calm
 * empty state otherwise.
 *
 * Filter chips are functional: All / High / Medium / Low filter on
 * the priority Jarvis assigned at suggestion-build time. Priority is
 * derived from the underlying SignalType (see `priorityOf`).
 */
@Composable
fun SuggestionsScreen(
    viewModel: SuggestionsViewModel = hiltViewModel(),
) {
    val active by viewModel.active.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf(Priority.ALL) }
    val visible = active?.let { listOf(it) }.orEmpty()
        .filter { filter == Priority.ALL || priorityOf(it.signalType) == filter }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlueprintBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("Suggestions", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Priority.values().forEach { p ->
                FilterChip(
                    selected = filter == p,
                    onClick = { filter = p },
                    label = { Text(p.label) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CobaltBright.copy(alpha = 0.18f),
                        selectedLabelColor = CobaltBright,
                        containerColor = BlueprintBorder.copy(alpha = 0.4f),
                        labelColor = TextSecondary,
                    ),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        if (visible.isEmpty()) {
            EmptyState(
                title = "No suggestions",
                body = "Jarvis only nudges when there's something worth your attention.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visible) { s ->
                    SuggestionRow(
                        suggestion = s,
                        onAccept = { viewModel.accept(s) },
                        onDismiss = { viewModel.dismiss(s) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: Suggestion,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    val priority = priorityOf(suggestion.signalType)
    BlueprintCard(modifier = Modifier.fillMaxWidth(), glowing = priority == Priority.HIGH) {
        Column {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    suggestion.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(priority.label.uppercase(), priority.chipStatus)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                suggestion.body,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Accept", onClick = onAccept, modifier = Modifier.weight(1f))
                OutlineButton("Dismiss", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    BlueprintCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextDim)
        }
    }
}

private enum class Priority(val label: String, val chipStatus: ChipStatus) {
    ALL("All", ChipStatus.NEUTRAL),
    HIGH("High", ChipStatus.DANGER),
    MEDIUM("Medium", ChipStatus.WARNING),
    LOW("Low", ChipStatus.INFO),
}

/** Map signal type → priority. Time/location/calendar = HIGH because
 *  they're moments-in-the-world; behaviour patterns are MEDIUM; idle
 *  / wake-pattern are LOW. */
private fun priorityOf(signalType: SignalType): Priority = when (signalType) {
    SignalType.LEFT_HOME, SignalType.ARRIVED_HOME, SignalType.ARRIVED_WORK,
    SignalType.CALENDAR_EVENT_APPROACHING, SignalType.LOW_BATTERY -> Priority.HIGH
    SignalType.SCREENSHOT_TAKEN, SignalType.APP_OPENED_FREQUENTLY,
    SignalType.HEADPHONES_CONNECTED, SignalType.DRIVING_STARTED -> Priority.MEDIUM
    else -> Priority.LOW
}

/**
 * Hilt-resolvable view-model. Wraps [SuggestionManager] and exposes
 * its `active` slot directly so the screen stays trivially testable.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class SuggestionsViewModel @javax.inject.Inject constructor(
    private val manager: SuggestionManager,
) : androidx.lifecycle.ViewModel() {
    val active = manager.active
    fun accept(s: Suggestion) = manager.accept(s)
    fun dismiss(s: Suggestion, dontSuggestAgain: Boolean = false) =
        manager.dismiss(s, dontSuggestAgain)
}
