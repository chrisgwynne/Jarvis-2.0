package ai.openclaw.jarvis.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.openclaw.jarvis.data.local.JarvisSettings
import ai.openclaw.jarvis.githubissues.ui.GitHubIssueLoggingSection
import ai.openclaw.jarvis.policy.ui.PolicySettingsSection
import ai.openclaw.jarvis.proactive.ui.ProactiveSettingsSection
import ai.openclaw.jarvis.screen.ui.ScreenAwarenessSettingsSection
import ai.openclaw.jarvis.trust.TrustLevel
import ai.openclaw.jarvis.ui.theme.*
import ai.openclaw.jarvis.ui.viewmodel.SettingsViewModel

// ─── Settings Hub ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenApprovals: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var activeSection by remember { mutableStateOf<SettingsSection?>(null) }

    if (activeSection != null) {
        SettingsSubScreen(
            section         = activeSection!!,
            onBack          = { activeSection = null },
            onOpenApprovals = onOpenApprovals,
            onOpenDiagnostics = onOpenDiagnostics,
            viewModel       = viewModel,
        )
        return
    }

    Scaffold(
        containerColor = BlueprintBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
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
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Connection ─────────────────────────────────────────────────────
            HubCard(
                title  = "Connection",
                status = if (settings.gatewayEnabled) "Enabled • ${settings.gatewayUrl}" else "Disabled",
                statusColor = if (settings.gatewayEnabled) StatusConnected else TextDim,
                description = "Configure the OpenClaw gateway URL, device name, and session key.",
                onClick = { activeSection = SettingsSection.CONNECTION },
            )

            // ── Voice & Audio ──────────────────────────────────────────────────
            val voiceStatus = buildString {
                if (settings.ttsEnabled) append("TTS on") else append("TTS off")
                if (settings.alwaysListeningEnabled) append(" • Always listening")
                else if (settings.pushToTalkEnabled) append(" • Push-to-talk")
            }
            HubCard(
                title  = "Voice & Audio",
                status = voiceStatus,
                statusColor = CobaltBright,
                description = "Text-to-speech, wake word, Bluetooth audio, and microphone mode.",
                onClick = { activeSection = SettingsSection.VOICE },
            )

            // ── Identity & Trust ───────────────────────────────────────────────
            val profileCount = viewModel.enrolledProfileCount
            HubCard(
                title  = "Identity & Trust",
                status = if (profileCount == 0) "No voice profiles enrolled"
                         else "$profileCount profile${if (profileCount == 1) "" else "s"} enrolled",
                statusColor = if (profileCount > 0) StatusConnected else StatusWarning,
                description = "Voice profiles, speaker trust levels, and session timeout.",
                onClick = { activeSection = SettingsSection.IDENTITY },
            )

            // ── Capabilities & Permissions ────────────────────────────────────
            val capList = viewModel.capabilityStatus
            val missingCount = capList.count { !it.second }
            HubCard(
                title  = "Capabilities & Permissions",
                status = if (missingCount == 0) "All ${capList.size} capabilities available"
                         else "$missingCount permission${if (missingCount == 1) "" else "s"} needed",
                statusColor = if (missingCount == 0) StatusConnected else StatusWarning,
                description = "Microphone, camera, contacts, SMS, location, notifications, and more.",
                onClick = { activeSection = SettingsSection.CAPABILITIES },
            )

            // ── Privacy & Behaviour ────────────────────────────────────────────
            HubCard(
                title  = "Privacy & Behaviour",
                status = buildString {
                    if (settings.sendLocationContext) append("Location context on")
                    else append("Location context off")
                    if (settings.conversationRecordingEnabled) append(" • Recording on")
                },
                statusColor = TextSecondary,
                description = "What context Jarvis sends, confirmation prompts, and recording.",
                onClick = { activeSection = SettingsSection.PRIVACY },
            )

            // ── Automation ─────────────────────────────────────────────────────
            HubCard(
                title  = "Automation",
                status = "Proactive suggestions • Screen awareness • Autonomy",
                statusColor = TextSecondary,
                description = "Proactive suggestions, screen content reading, and what Jarvis can do without asking.",
                onClick = { activeSection = SettingsSection.AUTOMATION },
            )

            // ── Diagnostics ────────────────────────────────────────────────────
            val debugStatus = if (settings.debugLogsEnabled) "Debug logs on" else "Debug logs off"
            HubCard(
                title  = "Diagnostics",
                status = debugStatus,
                statusColor = if (settings.debugLogsEnabled) StatusWarning else TextDim,
                description = "Device ID, pairing status, debug mode, GitHub issue logging.",
                onClick = { activeSection = SettingsSection.DIAGNOSTICS },
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─── Section enum ─────────────────────────────────────────────────────────────

private enum class SettingsSection {
    CONNECTION, VOICE, IDENTITY, CAPABILITIES, PRIVACY, AUTOMATION, DIAGNOSTICS,
}

// ─── Hub card ─────────────────────────────────────────────────────────────────

@Composable
private fun HubCard(
    title: String,
    status: String,
    statusColor: Color,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BlueprintSurface)
            .border(1.dp, BlueprintBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = title,
                color      = TextPrimary,
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text  = status,
                color = statusColor,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = description,
                color = TextDim,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = TextDim,
        )
    }
}

// ─── Sub-screen dispatcher ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubScreen(
    section: SettingsSection,
    onBack: () -> Unit,
    onOpenApprovals: () -> Unit,
    onOpenDiagnostics: () -> Unit = {},
    viewModel: SettingsViewModel,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val title = when (section) {
        SettingsSection.CONNECTION   -> "Connection"
        SettingsSection.VOICE        -> "Voice & Audio"
        SettingsSection.IDENTITY     -> "Identity & Trust"
        SettingsSection.CAPABILITIES -> "Capabilities"
        SettingsSection.PRIVACY      -> "Privacy & Behaviour"
        SettingsSection.AUTOMATION   -> "Automation"
        SettingsSection.DIAGNOSTICS  -> "Diagnostics"
    }

    Scaffold(
        containerColor = BlueprintBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BlueprintBackground),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (section) {
                SettingsSection.CONNECTION   -> ConnectionSection(settings, viewModel, onOpenDiagnostics)
                SettingsSection.VOICE        -> VoiceSection(settings, viewModel)
                SettingsSection.IDENTITY     -> IdentitySection(settings, viewModel)
                SettingsSection.CAPABILITIES -> CapabilitiesSection(viewModel)
                SettingsSection.PRIVACY      -> PrivacySection(settings, viewModel, onOpenApprovals)
                SettingsSection.AUTOMATION   -> AutomationSection(onOpenApprovals)
                SettingsSection.DIAGNOSTICS  -> DiagnosticsSection(settings, viewModel)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Section content ──────────────────────────────────────────────────────────

@Composable
private fun ConnectionSection(
    settings: JarvisSettings,
    vm: SettingsViewModel,
    onOpenDiagnostics: () -> Unit = {},
) {
    SettingsGroup("Gateway") {
        DescribedToggle(
            label       = "Gateway enabled",
            description = "Connect Jarvis to the OpenClaw server on your local network or via Tailscale.",
            value       = settings.gatewayEnabled,
            onChange    = vm::updateGatewayEnabled,
        )
        DescribedTextField(
            label       = "Gateway URL",
            description = "Full WebSocket URL. Local: ws://192.168.1.x:PORT  " +
                "Tailscale: ws://100.x.x.x:PORT  Secure: wss://domain:PORT/path",
            value       = settings.gatewayUrl,
            hint        = "ws://192.168.1.100:8765",
            onChange    = vm::updateGatewayUrl,
        )
        DescribedTextField(
            label       = "Auth token",
            description = "Bootstrap token from your OpenClaw gateway (set in the gateway config). Required to connect.",
            value       = settings.nodeSecret,
            hint        = "paste token from openclaw gateway",
            onChange    = vm::updateNodeSecret,
            isPassword  = true,
        )
        DescribedTextField(
            label       = "Device name",
            description = "How this device identifies itself to OpenClaw.",
            value       = settings.deviceName,
            hint        = "Jarvis Android",
            onChange    = vm::updateDeviceName,
        )
        DescribedTextField(
            label       = "Session key",
            description = "Routing key for this session (used by OpenClaw to route replies).",
            value       = settings.sessionKey,
            hint        = "jarvis:user:android",
            onChange    = vm::updateSessionKey,
        )
        DescribedTextField(
            label       = "Speaker name",
            description = "Default name used when no voice profile is matched.",
            value       = settings.speakerName,
            hint        = "user",
            onChange    = vm::updateSpeakerName,
        )
    }
    Spacer(Modifier.height(4.dp))
    Button(
        onClick  = onOpenDiagnostics,
        colors   = ButtonDefaults.buttonColors(containerColor = CobaltBright.copy(alpha = 0.15f), contentColor = CobaltBright),
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(10.dp),
    ) {
        Text("Open Connection Diagnostics", fontSize = 14.sp)
    }
}

@Composable
private fun VoiceSection(settings: JarvisSettings, vm: SettingsViewModel) {
    SettingsGroup("Input") {
        DescribedToggle(
            label       = "Push-to-talk",
            description = "Tap and hold the orb to speak. Simplest and most battery-efficient mode.",
            value       = settings.pushToTalkEnabled,
            onChange    = vm::updatePtt,
        )
        DescribedToggle(
            label       = "Always listening",
            description = "Jarvis listens in the background and activates on your wake phrase. Uses more battery.",
            value       = settings.alwaysListeningEnabled,
            onChange    = vm::updateAlwaysListening,
        )
        if (settings.alwaysListeningEnabled) {
            DescribedTextField(
                label       = "Wake phrase",
                description = "Say this to activate Jarvis hands-free.",
                value       = settings.wakePhrase,
                hint        = "hey jarvis",
                onChange    = vm::updateWakePhrase,
            )
            DescribedSlider(
                label       = "Wake sensitivity",
                description = "Higher = triggers on partial phrase match (more false triggers). " +
                    "Lower = requires exact phrase (may miss quiet speech).",
                value       = settings.wakeSensitivity,
                range       = 0.1f..1.0f,
                steps       = 8,
                format      = { when {
                    it >= 0.85f -> "High"
                    it >= 0.5f  -> "Medium"
                    else        -> "Low"
                } },
                onChange    = vm::updateWakeSensitivity,
            )
            DescribedToggle(
                label       = "Wake confirmation sound",
                description = "Plays a short beep when the wake phrase is detected.",
                value       = settings.wakeConfirmSound,
                onChange    = vm::updateWakeConfirmSound,
            )
            DescribedToggle(
                label       = "Suppress wake during speech",
                description = "Pauses wake word detection while Jarvis is speaking, to prevent self-triggering.",
                value       = settings.wakeSuppressDuringTts,
                onChange    = vm::updateWakeSuppressTts,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Engine: Basic STT (text matching) — not an always-on neural engine. " +
                    "Push-to-talk is more reliable.",
                color    = TextDim,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    SettingsGroup("Output (TTS)") {
        DescribedToggle(
            label       = "Text-to-speech",
            description = "Jarvis speaks responses aloud. Turn off to get text-only replies.",
            value       = settings.ttsEnabled,
            onChange    = vm::updateTtsEnabled,
        )
        Spacer(Modifier.height(8.dp))
        TtsEngineSelector(
            engine   = settings.ttsEngine,
            onChange = vm::updateTtsEngine,
        )
        Spacer(Modifier.height(8.dp))
        SttEngineSelector(
            engine   = settings.sttEngine,
            onChange = vm::updateSttEngine,
        )
        if (settings.ttsEnabled) {
            DescribedSlider(
                label       = "Speed",
                description = "How fast Jarvis speaks.",
                value       = settings.ttsSpeed,
                range       = 0.5f..2.0f,
                steps       = 14,
                format      = { "%.1f×".format(it) },
                onChange    = vm::updateTtsSpeed,
            )
            DescribedSlider(
                label       = "Pitch",
                description = "How high or low Jarvis's voice sounds.",
                value       = settings.ttsPitch,
                range       = 0.5f..2.0f,
                steps       = 14,
                format      = { "%.1f×".format(it) },
                onChange    = vm::updateTtsPitch,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    SettingsGroup("Notification style") {
        NotificationStyleSelector(
            style    = settings.notificationStyle,
            onChange = vm::updateNotificationStyle,
        )
    }
    Spacer(Modifier.height(4.dp))
    SettingsGroup("Audio routing") {
        DescribedToggle(
            label       = "Bluetooth audio",
            description = "Route microphone input and TTS output through a Bluetooth headset when connected.",
            value       = settings.bluetoothAudioEnabled,
            onChange    = vm::updateBluetoothAudio,
        )
    }
}

@Composable
private fun NotificationStyleSelector(style: String, onChange: (String) -> Unit) {
    val options = listOf(
        "minimal"    to "Minimal",
        "controls"   to "Controls",
        "diagnostic" to "Diagnostic",
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text      = "Minimal shows only the status and a Stop button. Controls adds " +
                        "Silence and Resume. Diagnostic shows verbose engine status.",
            color     = TextDim,
            fontSize  = 12.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, label) ->
                EngineSegmentButton(
                    label    = label,
                    selected = style == value,
                    modifier = Modifier.weight(1f),
                    onClick  = { onChange(value) },
                )
            }
        }
    }
}

@Composable
private fun TtsEngineSelector(engine: String, onChange: (String) -> Unit) {
    Column {
        Text(
            text      = "TTS engine",
            color     = TextPrimary,
            fontSize  = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text      = "System default uses whatever is set in Android settings. " +
                        "Google TTS sounds more natural but must be installed.",
            color     = TextDim,
            fontSize  = 12.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EngineSegmentButton(
                label    = "System default",
                selected = engine == "system",
                modifier = Modifier.weight(1f),
                onClick  = { onChange("system") },
            )
            EngineSegmentButton(
                label    = "Google TTS",
                selected = engine == "google",
                modifier = Modifier.weight(1f),
                onClick  = { onChange("google") },
            )
        }
    }
}

@Composable
private fun SttEngineSelector(engine: String, onChange: (String) -> Unit) {
    Column {
        Text(
            text      = "STT engine",
            color     = TextPrimary,
            fontSize  = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text      = "Android uses Google's cloud STT. Whisper runs on-device " +
                        "(requires native library + model download — see WhisperJni.kt).",
            color     = TextDim,
            fontSize  = 12.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EngineSegmentButton(
                label    = "Android STT",
                selected = engine == "android",
                modifier = Modifier.weight(1f),
                onClick  = { onChange("android") },
            )
            EngineSegmentButton(
                label    = "Whisper",
                selected = engine == "whisper",
                modifier = Modifier.weight(1f),
                onClick  = { onChange("whisper") },
            )
        }
    }
}

@Composable
private fun EngineSegmentButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) CobaltBright else BlueprintBorder
    val textColor   = if (selected) CobaltBright else TextSecondary
    OutlinedButton(
        onClick  = onClick,
        modifier = modifier.height(40.dp),
        colors   = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) CobaltBright.copy(alpha = 0.10f) else Color.Transparent,
            contentColor   = textColor,
        ),
        border = BorderStroke(1.dp, borderColor),
        shape  = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun IdentitySection(settings: JarvisSettings, vm: SettingsViewModel) {
    val profiles = vm.enrolledProfiles
    SettingsGroup("Voice profiles") {
        if (profiles.isEmpty()) {
            Text(
                "No voice profiles enrolled yet.",
                color = TextDim, fontSize = 13.sp,
            )
            Spacer(Modifier.height(4.dp))
        } else {
            profiles.forEach { profile ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(profile.displayName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            when (profile.trustLevel) {
                                TrustLevel.OWNER   -> "Owner — full access"
                                TrustLevel.TRUSTED -> "Trusted — most actions"
                                TrustLevel.GUEST   -> "Guest — safe actions only"
                                TrustLevel.UNKNOWN -> "Unknown — restricted"
                            },
                            color = TextDim, fontSize = 12.sp,
                        )
                    }
                    val trustColor = when (profile.trustLevel) {
                        TrustLevel.OWNER, TrustLevel.TRUSTED -> StatusConnected
                        TrustLevel.GUEST                     -> StatusWarning
                        TrustLevel.UNKNOWN                   -> TextDim
                    }
                    Text(
                        profile.trustLevel.name.lowercase()
                            .replaceFirstChar { it.uppercase() },
                        color = trustColor, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    )
                }
                if (profiles.last() != profile) {
                    HorizontalDivider(color = BlueprintBorder, modifier = Modifier.padding(vertical = 6.dp))
                }
            }
        }
        Text(
            "Say \"enrol my voice\" to add a new voice profile.",
            color = TextDim, fontSize = 12.sp,
        )
    }
    Spacer(Modifier.height(4.dp))
    SettingsGroup("Session") {
        DescribedSlider(
            label       = "Session timeout",
            description = "How long before Jarvis forgets who is speaking and reverts to Guest mode.",
            value       = settings.sessionTimeoutMinutes.toFloat(),
            range       = 5f..60f,
            steps       = 10,
            format      = { "${it.toInt()} min" },
            onChange    = { vm.updateSessionTimeout(it.toInt()) },
        )
    }
}

@Composable
private fun CapabilitiesSection(vm: SettingsViewModel) {
    val caps = vm.capabilityStatus
    SettingsGroup("Android capabilities") {
        caps.forEach { (name, available) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, color = TextPrimary, fontSize = 14.sp)
                    Text(
                        if (available) "Available" else "Requires permission — grant in Android Settings",
                        color = if (available) TextDim else StatusWarning,
                        fontSize = 12.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (available) StatusConnected.copy(alpha = 0.12f)
                            else StatusWarning.copy(alpha = 0.12f)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text  = if (available) "OK" else "Missing",
                        color = if (available) StatusConnected else StatusWarning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (caps.last().first != name) {
                HorizontalDivider(color = BlueprintBorder.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun PrivacySection(
    settings: JarvisSettings,
    vm: SettingsViewModel,
    onOpenApprovals: () -> Unit,
) {
    SettingsGroup("Context sharing") {
        DescribedToggle(
            label       = "Send location context",
            description = "Includes your approximate location with requests so Jarvis can give location-aware answers.",
            value       = settings.sendLocationContext,
            onChange    = vm::updateSendLocation,
        )
        DescribedToggle(
            label       = "Send screen context",
            description = "Includes what's visible on screen so Jarvis can answer questions about the current app.",
            value       = settings.sendScreenContext,
            onChange    = vm::updateSendScreen,
        )
    }
    Spacer(Modifier.height(4.dp))
    SettingsGroup("Confirmations") {
        DescribedToggle(
            label       = "Confirm destructive actions",
            description = "Jarvis asks before sending messages, making calls, or taking irreversible actions.",
            value       = settings.confirmDestructive,
            onChange    = vm::updateConfirmDestructive,
        )
        DescribedToggle(
            label       = "Trusted mode",
            description = "Skip confirmation prompts for SMS and calls. Only use when you fully trust the environment.",
            value       = settings.trustedMode,
            onChange    = vm::updateTrustedMode,
        )
    }
    Spacer(Modifier.height(4.dp))
    SettingsGroup("Recording") {
        DescribedToggle(
            label       = "Conversation recording",
            description = "Save audio of conversations to local storage. Recordings are deleted after the retention period.",
            value       = settings.conversationRecordingEnabled,
            onChange    = vm::updateRecordingEnabled,
        )
        if (settings.conversationRecordingEnabled) {
            DescribedSlider(
                label       = "Auto-delete after",
                description = "Recordings older than this are automatically deleted.",
                value       = settings.recordingRetentionHours.toFloat(),
                range       = 1f..168f,
                steps       = 6,
                format      = { if (it < 24) "${it.toInt()}h" else "${(it / 24).toInt()}d" },
                onChange    = { vm.updateRecordingRetention(it.toInt()) },
            )
        }
    }
}

@Composable
private fun AutomationSection(onOpenApprovals: () -> Unit) {
    PolicySettingsSection(onOpenApprovals = onOpenApprovals)
    Spacer(Modifier.height(4.dp))
    ProactiveSettingsSection()
    Spacer(Modifier.height(4.dp))
    ScreenAwarenessSettingsSection()
}

@Composable
private fun DiagnosticsSection(
    settings: JarvisSettings,
    vm: SettingsViewModel,
) {
    SettingsGroup("Device") {
        InfoRow("Device ID", vm.deviceId)
        InfoRow("Paired with OpenClaw", if (vm.isPaired) "Yes" else "No")
    }
    Spacer(Modifier.height(4.dp))
    SettingsGroup("Debug") {
        DescribedToggle(
            label       = "Debug logs",
            description = "Show extra technical information about requests and routing. Shows a bug icon in the top bar.",
            value       = settings.debugLogsEnabled,
            onChange    = vm::updateDebugLogs,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick  = vm::clearPairing,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = StatusOffline),
            border   = BorderStroke(1.dp, StatusOffline.copy(alpha = 0.5f)),
        ) {
            Text("Clear pairing token", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
    Spacer(Modifier.height(4.dp))
    GitHubIssueLoggingSection()
}

// ─── Reusable field components ─────────────────────────────────────────────────

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BlueprintSurface)
            .border(1.dp, BlueprintBorder, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text          = title.uppercase(),
            color         = CobaltBright,
            fontSize      = 11.sp,
            fontWeight    = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
        )
        content()
    }
}

@Composable
private fun DescribedToggle(
    label: String,
    description: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text       = label,
                color      = if (enabled) TextPrimary else TextDim,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(text = description, color = TextDim, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(
            checked         = value,
            onCheckedChange = onChange,
            enabled         = enabled,
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = CobaltGlow,
                checkedTrackColor   = CobaltPrimary.copy(alpha = 0.5f),
                uncheckedTrackColor = BlueprintBorder,
            ),
        )
    }
}

@Composable
private fun DescribedTextField(
    label: String,
    description: String,
    value: String,
    hint: String,
    onChange: (String) -> Unit,
    isPassword: Boolean = false,
) {
    var text by remember(value) { mutableStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(description, color = TextDim, fontSize = 12.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(2.dp))
        OutlinedTextField(
            value         = text,
            onValueChange = { text = it; onChange(it) },
            placeholder   = {
                Text(hint, color = TextDim, fontSize = 14.sp)
            },
            modifier  = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(
                color    = TextPrimary,
                fontSize = 14.sp,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = CobaltPrimary,
                unfocusedBorderColor = BlueprintBorder,
                cursorColor          = CobaltGlow,
            ),
            singleLine           = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions      = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        )
    }
}

@Composable
private fun DescribedSlider(
    label: String,
    description: String,
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(description, color = TextDim, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Text(format(value), color = CobaltBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value         = value,
            onValueChange = onChange,
            valueRange    = range,
            steps         = steps,
            colors        = SliderDefaults.colors(
                thumbColor         = CobaltGlow,
                activeTrackColor   = CobaltPrimary,
                inactiveTrackColor = BlueprintBorder,
            ),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextDim, fontSize = 13.sp)
        Text(
            value,
            color    = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
