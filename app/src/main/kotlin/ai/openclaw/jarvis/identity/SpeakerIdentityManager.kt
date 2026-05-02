package ai.openclaw.jarvis.identity

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.trust.TrustLevel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

private const val TAG = "SpeakerIdentityManager"
private const val PROFILES_DIR = "voice_profiles"

@Singleton
class SpeakerIdentityManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val profilesDir: File
        get() = File(context.filesDir, PROFILES_DIR).also { it.mkdirs() }
    private val profiles = mutableMapOf<String, SpeakerProfile>()

    init { loadProfiles() }

    // ─── Profile management ───────────────────────────────────────────────────

    fun enrolProfile(
        speakerId: String,
        displayName: String,
        trustLevel: TrustLevel,
        pcmSamples: List<ShortArray>,
    ) {
        val embeddings = pcmSamples.map { VoiceEmbeddingEngine.computeEmbedding(it) }
        if (embeddings.isEmpty()) return
        val dim = embeddings[0].size
        val avg = FloatArray(dim) { i -> embeddings.sumOf { it[i].toDouble() }.toFloat() / embeddings.size }
        var mag = 0f
        for (v in avg) mag += v * v
        mag = sqrt(mag)
        val normalized = if (mag > 1e-10f) FloatArray(dim) { avg[it] / mag } else avg

        val profile = SpeakerProfile(
            speakerId   = speakerId,
            displayName = displayName,
            trustLevel  = trustLevel,
            enrolledAt  = Instant.now().toString(),
            embedding   = normalized.toList(),
        )
        profiles[speakerId] = profile
        saveProfile(profile)
        Log.i(TAG, "Enrolled profile: $speakerId ($trustLevel)")
    }

    fun removeProfile(speakerId: String) {
        profiles.remove(speakerId)
        File(profilesDir, "$speakerId.json").delete()
    }

    fun updateTrustLevel(speakerId: String, trustLevel: TrustLevel) {
        val existing = profiles[speakerId] ?: return
        val updated = existing.copy(trustLevel = trustLevel)
        profiles[speakerId] = updated
        saveProfile(updated)
    }

    fun getAllProfiles(): List<SpeakerProfile> = profiles.values.toList()

    fun hasProfiles(): Boolean = profiles.isNotEmpty()

    fun findByName(name: String): SpeakerProfile? =
        profiles.values.firstOrNull { it.displayName.equals(name.trim(), ignoreCase = true) }

    // ─── Identification ───────────────────────────────────────────────────────

    fun identify(pcm: ShortArray): IdentityResult {
        if (profiles.isEmpty()) return IdentityResult.UNKNOWN
        val probe = VoiceEmbeddingEngine.computeEmbedding(pcm)
        var bestId = "unknown"
        var bestSim = -1f
        for ((id, profile) in profiles) {
            val sim = VoiceEmbeddingEngine.cosineSimilarity(probe, profile.embedding.toFloatArray())
            if (sim > bestSim) { bestSim = sim; bestId = id }
        }
        val confidence = VoiceEmbeddingEngine.cosineToConfidence(bestSim)
        // Resolve the matched profile defensively — `bestId` only differs
        // from "unknown" when at least one profile produced a similarity,
        // but profile maps can be mutated between iteration and lookup
        // when an enrolment lands concurrently. Fall through to UNKNOWN
        // rather than NPE.
        val matched = profiles[bestId]
        return if (confidence >= IdentityResult.CONFIDENT_THRESHOLD && matched != null) {
            IdentityResult(bestId, confidence, matched.trustLevel)
        } else {
            IdentityResult.UNKNOWN.copy(confidence = confidence)
        }
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    private fun loadProfiles() {
        profilesDir.listFiles { f -> f.name.endsWith(".json") }?.forEach { file ->
            runCatching {
                val profile = json.decodeFromString<SpeakerProfile>(file.readText())
                profiles[profile.speakerId] = profile
            }.onFailure { Log.w(TAG, "Failed to load profile ${file.name}: ${it.message}") }
        }
        Log.i(TAG, "Loaded ${profiles.size} voice profile(s)")
    }

    private fun saveProfile(profile: SpeakerProfile) {
        File(profilesDir, "${profile.speakerId}.json").writeText(json.encodeToString(profile))
    }
}
