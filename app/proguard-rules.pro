# Keep all app classes to avoid runtime issues with Hilt/Navigation/Compose.
# Library code is still shrunk.
-keep class com.toolstack.io.** { *; }

# Hilt / Dagger
-keepclassmembers class * {
    @dagger.* *;
    @com.google.dagger.* *;
}
-keep class dagger.hilt.** { *; }
-keep class com.google.dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponentManagerHolder { *; }

# Navigation type-safe args
-keepnames class * extends androidx.navigation.NavArgs
-keepclassmembers class * implements androidx.navigation.NavArgs { *; }

# Kotlinx Serialization (for future type-safe navigation / obfuscated serializers)
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions, SourceFile, LineNumberTable
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.Serializer *;
}

# Coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
