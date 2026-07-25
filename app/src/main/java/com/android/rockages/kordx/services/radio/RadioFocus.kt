package com.android.rockages.kordx.services.radio

import android.media.AudioManager
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.android.rockages.kordx.KordX

// Credits: https://github.com/RetroMusicPlayer/RetroMusicPlayer/blob/7b1593009319c8d8e04660470ba37f814e8203eb/app/src/main/java/code/name/monkey/retromusic/service/LocalPlayback.kt
class RadioFocus(val kordx: KordX) {
 var hasFocus = false
 private set
 private var restoreVolumeOnFocusGain = false

 private val audioManager: AudioManager =
 kordx.applicationContext.getSystemService(AudioManager::class.java)

 private val audioFocusRequest: AudioFocusRequestCompat =
 AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
 .setAudioAttributes(
 AudioAttributesCompat.Builder()
 .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
 .build()
 )
 .setOnAudioFocusChangeListener { event ->
 when (event) {
 AudioManager.AUDIOFOCUS_GAIN -> {
 hasFocus = true
 if (restoreVolumeOnFocusGain) {
 restoreVolumeOnFocusGain = false
 when {
 kordx.radio.isPlaying -> kordx.radio.restoreVolume()
 else -> kordx.radio.resume()
 }
 }
 }

 AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
 hasFocus = false
 val wasPlaying = kordx.radio.isPlaying
 if (!kordx.settings.ignoreAudioFocusLoss.value) {
 kordx.radio.pause()
 }
 // Arm AFTER pause(): Radio.pause() cancels any pending
 // focus-gain resume (so user-initiated pauses never
 // auto-resume); the focus-loss path re-arms it here so
 // playback interrupted by focus loss still resumes on
 // AUDIOFOCUS_GAIN.
 restoreVolumeOnFocusGain = wasPlaying
 }

 AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
 restoreVolumeOnFocusGain = kordx.radio.isPlaying
 if (kordx.radio.isPlaying) {
 kordx.radio.duck()
 }
 }
 }
 }
 .build()

 fun requestFocus() = AudioManagerCompat.requestAudioFocus(
 audioManager,
 audioFocusRequest
 ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

 fun abandonFocus() = AudioManagerCompat.abandonAudioFocusRequest(
 audioManager,
 audioFocusRequest
 ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

 /**
 * Cancels a pending focus-gain auto-resume. Called by
 * [Radio] when playback is paused or stopped outside the
 * focus-loss path (user pause, stop, sleep timer), so a
 * later AUDIOFOCUS_GAIN does not resurrect playback the
 * user explicitly stopped. The focus-loss path itself
 * re-arms the flag after calling `Radio.pause()`.
 */
 fun cancelPendingFocusResume() {
 restoreVolumeOnFocusGain = false
 }
}
