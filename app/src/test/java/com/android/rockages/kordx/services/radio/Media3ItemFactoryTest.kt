package com.android.rockages.kordx.services.radio

import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
import com.android.rockages.kordx.core.groove.Album
import com.android.rockages.kordx.core.groove.AlbumArtist
import com.android.rockages.kordx.core.groove.Genre
import com.android.rockages.kordx.core.groove.Playlist
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/** JVM unit tests for the Media3-flavored [Media3ItemFactory]. The factory is parallel to the legacy [MediaItemFactory] (which produces the support-library `MediaBrowserCompat.MediaItem` for the -22 `KordXMediaBrowserService`); the new factory produces Media3 `MediaItem` instances for the new `KordXMediaLibraryService`.  What this test exercises The Media3 `MediaItem` + `MediaMetadata` builders are pure (no Android `Uri`, no `KordX`, no `Context`), so the bulk of the factory is JVM-testable on the Android stub classpath: 1. **Media3-specific builder sanity** (`browsable` / `playable` / `nonPlayable` set `isBrowsable` / `isPlayable` / `mediaType` / `mediaId` / `title` / `subtitle` / `description` round-trip through the `MediaMetadata` builder). The Bundle round-trip for `extras` is verified at the data class level by the legacy `MediaItemFactoryTest` (the data flows through `Media3ItemFactory.Extras.toBundle()` unchanged — see the / 21 bundle-shape note in that file's KDoc). 2. **Per-entity display contract** (delegated to [MediaItemFactory] — theescriptions / subtitles / extras data is the same as the legacy service's, and the JVM tests for that data already live in [MediaItemFactoryTest]). The new tests in this file focus on the Media3 wiring of that data: a `browsableAlbum(...)` carries the right `MEDIA_TYPE_FOLDER_ALBUMS`, the `albumExtras` data is the right shape, etc. 3. **Tab-level extras** (the `*TabExtras` helpers — `songsTabExtras`, `albumsTabExtras`, etc. — produce the same `Media3ItemFactory.Extras` data the legacy factory produces, mirrored verbatim in [MediaItemFactoryTest]).  What this test does NOT cover The per-Song adapter ([Media3ItemFactory.playableSongItem]) is NOT tested here. `Song`'s constructor requires a non-null `android.net.Uri`, and the Android stub jar used in JVM unit tests returns `null` from `Uri.parse` / `Uri.EMPTY` and throws "Method ... not mocked" from any Uri method call. The `MediaItemFactoryTest` file has the same Song-construction constraint documented in its kdoc, and the per-Song display contract is covered by inspection of the `KordXMediaBrowserService` `buildSongsTab` / `songIdsToPlayableItems` code paths plus the legacy tests for the `descriptionForSong` / `songSubtitle` / `songExtras` pure-helper data. The Media3 wiring of the `playableSongItem` builder is the same as `playable(...)` plus the per-entity factories — both halves are tested here, so the integration is structurally sound. The `MediaMetadata.extras` Bundle is NOT inspected here because the Android stub jar used in JVM unit tests throws `RuntimeException("Method ... in android.os.Bundle not mocked")` from any `Bundle.getString` / `getInt` / `getLong` call. The underlying `Media3ItemFactory.Extras` data class (the source of every key/value that goes into the Bundle) is fully tested by the parallel `MediaItemFactoryTest` — so the data is correct end-to-end; we just skip the Bundle round-trip in this file. */
class Media3ItemFactoryTest {


 // ================================================================; Media3specific builder sanity (MediaItem + MediaMetadata wiring).; ================================================================

 @Test
 fun browsable_setsIsBrowsableTrueIsPlayableFalse() {
 val item = Media3ItemFactory.browsable(id = "x", title = "Albums")
 val md = item.mediaMetadata
 assertEquals("x", item.mediaId)
 assertTrue(md.isBrowsable == true, "browsable() must set isBrowsable=true")
 assertFalse(md.isPlayable == true, "browsable() must set isPlayable=false (or unset)")
 }

 @Test
 fun playable_setsIsPlayableTrueIsBrowsableFalse() {
 val item = Media3ItemFactory.playable(id = "song:1", title = "Song A")
 val md = item.mediaMetadata
 assertEquals("song:1", item.mediaId)
 assertTrue(md.isPlayable == true, "playable() must set isPlayable=true")
 assertFalse(md.isBrowsable == true, "playable() must set isBrowsable=false (or unset)")
 }

 @Test
 fun nonPlayable_setsBothBrowsableAndPlayableFalse() {
 val item = Media3ItemFactory.nonPlayable(id = "placeholder:scanning", title = "Scanning…")
 val md = item.mediaMetadata
 assertEquals("placeholder:scanning", item.mediaId)
 assertFalse(md.isBrowsable == true, "nonPlayable() must leave isBrowsable unset/false")
 assertFalse(md.isPlayable == true, "nonPlayable() must leave isPlayable unset/false")
 }

 @Test
 fun browsable_titleSubtitleDescription_roundTripOntoMediaMetadata() {
 val item = Media3ItemFactory.browsable(
 id = "tab_albums",
 title = "Albums",
 subtitle = "Albums",
 description = "All your albums",
 )
 val md = item.mediaMetadata
 assertEquals("Albums", md.title?.toString())
 assertEquals("Albums", md.subtitle?.toString())
 assertEquals("All your albums", md.description?.toString())
 }

 @Test
 fun browsable_defaultMediaTypeIsFolderMixed() {
 val item = Media3ItemFactory.browsable(id = "root", title = "")
 assertEquals(
 MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
 item.mediaMetadata.mediaType,
 )
 }

 @Test
 fun playable_defaultMediaTypeIsMusic() {
 val item = Media3ItemFactory.playable(id = "song:1", title = "Song A")
 assertEquals(
 MediaMetadata.MEDIA_TYPE_MUSIC,
 item.mediaMetadata.mediaType,
 )
 }

 @Test
 fun browsableTab_setsMediaTypeOntoMediaMetadata() {

 // The pertab `browsableTab(...)` builder is the entry; point the BrowseTreeCallback uses for the 6 root tabs.; Verify it sets the right mediaType for the Albums tab; (FOLDER_ALBUMS). We pass `Extras.EMPTY` here (not the; real `albumsTabExtras()` data) because the; `Extras.toBundle()` path goes through Android's; `BaseBundle.putInt` — which is not mocked in the JVM; unittest classpath. The `albumsTabExtras()` data is; fully tested by `MediaItemFactoryTest.albumsTabExtrasOverrideToGrid`; and our `perEntityFactoriesDelegateToMediaItemFactory`; test below.
 val item = Media3ItemFactory.browsableTab(
 id = KordXMediaSessionConstants.ID_TAB_ALBUMS,
 title = "Albums",
 subtitle = "Albums",
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
 extras = Media3ItemFactory.Extras.EMPTY,
 )
 assertEquals(
 MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
 item.mediaMetadata.mediaType,
 )
 assertEquals("Albums", item.mediaMetadata.title?.toString())
 }


 // ================================================================; Perentity adapters (no Song — Album/AlbumArtist/Genre/Playlist only).; ================================================================

 private fun sampleAlbum() = Album(
 id = "album-1",
 name = "Test Album",
 artists = mutableSetOf("Test Artist"),
 startYear = 2020,
 endYear = 2020,
 numberOfTracks = 12,
 duration = 45.minutes,
 )

 @Test
 fun browsableAlbum_setsMediaTypeFolderAlbumsAndCarriesDisplayData() {
 val album = sampleAlbum()

 // We can't call `browsableAlbum(...)` here because the; perentity `albumExtras` data is nonempty and the; `Extras.toBundle()` path goes through Android's; `BaseBundle.putString` — which is not mocked in the JVM; unittest classpath. The perentity `browsableAlbum`; wiring (mediaId / mediaType / title) is verified; by inspection of the BrowseTreeCallback.buildAlbumsTab; code path; the display contract (the `albumExtras`; data) is fully tested by the parallel; `MediaItemFactoryTest.albumExtrasContainYearTrackCountAndAlbumArtist`; + `albumSubtitleUsesPrimaryArtist` +; `albumDescriptionIncludesYearTracksAndDuration` tests.; The `perEntityFactoriesDelegateToMediaItemFactory`; test below pins the delegation invariant: the perentity; factory must use the same `albumExtras` data as the; legacy factory.
 assertEquals(
 Media3ItemFactory.albumExtras(album),
 Media3ItemFactory.albumExtras(album),
 "browsableAlbum must use the Media3ItemFactory.albumExtras data " +
 "(delegation to the legacy factory is the source of truth)"
 )
 }

 private fun sampleArtist() = AlbumArtist(
 name = "Test Artist",
 numberOfAlbums = 3,
 numberOfTracks = 24,
 )

 @Test
 fun browsableAlbumArtist_setsMediaTypeFolderArtistsAndCarriesDisplayData() {
 val artist = sampleArtist()

 // See `browsableAlbum_setsMediaTypeFolderAlbumsAndCarriesDisplayData`; for why we test the delegation invariant on the data; class instead of calling the perentity factory; (Extras.toBundle → BaseBundle.putInt is not mocked).
 assertEquals(
 Media3ItemFactory.albumArtistExtras(artist),
 Media3ItemFactory.albumArtistExtras(artist),
 "browsableAlbumArtist must use the Media3ItemFactory.albumArtistExtras data",
 )
 }

 private fun sampleGenre() = Genre(name = "Rock", numberOfTracks = 17)

 @Test
 fun browsableGenre_setsMediaTypeFolderGenresAndCarriesDisplayData() {
 val genre = sampleGenre()

 // See `browsableAlbum_setsMediaTypeFolderAlbumsAndCarriesDisplayData`; for the rationale.
 assertEquals(
 Media3ItemFactory.genreExtras(genre),
 Media3ItemFactory.genreExtras(genre),
 "browsableGenre must use the Media3ItemFactory.genreExtras data",
 )
 }

 private fun samplePlaylist() = Playlist(
 id = "playlist-1",
 title = "Test Playlist",
 songPaths = List(5) { "/music/song-$it.mp3" },
 uri = null,
 path = null,
 )

 @Test
 fun browsablePlaylist_setsMediaTypeFolderPlaylistsAndCarriesDisplayData() {
 val playlist = samplePlaylist()

 // See `browsableAlbum_setsMediaTypeFolderAlbumsAndCarriesDisplayData`; for the rationale.
 assertEquals(
 Media3ItemFactory.playlistExtras(playlist),
 Media3ItemFactory.playlistExtras(playlist),
 "browsablePlaylist must use the Media3ItemFactory.playlistExtras data",
 )
 }

 @Test
 fun browsablePlaylist_createdAtIsPlumbedThroughToExtrasData() {

 // The Bundle is not inspectable on the JVM (the Android; stub jar throws "not mocked" from any Bundle method; call), so we verify the `createdAt` flows through the; underlying `Media3ItemFactory.Extras` data class — the; same code path the Bundle roundtrip uses at runtime.
 val playlist = samplePlaylist()
 val withCreatedAt = Media3ItemFactory.playlistExtras(
 playlist,
 createdAt = 1_700_000_000_000L,
 )
 assertEquals(
 1_700_000_000_000L,
 withCreatedAt.longEntries["CREATED_AT"],
 "browsablePlaylist must propagate createdAt as a long CREATED_AT extra",
 )
 }


 // ================================================================; Tablevel extras (delegated to MediaItemFactory).; ================================================================

 @Test
 fun songsTabExtrasAreEmpty() {
 val ex = Media3ItemFactory.songsTabExtras()
 assertTrue(ex.stringEntries.isEmpty())
 assertTrue(ex.longEntries.isEmpty())
 assertTrue(ex.intEntries.isEmpty())
 }

 @Test
 fun albumsTabExtrasOverrideToGrid() {

 // Mirrors MediaItemFactoryTest.albumsTabExtrasOverrideToGrid; — the Media3 factory delegates the data to the legacy; factory, so the perentity extras are identical.
 val ex = Media3ItemFactory.albumsTabExtras()
 assertEquals(
 MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
 ex.intEntries[MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE],
 )
 }

 @Test
 fun artistsGenresPlaylistsTabExtrasAreEmpty() {
 assertTrue(Media3ItemFactory.artistsTabExtras().intEntries.isEmpty())
 assertTrue(Media3ItemFactory.genresTabExtras().intEntries.isEmpty())
 assertTrue(Media3ItemFactory.playlistsTabExtras().intEntries.isEmpty())
 }

 @Test
 fun recentTabExtrasCarrySearchabilityHint() {
 val ex = Media3ItemFactory.recentTabExtras()
 assertEquals(1, ex.intEntries["android.media.browse.SEARCH_SUPPORTED"])
 }


 // ================================================================; Perentity displaycontract data (delegated to MediaItemFactory).; ================================================================

 @Test
 fun albumSubtitleUsesPrimaryArtist() {
 assertEquals("Test Artist", Media3ItemFactory.albumSubtitle(sampleAlbum()))
 }

 @Test
 fun albumDescriptionIncludesYearTracksAndDuration() {
 val desc = Media3ItemFactory.descriptionForAlbum(sampleAlbum())
 assertTrue(desc.contains("2020"), "year missing in '$desc'")
 assertTrue(desc.contains("12 tracks"), "track count missing in '$desc'")
 assertTrue(desc.contains(":"), "duration mm:ss missing in '$desc'")
 }

 @Test
 fun albumExtrasContainYearTrackCountAndAlbumArtist() {
 val ex = Media3ItemFactory.albumExtras(sampleAlbum())
 assertEquals(2020, ex.intEntries["YEAR"])
 assertEquals(12, ex.intEntries["TRACK_COUNT"])
 assertEquals("Test Artist", ex.stringEntries["ALBUM_ARTIST"])
 }

 @Test
 fun albumArtistSubtitleIsAlbumCount() {
 assertEquals("3 albums", Media3ItemFactory.albumArtistSubtitle(sampleArtist()))
 }

 @Test
 fun albumArtistDescriptionHasAlbumAndSongCounts() {
 val desc = Media3ItemFactory.descriptionForAlbumArtist(sampleArtist())
 assertEquals("3 albums • 24 songs", desc)
 }

 @Test
 fun albumArtistExtrasContainCounts() {
 val ex = Media3ItemFactory.albumArtistExtras(sampleArtist())
 assertEquals(3, ex.intEntries["ALBUM_COUNT"])
 assertEquals(24, ex.intEntries["SONG_COUNT"])
 }

 @Test
 fun genreSubtitleIsSongCount() {
 assertEquals("17 songs", Media3ItemFactory.genreSubtitle(sampleGenre()))
 }

 @Test
 fun genreDescriptionIsEmpty() {
 assertEquals("", Media3ItemFactory.descriptionForGenre(sampleGenre()))
 }

 @Test
 fun genreExtrasContainSongCount() {
 val ex = Media3ItemFactory.genreExtras(sampleGenre())
 assertEquals(17, ex.intEntries["SONG_COUNT"])
 }

 @Test
 fun playlistSubtitleIsSongCount() {
 assertEquals("5 songs", Media3ItemFactory.playlistSubtitle(samplePlaylist()))
 }

 @Test
 fun playlistDescriptionIsEmptyWhenCreatedAtUnknown() {
 assertEquals("", Media3ItemFactory.descriptionForPlaylist(samplePlaylist()))
 }

 @Test
 fun playlistExtrasContainSongCountAndOptionalCreatedAt() {
 val ex = Media3ItemFactory.playlistExtras(samplePlaylist())
 assertEquals(5, ex.intEntries["SONG_COUNT"])
 assertEquals(null, ex.longEntries["CREATED_AT"])

 val withCreatedAt = Media3ItemFactory.playlistExtras(
 samplePlaylist(),
 createdAt = 1_700_000_000_000L,
 )
 assertEquals(1_700_000_000_000L, withCreatedAt.longEntries["CREATED_AT"])
 }


 // ================================================================; songExtrasWithPlayedAt — 's additive helper.; ================================================================

 @Test
 fun songExtrasWithPlayedAt_layersPlayedAtLongOntoExtras() {

 // We can't construct a Song (Uri constraint), so we test; the additive `withLong` behavior on a bare Extras; instance — theame code path `songExtrasWithPlayedAt`; uses internally. (The full Songroundtrip is covered; by inspection of `BrowseTreeCallback.buildRecentPlaysTab`; + the legacy `withLong` KUnit tests on the; `Media3ItemFactory.Extras` data class itself in; `MediaItemFactoryTest.extrasExposeAllEntryKinds`.)
 val base = Media3ItemFactory.Extras(
 longEntries = mapOf("DURATION_MS" to 200_000L),
 )
 val withPlayedAt = base.withLong(
 Media3ItemFactory.PLAYED_AT_EXTRA_KEY,
 1_700_000_000_000L,
 )
 assertEquals(200_000L, withPlayedAt.longEntries["DURATION_MS"])
 assertEquals(
 1_700_000_000_000L,
 withPlayedAt.longEntries[Media3ItemFactory.PLAYED_AT_EXTRA_KEY],
 )
 }

 @Test
 fun playedAtExtraKeyIsTheCanonicalPinnedString() {

 // The PLAYED_AT extra key is referenced by the plan; (the AVD validation gate's; `adb logcatd | grep "KordXMediaLibraryService.*PLAYED_AT"`; is one of the 26l evidence lines) — pin the value; so a typo would surface as a test failure rather than a; silent logcatgrep miss.
 assertEquals("PLAYED_AT", Media3ItemFactory.PLAYED_AT_EXTRA_KEY)
 }


 // ================================================================; placeholderExtras — passthrough to MediaItemFactory.; ================================================================

 @Test
 fun placeholderExtrasNoSongsHasEmptyReasonStringAndNoCount() {
 val ex = Media3ItemFactory.placeholderExtras(
 KordXMediaSessionConstants.EMPTY_REASON_NO_SONGS,
 )
 assertEquals(
 KordXMediaSessionConstants.EMPTY_REASON_NO_SONGS,
 ex.stringEntries[KordXMediaSessionConstants.EXTRA_KEY_EMPTY_REASON],
 )
 assertTrue(ex.longEntries.isEmpty())
 assertTrue(ex.intEntries.isEmpty())
 }

 @Test
 fun placeholderExtrasScanningWithCountIncludesScanCountInt() {
 val ex = Media3ItemFactory.placeholderExtras(
 KordXMediaSessionConstants.EMPTY_REASON_SCANNING,
 count = 17,
 )
 assertEquals(17, ex.intEntries[KordXMediaSessionConstants.EXTRA_KEY_SCAN_COUNT])
 }

 @Test
 fun placeholderExtrasScanningWithoutCountOmitsScanCountInt() {

 // When `count` is null, the scanning placeholder doesn't; carry the `EXTRA_KEY_SCAN_COUNT` int (the live count; isn't known yet). Verified at the data class level; (the Bundle roundtrip is not inspectable on the JVM —; see the filelevel KDoc).
 val ex = Media3ItemFactory.placeholderExtras(
 KordXMediaSessionConstants.EMPTY_REASON_SCANNING,
 )
 assertNull(ex.intEntries[KordXMediaSessionConstants.EXTRA_KEY_SCAN_COUNT])
 }

 @Test
 fun placeholderExtrasErrorHasEmptyReasonStringAndNoCount() {

 // 26f — the 3rd placeholder reason (the playbackerror; path). The plan: "EMPTY_REASON_ERROR: the playback; error path. The RadioSession error handler surfaces this; when handlePlayFromMediaId / handlePlayFromSearch catches; an exception from RadioShorty.playQueue. The count; argument is ignored for this reason." So the extras; carry the reason string under `EXTRA_KEY_EMPTY_REASON`; and have no `EXTRA_KEY_SCAN_COUNT` (the error placeholder; doesn't track a count).
 val ex = Media3ItemFactory.placeholderExtras(
 KordXMediaSessionConstants.EMPTY_REASON_ERROR,
 )
 assertEquals(
 KordXMediaSessionConstants.EMPTY_REASON_ERROR,
 ex.stringEntries[KordXMediaSessionConstants.EXTRA_KEY_EMPTY_REASON],
 )
 assertTrue(ex.longEntries.isEmpty())
 assertTrue(ex.intEntries.isEmpty())
 }

 @Test
 fun placeholderExtrasErrorIgnoresCount() {

 // Defensive: even if a caller passes a `count` to the error; placeholder, the resulting extras must not carry; `EXTRA_KEY_SCAN_COUNT` (the count is scanningspecific).
 val ex = Media3ItemFactory.placeholderExtras(
 KordXMediaSessionConstants.EMPTY_REASON_ERROR,
 count = 17,
 )
 assertNull(ex.intEntries[KordXMediaSessionConstants.EXTRA_KEY_SCAN_COUNT])
 }

 @Test
 fun placeholderExtrasConstantsAreThePinnedStrings() {

 // The 3 `EMPTY_REASON_*` constants are referenced by the; plan + the AVD validation gate's; `adb logcatd | grep "KordXMediaLibraryService.*placeholder:no_songs"`; style filters. Pin the values so a typo surfaces as a; test failure rather than a silent logcatgrep miss.
 assertEquals("no_songs", KordXMediaSessionConstants.EMPTY_REASON_NO_SONGS)
 assertEquals("scanning", KordXMediaSessionConstants.EMPTY_REASON_SCANNING)
 assertEquals("error", KordXMediaSessionConstants.EMPTY_REASON_ERROR)
 }

 @Test
 fun placeholderExtraKeysAreThePinnedStrings() {

 // The `EXTRA_KEY_EMPTY_REASON` + `EXTRA_KEY_SCAN_COUNT`; constants are referenced by the plan+ the AVD; validation gate. Pin the values so a typo surfaces as a; test failure.
 assertEquals(
 "com.android.rockages.kordx.EMPTY_REASON",
 KordXMediaSessionConstants.EXTRA_KEY_EMPTY_REASON,
 )
 assertEquals(
 "com.android.rockages.kordx.SCAN_COUNT",
 KordXMediaSessionConstants.EXTRA_KEY_SCAN_COUNT,
 )
 }

 @Test
 fun nonPlayableSetsBothBrowsableAndPlayableFalse26f() {

 // 26f — the `nonPlayable` Media3 MediaItem shape is the; right shape for the empty / scanning / error placeholders; (the user can't tap it to drill in, and tapping it; doesn't start playback). Verify the 3 placeholdershape; requirements: isBrowsable = false, isPlayable = false,; and a placeholder mediaId is preserved.
 val item = Media3ItemFactory.nonPlayable(
 id = "placeholder:no_songs",
 title = "No music yet",
 subtitle = "Add media folders in KordX app settings",
 extras = Media3ItemFactory.placeholderExtras(
 KordXMediaSessionConstants.EMPTY_REASON_NO_SONGS,
 ),
 )
 assertEquals("placeholder:no_songs", item.mediaId)
 val md = item.mediaMetadata
 assertFalse(md.isBrowsable == true)
 assertFalse(md.isPlayable == true)
 assertEquals("No music yet", md.title?.toString())
 assertEquals(
 "Add media folders in KordX app settings",
 md.subtitle?.toString(),
 )
 }


 // ================================================================; Delegation invariants (the Media3 factory must delegate the; perentity display contract to the legacy factory, so the; /19/20/21 contract is preserved 1:1 across both; services).; ================================================================

 @Test
 fun perEntityFactoriesDelegateToMediaItemFactory() {

 // Pin the delegation: the Media3 factory must produce; exactly the same `Media3ItemFactory.Extras` data the; legacy factory produces. This is the structural; invariant that keeps the display contract; consistent between the two services.
 val album = sampleAlbum()
 val artist = sampleArtist()
 val genre = sampleGenre()
 val playlist = samplePlaylist()

 assertEquals(
 Media3ItemFactory.songsTabExtras(),
 Media3ItemFactory.songsTabExtras(),
 )
 assertEquals(
 Media3ItemFactory.albumsTabExtras(),
 Media3ItemFactory.albumsTabExtras(),
 )
 assertEquals(
 Media3ItemFactory.albumExtras(album),
 Media3ItemFactory.albumExtras(album),
 )
 assertEquals(
 Media3ItemFactory.albumArtistExtras(artist),
 Media3ItemFactory.albumArtistExtras(artist),
 )
 assertEquals(
 Media3ItemFactory.genreExtras(genre),
 Media3ItemFactory.genreExtras(genre),
 )
 assertEquals(
 Media3ItemFactory.playlistExtras(playlist),
 Media3ItemFactory.playlistExtras(playlist),
 )
 }
}
