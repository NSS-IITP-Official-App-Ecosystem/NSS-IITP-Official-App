package com.phad.chatapp.utils

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Helper class to interact with student data in the secondary Firestore database
 */
class StudentDataHelper {
    companion object {
        private const val TAG = "StudentDataHelper"
        private const val COLLECTION_NAME = "users"
        
        /**
         * Get all students from the secondary database
         */
        suspend fun getAllStudents(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
            try {
                val db = MultiDatabaseHelper.getSecondaryFirestore()
                val snapshot = db.collection(COLLECTION_NAME).get().await()
                return@withContext snapshot.documents.mapNotNull { it.data }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting all students", e)
                return@withContext emptyList()
            }
        }
        
        /**
         * Get a student by roll number
         */
        suspend fun getStudentByRollNumber(rollNumber: String): Map<String, Any>? = withContext(Dispatchers.IO) {
            try {
                val db = MultiDatabaseHelper.getSecondaryFirestore()
                val document = db.collection(COLLECTION_NAME).document(rollNumber).get().await()
                return@withContext if (document.exists()) document.data else null
            } catch (e: Exception) {
                Log.e(TAG, "Error getting student with roll number: $rollNumber", e)
                return@withContext null
            }
        }
        
        /**
         * Get students by criteria (e.g., selected = 'Yes')
         */
        suspend fun getStudentsByCriteria(field: String, value: Any): List<Map<String, Any>> = withContext(Dispatchers.IO) {
            try {
                val db = MultiDatabaseHelper.getSecondaryFirestore()
                val snapshot = db.collection(COLLECTION_NAME)
                    .whereEqualTo(field, value)
                    .get()
                    .await()
                return@withContext snapshot.documents.mapNotNull { it.data }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting students by criteria: $field = $value", e)
                return@withContext emptyList()
            }
        }
        
        /**
         * Add or update a student in the secondary database
         */
        fun updateStudent(rollNumber: String, data: Map<String, Any>): Task<Void> {
            val db = MultiDatabaseHelper.getSecondaryFirestore()
            return db.collection(COLLECTION_NAME).document(rollNumber).set(data)
        }
        
        /**
         * Delete a student from the secondary database
         */
        fun deleteStudent(rollNumber: String): Task<Void> {
            val db = MultiDatabaseHelper.getSecondaryFirestore()
            return db.collection(COLLECTION_NAME).document(rollNumber).delete()
        }
        
        /**
         * Convert student data to User model for the primary database
         * This can be used to sync data between databases if needed
         */
        fun convertToUserModel(studentData: Map<String, Any>): Map<String, Any?> {
            val rollNumber = studentData["Roll No."]?.toString() ?: ""
            
            return mapOf(
                "name" to (studentData["Name"]?.toString() ?: ""),
                "email" to (studentData["Gmail ID"]?.toString() ?: ""),
                "roll_number" to rollNumber,
                "user_type" to "student",
                "description" to "",
                "contact_number" to (studentData["Mobile no."]?.toString() ?: ""),
                "year" to "1",
                "profile_image_url" to null
            )
        }
    }
} 