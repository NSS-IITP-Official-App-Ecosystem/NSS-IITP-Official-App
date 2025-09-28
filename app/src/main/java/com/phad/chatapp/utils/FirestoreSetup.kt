package com.phad.chatapp.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.phad.chatapp.models.Admin
import com.phad.chatapp.models.Student
import java.io.File

/**
 * Utility class for setting up sample data in Firestore
 * This would be used during development to populate test data
 */
object FirestoreSetup {
    private val TAG = "FirestoreSetup"
    private val firestore = FirebaseFirestore.getInstance()
    
    /**
     * Call this method to populate Firestore with sample data
     */
    fun setupSampleData() {
        Log.d(TAG, "Setting up sample data in Firestore")
        // We don't create any collections automatically anymore
        Log.d(TAG, "Sample data setup is disabled to prevent unwanted collection creation")
    }

    /**
     * Diagnose and attempt to fix common Firebase configuration issues
     * @return A pair of (isFixed, message) where isFixed indicates if the problem was resolved
     */
    fun diagnoseAndFixFirebaseConfig(context: Context): Pair<Boolean, String> {
        // First check if FirebaseApp is initialized
        val isInitialized = try {
            FirebaseApp.getInstance() != null
        } catch (e: Exception) {
            false
        }

        if (!isInitialized) {
            try {
                // Try to initialize Firebase
                FirebaseApp.initializeApp(context)
                return Pair(true, "Firebase initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Firebase", e)
                return Pair(false, "Failed to initialize Firebase: ${e.message}")
            }
        }

        // Don't check for google-services.json file anymore since it's not reliable at runtime
        // Instead, try a simple Firestore operation directly
        return try {
            Log.d(TAG, "Testing Firestore connectivity...")
            firestore.collection("_connectivity_test_").document("test").set(mapOf("timestamp" to System.currentTimeMillis()))
                .addOnSuccessListener {
                    Log.d(TAG, "Firestore test succeeded")
                    // Clean up test document
                    firestore.collection("_connectivity_test_").document("test").delete()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firestore test failed", e)
                    
                    // Log detailed error information
                    if (e is FirebaseFirestoreException) {
                        Log.e(TAG, "Firestore error code: ${e.code}")
                    }
                }
            
            Pair(true, "Firebase configuration appears valid. Check network connection and Firebase console for security rules issues.")
        } catch (e: Exception) {
            Log.e(TAG, "Error testing Firestore", e)
            return Pair(false, "Error testing Firestore: ${e.message}")
        }
    }
} 