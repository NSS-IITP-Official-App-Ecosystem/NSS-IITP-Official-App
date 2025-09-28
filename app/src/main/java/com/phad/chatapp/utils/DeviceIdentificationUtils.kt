package com.phad.chatapp.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.security.MessageDigest
import java.util.*

/**
 * Utility class for generating and managing unique device identifiers
 * Uses multiple device characteristics to create a persistent device fingerprint
 * that survives app reinstalls and user account changes
 */
object DeviceIdentificationUtils {
    private const val TAG = "DeviceIdentification"
    private const val PREFS_NAME = "device_identification_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_ID_VERSION = "device_id_version"
    private const val CURRENT_VERSION = 1
    
    @Volatile
    private var cachedDeviceId: String? = null
    
    /**
     * Get the unique device identifier for this device
     * This method ensures the device ID is consistent across app sessions and user logins
     */
    fun getDeviceId(context: Context): String {
        // Return cached value if available
        cachedDeviceId?.let { return it }
        
        synchronized(this) {
            // Double-check after acquiring lock
            cachedDeviceId?.let { return it }
            
            val prefs = getPreferences(context)
            val storedDeviceId = prefs.getString(KEY_DEVICE_ID, null)
            val storedVersion = prefs.getInt(KEY_DEVICE_ID_VERSION, 0)
            
            // Use stored device ID if it exists and is current version
            if (!storedDeviceId.isNullOrEmpty() && storedVersion == CURRENT_VERSION) {
                Log.d(TAG, "Using stored device ID: ${storedDeviceId.take(8)}...")
                cachedDeviceId = storedDeviceId
                return storedDeviceId
            }
            
            // Generate new device ID
            val newDeviceId = generateDeviceId(context)
            
            // Store the new device ID
            prefs.edit()
                .putString(KEY_DEVICE_ID, newDeviceId)
                .putInt(KEY_DEVICE_ID_VERSION, CURRENT_VERSION)
                .apply()
            
            Log.d(TAG, "Generated and stored new device ID: ${newDeviceId.take(8)}...")
            cachedDeviceId = newDeviceId
            return newDeviceId
        }
    }
    
    /**
     * Generate a unique device identifier using multiple device characteristics
     */
    @SuppressLint("HardwareIds")
    private fun generateDeviceId(context: Context): String {
        val deviceCharacteristics = mutableListOf<String>()
        
        try {
            // Primary identifier: Android ID (most reliable)
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") { // Avoid known bad Android ID
                deviceCharacteristics.add("android_id:$androidId")
                Log.d(TAG, "Added Android ID to device fingerprint")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get Android ID", e)
        }
        
        try {
            // Hardware characteristics
            deviceCharacteristics.add("manufacturer:${Build.MANUFACTURER}")
            deviceCharacteristics.add("model:${Build.MODEL}")
            deviceCharacteristics.add("device:${Build.DEVICE}")
            deviceCharacteristics.add("board:${Build.BOARD}")
            deviceCharacteristics.add("hardware:${Build.HARDWARE}")
            deviceCharacteristics.add("product:${Build.PRODUCT}")
            Log.d(TAG, "Added hardware characteristics to device fingerprint")
        } catch (e: Exception) {
            Log.w(TAG, "Could not get hardware characteristics", e)
        }
        
        try {
            // Build characteristics
            deviceCharacteristics.add("fingerprint:${Build.FINGERPRINT}")
            deviceCharacteristics.add("serial:${getSerialNumber()}")
            Log.d(TAG, "Added build characteristics to device fingerprint")
        } catch (e: Exception) {
            Log.w(TAG, "Could not get build characteristics", e)
        }
        
        // Fallback: Generate random UUID if no characteristics available
        if (deviceCharacteristics.isEmpty()) {
            Log.w(TAG, "No device characteristics available, using random UUID")
            deviceCharacteristics.add("random:${UUID.randomUUID()}")
        }
        
        // Combine all characteristics and hash them
        val combinedCharacteristics = deviceCharacteristics.joinToString("|")
        val deviceId = hashString(combinedCharacteristics)
        
        Log.d(TAG, "Generated device ID from ${deviceCharacteristics.size} characteristics")
        return deviceId
    }
    
    /**
     * Get device serial number with proper API level handling
     */
    @SuppressLint("HardwareIds")
    private fun getSerialNumber(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Build.getSerial()
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get serial number", e)
            "unknown"
        }
    }
    
    /**
     * Hash a string using SHA-256
     */
    private fun hashString(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error hashing string", e)
            // Fallback to simple hash
            input.hashCode().toString()
        }
    }
    
    /**
     * Get SharedPreferences for device identification
     */
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Clear stored device ID (for testing purposes)
     */
    fun clearStoredDeviceId(context: Context) {
        synchronized(this) {
            getPreferences(context).edit().clear().apply()
            cachedDeviceId = null
            Log.d(TAG, "Cleared stored device ID")
        }
    }
    
    /**
     * Get device information for debugging
     */
    fun getDeviceInfo(context: Context): Map<String, String> {
        val info = mutableMapOf<String, String>()
        
        try {
            info["device_id"] = getDeviceId(context).take(16) + "..."
            info["android_id"] = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "null"
            info["manufacturer"] = Build.MANUFACTURER
            info["model"] = Build.MODEL
            info["device"] = Build.DEVICE
            info["serial"] = getSerialNumber().take(8) + "..."
            info["sdk_int"] = Build.VERSION.SDK_INT.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device info", e)
            info["error"] = e.message ?: "Unknown error"
        }
        
        return info
    }
    
    /**
     * Validate that device ID is properly formatted
     */
    fun isValidDeviceId(deviceId: String?): Boolean {
        return !deviceId.isNullOrEmpty() && 
               deviceId.length >= 32 && // SHA-256 hash should be at least 32 chars
               deviceId.matches(Regex("^[a-f0-9]+$")) // Should be hex string
    }
}
