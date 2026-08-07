package com.android.rockages.kordx.services

import android.content.Context
import android.os.Build
import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.ui.theme.ThemeMode
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM unit tests for [Settings] state-holder entries.
 *
 * [Settings] is the app's user-preferences surface. These tests exercise the
 * SharedPreferences-backed entries by mocking the [KordX] dependency so the
 * heavy application graph is never constructed; the real Robolectric
 * application context provides a real SharedPreferences instance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SettingsTest {

    private fun settings(context: Context): Settings {
        val kordx = mockk<KordX>(relaxed = true)
        every { kordx.applicationContext } returns context
        return Settings(kordx)
    }

    @Test
    fun booleanEntryDefaultsAndPersists() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val settings1 = settings(app)
        val entry = settings1.useMaterialYou

        assertTrue("default value must match constructor default", entry.value)

        entry.setValue(false)
        assertFalse(entry.value)

        // Re-create Settings to verify persistence.
        val reloaded = settings(app).useMaterialYou
        assertFalse(reloaded.value)
    }

    @Test
    fun enumEntryDefaultsAndPersists() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val settings1 = settings(app)
        val entry = settings1.themeMode

        assertEquals(ThemeMode.SYSTEM, entry.value)

        entry.setValue(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, entry.value)

        val reloaded = settings(app).themeMode
        assertEquals(ThemeMode.DARK, reloaded.value)
    }

    @Test
    fun nullableStringEntryDefaultsToNullAndPersists() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val settings1 = settings(app)
        val entry = settings1.language

        assertNull(entry.value)

        entry.setValue("ta")
        assertEquals("ta", entry.value)

        val reloaded = settings(app).language
        assertEquals("ta", reloaded.value)
    }

    @Test
    fun stringSetEntryDefaultsAndPersists() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val settings1 = settings(app)
        val entry = settings1.artistMergeMap

        assertEquals(emptySet<String>(), entry.value)

        entry.setValue(setOf("A|B"))
        assertEquals(setOf("A|B"), entry.value)

        val reloaded = settings(app).artistMergeMap
        assertEquals(setOf("A|B"), reloaded.value)
    }
}
