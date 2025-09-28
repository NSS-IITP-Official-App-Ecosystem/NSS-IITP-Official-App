package com.phad.chatapp.models

import com.google.firebase.firestore.GeoPoint
import java.util.Date

/**
 * Data model for displaying attendance items in the admin approval list
 */
data class AttendanceApprovalItem(
    val id: String = "",
    val rollNumber: String = "",
    val name: String = "",
    val timestamp: Date = Date(),
    val location: GeoPoint? = null,
    val submittedImageUrl: String? = null,
    val referenceImageUrl: String? = null
) 