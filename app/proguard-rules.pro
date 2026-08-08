# ProGuard/R8 rules for Wearsic app

# ============================================================
# General Android
# ============================================================
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Exceptions,InnerClasses

# Keep the entry point
-keep class com.wearsic.app.MainActivity { *; }

# ============================================================
# Kotlin Serialization
# ============================================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
}

-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep serializable models
-keep class com.wearsic.app.data.model.** { *; }

# ============================================================
# Ktor Client
# ============================================================
-keep class io.ktor.** { *; }
-keep class io.ktor.client.** { *; }
-keep class io.ktor.serialization.** { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.serialization.**

# ============================================================
# Media3 / ExoPlayer
# ============================================================
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ============================================================
# Wear Compose
# ============================================================
-keep class androidx.wear.compose.** { *; }
-dontwarn androidx.wear.compose.**

# ============================================================
# Coil Image Loading
# ============================================================
-keep class coil.** { *; }
-dontwarn coil.**

# ============================================================
# OkHttp (used by Ktor)
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ============================================================
# DataStore
# ============================================================
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ============================================================
# Coroutines
# ============================================================
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ============================================================
# Compose
# ============================================================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ============================================================
# Keep enums
# ============================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================
# Keep Parcelable
# ============================================================
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ============================================================
# SLF4J (missing classes)
# ============================================================
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn org.slf4j.**

# ============================================================
# R8 full mode optimizations
# ============================================================
-allowaccessmodification
-repackageclasses ''
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
