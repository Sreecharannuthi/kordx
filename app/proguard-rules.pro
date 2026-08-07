# KordX ProGuard / R8 rules

# --- Kotlin Serialization ---
#
# `MethodParameters` is REQUIRED for the i18n reflection loader. `Translation.kt`
# uses `_Keys::class.java.declaredConstructors.first { ... }.parameters.map
# { it.name to it.isNamePresent() }` to map JSON keys to the data-class
# constructor parameters BY NAME. Without the `MethodParameters` attribute
# (emitted by kotlinc when `kotlinOptions.freeCompilerArgs +=
# "-java-parameters"` is set), `Parameter.getName()` returns synthetic
# names ("p0", "p1", ...) on every Android runtime; every `obj[name]`
# lookup in `buildKeys` misses; the loader falls through to a
# positional fallback that shuffles every JSON value into the wrong
# field. R8 in non-full mode STRIPS `MethodParameters` by default
# even when the kotlinc flag is on (R8 operates on the .class bytecode
# after kotlinc emits it, and the attribute is not in the default
# `-keepattributes` list). Adding `MethodParameters` to the keep
# list makes R8 preserve the attribute, the loader sees real names
# (Songs, Albums, …), and the i18n maps correctly.
-keepattributes *Annotation*, InnerClasses, MethodParameters
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
#
# `Translation.kt` uses Java reflection to build the generated `_Keys`
# data class (`Translation.keysConstructor` calls
# `_Keys::class.java.declaredConstructors`). R8 in non-full mode
# does NOT follow reflection calls; it relies on these `-keep` rules
# to preserve the reflection targets. The class FQNs are the JVM-level
# ones (Kotlin `class` -> `Outer` + `Inner` -> `Outer$Inner`):
#
# - `Translation`         (com.android.rockages.kordx.services.i18n.Translation)
# - `_Translation`         (parent class of `Translation`, contains the nested data classes)
# - `_Translation$_Keys`   (the 251-field data class accessed via reflection)
# - `_Translation$_Container` (nested data class, used in `Translation` ctor signature)
# - `_Translation$_Locale` (nested data class, used in `_Container` ctor signature)
# - `_Translations`         (the static `localeCodes` / `localeDisplayNames` / `localeNativeNames` data source)
#
# The previous rules used the wrong FQNs (e.g. `Translation$_Keys`
# instead of `_Translation$_Keys` — `_Keys` is nested in `_Translation`,
# not `Translation`) so the rules were silent no-ops. R8 stripped the
# primary constructor of `_Keys`, the `keysConstructor` lazy delegate
# threw `NoSuchElementException: Array contains no element matching the
# predicate` at first i18n load, the exception propagated out of the
# `Translation` constructor → the `KordX` ViewModel constructor → the
# `ViewModelProvider` factory, and the user saw the red "Cannot create an
# instance of class com.android.rockages.kordx.KordX" crash screen on
# every release-build install. AVD validation missed this because the
# AVD gate runs a DEBUG build (no R8).
-keep class com.android.rockages.kordx.services.i18n.Translation { *; }
-keep class com.android.rockages.kordx.services.i18n._Translation { *; }
-keep class com.android.rockages.kordx.services.i18n._Translation$_Keys { *; }
-keep class com.android.rockages.kordx.services.i18n._Translation$_Container { *; }
-keep class com.android.rockages.kordx.services.i18n._Translation$_Locale { *; }
-keep class com.android.rockages.kordx.services.i18n._Translations { *; }

# --- Keep enums used in settings ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Keep data classes used in navigation ---
-keep class kotlinx.serialization.Serializable { *; }
