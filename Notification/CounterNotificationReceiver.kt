package com.example.classseek.Notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CounterNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {
            val service = CounterNotificationService(it)
            service.showNotification(++NotificationCounter.value)
        }
    }
}