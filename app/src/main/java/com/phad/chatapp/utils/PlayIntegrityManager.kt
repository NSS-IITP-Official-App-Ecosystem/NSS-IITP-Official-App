package com.phad.chatapp.utils

import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.firebase.auth.FirebaseAuth
import com.phad.chatapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/**
 * Manages Play Integrity API interactions for QR attendance security.
 * 
 * BEHAVIOR:
 * - DEBUG builds: Run integrity check but LOG results only (don't block)
 * - RELEASE builds: Enforce integrity check, block QR scanning if fails
 * 
 * This replaces client-side clone detection with server-side verification.
 * Only blocks QR scanning access, NOT the whole app.
 */
object PlayIntegrityManager {
    private const val TAG = "PlayIntegrityManager"
    
    // Cloud Function URL for integrity verification
    private val VERIFY_INTEGRITY_URL: String
        get() {
            val projId = try {
                com.google.firebase.FirebaseApp.getInstance().options.projectId
            } catch (e: Exception) {
                null
            } ?: "nssiitp-app"
            return if (com.phad.chatapp.BuildConfig.DEBUG) {
                val isEmulator = android.os.Build.FINGERPRINT.startsWith("generic") || 
                                 android.os.Build.MODEL.contains("google_sdk") || 
                                 android.os.Build.MODEL.contains("Emulator")
                val host = if (isEmulator) "10.0.2.2" else "127.0.0.1"
                "http://$host:5001/$projId/asia-south1/verifyPlayIntegrity"
            } else {
                "https://asia-south1-$projId.cloudfunctions.net/verifyPlayIntegrity"
            }
        }

    // Shared Preferences for Integrity Cache
    private const val PREFS_NAME = "nss_integrity_prefs"
    private const val KEY_LAST_VERIFIED = "last_verified_timestamp"
    private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 Hours
    
    // Mutex for request deduplication
    private val verificationMutex = Mutex()
    
    /**
     * Result of Play Integrity verification
     */
    sealed class IntegrityResult {
        /** Integrity check passed - allow QR scanning */
        object Success : IntegrityResult()
        
        /** Integrity check failed - block QR scanning with reason */
        data class Failure(
            val message: String, 
            val canRetry: Boolean = true
        ) : IntegrityResult()
        
        /** Integrity check is in progress */
        object Loading : IntegrityResult()
    }
    
    /**
     * Verify app integrity before allowing QR attendance scanning.
     * 
     * In DEBUG builds: Performs check but logs result without blocking.
     * In RELEASE builds: Blocks QR scanning if integrity check fails.
     *
     * @param context Application context
     * @param userId User's roll number for logging/tracking
     * @param forceRefresh If true, ignores cache and forces a new network check
     * @return IntegrityResult indicating success or failure
     */
    suspend fun verifyIntegrity(
        context: Context, 
        userId: String, 
        forceRefresh: Boolean = false
    ): IntegrityResult {
        return withContext(Dispatchers.IO) {
            // Use Mutex to serialize requests and prevent race conditions
            // (e.g. Silent Check vs User Action happening at same time)
            verificationMutex.withLock {
                // Check cache AGAIN inside the lock.
                // If a previous request just finished and updated the cache, we can return success immediately.
                if (!forceRefresh && isCacheValid(context)) {
                    Log.i(TAG, "✅ Integrity cache valid (< 24h). Skipping network check.")
                    return@withLock IntegrityResult.Success
                }

                try {
                    Log.d(TAG, "Starting verification for user: $userId")
                    
                    // Step 1: Check for multi-user profile
                    val multiUserCheck = checkMultiUserProfile(context)
                    if (multiUserCheck != null) {
                        Log.w(TAG, "Multi-user profile detected: ${multiUserCheck.message}")
                        return@withLock handleResult(context, multiUserCheck)
                    }
                    
                    // Step 2: Play Integrity verification
                    Log.d(TAG, "Starting Play Integrity verification...")
                    val nonce = generateNonce(userId)
                    
                    // Request integrity token from Play Integrity API
                    val integrityManager = IntegrityManagerFactory.create(context)
                    val tokenRequest = IntegrityTokenRequest.builder()
                        .setNonce(nonce)
                        .build()
                    
                    Log.d(TAG, "Requesting integrity token from Play Integrity API...")
                    val tokenResponse = integrityManager.requestIntegrityToken(tokenRequest).await()
                    val integrityToken = tokenResponse.token()
                    
                    if (integrityToken.isNullOrEmpty()) {
                        Log.e(TAG, "Received empty integrity token")
                        return@withLock handleResult(context, 
                            IntegrityResult.Failure(
                                message = "Could not verify app integrity. Please try again.",
                                canRetry = true
                            )
                        )
                    }
                    
                    Log.d(TAG, "Received integrity token, sending to backend for verification...")
                    
                    // Send token to backend for verification
                    val verificationResult = verifyTokenWithBackend(integrityToken, userId, nonce)
                    
                    handleResult(context, verificationResult)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error during integrity verification", e)
                    
                    // Handle specific error cases
                    val result = when {
                        e.message?.contains("NETWORK") == true -> {
                            IntegrityResult.Failure(
                                message = "Network error. Please check your connection and try again.",
                                canRetry = true
                            )
                        }
                        e.message?.contains("API_NOT_AVAILABLE") == true -> {
                            IntegrityResult.Failure(
                                message = "Play services unavailable. Please update Google Play Services.",
                                canRetry = false
                            )
                        }
                        e.message?.contains("PLAY_STORE_NOT_FOUND") == true -> {
                            IntegrityResult.Failure(
                                message = "To mark attendance, please use the official app installed from the Google Play Store.",
                                canRetry = false
                            )
                        }
                        else -> {
                            IntegrityResult.Failure(
                                message = "Verification failed (${e.message ?: "Unknown"}). Please try again.",
                                canRetry = true
                            )
                        }
                    }
                    
                    handleResult(context, result)
                }
            }
        }
    }
    
    /**
     * Handle result based on build type.
     * DEBUG: Log only, always return Success
     * RELEASE: Return actual result
     */
    private fun handleResult(context: Context, result: IntegrityResult): IntegrityResult {
        return if (BuildConfig.DEBUG) {
            when (result) {
                is IntegrityResult.Success -> {
                    Log.i(TAG, "✅ DEBUG MODE: Integrity check PASSED")
                    // Update cache even in debug mode for testing
                    updateCache(context)
                }
                is IntegrityResult.Failure -> {
                    Log.w(TAG, "⚠️ DEBUG MODE: Integrity check FAILED (but allowing anyway)")
                    Log.w(TAG, "   Reason: ${result.message}")
                    Log.w(TAG, "   Can retry: ${result.canRetry}")
                }
                is IntegrityResult.Loading -> {
                    Log.d(TAG, "DEBUG MODE: Integrity check in progress...")
                }
            }
            // In DEBUG mode, always allow (but log the actual result)
            Log.i(TAG, "🔓 DEBUG MODE: Bypassing integrity enforcement - QR scanning allowed")
            IntegrityResult.Success
        } else {
            // In RELEASE mode, enforce the actual result
            when (result) {
                is IntegrityResult.Success -> {
                    Log.i(TAG, "✅ RELEASE MODE: Integrity check PASSED - QR scanning allowed")
                    // Save success to cache
                    updateCache(context)
                }
                is IntegrityResult.Failure -> {
                    Log.w(TAG, "🚫 RELEASE MODE: Integrity check FAILED - QR scanning BLOCKED")
                    Log.w(TAG, "   Reason: ${result.message}")
                }
                is IntegrityResult.Loading -> { /* No-op */ }
            }
            result
        }
    }

    /**
     * Check if a valid successful verification exists within the last 24 hours.
     */
    private fun isCacheValid(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastVerified = prefs.getLong(KEY_LAST_VERIFIED, 0)
        val currentTime = System.currentTimeMillis()
        
        val isValid = (currentTime - lastVerified) < CACHE_DURATION_MS
        
        if (isValid) {
            Log.d(TAG, "Cache Valid: Last verified ${(currentTime - lastVerified) / 1000 / 60} min ago")
        } else {
            Log.d(TAG, "Cache Expired or Missing. Needs verification.")
        }
        
        return isValid
    }

    /**
     * Update the cache with the current timestamp upon successful verification.
     */
    private fun updateCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_VERIFIED, System.currentTimeMillis()).apply()
        Log.d(TAG, "Integrity cache updated to NOW")
    }
    
    /**
     * Send integrity token to backend Cloud Function for verification.
     * The backend decrypts the token and checks verdicts.
     */
    private suspend fun verifyTokenWithBackend(
        token: String, 
        userId: String,
        nonce: String
    ): IntegrityResult {
        return withContext(Dispatchers.IO) {
            try {
                // Get Firebase Auth token for authenticated request
                val authToken = FirebaseAuth.getInstance().currentUser
                    ?.getIdToken(false)
                    ?.await()
                    ?.token
                
                if (authToken == null) {
                    Log.e(TAG, "No Firebase auth token available")
                    return@withContext IntegrityResult.Failure(
                        message = "Not authenticated. Please log in again.",
                        canRetry = false
                    )
                }
                
                val url = URL(VERIFY_INTEGRITY_URL)
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $authToken")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 30000
                }
                
                // Send request body
                val requestBody = JSONObject().apply {
                    put("integrityToken", token)
                    put("userId", userId)
                    put("nonce", nonce)
                }
                
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(requestBody.toString())
                    writer.flush()
                }
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Backend response code: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseBody = connection.inputStream.bufferedReader().readText()
                    val response = JSONObject(responseBody)
                    
                    val allowed = response.optBoolean("allowed", false)
                    val reason = response.optString("reason", "Unknown")
                    
                    Log.d(TAG, "Backend verdict: allowed=$allowed, reason=$reason")
                    
                    if (allowed) {
                        IntegrityResult.Success
                    } else {
                        IntegrityResult.Failure(
                            message = "Attendance cannot be marked.\nPlease use the official app installed from the Google Play Store.",
                            canRetry = false
                        )
                    }
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                    Log.e(TAG, "Backend error: $responseCode - $errorBody")
                    
                    IntegrityResult.Failure(
                        message = "Verification failed (Server $responseCode). Please try again.",
                        canRetry = true
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error calling backend", e)
                IntegrityResult.Failure(
                    message = "Network error (${e.message}). Please check your connection.",
                    canRetry = true
                )
            }
        }
    }
    
    /**
     * Generate a secure nonce for the integrity request.
     * Combines userId, timestamp, and random UUID for uniqueness.
     */
    private fun generateNonce(userId: String): String {
        val timestamp = System.currentTimeMillis()
        val random = UUID.randomUUID().toString()
        val combined = "$userId:$timestamp:$random"
        
        // Hash the combined string for consistent length
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(combined.toByteArray())
        
        // Convert to Base64-like string (URL-safe)
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Check if Play Integrity API is available on this device.
     * Useful for graceful degradation on devices without Play Services.
     */
    fun isPlayIntegrityAvailable(context: Context): Boolean {
        return try {
            IntegrityManagerFactory.create(context)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Play Integrity API not available", e)
            false
        }
    }
    
    /**
     * Check if app is running in a secondary user profile.
     * This detects:
     * - Second Space (Xiaomi, Oppo, Vivo, etc.)
     * - Work Profile (Android Enterprise)
     * - Guest users
     * - Any non-primary Android user profile
     * 
     * @return IntegrityResult.Failure if in secondary profile, null if primary user
     */
    private fun checkMultiUserProfile(context: Context): IntegrityResult.Failure? {
        try {
            val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
            if (userManager != null) {
                // Check if running in a work profile (managed profile)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+ has more reliable check
                    if (userManager.isManagedProfile) {
                        Log.w(TAG, "Detected: Work/Managed Profile")
                        return IntegrityResult.Failure(
                            message = "QR attendance is not available in Work Profile.\nPlease use the app from your personal profile.",
                            canRetry = false
                        )
                    }
                }
                
                // Check if this is the primary/system user
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if (!userManager.isSystemUser) {
                        Log.w(TAG, "Detected: Secondary user profile (not system user)")
                        return IntegrityResult.Failure(
                            message = "QR attendance is not available in Second Space or guest profile.\nPlease use the app from your primary profile.",
                            canRetry = false
                        )
                    }
                }
                
                // Additional check using user serial number
                // Primary user typically has serial 0
                val userHandle = android.os.Process.myUserHandle()
                val serialNumber = userManager.getSerialNumberForUser(userHandle)
                if (serialNumber != 0L) {
                    Log.w(TAG, "Detected: Non-primary user (serial: $serialNumber)")
                    return IntegrityResult.Failure(
                        message = "QR attendance is not available in Second Space.\nPlease use the app from your primary profile.",
                        canRetry = false
                    )
                }
            }
            
            // Also check for cloned app indicators via data directory path
            val dataDir = context.dataDir.absolutePath
            if (dataDir.contains("/user/") && !dataDir.contains("/user/0/")) {
                // Running in a non-primary user's data directory
                Log.w(TAG, "Detected: Non-primary user data directory: $dataDir")
                return IntegrityResult.Failure(
                    message = "QR attendance is not available in Second Space.\nPlease use the app from your primary profile.",
                    canRetry = false
                )
            }
            
            Log.d(TAG, "Multi-user check passed: Running in primary user profile")
            return null // Primary user, allow
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking multi-user profile", e)
            // On error, don't block - let Play Integrity handle it
            return null
        }
    }
    
    /**
     * Perform a silent background verification to update the cache.
     * Call this when app opens or from a worker.
     * It swallows errors and only logs them, preventing UI blocking.
     */
    suspend fun performSilentBackgroundCheck(context: Context, userId: String) {
        if (isCacheValid(context)) {
            Log.d(TAG, "Silent Check: Cache already valid, skipping.")
            return
        }
        
        Log.d(TAG, "Silent Check: Cache expired. Refreshing in background...")
        try {
            // We call verifyIntegrity with forceRefresh=true effectively,
            // but we don't care about the return value, just the side effect (cache update).
            // NOTE: verifyIntegrity internal logic already updates cache on Success.
            
            withContext(Dispatchers.IO) {
                // Manually duplicate logic slightly to avoid modifying the main public method signature if it gets complex,
                // BUT actually re-using verifyIntegrity(forceRefresh=true) is cleaner.
                // We just need to ensure verifyIntegrity exposes forceRefresh.
                // Since we updated verifyIntegrity signature in previous step, we can use it.
                
               val result = verifyIntegrity(context, userId, forceRefresh = true)
               
               if (result is IntegrityResult.Success) {
                   Log.i(TAG, "Silent Check: ✅ Success. Cache updated.")
               } else {
                   Log.w(TAG, "Silent Check: ❌ Failed. Cache remains expired.")
               }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Silent Check: Error", e)
        }
    }
}
