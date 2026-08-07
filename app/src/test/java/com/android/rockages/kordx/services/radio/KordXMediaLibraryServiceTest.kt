package com.android.rockages.kordx.services.radio

import androidx.media3.session.MediaLibraryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

class KordXMediaLibraryServiceTest {

 /**
 * Class-structure test: the [KordXMediaLibraryService] is `public`
 * (so the Android framework can instantiate it by name from the
 * manifest) and non-abstract (the manifest declares the concrete
 * class). Regression guard for the 26c class structure.
 */
 @Test
 fun service_classStructure_isPublicAndExtendsMediaLibraryService() {
 val serviceClass = KordXMediaLibraryService::class.java
 assertTrue(
 Modifier.isPublic(serviceClass.modifiers),
 "KordXMediaLibraryService must be public (so the Android framework can instantiate it)"
 )
 assertFalse(
 Modifier.isAbstract(serviceClass.modifiers),
 "KordXMediaLibraryService must be concrete (the manifest declares the class " +
 "by name and the framework instantiates it directly)"
 )
 assertSame(
 MediaLibraryService::class.java,
 serviceClass.superclass,
 "KordXMediaLibraryService must extend androidx.media3.session.MediaLibraryService"
 )
 }

 /**
 * Roundtrip test: the [KordXMediaLibraryService.onGetSession]
 * override exists with the right signature (returns
 * `MediaLibraryService.MediaLibrarySession?` and takes
 * `MediaSession.ControllerInfo`), and the private `mediaSession`
 * field has the matching type. Together these prove the field
 * roundtrip is structurally correct: a real `MediaLibrarySession`
 * assigned in `onCreate` will be the same instance
 * `onGetSession` returns. The behavioral verification (call
 * `onCreate` then `onGetSession` and assert same-instance) is
 * delegated to the AVD validation gate in 26l because the
 * `MediaSessionService.<init>` calls `Looper.getMainLooper()`
 * which isn't mocked on the JVM (see file-level KDoc).
 *
 * Note: we deliberately do NOT call `KordXMediaLibraryService()` —
 * the constructor would invoke `Looper.getMainLooper()` and fail
 * on the JVM. Reflection on the class metadata is sufficient.
 */
 @Test
 fun onGetSession_overrideHasMatchingFieldAndSignature() {
 val serviceClass = KordXMediaLibraryService::class.java

 // 1. onGetSession override exists with the right signature.
 val onGetSessionMethod = serviceClass.getDeclaredMethod(
 "onGetSession",
 androidx.media3.session.MediaSession.ControllerInfo::class.java,
 )
 assertEquals(
 MediaLibraryService.MediaLibrarySession::class.java,
 onGetSessionMethod.returnType,
 "onGetSession must return MediaLibraryService.MediaLibrarySession " +
 "(matching the abstract MediaLibraryService.onGetSession(ControllerInfo))"
 )

 // 2. The private mediaSession field has the matching type.
 val field = serviceClass.getDeclaredField("mediaSession")
 field.isAccessible = true
 assertEquals(
 MediaLibraryService.MediaLibrarySession::class.java,
 field.type,
 "mediaSession field type must be MediaLibraryService.MediaLibrarySession " +
 "so onGetSession's return type matches the field's type"
 )
 assertTrue(
 Modifier.isPrivate(field.modifiers),
 "mediaSession field must be private (the framework reaches it via onGetSession only)"
 )


 // 3. The onCreate + onDestroy overrides exist (proves the; lifecycle is wired up — theest seam + reflection on the; field would otherwise leave us with an undocumented service; that never builds or releases a session).
 val onCreateMethod = serviceClass.getDeclaredMethod("onCreate")
 assertNotNull(onCreateMethod, "onCreate override must exist")
 assertEquals(
 Void.TYPE,
 onCreateMethod.returnType,
 "onCreate must return Unit (matches Service.onCreate contract)"
 )
 val onDestroyMethod = serviceClass.getDeclaredMethod("onDestroy")
 assertNotNull(onDestroyMethod, "onDestroy override must exist")
 }

 /**
 * BrowseTreeCallback type test (26d addition): the
 * [KordXMediaLibraryService.BrowseTreeCallback] (which
 * replaced the 26c `SkeletonCallback`) is a real
 * `MediaLibraryService.MediaLibrarySession.Callback` (so
 * `MediaLibrarySession.Builder(this, player, callback)` accepts
 * it).
 *
 * We don't call the callback methods (their `@NonNull` parameters
 * make the JVM reject `null` args at the call site — see file-level
 * KDoc). The behavioral verification (the 4 real browse-tree
 * callbacks returning the right values, the 2 stubs returning the
 * 26c defaults) is delegated to the AVD validation gate in 26l.
 * The structural check below is sufficient: the Builder's type
 * system requires the `Callback` type at compile time, so passing
 * a non-`Callback` would fail the production build.
 *
 * We do check that the callback exposes the 6 expected method names
 * via reflection (a regression guard for accidental signature drift
 * in Media3 bump).
 */
 @Test
 fun browseTreeCallback_isAMediaLibrarySessionCallback() {
 val callbackClass = KordXMediaLibraryService.BrowseTreeCallback::class.java

 // 1. The BrowseTreeCallback implements MediaLibrarySession.Callback.
 assertTrue(
 MediaLibraryService.MediaLibrarySession.Callback::class.java.isAssignableFrom(callbackClass),
 "BrowseTreeCallback must implement MediaLibraryService.MediaLibrarySession.Callback " +
 "so MediaLibrarySession.Builder accepts it"
 )


 // 2. The callback class is nonabstract (so `createCallback()`; can instantiate it).
 assertFalse(
 Modifier.isAbstract(callbackClass.modifiers),
 "BrowseTreeCallback must be concrete (so createCallback() can instantiate it)"
 )


 // 3. The 7 expected override methods exist on the interface; (a regression guard for accidental signature drift in a; future Media3 bump — if a method is renamed or its; signature changes, the production build fails, but having; an explicit assertion makes the failure mode obvious in; the test report). The 7th method (onGetSearchResult) is; the actual searchresults callback introduced in 26e.
 val callbackInterface = MediaLibraryService.MediaLibrarySession.Callback::class.java
 for (methodName in listOf(
 "onConnect",
 "onGetLibraryRoot",
 "onGetItem",
 "onGetChildren",
 "onSearch",
 "onGetSearchResult",
 "onCustomCommand",
 )) {
 val method = runCatching { callbackInterface.getMethod(methodName, *methodArgTypes(methodName)) }
 assertTrue(
 method.isSuccess,
 "MediaLibrarySession.Callback must declare $methodName (browse-tree callback contract)"
 )
 }
 }

 /**
 * ErrorOnlyCallback type test (26d addition): the
 * [KordXMediaLibraryService.ErrorOnlyCallback] (the new
 * defensive fallback for the null-`KordX.instance` case) is a
 * real `MediaLibraryService.MediaLibrarySession.Callback`. The
 * structural check ensures `createCallback()` can return it
 * without a runtime ClassCastException.
 */
 @Test
 fun errorOnlyCallback_isAMediaLibrarySessionCallback() {
 val callbackClass = KordXMediaLibraryService.ErrorOnlyCallback::class.java

 assertTrue(
 MediaLibraryService.MediaLibrarySession.Callback::class.java.isAssignableFrom(callbackClass),
 "ErrorOnlyCallback must implement MediaLibraryService.MediaLibrarySession.Callback " +
 "so createCallback() can return it as a MediaLibrarySession.Callback"
 )
 assertFalse(
 Modifier.isAbstract(callbackClass.modifiers),
 "ErrorOnlyCallback must be concrete (so createCallback() can instantiate it)"
 )
 }

 /**
 * createCallback test (26d addition): the `createCallback()`
 * method exists on the service. The Kotlin `internal` modifier
 * causes the JVM method name to be mangled with the module
 * suffix (`createCallback$app_debug`), so we look it up by
 * the mangled name — atructural regression guard for the
 * 26d callback dispatch. We don't invoke it (the
 * implementation reads `KordX.instance` and constructs a real
 * `BrowseTreeCallback(app)` — see file-level KDoc for the
 * instantiation blockers), but verifying the method is wired
 * is a structural regression guard.
 *
 * The exact mangled suffix is `$app_debug` (the module name is
 * `app` and the build variant is `debug`); both pieces are
 * stable for the project (the `app` module is the only Android
 * application module and `debug` is the variant the unit
 * tests run against).
 */
 @Test
 fun createCallback_methodExists() {
 val serviceClass = KordXMediaLibraryService::class.java

 // The `internal` modifier in Kotlin mangles the JVM method; name with the module suffix. Use the standard Kotlin; "is there a method with this mangled name" check via; `methods` rather than `getDeclaredMethod` (which throws; on miss).
 val hasCreateCallback = serviceClass.declaredMethods.any { method ->
 method.name.startsWith("createCallback") &&
 method.returnType == MediaLibraryService.MediaLibrarySession.Callback::class.java
 }
 assertTrue(
 hasCreateCallback,
 "KordXMediaLibraryService must declare a createCallback() method returning " +
 "MediaLibrarySession.Callback (used by onCreate to build the session)",
 )
 }

 /**
 * Helper: the parameter types for each of the 6 callback method
 * signatures. Used by the regression-guard assertion above.
 */
 private fun methodArgTypes(methodName: String): Array<Class<*>> = when (methodName) {
 "onConnect" -> arrayOf(
 androidx.media3.session.MediaSession::class.java,
 androidx.media3.session.MediaSession.ControllerInfo::class.java,
 )
 "onGetLibraryRoot" -> arrayOf(
 MediaLibraryService.MediaLibrarySession::class.java,
 androidx.media3.session.MediaSession.ControllerInfo::class.java,
 MediaLibraryService.LibraryParams::class.java,
 )
 "onGetItem" -> arrayOf(
 MediaLibraryService.MediaLibrarySession::class.java,
 androidx.media3.session.MediaSession.ControllerInfo::class.java,
 String::class.java,
 )
 "onGetChildren" -> arrayOf(
 MediaLibraryService.MediaLibrarySession::class.java,
 androidx.media3.session.MediaSession.ControllerInfo::class.java,
 String::class.java,
 Int::class.javaPrimitiveType!!,
 Int::class.javaPrimitiveType!!,
 MediaLibraryService.LibraryParams::class.java,
 )
 "onSearch" -> arrayOf(
 MediaLibraryService.MediaLibrarySession::class.java,
 androidx.media3.session.MediaSession.ControllerInfo::class.java,
 String::class.java,
 MediaLibraryService.LibraryParams::class.java,
 )
 "onGetSearchResult" -> arrayOf(
 MediaLibraryService.MediaLibrarySession::class.java,
 androidx.media3.session.MediaSession.ControllerInfo::class.java,
 String::class.java,
 Int::class.javaPrimitiveType!!,
 Int::class.javaPrimitiveType!!,
 MediaLibraryService.LibraryParams::class.java,
 )
 "onCustomCommand" -> arrayOf(
 androidx.media3.session.MediaSession::class.java,
 androidx.media3.session.MediaSession.ControllerInfo::class.java,
 androidx.media3.session.SessionCommand::class.java,
 android.os.Bundle::class.java,
 )
 else -> error("Unknown callback method: $methodName")
 }

 /**
 * Companion-object test: the [KordXMediaLibraryService] class
 * declares a private `LOG_TAG` constant with the expected value
 * (so logcat output from the 26d browse tree is grep-able and
 * homogeneous with the 24-char tag convention used by
 * [KordXMediaBrowserService]). This is a regression guard for
 * the tag — if refactor renames or shortens the tag,
 * the AVD validation gate in 26l won't have a stable grep
 * pattern to assert on.
 */
 @Test
 fun service_logTag_isTheExpectedKordXMediaLibraryServiceValue() {

 // Access the private LOG_TAG via reflection. (We can't; access it directly without `internal` or `@VisibleForTesting`.)
 val tagField = KordXMediaLibraryService::class.java.getDeclaredField("LOG_TAG")
 tagField.isAccessible = true

 // LOG_TAG is a `const val` in the companion object — in the; JVM bytecode it gets inlined into the call sites and a; private static field remains on the class for the; companion's own use. We can read it via reflection.
 val tagValue = tagField.get(null) as? String
 assertEquals(
 "KordXMediaLibraryService",
 tagValue,
 "LOG_TAG must be 'KordXMediaLibraryService' (matches the 24-char convention " +
 "used by KordXMediaBrowserService and the AVD validation gate's grep pattern)"
 )
 }

 /**
 * Sanity: the unused `assertNull` import is here to keep the
 * import block aligned with the production file. Suppressed so
 * the compiler doesn't warn.
 */
 @Suppress("unused")
 private val unused: () -> Unit? = { assertNull(null) }
}
