package com.phad.chatapp.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Utility for uploading student data to the TWApp secondary database
 */
object StudentDataUploader {
    private const val TAG = "StudentDataUploader"
    
    /**
     * Upload a single student to the TWApp database
     */
    suspend fun uploadStudent(
        rollNumber: String,
        name: String,
        email: String,
        phone: String,
        year: Int,
        branch: String,
        section: String,
        preferences: List<String>
    ): Boolean {
        try {
            // Get the secondary Firestore instance
            val db = MultiDatabaseHelper.getSecondaryFirestore()
            
            // Check if student already exists
            val existingStudents = db.collection("students")
                .whereEqualTo("rollNumber", rollNumber)
                .get()
                .await()
                .documents
            
            // Create the student data map
            val studentData = hashMapOf(
                "rollNumber" to rollNumber,
                "name" to name,
                "email" to email,
                "phone" to phone,
                "year" to year,
                "branch" to branch,
                "section" to section,
                "preferences" to preferences,
                "lastUpdated" to com.google.firebase.Timestamp.now()
            )
            
            if (existingStudents.isEmpty()) {
                // Create a new student document
                db.collection("students")
                    .document(UUID.randomUUID().toString())
                    .set(studentData)
                    .await()
                
                Log.d(TAG, "Created new student: $rollNumber")
            } else {
                // Update existing student
                db.collection("students")
                    .document(existingStudents.first().id)
                    .update(studentData)
                    .await()
                
                Log.d(TAG, "Updated existing student: $rollNumber")
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading student: $rollNumber", e)
            return false
        }
    }
    
    /**
     * Upload a batch of students to the TWApp database
     */
    suspend fun uploadStudentBatch(students: List<Map<String, Any>>): Int {
        var successCount = 0
        
        for (student in students) {
            try {
                val rollNumber = student["rollNumber"] as? String ?: continue
                val name = student["name"] as? String ?: continue
                val email = student["email"] as? String ?: ""
                val phone = student["phone"] as? String ?: ""
                val year = (student["year"] as? Number)?.toInt() ?: 0
                val branch = student["branch"] as? String ?: ""
                val section = student["section"] as? String ?: ""
                val preferences = student["preferences"] as? List<String> ?: emptyList()
                
                val success = uploadStudent(
                    rollNumber = rollNumber,
                    name = name,
                    email = email,
                    phone = phone,
                    year = year,
                    branch = branch,
                    section = section,
                    preferences = preferences
                )
                
                if (success) {
                    successCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing student in batch upload", e)
            }
        }
        
        return successCount
    }
}
