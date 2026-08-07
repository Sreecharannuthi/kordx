package com.android.rockages.kordx.services.radio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import com.android.rockages.kordx.KordX

class RadioNativeReceiver(private val kordx: KordX) : BroadcastReceiver() {
 fun start() {
 kordx.applicationContext.registerReceiver(
 this,
 IntentFilter().apply {
 addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
 addAction(Intent.ACTION_HEADSET_PLUG)
 }
 )
 }

 fun destroy() {
 kordx.applicationContext.unregisterReceiver(this)
 }

 override fun onReceive(context: Context?, intent: Intent?) {
 intent?.action?.let { action ->
 when (action) {
 Intent.ACTION_HEADSET_PLUG -> {
 intent.extras?.getInt("state", -1)?.let {
 when (it) {
 0 -> onHeadphonesDisconnect()
 1 -> onHeadphonesConnect()
 else -> {}
 }
 }
 }

 AudioManager.ACTION_AUDIO_BECOMING_NOISY -> onHeadphonesDisconnect()
 else -> {}
 }
 }
 }

 private fun onHeadphonesConnect() {
 if (!kordx.radio.hasPlayer) {
 return
 }
 if (!kordx.radio.isPlaying && kordx.settings.playOnHeadphonesConnect.value) {
 kordx.radio.resume()
 }
 }

 private fun onHeadphonesDisconnect() {
 if (!kordx.radio.hasPlayer) {
 return
 }
 if (kordx.radio.isPlaying && kordx.settings.pauseOnHeadphonesDisconnect.value) {
 kordx.radio.pauseInstant()
 }
 }
}
