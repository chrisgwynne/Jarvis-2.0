package ai.openclaw.jarvis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.openclaw.jarvis.trust.TrustLevel
import ai.openclaw.jarvis.ui.theme.*
import ai.openclaw.jarvis.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = BlueprintBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text("SETTINGS", color = CobaltGlow, letterSpacing = 3.sp,
                        style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextSecondary,
                        )
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Gateway ────────────────────────────────────────────────────────
            SettingsSection(title = "GATEWAY") {
                SettingsTextField(
                    label    = "Gateway URL",
                    value    = settings.gatewayUrl,
                    hint     = "ws://192.168.1.x:8765",
                    onChange = viewModel::updateGatewayUrl,
                )
                SettingsTextField(
                    label    = "Device Name",
                    value    = settings.deviceName,
                    hint     = "Jarvis Android",
                    onChange = viewModel::updateDeviceName,
                )
                SettingsTextField(
                    label    = "Session Key",
                    value    = settings.sessionKey,
                    hint     = "jarvis:user:android",
                    onChange = viewModel::updateSessionKey,
                )
                SettingsTextField(
                    label    = "Speaker Name",
                    value    = settings.speakerName,
                    hint     = "user",
                    onChange = viewModel::updateSpeakerName,
                )
                SettingsToggle(
                    label    = "Gateway Enabled",
                    value    = settings.gatewayEnabled,
                    onChange = viewModel::updateGatewayEnabled,
                )
            }

            // ── Voice ──────────────────────────────────────────────────────────
            SettingsSection(title = "VOICE") {
                SettingsToggle(
                    label    = "Text-to-Speech",
                    value    = settings.ttsEnabled,
                    onChange = viewModel::updateTtsEnabled,
                )
                SettingsToggle(
                    label    = "Push-to-Talk",
                    value    = settings.pushToTalkEnabled,
                    onChange = viewModel::updatePtt,
                )
                SettingsToggle(
                    label    = "Always Listening",
                    value    = settings.alwaysListeningEnabled,
                    onChange = viewModel::updateAlwaysListening,
                )
                if (settings.alwaysListeningEnabled) {
                    SettingsTextField(
                        label    = "Wake Phrase",
                        value    = settings.wakePhrase,
                        hint     = "hey jarvis",
                        onChange = viewModel::updateWakePhrase,
                    )
                }
                SettingsSlider(
                    label  = "TTS Speed",
                    value  = settings.ttsSpeed,
                    range  = 0.5f..2.0f,
                    steps  = 14,
                    format = { "%.1f×".format(it) },
                    onChange = viewModel::updateTtsSpeed,
                )
                SettingsSlider(
                    label  = "TTS Pitch",
                    value  = settings.ttsPitch,
                    range  = 0.5f..2.0f,
                    steps  = 14,
                    format = { "%.1f×".format(it) },
                    onChange = viewModel::updateTtsPitch,
                )
            }

            // ── Audio ──────────────────────────────────────────────────────────
            SettingsSection(title = "AUDIO") {
                SettingsToggle(
                    label    = "Bluetooth Audio",
                    value    = settings.bluetoothAudioEnabled,
                    onChange = viewModel::updateBluetoothAudio,
                )
            }

            // ── Identity ──────────────────────────────────────────────────────
            SettingsSection(title = "IDENTITY") {
                SettingsInfoRow(label = "Enrolled Profiles", value = "${viewModel.enrolledProfileCount}")
                if (viewModel.enrolledProfiles.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        viewModel.enrolledProfiles.forEach { profile ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    profile.displayName,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                )
                                Text(
                                    profile.trustLevel.name.lowercase(),
                                    color = when (profile.trustLevel) {
                                        TrustLevel.OWNER   -> CobaltGlow
                                        TrustLevel.TRUSTED -> StatusConnected
                                        TrustLevel.GUEST   -> StatusQueued
                                        TrustLevel.UNKNOWN -> TextDim
                                    },
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
                SettingsSlider(
                    label    = "Session Timeout",
                    value    = settings.sessionTimeoutMinutes.toFloat(),
                    range    = 5f..60f,
                    steps    = 10,
                    format   = { "${it.toInt()} min" },
                    onChange = { viewModel.updateSessionTimeout(it.toInt()) },
                )
                Text(
                    text = "Say \"enrol my voice\" to add a voice profile.",
                    color = TextDim,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }

            // ── Android Capabilities ───────────────────────────────────────────
            SettingsSection(title = "ANDROID CAPABILITIES") {
                CapabilityStatusList(capabilities = viewModel.capabilityStatus)
            }

            // ── Privacy / Data ────────────────────────────────────────────────
            SettingsSection(title = "PRIVACY / DATA") {
                SettingsToggle(
                    label    = "Send Location Context",
                    value    = settings.sendLocationContext,
                    onChange = viewModel::updateSendLocation,
                )
                SettingsToggle(
                    label    = "Send Screen Context",
                    value    = settings.sendScreenContext,
                    onChange = viewModel::updateSendScreen,
                )
                SettingsToggle(
                    label    = "Trusted Mode (skip SMS/call confirmation)",
                    value    = settings.trustedMode,
                    onChange = viewModel::updateTrustedMode,
                )
                SettingsToggle(
                    label    = "Confirm Destructive Actions",
                    value    = settings.confirmDestructive,
                    onChange = viewModel::updateConfirmDestructive,
                )
                HorizontalDivider(color = BlueprintBorder, modifier = Modifier.padding(vertical = 4.dp))
                SettingsToggle(
                    label    = "Conversation Recording",
                    value    = settings.conversationRecordingEnabled,
                    onChange = viewModel::updateRecordingEnabled,
                )
                if (settings.conversationRecordingEnabled) {
                    SettingsSlider(
                        label    = "Auto-Delete After",
                        value    = settings.recordingRetentionHours.toFloat(),
                        range    = 1f..168f,
                        steps    = 6,
                        format   = { if (it < 24) "${it.toInt()}h" else "${(it / 24).toInt()}d" },
                        onChange = { viewModel.updateRecordingRetention(it.toInt()) },
                    )
                }
            }

            // ── Debug ─────────────────────────────────────────────────────────
            SettingsSection(title = "DEBUG") {
                SettingsToggle(
                    label    = "Debug Logs",
                    value    = settings.debugLogsEnabled,
                    onChange = viewModel::updateDebugLogs,
                )
                SettingsInfoRow(label = "Device ID", value = viewModel.deviceId)
                SettingsInfoRow(label = "Paired",    value = if (viewModel.isPaired) "Yes" else "No")
                OutlinedButton(
                    onClick  = viewModel::clearPairing,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = StatusOffline),
                    border   = BorderStroke(1.dp, StatusOffline.copy(alpha = 0.5f)),
                ) {
                    Text("Clear Pairing Token", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Section / Field helpers ──────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlueprintSurface, RoundedCornerShape(12.dp))
            .border(1.dp, BlueprintBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text     = title,
            color    = CobaltBright,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
        content()
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    hint: String,
    onChange: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = TextSecondary, fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        OutlinedTextField(
            value         = text,
            onValueChange = { text = it; onChange(it) },
            placeholder   = { Text(hint, color = TextDim,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) },
            modifier      = Modifier.fillMaxWidth(),
            textStyle     = androidx.compose.ui.text.TextStyle(
                color = TextPrimary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 14.sp,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = CobaltPrimary,
                unfocusedBorderColor = BlueprintBorder,
                cursorColor          = CobaltGlow,
            ),
            singleLine = true,
        )
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text    = label,
            color   = if (enabled) TextPrimary else TextDim,
            fontSize = 14.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked         = value,
            onCheckedChange = onChange,
            enabled         = enabled,
            colors          = SwitchDefaults.colors(
                checkedThumbColor  = CobaltGlow,
                checkedTrackColor  = CobaltPrimary.copy(alpha = 0.5f),
                uncheckedTrackColor = BlueprintBorder,
            ),
        )
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = TextSecondary, fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text(format(value), color = CobaltBright, fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
        Slider(
            value         = value,
            onValueChange = onChange,
            valueRange    = range,
            steps         = steps,
            colors        = SliderDefaults.colors(
                thumbColor        = CobaltGlow,
                activeTrackColor  = CobaltPrimary,
                inactiveTrackColor = BlueprintBorder,
            ),
        )
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = TextDim, fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        Text(value, color = TextSecondary, fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

@Composable
private fun CapabilityStatusList(
    capabilities: List<Pair<String, Boolean>>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        capabilities.forEach { (name, available) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(name, color = TextSecondary, fontSize = 13.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text(
                    text  = if (available) "available" else "needs permission",
                    color = if (available) StatusConnected else StatusQueued,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }
    }
}
