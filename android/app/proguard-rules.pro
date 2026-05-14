# Keep Kotlinx Serialization metadata.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep SceneView / Filament natives.
-keep class com.google.android.filament.** { *; }
-keep class io.github.sceneview.** { *; }
