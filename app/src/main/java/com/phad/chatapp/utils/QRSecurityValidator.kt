package com.phad.chatapp.utils

import android.util.Log
import com.phad.chatapp.models.QRAttendanceData
import com.phad.chatapp.models.AttendeeRecord
import com.phad.chatapp.repositories.AttendanceQRRepository
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Security validator for QR attendance system
 * Provides comprehensive validation and security measures
 */
class QRSecurityValidator {
    private val TAG = "QRSecurityValidator"
    
    companion object {
        // Security constants - 10 second validity window to account for processing delays and network latency
        private const val MAX_QR_AGE_MS = 10000L // 10 seconds maximum age for security
        private const val MIN_QR_AGE_MS = -2000L // Allow 2 second tolerance for clock differences
        private const val MAX_SCAN_ATTEMPTS_PER_MINUTE = 10
        private const val RATE_LIMIT_WINDOW_MS = 60000L // 1 minute
        
        // Singleton instance
        @Volatile
        private var INSTANCE: QRSecurityValidator? = null
        
        fun getInstance(): QRSecurityValidator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: QRSecurityValidator().also { INSTANCE = it }
            }
        }
    }
    
    // Rate limiting storage
    private val scanAttempts = ConcurrentHashMap<String, MutableList<Long>>()
    
    // Used QR codes cache to prevent replay attacks
    private val usedQRCodes = ConcurrentHashMap<String, Long>()
    
    // Session validation cache
    private val validSessions = ConcurrentHashMap<String, SessionValidationInfo>()

    // Repository for Firestore access
    private val repository = AttendanceQRRepository()
    
    /**
     * Comprehensive QR code validation
     */
    fun validateQRCode(
        qrData: QRAttendanceData,
        studentId: String,
        sessionId: String? = null
    ): ValidationResult {
        Log.d(TAG, "Starting comprehensive QR validation for student: $studentId")
        
        try {
            // 1. Basic data validation
            val basicValidation = validateBasicData(qrData)
            if (!basicValidation.isValid) {
                return basicValidation
            }
            
            // 2. Timestamp validation
            val timestampValidation = validateTimestamp(qrData)
            if (!timestampValidation.isValid) {
                return timestampValidation
            }
            
            // 3. Integrity validation (signature check)
            val integrityValidation = validateIntegrity(qrData)
            if (!integrityValidation.isValid) {
                return integrityValidation
            }
            
            // 4. Replay attack prevention
            val replayValidation = validateReplayAttack(qrData)
            if (!replayValidation.isValid) {
                return replayValidation
            }
            
            // 5. Rate limiting validation
            val rateLimitValidation = validateRateLimit(studentId)
            if (!rateLimitValidation.isValid) {
                return rateLimitValidation
            }
            
            // 6. Session validation
            // Always validate the session from QR data, and optionally check against expected session
            val sessionValidation = if (sessionId != null) {
                // If expected session provided, validate against it
                validateSession(qrData, sessionId)
            } else {
                // Otherwise, validate that the session in QR data is active
                validateQRSession(qrData)
            }
            if (!sessionValidation.isValid) {
                return sessionValidation
            }
            
            // 7. Mark QR as used
            markQRAsUsed(qrData)
            
            // 8. Record scan attempt
            recordScanAttempt(studentId)
            
            Log.d(TAG, "QR validation successful for student: $studentId")
            return ValidationResult(true, "QR code is valid", ValidationResult.VALID)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during QR validation", e)
            return ValidationResult(false, "Validation error: ${e.message}", ValidationResult.ERROR)
        }
    }
    
    /**
     * Validate basic QR data structure
     */
    private fun validateBasicData(qrData: QRAttendanceData): ValidationResult {
        if (!qrData.isDataComplete()) {
            return ValidationResult(false, "Incomplete QR data", ValidationResult.INVALID_FORMAT)
        }
        
        if (qrData.version != "1.0") {
            return ValidationResult(false, "Unsupported QR version", ValidationResult.INVALID_VERSION)
        }
        
        return ValidationResult(true, "Basic data valid", ValidationResult.VALID)
    }
    
    /**
     * Validate QR code timestamp with comprehensive security logging
     */
    private fun validateTimestamp(qrData: QRAttendanceData): ValidationResult {
        val currentTime = System.currentTimeMillis()
        val qrTime = qrData.timestamp
        val age = currentTime - qrTime

        // Enhanced logging for debugging and security monitoring
        Log.d(TAG, "Timestamp validation - Current: $currentTime, QR: $qrTime, Age: ${age}ms")
        Log.d(TAG, "Timestamp validation - Age: ${age / 1000.0}s, Max allowed: ${MAX_QR_AGE_MS / 1000.0}s")
        Log.d(TAG, "QR ID: ${qrData.qrId}, Session: ${qrData.sessionId}")

        if (age < MIN_QR_AGE_MS) {
            Log.w(TAG, "SECURITY VIOLATION: QR code timestamp is from the future")
            Log.w(TAG, "Future timestamp detected - Age: ${age}ms, QR ID: ${qrData.qrId}")
            return ValidationResult(false, "QR code timestamp error", ValidationResult.INVALID_TIMESTAMP)
        }

        if (age > MAX_QR_AGE_MS) {
            Log.w(TAG, "SECURITY VIOLATION: QR code has expired")
            Log.w(TAG, "Expired QR rejected - Age: ${age}ms (${age / 1000.0}s), Max: ${MAX_QR_AGE_MS}ms (${MAX_QR_AGE_MS / 1000.0}s)")
            Log.w(TAG, "Expired QR details - ID: ${qrData.qrId}, Session: ${qrData.sessionId}, Admin: ${qrData.adminId}")
            return ValidationResult(false, "QR code has expired (${age / 1000.0}s old), please try scanning again", ValidationResult.EXPIRED)
        }

        Log.d(TAG, "Timestamp validation successful - Age: ${age}ms (${age / 1000.0}s) is within valid range")
        return ValidationResult(true, "Timestamp valid (${age}ms old)", ValidationResult.VALID)
    }
    
    /**
     * Validate QR code integrity using signature
     */
    private fun validateIntegrity(qrData: QRAttendanceData): ValidationResult {
        if (!qrData.isIntegrityValid()) {
            return ValidationResult(false, "QR code signature is invalid", ValidationResult.INVALID_SIGNATURE)
        }
        
        return ValidationResult(true, "Integrity valid", ValidationResult.VALID)
    }
    
    /**
     * Prevent replay attacks by checking if QR was already used
     */
    private fun validateReplayAttack(qrData: QRAttendanceData): ValidationResult {
        val qrId = qrData.qrId
        
        if (usedQRCodes.containsKey(qrId)) {
            val usedTime = usedQRCodes[qrId] ?: 0
            val timeSinceUsed = System.currentTimeMillis() - usedTime
            return ValidationResult(
                false, 
                "QR code already used ${timeSinceUsed}ms ago", 
                ValidationResult.REPLAY_ATTACK
            )
        }
        
        return ValidationResult(true, "No replay detected", ValidationResult.VALID)
    }
    
    /**
     * Validate rate limiting to prevent spam
     */
    private fun validateRateLimit(studentId: String): ValidationResult {
        val currentTime = System.currentTimeMillis()
        val attempts = scanAttempts.getOrPut(studentId) { mutableListOf() }
        
        // Remove old attempts outside the window
        attempts.removeAll { currentTime - it > RATE_LIMIT_WINDOW_MS }
        
        if (attempts.size >= MAX_SCAN_ATTEMPTS_PER_MINUTE) {
            return ValidationResult(
                false, 
                "Too many scan attempts. Please wait before trying again.", 
                ValidationResult.RATE_LIMITED
            )
        }
        
        return ValidationResult(true, "Rate limit OK", ValidationResult.VALID)
    }
    
    /**
     * Validate session consistency
     */
    private fun validateSession(qrData: QRAttendanceData, expectedSessionId: String): ValidationResult {
        if (qrData.sessionId != expectedSessionId) {
            return ValidationResult(
                false,
                "QR code is for a different session",
                ValidationResult.INVALID_SESSION
            )
        }

        // First check in-memory cache
        var sessionInfo = validSessions[expectedSessionId]

        // If not found in cache, check Firestore using consolidated schema
        if (sessionInfo == null) {
            Log.d(TAG, "Expected session not found in cache, checking consolidated event: $expectedSessionId")
            try {
                // In consolidated schema, session ID is the event ID
                val firestoreEvent = runBlocking {
                    repository.getAttendanceEvent(expectedSessionId).getOrNull()
                }

                if (firestoreEvent != null && firestoreEvent.isLive) {
                    // Event found in Firestore and is live
                    // Use the QR admin ID instead of event createdBy to match current session
                    val currentAdminId = qrData.adminId
                    Log.d(TAG, "Using current admin ID from QR: '$currentAdminId' instead of event createdBy: '${firestoreEvent.createdBy}'")

                    sessionInfo = SessionValidationInfo(
                        sessionId = firestoreEvent.id,
                        adminId = currentAdminId, // Use current admin ID from QR data
                        eventId = firestoreEvent.id,
                        startTime = firestoreEvent.createdAt.toDate().time,
                        endTime = firestoreEvent.closedAt?.toDate()?.time,
                        isActive = firestoreEvent.isLive
                    )
                    validSessions[expectedSessionId] = sessionInfo
                    Log.d(TAG, "Expected session found in consolidated event and added to cache: $expectedSessionId")
                } else {
                    Log.w(TAG, "Expected session not found in consolidated events or is not live: $expectedSessionId")
                    return ValidationResult(
                        false,
                        "Session not found or inactive (consolidated schema)",
                        ValidationResult.SESSION_NOT_FOUND
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking expected session in consolidated events: $expectedSessionId", e)
                return ValidationResult(
                    false,
                    "Session validation failed: ${e.message}",
                    ValidationResult.ERROR
                )
            }
        }

        if (!sessionInfo.isActive) {
            return ValidationResult(
                false,
                "Session has ended",
                ValidationResult.SESSION_ENDED
            )
        }

        return ValidationResult(true, "Session valid", ValidationResult.VALID)
    }

    /**
     * Validate that the session in QR data is active (without expected session check)
     */
    private fun validateQRSession(qrData: QRAttendanceData): ValidationResult {
        val sessionId = qrData.sessionId

        Log.d(TAG, "Validating QR session: $sessionId")
        Log.d(TAG, "QR Data - AdminId: ${qrData.adminId}, EventId: ${qrData.eventId}")
        Log.d(TAG, "Available sessions in cache: ${validSessions.keys.joinToString(", ")}")

        // First check in-memory cache
        var sessionInfo = validSessions[sessionId]
        Log.d(TAG, "Session found in cache: ${sessionInfo != null}")

        // If not found in cache, check Firestore using consolidated schema
        if (sessionInfo == null) {
            Log.d(TAG, "Session not found in cache, checking consolidated event: $sessionId")
            try {
                // In consolidated schema, session ID is the event ID
                val firestoreEvent = runBlocking {
                    repository.getAttendanceEvent(sessionId).getOrNull()
                }

                if (firestoreEvent != null && firestoreEvent.isLive) {
                    // Event found in Firestore and is live, add to cache as session
                    Log.d(TAG, "=== FIRESTORE EVENT DEBUG ===")
                    Log.d(TAG, "Event ID: ${firestoreEvent.id}")
                    Log.d(TAG, "Event createdBy: '${firestoreEvent.createdBy}'")
                    Log.d(TAG, "Event creatorName: '${firestoreEvent.creatorName}'")
                    Log.d(TAG, "Event isLive: ${firestoreEvent.isLive}")
                    Log.d(TAG, "QR Admin ID: '${qrData.adminId}'")
                    Log.d(TAG, "=============================")

                    // Use the QR admin ID instead of event createdBy to match current session
                    val currentAdminId = qrData.adminId
                    Log.d(TAG, "Using current admin ID from QR: '$currentAdminId' instead of event createdBy: '${firestoreEvent.createdBy}'")

                    sessionInfo = SessionValidationInfo(
                        sessionId = firestoreEvent.id,
                        adminId = currentAdminId, // Use current admin ID from QR data
                        eventId = firestoreEvent.id,
                        startTime = firestoreEvent.createdAt.toDate().time,
                        endTime = firestoreEvent.closedAt?.toDate()?.time,
                        isActive = firestoreEvent.isLive
                    )
                    validSessions[sessionId] = sessionInfo
                    Log.d(TAG, "Session found in consolidated event and added to cache: $sessionId")
                    Log.d(TAG, "Cached session admin ID: '${sessionInfo.adminId}'")
                } else {
                    Log.w(TAG, "Session not found in consolidated events or is not live: $sessionId")
                    return ValidationResult(
                        false,
                        "Session not found or inactive (consolidated schema)",
                        ValidationResult.SESSION_NOT_FOUND
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking session in consolidated events: $sessionId", e)
                return ValidationResult(
                    false,
                    "Session validation failed: ${e.message}",
                    ValidationResult.ERROR
                )
            }
        }

        if (!sessionInfo.isActive) {
            return ValidationResult(
                false,
                "Session has ended",
                ValidationResult.SESSION_ENDED
            )
        }

        // Verify session data matches QR data
        if (sessionInfo.eventId != qrData.eventId) {
            return ValidationResult(
                false,
                "QR code event doesn't match session",
                ValidationResult.INVALID_SESSION
            )
        }

        if (sessionInfo.adminId != qrData.adminId) {
            Log.w(TAG, "=== ADMIN ID MISMATCH DEBUG ===")
            Log.w(TAG, "Session admin ID: '${sessionInfo.adminId}'")
            Log.w(TAG, "QR code admin ID: '${qrData.adminId}'")
            Log.w(TAG, "Session ID: '${sessionInfo.sessionId}'")
            Log.w(TAG, "Event ID: '${sessionInfo.eventId}'")
            Log.w(TAG, "Session start time: ${sessionInfo.startTime}")
            Log.w(TAG, "Session is active: ${sessionInfo.isActive}")
            Log.w(TAG, "===============================")
            return ValidationResult(
                false,
                "QR code admin doesn't match session (Session: '${sessionInfo.adminId}', QR: '${qrData.adminId}')",
                ValidationResult.INVALID_SESSION
            )
        }

        return ValidationResult(true, "QR session is valid", ValidationResult.VALID)
    }
    
    /**
     * Mark QR code as used to prevent replay
     */
    private fun markQRAsUsed(qrData: QRAttendanceData) {
        usedQRCodes[qrData.qrId] = System.currentTimeMillis()
        
        // Clean up old entries to prevent memory leaks
        cleanupUsedQRCodes()
    }
    
    /**
     * Record scan attempt for rate limiting
     */
    private fun recordScanAttempt(studentId: String) {
        val attempts = scanAttempts.getOrPut(studentId) { mutableListOf() }
        attempts.add(System.currentTimeMillis())
    }
    
    /**
     * Register an active session
     */
    fun registerSession(sessionId: String, adminId: String, eventId: String) {
        validSessions[sessionId] = SessionValidationInfo(
            sessionId = sessionId,
            adminId = adminId,
            eventId = eventId,
            startTime = System.currentTimeMillis(),
            isActive = true
        )
        Log.d(TAG, "Session registered: $sessionId")
        Log.d(TAG, "Session details - AdminId: $adminId, EventId: $eventId")
        Log.d(TAG, "Total registered sessions: ${validSessions.size}")
        Log.d(TAG, "All registered session IDs: ${validSessions.keys.joinToString(", ")}")
    }
    
    /**
     * End a session
     */
    fun endSession(sessionId: String) {
        validSessions[sessionId]?.let { sessionInfo ->
            validSessions[sessionId] = sessionInfo.copy(
                isActive = false,
                endTime = System.currentTimeMillis()
            )
        }
        Log.d(TAG, "Session ended: $sessionId")
    }
    
    /**
     * Check for duplicate attendance in the same session
     */
    fun checkDuplicateAttendance(sessionId: String, studentId: String, existingAttendees: List<AttendeeRecord>): Boolean {
        return existingAttendees.any { it.rollNumber == studentId }
    }

    /**
     * Check for device-based duplicate attendance in the same session
     * Returns true if the device has already been used for attendance in this session
     */
    fun checkDeviceDuplicateAttendance(sessionId: String, deviceId: String, existingAttendees: List<AttendeeRecord>): Boolean {
        if (deviceId.isBlank()) {
            Log.w(TAG, "Device ID is blank, skipping device duplicate check")
            return false
        }

        val deviceAlreadyUsed = existingAttendees.any { attendee ->
            attendee.deviceId.isNotBlank() && attendee.deviceId == deviceId
        }

        if (deviceAlreadyUsed) {
            Log.w(TAG, "Device $deviceId has already been used for attendance in session $sessionId")
        }

        return deviceAlreadyUsed
    }

    /**
     * Comprehensive duplicate check including both user and device validation
     * Returns a result indicating the type of duplicate found
     */
    fun checkComprehensiveDuplicate(
        sessionId: String,
        studentId: String,
        deviceId: String,
        existingAttendees: List<AttendeeRecord>
    ): DuplicateCheckResult {
        // Check for user duplicate first
        val userDuplicate = checkDuplicateAttendance(sessionId, studentId, existingAttendees)
        if (userDuplicate) {
            return DuplicateCheckResult(
                isDuplicate = true,
                duplicateType = DuplicateType.USER_DUPLICATE,
                message = "Your attendance has already been marked"
            )
        }

        // Check for device duplicate
        val deviceDuplicate = checkDeviceDuplicateAttendance(sessionId, deviceId, existingAttendees)
        if (deviceDuplicate) {
            return DuplicateCheckResult(
                isDuplicate = true,
                duplicateType = DuplicateType.DEVICE_DUPLICATE,
                message = "This phone has been used to mark attendance for this event"
            )
        }

        return DuplicateCheckResult(
            isDuplicate = false,
            duplicateType = DuplicateType.NO_DUPLICATE,
            message = "No duplicate attendance found"
        )
    }
    
    /**
     * Clean up old used QR codes to prevent memory leaks
     */
    private fun cleanupUsedQRCodes() {
        val currentTime = System.currentTimeMillis()
        val cutoffTime = currentTime - (MAX_QR_AGE_MS * 10) // Keep for 10x the validity period
        
        usedQRCodes.entries.removeAll { (_, timestamp) ->
            currentTime - timestamp > cutoffTime
        }
    }
    
    /**
     * Get security statistics
     */
    fun getSecurityStats(): SecurityStats {
        return SecurityStats(
            totalUsedQRCodes = usedQRCodes.size,
            activeSessions = validSessions.values.count { it.isActive },
            totalSessions = validSessions.size,
            studentsWithAttempts = scanAttempts.size
        )
    }
}

/**
 * Data class for validation results
 */
data class ValidationResult(
    val isValid: Boolean,
    val message: String,
    val code: Int
) {
    companion object {
        const val VALID = 0
        const val INVALID_FORMAT = 1
        const val INVALID_VERSION = 2
        const val INVALID_TIMESTAMP = 3
        const val EXPIRED = 4
        const val INVALID_SIGNATURE = 5
        const val REPLAY_ATTACK = 6
        const val RATE_LIMITED = 7
        const val INVALID_SESSION = 8
        const val SESSION_NOT_FOUND = 9
        const val SESSION_ENDED = 10
        const val ERROR = 99
    }
}

/**
 * Data class for session validation info
 */
data class SessionValidationInfo(
    val sessionId: String,
    val adminId: String,
    val eventId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val isActive: Boolean
)

/**
 * Data class for security statistics
 */
data class SecurityStats(
    val totalUsedQRCodes: Int,
    val activeSessions: Int,
    val totalSessions: Int,
    val studentsWithAttempts: Int
)

/**
 * Result of duplicate attendance check
 */
data class DuplicateCheckResult(
    val isDuplicate: Boolean,
    val duplicateType: DuplicateType,
    val message: String
)

/**
 * Types of duplicate attendance
 */
enum class DuplicateType {
    NO_DUPLICATE,
    USER_DUPLICATE,
    DEVICE_DUPLICATE
}
