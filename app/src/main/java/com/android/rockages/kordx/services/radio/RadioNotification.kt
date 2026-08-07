package com.android.rockages.kordx.services.radio

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.android.rockages.kordx.MainActivity
import com.android.rockages.kordx.R
import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.utils.Logger


class RadioNotification(private val kordx: KordX) {
 private var manager = RadioNotificationManager(kordx)

 fun start() {
 manager.prepare()
 }

 fun cancel() {
 manager.cancel()
 }

 fun update(req: RadioSession.UpdateRequest) {
 val notification = NotificationCompat.Builder(
 kordx.applicationContext,
 CHANNEL_ID
 ).run {
 setSmallIcon(R.drawable.material_icon_music_note)
 setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
 setContentIntent(
 PendingIntent.getActivity(
 kordx.applicationContext,
 0,
 Intent(kordx.applicationContext, MainActivity::class.java)
 .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
 PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
 )
 )
 setContentTitle(req.song.title)
 setContentText(req.song.artists.joinToString(", "))
 setLargeIcon(req.artworkBitmap)
 setOngoing(req.isPlaying)
 addAction(
 createAction(
 R.drawable.material_icon_skip_previous,
 kordx.t.Previous,
 RadioSession.ACTION_PREVIOUS
 )
 )
 addAction(
 when {
 req.isPlaying -> createAction(
 R.drawable.material_icon_pause,
 kordx.t.Play,
 RadioSession.ACTION_PLAY_PAUSE
 )

 else -> createAction(
 R.drawable.material_icon_play,
 kordx.t.Pause,
 RadioSession.ACTION_PLAY_PAUSE
 )
 }
 )
 addAction(
 createAction(
 R.drawable.material_icon_skip_next,
 kordx.t.Next,
 RadioSession.ACTION_NEXT
 )
 )
 addAction(
 createAction(
 R.drawable.material_icon_stop,
 kordx.t.Stop,
 RadioSession.ACTION_STOP
 )
 )
 setStyle(
 androidx.media.app.NotificationCompat.MediaStyle()
 .setMediaSession(kordx.radio.session.mediaSession.sessionToken)
 .setShowActionsInCompactView(0, 1, 2)
 )
 }
 try {
 manager.notify(notification.build())
 } catch (err: Exception) {
 Logger.error("RadioNotification", "unable to update notification", err)
 }
 }

 private fun createAction(icon: Int, title: String, action: String): NotificationCompat.Action {
 return NotificationCompat.Action
 .Builder(icon, title, createActionIntent(action))
 .build()
 }

 private fun createActionIntent(action: String): PendingIntent {
 return PendingIntent.getBroadcast(
 kordx.applicationContext,
 0,
 Intent(action),
 PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
 )
 }

 companion object {
 val CHANNEL_ID = "${R.string.app_name}_media_notification"
 const val NOTIFICATION_ID = 69421
 }
}
