package com.example.classseek.Notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.classseek.R
import com.example.classseek.ui.ClassSeekActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMsgService"
        private const val CHANNEL_ID = "classseek_notifications"
        private const val CHANNEL_NAME = "ClassSeek Notifications"

        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_CHAT_TITLE = "chat_title"
        const val EXTRA_FRIEND_REQUEST_UID = "requester_uid"
        const val EXTRA_NAVIGATE_TO = "navigate_to"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")
        Log.d(TAG, "Data payload: ${remoteMessage.data}")

        val data = remoteMessage.data

        // Handle different notification types
        when (data["type"]) {
            "event_reminder" -> {
                val eventId = data["eventId"]
                val title = data["title"] ?: remoteMessage.notification?.title ?: "Event Reminder"
                val body = data["body"] ?: remoteMessage.notification?.body ?: ""

                sendReminderNotification(
                    eventId = eventId ?: "",
                    title = title,
                    messageBody = body
                )
            }
            "event_shared" -> {
                val eventTitle = data["eventTitle"] ?: "Event"
                val senderName = data["senderName"] ?: "Someone"

                sendEventSharedNotification(
                    title = "📅 Event Shared",
                    messageBody = "$senderName shared an event: $eventTitle"
                )
            }

            "friend_request" -> {
                val requesterUid = data["requesterUid"]
                val title = data["title"] ?: remoteMessage.notification?.title ?: "Friend Request"
                val body = data["body"] ?: remoteMessage.notification?.body ?: ""

                sendFriendRequestNotification(
                    requesterUid = requesterUid ?: "",
                    title = title,
                    messageBody = body
                )
            }

            "friend_request_accepted" -> {
                val title = data["title"] ?: remoteMessage.notification?.title ?: "Friend Request Accepted"
                val body = data["body"] ?: remoteMessage.notification?.body ?: ""

                sendFriendRequestAcceptedNotification(
                    title = title,
                    messageBody = body
                )
            }

            else -> {
                // Existing notification handling
                val chatId = data["chatId"]
                val title = data["title"] ?: remoteMessage.notification?.title ?: "ClassSeek"
                val body = data["body"] ?: remoteMessage.notification?.body ?: ""
                val chatTitle = data["chatTitle"] ?: title

                if (!chatId.isNullOrBlank()) {
                    sendChatNotification(
                        chatId = chatId,
                        chatTitle = chatTitle,
                        title = title,
                        messageBody = body
                    )
                } else if (body.isNotBlank()) {
                    sendNotification(
                        title = title,
                        messageBody = body
                    )
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            Log.d(TAG, "No signed-in user, skipping token save")
            return
        }

        val db = FirebaseFirestore.getInstance()

        val deviceDoc = mapOf(
            "token" to token,
            "platform" to "android",
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("users")
            .document(user.uid)
            .collection("devices")
            .document(token)
            .set(deviceDoc)
            .addOnSuccessListener {
                Log.d(TAG, "FCM token saved successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save FCM token", e)
            }
    }

    private fun sendChatNotification(
        chatId: String,
        chatTitle: String,
        title: String,
        messageBody: String
    ) {
        val intent = Intent(this, ClassSeekActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_CHAT_TITLE, chatTitle)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            chatId.hashCode(),
            intent,
            pendingIntentFlags
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannelIfNeeded(notificationManager)

        notificationManager.notify(chatId.hashCode(), notificationBuilder.build())
    }

    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, ClassSeekActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            pendingIntentFlags
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannelIfNeeded(notificationManager)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun sendReminderNotification(
        eventId: String,
        title: String,
        messageBody: String
    ) {
        val intent = Intent(this, ClassSeekActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("event_id", eventId)
            putExtra("navigate_to", "calendar")
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            eventId.hashCode(),
            intent,
            pendingIntentFlags
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.drawable.ic_notification,
                "Dismiss",
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, ClassSeekActivity::class.java),
                    pendingIntentFlags
                )
            )

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannelIfNeeded(notificationManager)

        notificationManager.notify(eventId.hashCode(), notificationBuilder.build())
    }

    private fun sendEventSharedNotification(
        title: String,
        messageBody: String
    ) {
        val intent = Intent(this, ClassSeekActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("navigate_to", "calendar")
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            pendingIntentFlags
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannelIfNeeded(notificationManager)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun sendFriendRequestNotification(
        requesterUid: String,
        title: String,
        messageBody: String
    ) {
        val intent = Intent(this, ClassSeekActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_FRIEND_REQUEST_UID, requesterUid)
            putExtra(EXTRA_NAVIGATE_TO, "friends")
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            requesterUid.hashCode(),
            intent,
            pendingIntentFlags
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannelIfNeeded(notificationManager)

        notificationManager.notify(requesterUid.hashCode(), notificationBuilder.build())
    }

    private fun sendFriendRequestAcceptedNotification(
        title: String,
        messageBody: String
    ) {
        val intent = Intent(this, ClassSeekActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_NAVIGATE_TO, "friends")
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            pendingIntentFlags
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannelIfNeeded(notificationManager)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun createNotificationChannelIfNeeded(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "ClassSeek push notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}