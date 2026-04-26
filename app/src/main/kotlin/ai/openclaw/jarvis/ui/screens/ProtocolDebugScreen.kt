package ai.openclaw.jarvis.ui.screens

import ai.openclaw.jarvis.ui.theme.BlueprintBackground
import ai.openclaw.jarvis.ui.theme.BlueprintBorder
import ai.openclaw.jarvis.ui.theme.CobaltBright
import ai.openclaw.jarvis.ui.theme.CobaltGlow
import ai.openclaw.jarvis.ui.theme.TextDim
import ai.openclaw.jarvis.ui.theme.TextSecondary
import ai.openclaw.jarvis.ui.viewmodel.ProtocolDebugViewModel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Read-only inspector for the typed Jarvis ↔ OpenClaw contract. Shows the
 * latest request / response / action result / skill manifest exactly as
 * they crossed the wire, plus a copy button per panel for exporting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolDebugScreen(
    onBack: () -> Unit,
    viewModel: ProtocolDebugViewModel = hiltViewModel(),
) {
    val request by viewModel.lastRequest.collectAsStateWithLifecycle()
    val response by viewModel.lastResponse.collectAsStateWithLifecycle()
    val result by viewModel.lastActionResult.collectAsStateWithLifecycle()
    val manifest by viewModel.lastSkillManifest.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    Scaffold(
        containerColor = BlueprintBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "PROTOCOL", color = CobaltGlow, letterSpacing = 3.sp,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BlueprintBackground),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VersionRow(viewModel.protocolVersion)
            ProtocolPanel(
                title = "LAST LIVE REQUEST",
                json = viewModel.encode(request),
                onCopy = { copy(ctx, "JarvisLiveRequest", viewModel.encode(request)) },
            )
            ProtocolPanel(
                title = "LAST OPENCLAW RESPONSE",
                json = viewModel.encode(response),
                onCopy = { copy(ctx, "OpenClawResponse", viewModel.encode(response)) },
            )
            ProtocolPanel(
                title = "LAST ACTION RESULT",
                json = viewModel.encode(result),
                onCopy = { copy(ctx, "JarvisActionResult", viewModel.encode(result)) },
            )
            ProtocolPanel(
                title = "SKILL MANIFEST",
                json = viewModel.encode(manifest),
                onCopy = { copy(ctx, "OpenClawSkillManifest", viewModel.encode(manifest)) },
            )
        }
    }
}

@Composable private fun VersionRow(version: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CobaltGlow.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("PROTOCOL VERSION", color = TextDim, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
        Text(version, color = CobaltBright, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable private fun ProtocolPanel(title: String, json: String, onCopy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BlueprintBorder, RoundedCornerShape(4.dp))
            .background(BlueprintBackground.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, color = CobaltBright, letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            TextButton(onClick = onCopy) {
                Text("COPY", color = CobaltGlow, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = json,
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun copy(ctx: Context, label: String, content: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, content))
}
