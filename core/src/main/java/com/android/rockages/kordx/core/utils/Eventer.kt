package com.android.rockages.kordx.core.utils

typealias EventSubscriber<T> = (T) -> Unit
typealias EventUnsubscribeFn = () -> Unit

class Eventer<T> {
 // CopyOnWriteArrayList (via concurrentListOf): subscribe/unsubscribe during
 // dispatch must not throw ConcurrentModificationException — dispatchers and
 // subscribers routinely run on different threads (player callbacks vs UI).
 private val subscribers = concurrentListOf<EventSubscriber<T>>()

 fun subscribe(subscriber: EventSubscriber<T>): EventUnsubscribeFn {
 subscribers.add(subscriber)
 return { unsubscribe(subscriber) }
 }

 fun unsubscribe(subscriber: EventSubscriber<T>) {
 subscribers.remove(subscriber)
 }

 fun dispatch(event: T) {
 subscribers.forEach { it(event) }
 }
}
