package com.android.rockages.kordx.metaphony

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM unit tests for [AudioMetadataParser] pure-data paths. The native
 * taglib bridge is exercised by instrumented tests on the AVD. */
class AudioMetadataParserTest {

 private fun parser(
 tags: Map<String, List<String>> = emptyMap(),
 pictures: List<AudioMetadata.Picture> = emptyList(),
 audioProperties: Map<String, Int> = emptyMap(),
 ): AudioMetadata {
 val p = AudioMetadataParser()
 for ((key, values) in tags) {
 for (value in values) {
 p.putTag(key, value)
 }
 }
 for (picture in pictures) {
 p.putPicture(picture.pictureType, picture.mimeType, picture.data)
 }
 for ((key, value) in audioProperties) {
 p.putAudioProperty(key, value)
 }
 return p.toMetadata()
 }

 @Test
 fun dateExtractedFromDateTag() {
 val metadata = parser(
 tags = mapOf("DATE" to listOf("2021-03-15"))
 )
 assertNotNull(metadata.date)
 assertEquals(2021, metadata.date!!.year)
 assertEquals(3, metadata.date!!.monthValue)
 assertEquals(15, metadata.date!!.dayOfMonth)
 }

 @Test
 fun dateExtractedFromYearTagWhenDateMissing() {
 // Falls back to `YEAR` when `DATE` is missing; uses Jan 1 placeholder.
 val metadata = parser(
 tags = mapOf("YEAR" to listOf("2021"))
 )
 assertNotNull(metadata.date)
 assertEquals(2021, metadata.date!!.year)
 assertEquals(1, metadata.date!!.monthValue)
 assertEquals(1, metadata.date!!.dayOfMonth)
 }

 @Test
 fun datePrefersDateOverYear() {

 // When both `DATE` and `YEAR` are present, `DATE` wins (it; has more specificity — month and day, not just year).
 val metadata = parser(
 tags = mapOf(
 "DATE" to listOf("2021-03-15"),
 "YEAR" to listOf("1999"),
 )
 )
 assertNotNull(metadata.date)
 assertEquals(2021, metadata.date!!.year)
 assertEquals(3, metadata.date!!.monthValue)
 assertEquals(15, metadata.date!!.dayOfMonth)
 }

 @Test
 fun dateIsNullWhenNoRelevantTag() {
 val metadata = parser(
 tags = mapOf("TITLE" to listOf("Test"))
 )
 assertNull(metadata.date)
 }

 @Test
 fun dateFallsBackToOriginalDate() {

 // `ORIGINALDATE` is the originalrelease date (e.g. for; rereleases). Taglib maps the `TDOR` (ID3v2.4) and `TORY`; (ID3v2.3) frames to this key.
 val metadata = parser(
 tags = mapOf("ORIGINALDATE" to listOf("1995-06-20"))
 )
 assertNotNull(metadata.date)
 assertEquals(1995, metadata.date!!.year)
 }

 @Test
 fun dateFallsBackToOriginalYear() {
 val metadata = parser(
 tags = mapOf("ORIGINALYEAR" to listOf("1995"))
 )
 assertNotNull(metadata.date)
 assertEquals(1995, metadata.date!!.year)
 assertEquals(1, metadata.date!!.monthValue)
 }

 @Test
 fun slashedTrackNumberSplitsCorrectly() {
 // Regression guard: `"3/12"` → `trackNumber = 3`, `trackTotal = 12`.
 val metadata = parser(
 tags = mapOf(
 "TRACKNUMBER" to listOf("3/12"),
 "ARTIST" to listOf("Test Artist"),
 )
 )
 assertEquals(3, metadata.trackNumber)
 assertEquals(12, metadata.trackTotal)
 }

 @Test
 fun singleTrackNumberParses() {

 // `"7"` (no slash) should produce `trackNumber = 7`,; `trackTotal = null` (no total in the tag).
 val metadata = parser(
 tags = mapOf("TRACKNUMBER" to listOf("7"))
 )
 assertEquals(7, metadata.trackNumber)
 assertNull(metadata.trackTotal)
 }

 @Test
 fun multipleArtistsAreKeptAsSet() {

 // taglib returns one value per artist (when the file has; multiple `ARTIST` frames); the parser collects them into; a `Set<String>`.
 val metadata = parser(
 tags = mapOf(
 "ARTIST" to listOf("First Artist", "Second Artist", "First Artist"),
 )
 )
 assertEquals(2, metadata.artists.size)
 assertTrue("First Artist" in metadata.artists)
 assertTrue("Second Artist" in metadata.artists)
 }

 @Test
 fun lyricsAreExtracted() {
 // taglib normalizes all embedded-lyrics frames to the `LYRICS` key.
 val metadata = parser(
 tags = mapOf("LYRICS" to listOf("Line 1\nLine 2"))
 )
 assertNotNull(metadata.lyrics)
 assertEquals("Line 1\nLine 2", metadata.lyrics)
 }
}
