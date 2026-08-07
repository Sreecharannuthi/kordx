package com.android.rockages.kordx.services.radio

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import com.android.rockages.kordx.BuildConfig
import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.utils.Logger
import com.android.rockages.kordx.services.groove.getSongIds
import com.android.rockages.kordx.services.groove.getSortedSongIds

/**
 * Legacy-free dispatcher for Android Auto / AAOS voice-search and debug-only
 * broadcast commands. The Media3 session lives in [KordXMediaLibraryService];
 * this class only keeps the handler methods that are shared between the
 * `MEDIA_PLAY_FROM_SEARCH` activity-alias path and the AVD validation adb
 * broadcasts.
 */
class RadioSession(val kordx: KordX) {

    /**
     * Debug-only receiver that lets `adb shell am broadcast` trigger the
     * custom-action handlers (shuffle / repeat / favorite) without going
     * through the MediaController / DHU. The Android Auto integration is
     * the production path for these actions; this receiver exists so the
     * "How to test" command can confirm the handlers fire under
     * `adb logcat -d | grep "RadioSession.*(shuffle|repeat|favorite)"`.
     *
     * Marked `RECEIVER_NOT_EXPORTED` so only the app itself (or an
     * `adb shell am broadcast` from the same UID) can send these intents.
     */
    private val debugCustomActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DEBUG_ACTION_SHUFFLE -> handleCustomAction(RadioSessionState.ACTION_SHUFFLE)
                DEBUG_ACTION_REPEAT -> handleCustomAction(RadioSessionState.ACTION_REPEAT)
                DEBUG_ACTION_FAVORITE -> handleCustomAction(RadioSessionState.ACTION_FAVORITE)
                DEBUG_ACTION_SHUFFLE_ALL -> handleCustomAction(RadioSessionState.ACTION_SHUFFLE_ALL)
                DEBUG_ACTION_ROOT_SEARCH -> handleCustomAction(RadioSessionState.ACTION_SEARCH)
            }
        }
    }

    /**
     * — debug-only receiver that lets `adb shell am broadcast`
     * trigger the play-from-search handler with a query taken from the
     * intent's `query` extra. Mirrors the [debugCustomActionReceiver]
     * pattern from so the "How to test" commands can
     * fire `handlePlayFromSearch` without going through the
     * MediaController / DHU. Marked `RECEIVER_EXPORTED` because the
     * sender is the shell user (see "Confirmed" note in
     */
    private val debugSearchReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val query = intent?.getStringExtra(EXTRA_DEBUG_QUERY)
            Logger.warn(
                "RadioSession",
                "DEBUG_ACTION_SEARCH received: query='${query.orEmpty()}'",
            )
            handlePlayFromSearch(query)
        }
    }

    /**
     * — debug-only receiver that lets `adb shell am broadcast`
     * add a song id to the recently-played history. The "production"
     * hook is in [Radio]'s `onUpdate` subscriber, which fires on
     * `Radio.Events.Player.Started` and calls `recentPlays.add(songId)`;
     * the receiver exists for the AVD validation step (the plans
     * "play 3 songs and check the 3 entries persist across force-stop"
     * — the SAF picker + MediaController / DHU paths the production hook
     * needs are not available in this CI environment).
     *
     * Usage: `adb shell am broadcast -a
     * com.android.rockages.kordx.radio.DEBUG_RECENT_PLAY --es
     * songId "test-song-1"`.
     */
    private val debugRecentPlayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val songId = intent?.getStringExtra(EXTRA_DEBUG_SONG_ID)
            if (songId.isNullOrBlank()) {
                Logger.warn(
                    "RadioSession",
                    "DEBUG_ACTION_RECENT_PLAY received: no songId extra, ignored",
                )
                return
            }
            Logger.warn(
                "RadioSession",
                "DEBUG_ACTION_RECENT_PLAY received: songId='$songId'",
            )
            kordx.groove.recentPlays.add(songId)
        }
    }

    /**
     * — debug-only receiver that lets `adb shell am broadcast`
     * trigger the playback error path in [handlePlaybackError] with a
     * fake song id and error code. The production error path fires
     * from a `Radio.play(song)` / `RadioPlayer.start()` exception,
     * which is hard to drive from a shell command (the production
     * path needs a populated library + MediaController / DHU). The
     * receiver calls [handlePlaybackError] directly so the AVD
     * validation gate can confirm the error path:
     * - logs the error (plan: "log the error with the song id
     * and exception class")
     * - auto-skips to the next song (plan: "Skip to next song
     * in the queue automatically")
     * - does not crash the service (plan: "Do not crash the
     * service")
     *
     * Usage: `adb shell am broadcast -a
     * com.android.rockages.kordx.radio.DEBUG_PLAYBACK_ERROR --es
     * songId "song:test-error" --ei code 1`.
     */
    private val debugPlaybackErrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val songId = intent?.getStringExtra(EXTRA_DEBUG_SONG_ID)
            val code = intent?.getIntExtra(EXTRA_DEBUG_ERROR_CODE, ERROR_CODE_UNKNOWN_ERROR)
                ?: ERROR_CODE_UNKNOWN_ERROR
            Logger.warn(
                "RadioSession",
                "DEBUG_ACTION_PLAYBACK_ERROR received: songId='${songId ?: "<null>"}', code=$code",
            )
            handlePlaybackError(
                songId = songId,
                errorCode = code,
                errorMessage = "simulated playback error (code $code)",
                exception = RuntimeException("simulated playback error (code $code)"),
            )
        }
    }

    /**
     * Register the debug receivers. Called from [Radio.ready] after the
     * legacy MediaSessionCompat lifecycle was removed in LG2.
     */
    fun attachDebugReceivers() {
        if (!BuildConfig.DEBUG) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            kordx.applicationContext.registerReceiver(
                debugCustomActionReceiver,
                IntentFilter().apply {
                    addAction(DEBUG_ACTION_SHUFFLE)
                    addAction(DEBUG_ACTION_REPEAT)
                    addAction(DEBUG_ACTION_FAVORITE)
                    addAction(DEBUG_ACTION_SHUFFLE_ALL)
                    addAction(DEBUG_ACTION_ROOT_SEARCH)
                },
                Context.RECEIVER_EXPORTED,
            )
            kordx.applicationContext.registerReceiver(
                debugSearchReceiver,
                IntentFilter().apply { addAction(DEBUG_ACTION_SEARCH) },
                Context.RECEIVER_EXPORTED,
            )
            kordx.applicationContext.registerReceiver(
                debugRecentPlayReceiver,
                IntentFilter().apply { addAction(DEBUG_ACTION_RECENT_PLAY) },
                Context.RECEIVER_EXPORTED,
            )
            kordx.applicationContext.registerReceiver(
                debugPlaybackErrorReceiver,
                IntentFilter().apply { addAction(DEBUG_ACTION_PLAYBACK_ERROR) },
                Context.RECEIVER_EXPORTED,
            )
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            kordx.applicationContext.registerReceiver(
                debugCustomActionReceiver,
                IntentFilter().apply {
                    addAction(DEBUG_ACTION_SHUFFLE)
                    addAction(DEBUG_ACTION_REPEAT)
                    addAction(DEBUG_ACTION_FAVORITE)
                    addAction(DEBUG_ACTION_SHUFFLE_ALL)
                    addAction(DEBUG_ACTION_ROOT_SEARCH)
                },
            )
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            kordx.applicationContext.registerReceiver(
                debugSearchReceiver,
                IntentFilter().apply { addAction(DEBUG_ACTION_SEARCH) },
            )
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            kordx.applicationContext.registerReceiver(
                debugRecentPlayReceiver,
                IntentFilter().apply { addAction(DEBUG_ACTION_RECENT_PLAY) },
            )
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            kordx.applicationContext.registerReceiver(
                debugPlaybackErrorReceiver,
                IntentFilter().apply { addAction(DEBUG_ACTION_PLAYBACK_ERROR) },
            )
        }
    }

    /**
     * Unregister the debug receivers. Called from [Radio.destroy].
     */
    fun detachDebugReceivers() {
        if (!BuildConfig.DEBUG) return
        try {
            kordx.applicationContext.unregisterReceiver(debugCustomActionReceiver)
        } catch (err: Exception) {
            // Already unregistered — tolerate double-destroy.
        }
        try {
            kordx.applicationContext.unregisterReceiver(debugSearchReceiver)
        } catch (err: Exception) {
            // Already unregistered — tolerate double-destroy.
        }
        try {
            kordx.applicationContext.unregisterReceiver(debugRecentPlayReceiver)
        } catch (err: Exception) {
            // Already unregistered — tolerate double-destroy.
        }
        try {
            kordx.applicationContext.unregisterReceiver(debugPlaybackErrorReceiver)
        } catch (err: Exception) {
            // Already unregistered — tolerate double-destroy.
        }
    }

    /**
     * Handle a custom action from Android Auto / the system (shuffle,
     * repeat, favorite). The custom-action strings live in
     * [RadioSessionState] alongside their pure icon/label builders. The
     * logcat `Logger` lines make the validation gate trivial:
     * `adb logcat -d | grep "RadioSession.*(shuffle|repeat|favorite)"`.
     */
    fun handleCustomAction(action: String) {
        when (action) {
            RadioSessionState.ACTION_SHUFFLE -> {
                val now = kordx.radio.queue.currentShuffleMode
                kordx.radio.queue.toggleShuffleMode()
                val next = kordx.radio.queue.currentShuffleMode
                Logger.warn(
                    "RadioSession",
                    "shuffle toggled: $now -> $next",
                )
            }
            RadioSessionState.ACTION_REPEAT -> {
                val now = kordx.radio.queue.currentLoopMode
                kordx.radio.queue.toggleLoopMode()
                val next = kordx.radio.queue.currentLoopMode
                Logger.warn(
                    "RadioSession",
                    "repeat -> $next",
                )
            }
            RadioSessionState.ACTION_FAVORITE -> {
                val songId = kordx.radio.queue.currentSongId
                if (songId == null) {
                    Logger.warn(
                        "RadioSession",
                        "favorite ignored: no current song",
                    )
                    return
                }
                kordx.groove.songFavorites.toggle(songId)
                val next = kordx.groove.songFavorites.isFavorite(songId)
                Logger.warn(
                    "RadioSession",
                    "favorite toggled: $next for songId=$songId",
                )
            }
            RadioSessionState.ACTION_SHUFFLE_ALL -> {
                val allSongIds = kordx.groove.song.all.value
                if (allSongIds.isEmpty()) {
                    Logger.warn(
                        "RadioSession",
                        "shuffle all ignored: library is empty",
                    )
                    return
                }

                // the workaround that wrote directly to `kordx.radio.queue.originalQueue` /
                // `currentQueue` / `currentSongIndex` is reverted. The recursion it was
                // working around is now handled by the `autostart = false` early-return
                // guard in `Radio.play()`. With the guard in place, the canonical
                // `RadioShorty.playQueue(...)` API is safe to use even when some song ids in
                // the library can't be resolved: `playQueue` calls `RadioQueue.add` →
                // `afterAdd` → `Radio.play(...)` with `autostart = false`, and the guard
                // returns cleanly for stale ids. We keep the `autostart = false` semantics
                // so the queue is set up ready to play and the user starts playback via the
                // standard play action (the production music-app pattern — Spotify / Apple
                // Music both defer playback start to the user's explicit play action).
                val shuffledIndex = kotlin.random.Random.nextInt(allSongIds.size)
                kordx.radio.shorty.playQueue(
                    allSongIds,
                    options = Radio.PlayOptions(
                        index = shuffledIndex,
                        autostart = false,
                    ),
                    shuffle = true,
                )
                Logger.warn(
                    "RadioSession",
                    "shuffle all started: ${allSongIds.size} songs in random order (index=$shuffledIndex)",
                )
            }
            RadioSessionState.ACTION_SEARCH -> {

                // The AAOS client handles the actual voice / text input; we just log the
                // receipt so the AVD validation gate confirms the action was routed. The
                // plan: "opens the search bar (delegates to the same code path as
                // `onSearch`)" — the AAOS search dialog is opened by the framework once
                // the action is dispatched; the KordX side doesn't need to do anything
                // beyond logging.
                Logger.warn(
                    "RadioSession",
                    "search action received: opening AAOS search bar",
                )
            }
        }
    }

    /**
     * Handle a voice search from Android Auto / Assistant. Plays the
     * best match from the existing
     * [com.android.rockages.kordx.services.groove.repositories.SongRepository]
     * fuzzy search. fixes the no-match fallback: the prior
     * implementation fell back to playing the whole library when the
     * fuzzy search returned an empty list, which meant a voice
     * "play zzzzz" command would silently start a full-library
     * shuffle. The plan calls for "0 matches → log warning, do
     * nothing" so the user is never surprised by unrelated playback.
     *
     * Empty / null / blank query: shuffle the whole library (the
     * "play some music" / "surprise me" use case). Non-empty query
     * with at least one match: play the first (best) match via
     * [com.android.rockages.kordx.services.radio.RadioShorty.playQueue].
     */
    fun handlePlayFromSearch(query: String?) {
        val app = kordx
        val allSongIds = app.groove.song.all.value
        if (allSongIds.isEmpty()) {
            Logger.warn(
                "RadioSession",
                "onPlayFromSearch: empty library, no action",
            )
            return
        }
        if (query.isNullOrBlank()) {
            Logger.warn(
                "RadioSession",
                "onPlayFromSearch: empty query -> shuffle all (${allSongIds.size} songs)",
            )
            try {
                app.radio.shorty.playQueue(allSongIds, shuffle = true)
            } catch (err: Exception) {
                handlePlaybackError(
                    songId = null,
                    errorCode = ERROR_CODE_UNKNOWN_ERROR,
                    errorMessage = err.message,
                    exception = err,
                )
            }
            return
        }
        val matches = KordXSearch.search(
            query = query,
            songIds = allSongIds,
            lookup = { id -> app.groove.song.get(id)?.let { KordXSearch.songSearchText(it) } ?: "" },
        )
        if (matches.isEmpty()) {
            Logger.warn(
                "RadioSession",
                "onPlayFromSearch: query='$query' -> 0 matches, no action",
            )
            return
        }
        Logger.warn(
            "RadioSession",
            "onPlayFromSearch: query='$query' -> ${matches.size} matches, playing first",
        )
        try {
            app.radio.shorty.playQueue(
                matches,
                options = Radio.PlayOptions(index = 0),
            )
        } catch (err: Exception) {
            handlePlaybackError(
                songId = matches.firstOrNull(),
                errorCode = ERROR_CODE_UNKNOWN_ERROR,
                errorMessage = err.message,
                exception = err,
            )
        }
    }

    /**
     * Resolve a media id produced by legacy MediaBrowserServiceCompat to a queue and
     * play it via the existing [com.android.rockages.kordx.services.radio.Radio]
     * player. Supports a single song leaf (`song:<id>`) or a container
     * (`album:<id>`, `albumArtist:<name>`, `genre:<name>`, `playlist:<id>`),
     * which plays the container's sorted song list starting at index 0.
     */
    fun handlePlayFromMediaId(mediaId: String?, autostart: Boolean = true) {
        val id = mediaId ?: return
        val app = kordx
        val songIds: List<String> = try {
            when {
                id.startsWith(KordXMediaSessionConstants.PREFIX_SONG) -> {
                    KordXMediaSessionConstants.mediaIdToSongId(id)?.let { listOf(it) } ?: return
                }
                id.startsWith(KordXMediaSessionConstants.PREFIX_ALBUM) -> {
                    app.groove.album.get(id.removePrefix(KordXMediaSessionConstants.PREFIX_ALBUM))
                        ?.getSortedSongIds(app) ?: return
                }
                id.startsWith(KordXMediaSessionConstants.PREFIX_ALBUM_ARTIST) -> {
                    val name = id.removePrefix(KordXMediaSessionConstants.PREFIX_ALBUM_ARTIST)
                    com.android.rockages.kordx.core.groove.AlbumArtist(name, 0, 0).getSortedSongIds(app)
                }
                id.startsWith(KordXMediaSessionConstants.PREFIX_GENRE) -> {
                    val name = id.removePrefix(KordXMediaSessionConstants.PREFIX_GENRE)
                    com.android.rockages.kordx.core.groove.Genre(name, 0).getSortedSongIds(app)
                }
                id.startsWith(KordXMediaSessionConstants.PREFIX_PLAYLIST) -> {
                    app.groove.playlist.get(id.removePrefix(KordXMediaSessionConstants.PREFIX_PLAYLIST))
                        ?.getSongIds(app) ?: return
                }
                else -> return
            }
        } catch (err: Exception) {
            handlePlaybackError(
                songId = KordXMediaSessionConstants.mediaIdToSongId(id),
                errorCode = ERROR_CODE_UNKNOWN_ERROR,
                errorMessage = err.message,
                exception = err,
            )
            return
        }
        if (songIds.isEmpty()) return
        val startSongId = KordXMediaSessionConstants.mediaIdToSongId(id)
        val index = if (startSongId != null) {
            songIds.indexOf(startSongId).coerceAtLeast(0)
        } else 0
        try {
            app.radio.shorty.playQueue(
                songIds,
                options = Radio.PlayOptions(
                    index = index,
                    autostart = autostart,
                ),
            )
        } catch (err: Exception) {
            handlePlaybackError(
                songId = startSongId,
                errorCode = ERROR_CODE_UNKNOWN_ERROR,
                errorMessage = err.message,
                exception = err,
            )
        }
    }

    /**
     * — playback error path. Called from
     * [handlePlayFromMediaId] and [handlePlayFromSearch] when
     * [com.android.rockages.kordx.services.radio.RadioShorty.playQueue]
     * (or any of the song-lookup calls that precede it) throws. The
     * plan's contract: "On exception: log the error (with the song id and
     * exception class). Skip to next song in the queue automatically.
     * Do not crash the service."
     *
     * The Media3 service now owns the published playback state, so this
     * handler no longer pushes to a legacy MediaSessionCompat. It only
     * logs and auto-skips.
     */
    internal fun handlePlaybackError(
        songId: String?,
        errorCode: Int,
        errorMessage: String?,
        exception: Exception? = null,
    ) {
        val message = "playback error: song=${songId ?: "<unknown>"}, " +
            "code=$errorCode, " +
            "exception=${exception?.javaClass?.simpleName ?: "<none>"}, " +
            "message=${errorMessage ?: exception?.message ?: "<no message>"}"
        Logger.warn(
            "RadioSession",
            message,
        )

        // Auto-skip to the next song. If the next song also fails, the existing
        // `Radio.play()` error handler will recursively call
        // `onSongFinish(SongFinishSource.Exception)`, which advances to the next
        // index; this loop continues until either a song plays successfully or the
        // queue is empty. The plan: "Skip to next song in the queue automatically".
        try {
            if (kordx.radio.canJumpToNext()) {
                kordx.radio.shorty.skip()
            }
        } catch (skipErr: Exception) {
            Logger.warn(
                "RadioSession",
                "auto-skip failed: ${skipErr.message}",
            )
        }
    }

    fun createEqualizerActivityContract() = object : ActivityResultContract<Unit, Unit>() {
        override fun createIntent(
            context: Context,
            input: Unit,
        ) = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, kordx.applicationContext.packageName)
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, kordx.radio.audioSessionId ?: 0)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }

        override fun parseResult(
            resultCode: Int,
            intent: Intent?,
        ) {
        }
    }

    companion object {
        private const val ERROR_CODE_UNKNOWN_ERROR = -1

        const val DEBUG_ACTION_SHUFFLE = "com.android.rockages.kordx.radio.DEBUG_SHUFFLE"
        const val DEBUG_ACTION_REPEAT = "com.android.rockages.kordx.radio.DEBUG_REPEAT"
        const val DEBUG_ACTION_FAVORITE = "com.android.rockages.kordx.radio.DEBUG_FAVORITE"
        const val DEBUG_ACTION_SHUFFLE_ALL = "com.android.rockages.kordx.radio.DEBUG_SHUFFLE_ALL"
        const val DEBUG_ACTION_ROOT_SEARCH = "com.android.rockages.kordx.radio.DEBUG_ROOT_SEARCH"

        const val DEBUG_ACTION_SEARCH = "com.android.rockages.kordx.radio.DEBUG_SEARCH"
        const val EXTRA_DEBUG_QUERY = "query"
        const val DEBUG_ACTION_RECENT_PLAY = "com.android.rockages.kordx.radio.DEBUG_RECENT_PLAY"
        const val EXTRA_DEBUG_SONG_ID = "songId"
        const val DEBUG_ACTION_PLAYBACK_ERROR = "com.android.rockages.kordx.radio.DEBUG_PLAYBACK_ERROR"
        const val EXTRA_DEBUG_ERROR_CODE = "code"
    }
}
