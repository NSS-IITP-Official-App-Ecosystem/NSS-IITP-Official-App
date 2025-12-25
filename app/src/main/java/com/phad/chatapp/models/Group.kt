package com.phad.chatapp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.Exclude
import java.util.Date

data class Group(
    var id: String = "",
    var name: String = "",
    var description: String = "Group chat",
    val participants: List<String> = emptyList(),
    val admins: List<String> = emptyList(),
    val createdBy: String = "",
    
    // Map of user IDs to boolean values indicating whether they can send messages
    @PropertyName("messagingPermissions")
    val messagingPermissions: MutableMap<String, Boolean> = mutableMapOf(),
    
    // Use Timestamp for Firestore compatibility
    @PropertyName("createdAt")
    var createdAt: Timestamp? = null,
    
    @get:PropertyName("subject") @set:PropertyName("subject")
    var subject: Boolean = false,
    @get:PropertyName("update") @set:PropertyName("update")
    var update: Boolean = false,
    var pendingAdd: List<String> = emptyList(),
    var pendingRemove: List<String> = emptyList()
) {
    @get:Exclude
    var groupId: String
        get() = id
        set(value) { id = value }
        
    // Empty constructor for Firestore
    constructor() : this("", "", "", emptyList(), emptyList(), "", mutableMapOf(), null, false, false, emptyList(), emptyList())
    
    // Convenience method to get the timestamp as millis
    @Exclude
    fun getCreatedAtMillis(): Long {
        return createdAt?.toDate()?.time ?: System.currentTimeMillis()
    }
    
    // Alternative setter for when we want to use a Long (e.g., when creating a new group)
    @Exclude
    fun setCreatedAtMillis(timeMillis: Long) {
        this.createdAt = Timestamp(Date(timeMillis))
    }
    
    // Check if a user is an admin of this group
    @Exclude
    fun isUserAdmin(userId: String): Boolean {
        return userId in admins || userId == createdBy
    }
    
    // Check if a user has permission to send messages in this group
    @Exclude
    fun canUserSendMessages(userId: String): Boolean {
        // If permissions are not set yet, default to true
        if (messagingPermissions.isEmpty()) {
            return true
        }
        
        // Get permission for the user or default to false if not specified
        return messagingPermissions[userId] ?: false
    }
    
    // Initialize messaging permissions for all participants
    @Exclude
    fun initializeMessagingPermissions() {
        participants.forEach { userId ->
            if (!messagingPermissions.containsKey(userId)) {
                messagingPermissions[userId] = true
            }
        }
    }
} 