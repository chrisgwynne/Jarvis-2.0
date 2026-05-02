// whisper_jni.cpp — JNI bridge for whisper.cpp
//
// STUB IMPLEMENTATION — returns empty strings until whisper.cpp is integrated.
//
// To wire up real transcription:
//   1. Place whisper.cpp, whisper.h, and ggml source files in this directory.
//   2. Update CMakeLists.txt to compile them.
//   3. Replace the stub bodies below with real whisper_init / whisper_full calls.
//
// Reference: https://github.com/ggerganov/whisper.cpp/tree/master/examples/whisper.android

#include <jni.h>
#include <string>

extern "C" {

JNIEXPORT jlong JNICALL
Java_ai_openclaw_jarvis_voice_whisper_WhisperJni_init(
        JNIEnv* env, jclass /*cls*/, jstring modelPath) {
    // TODO: replace with whisper_init_from_file(path)
    (void)env; (void)modelPath;
    return 0L;   // 0 = not available
}

JNIEXPORT void JNICALL
Java_ai_openclaw_jarvis_voice_whisper_WhisperJni_free(
        JNIEnv* /*env*/, jclass /*cls*/, jlong handle) {
    // TODO: replace with whisper_free((whisper_context*)handle)
    (void)handle;
}

JNIEXPORT jstring JNICALL
Java_ai_openclaw_jarvis_voice_whisper_WhisperJni_transcribePcm16(
        JNIEnv* env, jclass /*cls*/,
        jlong handle, jshortArray pcm, jint sampleRate) {
    // TODO: convert pcm → float, run whisper_full(), collect segments
    (void)handle; (void)pcm; (void)sampleRate;
    return env->NewStringUTF("");
}

} // extern "C"
