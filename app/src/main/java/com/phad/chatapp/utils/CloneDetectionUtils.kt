package com.phad.chatapp.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.security.MessageDigest

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
            // Method 0: APK Signature Verification (MOST IMPORTANT - Check first!)
            // This is the most reliable method as cloners cannot replicate your signing certificate
            val signatureCheck = checkAppSignature(context)
            if (signatureCheck.isCloned) {
                Log.w(TAG, "Clone detected via signature: ${signatureCheck.reason}")
                logCloneDetectionEvent(context, signatureCheck)
                return signatureCheck
            }
            
            // Method 1: Installer Package Verification
            val installerCheck = checkInstallerPackage(context)
            if (installerCheck.isCloned) {
                Log.w(TAG, "Clone detected via installer: ${installerCheck.reason}")
                logCloneDetectionEvent(context, installerCheck)
                return installerCheck
            }

            // Method 2: File Token Consistency Check (New)
            val tokenCheck = checkFileTokenConsistency(context)
            if (tokenCheck.isCloned) {
                 Log.w(TAG, "Clone detected via token consistency: ${tokenCheck.reason}")
                 logCloneDetectionEvent(context, tokenCheck)
                 return tokenCheck
            }
            
            // Method 3: Native Library Detection
            val nativeLibCheck = checkNativeLibraries(context)
            if (nativeLibCheck.isCloned) {
                Log.w(TAG, "Clone detected via native libraries: ${nativeLibCheck.reason}")
                logCloneDetectionEvent(context, nativeLibCheck)
                return nativeLibCheck
            }
            
            // Method 3: ClassLoader Detection
            val classLoaderCheck = checkClassLoader()
            if (classLoaderCheck.isCloned) {
                Log.w(TAG, "Clone detected via ClassLoader: ${classLoaderCheck.reason}")
                logCloneDetectionEvent(context, classLoaderCheck)
                return classLoaderCheck
            }
            
            // Method 4: Stack Trace Analysis
            val stackTraceCheck = checkStackTrace()
            if (stackTraceCheck.isCloned) {
                Log.w(TAG, "Clone detected via stack trace: ${stackTraceCheck.reason}")
                logCloneDetectionEvent(context, stackTraceCheck)
                return stackTraceCheck
            }
            
            // Method 5: Hardware Consistency Check
            val hardwareCheck = checkHardwareConsistency(context)
            if (hardwareCheck.isCloned) {
                Log.w(TAG, "Clone detected via hardware check: ${hardwareCheck.reason}")
                logCloneDetectionEvent(context, hardwareCheck)
                return hardwareCheck
            }
            
            // Method 6: Check for installed cloner apps on device
            // Some sophisticated cloners preserve signature but are detectable by their presence
            val clonerAppsCheck = checkForInstalledClonerApps(context)
            if (clonerAppsCheck.isCloned) {
                Log.w(TAG, "Clone detected via installed cloner apps: ${clonerAppsCheck.reason}")
                logCloneDetectionEvent(context, clonerAppsCheck)
                return clonerAppsCheck
            }

            // Method 7: Check Memory Maps for suspicious hooking libraries
            // This detects the actual engine (Xposed, Frida, etc.) even if package name is spoofed
            val memoryMapCheck = checkMemoryMaps()
            if (memoryMapCheck.isCloned) {
                // Allow official emulators (Android Studio) to bypass this specific check
                // This prevents development/testing blocks while keeping the check active for real devices
                if (isOfficialEmulator()) {
                     Log.i(TAG, "Official emulator detected: Ignoring memory map check failure (Result: ${memoryMapCheck.reason})")
                } else {
                    Log.w(TAG, "Clone detected via memory maps: ${memoryMapCheck.reason}")
                    logCloneDetectionEvent(context, memoryMapCheck)
                    return memoryMapCheck
                }
            }

            // Method 8: Check Shared User ID
            val uidCheck = checkSharedUid(context)
            if (uidCheck.isCloned) {
                 Log.w(TAG, "Clone detected via Shared UID: ${uidCheck.reason}")
                 logCloneDetectionEvent(context, uidCheck)
                 return uidCheck
            }

            
            // Method 9: Check package name for known clone app patterns
            val packageNameCheck = checkPackageNamePatterns(context)
            if (packageNameCheck.isCloned) {
                Log.w(TAG, "Clone detected via package name: ${packageNameCheck.reason}")
                return packageNameCheck
            }
            
            // Method 10: Check installation directory path
            val pathCheck = checkInstallationPath(context)
            if (pathCheck.isCloned) {
                Log.w(TAG, "Clone detected via installation path: ${pathCheck.reason}")
                return pathCheck
            }
            
            // Method 11: Check if running in multiple user profile
            val multiUserCheck = checkMultipleUserProfile(context)
            if (multiUserCheck.isCloned) {
                Log.w(TAG, "Clone detected via multi-user: ${multiUserCheck.reason}")
                return multiUserCheck
            }
            
            // Method 12: Check application flags and system app status
            val appFlagsCheck = checkApplicationFlags(context)
            if (appFlagsCheck.isCloned) {
                Log.w(TAG, "Clone detected via app flags: ${appFlagsCheck.reason}")
                return appFlagsCheck
            }
            
            // Method 10: Check process name
            val processCheck = checkProcessName(context)
            if (processCheck.isCloned) {
                Log.w(TAG, "Clone detected via process name: ${processCheck.reason}")
                return processCheck
            }
            
            // Method 11: Check for known clone app files/directories
            val filesCheck = checkCloneAppFiles()
            if (filesCheck.isCloned) {
                Log.w(TAG, "Clone detected via files: ${filesCheck.reason}")
                return filesCheck
            }
            
            // Method 12: Check system properties and environment
            val systemPropsCheck = checkSystemProperties()
            if (systemPropsCheck.isCloned) {
                Log.w(TAG, "Clone detected via system properties: ${systemPropsCheck.reason}")
                return systemPropsCheck
            }
            
            Log.d(TAG, "No clone environment detected")
            val result = CloneDetectionResult(
                isCloned = false,
                reason = "App appears to be running in normal environment"
            )
            
            // Log to Firebase Analytics
            logCloneDetectionEvent(context, result)
            
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during clone detection", e)
            // In case of error, fail open (don't block) to avoid false positives
            val result = CloneDetectionResult(
                isCloned = false,
                reason = "Detection error: ${e.message}"
            )
            
            // Log error to Firebase Analytics
            logCloneDetectionEvent(context, result)
            
            return result
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
    
    /**
     * Log clone detection event to Firebase Analytics
     * Tracks which methods are detecting clones and how often
     */
    private fun logCloneDetectionEvent(context: Context, result: CloneDetectionResult) {
        try {
            val analytics = FirebaseAnalytics.getInstance(context)
            
            val params = Bundle().apply {
                putString("detection_result", if (result.isCloned) "cloned" else "legitimate")
                putString("detection_method", result.detectionMethod ?: "none")
                putString("detection_reason", result.reason)
                putString("app_data_dir", context.applicationInfo.dataDir)
                putString("installer_package", try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName ?: "null"
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getInstallerPackageName(context.packageName) ?: "null"
                    }
                } catch (e: Exception) { "error" })
                putInt("user_id", getUserId())
                putBoolean("is_cloned", result.isCloned)
            }
            
            // Log the event
            analytics.logEvent("clone_detection_check", params)
            
            // If clone detected, log a separate event for easier tracking
            if (result.isCloned) {
                val cloneParams = Bundle().apply {
                    putString("method", result.detectionMethod ?: "unknown")
                    putString("reason_summary", result.reason.take(100)) // Limit length
                }
                analytics.logEvent("clone_app_blocked", cloneParams)
                
                Log.d(TAG, "📊 Firebase Analytics: Clone app blocked - Method: ${result.detectionMethod}")
            } else {
                Log.d(TAG, "📊 Firebase Analytics: Legitimate app verified")
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error logging to Firebase Analytics", e)
        }
    }
    
    // ==================== ENHANCED DETECTION METHODS ====================
    
    /**
     * Method 0: APK Signature Verification (MOST RELIABLE)
     * Verify that the app is signed with the expected certificate.
     * Cloner apps cannot replicate your signing certificate.
     */
    private fun checkAppSignature(context: Context): CloneDetectionResult {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signatureHash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Android P+ uses SigningInfo
                val signatures = packageInfo.signingInfo
                if (signatures?.hasMultipleSigners() == true) {
                    signatures.apkContentsSigners?.firstOrNull()
                } else {
                    signatures?.signingCertificateHistory?.firstOrNull()
                }?.let { getSignatureHash(it.toByteArray()) }
            } else {
                // Android N and below use Signature array
                @Suppress("DEPRECATION")
                packageInfo.signatures?.firstOrNull()?.let { 
                    getSignatureHash(it.toByteArray()) 
                }
            }

            // Expected signature hashes
            // Debug certificate (from your keytool output)
            val debugCertificateHash = "fd632490dc4e7a426a401d61b14da2f0b72949a09bd344a03b5f793665c716ed"
            
            // Internal release certificate (detected from actual build via logcat)
            val internalReleaseCertificateHash = "cdbada0a0f7cdac8fddbc8ac2995020f8a29f1078c3e7357c4c20c6f61312c0a"
            
            // Play Store release certificate (from keytool if different from internal)
            val playStoreReleaseCertificateHash = "f17cb7278858d5109a26037b2bb47cd6d5b9e3d7e031b22aa97b03d4ee34d5aa"
            
            // Firebase App Distribution / Additional release certificate (detected 2026-01-28)
            val appDistributionCertificateHash = "cb700187cae8c934310a7269375684e6da1de38378621ee335e94623a3abec0e"
            
            val validHashes = listOf(
                debugCertificateHash.lowercase(),
                internalReleaseCertificateHash.lowercase(),
                playStoreReleaseCertificateHash.lowercase(),
                appDistributionCertificateHash.lowercase()
            )
            
            if (signatureHash != null) {
                val hashLowercase = signatureHash.lowercase()
                Log.d(TAG, "App signature hash: $hashLowercase")
                
                // Check if current signature matches any valid hash
                val isValid = validHashes.any { expectedHash ->
                    // Also check partial match since we extracted from your keytool output
                    hashLowercase.contains(expectedHash) || expectedHash.contains(hashLowercase)
                }
                
                if (isValid) {
                    Log.d(TAG, "✓ App signature verified successfully")
                    return CloneDetectionResult(
                        isCloned = false,
                        reason = "Signature verification passed"
                    )
                } else {
                    Log.w(TAG, "x App signature mismatch! Got: $hashLowercase")
                    return CloneDetectionResult(
                        isCloned = true,
                        reason = "App signature does not match official certificate. This appears to be a tampered or cloned version.",
                        detectionMethod = "APK Signature Verification"
                    )
                }
            } else {
                Log.w(TAG, "✗ Unable to extract app signature")
                return CloneDetectionResult(
                    isCloned = true,
                    reason = "Unable to verify app signature",
                    detectionMethod = "APK Signature Verification"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking app signature", e)
            // Fail secure: if we can't verify, assume it's potentially cloned
            return CloneDetectionResult(
                isCloned = true,
                reason = "SignatureJSONException verification failed: ${e.message}",
                detectionMethod = "APK Signature Verification"
            )
        }
    }

    /**
     * Calculate SHA-256 hash of signature bytes
     */
    private fun getSignatureHash(signature: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(signature)
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Method 1: Installer Package Verification
     * Strict Whitelist: Only allow Play Store, System, or ADB (null)
     */
    private fun checkInstallerPackage(context: Context): CloneDetectionResult {
        try {
            val installerPackage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            
            Log.d(TAG, "Installer package: ${installerPackage ?: "null (sideloaded)"}")
            
            // Strictly Allowed Installers
            val allowedInstallers = listOf(
                "com.android.vending",          // Google Play Store
                "com.google.android.feedback",   // Google internal
                "com.android.packageinstaller",  // System Package Installer
                "com.google.android.packageinstaller", // Google Package Installer
                "com.miui.packageinstaller",     // Xiaomi System Installer (optional, but safe)
                "com.samsung.android.packageinstaller" // Samsung System Inataller
            )
            
            // If installer is NOT null and NOT in whitelist -> BLOCK IT
            if (installerPackage != null && !allowedInstallers.contains(installerPackage)) {
                return CloneDetectionResult(
                    isCloned = true,
                    reason = "Unauthorized installer detected: $installerPackage. Expected Play Store or System.",
                    detectionMethod = "Installer Package Verification"
                )
            }
            
            // Allow null (ADB/Standard Sideload)
            // But if it was installed by a known cloner (even if not in blacklist before), it's caught above.
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking installer package", e)
        }
        
        return CloneDetectionResult(isCloned = false, reason = "Installer check passed")
    }

    /**
     * Method 2: File Token Consistency
     * Checks if Internal Storage and External Storage are consistent.
     * Cloners often sandbox Internal Storage (filesDir) but share External Storage.
     */
    private fun checkFileTokenConsistency(context: Context): CloneDetectionResult {
        try {
            val tokenFileName = ".nss_auth_token"
            
            // 1. Internal Storage Token
            val internalFile = File(context.filesDir, tokenFileName)
            var internalToken: String? = null
            if (internalFile.exists()) {
                internalToken = internalFile.readText().trim()
            }

            // 2. External Service Token (if available)
            // We use getExternalFilesDir because it's app-private but on external storage
            // Cloners often remap this or share it differently than internal
            val externalDir = context.getExternalFilesDir(null)
            if (externalDir != null) {
                val externalFile = File(externalDir, tokenFileName)
                var externalToken: String? = null
                if (externalFile.exists()) {
                    externalToken = externalFile.readText().trim()
                }

                Log.d(TAG, "Token Check: Internal=$internalToken, External=$externalToken")

                // CASE A: Mismatch (One exists, other doesn't OR values differ)
                // If legit app, we write to both at same time. They should sync.
                if (internalToken != null && externalToken != null && internalToken != externalToken) {
                     return CloneDetectionResult(
                        isCloned = true,
                        reason = "Storage inconsistency detected (Tokens mismatch)",
                        detectionMethod = "File Token Check"
                    )
                }
                
                // CASE B: External exists but Internal is empty
                // This is the "Dead Giveaway" for cloners that share SD card but sandbox internal data
                // The REAL app wrote the token to SD card previously. The CLONE (sandbox) sees empty internal.
                // But the CLONE sees the SHARED SD card file.
                if (internalToken == null && externalToken != null) {
                     return CloneDetectionResult(
                        isCloned = true,
                        reason = "Storage inconsistency detected (External token exists without Internal)",
                        detectionMethod = "File Token Check"
                    )
                }

                // Setup for next time: If both missing, or just internal exists (maybe cleared data?), ensure sync
                if (internalToken == null && externalToken == null) {
                    val newToken = java.util.UUID.randomUUID().toString()
                    internalFile.writeText(newToken)
                    externalFile.writeText(newToken)
                    Log.d(TAG, "Generated new consistency tokens")
                } else if (internalToken != null && externalToken == null) {
                    // Internal exists, Exernal missing (maybe user deleted folder). Re-sync.
                    externalFile.writeText(internalToken)
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "Error checking file tokens", e)
        }
        return CloneDetectionResult(isCloned = false, reason = "Token check passed")
    }
    
    /**
     * Method 2: Native Library Detection
     * Check for suspicious native libraries that indicate virtualization
     */
    private fun checkNativeLibraries(context: Context): CloneDetectionResult {
        try {
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val libDir = File(nativeLibDir)
            
            if (!libDir.exists() || !libDir.isDirectory) {
                return CloneDetectionResult(isCloned = false, reason = "Native lib check skipped")
            }
            
            // List all .so files
            val soFiles = libDir.listFiles { file -> file.extension == "so" }
            
            // Suspicious library name patterns used by clone/virtual apps
            val suspiciousLibPatterns = listOf(
                "epic",      // Epic Games (virtualizes apps)
                "virtual",   // VirtualApp framework
                "va_",       // VirtualApp abbreviated (prefix)
                "xposed",    // Xposed framework
                "substrate", // Substrate (hooking framework)
                "lody",      // Lody Virtual App creator
                "hook",      // General hooking libraries
                "inject"     // Code injection libraries
            )
            
            soFiles?.forEach { soFile ->
                val fileName = soFile.name.lowercase()
                for (pattern in suspiciousLibPatterns) {
                    if (fileName.contains(pattern)) {
                        Log.w(TAG, "Suspicious native library found: ${soFile.name}")
                        return CloneDetectionResult(
                            isCloned = true,
                            reason = "Suspicious native library detected: ${soFile.name}",
                            detectionMethod = "Native Library Detection"
                        )
                    }
                }
            }
            
            Log.d(TAG, "Native libraries (${soFiles?.size ?: 0}): ${soFiles?.joinToString(", ") { it.name }}")
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking native libraries", e)
        }
        
        return CloneDetectionResult(isCloned = false, reason = "Native lib check passed")
    }
    
    /**
     * Method 3: ClassLoader Detection
     * Detect custom ClassLoaders used by virtualization frameworks
     */
    private fun checkClassLoader(): CloneDetectionResult {
        try {
            val classLoader = CloneDetectionUtils::class.java.classLoader
            val classLoaderName = classLoader?.javaClass?.name ?: "null"
            
            Log.d(TAG, "ClassLoader: $classLoaderName")
            
            // Normal Android apps use PathClassLoader or DexClassLoader
            val suspiciousClassLoaders = listOf(
                "de.robv.android.xposed",    // Xposed
                "com.lody.virtual",          // VirtualApp
                "com.pspace.vandroid",       // Parallel Space
                "bin.mt.plus",               // MT Manager
                "io.va.exposed",             // VirtualApp exposed variant
                "com.swift.sandhook",        // SandHook framework
                "me.weishu"                  // VirtualXposed/Epic
            )
            
            for (pattern in suspiciousClassLoaders) {
                if (classLoaderName.contains(pattern, ignoreCase = true)) {
                    return CloneDetectionResult(
                        isCloned = true,
                        reason = "Suspicious ClassLoader detected: $classLoaderName",
                        detectionMethod = "ClassLoader Detection"
                    )
                }
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking ClassLoader", e)
        }
        
        return CloneDetectionResult(isCloned = false, reason = "ClassLoader check passed")
    }
    
    /**
     * Method 4: Stack Trace Analysis
     * Analyze stack trace for signs of hooking or virtualization
     */
    private fun checkStackTrace(): CloneDetectionResult {
        try {
            val stackTrace = Thread.currentThread().stackTrace
            val stackString = stackTrace.joinToString("\n") { it.toString() }
            
            // Suspicious package patterns in stack trace
            val suspiciousPatterns = listOf(
                "de.robv.android.xposed",
                "com.lody.virtual",
                "com.pspace",
                "io.va.exposed",
                "com.swift.sandhook",
                "me.weishu",
                "bin.mt.plus",
                "epic.android"
            )
            
            for (pattern in suspiciousPatterns) {
                if (stackString.contains(pattern, ignoreCase = true)) {
                    Log.w(TAG, "Suspicious framework detected in stack trace: $pattern")
                    return CloneDetectionResult(
                        isCloned = true,
                        reason = "Suspicious framework detected in stack trace",
                        detectionMethod = "Stack Trace Analysis"
                    )
                }
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error analyzing stack trace", e)
        }
        
        return CloneDetectionResult(isCloned = false, reason = "Stack trace check passed")
    }
    
    /**
     * Method 5: Hardware ID Verification
     * Enhanced hardware verification with cross-checks for fake/cloned IDs
     */
    @SuppressLint("HardwareIds")
    private fun checkHardwareConsistency(context: Context): CloneDetectionResult {
        try {
            // Get Android ID
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            
            // Known fake Android IDs used by emulators/cloners
            val fakeIds = listOf(
                "9774d56d682e549c",  // Default emulator ID
                "0123456789abcdef",  // Common fake ID
                "1234567890abcdef",  // Another common fake
                null,
                ""
            )
            
            if (androidId in fakeIds) {
                Log.w(TAG, "Suspicious or fake Android ID detected: $androidId")
                return CloneDetectionResult(
                    isCloned = true,
                    reason = "Suspicious or fake Android ID detected",
                    detectionMethod = "Hardware ID Verification"
                )
            }
            
            // Check if Build.SERIAL is accessible and valid
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val serial = Build.SERIAL
                if (serial == "unknown" || serial == "0" || serial.isNullOrEmpty()) {
                    Log.w(TAG, "Suspicious device serial: $serial")
                    // Don't block based on this alone, just log
                }
            }
            
            Log.d(TAG, "Android ID: $androidId")
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking hardware IDs", e)
        }
        

        return CloneDetectionResult(isCloned = false, reason = "Hardware check passed")
    }

    /**
     * Method 6: Checking Memory Maps (/proc/self/maps)
     * This scans the process's memory mapping for suspicious libraries or frameworks
     * widely used by cloning and hooking engines (Xposed, Frida, Substrate, etc.).
     * This is very hard for cloners to generic spoof without breaking functionality.
     */
    /**
     * Method 7: Check Memory Maps for suspicious hooking libraries
     * This scans the process's memory mapping for suspicious libraries or frameworks
     * widely used by cloning and hooking engines (Xposed, Frida, Substrate, etc.).
     * This is very hard for cloners to generic spoof without breaking functionality.
     */
    private fun checkMemoryMaps(): CloneDetectionResult {
        try {
            val file = File("/proc/self/maps")
            if (!file.exists() || !file.canRead()) {
                 // If we can't read our own maps, something is restricting us (typical of sandboxes)
                 return CloneDetectionResult(
                    isCloned = true, 
                    reason = "Unable to read /proc/self/maps", 
                    detectionMethod = "Memory Maps Security"
                )
            }

            val suspiciousTerms = listOf(
                "Xposed", "xposed",
                "Substrate", "substrate",
                "Frida", "frida",
                "lody", "virtual", // VirtualApp/Lody
                "sandhook", "SandHook",
                "edxp", "EdXposed",
                "magisk", "Magisk",
                "riru", "Riru", 
                "lsposed", "LSPosed",
                "com.saurik", // Cydia Substrate
                "yahfa", // YAHFA hooking
                "epic", // Epic hooking
                "blackbox", // BlackBox
                "io.va.exposed" // VirtualApp exposed
            )
            
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    for (term in suspiciousTerms) {
                        if (currentLine.contains(term, ignoreCase = true)) {
                            Log.w(TAG, "Suspicious memory map detected: $term in line: $currentLine")
                             return CloneDetectionResult(
                                isCloned = true,
                                reason = "Suspicious memory map detected: $term",
                                detectionMethod = "Memory Maps Analysis"
                            )
                        }
                    }
                }
            }
            Log.d(TAG, "Memory maps check passed")

        } catch (e: Exception) {
            Log.w(TAG, "Error checking memory maps app", e)
        }
        
        return CloneDetectionResult(isCloned = false, reason = "Memory maps check passed")
    }

    /**
     * Check if the device is an official Android Studio/Google emulator
     * Used to bypass certain aggressive checks that false-positive on official dev tools
     */
    private fun isOfficialEmulator(): Boolean {
        return try {
            val isGenericInfo = Build.FINGERPRINT.startsWith("google/sdk_gphone") ||
                               Build.FINGERPRINT.startsWith("unknown") ||
                               Build.MODEL.contains("google_sdk") ||
                               Build.MODEL.contains("Emulator") ||
                               Build.MODEL.contains("Android SDK built for x86") ||
                               Build.MANUFACTURER.contains("Google") && Build.PRODUCT.startsWith("sdk_gphone") ||
                               Build.BRAND == "google" && Build.DEVICE.startsWith("generic")

            Log.d(TAG, "Emulator check: isOfficial=$isGenericInfo (Model=${Build.MODEL}, Manuf=${Build.MANUFACTURER})")
            isGenericInfo
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Method 7: Check Shared User ID
     * Legitimate apps (unless signed with system platform keys) typically don't share UIDs.
     * Cloners might force a shared UID.
     */
    private fun checkSharedUid(context: Context): CloneDetectionResult {
        try {
            val packageManager = context.packageManager
            val currentPkg = context.packageName
            val pkgInfo = packageManager.getPackageInfo(currentPkg, 0)
            
            // Unless you explicitly defined android:sharedUserId in your Manifest, this should be null.
            if (pkgInfo.sharedUserId != null) {
                 return CloneDetectionResult(
                    isCloned = true,
                    reason = "Unexpected Shared User ID detected: ${pkgInfo.sharedUserId}",
                    detectionMethod = "UID Validation"
                )
            }
            
            Log.d(TAG, "Shared UID check passed")
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Shared UID", e)
        }
        
        return CloneDetectionResult(isCloned = false, reason = "UID check passed")
    }

    /**
     * Method 8: Check for Installed Cloner Apps
     * Detects if known cloner apps are installed on the device
     * Sophisticated cloners like CloneApp preserve signatures but are still detectable
     */
    private fun checkForInstalledClonerApps(context: Context): CloneDetectionResult {
        try {
            val packageManager = context.packageManager
            
            // Known cloner app package names (comprehensive list)
            val knownClonerPackages = listOf(
                // Most common/sophisticated cloners
                "com.pengyou.cloneapp",              // CloneApp by szpy tech
                "com.pengyou.cloneapp.pro",          // CloneApp Pro
                "com.szpy.cloneapp",                 // CloneApp older pkg
                "com.szpy.cloneapp.pro",             // CloneApp Pro older pkg
                "com.parallel.space.lite",        // Parallel Space Lite
                "com.lbe.parallel.intl",          // Parallel Space
                "com.lbe.parallel",               // Parallel Space
                "com.excelliance.multiaccounts",  // Multi Accounts/2Accounts
                "com.jumobile.multiapp",          // Multiple Accounts
                "com.oasisfeng.island",           // Island
                "com.ludashi.dualspace",          // Dual Space
                "com.jiubang.commerce.gomultiple", // GO Multiple
                "com.lody.virtual",               // VirtualXposed
                "com.excean.parallelspace",       // Parallel Space variants
                "io.va.exposed",                  // VirtualApp
                "com.pspace.vandroid",            // Parallel Space Android
                "com.applisto.appcloner",         // App Cloner
                "com.noxgroup.app.multi",         // Multi Space
                "com.triggertrap.seek.you.cloneapp", // You CloneApp
                "you.cloneapp",                   // You CloneApp variant
                "com.mad.multiapp",               // Multi App
                "com.gizmoquip.multi",            // Multi accounts
                "com.beantech.multipleaccounts",  // Multiple Accounts
                "com.appsinnova.android.dualapp", // Dual App
                "com.jiubang.goscreenlock",       // GO Multiple variants
                "com.polestar.super.clone",       // Super Clone
                "com.excelliance.multiaccount",   // Multi Account
                "com.cloneapp.parallelspace.dualspace" // Clone App
            )
            
            val installedCloners = mutableListOf<String>()
            
            // Check each known cloner package
            for (packageName in knownClonerPackages) {
                try {
                    packageManager.getPackageInfo(packageName, 0)
                    // If we get here, the package is installed
                    installedCloners.add(packageName)
                    Log.w(TAG, "Detected installer cloner app: $packageName")
                } catch (e: PackageManager.NameNotFoundException) {
                    // Package not installed, this is expected
                }
            }
            
            if (installedCloners.isNotEmpty()) {
                val clonerList = installedCloners.joinToString(", ")
                return CloneDetectionResult(
                    isCloned = true,
                    reason = "Cloner app(s) detected on device: $clonerList",
                    detectionMethod = "Installed Cloner Apps Detection"
                )
            }
            
            Log.d(TAG, "No known cloner apps detected on device")
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking for cloner apps", e)
        }
        
        return CloneDetectionResult(isCloned = false, reason = "Cloner apps check passed")
    }
}
