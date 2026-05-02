package ai.openclaw.jarvis.awareness

import ai.openclaw.jarvis.audio.AudioRouteManager
import ai.openclaw.jarvis.capabilities.CapabilityRegistry
import ai.openclaw.jarvis.capabilities.base.Capability
import ai.openclaw.jarvis.data.models.GatewayState
import ai.openclaw.jarvis.network.OpenClawClient
import ai.openclaw.jarvis.protocol.client.OpenClawProtocolClient
import ai.openclaw.jarvis.protocol.model.OpenClawSkillManifest
import ai.openclaw.jarvis.trust.TrustLevel
import ai.openclaw.jarvis.trust.TrustManager
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds an [AwarenessSnapshot] from live system state. The single source
 * of truth for "what can Jarvis actually do right now?".
 *
 * Inputs:
 *   - [CapabilityRegistry]              — every registered Android capability
 *   - Android permission grants         — checked per capability
 *   - app-installed checks              — currently WhatsApp via [WhatsAppCapabilityImpl]
 *   - [AudioRouteManager]               — Bluetooth state
 *   - [OpenClawClient.gatewayState]     — connected / pairing / offline
 *   - [OpenClawProtocolClient.skillManifest] — last manifest pushed by OpenClaw
 *   - [TrustManager.currentTrustLevel]  — OWNER / TRUSTED / GUEST / UNKNOWN
 *
 * The snapshot is recomputed on every call — never cached. Cheap enough.
 */
@Singleton
class CapabilityAwarenessManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: CapabilityRegistry,
    private val audioRouter: AudioRouteManager,
    private val openClawClient: OpenClawClient,
    private val protocolClient: OpenClawProtocolClient,
    private val trustManager: TrustManager,
) {

    fun snapshot(): AwarenessSnapshot {
        val trust = runCatching { trustManager.currentTrustLevel() }.getOrDefault(TrustLevel.UNKNOWN)
        val openClawConnected = openClawClient.gatewayState.value == GatewayState.CONNECTED

        val androidActions = mutableListOf<LocalAction>()
        val missingPerms = mutableListOf<MissingPermission>()
        val recommended = mutableListOf<String>()

        // Phone control roll-up — torch / volume / media / mute aren't worth
        // listing one-by-one in the answer. Available iff the device cap is.
        androidActions += LocalAction(
            id = "phone_control",
            label = "Control your phone",
            state = simpleStateOf(registry.device, trust = trust, restrictedTrust = false),
        )

        // SMS
        androidActions += capabilityRow(
            registry.sms,
            label = "Send texts",
            restrictedTrust = trust == TrustLevel.UNKNOWN,
            trust = trust,
            missingSink = missingPerms,
        )

        // WhatsApp — needs both install AND no permission gate
        androidActions += whatsAppRow(trust)

        // Calls
        androidActions += capabilityRow(
            registry.calls,
            label = "Make calls",
            restrictedTrust = trust == TrustLevel.UNKNOWN,
            trust = trust,
            missingSink = missingPerms,
        )

        // Open apps (no permission required)
        androidActions += LocalAction(
            id = "open_app",
            label = "Open apps",
            state = if (registry.apps.isAvailable()) AvailabilityState.AVAILABLE else AvailabilityState.UNKNOWN,
        )

        // Location
        androidActions += capabilityRow(
            registry.location,
            label = "Get your location",
            restrictedTrust = trust == TrustLevel.UNKNOWN,
            trust = trust,
            missingSink = missingPerms,
        )

        // Camera / photos
        androidActions += capabilityRow(
            registry.camera,
            label = "Take photos",
            restrictedTrust = false,
            trust = trust,
            missingSink = missingPerms,
        )

        // Screenshot — `requiresUserConsent` is treated as "needs a one-time
        // grant" so unavailable maps to PERMISSION_MISSING rather than
        // HARDWARE_MISSING.
        androidActions += if (registry.screenshot.isAvailable()) {
            LocalAction(
                id = "screenshot",
                label = "Capture screenshots",
                state = AvailabilityState.AVAILABLE,
            )
        } else {
            recommended += "Grant screen-capture consent to enable screenshots"
            LocalAction(
                id = "screenshot",
                label = "Capture screenshots",
                state = AvailabilityState.PERMISSION_MISSING,
                reason = "Screenshot permission not granted",
            )
        }

        // Contacts
        androidActions += capabilityRow(
            registry.contacts,
            label = "Look up contacts",
            restrictedTrust = trust == TrustLevel.UNKNOWN,
            trust = trust,
            missingSink = missingPerms,
        )

        // Notifications
        androidActions += capabilityRow(
            registry.notification,
            label = "Show notifications",
            restrictedTrust = false,
            trust = trust,
            missingSink = missingPerms,
        )

        // Recommend installs
        if (!registry.whatsApp.isWhatsAppInstalled()) {
            recommended += "Install WhatsApp to enable WhatsApp messaging"
        }
        if (trust == TrustLevel.UNKNOWN) {
            recommended += "Verify your voice as the device owner to unlock messages, email and location sharing"
        }
        if (!openClawConnected) {
            recommended += "Connect to OpenClaw to enable email, research, memory, and business tasks"
        }

        // OpenClaw skills — empty list when the manifest hasn't arrived yet.
        val manifest = protocolClient.skillManifest.value
        val skills = openClawSkills(manifest, openClawConnected)

        val audio = audioRouter.state.value
        return AwarenessSnapshot(
            androidActions = androidActions,
            openClawSkills = skills,
            openClawConnected = openClawConnected,
            bluetoothMicConnected = audio.bluetoothScoConnected,
            bluetoothOutputConnected = audio.bluetoothA2dpConnected || audio.bluetoothScoConnected,
            trustLevel = trust.name,
            missingPermissions = missingPerms,
            recommendedSetup = recommended,
        )
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private fun simpleStateOf(
        cap: Capability,
        trust: TrustLevel,
        restrictedTrust: Boolean,
    ): AvailabilityState = when {
        restrictedTrust && trust == TrustLevel.UNKNOWN -> AvailabilityState.DISABLED_BY_TRUST
        cap.isAvailable() -> AvailabilityState.AVAILABLE
        cap.requiredPermissions.any { perm ->
            context.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED
        } -> AvailabilityState.PERMISSION_MISSING
        else -> AvailabilityState.UNKNOWN
    }

    private fun capabilityRow(
        cap: Capability,
        label: String,
        restrictedTrust: Boolean,
        trust: TrustLevel,
        missingSink: MutableList<MissingPermission>,
    ): LocalAction {
        val state = simpleStateOf(cap, trust, restrictedTrust)
        if (state == AvailabilityState.PERMISSION_MISSING) {
            cap.requiredPermissions
                .filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                .forEach {
                    missingSink += MissingPermission(
                        capabilityId = cap.id,
                        permission = it,
                        rationale = label,
                    )
                }
        }
        val reason = when (state) {
            AvailabilityState.PERMISSION_MISSING ->
                "Permission not granted: ${cap.requiredPermissions.joinToString(", ")}"
            AvailabilityState.DISABLED_BY_TRUST ->
                "Owner verification required"
            AvailabilityState.UNKNOWN -> "Capability unavailable"
            else -> null
        }
        return LocalAction(
            id = cap.id,
            label = label,
            state = state,
            reason = reason,
            restrictedByTrust = restrictedTrust && trust == TrustLevel.UNKNOWN,
        )
    }

    private fun whatsAppRow(trust: TrustLevel): LocalAction {
        val installed = registry.whatsApp.isWhatsAppInstalled()
        return when {
            !installed -> LocalAction(
                id = "whatsapp",
                label = "Send WhatsApps",
                state = AvailabilityState.NOT_INSTALLED,
                reason = "WhatsApp is not installed",
            )
            trust == TrustLevel.UNKNOWN -> LocalAction(
                id = "whatsapp",
                label = "Send WhatsApps",
                state = AvailabilityState.DISABLED_BY_TRUST,
                reason = "Owner verification required",
                restrictedByTrust = true,
            )
            else -> LocalAction(
                id = "whatsapp",
                label = "Send WhatsApps",
                state = AvailabilityState.AVAILABLE,
            )
        }
    }

    private fun openClawSkills(
        manifest: OpenClawSkillManifest?,
        connected: Boolean,
    ): List<OpenClawSkillStatus> {
        if (manifest == null) return emptyList()
        return manifest.skills.map { s ->
            val state = when {
                !connected -> AvailabilityState.OFFLINE
                !s.available -> AvailabilityState.UNKNOWN
                else -> AvailabilityState.AVAILABLE
            }
            val reason = when (state) {
                AvailabilityState.OFFLINE -> "OpenClaw is offline"
                AvailabilityState.UNKNOWN -> "Skill is currently disabled"
                else -> null
            }
            OpenClawSkillStatus(
                id = s.id,
                name = s.name,
                description = s.description,
                state = state,
                reason = reason,
            )
        }
    }
}
