package com.phad.chatapp.utils

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object AttendanceStatsUpdater {
    suspend fun updateAttendanceStatsInSession(context: Context) {
        val sessionManager = SessionManager(context)
        val userType = sessionManager.fetchUserType()
        val rollNumber = sessionManager.fetchUserId()
        val db = FirebaseFirestore.getInstance()
        val metaRef = db.collection("meta").document("statistics")
        if (userType == "Student") {
            val studentRef = db.collection("Student").document(rollNumber)
            try {
                val studentDoc = studentRef.get().await()
                val attended = studentDoc.getLong("event_attendance") ?: 0
                val metaDoc = metaRef.get().await()
                val total = metaDoc.getLong("total_events") ?: 0
                val stats = "$attended/$total"
                sessionManager.saveAttendanceStats(stats)
            } catch (e: Exception) {
                sessionManager.saveAttendanceStats("0/0")
            }
        } else {
            try {
                val metaDoc = metaRef.get().await()
                val total = metaDoc.getLong("total_events") ?: 0
                val stats = "-/$total"
                sessionManager.saveAttendanceStats(stats)
            } catch (e: Exception) {
                sessionManager.saveAttendanceStats("-/0")
            }
        }
    }
} 