package com.android.rockages.kordx

import android.app.Application
import com.android.rockages.kordx.core.utils.Logger

/**
 * KordX [Application] entry-point. Builds the process-level
 * [KordX] graph here so it is available before any Activity or
 * Service is created. This is required for Android Auto cold
 * starts: the system can bind to [KordXMediaLibraryService]
 * without first launching [MainActivity], and the service needs
 * the live [KordX.radio] / [KordX.groove] state to build a
 * real playback session.
 */
class KordXApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Logger.warn(LOG_TAG, "onCreate: building KordX graph")
        val kordx = KordX(this)
        kordx.emitReady()
    }

    override fun onTerminate() {
        Logger.warn(LOG_TAG, "onTerminate: destroying KordX graph")
        KordX.instance?.emitDestroy()
        super.onTerminate()
    }

    companion object {
        private const val LOG_TAG = "KordXApplication"
    }
}
