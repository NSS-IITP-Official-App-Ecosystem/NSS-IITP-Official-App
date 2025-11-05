package com.phad.chatapp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.GeoPoint

/**
 * Data class representing admin details who generated the QR code
 */
data class ScannedFromAdmin(
    @PropertyName("admin_roll_number")
    var adminRollNumber: String = "",

    @PropertyName("admin_name")
    var adminName: String = ""
) {
    // Empty constructor for Firestore
    constructor() : this("", "")
}

/**
 * Data model representing an individual attendee record in the consolidated NSS_Events_Attendence collection
 */
data class AttendeeRecord(
    @PropertyName("roll_number")
    var rollNumber: String = "",

    @PropertyName("name")
    var name: String = "",

    @PropertyName("scan_timestamp")
    var scanTimestamp: Timestamp = Timestamp.now(),

    @PropertyName("scanned_from")
    var scannedFrom: ScannedFromAdmin = ScannedFromAdmin(),

    @PropertyName("device_id")
    var deviceId: String = "", // Unique device identifier for duplicate prevention

    @PropertyName("scan_location")
    var scanLocation: GeoPoint? = null, // GPS location where QR code was scanned (null for manual entries)

    @PropertyName("is_manual_entry")
    var isManualEntry: Boolean = false // True for manual add/delete, false for QR scan
) {
    // Empty constructor for Firestore
    constructor() : this("", "", Timestamp.now(), ScannedFromAdmin(), "", null, false)
    
    /**
     * Check if the attendance was marked recently (within last 5 seconds)
     */
    @Exclude
    fun isRecentScan(): Boolean {
        val currentTime = System.currentTimeMillis()
        val scanTime = scanTimestamp.toDate().time
        val timeDifferenceSeconds = (currentTime - scanTime) / 1000
        return timeDifferenceSeconds <= 5
    }

    /**
     * Get formatted scan timestamp
     */
    @Exclude
    fun getFormattedScanTime(): String {
        val date = scanTimestamp.toDate()
        val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    }

    /**
     * Get formatted scan date and time
     */
    @Exclude
    fun getFormattedScanDateTime(): String {
        val date = scanTimestamp.toDate()
        val formatter = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    }
    
    /**
     * Validate attendee record data
     */
    @Exclude
    fun isDataValid(): Boolean {
        return rollNumber.isNotBlank() &&
               name.isNotBlank() &&
               scannedFrom.adminRollNumber.isNotBlank()
    }

    /**
     * Create attendee record with admin information
     */
    @Exclude
    fun withAdminInfo(adminRollNumber: String, adminName: String): AttendeeRecord {
        return this.copy(
            scannedFrom = ScannedFromAdmin(adminRollNumber, adminName)
        )
    }



    /**
     * Create attendee record with device ID
     */
    @Exclude
    fun withDeviceId(deviceId: String): AttendeeRecord {
        return this.copy(deviceId = deviceId)
    }

    /**
     * Create attendee record with uppercase roll number for consistency
     */
    @Exclude
    fun withUppercaseRollNumber(): AttendeeRecord {
        return this.copy(rollNumber = rollNumber.uppercase())
    }
}
