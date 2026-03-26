package com.example.classseek.models

data class ClassSchedule(
    val className: String,
    val daysOfWeek: List<Int>, // 1 for Monday, 2 for Tuesday, etc.
    val startTime: String, // "HH:mm"
    val endTime: String,   // "HH:mm"
    val location: String = "",
    val startDate: Long,    // timestamp
    val endDate: Long       // timestamp
)
