package com.example.classseek.ui.calendar

data class EventReminder(
    val eventId: String = "",
    val eventTitle: String = "",
    val eventTime: Long = 0L,
    val reminderMinutes: Int = 15,
    val reminderType: String = "notification",
    val enabled: Boolean = true,
    val notificationSent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class UserReminderPreference(
    val eventId: String = "",
    val selectedMinutes: Int = 15,
    val lastUpdated: Long = System.currentTimeMillis()
)