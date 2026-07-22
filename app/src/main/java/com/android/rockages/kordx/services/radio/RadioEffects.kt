package com.android.rockages.kordx.services.radio

import java.util.Timer
import kotlin.math.max
import kotlin.math.min

object RadioEffects {
 class Fader(
 val options: Options,
 val onUpdate: (Float) -> Unit,
 val onFinish: (Boolean) -> Unit,
 ) {
 data class Options(
 val from: Float,
 val to: Float,
 val duration: Int,
 val interval: Int = DEFAULT_INTERVAL,
 ) {
 companion object {
 private const val DEFAULT_INTERVAL = 50
 }
 }

 private var timer: Timer? = null
 private var ended = false
 private val lock = Any()

 fun start() {
 val increments =
 (options.to - options.from) * (options.interval.toFloat() / options.duration)
 var volume = options.from
 val isReverse = options.to < options.from
 timer = kotlin.concurrent.timer(period = options.interval.toLong()) {
 synchronized(lock) {
 if (ended) return@timer
 if (volume != options.to) {
 onUpdate(volume)
 volume = when {
 isReverse -> max(options.to, volume + increments)
 else -> min(options.to, volume + increments)
 }
 } else {
 ended = true
 onFinish(true)
 destroy()
 }
 }
 }
 }

 fun stop() {
 synchronized(lock) {
 if (!ended) {
 ended = true
 onFinish(false)
 }
 destroy()
 }
 }

 private fun destroy() {
 timer?.cancel()
 timer = null
 }
 }

}
