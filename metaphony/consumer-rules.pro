# KordX metaphony — consumer ProGuard / R8 rules
#
# These rules are merged into every consuming module (notably the `app`
# module) when R8 minifies the release build. They MUST stay in sync with
# the JNI boundary declared in `src/main/cpp/AudioMetadataParser.cpp`.
#
# ---------------------------------------------------------------------------
# CRITICAL: keep the JNI callback entry points on AudioMetadataParser
# ---------------------------------------------------------------------------
# `AudioMetadataParser.cpp` bridges JNI -> Java by LOOKING UP Java methods by
# their literal names (R8 does NOT follow these call sites, so it will
# otherwise obfuscate them):
#
#   JNI_OnLoad():
#     FindClass("com/android/rockages/kordx/metaphony/AudioMetadataParser")
#     GetMethodID(..., "putTag",          "(Ljava/lang/String;Ljava/lang/String;)V")
#     GetMethodID(..., "putPicture",      "(Ljava/lang/String;Ljava/lang/String;[B)V")
#     GetMethodID(..., "putAudioProperty","(Ljava/lang/String;I)V")
#   readMetadataNative():
#     CallVoidMethod(thiz, putTagMethodId, ...)
#     CallVoidMethod(thiz, putPictureMethodId, ...)
#     CallVoidMethod(thiz, putAudioPropertyMethodId, ...)
#
# If any of these names are renamed, GetMethodID() returns null and the next
# JNI call raises NoSuchMethodError. Because the error originates in native
# code it is not caught by the Kotlin `try/catch (Exception)` around the scan
# in MediaExposer.fetch(); it aborts the runtime (SIGABRT). During a
# media-folder scan (kordx.groove.fetch() launched from a coroutine) this
# kills the whole process — the user sees the app "minimise and not reopen"
# the moment they finish adding a folder.
#
# The private `external fun readMetadataNative` is auto-kept by R8 (native
# methods are preserved so the generated `Java_..._readMetadataNative` C++
# symbol resolves), but the *callback* methods above are plain Kotlin
# methods and must be kept explicitly.
#
# `*-keep class ... { *; }` keeps the class name AND every member — which is
# exactly what JNI needs: a stable class FQN and the original method names.
-keep class com.android.rockages.kordx.metaphony.AudioMetadataParser { *; }
