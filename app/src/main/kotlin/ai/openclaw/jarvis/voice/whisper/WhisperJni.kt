package ai.openclaw.jarvis.voice.whisper

/**
 * JNI bridge to whisper.cpp.
 *
 * To enable on-device Whisper transcription:
 *   1. Clone https://github.com/ggerganov/whisper.cpp
 *   2. Copy whisper.cpp + whisper.h + ggml*.{c,h} into app/src/main/cpp/
 *   3. Uncomment the externalNativeBuild block in app/build.gradle.kts
 *   4. Run `./gradlew assembleDebug` with NDK installed
 *
 * Until the native library is present, [isNativeAvailable] returns false
 * and [SpeechToTextProxy] falls back to the standard Android SpeechRecognizer.
 */
object WhisperJni {
    private var nativeAvailable = false

    init {
        try {
            System.loadLibrary("whisper_jni")
            nativeAvailable = true
        } catch (_: UnsatisfiedLinkError) {
            // Native library not built yet — app continues with Android STT fallback.
        }
    }

    fun isNativeAvailable(): Boolean = nativeAvailable

    /** Load a .ggml model file. Returns a native handle (> 0) on success, 0 on failure. */
    @JvmStatic external fun init(modelPath: String): Long

    /** Release a previously allocated model handle. */
    @JvmStatic external fun free(handle: Long)

    /**
     * Transcribe raw 16-bit PCM samples recorded at [sampleRate] Hz (16 000 recommended).
     * Blocks until inference completes. Returns recognised text, or "" on failure.
     */
    @JvmStatic external fun transcribePcm16(handle: Long, pcm: ShortArray, sampleRate: Int): String
}
