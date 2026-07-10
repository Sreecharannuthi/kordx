package com.android.rockages.kordx.services.radio

import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.utils.EventUnsubscribeFn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RadioObservatory(private val kordx: KordX) {
 private var updateSubscriber: EventUnsubscribeFn? = null
 private var playbackPositionUpdateSubscriber: EventUnsubscribeFn? = null

 private val _isPlaying = MutableStateFlow(false)
 val isPlaying = _isPlaying.asStateFlow()
 private val _playbackPosition = MutableStateFlow(RadioPlayer.PlaybackPosition.zero)
 val playbackPosition = _playbackPosition.asStateFlow()
 private val _queueIndex = MutableStateFlow(-1)
 val queueIndex = _queueIndex.asStateFlow()
 private val _queue = MutableStateFlow(emptyList<String>())
 val queue = _queue.asStateFlow()
 private val _loopMode = MutableStateFlow(RadioQueue.LoopMode.None)
 val loopMode = _loopMode.asStateFlow()
 private val _shuffleMode = MutableStateFlow(false)
 val shuffleMode = _shuffleMode.asStateFlow()
 private val _sleepTimer = MutableStateFlow<Radio.SleepTimer?>(null)
 val sleepTimer = _sleepTimer.asStateFlow()
 private val _pauseOnCurrentSongEnd = MutableStateFlow(false)
 val pauseOnCurrentSongEnd = _pauseOnCurrentSongEnd.asStateFlow()
 private val _speed = MutableStateFlow(RadioPlayer.DEFAULT_SPEED)
 val speed = _speed.asStateFlow()
 private val _persistedSpeed = MutableStateFlow(RadioPlayer.DEFAULT_SPEED)
 val persistedSpeed = _persistedSpeed.asStateFlow()
 private val _pitch = MutableStateFlow(RadioPlayer.DEFAULT_PITCH)
 val pitch = _pitch.asStateFlow()
 private val _persistedPitch = MutableStateFlow(RadioPlayer.DEFAULT_PITCH)
 val persistedPitch = _persistedPitch.asStateFlow()

 fun start() {
 updateSubscriber = kordx.radio.onUpdate.subscribe { event ->
 when (event) {
 Radio.Events.Player.Seeked -> emitPlaybackPosition()
 is Radio.Events.Player -> emitIsPlaying()
 is Radio.Events.Queue.IndexChanged -> emitQueueIndex()
 is Radio.Events.Queue -> emitQueue()
 Radio.Events.QueueOption.LoopModeChanged -> emitLoopMode()
 Radio.Events.QueueOption.ShuffleModeChanged -> emitShuffleMode()
 Radio.Events.QueueOption.SleepTimerChanged -> emitSleepTimer()
 Radio.Events.QueueOption.SpeedChanged -> emitSpeed()
 Radio.Events.QueueOption.PitchChanged -> emitPitch()
 Radio.Events.QueueOption.PauseOnCurrentSongEndChanged -> emitPauseOnCurrentSongEnd()
 }
 }
 playbackPositionUpdateSubscriber = kordx.radio.onPlaybackPositionUpdate.subscribe {
 emitPlaybackPosition()
 }
 }

 fun destroy() {
 updateSubscriber?.invoke()
 playbackPositionUpdateSubscriber?.invoke()
 }

 private fun emitIsPlaying() = _isPlaying.update {
 kordx.radio.isPlaying
 }

 private fun emitPlaybackPosition() = _playbackPosition.update {
 kordx.radio.currentPlaybackPosition ?: RadioPlayer.PlaybackPosition.zero
 }

 private fun emitQueueIndex() = _queueIndex.update {
 kordx.radio.queue.currentSongIndex
 }

 private fun emitLoopMode() = _loopMode.update {
 kordx.radio.queue.currentLoopMode
 }

 private fun emitShuffleMode() = _shuffleMode.update {
 kordx.radio.queue.currentShuffleMode
 }

 private fun emitSleepTimer() = _sleepTimer.update {
 kordx.radio.sleepTimer
 }

 private fun emitSpeed() {
 _speed.update { kordx.radio.currentSpeed }
 _persistedSpeed.update { kordx.radio.persistedSpeed }
 }

 fun emitPitch() {
 _pitch.update { kordx.radio.currentPitch }
 _persistedPitch.update { kordx.radio.persistedPitch }
 }


 private fun emitPauseOnCurrentSongEnd() = _pauseOnCurrentSongEnd.update {
 kordx.radio.pauseOnCurrentSongEnd
 }

 private fun emitQueue() {
 _queue.update {
 kordx.radio.queue.currentQueue.toList()
 }
 }
}
