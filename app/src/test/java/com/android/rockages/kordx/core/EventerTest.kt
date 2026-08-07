package com.android.rockages.kordx.core

import com.android.rockages.kordx.core.utils.Eventer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class EventerTest {

 @Test
 fun `unsubscribe during dispatch does not throw`() {
 val eventer = Eventer<Int>()
 val received = mutableListOf<Int>()

 lateinit var unsubscribeSelf: () -> Unit
 unsubscribeSelf = eventer.subscribe { value ->
 received.add(value)
 // A subscriber removing itself mid-dispatch must not throw
 // ConcurrentModificationException (CopyOnWriteArrayList).
 unsubscribeSelf()
 }
 eventer.subscribe { received.add(it * 10) }

 eventer.dispatch(1)
 eventer.dispatch(2)

 // First dispatch hit both subscribers; the self-removing one is gone
 // for the second.
 assertEquals(listOf(1, 10, 20), received)
 }

 @Test
 fun `subscribe during dispatch does not throw`() {
 val eventer = Eventer<Int>()
 val received = mutableListOf<Int>()

 var added = false
 eventer.subscribe {
 received.add(it)
 if (!added) {
 added = true
 eventer.subscribe { late -> received.add(late * 100) }
 }
 }

 eventer.dispatch(1)
 eventer.dispatch(2)

 // Late subscriber sees only the second dispatch (snapshot iteration).
 assertEquals(listOf(1, 2, 200), received)
 }

 @Test
 fun `concurrent subscribe unsubscribe and dispatch do not throw`() {
 val eventer = Eventer<Int>()
 val dispatched = AtomicInteger(0)
 val failures = AtomicInteger(0)
 val stop = CountDownLatch(1)

 val workers = List(8) { worker ->
 thread {
 try {
 while (stop.count > 0) {
 val unsub = eventer.subscribe { }
 eventer.dispatch(worker)
 dispatched.incrementAndGet()
 unsub()
 }
 } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
 t.printStackTrace()
 failures.incrementAndGet()
 }
 }
 }

 Thread.sleep(200)
 stop.countDown()
 workers.forEach { it.join(5000) }

 assertTrue(dispatched.get() > 0, "dispatches happened")
 assertEquals(0, failures.get(), "no concurrent-modification failures")
 }
}
