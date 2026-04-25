package ai.openclaw.jarvis.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openclaw.jarvis.ui.theme.*
import ai.openclaw.jarvis.voice.TranscriptEntry
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TranscriptPanel(
    entries: List<TranscriptEntry>,
    partialText: String = "",
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom
    LaunchedEffect(entries.size, partialText) {
        val totalItems = entries.size + (if (partialText.isNotBlank()) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(modifier = modifier) {
        Text(
            text     = "TRANSCRIPT",
            color    = TextDim,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BlueprintSurface, RoundedCornerShape(12.dp))
                .border(1.dp, BlueprintBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            if (entries.isEmpty() && partialText.isBlank()) {
                Text(
                    text     = "// waiting for input…",
                    color    = TextDim,
                    fontSize = 13.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Center).padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        AnimatedVisibility(
                            visible = true,
                            enter   = fadeIn() + slideInVertically { it },
                        ) {
                            TranscriptBubble(entry)
                        }
                    }
                    if (partialText.isNotBlank()) {
                        item("partial") {
                            PartialTextBubble(partialText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptBubble(entry: TranscriptEntry) {
    val isUser  = entry.speaker == "user"
    val bubbleColor = if (isUser) BlueprintCard else BlueprintBackground
    val textColor   = if (isUser) TextPrimary else CobaltGlow
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        .format(Date(entry.timestamp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bubbleColor, RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (isUser) BlueprintBorder else CobaltPrimary.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp),
            )
            .padding(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text     = if (isUser) "you" else "jarvis",
                color    = if (isUser) CobaltBright else CobaltGlow,
                fontSize = 10.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text     = entry.route.lowercase(),
                    color    = TextDim,
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
                Text(
                    text     = timeStr,
                    color    = TextDim,
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text     = entry.text,
            color    = textColor,
            fontSize = 14.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun PartialTextBubble(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlueprintCard.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .border(1.dp, CobaltPrimary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = "you (listening):",
            color    = CobaltBright.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
        Text(
            text     = "$text▌",
            color    = TextSecondary,
            fontSize = 14.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}
