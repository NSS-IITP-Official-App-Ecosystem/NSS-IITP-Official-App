package com.phad.chatapp.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.ui.profile.ProfileUiState
import com.phad.chatapp.services.TokenRefreshService
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

/**
 * Session management for user login state
 * Stores login information in SharedPreferences and integrates with Firebase Auth
 */
class SessionManager(context: Context) {
    private val TAG = "SessionManager"
    
    // Shared preferences file name
    private val PREF_NAME = "ChatAppSession"
    
    // SharedPreferences and Editor
    private val pref: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = pref.edit()
    
    // Context for service management
    private val context: Context = context.applicationContext
    
    // Public variables
    companion object {
        // Shared preferences keys
        const val KEY_IS_LOGGED_IN = "isLoggedIn"
        const val KEY_USER_TYPE = "userType"
        const val KEY_USER_ROLL_NUMBER = "userRollNumber"
        const val KEY_USER_YEAR = "userYear"
        const val KEY_USER_NAME = "userName"
        const val KEY_LOCATION = "location"
        const val KEY_EMAIL = "email"
        const val KEY_PHONE = "phone"
        const val KEY_COLLEGE_EMAIL = "collegeEmail"
        const val KEY_ACADEMIC_GROUP = "academicGroup"
        const val KEY_NSS_GROUP = "nssGroup"
        const val KEY_TOPIC_1 = "topic1"
        const val KEY_TOPIC_2 = "topic2"
        const val KEY_TOPIC_3 = "topic3"
        const val KEY_IS_STUDENT = "isStudent"
        const val KEY_TEACHING_WING = "teachingWing" // Add this line
        const val KEY_BYPASS_FIREBASE_AUTH = "bypassFirebaseAuth"
        const val KEY_ATTENDANCE_STATS = "attendanceStats"
        const val KEY_LAST_INTERFACE = "lastInterfaceChoice" // Add this line
    }
    
    /**
     * Create login session with user information
     */
    fun createLoginSession(userType: String, userRollNumber: String, userYear: Int) {
        Log.d(TAG, "Creating login session for: userType=$userType, userRoll=$userRollNumber")
        
        // Store login state and user info
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putString(KEY_USER_TYPE, userType)
        editor.putString(KEY_USER_ROLL_NUMBER, userRollNumber)
        editor.putInt(KEY_USER_YEAR, 0)
        
        // Commit changes
        editor.apply()
        
        // Verify data was stored correctly
        val savedUserType = pref.getString(KEY_USER_TYPE, null)
        Log.d(TAG, "Verified saved data: userType=$savedUserType")
        
        // Test Firebase authentication state
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser != null) {
            Log.d(TAG, "Firebase user is authenticated: ${firebaseUser.email}")
            // Start token refresh service for automatic token renewal
            startTokenRefreshService()
        } else {
            Log.w(TAG, "Firebase user is NOT authenticated!")
        }
    }
    
    /**
     * Emergency bypass for Firebase authentication issues
     * This should ONLY be used for testing/debugging
     */
    fun enableFirebaseAuthBypass(enable: Boolean) {
        Log.w(TAG, "Setting Firebase auth bypass to: $enable")
        editor.putBoolean(KEY_BYPASS_FIREBASE_AUTH, enable)
        editor.apply()
    }
    
    /**
     * Test if we can access Firestore with the current authentication
     */
    // Removed test connection diagnostics
    
    /**
     * Save user name to shared preferences
     */
    fun saveUserName(userName: String) {
        editor.putString(KEY_USER_NAME, userName)
        editor.apply()
        Log.d(TAG, "Saved user name: $userName")
    }
    
    /**
     * Fetch user name from shared preferences
     */
    fun fetchUserName(): String {
        return pref.getString(KEY_USER_NAME, "") ?: ""
    }
    
    /**
     * Get stored session data
     */
    fun getUserDetails(): HashMap<String, Any?> {
        val user = HashMap<String, Any?>()
        
        // Get stored user info
        val userType = pref.getString(KEY_USER_TYPE, null)
        val userRoll = pref.getString(KEY_USER_ROLL_NUMBER, null)
        val userYear = pref.getInt(KEY_USER_YEAR, 0)
        val userName = pref.getString(KEY_USER_NAME, null)
        
        user[KEY_USER_TYPE] = userType
        user[KEY_USER_ROLL_NUMBER] = userRoll
        user[KEY_USER_YEAR] = userYear
        user[KEY_USER_NAME] = userName
        
        Log.d(TAG, "Retrieved user details: userType=$userType, userRoll=$userRoll, userYear=$userYear, userName=$userName")
        
        return user
    }
    
    /**
     * Fetch the user's ID (roll number)
     */
    fun fetchUserId(): String {
        return pref.getString(KEY_USER_ROLL_NUMBER, "") ?: ""
    }
    
    /**
     * Fetch user type
     */
    fun fetchUserType(): String {
        return pref.getString(KEY_USER_TYPE, "") ?: ""
    }
    
    /**
     * Fetch isStudent flag
     */
    fun fetchIsStudent(): Boolean {
        return pref.getBoolean(KEY_IS_STUDENT, true) // Default to true (student)
    }

    /**
     * Fetch Teaching Wing eligibility flag
     */
    fun getTeachingWing(): Boolean {
        return pref.getBoolean(KEY_TEACHING_WING, false)
    }

    /**
     * Fetch user roll number
     */
    fun fetchRollNumber(): String? {
        return pref.getString(KEY_USER_ROLL_NUMBER, null)
    }
    
    /**
     * Check login status
     */
    fun isLoggedIn(): Boolean {
        // Get bypass setting
        val bypassAuth = pref.getBoolean(KEY_BYPASS_FIREBASE_AUTH, false)
        if (bypassAuth) {
            Log.w(TAG, "⚠️ USING AUTHENTICATION BYPASS! This is for testing only!")
            return pref.getBoolean(KEY_IS_LOGGED_IN, false)
        }
        
        // Check if user is logged in both in SharedPreferences and in Firebase Auth
        val isLoggedInPref = pref.getBoolean(KEY_IS_LOGGED_IN, false)
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val isLoggedInFirebase = firebaseUser != null
        
        // Log detailed information about auth state
        if (isLoggedInFirebase && firebaseUser != null) {
            Log.d(TAG, "Firebase auth state: User=${firebaseUser.email}, UID=${firebaseUser.uid}")
            
            // Check if token is expired 
            firebaseUser.getIdToken(false)
                .addOnSuccessListener { result ->
                    val validToken = result.token != null
                    Log.d(TAG, "Token check: valid=$validToken")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get ID token", e)
                }
        } else {
            Log.d(TAG, "Firebase auth state: Not authenticated")
        }
        
        Log.d(TAG, "Login status: SharedPrefs=$isLoggedInPref, Firebase=$isLoggedInFirebase")
        
        // Both must be true for the user to be considered logged in
        return isLoggedInPref && isLoggedInFirebase
    }
    
    /**
     * Clear session details
     */
    fun logoutUser() {
        Log.d(TAG, "Logging out user")
        
        // Stop token refresh service
        stopTokenRefreshService()
        
        // Sign out from Firebase Auth
        FirebaseAuth.getInstance().signOut()
        
        // Clear all data from SharedPreferences
        editor.clear()
        editor.apply()
    }
    
    /**
     * Start token refresh service for automatic Firebase token renewal
     */
    fun startTokenRefreshService() {
        try {
            Log.d(TAG, "Starting token refresh service")
            TokenRefreshService.startService(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start token refresh service", e)
        }
    }
    
    /**
     * Stop token refresh service
     */
    fun stopTokenRefreshService() {
        try {
            Log.d(TAG, "Stopping token refresh service")
            TokenRefreshService.stopService(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop token refresh service", e)
        }
    }
    
    /**
     * Manually refresh Firebase token (for immediate refresh if needed)
     */
    suspend fun refreshFirebaseToken(): Boolean {
        return try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Log.w(TAG, "No Firebase user found for token refresh")
                return false
            }
            
            Log.d(TAG, "Manually refreshing Firebase token")
            val tokenResult = currentUser.getIdToken(true).await()
            val newToken = tokenResult.token
            
            if (newToken != null) {
                Log.d(TAG, "✅ Firebase token refreshed successfully")
                true
            } else {
                Log.w(TAG, "⚠️ Token refresh returned null")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to manually refresh Firebase token", e)
            false
        }
    }
    
    /**
     * Check if Firebase token is valid and refresh if needed
     */
    suspend fun ensureValidFirebaseToken(): Boolean {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w(TAG, "No Firebase user found")
            return false
        }
        
        return try {
            // Try to get token without forcing refresh first
            val tokenResult = currentUser.getIdToken(false).await()
            if (tokenResult.token != null) {
                Log.d(TAG, "Firebase token is valid")
                true
            } else {
                Log.w(TAG, "Firebase token is null, attempting refresh")
                refreshFirebaseToken()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase token check failed, attempting refresh", e)
            refreshFirebaseToken()
        }
    }

    fun createProfileSession(profile: ProfileUiState) {
        Log.d(TAG, "Saving profile to session for: ${profile.name}")
        editor.putString(KEY_USER_NAME, profile.name)
        editor.putString(KEY_LOCATION, profile.location)
        editor.putString(KEY_EMAIL, profile.email)
        editor.putString(KEY_PHONE, profile.phone)
        editor.putString(KEY_USER_ROLL_NUMBER, profile.rollNumber)
        editor.putString(KEY_COLLEGE_EMAIL, profile.collegeEmail)
        editor.putString(KEY_ACADEMIC_GROUP, profile.academicGroup)
        editor.putString(KEY_NSS_GROUP, profile.nssGroup)
        editor.putString(KEY_TOPIC_1, profile.topic1)
        editor.putString(KEY_TOPIC_2, profile.topic2)
        editor.putString(KEY_TOPIC_3, profile.topic3)
        editor.putBoolean(KEY_IS_STUDENT, profile.isStudent)
        editor.putString(KEY_USER_TYPE, profile.userType) // Add this line
        editor.putBoolean(KEY_TEACHING_WING, profile.Teaching_wing) // Add this line
        editor.apply()
    }
    
    /**
     * Get stored session data
     */
    fun getProfileFromSession(): ProfileUiState {
        return ProfileUiState(
            name = pref.getString(KEY_USER_NAME, "...") ?: "...",
            location = pref.getString(KEY_LOCATION, "...") ?: "...",
            email = pref.getString(KEY_EMAIL, "...") ?: "...",
            phone = pref.getString(KEY_PHONE, "...") ?: "...",
            rollNumber = pref.getString(KEY_USER_ROLL_NUMBER, "...") ?: "...",
            collegeEmail = pref.getString(KEY_COLLEGE_EMAIL, "...") ?: "...",
            academicGroup = pref.getString(KEY_ACADEMIC_GROUP, "...") ?: "...",
            nssGroup = pref.getString(KEY_NSS_GROUP, "...") ?: "...",
            topic1 = pref.getString(KEY_TOPIC_1, "...") ?: "...",
            topic2 = pref.getString(KEY_TOPIC_2, "...") ?: "...",
            topic3 = pref.getString(KEY_TOPIC_3, "...") ?: "...",
            isStudent = pref.getBoolean(KEY_IS_STUDENT, true),
            userType = pref.getString(KEY_USER_TYPE, "Student") ?: "Student", // Add this line
            Teaching_wing = pref.getBoolean(KEY_TEACHING_WING, false) // Add this line
        )
    }

    /**
     * Save attendance stats string (e.g., "3/10" or "-/10")
     */
    fun saveAttendanceStats(stats: String) {
        editor.putString(KEY_ATTENDANCE_STATS, stats)
        editor.apply()
    }

    /**
     * Fetch attendance stats string
     */
    fun fetchAttendanceStats(): String {
        return pref.getString(KEY_ATTENDANCE_STATS, "-/0") ?: "-/0"
    }

    fun setLastInterfaceChoice(choice: String) {
        editor.putString(KEY_LAST_INTERFACE, choice)
        editor.apply()
    }

    fun getLastInterfaceChoice(): String? {
        return pref.getString(KEY_LAST_INTERFACE, null)
    }
} 