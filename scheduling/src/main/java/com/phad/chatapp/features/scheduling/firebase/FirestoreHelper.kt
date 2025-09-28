package com.phad.chatapp.features.scheduling.firebase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.tasks.await

/**
 * Helper class to manage Firestore operations in the scheduling module
 * This class provides a consistent way to access Firestore without depending on the main app's MultiDatabaseHelper
 */
object FirestoreHelper {
    private const val TAG = "FirestoreHelper"
    
    /**
     * Get the Firestore instance with persistence enabled
     */
    fun getFirestore(): FirebaseFirestore {
        val db = FirebaseFirestore.getInstance()
        
        try {
            // Enable offline persistence
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            db.firestoreSettings = settings
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable Firestore persistence", e)
        }
        
        return db
    }
    
    /**
     * Check if the device is connected to Firestore
     * This is used to determine if we can perform write operations
     */
    suspend fun isConnectedToFirestore(): Boolean {
        return try {
            val db = getFirestore()
            
            // Try to write a small test document
            val testDoc = hashMapOf(
                "timestamp" to com.google.firebase.Timestamp.now(),
                "test" to "connectivity_check"
            )
            
            // Use a unique collection name to avoid conflicts
            db.collection("_connectivity_test_")
                .document("test_${System.currentTimeMillis()}")
                .set(testDoc)
                .await()
                
            true
        } catch (e: Exception) {
            Log.e(TAG, "Firestore connectivity check failed", e)
            false
        }
    }
} 