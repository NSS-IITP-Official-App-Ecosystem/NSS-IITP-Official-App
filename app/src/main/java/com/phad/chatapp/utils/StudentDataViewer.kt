package com.phad.chatapp.utils

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Utility class to fetch and display student data from the TWApp database
 */
class StudentDataViewer {
    companion object {
        private const val TAG = "StudentDataViewer"
        private const val COLLECTION_NAME = "Student"
        
        /**
         * Get all students from the TWApp database
         */
        suspend fun getAllStudents(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
            try {
                val db = MultiDatabaseHelper.getSecondaryFirestore()
                val snapshot = db.collection(COLLECTION_NAME).get().await()
                val students = snapshot.documents.mapNotNull { it.data }
                Log.d(TAG, "Successfully fetched ${students.size} students from TWApp database")
                return@withContext students
            } catch (e: Exception) {
                Log.e(TAG, "Error getting all students from TWApp", e)
                return@withContext emptyList()
            }
        }
        
        /**
         * Get a student by roll number from the TWApp database
         */
        suspend fun getStudentByRollNumber(rollNumber: String): Map<String, Any>? = withContext(Dispatchers.IO) {
            try {
                val db = MultiDatabaseHelper.getSecondaryFirestore()
                val document = db.collection(COLLECTION_NAME).document(rollNumber).get().await()
                val student = if (document.exists()) document.data else null
                Log.d(TAG, "Fetched student with roll number $rollNumber from TWApp: ${student != null}")
                return@withContext student
            } catch (e: Exception) {
                Log.e(TAG, "Error getting student with roll number: $rollNumber from TWApp", e)
                return@withContext null
            }
        }
        
        /**
         * Get students by criteria (e.g., selected = 'Yes')
         */
        suspend fun getStudentsByCriteria(field: String, value: Any): List<Map<String, Any>> = withContext(Dispatchers.IO) {
            try {
                val db = MultiDatabaseHelper.getSecondaryFirestore()
                
                // Convert field name to match the format in the database
                val dbField = field.toLowerCase().replace(" ", "_").replace(".", "").replace("/", "_")
                
                val snapshot = db.collection(COLLECTION_NAME)
                    .whereEqualTo(dbField, value)
                    .get()
                    .await()
                val students = snapshot.documents.mapNotNull { it.data }
                Log.d(TAG, "Successfully fetched ${students.size} students with $dbField = $value from TWApp")
                return@withContext students
            } catch (e: Exception) {
                Log.e(TAG, "Error getting students by criteria: $field = $value from TWApp", e)
                return@withContext emptyList()
            }
        }
        
        /**
         * Fetch students that match specific subject preferences
         */
        suspend fun getStudentsBySubjectPreference(subject: String): List<Map<String, Any>> = withContext(Dispatchers.IO) {
            try {
                val allStudents = getAllStudents()
                
                // Filter students that have the given subject as one of their preferences
                val matchingStudents = allStudents.filter { student ->
                    val pref1 = student["subjec_prefrence_1"] as? String // Note the field naming matches DB
                    val pref2 = student["sub_preference2"] as? String
                    val pref3 = student["sub_preference_3"] as? String
                    
                    subject.equals(pref1, ignoreCase = true) || 
                    subject.equals(pref2, ignoreCase = true) || 
                    subject.equals(pref3, ignoreCase = true)
                }
                
                Log.d(TAG, "Found ${matchingStudents.size} students with preference for $subject")
                return@withContext matchingStudents
            } catch (e: Exception) {
                Log.e(TAG, "Error getting students by subject preference: $subject", e)
                return@withContext emptyList()
            }
        }
        
        /**
         * Get selected students with a specific preference order
         * For example, get students who selected "Physics" as their first preference
         */
        suspend fun getStudentsBySpecificPreference(subject: String, preferenceNumber: Int): List<Map<String, Any>> = withContext(Dispatchers.IO) {
            try {
                val db = MultiDatabaseHelper.getSecondaryFirestore()
                
                // Determine which field to query based on preference number
                val fieldName = when (preferenceNumber) {
                    1 -> "subjec_prefrence_1" // Field name matches what's in the database
                    2 -> "sub_preference2"
                    3 -> "sub_preference_3"
                    else -> throw IllegalArgumentException("Preference number must be 1, 2, or 3")
                }
                
                // First get students who have this subject preference
                val snapshot = db.collection(COLLECTION_NAME)
                    .whereEqualTo(fieldName, subject)
                    .whereEqualTo("selected", "Yes")  // Only selected students
                    .get()
                    .await()
                    
                val students = snapshot.documents.mapNotNull { it.data }
                Log.d(TAG, "Found ${students.size} selected students with $subject as preference $preferenceNumber")
                return@withContext students
            } catch (e: Exception) {
                Log.e(TAG, "Error getting students by specific preference: $subject (#$preferenceNumber)", e)
                return@withContext emptyList()
            }
        }
        
        /**
         * Get students by NSS group
         */
        suspend fun getStudentsByNssGroup(nssGroup: String): List<Map<String, Any>> = withContext(Dispatchers.IO) {
            try {
                val db = MultiDatabaseHelper.getSecondaryFirestore()
                val snapshot = db.collection(COLLECTION_NAME)
                    .whereEqualTo("nss_gro", nssGroup) // Field name matches what's in the database
                    .get()
                    .await()
                    
                val students = snapshot.documents.mapNotNull { it.data }
                Log.d(TAG, "Found ${students.size} students in NSS group $nssGroup")
                return@withContext students
            } catch (e: Exception) {
                Log.e(TAG, "Error getting students by NSS group: $nssGroup", e)
                return@withContext emptyList()
            }
        }
    }
} 