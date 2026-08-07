package com.android.rockages.kordx

import android.content.Context
import android.os.Process
import com.android.rockages.kordx.core.utils.Logger
import kotlin.system.exitProcess

/**
 * Process-wide crash handler, registered once in [KordXApplication.onCreate]
 * (previously in `MainActivity.onCreate`, which left Application-, service-,
 * and Auto cold-start crashes uncovered).
 *
 * Policy:
 * - **Main thread**: kill the process — `ErrorActivity` creation is
 *   dispatched to the (now dead) main Looper, so the process would linger
 *   as a headless zombie rendering a white window (see WS1 post-mortem).
 * - **Background thread**: show `ErrorActivity` and keep the process alive
 *   (playback continues; the screen offers relaunch). If the screen cannot
 *   be started, delegate to the previously registered handler — or kill the
 *   process when there is none — so a crash is never swallowed silently.
 *
 * Dependencies are constructor-injected so the policy is unit-testable on
 * the JVM (see `KordXCrashHandlerTest`).
 */
class KordXCrashHandler(
 private val context: Context?,
 private val mainThread: Thread,
 private val previous: Thread.UncaughtExceptionHandler?,
 private val logger: (String, Throwable) -> Unit = { m, e -> Logger.error(LOG_TAG, m, e) },
 private val errorScreenStarter: (Context?, Throwable) -> Unit = { c, e ->
 ErrorActivity.start(c!!, e)
 },
 private val processKiller: () -> Unit = {
 Process.killProcess(Process.myPid())
 exitProcess(10)
 },
) : Thread.UncaughtExceptionHandler {

 override fun uncaughtException(thread: Thread, err: Throwable) {
 logger("uncaught exception on thread '${thread.name}'", err)
 if (thread == mainThread) {
 processKiller()
 return
 }
 runCatching { errorScreenStarter(context, err) }.onFailure { failure ->
 logger("error screen failed to start; delegating to previous handler", failure)
 previous?.uncaughtException(thread, err) ?: processKiller()
 }
 }

 private companion object {
 const val LOG_TAG = "KordXCrashHandler"
 }
}
