package com.android.rockages.kordx

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Verify theme and resource consistency. */
class ThemeResourceTest {

    @Test
    fun buildConfigHasKordXPackage() {
        assertTrue(
            BuildConfig.APPLICATION_ID.contains("com.android.rockages.kordx"),
            "BuildConfig must use KordX package"
        )
    }

    @Test
    fun buildConfigHasNoSymphony() {
        assertFalse(
            BuildConfig.APPLICATION_ID.contains("symphony", ignoreCase = true),
            "BuildConfig must not contain 'symphony'"
        )
    }

    @Test
    fun buildConfigHasNoRemusic() {
        assertFalse(
            BuildConfig.APPLICATION_ID.contains("remusic", ignoreCase = true),
            "BuildConfig must not contain 'remusic'"
        )
    }

    @Test
    fun buildConfigDebugMode() {
        assertTrue(BuildConfig.DEBUG, "Debug build should have DEBUG=true")
    }

    @Test
    fun buildConfigVersionCodePositive() {
        assertTrue(BuildConfig.VERSION_CODE > 0, "VERSION_CODE must be positive")
    }

    @Test
    fun buildConfigVersionNameNotBlank() {
        assertTrue(BuildConfig.VERSION_NAME.isNotBlank(), "VERSION_NAME must be set")
    }

    @Test
    fun buildConfigHasNoStaleSymphonyReferences() {
        // Verify the build config class name doesn't reference stale branding.
        assertFalse(
            BuildConfig::class.java.simpleName.contains("Symphony"),
            "BuildConfig class name must not contain Symphony"
        )
    }
}
