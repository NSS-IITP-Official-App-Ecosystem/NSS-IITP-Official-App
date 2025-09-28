package com.phad.chatapp.repositories

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.phad.chatapp.models.Attendance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Repository class for handling attendance-related operations with Firestore
 */
class AttendanceRepository {
    private val TAG = "AttendanceRepository"
    private val db = FirebaseFirestore.getInstance()
    private val attendanceCollection = db.collection("attendances")
    private val usersCollection = db.collection("users")
    
    /**
     * Add a new attendance record to the database
     */
    suspend fun addAttendance(attendance: Attendance): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Adding attendance record: ${attendance.id}")
            
            attendanceCollection.document(attendance.id)
                .set(attendance)
                .await()
            
            Log.d(TAG, "Attendance record added successfully")
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding attendance record", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Get an attendance record by ID
     */
    suspend fun getAttendance(id: String): Result<Attendance?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting attendance record: $id")
            
            val document = attendanceCollection.document(id).get().await()
            
            return@withContext if (document.exists()) {
                val attendance = document.toObject(Attendance::class.java)
                Log.d(TAG, "Attendance record found: $attendance")
                Result.success(attendance)
            } else {
                Log.d(TAG, "Attendance record not found")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting attendance record", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Update the status of an attendance record
     */
    suspend fun updateAttendanceStatus(id: String, status: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating attendance status: $id -> $status")
            
            attendanceCollection.document(id)
                .update("status", status)
                .await()
            
            Log.d(TAG, "Attendance status updated successfully")
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating attendance status", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Get attendance records by status
     */
    suspend fun getAttendancesByStatus(status: String): Result<List<Attendance>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting attendances by status: $status")
            
            val querySnapshot = attendanceCollection
                .whereEqualTo("status", status)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val attendances = querySnapshot.documents.mapNotNull { 
                it.toObject(Attendance::class.java) 
            }
            
            Log.d(TAG, "Found ${attendances.size} attendance records")
            return@withContext Result.success(attendances)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting attendances by status", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Get attendance records by roll number (coroutine version)
     */
    suspend fun getAttendancesByRollNumber(rollNumber: String): Result<List<Attendance>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting attendances by roll number: $rollNumber")
            
            val querySnapshot = attendanceCollection
                .whereEqualTo("rollNumber", rollNumber)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val attendances = querySnapshot.documents.mapNotNull { 
                it.toObject(Attendance::class.java) 
            }
            
            Log.d(TAG, "Found ${attendances.size} attendance records")
            return@withContext Result.success(attendances)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting attendances by roll number", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Get attendance records by date range
     */
    suspend fun getAttendancesByDateRange(startDate: Date, endDate: Date): Result<List<Attendance>> = 
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Getting attendances by date range: $startDate to $endDate")
                
                val querySnapshot = attendanceCollection
                    .whereGreaterThanOrEqualTo("timestamp", startDate)
                    .whereLessThanOrEqualTo("timestamp", endDate)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .await()
                
                val attendances = querySnapshot.documents.mapNotNull { 
                    it.toObject(Attendance::class.java) 
                }
                
                Log.d(TAG, "Found ${attendances.size} attendance records")
                return@withContext Result.success(attendances)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting attendances by date range", e)
                return@withContext Result.failure(e)
            }
        }
    
    /**
     * Get all pending attendance records for admin approval
     * @return Task with query snapshot containing pending attendances
     */
    fun getPendingAttendances(): Task<QuerySnapshot> {
        Log.d(TAG, "Fetching pending attendances")
        return attendanceCollection
            .whereEqualTo("status", "pending")
            .whereEqualTo("verification_method", "manual")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                Log.d(TAG, "Found ${snapshot.size()} pending attendances")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching pending attendances", e)
            }
    }
    
    /**
     * Get attendance records for a specific user (student) - Task-based version
     * @param rollNumber The roll number of the student
     * @return Task with query snapshot containing the student's attendances
     */
    fun getAttendancesByRollNumberTask(rollNumber: String): Task<QuerySnapshot> {
        Log.d(TAG, "Fetching attendances for roll number: $rollNumber")
        return attendanceCollection
            .whereEqualTo("roll_number", rollNumber)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                Log.d(TAG, "Found ${snapshot.size()} attendances for roll number: $rollNumber")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching attendances for roll number: $rollNumber", e)
            }
    }
    
    /**
     * Get reference image URL for a user
     * @param rollNumber The roll number of the user
     * @return Task with document snapshot containing user data
     */
    fun getUserReferenceImageUrl(rollNumber: String): Task<DocumentSnapshot> {
        Log.d(TAG, "Fetching reference image URL for roll number: $rollNumber")
        return usersCollection.document(rollNumber).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val imageUrl = doc.getString("reference_image_url")
                    Log.d(TAG, "Reference image URL for $rollNumber: $imageUrl")
                } else {
                    Log.w(TAG, "No user document found for roll number: $rollNumber")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching user reference image", e)
            }
    }
    
    /**
     * Update user's reference image URL
     * @param rollNumber The roll number of the user
     * @param imageUrl The URL of the reference image
     * @return Task for the update operation
     */
    fun updateUserReferenceImage(rollNumber: String, imageUrl: String): Task<Void> {
        Log.d(TAG, "Updating reference image URL for roll number: $rollNumber")
        return usersCollection.document(rollNumber)
            .update("reference_image_url", imageUrl)
            .addOnSuccessListener {
                Log.d(TAG, "Reference image URL updated successfully for $rollNumber")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating reference image URL for $rollNumber", e)
            }
    }
    
    /**
     * Delete a temporary image from the attendance record after processing
     * @param attendanceId The ID of the attendance record
     * @return Task for the update operation
     */
    fun deleteTemporaryImage(attendanceId: String): Task<Void> {
        Log.d(TAG, "Deleting temporary image for attendance: $attendanceId")
        return attendanceCollection.document(attendanceId)
            .update("submitted_image_url", null)
            .addOnSuccessListener {
                Log.d(TAG, "Temporary image deleted successfully for attendance: $attendanceId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error deleting temporary image for attendance: $attendanceId", e)
            }
    }
} 