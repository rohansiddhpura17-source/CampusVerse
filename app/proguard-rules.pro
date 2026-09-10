# CampusVerse Release Proguard / R8 Optimization Rules

# Preserve annotations for Compose and reflection
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Jetpack Compose
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# CampusVerse Data Models (Preserve property names for JSON/Data serialization)
-keep class com.campusverse.app.data.models.** { *; }
-keepclassmembers class com.campusverse.app.data.models.** { *; }
