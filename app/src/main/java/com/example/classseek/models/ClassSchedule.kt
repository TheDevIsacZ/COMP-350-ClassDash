package com.example.classseek.models

data class ClassSchedule(
    val className: String,
    val daysOfWeek: List<Int>, // 1 for Monday, 2 for Tuesday, etc.
    val startTime: String, // "hh:mm a"
    val endTime: String,   // "hh:mm a"
    val location: String = "",
    val startDate: Long,    // timestamp
    val endDate: Long,       // timestamp
    val reminders: List<Int> = emptyList() // list of minutes before event
)
