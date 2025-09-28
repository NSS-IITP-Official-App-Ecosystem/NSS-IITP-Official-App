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
        val studentRef = db.collection("Student").document(rollNumber)
        studentRef.update("event_attendance", FieldValue.increment(1))
    }
} 