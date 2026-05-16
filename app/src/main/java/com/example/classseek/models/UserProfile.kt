package com.example.classseek.models

data class ClassInfo(
    val className: String = "",
    val building: String = "",
    val roomNumber: String = "",
    val dayOfWeek: String = "",
    val startTime: String = "",
    val endTime: String = ""
)

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val major: String = "",
    val bio: String = "",
    val profilePictureUrl: String = "",
    val bannerUrl: String = "",
    val location: String = "",
    val githubUrl: String = "",
    val joinDate: String = "",
    val followersCount: String = "0",
    val followingCount: String = "0",
    val bookmarkedEventIds: List<String> = emptyList(),
    val semester: String = "",
    val classes: List<ClassInfo> = emptyList(),
    val chatNotificationsEnabled: Boolean = true,
    val calendarRemindersEnabled: Boolean = true,
    val eventNotificationsEnabled: Boolean = true,
    val friendRequestNotificationsEnabled: Boolean = true,
    val showOnlineStatus: Boolean = true,
    val profileVisibility: String = "Public", // "Public" or "Friends Only"
    val shareLocation: Boolean = true
) {
    val isProfileComplete: Boolean
        get() = name.isNotBlank() && major.isNotBlank()
}
