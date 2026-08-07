package com.android.rockages.kordx

import com.android.rockages.kordx.services.AppMeta
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Regression tests: verify that the update-check system has been fully removed from KordX. */
class UpdateRemovalTest {

    @Test
    fun appMetaHasNoFetchLatestVersion() {
        val methods = AppMeta::class.java.methods.map { it.name }
        assertFalse(
            methods.contains("fetchLatestVersion"),
            "AppMeta must not have fetchLatestVersion method"
        )
    }

    @Test
    fun appMetaHasNoFetchLatestStableVersion() {
        val methods = AppMeta::class.java.methods.map { it.name }
        assertFalse(
            methods.contains("fetchLatestStableVersion"),
            "AppMeta must not have fetchLatestStableVersion method"
        )
    }

    @Test
    fun appMetaHasNoFetchLatestNightlyVersion() {
        val methods = AppMeta::class.java.methods.map { it.name }
        assertFalse(
            methods.contains("fetchLatestNightlyVersion"),
            "AppMeta must not have fetchLatestNightlyVersion method"
        )
    }

    @Test
    fun appMetaHasNoLatestVersionField() {
        val fields = AppMeta::class.java.fields.map { it.name }
        assertFalse(
            fields.contains("latestVersion"),
            "AppMeta must not have latestVersion field"
        )
    }

    @Test
    fun appMetaHasNoDiscordUrl() {
        val fields = AppMeta::class.java.declaredFields.map { it.name }
        assertFalse(
            fields.contains("discordUrl"),
            "AppMeta must not have discordUrl"
        )
    }

    @Test
    fun appMetaHasNoRedditUrl() {
        val fields = AppMeta::class.java.declaredFields.map { it.name }
        assertFalse(
            fields.contains("redditUrl"),
            "AppMeta must not have redditUrl"
        )
    }

    @Test
    fun kordxClassHasNoCheckVersionMethod() {
        val methods = KordX::class.java.declaredMethods.map { it.name }
        assertFalse(
            methods.contains("checkVersion"),
            "KordX must not have checkVersion method"
        )
    }

    @Test
    fun appMetaHasRequiredFields() {
        assertTrue(AppMeta.appName == "KordX", "appName must be KordX")
        assertTrue(AppMeta.packageName == "com.android.rockages.kordx", "packageName must be correct")
        assertTrue(AppMeta.githubRepositoryOwner == "Sreecharannuthi", "githubRepositoryOwner must be correct")
        assertTrue(AppMeta.githubRepositoryName == "kordx", "githubRepositoryName must be correct")
    }

    @Test
    fun appMetaHasNoStaleSymphonyReferences() {
        val fields = AppMeta::class.java.declaredFields
        for (field in fields) {
            field.isAccessible = true
            val value = field.get(AppMeta)
            if (value is String) {
                assertFalse(
                    value.lowercase().contains("symphony"),
                    "AppMeta field ${field.name} must not contain 'symphony': $value"
                )
            }
        }
    }
}
