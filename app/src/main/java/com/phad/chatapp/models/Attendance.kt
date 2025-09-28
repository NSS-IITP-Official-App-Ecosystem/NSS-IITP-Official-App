package com.phad.chatapp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

/**
 * Model class representing an attendance record in the system
 */
data class Attendance(
    val id: String = "",
    val rollNumber: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val location: GeoPoint? = null,
    val imageUrl: String? = null,
    val status: String = STATUS_PENDING, // "approved", "pending", "rejected"
    val verificationMethod: String = VERIFICATION_MANUAL, // "auto", "manual"
    val approvedBy: String? = null,
    val approvalTimestamp: Timestamp? = null,
    val notes: String? = null
) {
    companion object {
        // Status constants
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REJECTED = "rejected"
        
        // Verification method constants
        const val VERIFICATION_MANUAL = "manual"
        const val VERIFICATION_FACE_RECOGNITION = "face_recognition"
        const val VERIFICATION_QR_CODE = "qr_code"
        const val VERIFICATION_LOCATION = "location"
    }
    
    /**
     * Check if attendance is pending
     */
    fun isPending(): Boolean {
        return status == STATUS_PENDING
    }
    
    /**
     * Check if attendance is approved
     */
    fun isApproved(): Boolean {
        return status == STATUS_APPROVED
    }
    
    /**
     * Check if attendance is rejected
     */
    fun isRejected(): Boolean {
        return status == STATUS_REJECTED
    }
    
    /**
     * Format the timestamp as a string
     */
    fun getFormattedTimestamp(): String {
        val date = timestamp.toDate()
        val formatter = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    }
    
    /**
     * Format the location as a string
     */
    fun getFormattedLocation(): String {
        return if (location != null) {
            val lat = location.latitude
            val lng = location.longitude
            "Lat: ${String.format("%.4f", lat)}, Long: ${String.format("%.4f", lng)}"
        } else {
            "Location not available"
        }
    }
} 