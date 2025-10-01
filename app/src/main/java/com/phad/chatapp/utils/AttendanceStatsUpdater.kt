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
        if (userType.equals("Student", ignoreCase = true)) {
            val userRef = db.collection("users").document(rollNumber)
            try {
                val userDoc = userRef.get().await()
                val attended = userDoc.getLong("eventsAttended") ?: 0
                // Get total events count from NSS_Events_Attendence collection
                val eventsSnapshot = db.collection("NSS_Events_Attendence").get().await()
                val total = eventsSnapshot.size()
                val stats = "$attended/$total"
                sessionManager.saveAttendanceStats(stats)
            } catch (e: Exception) {
                sessionManager.saveAttendanceStats("0/0")
            }
        } else {
            try {
                // Get total events count from NSS_Events_Attendence collection
                val eventsSnapshot = db.collection("NSS_Events_Attendence").get().await()
                val total = eventsSnapshot.size()
                val stats = "-/$total"
                sessionManager.saveAttendanceStats(stats)
            } catch (e: Exception) {
                sessionManager.saveAttendanceStats("-/0")
            }
        }
    }
} 