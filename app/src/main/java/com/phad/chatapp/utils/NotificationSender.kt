package com.phad.chatapp.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import com.google.firebase.FirebaseApp

object NotificationSender {
    private const val TAG = "NotificationSender"
    private const val VERCEL_API_URL = "https://nss-app-notif.vercel.app/api/send"

    suspend fun sendNotification(
        topic: String? = null,
        token: String? = null,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        withContext(Dispatchers.IO) {
            try {
                if (topic == null && token == null) {
                    Log.e(TAG, "Must provide either topic or token")
                    return@withContext
                }

                val projectId = FirebaseApp.getInstance().options.projectId ?: "chatapp-24fae"

                val url = URL(VERCEL_API_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val jsonPayload = JSONObject().apply {
                    put("projectId", projectId)
                    topic?.let { put("topic", it) }
                    token?.let { put("token", it) }
                    put("title", title)
                    put("body", body)
                    
                    if (data.isNotEmpty()) {
                        val dataObj = JSONObject()
                        data.forEach { (k, v) -> dataObj.put(k, v) }
                        put("data", dataObj)
                    }
                }

                val payloadString = jsonPayload.toString()
                Log.d(TAG, "Sending notification payload: $payloadString")

                val out = OutputStreamWriter(conn.outputStream)
                out.write(payloadString)
                out.flush()
                out.close()

                val responseCode = conn.responseCode
                if (responseCode == 200 || responseCode == 201) {
                    Log.d(TAG, "Notification sent successfully. Code: $responseCode")
                } else {
                    val errorMsg = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    Log.e(TAG, "Failed to send notification. Code: $responseCode, Error: $errorMsg")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while sending notification: ${e.message}", e)
            }
        }
    }
}
