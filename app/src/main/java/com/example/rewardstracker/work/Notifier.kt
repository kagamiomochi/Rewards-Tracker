package com.example.rewardstracker.work

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.rewardstracker.MainActivity

object Notifier {
    private const val CHANNEL_ID = "rewards_tracker"

    @SuppressLint("MissingPermission")
    fun notify(
        context: Context,
        text: String,
        id: Int = 1,
        icon: Int = android.R.drawable.stat_notify_error
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val channel = NotificationChannel(
            CHANNEL_ID, "Rewards Tracker", NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)

        val intent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle("Rewards Tracker")
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }
}
