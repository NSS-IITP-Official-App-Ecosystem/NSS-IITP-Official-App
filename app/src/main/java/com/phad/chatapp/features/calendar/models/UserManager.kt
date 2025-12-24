package com.phad.chatapp.features.calendar.models

/**
 * Singleton class to manage user information and authentication state.
 */
object UserManager {
    // Default user ID
    const val currentUserId = "user123"
    
    // Admin status - can be toggled for testing
    var isAdmin = true
    
    // Current user role - defaults to admin for testing
    var currentRole = UserRole.ADMIN1
    
    // Get the current user's role
    fun getUserRole(): UserRole {
        return currentRole
    }
    
    // Set user role
    fun setUserRole(role: UserRole) {
        currentRole = role
    }
    
    // Toggle between admin and user for testing
    fun toggleUserRole() {
        isAdmin = !isAdmin
        currentRole = if (isAdmin) UserRole.ADMIN1 else UserRole.USER
    }
} 
