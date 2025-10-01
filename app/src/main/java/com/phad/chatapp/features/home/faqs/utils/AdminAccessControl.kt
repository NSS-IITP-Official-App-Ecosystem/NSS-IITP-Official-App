package com.phad.chatapp.features.home.faqs.utils

import com.phad.chatapp.utils.SessionManager

/**
 * Utility class for FAQ admin access control
 */
object AdminAccessControl {
    
    /**
     * Check if the current user is an admin who can access FAQ management
     */
    fun isUserAdmin(sessionManager: SessionManager): Boolean {
        val userType = sessionManager.fetchUserType()
        return userType.equals("Admin", ignoreCase = true)
    }
    
    /**
     * Get the user type for admin operations
     */
    fun getUserType(sessionManager: SessionManager): String {
        return sessionManager.fetchUserType()
    }
    
    /**
     * Check if the user is in teaching wing
     */
    fun isTeachingWing(sessionManager: SessionManager): Boolean {
        return sessionManager.getTeachingWing()
    }
    
    /**
     * Get admin info for FAQ management
     */
    fun getAdminInfo(sessionManager: SessionManager): AdminInfo {
        return AdminInfo(
            isAdmin = isUserAdmin(sessionManager),
            userType = getUserType(sessionManager),
            isTeachingWing = isTeachingWing(sessionManager),
            rollNumber = sessionManager.fetchRollNumber() ?: ""
        )
    }
}

/**
 * Data class to hold admin information
 */
data class AdminInfo(
    val isAdmin: Boolean,
    val userType: String,
    val isTeachingWing: Boolean,
    val rollNumber: String
)
