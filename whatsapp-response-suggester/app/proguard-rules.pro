# Keep accessibility service
-keep class com.whatsappsuggester.service.** { *; }
-keep class com.whatsappsuggester.api.** { *; }

# Keep JSON parsing
-keepattributes Signature
-keepattributes *Annotation*
-keep class org.json.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
