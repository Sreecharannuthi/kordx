# KordX ProGuard / R8 rules

# --- Kotlin Serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.android.rockages.kordx.**$$serializer { *; }
-keepclassmembers class com.android.rockages.kordx.** {
    *** Companion;
}

# --- Compose ---
-dontwarn androidx.compose.**

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# --- Coil ---
-dontwarn coil.**

# --- okhttp ---
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Media3 ---
-dontwarn androidx.media3.**

# --- fuzzywuzzy ---
-dontwarn me.xdrop.fuzzywuzzy.**

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# --- Keep app entry points ---
-keep class com.android.rockages.kordx.MainActivity { *; }
-keep class com.android.rockages.kordx.KordX { *; }
-keep class com.android.rockages.kordx.services.radio.RadioSession { *; }
-keep class com.android.rockages.kordx.KordXMediaBrowserService { *; }
-keep class com.android.rockages.kordx.KordXMediaLibraryService { *; }

# --- Keep i18n ---
-keep class com.android.rockages.kordx.services.i18n.Translation { *; }
-keep class com.android.rockages.kordx.services.i18n.Translation$_Keys { *; }
-keep class com.android.rockages.kordx.services.i18n.Translation$Translations { *; }

# --- Keep enums used in settings ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Keep data classes used in navigation ---
-keep class kotlinx.serialization.Serializable { *; }
