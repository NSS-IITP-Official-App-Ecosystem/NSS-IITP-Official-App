package com.phad.chatapp.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Utility class to fetch and display admin data from the TWApp database
 */
class Admin1DataViewer {
    companion object {
        private const val TAG = "Admin1DataViewer"
        private const val COLLECTION_NAME = "Admin1"
        
        /**
         * Get all admins from the TWApp database
         */
        suspend fun getAllAdmins(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
            try {
                val db = MultiDatabaseHelper.getSecondaryFirestore()
                val snapshot = db.collection(COLLECTION_NAME).get().await()
                val admins = snapshot.documents.mapNotNull { it.data }
                Log.d(TAG, "Successfully fetched ${admins.size} admins from TWApp database")
                return@withContext admins
            } catch (e: Exception) {
                Log.e(TAG, "Error getting all admins from TWApp", e)
                return@withContext emptyList()
            }
        }
        
        /**
         * Get an admin by roll number from the TWApp database
         */
        suspend fun getAdminByRollNumber(rollNumber: String): Map<String, Any>? = withContext(Dispatchers.IO) {
            try {
                val db = MultiDatabaseHelper.getSecondaryFirestore()
                val document = db.collection(COLLECTION_NAME).document(rollNumber).get().await()
                val admin = if (document.exists()) document.data else null
                Log.d(TAG, "Fetched admin with roll number $rollNumber from TWApp: ${admin != null}")
                return@withContext admin
            } catch (e: Exception) {
                Log.e(TAG, "Error getting admin with roll number: $rollNumber from TWApp", e)
                return@withContext null
            }
        }
        
        /**
         * Get admins by criteria (e.g., committee = 'Core Committee')
         */
        suspend fun getAdminsByCriteria(field: String, value: Any): List<Map<String, Any>> = withContext(Dispatchers.IO) {
            try {
                val db = MultiDatabaseHelper.getSecondaryFirestore()
                
                // Convert field name to match the format in the database
                val dbField = field.toLowerCase().replace(" ", "_").replace(".", "").replace("/", "_")
                
                val snapshot = db.collection(COLLECTION_NAME)
                    .whereEqualTo(dbField, value)
                    .get()
                    .await()
                val admins = snapshot.documents.mapNotNull { it.data }
                Log.d(TAG, "Successfully fetched ${admins.size} admins with $dbField = $value from TWApp")
                return@withContext admins
            } catch (e: Exception) {
                Log.e(TAG, "Error getting admins by criteria: $field = $value from TWApp", e)
                return@withContext emptyList()
            }
        }
        
        /**
         * Get admins by designation (e.g., "Subcoordinator")
         */
        suspend fun getAdminsByDesignation(designation: String): List<Map<String, Any>> = withContext(Dispatchers.IO) {
            try {
                val db = MultiDatabaseHelper.getSecondaryFirestore()
                val snapshot = db.collection(COLLECTION_NAME)
                    .whereEqualTo("designation", designation)
                    .get()
                    .await()
                    
                val admins = snapshot.documents.mapNotNull { it.data }
                Log.d(TAG, "Found ${admins.size} admins with designation $designation")
                return@withContext admins
            } catch (e: Exception) {
                Log.e(TAG, "Error getting admins by designation: $designation", e)
                return@withContext emptyList()
            }
        }
        
        /**
         * Get admins by committee (e.g., "Core Commitee")
         */
        suspend fun getAdminsByCommittee(committee: String): List<Map<String, Any>> = withContext(Dispatchers.IO) {
            try {
                val db = MultiDatabaseHelper.getSecondaryFirestore()
                val snapshot = db.collection(COLLECTION_NAME)
                    .whereEqualTo("commitee_code", committee)
                    .get()
                    .await()
                    
                val admins = snapshot.documents.mapNotNull { it.data }
                Log.d(TAG, "Found ${admins.size} admins in committee $committee")
                return@withContext admins
            } catch (e: Exception) {
                Log.e(TAG, "Error getting admins by committee: $committee", e)
                return@withContext emptyList()
            }
        }
    }
} 