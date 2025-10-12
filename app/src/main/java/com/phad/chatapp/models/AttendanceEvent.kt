package com.phad.chatapp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.Exclude

/**
 * Model class representing a consolidated attendance event in the NSS_Events_Attendence collection
 * This model combines event metadata with attendee records in a single document
 * Document ID format: {day}_{month}_{event_name_with_underscores}
 */
data class AttendanceEvent(
    @DocumentId
    val id: String = "", // Format: "{day}_{month}_{event_name_with_underscores}"

    @PropertyName("description")
    val description: String = "",

    @PropertyName("createdBy")
    val createdBy: String = "", // Roll number of the admin who created the event

    @PropertyName("creatorName")
    val creatorName: String = "", // Actual name of the person who created the event

    @PropertyName("eventDate")
    val eventDate: String = "", // Date in DD MMM YYYY format

    @PropertyName("eventTime")
    val eventTime: String = "", // Time range in "HH:MM AM/PM - HH:MM AM/PM" format

    @PropertyName("hours")
    val hours: Double = 0.0, // Volunteer hours/tokens awarded for event participation

    @PropertyName("mandatory")
    val isMandatory: Boolean = false, // Whether attendance is mandatory

    @PropertyName("negativeHours")
    val negativeHours: Double = 0.0, // Hours to deduct for absentees when mandatory

    @PropertyName("location")
    val location: String = "", // Location where the event will take place

    @PropertyName("created_at")
    val createdAt: Timestamp = Timestamp.now(), // When the event was created in the system

    @PropertyName("attendees")
    val attendees: List<AttendeeRecord> = emptyList(),

    @PropertyName("closed_at")
    val closedAt: Timestamp? = null,

    // Use a private backing field to prevent automatic serialization of 'live' field
    @PropertyName("is_live")
    private val _isLive: Boolean = true // Default to true for new events
) {
    /**
     * Public accessor for the isLive field
     * This prevents Firebase from automatically creating a 'live' field
     */
    @get:Exclude
    val isLive: Boolean
        get() = _isLive
    companion object {
        // Status constants (removed eventStatus field as it's not in new schema)
        const val STATUS_LIVE = "Live"
        const val STATUS_END = "End"
    }

    // Empty constructor for Firestore
    constructor() : this(
        id = "",
        eventDate = "",
        eventTime = "",
        hours = 0.0,
        isMandatory = false,
        negativeHours = 0.0,
        location = "",
        description = "",
        createdBy = "",
        creatorName = "",
        createdAt = Timestamp.now(),
        attendees = emptyList(),
        closedAt = null,
        _isLive = true
    )

    /**
     * Check if student has already marked attendance
     */
    @Exclude
    fun hasStudentAttended(rollNumber: String): Boolean {
        return attendees.any { it.rollNumber == rollNumber }
    }

    /**
     * Add attendee to the event
     */
    @Exclude
    fun addAttendee(attendee: AttendeeRecord): AttendanceEvent {
        if (!hasStudentAttended(attendee.rollNumber)) {
            val updatedAttendees = attendees.toMutableList()
            updatedAttendees.add(attendee)
            return this.copy(
                attendees = updatedAttendees
            )
        }
        return this
    }

    /**
     * Get attendee count (replaces totalMarked field)
     */
    @Exclude
    fun getAttendeeCount(): Int {
        return attendees.size
    }

    /**
     * Get total marked count (for backward compatibility)
     * This replaces the removed totalMarked field
     */
    @get:Exclude
    val totalMarked: Int
        get() = attendees.size








    /**
     * Get formatted time range for display (e.g., "9:00 AM - 5:00 PM")
     * Uses new format if available, falls back to legacy format
     */
    @Exclude
    fun getFormattedTimeRange(): String {
        // Use new format if available
        return getTimeRangeString()
    }

    /**
     * Check if event is currently within its scheduled time window
     * Note: This is a simplified implementation since we no longer store precise timestamps
     */
    @Exclude
    fun isWithinScheduledTime(): Boolean {
        // For now, return true if the event has valid date/time format
        // This could be enhanced to parse the time strings and compare with current time
        return hasValidDateTimeFormat()
    }

    /**
     * Check if event has passed its closing time
     * Note: This is a simplified implementation since we no longer store precise timestamps
     */
    @Exclude
    fun hasPassedClosingTime(): Boolean {
        // For now, return false since we don't have precise timestamp comparison
        // This could be enhanced to parse the date/time strings and compare with current time
        return false
    }

    /**
     * Get event status dynamically (not stored in database)
     * Determines status based on isLive field, manual closure, scheduled time, or admin action
     * Priority: isLive field > manual closure > time-based closure
     */
    @Exclude
    fun getEventStatus(): String {
        return when {
            // First check the isLive field (highest priority)
            !isLive -> STATUS_END
            // Then check if manually closed by admin (for backward compatibility)
            closedAt != null -> STATUS_END
            // Then check if past scheduled closing time
            hasPassedClosingTime() -> STATUS_END
            // Otherwise it's live
            else -> STATUS_LIVE
        }
    }

    /**
     * Extract event name from document ID
     * Document ID format: {day}_{month}_{event_name_with_underscores}
     */
    @Exclude
    fun getEventName(): String {
        val components = id.split("_")
        return if (components.size >= 3) {
            // Join all components after day and month, replace underscores with spaces
            components.drop(2).joinToString("_").replace("_", " ")
        } else {
            "Unknown Event"
        }
    }

    /**
     * Extract event date components from document ID
     * Returns Triple(day, month, eventName)
     */
    @Exclude
    fun parseDocumentId(): Triple<String, String, String> {
        val components = id.split("_")
        return if (components.size >= 3) {
            val day = components[0]
            val month = components[1]
            val eventName = components.drop(2).joinToString("_").replace("_", " ")
            Triple(day, month, eventName)
        } else {
            Triple("", "", id)
        }
    }

    /**
     * Format the event date as a string for display
     * Handles both new DD MMM YYYY format and old YYYY-MM-DD format for backward compatibility
     */
    @Exclude
    fun getFormattedEventDate(): String {
        return if (eventDate.isNotEmpty()) {
            // Check if it's in old format and convert if needed
            if (com.phad.chatapp.utils.AttendanceEventUtils.isOldDateFormat(eventDate)) {
                com.phad.chatapp.utils.AttendanceEventUtils.convertOldDateFormatToNew(eventDate) ?: eventDate
            } else {
                eventDate // Already in DD MMM YYYY format
            }
        } else {
            // Fallback to creation date for legacy events
            val date = createdAt.toDate()
            val formatter = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            formatter.format(date)
        }
    }

    /**
     * Format the creation timestamp as a string
     */
    @Exclude
    fun getFormattedCreatedAt(): String {
        val date = createdAt.toDate()
        val formatter = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    }

    // ==================== NEW DATE/TIME STRUCTURE METHODS ====================

    /**
     * Get the event date as a string in DD MMM YYYY format
     */
    @Exclude
    fun getDateString(): String {
        return eventDate
    }

    /**
     * Get the event time range as a formatted string
     */
    @Exclude
    fun getTimeRangeString(): String {
        return if (eventTime.isNotEmpty()) {
            eventTime
        } else {
            // Provide default time range for legacy events
            "9:00 AM - 5:00 PM"
        }
    }



    /**
     * Check if this event has valid date/time format
     */
    @Exclude
    fun hasValidDateTimeFormat(): Boolean {
        return eventDate.isNotEmpty() && eventTime.isNotEmpty()
    }

    /**
     * Get event date as Date object for compatibility
     * Supports both new DD MMM YYYY format and old YYYY-MM-DD format
     */
    @Exclude
    fun getEventDateAsDate(): java.util.Date {
        return if (eventDate.isNotEmpty()) {
            com.phad.chatapp.utils.AttendanceEventUtils.parseDateString(eventDate) ?: java.util.Date()
        } else {
            java.util.Date()
        }
    }

    /**
     * Check if this event's date is in the old YYYY-MM-DD format and needs migration
     */
    @Exclude
    fun needsDateFormatMigration(): Boolean {
        return eventDate.isNotEmpty() && com.phad.chatapp.utils.AttendanceEventUtils.isOldDateFormat(eventDate)
    }

    /**
     * Get the event date in the new DD MMM YYYY format, converting from old format if needed
     */
    @Exclude
    fun getEventDateInNewFormat(): String {
        return if (needsDateFormatMigration()) {
            com.phad.chatapp.utils.AttendanceEventUtils.convertOldDateFormatToNew(eventDate) ?: eventDate
        } else {
            eventDate
        }
    }
}
