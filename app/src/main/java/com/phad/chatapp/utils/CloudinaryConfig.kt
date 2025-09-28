package com.phad.chatapp.utils

import android.content.Context
import android.util.Log
import com.cloudinary.android.MediaManager
import java.util.HashMap

/**
 * Utility class to handle Cloudinary configuration
 */
object CloudinaryConfig {
    private const val TAG = "CloudinaryConfig"
    private var isInitialized = false

    /**
     * Initialize Cloudinary with the required configuration
     * @param context Application context
     * @return true if initialization was successful, false otherwise
     */
    fun init(context: Context): Boolean {
        if (isInitialized) {
            return true
        }

        return try {
            val config = HashMap<String, String>()
            
            // TODO: Replace with your actual Cloudinary credentials from your Cloudinary dashboard
            // 1. Sign up at https://cloudinary.com/users/register/free if you don't have an account
            // 2. Get your credentials from the Dashboard: https://cloudinary.com/console
            config["cloud_name"] = "ds58ghwrz" // e.g., "mycompany"
            config["api_key"] = "135458374549655" // e.g., "123456789012345"
            config["api_secret"] = "eWpBglyGZROt7TgvsqZFR5XJQAI" // e.g., "abcdefghijklmnopqrstuvwxyz12"
            
            MediaManager.init(context, config)
            isInitialized = true
            Log.d(TAG, "Cloudinary initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Cloudinary: ${e.message}", e)
            false
        }
    }

    /**
     * Check if Cloudinary is initialized
     * @return true if initialized, false otherwise
     */
    fun isInitialized(): Boolean {
        return isInitialized
    }
} 