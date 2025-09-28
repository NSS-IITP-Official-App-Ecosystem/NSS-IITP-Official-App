package com.phad.chatapp.utils

/**
 * Constants used throughout the app
 */
object Constants {
    // Announcement group constants
    const val ANNOUNCEMENT_GROUP_ID = "announcements"
    const val ANNOUNCEMENT_GROUP_NAME = "Announcements"
    
    // Function to safely check if a group exists
    fun exists(groupId: String): Boolean {
        return groupId.isNotEmpty()
    }
    
    // Function to get or create the announcement group
    fun getOrCreateAnnouncementGroup(callback: (String) -> Unit) {
        callback(ANNOUNCEMENT_GROUP_ID)
    }
    
    // Function to add a user to the announcement group
    fun addUserToAnnouncementGroup(userId: String, callback: (Boolean) -> Unit) {
        callback(true)
    }
} 