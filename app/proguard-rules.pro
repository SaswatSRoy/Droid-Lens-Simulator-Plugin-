# Keep BuildConfig because we use it for API keys
-keep class com.droidlens.app.BuildConfig { *; }

# Keep DroidLens library entry points
-keep class com.droidlens.DroidLens { *; }
-keep class com.droidlens.config.** { *; }

# General Android rules
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# Compose-specific rules
-keep class androidx.compose.runtime.Composer { *; }
-keep class androidx.compose.runtime.Recomposer { *; }

# kotlinx-serialization
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
