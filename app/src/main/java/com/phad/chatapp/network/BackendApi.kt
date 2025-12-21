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
    // Adjust region if different
    private const val BASE_URL = "https://asia-south1-chatapp-24fae.cloudfunctions.net"
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

    suspend fun getAttendanceChallenge(rollNumber: String, eventId: String): String {
        val body = JSONObject().put("rollNumber", rollNumber).put("eventId", eventId).toString().toRequestBody(json)
        val req = Request.Builder()
            .url("$BASE_URL/getAttendanceChallenge")
            .post(body)
            .addHeader("Authorization", authHeader())
            .build()
        client.newCall(req).execute().use { resp ->
            val respBody = resp.body?.string() ?: "{}"
            if (!resp.isSuccessful) throw IllegalStateException("Attendance challenge failed: ${resp.code} $respBody")
            val obj = JSONObject(respBody)
            return obj.getString("nonce")
        }
    }

    suspend fun markAttendance(rollNumber: String, eventId: String, attendeeJson: JSONObject, signatureBase64: String) {
        val payload = JSONObject()
            .put("rollNumber", rollNumber)
            .put("eventId", eventId)
            .put("signatureBase64", signatureBase64)
            .put("attendee", attendeeJson)
            .toString().toRequestBody(json)
        val req = Request.Builder()
            .url("$BASE_URL/markAttendance")
            .post(payload)
            .addHeader("Authorization", authHeader())
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string()
            if (!resp.isSuccessful) {
                throw IllegalStateException("markAttendance failed: ${resp.code} $body")
            }
        }
    }
}


