# Droid Lens consumer ProGuard rules
# These rules are applied to any app that depends on droidlens-core

# Keep all public Droid Lens API classes
-keep class com.droidlens.DroidLens { *; }
-keep class com.droidlens.config.** { *; }
-keep class com.droidlens.model.** { *; }

# Keep Room entities and DAOs
-keep class com.droidlens.db.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep class kotlinx.serialization.** { *; }

# Keep MediaPipe classes
-keep class com.google.mediapipe.** { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
