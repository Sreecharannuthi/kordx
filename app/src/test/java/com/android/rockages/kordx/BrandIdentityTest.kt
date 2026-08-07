package com.android.rockages.kordx

import com.android.rockages.kordx.services.AppMeta
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Regression tests: the KordX brand identity and package consistency. */
class BrandIdentityTest {

    @Test
    fun appMetaIsKordX() {
        assertEquals("KordX", AppMeta.appName)
        assertEquals("com.android.rockages.kordx", AppMeta.packageName)
    }

    @Test
    fun appMetaPointsAtKordXRepository() {
        assertTrue(AppMeta.githubRepositoryName == "kordx")
        assertTrue(AppMeta.githubRepositoryUrl.endsWith("/kordx"))
        assertFalse(AppMeta.githubRepositoryUrl.contains("symphony"))
    }

    @Test
    fun noSymphonyInPackageIdentity() {
        assertFalse(AppMeta.packageName.contains("symphony", ignoreCase = true))
    }
}
