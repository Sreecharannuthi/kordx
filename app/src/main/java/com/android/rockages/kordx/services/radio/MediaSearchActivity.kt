package com.android.rockages.kordx.services.radio

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.app.SearchManager
import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.utils.Logger

/** Handles Android Auto voice playback without launching the phone Compose UI. */
class MediaSearchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val query = MediaSearchCommand.queryFrom(intent)
        dispatchWhenReady(query, attemptsRemaining = 20)
    }

    private fun dispatchWhenReady(query: String?, attemptsRemaining: Int) {
        val app = KordX.instance
        if (app != null) {
            Logger.warn(LOG_TAG, "MEDIA_PLAY_FROM_SEARCH: query='${query.orEmpty()}'")
            app.radio.session.handlePlayFromSearch(query)
            finish()
            return
        }
        if (attemptsRemaining == 0) {
            Logger.warn(LOG_TAG, "KordX graph unavailable after startup grace period")
            finish()
            return
        }
        Handler(Looper.getMainLooper()).postDelayed(
            { dispatchWhenReady(query, attemptsRemaining - 1) },
            RETRY_DELAY_MS,
        )
    }

    companion object {
        private const val LOG_TAG = "MediaSearchActivity"
        private const val RETRY_DELAY_MS = 50L
    }
}

internal object MediaSearchCommand {
    fun queryFrom(intent: Intent?): String? {
        if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return null
        val focus = intent.getBundleExtra(MediaStore.EXTRA_MEDIA_FOCUS)
        val typedQuery = focus?.let {
            buildList {
                it.getString(MediaStore.EXTRA_MEDIA_TITLE)?.takeIf(String::isNotBlank)?.let(::add)
                it.getString(MediaStore.EXTRA_MEDIA_ALBUM)?.takeIf(String::isNotBlank)?.let(::add)
                it.getString(MediaStore.EXTRA_MEDIA_ARTIST)?.takeIf(String::isNotBlank)?.let(::add)
                it.getString(MediaStore.EXTRA_MEDIA_GENRE)?.takeIf(String::isNotBlank)?.let(::add)
                @Suppress("DEPRECATION")
                it.getString(MediaStore.EXTRA_MEDIA_PLAYLIST)?.takeIf(String::isNotBlank)?.let(::add)
            }.joinToString(" ").takeIf(String::isNotEmpty)
        }
        return typedQuery ?: intent.getStringExtra(SearchManager.QUERY)
    }
}
