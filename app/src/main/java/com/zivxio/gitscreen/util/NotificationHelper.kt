package com.zivxio.gitscreen.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.zivxio.gitscreen.R
import com.zivxio.gitscreen.ui.ManualTagActivity

object NotificationHelper {

    const val CHANNEL_WATCH = "gitscreen_watch"
    const val CHANNEL_PROMPT = "gitscreen_prompt"

    const val WATCH_NOTIFICATION_ID = 1001
    const val MANUAL_TAG_NOTIFICATION_ID_BASE = 2000

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WATCH,
                context.getString(R.string.notif_channel_watch_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = context.getString(R.string.notif_channel_watch_desc)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROMPT,
                context.getString(R.string.notif_channel_prompt_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_prompt_desc)
            }
        )
    }

    fun buildWatchNotification(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_WATCH)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(context.getString(R.string.notif_watch_title))
            .setContentText(context.getString(R.string.notif_watch_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    fun showManualTagNotification(context: Context, screenshotUri: Uri, takenAt: Long) {
        val intent = Intent(context, ManualTagActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = screenshotUri
            putExtra(ManualTagActivity.EXTRA_TAKEN_AT, takenAt)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            screenshotUri.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PROMPT)
            .setSmallIcon(android.R.drawable.ic_menu_help)
            .setContentTitle(context.getString(R.string.notif_manual_title))
            .setContentText(context.getString(R.string.notif_manual_text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(MANUAL_TAG_NOTIFICATION_ID_BASE + (screenshotUri.hashCode() % 1000), notification)
    }
}
