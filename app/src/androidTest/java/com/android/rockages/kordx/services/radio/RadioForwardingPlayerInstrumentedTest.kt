package com.android.rockages.kordx.services.radio

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.rockages.kordx.core.utils.EventUnsubscribeFn
import com.android.rockages.kordx.core.utils.Eventer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@UnstableApi
@RunWith(AndroidJUnit4::class)
class RadioForwardingPlayerInstrumentedTest {
    private lateinit var radio: FakeRadio
    private lateinit var player: RadioForwardingPlayer

    @Before
    fun setUp() {
        radio = FakeRadio()
        radio.queue.currentSongId = "song-1"
        radio.queue.currentSongIndex = 0
        radio.queue.currentQueue = listOf("song-1")
        player = RadioForwardingPlayer(
            radio = radio,
            songMediaItemResolver = {
                MediaItem.Builder()
                    .setMediaId("song-1")
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setDurationMs(240_000L)
                            .build()
                    )
                    .build()
            },
            seekBackDurationMs = 15_000L,
            seekForwardDurationMs = 30_000L,
            playerListenerHandler = Handler(Looper.getMainLooper()),
        )
    }

    @After
    fun tearDown() {
        player.release()
    }

    @Test
    fun availableCommandsIncludeCurrentMediaItemBeforeListeners() {
        val commands = player.getAvailableCommands()

        assertTrue(commands.contains(Player.COMMAND_GET_CURRENT_MEDIA_ITEM))
    }

    @Test
    fun localSongWindowIsNotLive() {
        val window = player.currentTimeline.getWindow(0, Timeline.Window())

        assertFalse(window.isLive)
        assertFalse(player.isCurrentMediaItemLive())
        assertEquals(C.msToUs(240_000L), window.durationUs)
    }

    @Test
    fun firstPositivePositionPublishesInitialTimelineSnapshot() {
        val callbackCount = AtomicInteger()
        val initialCallback = CountDownLatch(1)
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) {
                    callbackCount.incrementAndGet()
                    initialCallback.countDown()
                }
            }
        }
        player.addListener(listener)

        assertTrue(initialCallback.await(1, TimeUnit.SECONDS))
        callbackCount.set(0)

        radio.isPlaying = true
        radio.currentPlaybackPosition = RadioPlayer.PlaybackPosition(1_000L, 240_000L)
        radio.onPlaybackPositionUpdate.dispatch(radio.currentPlaybackPosition!!)

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(1, callbackCount.get())
    }

    private class FakeRadio : RadioAdapterTarget {
        override var hasPlayer: Boolean = true
        override var isPlaying: Boolean = false
        override var currentPlaybackPosition: RadioPlayer.PlaybackPosition? = null
        override val currentSpeed: Float = 1f
        override val currentPitch: Float = 1f
        override val audioSessionId: Int? = 0
        override val onPlaybackPositionUpdate = Eventer<RadioPlayer.PlaybackPosition>()
        override val queue = FakeQueue()
        override val shorty = FakeShorty()

        override fun subscribeToEvents(subscriber: (Radio.Events) -> Unit): EventUnsubscribeFn = {}
        override fun seek(positionMs: Long) {}
        override fun stop() {}
        override fun setSpeed(speed: Float, persist: Boolean) {}
        override fun setPitch(pitch: Float, persist: Boolean) {}
    }

    private class FakeQueue : RadioQueueAdapterTarget {
        override var currentShuffleMode: Boolean = false
        override var currentLoopMode: RadioQueue.LoopMode = RadioQueue.LoopMode.None
        override var currentSongId: String? = null
        override var currentSongIndex: Int = -1
        override var currentQueue: List<String> = emptyList()

        override fun setLoopMode(mode: RadioQueue.LoopMode) {}
        override fun setShuffleMode(enabled: Boolean) {}
    }

    private class FakeShorty : RadioShortyAdapterTarget {
        override fun playPause() {}
        override fun seekFromCurrent(offsetSecs: Int) {}
        override fun previous(): Boolean = false
        override fun skip(): Boolean = false
    }
}
