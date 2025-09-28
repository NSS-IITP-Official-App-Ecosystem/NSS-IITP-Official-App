package com.phad.chatapp.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Utility for accessing Coordinator (Admin2) data from TWApp database
 */
object Admin2DataViewer {
    private const val TAG = "Admin2DataViewer"
    
    /**
     * Get all coordinators from TWApp database
     */
    suspend fun getAllCoordinators(): List<Map<String, Any>> {
        try {
            val db = MultiDatabaseHelper.getSecondaryFirestore()
            val coordinators = db.collection("coordinators")
                .get()
                .await()
                .documents
                .mapNotNull { it.data }
            
            Log.d(TAG, "Retrieved ${coordinators.size} coordinators from TWApp")
            return coordinators
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving coordinators", e)
            throw e
        }
    }
    
    /**
     * Get a specific coordinator by roll number
     */
    suspend fun getCoordinatorByRollNumber(rollNumber: String): Map<String, Any>? {
        try {
            val db = MultiDatabaseHelper.getSecondaryFirestore()
            val coordinator = db.collection("coordinators")
                .whereEqualTo("rollNumber", rollNumber)
                .get()
                .await()
                .documents
                .firstOrNull()
                ?.data
            
            Log.d(TAG, "Retrieved coordinator with roll number $rollNumber: ${coordinator != null}")
            return coordinator
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving coordinator with roll number $rollNumber", e)
            throw e
        }
    }
    
    /**
     * Get all coordinators and subcoordinators combined
     */
    suspend fun getAllAdmins(): List<Map<String, Any>> {
        try {
            val coordinators = getAllCoordinators()
            val subcoordinators = Admin1DataViewer.getAllAdmins()
            
            // Combine the lists
            val allAdmins = coordinators + subcoordinators
            
            Log.d(TAG, "Combined ${coordinators.size} coordinators and ${subcoordinators.size} subcoordinators")
            return allAdmins
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving all admins", e)
            throw e
        }
    }
}
