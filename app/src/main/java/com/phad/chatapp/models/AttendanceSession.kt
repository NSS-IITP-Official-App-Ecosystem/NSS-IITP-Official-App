package com.phad.chatapp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.Exclude

/**
 * Data model representing a QR-based attendance session
 * Document ID format: {eventId}_{adminId}_{sessionTimestamp}
 */
data class AttendanceSession(
    @DocumentId
    var id: String = "",
    
    @PropertyName("event_name")
    var eventName: String = "",
    
    @PropertyName("event_id")
    var eventId: String = "",
    
    @PropertyName("admin_id")
    var adminId: String = "", // Roll number of admin taking attendance
    
    @PropertyName("admin_name")
    var adminName: String = "",
    
    @PropertyName("session_start_time")
    var sessionStartTime: Timestamp = Timestamp.now(),
    
    @PropertyName("session_end_time")
    var sessionEndTime: Timestamp? = null,
    
    @PropertyName("is_active")
    var isActive: Boolean = true,
    
    @PropertyName("current_qr_id")
    var currentQrId: String = "",
    
    @PropertyName("qr_refresh_count")
    var qrRefreshCount: Int = 0,
    
    @PropertyName("total_attendees")
    var totalAttendees: Int = 0,
    
    @PropertyName("attendees")
    var attendees: List<AttendeeRecord> = emptyList(),
    
    @PropertyName("location")
    var location: String = "",
    
    @PropertyName("notes")
    var notes: String = ""
) {
    // Empty constructor for Firestore
    constructor() : this("", "", "", "", "", Timestamp.now(), null, true, "", 0, 0, emptyList(), "", "")
    
    /**
     * Generate session document ID
     */
    @Exclude
    fun generateSessionId(): String {
        val timestamp = System.currentTimeMillis()
        return "${eventId}_${adminId}_${timestamp}"
    }
    
    /**
     * Check if session is currently active
     */
    @Exclude
    fun isSessionActive(): Boolean {
        return isActive && sessionEndTime == null
    }
    
    /**
     * Get session duration in minutes
     */
    @Exclude
    fun getSessionDurationMinutes(): Long {
        val endTime = sessionEndTime?.toDate()?.time ?: System.currentTimeMillis()
        val startTime = sessionStartTime.toDate().time
        return (endTime - startTime) / (1000 * 60)
    }
    
    /**
     * Get formatted session start time
     */
    @Exclude
    fun getFormattedStartTime(): String {
        val date = sessionStartTime.toDate()
        val formatter = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    }
    
    /**
     * Check if student has already marked attendance
     */
    @Exclude
    fun hasStudentAttended(rollNumber: String): Boolean {
        return attendees.any { it.rollNumber == rollNumber }
    }
    
    /**
     * Add attendee to the session
     */
    @Exclude
    fun addAttendee(attendee: AttendeeRecord): AttendanceSession {
        if (!hasStudentAttended(attendee.rollNumber)) {
            val updatedAttendees = attendees.toMutableList()
            updatedAttendees.add(attendee)
            return this.copy(
                attendees = updatedAttendees,
                totalAttendees = updatedAttendees.size
            )
        }
        return this
    }
    
    /**
     * End the attendance session
     */
    @Exclude
    fun endSession(): AttendanceSession {
        return this.copy(
            isActive = false,
            sessionEndTime = Timestamp.now()
        )
    }
    
    /**
     * Update QR code information
     */
    @Exclude
    fun updateQrCode(newQrId: String): AttendanceSession {
        return this.copy(
            currentQrId = newQrId,
            qrRefreshCount = qrRefreshCount + 1
        )
    }
    
    /**
     * Validate session data
     */
    @Exclude
    fun isValid(): Boolean {
        return eventName.isNotBlank() && 
               eventId.isNotBlank() && 
               adminId.isNotBlank() && 
               adminName.isNotBlank()
    }
}
