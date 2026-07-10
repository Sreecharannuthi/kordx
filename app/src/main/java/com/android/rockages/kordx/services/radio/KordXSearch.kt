package com.android.rockages.kordx.services.radio

import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.core.utils.Fuzzy
import com.android.rockages.kordx.core.utils.FuzzySearchOption
import com.android.rockages.kordx.core.utils.FuzzySearcher

internal object KordXSearch {

 /** Default cap on the result list for non-empty queries (browse + play). */
 const val DEFAULT_LIMIT = 50

 /**
 * Default size of the random sample for the empty-query "surprise me"
 * case. Matches the plan "Empty query (`""` or `null`): return
 * up to 20 random songs".
 */
 const val DEFAULT_RANDOM_SAMPLE_SIZE = 20

 /**
 * Minimum fuzzy-match score for a song to count as a "match" for
 * the plans "Non-empty query with 0 matches: return empty
 * list" contract. The match score comes from
 * `FuzzySearch.tokenSetPartialRatio` (0-100 scale, used by
 * `Fuzzy.compare` in `:core`), which is a *partial* ratio — it
 * returns non-zero values for token-disjoint strings via
 * character-level Levenshtein on substrings. A raw `score > 0`
 * filter therefore returns spurious "matches" for queries like
 * `"xyz"` against unrelated library text.
 *
 * 60 is the conventional fuzzywuzzy "good match" threshold (the
 * `weightedRatio` helper interprets 0-39 as poor / 40-59 as
 * partial / 60-79 as good / 80-99 as strong / 100 as perfect). The
 * "0 matches" intent is user-meaningful — "the user typed
 * something and we found nothing relevant" — and 60 is the
 * boundary that matches that intent without rejecting legitimate
 * fuzzy hits (e.g. "swdish" → "Swedish" still scores in the
 * 70-90 range and clears the threshold).
 */
 const val MIN_MATCH_SCORE = 60

 /**
 * Build the single searchable text for a [song] from the fields the
 * fuzzy matcher scores against: title, album, artists,
 * albumArtists, composers. Empty / null fields are skipped so the
 * text is always non-empty when the song has at least one
 * populated field. Used as the [lookup] body in [search] so the
 * `KordXMediaLibraryService.onSearch` browse path and the
 * `RadioSession.handlePlayFromSearch` play path compose the same
 * searchable text.
 */
 fun songSearchText(song: Song): String = listOfNotNull(
 song.title.takeIf { it.isNotBlank() },
 song.album?.takeIf { it.isNotBlank() },
 song.artists.takeIf { it.isNotEmpty() }?.joinToString(),
 song.albumArtists?.takeIf { it.isNotEmpty() }?.joinToString(),
 song.composers?.takeIf { it.isNotEmpty() }?.joinToString(),
 ).joinToString(" ")

 /**
 * Resolve a search [query] over [songIds] (the live library's song-id
 * list) and return the matching ids in best-match order.
 *
 * - `query` is null / blank / whitespace-only → a random sample of
 * up to [randomSampleSize] ids from [songIds]. The sample is
 * uniformly random per call; the test for the empty-query path
 * asserts only the size + the all-from-input invariant.
 * - `query` is non-empty → fuzzy top-N over [songIds] using the
 * [lookup] callback to retrieve each id's searchable text. The
 * match is `FuzzySearch.tokenSetPartialRatio`-based (the same
 * algorithm `SongRepository.search` uses internally) and the
 * result is filtered to score ≥ [MIN_MATCH_SCORE] so genuinely
 * non-matching ids are dropped before the limit is applied.
 * The plan calls for "0 matches → return empty list (do
 * not crash)"; that is what the [MIN_MATCH_SCORE] filter
 * guarantees.
 *
 * The function never throws and never returns null. Callers can
 * trust the returned list for the `Result.sendResult` IPC and the
 * `RadioShorty.playQueue` input without a defensive null/empty check.
 */
 fun search(
 query: String?,
 songIds: List<String>,
 lookup: (String) -> String,
 limit: Int = DEFAULT_LIMIT,
 randomSampleSize: Int = DEFAULT_RANDOM_SAMPLE_SIZE,
 ): List<String> {
 if (songIds.isEmpty()) return emptyList()
 val trimmed = query?.trim().orEmpty()
 if (trimmed.isEmpty()) {
 return songIds.shuffled().take(randomSampleSize)
 }
 val searcher = FuzzySearcher<String>(
 options = listOf(
 FuzzySearchOption(
 match = { id ->
 lookup(id).takeIf { it.isNotEmpty() }?.let {
 Fuzzy.compare(trimmed, it)
 }
 },
 ),
 ),
 )
 val scored = searcher.search(trimmed, songIds, maxLength = -1)
 val matched = scored.filter { it.score >= MIN_MATCH_SCORE }
 return matched.take(limit).map { it.entity }
 }
}
