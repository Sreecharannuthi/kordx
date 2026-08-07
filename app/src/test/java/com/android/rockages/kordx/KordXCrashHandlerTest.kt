package com.android.rockages.kordx

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pinning tests for [KordXCrashHandler] policy (RV6):
 * - main-thread crash → process kill, no error screen;
 * - background crash → error screen, process kept alive;
 * - error-screen failure → delegate to the previously installed handler
 *   (or kill when there is none) so crashes are never silently swallowed.
 */
class KordXCrashHandlerTest {

 private class Recorder {
 var killed = false
 var errorStartedWith: Throwable? = null
 var previousInvokedWith: Throwable? = null
 var logCount = 0
 }

 private fun handler(
 recorder: Recorder,
 mainThread: Thread,
 previous: Thread.UncaughtExceptionHandler?,
 failErrorScreen: Boolean = false,
 ) = KordXCrashHandler(
 context = null,
 mainThread = mainThread,
 previous = previous,
 logger = { _, _ -> recorder.logCount++ },
 errorScreenStarter = { _, err ->
 check(!failErrorScreen) { "cannot start" }
 recorder.errorStartedWith = err
 },
 processKiller = { recorder.killed = true },
 )

 @Test
 fun `main thread crash kills process without error screen`() {
 val recorder = Recorder()
 val main = Thread.currentThread()
 val h = handler(recorder, mainThread = main, previous = null)

 h.uncaughtException(main, RuntimeException("boom"))

 assertTrue(recorder.killed, "main-thread crash must kill the process")
 assertEquals(null, recorder.errorStartedWith, "error screen must not start on main-thread crash")
 assertEquals(null, recorder.previousInvokedWith)
 }

 @Test
 fun `background crash starts error screen and keeps process alive`() {
 val recorder = Recorder()
 val err = RuntimeException("bg")
 val h = handler(recorder, mainThread = Thread("main"), previous = null)

 h.uncaughtException(Thread("worker-1"), err)

 assertEquals(err, recorder.errorStartedWith, "background crash must show the error screen")
 assertFalse(recorder.killed, "background crash must not kill the process")
 assertEquals(null, recorder.previousInvokedWith)
 }

 @Test
 fun `error screen failure delegates to previous handler`() {
 val recorder = Recorder()
 val err = RuntimeException("bg")
 val previous = Thread.UncaughtExceptionHandler { _, e -> recorder.previousInvokedWith = e }
 val h = handler(recorder, mainThread = Thread("main"), previous = previous, failErrorScreen = true)

 h.uncaughtException(Thread("worker-2"), err)

 assertEquals(err, recorder.previousInvokedWith, "crash must be delegated when the screen can't start")
 assertFalse(recorder.killed, "delegation, not kill, when a previous handler exists")
 }

 @Test
 fun `error screen failure with no previous handler kills process`() {
 val recorder = Recorder()
 val h = handler(recorder, mainThread = Thread("main"), previous = null, failErrorScreen = true)

 h.uncaughtException(Thread("worker-3"), RuntimeException("bg"))

 assertTrue(recorder.killed, "no previous handler + no error screen → kill (never swallow)")
 }

 @Test
 fun `crash is always logged`() {
 val recorder = Recorder()
 val h = handler(recorder, mainThread = Thread("main"), previous = null)

 h.uncaughtException(Thread("worker-4"), RuntimeException("bg"))

 assertTrue(recorder.logCount > 0, "crash must be logged")
 }
}
