package com.example.classseek.models

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val displayName: String = "",
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
    val isOnline: Boolean = false
) {
    val isProfileComplete: Boolean
        get() = name.isNotBlank() && major.isNotBlank()
}

