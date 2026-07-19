package com.phad.chatapp.network

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object BackendApi {
    private const val TAG = "BackendApi"
    private val BASE_URL: String
        get() {
            val projId = try {
                com.google.firebase.FirebaseApp.getInstance().options.projectId
            } catch (e: Exception) {
                null
            } ?: "nssiitp-app"
            // Use production Cloud Functions URL
            return "https://asia-south1-$projId.cloudfunctions.net"
        }
    private val client = OkHttpClient()
    private val json = "application/json; charset=utf-8".toMediaType()

    private suspend fun authHeader(): String {
        val user = FirebaseAuth.getInstance().currentUser ?: throw IllegalStateException("Not authenticated")
        val token = user.getIdToken(true).await().token ?: throw IllegalStateException("No ID token")
        return "Bearer $token"
    }

    suspend fun getBindChallenge(rollNumber: String): String {
        val body = JSONObject().put("rollNumber", rollNumber).toString().toRequestBody(json)
        val req = Request.Builder()
            .url("$BASE_URL/getDeviceBindChallenge")
            .post(body)
            .addHeader("Authorization", authHeader())
            .build()
        client.newCall(req).execute().use { resp ->
            val respBody = resp.body?.string() ?: "{}"
            if (!resp.isSuccessful) throw IllegalStateException("Bind challenge failed: ${resp.code} $respBody")
            val obj = JSONObject(respBody)
            return obj.getString("nonce")
        }
    }

    suspend fun bindDevice(rollNumber: String, publicKeyPem: String, signatureBase64: String) {
        val payload = JSONObject()
            .put("rollNumber", rollNumber)
            .put("publicKeyPem", publicKeyPem)
            .put("signatureBase64", signatureBase64)
            .toString().toRequestBody(json)
        val req = Request.Builder()
            .url("$BASE_URL/bindDevice")
            .post(payload)
            .addHeader("Authorization", authHeader())
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string()
            if (!resp.isSuccessful && resp.code != 409) {
                Log.e(TAG, "bindDevice error: ${resp.code} $body")
                throw IllegalStateException("bindDevice failed: ${resp.code}")
            }
        }
    }

    /**
     * Schedule a cloud-side notification for an event.
     * @param eventId Firestore event document ID
     * @param title Notification title (editable by admin)
     * @param body Notification body text (editable by admin)
     * @param scheduledAtMs Unix timestamp in milliseconds when notification should fire
     * @param targetWings List of wings to notify; empty/null = all students
     * @return The Firestore document ID of the scheduled notification
     */
    suspend fun scheduleNotification(
        eventId: String,
        title: String,
        body: String,
        scheduledAtMs: Long,
        targetWings: List<String>
    ): String {
        val payload = JSONObject()
            .put("eventId", eventId)
            .put("title", title)
            .put("body", body)
            .put("scheduledAt", scheduledAtMs)
            .put("targetWings", org.json.JSONArray(targetWings))
            .toString().toRequestBody(json)
        val req = Request.Builder()
            .url("$BASE_URL/attendance/api/attendance/schedule-notification")
            .post(payload)
            .addHeader("Authorization", authHeader())
            .build()
        client.newCall(req).execute().use { resp ->
            val respBody = resp.body?.string() ?: "{}"
            if (!resp.isSuccessful) {
                Log.e(TAG, "scheduleNotification error: ${resp.code} $respBody")
                throw IllegalStateException("scheduleNotification failed: ${resp.code} $respBody")
            }
            return JSONObject(respBody).getString("id")
        }
    }

    /**
     * Cancel a pending scheduled notification by its Firestore document ID.
     */
    suspend fun deleteScheduledNotification(notificationId: String) {
        val req = Request.Builder()
            .url("$BASE_URL/attendance/api/attendance/schedule-notification/$notificationId")
            .delete()
            .addHeader("Authorization", authHeader())
            .build()
        client.newCall(req).execute().use { resp ->
            val respBody = resp.body?.string()
            if (!resp.isSuccessful) {
                Log.e(TAG, "deleteScheduledNotification error: ${resp.code} $respBody")
                throw IllegalStateException("deleteScheduledNotification failed: ${resp.code}")
            }
        }
    }
}



