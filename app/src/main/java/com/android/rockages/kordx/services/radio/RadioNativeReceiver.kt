package com.android.rockages.kordx.services.radio

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import com.android.rockages.kordx.KordX

class RadioNativeReceiver(private val kordx: KordX) : BroadcastReceiver() {
 fun start() {
 // On API 33+ (Tiramisu) `Context.registerReceiver` requires an
 // explicit `RECEIVER_EXPORTED` or `RECEIVER_NOT_EXPORTED` flag; the
 // unflagged overload throws `SecurityException` at runtime, which
 // propagates out of the `Radio` constructor → the `KordX` ViewModel
 // constructor → the framework's `ViewModelProvider` factory, surfacing
 // as "Cannot create an instance of class com.android.rockages.kordx.KordX"
 // and the red "Something went horribly wrong!" crash screen at app start
 // (see docs/workflow/learnings.md "Debug receiver gating" — this is the
 // same contract; the call site was missed in the rollout).
 //
 // The two broadcast actions below (`ACTION_AUDIO_BECOMING_NOISY`,
 // `ACTION_HEADSET_PLUG`) are protected system broadcasts. No external
 // app sends them, so `RECEIVER_NOT_EXPORTED` is the right flag here
 // (more restrictive than the `RECEIVER_EXPORTED` flag used in
 // `RadioSession`, which is correct there because the media-button
 // receiver gets intents from the notification / MediaController).
 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
 kordx.applicationContext.registerReceiver(
 this,
 IntentFilter().apply {
 addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
 addAction(Intent.ACTION_HEADSET_PLUG)
 },
 Context.RECEIVER_NOT_EXPORTED,
 )
 } else {
 @SuppressLint("UnspecifiedRegisterReceiverFlag")
 kordx.applicationContext.registerReceiver(
 this,
 IntentFilter().apply {
 addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
 addAction(Intent.ACTION_HEADSET_PLUG)
 },
 )
 }
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
