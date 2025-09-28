package com.phad.chatapp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class SessionManager(context: Context) {
    private val pref: SharedPreferences
    private val editor: SharedPreferences.Editor
    private val context: Context
    private val PREF_NAME = "ChatAppPref"
    private val PRIVATE_MODE = 0
    
    // Shared preferences keys
    private val KEY_IS_LOGGED_IN = "isLoggedIn"
    private val KEY_USER_TYPE = "userType"
    private val KEY_USER_ID = "userId"
    private val KEY_USER_NAME = "userName"
    private val KEY_ROLL_NUMBER = "rollNumber"
    
    init {
        this.context = context
        pref = context.getSharedPreferences(PREF_NAME, PRIVATE_MODE)
        editor = pref.edit()
    }
    
    /**
     * Create login session
     */
    fun createLoginSession(userType: String, rollNumber: String, userTypeInt: Int) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putString(KEY_USER_TYPE, userType)
        editor.putInt("userTypeInt", userTypeInt)
        editor.putString(KEY_ROLL_NUMBER, rollNumber)
        editor.apply()
    }
    
    /**
     * Save user ID to shared preferences
     */
    fun saveUserId(userId: String) {
        editor.putString(KEY_USER_ID, userId)
        editor.apply()
    }
    
    /**
     * Save user name to shared preferences
     */
    fun saveUserName(name: String) {
        editor.putString(KEY_USER_NAME, name)
        editor.apply()
    }
    
    /**
     * Get stored user name
     */
    fun getUserName(): String? {
        return pref.getString(KEY_USER_NAME, null)
    }
    
    /**
     * Get stored user ID
     */
    fun getUserId(): String? {
        return pref.getString(KEY_USER_ID, null)
    }
    
    /**
     * Get stored user type as int
     */
    fun getUserType(): Int {
        return pref.getInt("userTypeInt", STUDENT_TYPE)
    }
    
    fun fetchUserType(): String? {
        return pref.getString(KEY_USER_TYPE, null)
    }
    
    /**
     * Get stored roll number
     */
    fun getRollNumber(): String? {
        return pref.getString(KEY_ROLL_NUMBER, null)
    }
    
    /**
     * Check login status
     */
    fun isLoggedIn(): Boolean {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false)
    }
    
    /**
     * Clear user data and logout
     */
    fun logoutUser() {
        // Clear all data from shared preferences
        editor.clear()
        editor.apply()
    }
    
    companion object {
        const val ADMIN_TYPE_1 = 0 // Main admin
        const val ADMIN_TYPE_2 = 2 // Secondary admin
        const val TEACHER_TYPE = 1 // Teacher
        const val STUDENT_TYPE = 3 // Student
    }
} 