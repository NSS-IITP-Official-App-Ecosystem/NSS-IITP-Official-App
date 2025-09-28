package com.phad.chatapp.models

/**
 * Model class to represent a user in the chat interface user avatars
 */
data class AdminForChat(
    val rollNumber: String = "",
    val name: String = "",
    val userType: String = "",
    val imageUrl: String = "",
    val hasImportantMessages: Boolean = false,
    val hasUnreadMessages: Boolean = false
) 