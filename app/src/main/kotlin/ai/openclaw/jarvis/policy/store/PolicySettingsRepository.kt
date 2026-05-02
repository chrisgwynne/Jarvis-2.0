package ai.openclaw.jarvis.policy.store

import ai.openclaw.jarvis.policy.model.ActionKind
import ai.openclaw.jarvis.policy.model.AutonomyLevel
import ai.openclaw.jarvis.util.LazyHydrate
import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SharedPreferences-backed persistence for [PolicySettings]. Per-action
 * overrides are stored as discrete `override_<KIND>` entries so adding
 * a new ActionKind doesn't break old saved files.
 *
 * Lazy hydration: defaults are BALANCED + requireConfirmAllOutbound=true,
 * which is strictly stricter than any persisted user override could
 * be — so the brief default-only window during startup is harmless.
 */
@Singleton
class PolicySettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : PolicySettingsSource {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(PolicySettings())
    val settings: StateFlow<PolicySettings> = _settings.asStateFlow()

    private val hydrate = LazyHydrate(_settings) { load() }
    init { hydrate.start() }

    override fun current(): PolicySettings = _settings.value

    fun update(transform: (PolicySettings) -> PolicySettings) {
        hydrate.markUpdated()
        val next = transform(_settings.value)
        save(next)
        _settings.value = next
    }

    fun setOverride(kind: ActionKind, level: AutonomyLevel?) = update { s ->
        val next = if (level == null) s.perActionOverrides - kind
                   else s.perActionOverrides + (kind to level)
        s.copy(perActionOverrides = next)
    }

    private fun load() = PolicySettings(
        mode = runCatching {
            AutonomyMode.valueOf(prefs.getString(K_MODE, AutonomyMode.BALANCED.name)
                ?: AutonomyMode.BALANCED.name)
        }.getOrDefault(AutonomyMode.BALANCED),
        perActionOverrides = ActionKind.values().mapNotNull { kind ->
            val raw = prefs.getString(overrideKey(kind), null) ?: return@mapNotNull null
            runCatching { AutonomyLevel.valueOf(raw) }.getOrNull()?.let { kind to it }
        }.toMap(),
        requireConfirmAllOutbound = prefs.getBoolean(K_CONFIRM_OUTBOUND, true),
        allowAutoExecuteSafe = prefs.getBoolean(K_ALLOW_AUTO_SAFE, true),
        quietHoursForceConfirm = prefs.getBoolean(K_QUIET_FORCE_CONFIRM, true),
        approvalTimeoutMinutes = prefs.getInt(K_APPROVAL_TIMEOUT, 5),
    )

    private fun save(s: PolicySettings) {
        prefs.edit().apply {
            putString(K_MODE, s.mode.name)
            putBoolean(K_CONFIRM_OUTBOUND, s.requireConfirmAllOutbound)
            putBoolean(K_ALLOW_AUTO_SAFE, s.allowAutoExecuteSafe)
            putBoolean(K_QUIET_FORCE_CONFIRM, s.quietHoursForceConfirm)
            putInt(K_APPROVAL_TIMEOUT, s.approvalTimeoutMinutes)
            // Clear all override keys, then write only the ones we have, so
            // removing an override doesn't leave a stale entry behind.
            ActionKind.values().forEach { remove(overrideKey(it)) }
            for ((kind, level) in s.perActionOverrides) {
                putString(overrideKey(kind), level.name)
            }
        }.apply()
    }

    private fun overrideKey(kind: ActionKind) = "override_${kind.name}"

    companion object {
        private const val FILE_NAME = "jarvis_policy_settings"
        private const val K_MODE = "mode"
        private const val K_CONFIRM_OUTBOUND = "confirm_outbound"
        private const val K_ALLOW_AUTO_SAFE = "allow_auto_safe"
        private const val K_QUIET_FORCE_CONFIRM = "quiet_force_confirm"
        private const val K_APPROVAL_TIMEOUT = "approval_timeout_minutes"
    }
}
