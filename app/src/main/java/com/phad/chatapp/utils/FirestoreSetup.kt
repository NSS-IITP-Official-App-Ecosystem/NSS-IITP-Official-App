package com.phad.chatapp.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.phad.chatapp.models.Admin
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
        // Instead, perform a non-creating read to validate Firestore access
        return try {
            Log.d(TAG, "Testing Firestore connectivity with non-creating read...")
            firestore.collection("users").limit(1).get()
                .addOnSuccessListener {
                    Log.d(TAG, "Firestore read test succeeded")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firestore read test failed", e)
                    if (e is FirebaseFirestoreException) {
                        Log.e(TAG, "Firestore error code: ${e.code}")
                    }
                }
            Pair(true, "Firebase configuration appears valid. If issues persist, check network and security rules.")
        } catch (e: Exception) {
            Log.e(TAG, "Error testing Firestore", e)
            return Pair(false, "Error testing Firestore: ${e.message}")
        }
    }
} 