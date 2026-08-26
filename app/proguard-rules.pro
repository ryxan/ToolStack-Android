# Keep only app classes and members that reflection-based runtimes (Hilt/Navigation/Compose) need.
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <methods>;
    @dagger.Provides <methods>;
    @dagger.Binds <methods>;
}

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
