package com.example.classseek.Notification

data class NotificationPreferences(
    val messagesEnabled: Boolean = true,
    val eventRemindersEnabled: Boolean = true,
    val eventSharedEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true
)
