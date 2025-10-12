package com.phad.chatapp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

/**
 * Data model for QR code content in attendance system
 * This class handles encoding and decoding of QR code data
 */
@Serializable
data class QRAttendanceData(
    val sessionId: String = "",
    val eventId: String = "",
    val adminId: String = "",
    val qrId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val validationToken: String = "",
    val version: String = "1.0"
) {
    companion object {
        private const val QR_VERSION = "1.0"
        private const val VALIDITY_WINDOW_MS = 10000L // 10 seconds validity window to account for processing delays
        
        /**
         * Create a new QR attendance data with validation token
         */
        fun create(sessionId: String, eventId: String, adminId: String): QRAttendanceData {
            val qrId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val validationToken = generateValidationToken(sessionId, eventId, adminId, qrId, timestamp)
            
            return QRAttendanceData(
                sessionId = sessionId,
                eventId = eventId,
                adminId = adminId,
                qrId = qrId,
                timestamp = timestamp,
                validationToken = validationToken,
                version = QR_VERSION
            )
        }
        
        /**
         * Generate validation token for security
         */
        private fun generateValidationToken(
            sessionId: String,
            eventId: String,
            adminId: String,
            qrId: String,
            timestamp: Long
        ): String {
            val data = "$sessionId:$eventId:$adminId:$qrId:$timestamp:NSS_QR_SECRET"
            return hashString(data).take(16) // Take first 16 characters
        }
        
        /**
         * Hash string using SHA-256
         */
        private fun hashString(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
        
        /**
         * Decode QR data from JSON string
         */
        fun fromJson(jsonString: String): QRAttendanceData? {
            return try {
                Json.decodeFromString<QRAttendanceData>(jsonString)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Convert to JSON string for QR code encoding
     */
    @Exclude
    fun toJson(): String {
        return Json.encodeToString(this)
    }
    
    /**
     * Check if QR code is currently valid (within time window)
     */
    @Exclude
    fun isValid(): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeDifference = currentTime - timestamp
        return timeDifference <= VALIDITY_WINDOW_MS && timeDifference >= 0
    }
    
    /**
     * Check if QR code has expired
     */
    @Exclude
    fun isExpired(): Boolean {
        val currentTime = System.currentTimeMillis()
        return (currentTime - timestamp) > VALIDITY_WINDOW_MS
    }
    
    /**
     * Validate the QR code's integrity
     */
    @Exclude
    fun isIntegrityValid(): Boolean {
        val expectedToken = QRAttendanceData.generateValidationToken(
            sessionId, eventId, adminId, qrId, timestamp
        )
        return validationToken == expectedToken
    }
    
    /**
     * Get age of QR code in seconds
     */
    @Exclude
    fun getAgeInSeconds(): Long {
        return (System.currentTimeMillis() - timestamp) / 1000
    }
    
    /**
     * Get remaining validity time in seconds
     */
    @Exclude
    fun getRemainingValiditySeconds(): Long {
        val age = getAgeInSeconds()
        val validitySeconds = VALIDITY_WINDOW_MS / 1000
        return maxOf(0, validitySeconds - age)
    }
    
    /**
     * Get formatted timestamp
     */
    @Exclude
    fun getFormattedTimestamp(): String {
        val date = java.util.Date(timestamp)
        val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    }
    
    /**
     * Validate all QR data fields
     */
    @Exclude
    fun isDataComplete(): Boolean {
        return sessionId.isNotBlank() &&
               eventId.isNotBlank() &&
               adminId.isNotBlank() &&
               qrId.isNotBlank() &&
               validationToken.isNotBlank() &&
               timestamp > 0
    }
    
    /**
     * Get validation status with reason
     */
    @Exclude
    fun getValidationStatus(): Pair<Boolean, String> {
        return when {
            !isDataComplete() -> Pair(false, "Incomplete QR data")
            !isIntegrityValid() -> Pair(false, "Invalid QR code signature")
            isExpired() -> Pair(false, "QR code has expired")
            !isValid() -> Pair(false, "QR code is not yet valid")
            else -> Pair(true, "Valid QR code")
        }
    }
}
