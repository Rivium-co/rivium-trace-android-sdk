# RiviumTrace Android SDK ProGuard Rules

# Keep SDK public API
-keep class co.rivium.trace.sdk.RiviumTrace { *; }
-keep class co.rivium.trace.sdk.RiviumTraceConfig { *; }
-keep class co.rivium.trace.sdk.RiviumTraceConfig$Builder { *; }

# Keep models
-keep class co.rivium.trace.sdk.models.** { *; }

# Keep callback interfaces
-keep interface co.rivium.trace.sdk.** { *; }

# Keep enum values
-keepclassmembers enum co.rivium.trace.sdk.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
