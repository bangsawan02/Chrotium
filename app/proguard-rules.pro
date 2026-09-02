# ====================================================================
# ProGuard / R8 Rules for Release Build (Room & WebView Engine)
# ====================================================================

# --------------------------------------------------------------------
# 1. Room Database Rules
# --------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
}
-keep class *_Impl { *; }
-dontwarn androidx.room.paging.**

# --------------------------------------------------------------------
# 3. WebView & JavaScript Bridge Rules
# --------------------------------------------------------------------
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.example.engine.TampermonkeyBridge { *; }

# --------------------------------------------------------------------
# 4. Core Data Models, Engines & ViewModels
# --------------------------------------------------------------------
-keep class com.example.data.** { *; }
-keep class com.example.engine.** { *; }
-keep class com.example.ui.** { *; }

# --------------------------------------------------------------------
# 5. Kotlin & Coroutines Optimization
# --------------------------------------------------------------------
-keepnames class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class * implements kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class * implements kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keep class kotlinx.coroutines.CoroutineExceptionHandler { *; }

# --------------------------------------------------------------------
# 6. General Attributes & Stacktrace Preservation
# --------------------------------------------------------------------
-keepattributes Signature, InnerClasses, EnclosingMethod, Annotations, JavascriptInterface, *Annotation*
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Remove verbose debug logs in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# Keep QuickJS Native Engine & Ktor Client
-keep class app.cash.quickjs.** { *; }
-dontwarn app.cash.quickjs.**
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Advanced R8 compiler optimizations
-repackageclasses ''
-allowaccessmodification
