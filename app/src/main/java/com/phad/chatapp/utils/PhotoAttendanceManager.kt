package com.phad.chatapp.utils

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PhotoAttendanceManager {
    private const val TAG = "PhotoAttendanceManager"
    
    private val BASE_URL: String
        get() {
            val projId = try {
                com.google.firebase.FirebaseApp.getInstance().options.projectId
            } catch (e: Exception) {
                null
            } ?: "nssiitp-app"
            return if (com.phad.chatapp.BuildConfig.DEBUG) {
                val isEmulator = android.os.Build.FINGERPRINT.startsWith("generic") || 
                                 android.os.Build.MODEL.contains("google_sdk") || 
                                 android.os.Build.MODEL.contains("Emulator")
                val host = if (isEmulator) "10.0.2.2" else "127.0.0.1"
                "http://$host:5001/$projId/asia-south1/attendance"
            } else {
                "https://asia-south1-$projId.cloudfunctions.net/attendance"
            }
        }
    private val SUBMIT_URL get() = "$BASE_URL/api/attendance/submit-photo"
    private val PENDING_URL get() = "$BASE_URL/api/attendance/pending-photos"
    private val VERIFY_URL get() = "$BASE_URL/api/attendance/verify" // will append /:id

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Data class to represent a pending photo verification record
     */
    data class PendingPhotoRecord(
        val id: String,
        val rollNumber: String,
        val name: String,
        val eventId: String,
        val latitude: Double,
        val longitude: Double,
        val photoUrl: String,
        val status: String,
        val submittedAtMs: Long
    )

    /**
     * Helper to get Firebase ID token for auth headers
     */
    private suspend fun getIdToken(): String? {
        return try {
            val user = FirebaseAuth.getInstance().currentUser
            user?.getIdToken(false)?.await()?.token
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Firebase ID token", e)
            null
        }
    }

    /**
     * Submit a geo-tagged photo for attendance
     */
    suspend fun submitPhotoAttendance(
        userId: String,
        eventId: String,
        latitude: Double,
        longitude: Double,
        imageBytes: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getIdToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("userId", userId)
                .addFormDataPart("eventId", eventId)
                .addFormDataPart("latitude", latitude.toString())
                .addFormDataPart("longitude", longitude.toString())
                .addFormDataPart(
                    "image", 
                    "attendance_${userId}_${eventId}.jpg", 
                    imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(SUBMIT_URL)
                .header("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val errorMsg = response.body?.string() ?: "Unknown error"
                    val parsedError = try {
                        JSONObject(errorMsg).optString("error", errorMsg)
                    } catch (e: Exception) {
                        errorMsg
                    }
                    Result.failure(Exception("Server returned status ${response.code}: $parsedError"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit photo attendance", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch all pending photo records for admin review
     */
    suspend fun getPendingPhotos(): Result<List<PendingPhotoRecord>> = withContext(Dispatchers.IO) {
        try {
            val token = getIdToken() ?: return@withContext Result.failure(Exception("Not authenticated"))

            val request = Request.Builder()
                .url(PENDING_URL)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseStr = response.body?.string() ?: "[]"
                    val jsonArray = JSONArray(responseStr)
                    val records = mutableListOf<PendingPhotoRecord>()
                    
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        
                        // Parse timestamp object
                        val submittedAtObj = obj.optJSONObject("submittedAt")
                        val seconds = submittedAtObj?.optLong("_seconds") ?: 0L
                        val timestampMs = seconds * 1000
                        
                        val rawPhotoUrl = obj.optString("photo_url", "")
                        val finalPhotoUrl = if (com.phad.chatapp.BuildConfig.DEBUG && rawPhotoUrl.isNotEmpty()) {
                            val filename = rawPhotoUrl.substringAfterLast("/")
                            "$BASE_URL/api/attendance/photo/$filename"
                        } else {
                            rawPhotoUrl
                        }

                        records.add(
                            PendingPhotoRecord(
                                id = obj.optString("id"),
                                rollNumber = obj.optString("rollNumber"),
                                name = obj.optString("name", "Unknown Student"),
                                eventId = obj.optString("eventId"),
                                latitude = obj.optDouble("latitude", 0.0),
                                longitude = obj.optDouble("longitude", 0.0),
                                photoUrl = finalPhotoUrl,
                                status = obj.optString("verification_status", "Pending"),
                                submittedAtMs = timestampMs
                            )
                        )
                    }
                    Result.success(records)
                } else {
                    Result.failure(Exception("Failed to fetch pending photos: Code ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get pending photos", e)
            Result.failure(e)
        }
    }

    /**
     * Approve or reject a pending photo submission
     */
    suspend fun verifyPhoto(
        logId: String,
        status: String, // "Approved" or "Rejected"
        adminRollNumber: String,
        adminName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getIdToken() ?: return@withContext Result.failure(Exception("Not authenticated"))

            val jsonBody = JSONObject().apply {
                put("status", status)
                put("adminRollNumber", adminRollNumber)
                put("adminName", adminName)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("$VERIFY_URL/$logId")
                .header("Authorization", "Bearer $token")
                .put(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val errorMsg = response.body?.string() ?: "Unknown error"
                    val parsedError = try {
                        JSONObject(errorMsg).optString("error", errorMsg)
                    } catch (e: Exception) {
                        errorMsg
                    }
                    Result.failure(Exception("Failed to verify: Code ${response.code} - $parsedError"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify photo", e)
            Result.failure(e)
        }
    }
}
