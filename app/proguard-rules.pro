# Wearsic release shrinking rules.
# AndroidX/Compose/Media3/Ktor/Coil ship their own consumer rules; do not keep
# entire libraries here or R8 cannot remove unused code from the watch APK.

-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable,Exceptions

# Manifest entry points and MediaSessionService callbacks.
-keep class com.wearsic.app.MainActivity { <init>(...); }
-keep class com.wearsic.app.service.MediaPlaybackService { <init>(...); *; }

# Kotlin serialization generated serializers for the network/cache models.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.wearsic.app.data.model.** { *; }

# Keep the app's serializable request model used by reflection-free Ktor tests
# and release request construction.
-keep class com.wearsic.app.data.repository.YoutubeCookieRequest { *; }

# Kotlin/Android generated metadata that R8 must preserve.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Optional bindings may be absent on Wear OS.
-dontwarn org.slf4j.**
