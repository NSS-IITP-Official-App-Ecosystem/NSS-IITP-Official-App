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
    
    const val WING_TTW = "Teaching and Technical Wing"
    const val WING_PRN = "Prerna Wing"
    const val WING_RDW = "Rural Development Wing"
    const val WING_ENV = "Environmental Wing"
    const val WING_DCW = "Design and Curation Wing"
    
    val NSS_WINGS = listOf(
        WING_TTW,
        WING_PRN,
        WING_RDW,
        WING_ENV,
        WING_DCW
    )

    fun addUserToAnnouncementGroup(userId: String, callback: (Boolean) -> Unit) {
        callback(true)
    }
}