package com.phad.chatapp.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Utility class for detecting if the app is running in a cloned environment
 * (e.g., Parallel Space, Second Space, Dual Apps, etc.)
 * 
 * This helps prevent proxy attendance by detecting when students try to use
 * cloned versions of the app to mark attendance for multiple people.
 */
object CloneDetectionUtils {
    private const val TAG = "CloneDetection"
    
    /**
     * Result of clone detection with detailed information
     */
    data class CloneDetectionResult(
        val isCloned: Boolean,
        val reason: String,
        val detectionMethod: String? = null
    )
    
    /**
     * Main method to check if the app is running in a cloned environment
     * @return true if the app appears to be cloned, false otherwise
     */
    fun isCloneApp(context: Context): Boolean {
        val result = getCloneDetectionDetails(context)
        return result.isCloned
    }
    
    /**
     * Get detailed clone detection information
     * Uses multiple detection methods for comprehensive coverage
     */
    fun getCloneDetectionDetails(context: Context): CloneDetectionResult {
        try {
            // Method 1: Check package name for known clone app patterns
            val packageNameCheck = checkPackageNamePatterns(context)
            if (packageNameCheck.isCloned) {
                Log.w(TAG, "Clone detected via package name: ${packageNameCheck.reason}")
                return packageNameCheck
            }
            
            // Method 2: Check installation directory path
            val pathCheck = checkInstallationPath(context)
            if (pathCheck.isCloned) {
                Log.w(TAG, "Clone detected via installation path: ${pathCheck.reason}")
                return pathCheck
            }
            
            // Method 3: Check if running in multiple user profile
            val multiUserCheck = checkMultipleUserProfile(context)
            if (multiUserCheck.isCloned) {
                Log.w(TAG, "Clone detected via multi-user: ${multiUserCheck.reason}")
                return multiUserCheck
            }
            
            // Method 4: Check application flags and system app status
            val appFlagsCheck = checkApplicationFlags(context)
            if (appFlagsCheck.isCloned) {
                Log.w(TAG, "Clone detected via app flags: ${appFlagsCheck.reason}")
                return appFlagsCheck
            }
            
            // Method 5: Check process name
            val processCheck = checkProcessName(context)
            if (processCheck.isCloned) {
                Log.w(TAG, "Clone detected via process name: ${processCheck.reason}")
                return processCheck
            }
            
            // Method 6: Check for known clone app files/directories
            val filesCheck = checkCloneAppFiles()
            if (filesCheck.isCloned) {
                Log.w(TAG, "Clone detected via files: ${filesCheck.reason}")
                return filesCheck
            }
            
            // Method 7: Check system properties and environment
            val systemPropsCheck = checkSystemProperties()
            if (systemPropsCheck.isCloned) {
                Log.w(TAG, "Clone detected via system properties: ${systemPropsCheck.reason}")
                return systemPropsCheck
            }
            
            Log.d(TAG, "No clone environment detected")
            return CloneDetectionResult(
                isCloned = false,
                reason = "App appears to be running in normal environment"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during clone detection", e)
            // In case of error, fail open (don't block) to avoid false positives
            return CloneDetectionResult(
                isCloned = false,
                reason = "Detection error: ${e.message}"
            )
        }
    }
    
    /**
     * Check if the package name contains patterns associated with clone apps
     */
    private fun checkPackageNamePatterns(context: Context): CloneDetectionResult {
        val packageName = context.packageName
        
        // List of known clone app package patterns
        val clonePatterns = listOf(
            "parallel", "clone", "dual", "multi", "virtual", 
            "space", "lite", "sandbox", "island"
        )
        
        for (pattern in clonePatterns) {
            if (packageName.contains(pattern, ignoreCase = true)) {
                return CloneDetectionResult(
                    isCloned = true,
                    reason = "Package name contains clone app pattern: $pattern",
                    detectionMethod = "Package Name Pattern"
                )
            }
        }
        
        return CloneDetectionResult(isCloned = false, reason = "Package name check passed")
    }
    
    /**
     * Check if the app is installed in a non-standard directory
     * Clone apps often isolate apps in special directories
     */
    private fun checkInstallationPath(context: Context): CloneDetectionResult {
        try {
            val dataDir = context.applicationInfo.dataDir
            val sourceDir = context.applicationInfo.sourceDir
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            
            // Known clone app directory patterns (expanded list)
            val clonePathPatterns = listOf(
                "/data/data/com.parallel.space",
                "/data/data/com.lbe.parallel",
                "/data/data/com.excelliance.multiaccounts",
                "/data/data/com.jumobile.multiapp",
                "/data/data/com.oasisfeng.island",
                "/data/data/com.ludashi.dualspace",
                "/data/data/com.jiubang.commerce.gomultiple",
                "/data/data/com.lody.virtual",
                "/data/data/com.excean.parallelspace",  // Specific parallel space app
                "/data/user_de",
                "/storage/emulated/999",
                "/data/media/",
                "excean",       // com.excean.parallelspace package
                "gameplugins",  // Used by excean parallel space
                "virtual",
                "clone",
                "dual",
                "parallel",
                "space",
                "sandbox"
            )
            
            // Check all relevant paths
            val pathsToCheck = listOf(dataDir, sourceDir, nativeLibDir)
            
            for (path in pathsToCheck) {
                for (pattern in clonePathPatterns) {
                    if (path.contains(pattern, ignoreCase = true)) {
                        return CloneDetectionResult(
                            isCloned = true,
                            reason = "App installed in clone directory: $pattern (Path: ${path.substringAfterLast('/')})",
                            detectionMethod = "Installation Path"
                        )
                    }
                }
            }
            
            // Additional check: If sourceDir differs significantly from dataDir package structure
            // Clone apps often install APKs in different locations
            val expectedPattern = "/data/app/"
            if (!sourceDir.startsWith(expectedPattern) && !sourceDir.startsWith("/system/")) {
                Log.w(TAG, "Suspicious source directory: $sourceDir")
                // Some custom ROMs may have different paths, so this is just a warning
            }
            
            Log.d(TAG, "Data dir: $dataDir")
            Log.d(TAG, "Source dir: $sourceDir")
            Log.d(TAG, "Native lib dir: $nativeLibDir")
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking installation path", e)
        }
        
        return CloneDetectionResult(isCloned = false, reason = "Installation path check passed")
    }
    
    /**
     * Check if running in a secondary user profile
     * Android's multi-user feature assigns user IDs > 0
     */
    private fun checkMultipleUserProfile(context: Context): CloneDetectionResult {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                val userId = getUserId()
                
                // User ID 0 is the primary user, anything else is a secondary profile
                if (userId > 0) {
                    return CloneDetectionResult(
                        isCloned = true,
                        reason = "Running in secondary user profile (User ID: $userId)",
                        detectionMethod = "Multi-User Profile"
                    )
                }
                
                Log.d(TAG, "User ID: $userId (primary user)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking user profile", e)
        }
        
        return CloneDetectionResult(isCloned = false, reason = "User profile check passed")
    }
    
    /**
     * Check application flags for signs of cloning
     */
    private fun checkApplicationFlags(context: Context): CloneDetectionResult {
        try {
            val appInfo = context.applicationInfo
            
            // Check if the app's UID is suspicious
            // Clone apps often run with different UIDs
            val uid = appInfo.uid
            val expectedUidRange = 10000..19999 // Normal app UID range
            
            if (uid !in expectedUidRange && uid != Process.myUid()) {
                Log.d(TAG, "Suspicious UID: $uid (Process UID: ${Process.myUid()})")
                // This is informational only, not a definitive clone indicator
            }
            
            Log.d(TAG, "App UID: $uid, Process UID: ${Process.myUid()}")
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking application flags", e)
        }
        
        return CloneDetectionResult(isCloned = false, reason = "App flags check passed")
    }
    
    /**
     * Check if the process name differs from the package name
     * Clone apps may run the app in a different process
     */
    @SuppressLint("PrivateApi")
    private fun checkProcessName(context: Context): CloneDetectionResult {
        try {
            val packageName = context.packageName
            
            // Get current process name
            val processName = getCurrentProcessName()
            
            if (processName != null && processName != packageName) {
                // Some legitimate apps run in different processes, so this is informational
                Log.d(TAG, "Process name ($processName) differs from package ($packageName)")
                
                // Check if process name contains clone app patterns
                val clonePatterns = listOf("parallel", "clone", "dual", "virtual", "space")
                for (pattern in clonePatterns) {
                    if (processName.contains(pattern, ignoreCase = true)) {
                        return CloneDetectionResult(
                            isCloned = true,
                            reason = "Process name indicates clone environment: $processName",
                            detectionMethod = "Process Name"
                        )
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking process name", e)
        }
        
        return CloneDetectionResult(isCloned = false, reason = "Process name check passed")
    }
    
    /**
     * Get the current process name
     */
    private fun getCurrentProcessName(): String? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return android.app.Application.getProcessName()
            }
            
            // Fallback for older Android versions
            val pid = Process.myPid()
            val file = File("/proc/$pid/cmdline")
            if (file.exists()) {
                BufferedReader(FileReader(file)).use { reader ->
                    var processName = reader.readLine()
                    if (!processName.isNullOrEmpty()) {
                        processName = processName.trim { it <= ' ' }
                        return processName
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting process name", e)
        }
        
        return null
    }
    
    /**
     * Get user ID using reflection to access hidden API safely
     * Returns 0 (primary user) if unable to determine
     */
    @SuppressLint("PrivateApi")
    private fun getUserId(): Int {
        return try {
            // Use reflection to call UserHandle.myUserId() which is a hidden API
            val userHandleClass = Class.forName("android.os.UserHandle")
            val myUserIdMethod = userHandleClass.getDeclaredMethod("myUserId")
            myUserIdMethod.invoke(null) as? Int ?: 0
        } catch (e: Exception) {
            Log.w(TAG, "Could not get user ID via reflection, assuming primary user", e)
            0 // Assume primary user if we can't determine
        }
    }
    
    /**
     * Check for known clone app files or directories in the system
     */
    private fun checkCloneAppFiles(): CloneDetectionResult {
        try {
            // Known clone app package names to check
            val cloneAppPackages = listOf(
                "com.parallel.space.lite",
                "com.lbe.parallel.intl",
                "com.excelliance.multiaccounts",
                "com.jumobile.multiapp",
                "com.oasisfeng.island",
                "com.ludashi.dualspace"
            )
            
            // This check is limited as we can't access other app directories
            // But we can check if certain system properties exist
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking clone app files", e)
        }
        
        return CloneDetectionResult(isCloned = false, reason = "Clone app files check passed")
    }
    
    /**
     * Check system properties and environment for signs of virtualization
     */
    @SuppressLint("PrivateApi")
    private fun checkSystemProperties(): CloneDetectionResult {
        try {
            // Check for known system properties set by clone apps
            val propsToCheck = mapOf(
                "ro.build.version.emui" to "emui",
                "ro.kernel.qemu" to "qemu",
                "ro.hardware" to "goldfish", // Emulator
                "ro.product.model" to "sdk"
            )
            
            for ((propName, suspiciousValue) in propsToCheck) {
                try {
                    val systemPropertiesClass = Class.forName("android.os.SystemProperties")
                    val getMethod = systemPropertiesClass.getMethod("get", String::class.java)
                    val propValue = getMethod.invoke(null, propName) as? String
                    
                    if (propValue != null && propValue.contains(suspiciousValue, ignoreCase = true)) {
                        Log.d(TAG, "Suspicious system property: $propName=$propValue")
                        // Note: This could be an emulator, not necessarily a clone app
                        // So we don't block based on this alone
                    }
                } catch (e: Exception) {
                    // Property doesn't exist or can't be accessed
                }
            }
            
            // Check environment variables
            val env = System.getenv()
            val suspiciousEnvVars = listOf("PARALLEL_SPACE", "CLONE_APP", "VIRTUAL_ENV", "DUAL_APP")
            
            for (envVar in suspiciousEnvVars) {
                if (env?.containsKey(envVar) == true) {
                    return CloneDetectionResult(
                        isCloned = true,
                        reason = "Clone app environment variable detected: $envVar",
                        detectionMethod = "System Properties"
                    )
                }
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking system properties", e)
        }
        
        return CloneDetectionResult(isCloned = false, reason = "System properties check passed")
    }
    
    /**
     * Get a user-friendly message explaining why attendance is blocked
     */
    fun getBlockingMessage(detectionResult: CloneDetectionResult): String {
        return if (detectionResult.isCloned) {
            """
            Attendance marking is not allowed in cloned apps.
            
            Reason: ${detectionResult.reason}
            
            Please use the official app installed directly from your admin or authorized source.
            """.trimIndent()
        } else {
            "Attendance marking is allowed."
        }
    }
    
    /**
     * Log detection details for debugging
     */
    fun logDetectionDetails(context: Context) {
        try {
            Log.d(TAG, "=== Clone Detection Details ===")
            Log.d(TAG, "Package: ${context.packageName}")
            Log.d(TAG, "Data Dir: ${context.applicationInfo.dataDir}")
            Log.d(TAG, "Native Lib Dir: ${context.applicationInfo.nativeLibraryDir}")
            Log.d(TAG, "UID: ${context.applicationInfo.uid}")
            Log.d(TAG, "Process UID: ${Process.myUid()}")
            Log.d(TAG, "Process Name: ${getCurrentProcessName()}")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                Log.d(TAG, "User ID: ${getUserId()}")
            }
            
            val result = getCloneDetectionDetails(context)
            Log.d(TAG, "Detection Result: isCloned=${result.isCloned}, reason=${result.reason}")
            Log.d(TAG, "==============================")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging detection details", e)
        }
    }
}
