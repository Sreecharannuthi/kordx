package com.android.rockages.kordx

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Regression test for the metaphony ProGuard / R8 keep rules.
 *
 * **Background.** `metaphony/src/main/cpp/AudioMetadataParser.cpp` bridges
 * JNI -> Java/Kotlin by looking up Java methods by their *literal names*
 * (`FindClass("...AudioMetadataParser")` + `GetMethodID(..., "putTag", ...)`,
 * `"putPicture"`, `"putAudioProperty"`). R8 in non-full mode does NOT follow
 * these call sites; it obfuscates the method names. At runtime
 * `JNI_OnLoad()` calls `GetMethodID()` for the original names, gets null, and
 * the first native invocation raises `NoSuchMethodError`. Because the error
 * originates in native code it is NOT caught by the Kotlin
 * `try/catch (Exception)` around the scan in `MediaExposer.fetch()`; it
 * aborts the runtime (SIGABRT). During a media-folder scan
 * (`kordx.groove.fetch()` launched from a coroutine) this kills the whole
 * process — the user sees the app "minimise and not reopen" the instant they
 * finish adding a folder of songs.
 *
 * This was reproduced on an AVD running the release APK: adding the
 * `/sdcard/Music` folder and tapping Done triggered
 * `Fatal signal 6 (SIGABRT) ... tid ... (DefaultDispatch)` with
 * `NoSuchMethodError: no non-static method "...AudioMetadataParser.putTag(...)V"`.
 * The debug APK never showed it because debug builds skip R8.
 *
 * The keep rule lives in `metaphony/consumer-rules.pro` so it is automatically
 * merged into the consuming `app` module's R8 config. This test pins the rule
 * (and the three JNI callback method names it protects) so an over-eager
 * refactor or a move of the rule can't silently break the JNI contract again.
 */
class MetaphonyProguardRulesTest {

    private fun loadSource(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            // Common when running from app module's working dir.
            File("app/$relativePath"),
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.isFile) {
                return candidate.readText()
            }
        }
        throw IllegalStateException(
            "Could not locate source file $relativePath in ${System.getProperty("user.dir")}"
        )
    }

    private fun loadConsumerRules(): String =
        loadSource("metaphony/consumer-rules.pro")

    @Test
    fun keepsAudioMetadataParserClass() {
        val rules = loadConsumerRules()
        assertTrue(
            rules.contains(
                "-keep class com.android.rockages.kordx.metaphony.AudioMetadataParser"
            ),
            "metaphony/consumer-rules.pro must -keep " +
                "com.android.rockages.kordx.metaphony.AudioMetadataParser. The C++ JNI " +
                "code does FindClass(\"com/android/rockages/kordx/metaphony/AudioMetadataParser\") " +
                "and GetMethodID for putTag/putPicture/putAudioProperty, so the class name " +
                "and method names must not be obfuscated by R8, or the native scan crashes " +
                "the app (SIGABRT) when a media folder is added.",
        )
    }

    @Test
    fun keepsJniCallbackPutTag() {
        val rules = loadConsumerRules()
        assertTrue(
            rules.contains("putTag"),
            "metaphony/consumer-rules.pro must preserve the `putTag` method name. " +
                "AudioMetadataParser.cpp calls GetMethodID(..., \"putTag\", " +
                "\"(Ljava/lang/String;Ljava/lang/String;)V\") during JNI_OnLoad(); " +
                "if R8 renames it, GetMethodID returns null and the scan aborts the process.",
        )
    }

    @Test
    fun keepsJniCallbackPutPicture() {
        val rules = loadConsumerRules()
        assertTrue(
            rules.contains("putPicture"),
            "metaphony/consumer-rules.pro must preserve the `putPicture` method name. " +
                "AudioMetadataParser.cpp calls GetMethodID(..., \"putPicture\", " +
                "\"(Ljava/lang/String;Ljava/lang/String;[B)V\"); if R8 renames it the " +
                "native scan raises NoSuchMethodError and SIGABRTs the process.",
        )
    }

    @Test
    fun keepsJniCallbackPutAudioProperty() {
        val rules = loadConsumerRules()
        assertTrue(
            rules.contains("putAudioProperty"),
            "metaphony/consumer-rules.pro must preserve the `putAudioProperty` method name. " +
                "AudioMetadataParser.cpp calls GetMethodID(..., \"putAudioProperty\", " +
                "\"(Ljava/lang/String;I)V\"); if R8 renames it the native scan raises " +
                "NoSuchMethodError and SIGABRTs the process.",
        )
    }

    @Test
    fun keepRuleCoversAllMembers() {
        val rules = loadConsumerRules()
        assertTrue(
            // `{ *; }` keeps the class name AND every member (the callback
            // methods). A rule scoped only to the class name without member
            // wildcards would still let R8 rename the methods.
            rules.contains("-keep class com.android.rockages.kordx.metaphony.AudioMetadataParser { *; }"),
            "metaphony/consumer-rules.pro must keep the AudioMetadataParser class AND its " +
                "members (`{ *; }`). R8 keeps the class name anyway (because of the private " +
                "external readMetadataNative), but the JNI *callback* methods (putTag / " +
                "putPicture / putAudioProperty) are plain Kotlin methods and must be kept " +
                "explicitly with `{ *; }` so their names survive minification.",
        )
    }
}
