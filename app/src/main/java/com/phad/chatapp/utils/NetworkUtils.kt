package com.phad.chatapp.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.ChatApplication
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Utility class to check network and Firebase connectivity
 */
object NetworkUtils {
    private const val TAG = "NetworkUtils"
    private const val CONNECTION_TEST_TIMEOUT_MS = 10000L // 10 seconds

    /**
     * Check if the device has internet connectivity
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Check if Firebase is properly configured
     */
    fun isFirebaseConfigured(context: Context): Boolean {
        return try {
            FirebaseApp.getInstance() != null
        } catch (e: Exception) {
            Log.e(TAG, "Firebase not configured properly", e)
            false
        }
    }
    
    /**
     * Perform a detailed diagnostic check on Firestore connectivity
     * Returns a Pair of (isConnected, detailedMessage)
     */
    suspend fun checkFirestoreConnectivity(): Pair<Boolean, String> {
        if (!isFirebaseConfigured(ChatApplication.instance)) {
            return Pair(false, "Firebase not configured properly")
        }
        
        if (!isNetworkAvailable(ChatApplication.instance)) {
            return Pair(false, "No network connection available")
        }
        
        return try {
            val db = FirebaseFirestore.getInstance()
            
            // Try to ping Firestore by getting a simple document
            val result = withTimeoutOrNull(CONNECTION_TEST_TIMEOUT_MS) {
                // Test query to check connectivity - can be any collection
                db.collection("_connectivity_test_").document("test").get().await()
            }
            
            if (result != null) {
                Pair(true, "Firestore connection successful")
            } else {
                // Try a different approach - simple metadata operation
                val metadataResult = withTimeoutOrNull(CONNECTION_TEST_TIMEOUT_MS) {
                    db.collection("Admin1").get().await()
                    true
                } ?: false
                
                if (metadataResult) {
                    Pair(true, "Firestore connection successful")
                } else {
                    Pair(false, "Firestore connection timed out")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Firestore connectivity", e)
            
            // Provide more specific error messages based on exception type
            val errorMessage = when {
                e.message?.contains("PERMISSION_DENIED") == true -> 
                    "Firestore security rules are preventing access. Check your Firebase console."
                e.message?.contains("NOT_FOUND") == true -> 
                    "Firestore project not found. Verify your google-services.json file."
                e.message?.contains("UNAUTHENTICATED") == true -> 
                    "Authentication required for Firestore access."
                else -> "Firestore error: ${e.message}"
            }
            
            Pair(false, errorMessage)
        }
    }
} 