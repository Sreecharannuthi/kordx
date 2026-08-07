package com.android.rockages.kordx.services.radio

import com.android.rockages.kordx.core.utils.EventUnsubscribeFn

/** Narrow adapter surface for [Radio]. Lets the [RadioForwardingPlayer] be JVM-testable with a hand-rolled fake (no `KordX`, no `Application`, no `Room`). The real [Radio] class implements this interface in its class header. Tests provide hand-rolled fakes. Kept in a separate file from [Radio] and [RadioForwardingPlayer] so the compilation order doesn't matter (Kotlin compiles file-by-file; if [RadioAdapterTarget] is in the same file as the consumer, the compiler may not see the interface declaration before the consumer's `override` modifiers). */
interface RadioAdapterTarget {
 val hasPlayer: Boolean
 val isPlaying: Boolean
 val currentPlaybackPosition: RadioPlayer.PlaybackPosition?
 val currentSpeed: Float
 val currentPitch: Float
 val audioSessionId: Int?
 val queue: RadioQueueAdapterTarget
 val shorty: RadioShortyAdapterTarget

 /**
 * Subscribe to the [Radio]'s event stream. Returns an
 * unsubscribe function. The real [Radio] delegates to
 * `onUpdate.subscribe(...)`; tests can capture events in a
 * `MutableList` for assertion.
 */
 fun subscribeToEvents(subscriber: (Radio.Events) -> Unit): EventUnsubscribeFn

 fun seek(positionMs: Long)
 fun stop()
 fun setSpeed(speed: Float, persist: Boolean)
 fun setPitch(pitch: Float, persist: Boolean)
}

/** Narrow adapter surface for [RadioQueue]. The real [RadioQueue] implements this interface in its class header. */
interface RadioQueueAdapterTarget {
 val currentShuffleMode: Boolean
 val currentLoopMode: RadioQueue.LoopMode
 val currentSongId: String?
 val currentSongIndex: Int
 val currentQueue: List<String>
 fun setLoopMode(mode: RadioQueue.LoopMode)
 fun setShuffleMode(enabled: Boolean)
}

/** Narrow adapter surface for [RadioShorty]. The real [RadioShorty] implements this interface in its class header. */
interface RadioShortyAdapterTarget {
 fun playPause()
 fun seekFromCurrent(offsetSecs: Int)
 fun previous(): Boolean
 fun skip(): Boolean
}
