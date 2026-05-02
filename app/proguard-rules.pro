# Ktor
-keep class io.ktor.** { *; }
-keep class kotlinx.serialization.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class ai.openclaw.jarvis.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt
-keep class dagger.hilt.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Release log stripping ────────────────────────────────────────────────────
# Strip every Log.d / Log.v call from release builds so nothing slips into
# logcat by accident. Defence-in-depth on top of LogRedaction at the source —
# even if a developer adds a raw Log.d in a future change, R8 will remove
# it on release. Log.w / Log.e are kept on purpose so genuine errors still
# surface.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# Keep our protocol + capability + policy / proactive / screen / streaming
# data classes — they're used by kotlinx-serialization through reflection
# and by Hilt @Inject constructors that R8 might otherwise mangle.
-keepclasseswithmembers class ai.openclaw.jarvis.protocol.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ai.openclaw.jarvis.capabilities.** { <init>(...); }
-keepclasseswithmembers class ai.openclaw.jarvis.policy.model.** { <init>(...); }
-keepclasseswithmembers class ai.openclaw.jarvis.proactive.model.** { <init>(...); }
-keepclasseswithmembers class ai.openclaw.jarvis.screen.model.** { <init>(...); }
