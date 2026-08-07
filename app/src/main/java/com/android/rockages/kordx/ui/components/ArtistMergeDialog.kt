package com.android.rockages.kordx.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.android.rockages.kordx.ui.helpers.ViewContext

/**
 * Dialog that lets the user pick another artist to merge into.
 *
 * The current source artist is excluded from the searchable list.
 * At the top, the dialog surfaces auto-detected merge suggestions:
 * artists whose names contain the source name as a whole-word prefix
 * or suffix (e.g. "Tanvi" → "Tanvi Shah"). Tapping a suggestion
 * merges the source into that target.
 */
@Composable
fun ArtistMergeDialog(
    context: ViewContext,
    sourceName: String,
    onDismissRequest: () -> Unit,
) {
    val allNames by context.kordx.groove.artist.all.collectAsState()
    val imageRequest: (String) -> ImageRequest = { target ->
        context.kordx.groove.artist.createArtworkImageRequest(target).build()
    }
    val onMerge: (String) -> Unit = { target ->
        context.kordx.groove.artist.merge(sourceName, target)
    }

    MergeTargetPickerDialog(
        context = context,
        sourceName = sourceName,
        allNames = allNames,
        imageRequest = imageRequest,
        onMerge = onMerge,
        onDismissRequest = onDismissRequest,
    )
}

/**
 * Album-artist variant of [ArtistMergeDialog].
 *
 * Uses the same prefix/suffix suggestion heuristic and persistence model,
 * but operates on the album-artist repository so album-artist profiles can
 * be merged independently of artist profiles.
 */
@Composable
fun AlbumArtistMergeDialog(
    context: ViewContext,
    sourceName: String,
    onDismissRequest: () -> Unit,
) {
    val allNames by context.kordx.groove.albumArtist.all.collectAsState()
    val imageRequest: (String) -> ImageRequest = { target ->
        context.kordx.groove.albumArtist.createArtworkImageRequest(target).build()
    }
    val onMerge: (String) -> Unit = { target ->
        context.kordx.groove.albumArtist.merge(sourceName, target)
    }

    MergeTargetPickerDialog(
        context = context,
        sourceName = sourceName,
        allNames = allNames,
        imageRequest = imageRequest,
        onMerge = onMerge,
        onDismissRequest = onDismissRequest,
    )
}

/**
 * Generic merge-target picker used by both [ArtistMergeDialog] and
 * [AlbumArtistMergeDialog].
 *
 * @param allNames Complete list of candidate entity display names.
 * @param imageRequest Produces a Coil [ImageRequest] for a candidate name.
 * @param onMerge Called with the selected target name when confirmed.
 */
@Composable
private fun MergeTargetPickerDialog(
    context: ViewContext,
    sourceName: String,
    allNames: List<String>,
    imageRequest: (String) -> ImageRequest,
    onMerge: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var pendingTarget by remember { mutableStateOf<String?>(null) }

    val candidates by remember(allNames) {
        derivedStateOf { allNames.filter { it != sourceName } }
    }
    val suggestions by remember(candidates, sourceName) {
        derivedStateOf { computeMergeSuggestions(sourceName, candidates) }
    }
    val filtered by remember(query, candidates) {
        derivedStateOf {
            val q = query.trim().lowercase()
            if (q.isEmpty()) {
                candidates
            } else {
                candidates.filter { it.lowercase().contains(q) }
            }
        }
    }

    pendingTarget?.let { target ->
        ConfirmationDialog(
            context = context,
            title = { Text(context.kordx.t.MergeArtists) },
            description = {
                Text(
                    context.kordx.t.MergeArtistsConfirmation
                        .replace("{source}", sourceName)
                        .replace("{target}", target)
                )
            },
            onResult = { confirmed ->
                if (confirmed) {
                    onMerge(target)
                }
                pendingTarget = null
                onDismissRequest()
            },
        )
        return
    }

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(context.kordx.t.MergeWith) },
        content = {
            Column {
                if (suggestions.isNotEmpty()) {
                    Text(
                        text = context.kordx.t.SuggestedMerges,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    suggestions.forEach { target ->
                        GenericGrooveCard(
                            image = imageRequest(target),
                            title = { Text(target) },
                            subtitle = {
                                Text(
                                    "Merge \"$sourceName\" into \"$target\"",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            options = { _, _ -> },
                            onClick = { pendingTarget = target },
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                }

                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(context.kordx.t.SearchYourMusic) },
                    singleLine = true,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = TextFieldDefaults.colors(),
                )
                LazyColumn(modifier = Modifier.padding(bottom = 4.dp)) {
                    items(filtered) { target ->
                        GenericGrooveCard(
                            image = imageRequest(target),
                            title = { Text(target) },
                            options = { _, _ -> },
                            onClick = { pendingTarget = target },
                        )
                    }
                }
            }
        },
        actions = {
            TextButton(onClick = onDismissRequest) {
                Text(context.kordx.t.Cancel)
            }
        },
    )
}

/**
 * Returns names that are plausible merge targets for [sourceName].
 * A target qualifies when its name contains [sourceName] as a whole-word
 * prefix or suffix and is strictly longer (e.g. "Tanvi" → "Tanvi Shah").
 */
internal fun computeMergeSuggestions(
    sourceName: String,
    names: List<String>,
): List<String> {
    val source = sourceName.trim().lowercase()
    if (source.length < 3) return emptyList()
    return names.filter { candidate ->
        val name = candidate.trim().lowercase()
        name.length > source.length && (
            name.startsWith("$source ") || name.endsWith(" $source")
        )
    }
}
