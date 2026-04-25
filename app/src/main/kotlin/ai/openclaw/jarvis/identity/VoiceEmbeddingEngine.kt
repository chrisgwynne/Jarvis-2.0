package ai.openclaw.jarvis.identity

import kotlin.math.*

/**
 * Lightweight MFCC-based voice embedding engine.
 *
 * No external ML framework — pure Kotlin, runs fully on-device.
 *
 * Pipeline: 16kHz PCM → framing → Hamming → DFT → Mel filterbank → log → DCT → mean MFCC
 *
 * Speaker identification via cosine similarity on unit-normalised mean-MFCC vectors.
 * Accuracy is sufficient for household-scale speaker separation (3–5 people).
 */
object VoiceEmbeddingEngine {

    private const val SAMPLE_RATE = 16_000
    private const val FRAME_LEN   = 400     // 25 ms at 16 kHz
    private const val HOP_LEN     = 160     // 10 ms hop
    private const val N_FILTERS   = 26
    private const val N_MFCC      = 13

    private val filterbank by lazy { buildMelFilterbank() }

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Compute a unit-normalised mean-MFCC embedding from 16kHz mono PCM. */
    fun computeEmbedding(pcm: ShortArray): FloatArray {
        val frames = mutableListOf<FloatArray>()
        var pos = 0
        while (pos + FRAME_LEN <= pcm.size) {
            val frame = FloatArray(FRAME_LEN) { i -> pcm[pos + i] / 32768f }
            applyHamming(frame)
            frames += computeMfcc(frame)
            pos += HOP_LEN
        }
        if (frames.isEmpty()) return FloatArray(N_MFCC)
        val mean = FloatArray(N_MFCC) { c ->
            frames.sumOf { it[c].toDouble() }.toFloat() / frames.size
        }
        return normalize(mean)
    }

    /** Cosine similarity between two unit-normalised vectors, in [-1, 1]. */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return -1f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot.coerceIn(-1f, 1f)
    }

    /** Map cosine similarity [-1,1] to a confidence value [0,1]. */
    fun cosineToConfidence(cosine: Float): Float = ((cosine + 1f) / 2f).coerceIn(0f, 1f)

    // ─── Internal ─────────────────────────────────────────────────────────────

    private fun applyHamming(frame: FloatArray) {
        for (i in frame.indices) {
            frame[i] *= (0.54 - 0.46 * cos(2.0 * PI * i / (frame.size - 1))).toFloat()
        }
    }

    private fun computeMfcc(frame: FloatArray): FloatArray {
        val spectrum = powerSpectrum(frame)
        val melEnergies = FloatArray(N_FILTERS) { k ->
            var e = 0f
            for (n in spectrum.indices) e += filterbank[k][n] * spectrum[n]
            ln(e.coerceAtLeast(1e-10f))
        }
        return dct(melEnergies).copyOf(N_MFCC)
    }

    private fun powerSpectrum(frame: FloatArray): FloatArray {
        val n = frame.size
        val half = n / 2 + 1
        return FloatArray(half) { k ->
            var re = 0.0; var im = 0.0
            for (t in frame.indices) {
                val angle = 2.0 * PI * k * t / n
                re += frame[t] * cos(angle)
                im -= frame[t] * sin(angle)
            }
            (re * re + im * im).toFloat()
        }
    }

    private fun buildMelFilterbank(): Array<FloatArray> {
        val halfFft = FRAME_LEN / 2 + 1
        val melLo   = hz2mel(80f)
        val melHi   = hz2mel(SAMPLE_RATE / 2f)
        val melPts  = FloatArray(N_FILTERS + 2) { i ->
            mel2hz(melLo + i * (melHi - melLo) / (N_FILTERS + 1))
        }
        val freqBin = FloatArray(halfFft) { i -> i.toFloat() * SAMPLE_RATE / FRAME_LEN }
        return Array(N_FILTERS) { m ->
            FloatArray(halfFft) { k ->
                val f  = freqBin[k]
                val lo = melPts[m]; val mid = melPts[m + 1]; val hi = melPts[m + 2]
                when {
                    f < lo || f > hi -> 0f
                    f <= mid         -> (f - lo) / (mid - lo)
                    else             -> (hi - f) / (hi - mid)
                }
            }
        }
    }

    private fun hz2mel(hz: Float) = 2595f * log10(1f + hz / 700f)
    private fun mel2hz(mel: Float) = 700f * (10f.pow(mel / 2595f) - 1f)

    private fun dct(x: FloatArray): FloatArray {
        val n = x.size
        return FloatArray(n) { k ->
            var sum = 0f
            for (i in x.indices) sum += x[i] * cos(PI * k * (2 * i + 1) / (2 * n)).toFloat()
            sum
        }
    }

    private fun normalize(v: FloatArray): FloatArray {
        val mag = sqrt(v.fold(0f) { a, f -> a + f * f })
        return if (mag < 1e-10f) v else FloatArray(v.size) { v[it] / mag }
    }
}
