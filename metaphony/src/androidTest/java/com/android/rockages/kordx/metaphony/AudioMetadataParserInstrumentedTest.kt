package com.android.rockages.kordx.metaphony

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [AudioMetadataParser] that exercise the native taglib
 * bridge on an Android runtime.
 *
 * These tests open real audio assets from [metaphony/src/main/assets] and verify
 * the parser returns the expected metadata. They are the counterpart to the JVM
 * unit tests in [AudioMetadataParserTest], which only test the pure-data paths.
 */
@RunWith(AndroidJUnit4::class)
class AudioMetadataParserInstrumentedTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun parserLoadsNativeLibrary() {
        val parser = AudioMetadataParser()
        // Native load is triggered lazily by readMetadata; just constructing the
        // parser should not throw.
        assertNotNull(parser)
    }

    @Test
    fun parseMp3AssetExtractsTitleAndArtists() {
        val metadata = parseAsset("audio-id3v2.3.mp3")

        assertNotNull(metadata)
        assertFalse(
            "instrumented parser must read non-empty artists",
            metadata.artists.isEmpty(),
        )
        assertNotNull(metadata.title)
    }

    @Test
    fun parseFlacAssetExtractsAudioProperties() {
        val metadata = parseAsset("audio.flac")

        assertNotNull(metadata)
        assertNotNull("FLAC sample rate must be present", metadata.sampleRate)
        assertNotNull("FLAC bitrate must be present", metadata.bitrate)
        assertTrue(
            "FLAC duration must be positive",
            (metadata.lengthInSeconds ?: 0) > 0,
        )
    }

    @Test
    fun parseOggAssetExtractsMetadata() {
        val metadata = parseAsset("audio.ogg")

        assertNotNull(metadata)
        assertNotNull(metadata.title)
        assertFalse(metadata.artists.isEmpty())
    }

    private fun parseAsset(name: String): AudioMetadata {
        val parser = AudioMetadataParser()
        context.assets.openFd(name).use { fd ->
            val ok = parser.readMetadata(name, fd.parcelFileDescriptor.fd)
            assertTrue("readMetadata must succeed for $name", ok)
        }
        return parser.toMetadata()
    }
}
