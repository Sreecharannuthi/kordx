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
        // Register the crash handler first — before the graph build — so
        // Application-, service-, and Auto cold-start crashes are covered
        // (previously registered in MainActivity.onCreate, too late for
        // anything that crashes without a UI launch). Chains to the
        // previously installed handler when the error screen can't start.
        Thread.setDefaultUncaughtExceptionHandler(
            KordXCrashHandler(
                context = this,
                mainThread = mainLooper.thread,
                previous = Thread.getDefaultUncaughtExceptionHandler(),
            )
        )
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
