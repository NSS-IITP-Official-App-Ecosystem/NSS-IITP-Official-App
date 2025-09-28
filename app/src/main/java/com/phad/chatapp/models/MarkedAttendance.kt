package com.phad.chatapp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

/**
 * Model class representing an individual attendance submission
 */
data class MarkedAttendance(
    val rollNumber: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val location: GeoPoint? = null,
    val imageUrl: String = "",
    val approvalStatus: String = STATUS_PENDING,
    val approvedBy: String? = null,
    val approvalTimestamp: Timestamp? = null
) {
    companion object {
        // Status constants
        const val STATUS_PENDING = "Pending"
        const val STATUS_APPROVED = "Approved"
        const val STATUS_REJECTED = "Rejected"
    }
    
    /**
     * Check if attendance is pending
     */
    fun isPending(): Boolean {
        return approvalStatus == STATUS_PENDING
    }
    
    /**
     * Check if attendance is approved
     */
    fun isApproved(): Boolean {
        return approvalStatus == STATUS_APPROVED
    }
    
    /**
     * Check if attendance is rejected
     */
    fun isRejected(): Boolean {
        return approvalStatus == STATUS_REJECTED
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
