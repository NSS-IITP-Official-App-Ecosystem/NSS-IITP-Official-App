package com.phad.chatapp.utils

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object AttendanceUtils {
    fun incrementTotalEvents() {
        val db = FirebaseFirestore.getInstance()
        val metaRef = db.collection("meta").document("statistics")
        metaRef.update("total_events", FieldValue.increment(1))
    }

    fun incrementStudentAttendance(rollNumber: String) {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(rollNumber)
        userRef.update("eventsAttended", FieldValue.increment(1))
    }
} 