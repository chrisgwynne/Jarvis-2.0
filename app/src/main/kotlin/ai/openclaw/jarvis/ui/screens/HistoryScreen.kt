package ai.openclaw.jarvis.ui.screens

import ai.openclaw.jarvis.ui.components.BlueprintCard
import ai.openclaw.jarvis.ui.components.SectionHeader
import ai.openclaw.jarvis.ui.components.routeWord
import ai.openclaw.jarvis.ui.theme.BlueprintBackground
import ai.openclaw.jarvis.ui.theme.BlueprintBorder
import ai.openclaw.jarvis.ui.theme.CobaltBright
import ai.openclaw.jarvis.ui.theme.RouteAndroid
import ai.openclaw.jarvis.ui.theme.RouteOpenClaw
import ai.openclaw.jarvis.ui.theme.TextDim
import ai.openclaw.jarvis.ui.theme.TextPrimary
import ai.openclaw.jarvis.ui.theme.TextSecondary
import ai.openclaw.jarvis.ui.viewmodel.MainViewModel
import ai.openclaw.jarvis.voice.TranscriptEntry
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Timeline of commands / actions / system events. Built off the
 * transcript flow on [MainViewModel] (it's the only journal that
 * already exists end-to-end). The "System" filter shows transcript
 * rows that came from non-user / non-jarvis speakers (audit / hook
 * paths).
 */
@Composable
fun HistoryScreen(viewModel: MainViewModel = hiltViewModel()) {
    val transcript by viewModel.transcript.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }

    val filtered = transcript.asReversed().filter {
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.COMMANDS -> it.speaker == "user"
            HistoryFilter.ACTIONS -> it.speaker == "jarvis"
            HistoryFilter.SYSTEM -> it.speaker !in setOf("user", "jarvis")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlueprintBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("History", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryFilter.values().forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(f.label) },
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

        if (filtered.isEmpty()) {
            BlueprintCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Nothing yet. Recent commands and actions appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextDim,
                )
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            grouped(filtered).forEach { (group, rows) ->
                item(key = "header-$group") { SectionHeader(group) }
                items(rows, key = { row -> row.hashCode() }) { entry ->
                    HistoryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: TranscriptEntry) {
    BlueprintCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            val color = when {
                entry.speaker == "user" -> CobaltBright
                entry.route.equals("OpenClaw", true) -> RouteOpenClaw
                entry.route.equals("Android", true) -> RouteAndroid
                else -> TextDim
            }
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                val word = routeWord(entry.route).ifBlank { entry.route.ifBlank { "—" } }
                Text(
                    text = "${entry.speaker.replaceFirstChar { it.uppercase() }} • $word",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim,
                )
            }
            Text(
                text = TIME_FMT.format(Date(System.currentTimeMillis())),
                style = MaterialTheme.typography.bodySmall,
                color = TextDim,
            )
        }
    }
}

/**
 * Coarse grouping. The transcript model doesn't carry a per-row
 * timestamp today, so we lump everything under "Today" and leave
 * Yesterday / Earlier buckets for when the model evolves.
 */
private fun grouped(entries: List<TranscriptEntry>): List<Pair<String, List<TranscriptEntry>>> =
    listOf("Today" to entries)

private enum class HistoryFilter(val label: String) {
    ALL("All"),
    COMMANDS("Commands"),
    ACTIONS("Actions"),
    SYSTEM("System"),
}

private val TIME_FMT = SimpleDateFormat("HH:mm", Locale.getDefault())
