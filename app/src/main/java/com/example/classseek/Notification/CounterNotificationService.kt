.package com.example.classseek.Notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.classseek.MainActivity
import com.example.classseek.R

class CounterNotificationService(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                COUNTER_CHANNEL_ID,
                "Counter Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Used for counter increment notifications"
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(counter: Int) {
        val activityIntent = Intent(context, MainActivity::class.java)
        val activityPendingIntent = PendingIntent.getActivity(
            context,
            1, // requestCode
            activityIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val incrementIntent = PendingIntent.getBroadcast(
            context,
            2, // requestCode
            Intent(context, CounterNotificationReceiver::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(context, COUNTER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Make sure this icon exists
            .setContentTitle("Increment Counter")
            .setContentText("You have $counter notifications.")
            .setContentIntent(activityPendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground, // Make sure this icon exists
                "Increment",
                incrementIntent
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true) // Optional: dismiss notification when tapped
            .build()

        notificationManager.notify(
            1, // id
            notification
        )
    }

    companion object {
        const val COUNTER_CHANNEL_ID = "counter_channel"
    }
}