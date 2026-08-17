# ====================================================================
# ProGuard / R8 Rules for Release Build (Xposed, Room & WebView Engine)
# ====================================================================

# --------------------------------------------------------------------
# 1. Xposed Framework API Rules
# --------------------------------------------------------------------
-dontwarn de.robv.android.xposed.**
-keep class de.robv.android.xposed.** { *; }
-keep interface de.robv.android.xposed.** { *; }
-keep interface de.robv.android.xposed.IXposedMod { *; }
-keep interface de.robv.android.xposed.IXposedHookLoadPackage { *; }
-keep interface de.robv.android.xposed.IXposedHookZygoteInit { *; }
-keep interface de.robv.android.xposed.IXposedHookInitPackageResources { *; }

# Keep Xposed Module Entry Points & Hook Classes
-keep class org.matrix.chromext.** { *; }
-keepclassmembers class org.matrix.chromext.** { *; }
-keep class com.example.xposed.** { *; }
-keepclassmembers class com.example.xposed.** { *; }

# --------------------------------------------------------------------
# 2. Room Database Rules
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
}
