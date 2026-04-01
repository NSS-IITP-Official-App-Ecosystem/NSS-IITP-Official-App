package com.phad.chatapp.utils

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Utility to communicate with the Vercel FCM microservice 
 * since Firebase Cloud Functions require a Blaze (Billing) Plan.
 */
object FcmSender {
    private const val TAG = "FcmSender"
    
    // The Vercel API Endpoint deployed by the user
    // Expected format: https://your-project.vercel.app/api/send
    private const val VERCEL_API_URL = "https://nss-app-notif.vercel.app/api/send"
    // Wait: If the empty folder was pushed as 'fcm-backend', it might actually be:
    // "https://nss-app-notif.vercel.app/fcm-backend/api/send"
    // We will stick to the root for now, but log detailed errors.

    private val client = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Internal generic method to construct the HTTP proxy request
     */
    private suspend fun sendPayload(
        topic: String? = null,
        token: String? = null,
        title: String,
        body: String,
        data: Map<String, String>? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Get the active Firebase Project ID dynamically
            val projectId = FirebaseApp.getInstance().options.projectId
            if (projectId == null) {
                Log.e(TAG, "Failed to send FCM: Project ID is null")
                return@withContext false
            }

            val jsonBody = JSONObject().apply {
                put("projectId", projectId)
                if (topic != null) put("topic", topic)
                if (token != null) put("token", token)
                put("title", title)
                put("body", body)
                if (data != null) {
                    val dataJson = JSONObject()
                    data.forEach { (key, value) -> dataJson.put(key, value) }
                    put("data", dataJson)
                }
            }

            Log.d(TAG, "Requesting Vercel API push: $jsonBody")

            val requestBody = jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(VERCEL_API_URL)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful) {
                Log.d(TAG, "Successfully sent notification via Vercel: $responseBody")
                return@withContext true
            } else {
                Log.e(TAG, "Vercel API failed HTTP ${response.code}: $responseBody")
                
                // Fallback attempt just in case the folder was pushed as a subdirectory
                if (response.code == 404 && VERCEL_API_URL.endsWith("/api/send")) {
                    Log.w(TAG, "Attempting trailing folder fallback directory...")
                    val fallbackUrl = "https://nss-app-notif.vercel.app/fcm-backend/api/send"
                    val fallbackRequest = Request.Builder().url(fallbackUrl).post(requestBody).build()
                    val fallbackRes = client.newCall(fallbackRequest).execute()
                    if (fallbackRes.isSuccessful) {
                        Log.d(TAG, "Fallback Success: ${fallbackRes.body?.string()}")
                        return@withContext true
                    }
                }
                
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Vercel FCM call", e)
            return@withContext false
        }
    }

    /**
     * Send To A Specific Topic (e.g., nss, ttw, wing_aashayein)
     */
    suspend fun sendToTopic(
        topic: String,
        title: String,
        body: String,
        data: Map<String, String>? = null
    ) {
        sendPayload(
            topic = topic,
            title = title,
            body = body,
            data = data
        )
    }

    /**
     * Send to a specific User's Individual Topic (e.g. user_2101MC19)
     */
    suspend fun sendToUser(
        userId: String,
        title: String,
        body: String,
        data: Map<String, String>? = null
    ) {
        sendPayload(
            topic = "user_$userId",
            title = title,
            body = body,
            data = data
        )
    }
}
