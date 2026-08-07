package com.android.rockages.kordx

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Regression tests: package namespace and application ID consistency. */
class PackageConsistencyTest {

    @Test
    fun applicationIdIsCorrect() {
        assertTrue(
            BuildConfig.APPLICATION_ID.startsWith("com.android.rockages.kordx"),
            "Application ID must start with com.android.rockages.kordx"
        )
    }

    @Test
    fun applicationIdDebugSuffix() {
        assertTrue(
            BuildConfig.APPLICATION_ID.endsWith(".debug"),
            "Debug build should have .debug suffix"
        )
    }

    @Test
    fun versionNameIsSet() {
        assertTrue(
            BuildConfig.VERSION_NAME.isNotBlank(),
            "VERSION_NAME must be set"
        )
    }

    @Test
    fun versionCodeIsPositive() {
        assertTrue(
            BuildConfig.VERSION_CODE > 0,
            "VERSION_CODE must be positive"
        )
    }

    @Test
    fun debugModeEnabled() {
        assertTrue(BuildConfig.DEBUG, "Debug build should have DEBUG=true")
    }
}
