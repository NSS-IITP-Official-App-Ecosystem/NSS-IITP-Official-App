package com.phad.chatapp.features.calendar.utils

import android.content.Context
import android.util.Log

/**
 * Calendar module's session manager to interface with the app's SessionManager
 * Uses reflection to access the app's SessionManager methods
 */
class CalendarSessionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CalendarSessionManager"
        private var overrideUserType: String? = null
    }
    
    /**
     * Fetch user type from the app's SessionManager
     * Returns empty string if not found or error occurs
     */
    fun fetchUserType(): String {
        // If there's an override user type set, return it
        overrideUserType?.let { return it }
        
        return try {
            // Use reflection to get the app's SessionManager class
            val sessionManagerClass = Class.forName("com.phad.chatapp.utils.SessionManager")
            
            // Create an instance of SessionManager
            val constructor = sessionManagerClass.getConstructor(Context::class.java)
            val sessionManager = constructor.newInstance(context)
            
            // Call fetchUserType method
            val fetchUserTypeMethod = sessionManagerClass.getMethod("fetchUserType")
            val result = fetchUserTypeMethod.invoke(sessionManager) as? String ?: ""
            
            Log.d(TAG, "Successfully fetched user type: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing app's SessionManager: ${e.message}")
            e.printStackTrace()
            ""
        }
    }
    
    /**
     * Set an override user type for testing purposes
     * This will bypass the app's SessionManager
     */
    fun setOverrideUserType(userType: String) {
        overrideUserType = userType
        Log.d(TAG, "Set override user type to: $userType")
    }
} 
