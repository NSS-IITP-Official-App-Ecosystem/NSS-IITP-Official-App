package com.phad.chatapp.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Service to automatically refresh Firebase authentication tokens
 * Runs every 50 minutes to prevent token expiration during QR attendance scanning
 */
class TokenRefreshService : Service() {
    private val TAG = "TokenRefreshService"
    private val binder = TokenRefreshBinder()
    private var refreshJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Token refresh interval: 50 minutes (3000 seconds)
    private val REFRESH_INTERVAL_MINUTES = 50L
    private val REFRESH_INTERVAL_SECONDS = REFRESH_INTERVAL_MINUTES * 60L
    
    inner class TokenRefreshBinder : Binder() {
        fun getService(): TokenRefreshService = this@TokenRefreshService
    }
    
    override fun onBind(intent: Intent): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TokenRefreshService created")
        startTokenRefresh()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "TokenRefreshService started")
        return START_STICKY // Restart if killed by system
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TokenRefreshService destroyed")
        stopTokenRefresh()
    }
    
    /**
     * Start the periodic token refresh
     */
    private fun startTokenRefresh() {
        if (refreshJob?.isActive == true) {
            Log.d(TAG, "Token refresh already running")
            return
        }
        
        Log.d(TAG, "Starting token refresh every $REFRESH_INTERVAL_MINUTES minutes")
        
        refreshJob = serviceScope.launch {
            while (isActive) {
                try {
                    refreshFirebaseToken()
                    delay(REFRESH_INTERVAL_SECONDS * 1000) // Convert to milliseconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error in token refresh loop", e)
                    // Wait a bit before retrying
                    delay(TimeUnit.MINUTES.toMillis(5))
                }
            }
        }
    }
    
    /**
     * Stop the periodic token refresh
     */
    private fun stopTokenRefresh() {
        refreshJob?.cancel()
        refreshJob = null
        Log.d(TAG, "Token refresh stopped")
    }
    
    /**
     * Refresh Firebase authentication token
     */
    private suspend fun refreshFirebaseToken() {
        try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Log.w(TAG, "No Firebase user found, skipping token refresh")
                return
            }
            
            Log.d(TAG, "Refreshing Firebase token for user: ${currentUser.email}")
            
            // Force refresh the token
            val tokenResult = currentUser.getIdToken(true).await()
            val newToken = tokenResult.token
            
            if (newToken != null) {
                Log.d(TAG, "✅ Firebase token refreshed successfully")
                Log.d(TAG, "Token expires at: ${java.util.Date(tokenResult.expirationTimestamp * 1000)}")
            } else {
                Log.w(TAG, "⚠️ Token refresh returned null")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to refresh Firebase token", e)
            // If token refresh fails, the user might need to re-authenticate
            handleTokenRefreshFailure(e)
        }
    }
    
    /**
     * Handle token refresh failure
     */
    private fun handleTokenRefreshFailure(error: Exception) {
        Log.e(TAG, "Token refresh failed: ${error.message}")
        
        // Check if it's an authentication error
        when {
            error.message?.contains("invalid_token", ignoreCase = true) == true -> {
                Log.w(TAG, "Invalid token detected - user may need to re-authenticate")
            }
            error.message?.contains("network", ignoreCase = true) == true -> {
                Log.w(TAG, "Network error during token refresh - will retry")
            }
            else -> {
                Log.w(TAG, "Unknown token refresh error: ${error.message}")
            }
        }
    }
    
    /**
     * Manually trigger token refresh (for immediate refresh if needed)
     */
    fun refreshTokenNow() {
        serviceScope.launch {
            refreshFirebaseToken()
        }
    }
    
    /**
     * Check if token refresh is currently running
     */
    fun isRefreshRunning(): Boolean {
        return refreshJob?.isActive == true
    }
    
    /**
     * Get time until next refresh
     */
    fun getTimeUntilNextRefresh(): Long {
        // This is a simplified implementation
        // In a real app, you might want to track the last refresh time
        return REFRESH_INTERVAL_SECONDS
    }
    
    companion object {
        /**
         * Start the token refresh service
         */
        fun startService(context: android.content.Context) {
            val intent = Intent(context, TokenRefreshService::class.java)
            context.startService(intent)
        }
        
        /**
         * Stop the token refresh service
         */
        fun stopService(context: android.content.Context) {
            val intent = Intent(context, TokenRefreshService::class.java)
            context.stopService(intent)
        }
    }
}
