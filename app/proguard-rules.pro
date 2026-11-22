# Keep Kotlinx Serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.grude.app.model.**$$serializer { *; }
-keepclassmembers class com.grude.app.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.grude.app.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
