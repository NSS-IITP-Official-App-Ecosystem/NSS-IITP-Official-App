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
        if (userType.equals("Student", ignoreCase = true)) {
            val userRef = db.collection("users").document(rollNumber)
            try {
                val userDoc = userRef.get().await()
                val attended = userDoc.getLong("eventsAttended") ?: 0
                // Read total events count from meta/statistics
                val metaSnap = db.collection("meta").document("statistics").get().await()
                val total = (metaSnap.getLong("totalEvents") ?: 0L).toInt()
                val stats = "$attended/$total"
                sessionManager.saveAttendanceStats(stats)
            } catch (e: Exception) {
                sessionManager.saveAttendanceStats("0/0")
            }
        } else {
            try {
                // Read total events from meta/statistics
                val metaSnap = db.collection("meta").document("statistics").get().await()
                val total = (metaSnap.getLong("totalEvents") ?: 0L).toInt()
                val stats = "-/$total"
                sessionManager.saveAttendanceStats(stats)
            } catch (e: Exception) {
                sessionManager.saveAttendanceStats("-/0")
            }
        }
    }
} 