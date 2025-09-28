package com.phad.chatapp.services

import android.net.Uri
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.phad.chatapp.models.Attendance
import com.phad.chatapp.repositories.AttendanceRepository
import com.phad.chatapp.repositories.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class AttendanceService(
    private val attendanceRepository: AttendanceRepository,
    private val userRepository: UserRepository,
    private val cloudinaryService: CloudinaryService
) {
    private val TAG = "AttendanceService"

    /**
     * Submit attendance with image verification
     */
    suspend fun submitAttendance(
        rollNumber: String,
        imageUri: Uri,
        location: GeoPoint?,
        autoApproved: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting attendance submission for roll number: $rollNumber")
            
            // Upload the image to Cloudinary
            val uploadResult = cloudinaryService.uploadImage(imageUri)
            if (uploadResult.isFailure) {
                Log.e(TAG, "Failed to upload image: ${uploadResult.exceptionOrNull()?.message}")
                return@withContext Result.failure(uploadResult.exceptionOrNull() ?: Exception("Failed to upload image"))
            }
            
            val imageUrl = uploadResult.getOrNull()
            if (imageUrl.isNullOrEmpty()) {
                Log.e(TAG, "Image URL is null or empty after upload")
                return@withContext Result.failure(Exception("Failed to get image URL"))
            }
            
            Log.d(TAG, "Image uploaded successfully: $imageUrl")
            
            // Create attendance record
            val attendance = Attendance(
                id = UUID.randomUUID().toString(),
                rollNumber = rollNumber,
                timestamp = Timestamp.now(),
                location = location,
                imageUrl = imageUrl,
                status = if (autoApproved) Attendance.STATUS_APPROVED else Attendance.STATUS_PENDING,
                verificationMethod = Attendance.VERIFICATION_FACE_RECOGNITION
            )
            
            // Save attendance to repository
            val result = attendanceRepository.addAttendance(attendance)
            if (result.isFailure) {
                Log.e(TAG, "Failed to save attendance: ${result.exceptionOrNull()?.message}")
                return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Failed to save attendance"))
            }
            
            Log.d(TAG, "Attendance submitted successfully with ID: ${attendance.id}")
            return@withContext Result.success(attendance.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting attendance", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Compare two face images to determine if they match
     */
    suspend fun compareFaces(submittedImageUrl: String, referenceImageUrl: String): Result<Boolean> = 
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Comparing faces: submitted=$submittedImageUrl, reference=$referenceImageUrl")
                
                // Call the face recognition API
                val result = suspendCoroutine<Boolean> { continuation ->
                    // TODO: Replace with actual API call to face recognition service
                    // For now, we'll simulate a successful comparison
                    val isMatch = true
                    
                    if (isMatch) {
                        continuation.resume(true)
                    } else {
                        continuation.resume(false)
                    }
                }
                
                Log.d(TAG, "Face comparison result: $result")
                return@withContext Result.success(result)
            } catch (e: Exception) {
                Log.e(TAG, "Error comparing faces", e)
                return@withContext Result.failure(e)
            }
        }

    /**
     * Approve an attendance request
     */
    suspend fun approveAttendance(attendanceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Approving attendance with ID: $attendanceId")
            
            val result = attendanceRepository.updateAttendanceStatus(
                attendanceId, 
                Attendance.STATUS_APPROVED
            )
            
            if (result.isSuccess) {
                Log.d(TAG, "Attendance approved successfully")
            } else {
                Log.e(TAG, "Failed to approve attendance: ${result.exceptionOrNull()?.message}")
            }
            
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Error approving attendance", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Reject an attendance request
     */
    suspend fun rejectAttendance(attendanceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Rejecting attendance with ID: $attendanceId")
            
            val result = attendanceRepository.updateAttendanceStatus(
                attendanceId, 
                Attendance.STATUS_REJECTED
            )
            
            if (result.isSuccess) {
                Log.d(TAG, "Attendance rejected successfully")
            } else {
                Log.e(TAG, "Failed to reject attendance: ${result.exceptionOrNull()?.message}")
            }
            
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting attendance", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Get all pending attendance requests
     */
    suspend fun getPendingAttendances(): Result<List<Attendance>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching pending attendance requests")
            
            val result = attendanceRepository.getAttendancesByStatus(Attendance.STATUS_PENDING)
            
            if (result.isSuccess) {
                val attendances = result.getOrNull() ?: emptyList()
                Log.d(TAG, "Found ${attendances.size} pending attendance requests")
            } else {
                Log.e(TAG, "Failed to fetch pending attendances: ${result.exceptionOrNull()?.message}")
            }
            
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching pending attendances", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Get user's reference image URL
     */
    suspend fun getUserReferenceImageUrl(rollNumber: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting reference image URL for roll number: $rollNumber")
            
            val userResult = userRepository.getUserByRollNumber(rollNumber)
            if (userResult.isFailure) {
                Log.e(TAG, "Failed to get user: ${userResult.exceptionOrNull()?.message}")
                return@withContext Result.failure(userResult.exceptionOrNull() ?: Exception("Failed to get user"))
            }
            
            val user = userResult.getOrNull()
            if (user == null) {
                Log.e(TAG, "User not found for roll number: $rollNumber")
                return@withContext Result.failure(Exception("User not found"))
            }
            
            val referenceImageUrl = user.referenceImageUrl
            Log.d(TAG, "Reference image URL: $referenceImageUrl")
            
            return@withContext Result.success(referenceImageUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user reference image URL", e)
            return@withContext Result.failure(e)
        }
    }
} 