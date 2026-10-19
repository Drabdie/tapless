# ---------------------------------------------------------------------------
# AI Phone Agent — R8/ProGuard rules (release build).
# Debug builds are not minified; these rules apply to `assembleRelease`.
# ---------------------------------------------------------------------------

# Keep the app + module entry points (Hilt, Compose entry points, DI graph).
-keep class com.aiphoneagent.** { *; }
-keep interface com.aiphoneagent.** { *; }

# --- Android AccessibilityService (instantiated by the framework) ----------
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keepclassmembers class * extends android.accessibilityservice.AccessibilityService {
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent);
    public void onInterrupt();
    public void onServiceConnected();
}

# --- Hilt / Dagger ---------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep,allowobfuscation,allowshrinking @interface dagger.hilt.android.HiltAndroidApp
-keep,allowobfuscation,allowshrinking @interface dagger.hilt.android.AndroidEntryPoint
-keep,allowobfuscation,allowshrinking @interface dagger.hilt.android.lifecycle.HiltViewModel

# --- Room ------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# --- Kotlin Serialization --------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.aiphoneagent.**$$serializer { *; }
-keepclassmembers class com.aiphoneagent.** { *** Companion; }
-keepclasseswithmembers class com.aiphoneagent.** { kotlinx.serialization.KSerializer serializer(...); }

# --- OkHttp / Okio ---------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Security crypto (EncryptedSharedPreferences / Tink) -------------------
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.crypto.tink.**

# --- Kotlin coroutines -----------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# --- Compose ---------------------------------------------------------------
-dontwarn androidx.compose.**

# Keep annotations & generic signatures used by reflection/serialization.
-keepattributes Signature, Exceptions, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep line numbers for readable crash reports, hide the source file name.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
