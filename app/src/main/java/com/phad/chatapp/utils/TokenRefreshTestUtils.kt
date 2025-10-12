package com.phad.chatapp.utils

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*

/**
 * Test utility for verifying token refresh functionality
 * This can be used to manually test the token refresh implementation
 */
class TokenRefreshTestUtils {
    private val TAG = "TokenRefreshTestUtils"
    
    companion object {
        /**
         * Test token refresh functionality
         * Call this from any activity or fragment to verify the implementation
         */
        fun testTokenRefresh(context: Context) {
            val sessionManager = SessionManager(context)
            
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    Log.d("TokenRefreshTest", "=== TESTING TOKEN REFRESH ===")
                    
                    // Test 1: Check current auth state
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    Log.d("TokenRefreshTest", "Current user: ${currentUser?.email ?: "null"}")
                    
                    // Test 2: Test manual token refresh
                    Log.d("TokenRefreshTest", "Testing manual token refresh...")
                    val refreshSuccess = sessionManager.refreshFirebaseToken()
                    Log.d("TokenRefreshTest", "Manual refresh result: $refreshSuccess")
                    
                    // Test 3: Test ensureValidFirebaseToken
                    Log.d("TokenRefreshTest", "Testing ensureValidFirebaseToken...")
                    val ensureValid = sessionManager.ensureValidFirebaseToken()
                    Log.d("TokenRefreshTest", "Ensure valid result: $ensureValid")
                    
                    // Test 4: Start token refresh service
                    Log.d("TokenRefreshTest", "Starting token refresh service...")
                    sessionManager.startTokenRefreshService()
                    Log.d("TokenRefreshTest", "Token refresh service started")
                    
                    Log.d("TokenRefreshTest", "=== TOKEN REFRESH TEST COMPLETED ===")
                    
                } catch (e: Exception) {
                    Log.e("TokenRefreshTest", "Error during token refresh test", e)
                }
            }
        }
        
        /**
         * Test token expiration simulation
         * This simulates what happens when a token expires
         */
        fun testTokenExpirationScenario(context: Context) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    Log.d("TokenRefreshTest", "=== TESTING TOKEN EXPIRATION SCENARIO ===")
                    
                    val sessionManager = SessionManager(context)
                    
                    // Simulate expired token by checking current state
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser == null) {
                        Log.d("TokenRefreshTest", "No current user - simulating expired token scenario")
                        
                        // Test the repository's ensureValidFirebaseAuth method
                        val repository = com.phad.chatapp.repositories.AttendanceQRRepository()
                        repository.setSessionManager(sessionManager)
                        
                        // This should attempt to refresh the token
                        val authValid = repository.javaClass.getDeclaredMethod("ensureValidFirebaseAuth").invoke(repository) as Boolean
                        Log.d("TokenRefreshTest", "Repository auth validation result: $authValid")
                    } else {
                        Log.d("TokenRefreshTest", "User is authenticated: ${currentUser.email}")
                        Log.d("TokenRefreshTest", "Token expiration test not applicable - user is logged in")
                    }
                    
                    Log.d("TokenRefreshTest", "=== TOKEN EXPIRATION SCENARIO TEST COMPLETED ===")
                    
                } catch (e: Exception) {
                    Log.e("TokenRefreshTest", "Error during token expiration test", e)
                }
            }
        }
        
        /**
         * Log current authentication state for debugging
         */
        fun logAuthState(context: Context) {
            val sessionManager = SessionManager(context)
            
            Log.d("TokenRefreshTest", "=== CURRENT AUTH STATE ===")
            Log.d("TokenRefreshTest", "SessionManager.isLoggedIn(): ${sessionManager.isLoggedIn()}")
            Log.d("TokenRefreshTest", "FirebaseAuth.currentUser: ${FirebaseAuth.getInstance().currentUser?.email ?: "null"}")
            Log.d("TokenRefreshTest", "User ID: ${sessionManager.fetchUserId()}")
            Log.d("TokenRefreshTest", "User Type: ${sessionManager.fetchUserType()}")
            Log.d("TokenRefreshTest", "=== END AUTH STATE ===")
        }
    }
}
