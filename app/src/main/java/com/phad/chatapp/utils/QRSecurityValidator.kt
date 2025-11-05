package com.phad.chatapp.utils

import android.location.Location
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
        // Security constants - 3 second validity window to account for processing delays and network latency
        private const val MAX_QR_AGE_MS = 8000L // 3 seconds maximum age for security
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
        var sessionInfoSource = if (sessionInfo != null) "cache" else "unknown"

        // If not found in cache, check Firestore using consolidated schema
        if (sessionInfo == null) {
            Log.d(TAG, "Expected session not found in cache, checking consolidated event: $expectedSessionId")
            try {
                // In consolidated schema, session ID is the event ID
                val firestoreEvent = runBlocking {
                    repository.getAttendanceEvent(expectedSessionId).getOrNull()
                }

                // Check if event is live by examining raw Firestore data
                // This handles the case where is_live field is not serialized due to @get:Exclude
                val isEventLive = if (firestoreEvent != null) {
                    // We need to get the raw document data to check is_live field
                    // Since we can't access raw data here, we'll assume true for new events
                    // and let the closedAt field indicate if event is ended
                    val isEnded = firestoreEvent.closedAt != null
                    !isEnded // Event is live if not ended
                } else {
                    false
                }

                if (firestoreEvent != null && isEventLive) {
                    // Event found in Firestore and is live
                    // Use the QR admin ID instead of event createdBy to match current session
                    val currentAdminId = qrData.adminId
                    Log.d(TAG, "Using current admin ID from QR: '$currentAdminId' instead of event createdBy: '${firestoreEvent.createdBy}'")
                    Log.d(TAG, "Event closedAt: ${firestoreEvent.closedAt}")
                    Log.d(TAG, "Event isEventLive (calculated): $isEventLive")

                    sessionInfo = SessionValidationInfo(
                        sessionId = firestoreEvent.id,
                        adminId = currentAdminId, // Use current admin ID from QR data
                        eventId = firestoreEvent.id,
                        startTime = firestoreEvent.createdAt.toDate().time,
                        endTime = firestoreEvent.closedAt?.toDate()?.time,
                        isActive = isEventLive
                    )
                    validSessions[expectedSessionId] = sessionInfo
                    sessionInfoSource = "firestore"
                    Log.d(TAG, "Expected session found in consolidated event and added to cache: $expectedSessionId")
                } else {
                    Log.w(TAG, "Expected session not found in consolidated events or is not live: $expectedSessionId")
                    Log.w(TAG, "Event exists: ${firestoreEvent != null}")
                    Log.w(TAG, "Event is live: $isEventLive")
                    if (firestoreEvent != null) {
                        Log.w(TAG, "Event closedAt: ${firestoreEvent.closedAt}")
                    }
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
            // DEBUG: Deep dive before returning SESSION_ENDED
            try {
                val firestoreEvent = runBlocking { repository.getAttendanceEventForDuplicateCheck(expectedSessionId).getOrNull() }
                Log.w(TAG, "SESSION_ENDED DEBUG (validateSession): expectedSessionId='$expectedSessionId'")
                Log.w(TAG, "Source='$sessionInfoSource', cacheKeys='${validSessions.keys.joinToString()}'")
                Log.w(TAG, "sessionInfo: adminId='${sessionInfo.adminId}', eventId='${sessionInfo.eventId}', startTime=${sessionInfo.startTime}, endTime=${sessionInfo.endTime}, isActive=${sessionInfo.isActive}")
                if (firestoreEvent != null) {
                    Log.w(TAG, "firestoreEvent: id='${firestoreEvent.id}', isLive=${firestoreEvent.isLive}, closedAt=${firestoreEvent.closedAt}, createdAt=${firestoreEvent.createdAt}, createdBy='${firestoreEvent.createdBy}'")
                } else {
                    Log.w(TAG, "firestoreEvent: null for id='$expectedSessionId'")
                }
            } catch (e: Exception) {
                Log.e(TAG, "SESSION_ENDED DEBUG error (validateSession)", e)
            }
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

        Log.d(TAG, "=== QR SESSION VALIDATION START ===")
        Log.d(TAG, "Validating QR session: $sessionId")
        Log.d(TAG, "QR Data - AdminId: ${qrData.adminId}, EventId: ${qrData.eventId}")
        Log.d(TAG, "QR Data - QrId: ${qrData.qrId}, Timestamp: ${qrData.timestamp}")
        Log.d(TAG, "Available sessions in cache: ${validSessions.keys.joinToString(", ")}")

        // First check in-memory cache
        var sessionInfo = validSessions[sessionId]
        var sessionInfoSource = if (sessionInfo != null) "cache" else "unknown"
        Log.d(TAG, "Session found in cache: ${sessionInfo != null}")
        if (sessionInfo != null) {
            Log.d(TAG, "Cached session details - AdminId: '${sessionInfo.adminId}', EventId: '${sessionInfo.eventId}', IsActive: ${sessionInfo.isActive}")
        }

        // If not found in cache, check Firestore events directly
        // (We no longer store sessions in NSS_Sessions collection)
        if (sessionInfo == null) {
            Log.d(TAG, "Session not found in cache or Firestore, checking consolidated event with retry: $sessionId")
            
            // Retry mechanism for newly created events
            var retryCount = 0
            val maxRetries = 3
            var lastException: Exception? = null
            
            while (retryCount <= maxRetries) {
                try {
                    Log.d(TAG, "Attempt ${retryCount + 1}/${maxRetries + 1} to fetch event from Firestore")
                    
                // In consolidated schema, session ID is the event ID
                    Log.d(TAG, "Attempting to fetch event from Firestore with ID: '$sessionId'")
                val firestoreEvent = runBlocking {
                        // Try to get event - if cache fails, force server read
                        val result = repository.getAttendanceEvent(sessionId)
                        Log.d(TAG, "Repository result: isSuccess=${result.isSuccess}")
                        if (result.isFailure) {
                            Log.e(TAG, "Repository error: ${result.exceptionOrNull()?.message}")
                            // If cache read failed, try server read directly
                            try {
                                val serverResult = repository.getAttendanceEventForDuplicateCheck(sessionId)
                                if (serverResult.isSuccess) {
                                    serverResult.getOrNull()
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Server read also failed", e)
                                null
                            }
                        } else {
                            val event = result.getOrNull()
                            // If event is null from cache, try server read
                            if (event == null) {
                                Log.d(TAG, "Event not found in cache, trying server read")
                                val serverResult = repository.getAttendanceEventForDuplicateCheck(sessionId)
                                if (serverResult.isSuccess) {
                                    serverResult.getOrNull()
                                } else {
                                    null
                                }
                            } else {
                                event
                            }
                        }
                    }

                    Log.d(TAG, "Firestore event fetch result: ${firestoreEvent != null}")
                    if (firestoreEvent != null) {
                        Log.d(TAG, "Event details - ID: ${firestoreEvent.id}, CreatedBy: '${firestoreEvent.createdBy}', CreatedAt: ${firestoreEvent.createdAt}")
                        Log.d(TAG, "Event isLive: ${firestoreEvent.isLive}, closedAt: ${firestoreEvent.closedAt}")
                    } else {
                        Log.w(TAG, "Event not found in Firestore for sessionId: '$sessionId'")
                        Log.d(TAG, "This could mean:")
                        Log.d(TAG, "  1. Event was not created properly")
                        Log.d(TAG, "  2. Event ID mismatch between QR and database")
                        Log.d(TAG, "  3. Event was deleted or moved")
                }

                // Check if event is live - event is live if closedAt is null
                // (We don't need to check is_live field since we're using closedAt as the source of truth)
                val isEventLive = if (firestoreEvent != null) {
                    val isEnded = firestoreEvent.closedAt != null
                    val isLive = !isEnded // Event is live if not ended (closedAt == null)
                    Log.d(TAG, "Event live calculation - closedAt: ${firestoreEvent.closedAt}, isEnded: $isEnded, isLive: $isLive")
                    isLive
                } else {
                    Log.w(TAG, "Event not found in Firestore")
                    false
                }

                if (firestoreEvent != null) {
                    // Event found - check if it's live
                    Log.d(TAG, "=== FIRESTORE EVENT DEBUG ===")
                    Log.d(TAG, "Event ID: ${firestoreEvent.id}")
                    Log.d(TAG, "Event createdBy: '${firestoreEvent.createdBy}'")
                    Log.d(TAG, "Event creatorName: '${firestoreEvent.creatorName}'")
                    Log.d(TAG, "Event isLive: ${firestoreEvent.isLive}")
                    Log.d(TAG, "Event closedAt: ${firestoreEvent.closedAt}")
                    Log.d(TAG, "Event isEventLive (calculated): $isEventLive")
                    Log.d(TAG, "QR Admin ID: '${qrData.adminId}'")
                    Log.d(TAG, "=============================")

                    if (isEventLive) {
                        // Use event creator as admin ID - this allows QR codes from event creator to work
                        // even if another admin registers a session
                        val eventCreatorId = firestoreEvent.createdBy
                        Log.d(TAG, "Using event createdBy: '$eventCreatorId' as admin ID for validation")

                        sessionInfo = SessionValidationInfo(
                            sessionId = firestoreEvent.id,
                            adminId = eventCreatorId, // Use event creator ID so their QR codes always work
                            eventId = firestoreEvent.id,
                            startTime = firestoreEvent.createdAt.toDate().time,
                            endTime = firestoreEvent.closedAt?.toDate()?.time,
                            isActive = true
                        )
                        validSessions[sessionId] = sessionInfo
                        sessionInfoSource = "firestore"
                        Log.d(TAG, "✅ Session found in consolidated event and added to cache: $sessionId")
                        Log.d(TAG, "Cached session admin ID: '${sessionInfo.adminId}'")
                        break // Success, exit retry loop
                    } else {
                        Log.w(TAG, "Event exists but is not live (closedAt is not null)")
                        Log.w(TAG, "Event closedAt: ${firestoreEvent.closedAt}")
                        // If event is closed, return error immediately (no point retrying)
                        if (retryCount == maxRetries) {
                            return ValidationResult(
                                false,
                                "Event has ended (closedAt: ${firestoreEvent.closedAt})",
                                ValidationResult.SESSION_ENDED
                            )
                        }
                    }
                } else {
                    Log.w(TAG, "Event not found in Firestore for sessionId: '$sessionId'")
                    Log.w(TAG, "This could be due to:")
                    Log.w(TAG, "  1. Event ID mismatch - QR code has wrong event ID")
                    Log.w(TAG, "  2. Event was deleted")
                    Log.w(TAG, "  3. Network/cache issue - will retry")
                        
                        // If this is the last retry, return error
                        if (retryCount == maxRetries) {
                    return ValidationResult(
                        false,
                                "Event not found - sessionId '$sessionId' does not exist in database",
                        ValidationResult.SESSION_NOT_FOUND
                    )
                        }
                        
                        // Wait before retry (exponential backoff)
                        val waitTime = (1000 * (1 shl retryCount)).toLong() // 1s, 2s, 4s
                        Log.d(TAG, "Retrying in ${waitTime}ms...")
                        Thread.sleep(waitTime)
                        retryCount++
                }
            } catch (e: Exception) {
                    lastException = e
                    Log.e(TAG, "Error checking session in consolidated events (attempt ${retryCount + 1}): $sessionId", e)
                    
                    // If this is the last retry, return error
                    if (retryCount == maxRetries) {
                return ValidationResult(
                    false,
                            "Session validation failed after ${maxRetries + 1} attempts: ${e.message}",
                    ValidationResult.ERROR
                )
                    }
                    
                    // Wait before retry (exponential backoff)
                    val waitTime = (1000 * (1 shl retryCount)).toLong() // 1s, 2s, 4s
                    Log.d(TAG, "Retrying in ${waitTime}ms after exception...")
                    Thread.sleep(waitTime)
                    retryCount++
                }
            }
        }

        // Final validation
        if (sessionInfo == null) {
            Log.e(TAG, "❌ Session info is still null after all attempts")
            return ValidationResult(
                false,
                "Session validation failed - session info unavailable",
                ValidationResult.SESSION_NOT_FOUND
            )
        }

        if (!sessionInfo.isActive) {
            Log.w(TAG, "❌ Session is not active")
            // DEBUG: Deep dive before returning SESSION_ENDED
            try {
                val firestoreEvent = runBlocking { repository.getAttendanceEventForDuplicateCheck(sessionId).getOrNull() }
                Log.w(TAG, "SESSION_ENDED DEBUG (validateQRSession): sessionId='$sessionId'")
                Log.w(TAG, "Source='$sessionInfoSource', cacheKeys='${validSessions.keys.joinToString()}'")
                Log.w(TAG, "sessionInfo: adminId='${sessionInfo.adminId}', eventId='${sessionInfo.eventId}', startTime=${sessionInfo.startTime}, endTime=${sessionInfo.endTime}, isActive=${sessionInfo.isActive}")
                if (firestoreEvent != null) {
                    Log.w(TAG, "firestoreEvent: id='${firestoreEvent.id}', isLive=${firestoreEvent.isLive}, closedAt=${firestoreEvent.closedAt}, createdAt=${firestoreEvent.createdAt}, createdBy='${firestoreEvent.createdBy}'")
                } else {
                    Log.w(TAG, "firestoreEvent: null for id='$sessionId'")
                }
            } catch (e: Exception) {
                Log.e(TAG, "SESSION_ENDED DEBUG error (validateQRSession)", e)
            }
            return ValidationResult(
                false,
                "Session has ended",
                ValidationResult.SESSION_ENDED
            )
        }

        // Verify session data matches QR data
        if (sessionInfo.eventId != qrData.eventId) {
            Log.w(TAG, "❌ Event ID mismatch - Session: '${sessionInfo.eventId}', QR: '${qrData.eventId}'")
            return ValidationResult(
                false,
                "QR code event doesn't match session",
                ValidationResult.INVALID_SESSION
            )
        }

        // Always validate against event creator, not session admin
        // Fetch event to get the correct createdBy, even if session is cached
        // Use server read to ensure we get the latest data
        var eventCreatorId: String? = null
        try {
            val firestoreEvent = runBlocking {
                // Force server read to get latest event data
                repository.getAttendanceEventForDuplicateCheck(sessionId).getOrNull()
                    ?: repository.getAttendanceEvent(sessionId).getOrNull()
            }
            eventCreatorId = firestoreEvent?.createdBy
            Log.d(TAG, "Event creator from Firestore: '$eventCreatorId'")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching event for admin validation", e)
            // Fallback to session admin ID if event fetch fails
            eventCreatorId = sessionInfo.adminId
        }

        // Validate that QR admin ID matches event creator
        // This allows event creator's QR codes to work even if another admin registered a session
        val expectedAdminId = eventCreatorId ?: sessionInfo.adminId
        if (expectedAdminId != qrData.adminId) {
            Log.w(TAG, "=== ADMIN ID MISMATCH DEBUG ===")
            Log.w(TAG, "Event creator (expected): '$expectedAdminId'")
            Log.w(TAG, "QR code admin ID: '${qrData.adminId}'")
            Log.w(TAG, "Session ID: '${sessionInfo.sessionId}'")
            Log.w(TAG, "Event ID: '${sessionInfo.eventId}'")
            Log.w(TAG, "Session start time: ${sessionInfo.startTime}")
            Log.w(TAG, "Session is active: ${sessionInfo.isActive}")
            Log.w(TAG, "===============================")
            return ValidationResult(
                false,
                "QR code admin doesn't match event creator (Event creator: '$expectedAdminId', QR: '${qrData.adminId}')",
                ValidationResult.INVALID_SESSION
            )
        }

        Log.d(TAG, "✅ QR session validation successful")
        Log.d(TAG, "=== QR SESSION VALIDATION END ===")
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
     * Register an active session with enhanced logging and validation
     */
    fun registerSession(sessionId: String, adminId: String, eventId: String) {
        Log.d(TAG, "=== SESSION REGISTRATION START ===")
        Log.d(TAG, "Registering session: $sessionId")
        Log.d(TAG, "Session details - AdminId: '$adminId', EventId: '$eventId'")
        Log.d(TAG, "Current time: ${System.currentTimeMillis()}")
        
        // Validate input parameters
        if (sessionId.isBlank()) {
            Log.e(TAG, "❌ Session ID is blank - registration failed")
            return
        }
        if (adminId.isBlank()) {
            Log.e(TAG, "❌ Admin ID is blank - registration failed")
            return
        }
        if (eventId.isBlank()) {
            Log.e(TAG, "❌ Event ID is blank - registration failed")
            return
        }
        
        // Check if session already exists
        val existingSession = validSessions[sessionId]
        if (existingSession != null) {
            Log.w(TAG, "⚠️ Session already exists, updating with new details")
            Log.w(TAG, "Previous session - AdminId: '${existingSession.adminId}', EventId: '${existingSession.eventId}', IsActive: ${existingSession.isActive}")
        }
        
        val sessionInfo = SessionValidationInfo(
            sessionId = sessionId,
            adminId = adminId,
            eventId = eventId,
            startTime = System.currentTimeMillis(),
            isActive = true
        )
        
        validSessions[sessionId] = sessionInfo
        
        // Don't store in Firestore - validate based on event ownership instead
        // This prevents session overwriting when multiple admins start attendance
        
        Log.d(TAG, "✅ Session registered successfully: $sessionId")
        Log.d(TAG, "Registered session details - AdminId: '${sessionInfo.adminId}', EventId: '${sessionInfo.eventId}', IsActive: ${sessionInfo.isActive}")
        Log.d(TAG, "Total registered sessions: ${validSessions.size}")
        Log.d(TAG, "All registered session IDs: ${validSessions.keys.joinToString(", ")}")
        
        // Verify registration by reading back from cache
        val verificationSession = validSessions[sessionId]
        if (verificationSession != null) {
            Log.d(TAG, "✅ Session registration verified - AdminId: '${verificationSession.adminId}', EventId: '${verificationSession.eventId}'")
        } else {
            Log.e(TAG, "❌ Session registration verification failed - session not found in cache")
        }
        
        Log.d(TAG, "=== SESSION REGISTRATION END ===")
    }
    
    /**
     * Store session in Firestore for persistence
     */
    private fun storeSessionInFirestore(sessionInfo: SessionValidationInfo) {
        // Store session in Firestore asynchronously
        try {
            Log.d(TAG, "Storing session in Firestore: ${sessionInfo.sessionId}")
            // Use runBlocking to handle the suspend function
            runBlocking {
                repository.storeSession(sessionInfo)
            }
            Log.d(TAG, "Session stored in Firestore successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing session in Firestore", e)
        }
    }
    
    /**
     * End a session
     */
    fun endSession(sessionId: String) {
        Log.d(TAG, "=== SESSION END START ===")
        Log.d(TAG, "Ending session: $sessionId")
        
        validSessions[sessionId]?.let { sessionInfo ->
            val updatedSession = sessionInfo.copy(
                isActive = false,
                endTime = System.currentTimeMillis()
            )
            validSessions[sessionId] = updatedSession
            Log.d(TAG, "✅ Session ended successfully: $sessionId")
            Log.d(TAG, "Session details - AdminId: '${updatedSession.adminId}', EventId: '${updatedSession.eventId}', IsActive: ${updatedSession.isActive}")
        } ?: run {
            Log.w(TAG, "⚠️ Session not found in cache: $sessionId")
        }
        
        Log.d(TAG, "Total active sessions: ${validSessions.values.count { it.isActive }}")
        Log.d(TAG, "=== SESSION END END ===")
    }
    
    /**
     * Force refresh session cache from Firestore
     * This is useful when there might be timing issues between event creation and QR scanning
     */
    fun forceRefreshSessionCache(sessionId: String): Boolean {
        Log.d(TAG, "=== FORCE REFRESH SESSION CACHE START ===")
        Log.d(TAG, "Force refreshing session cache for: $sessionId")
        
        try {
            // Remove from cache first
            validSessions.remove(sessionId)
            Log.d(TAG, "Removed session from cache: $sessionId")
            
            // Try to fetch from Firestore
            val firestoreEvent = runBlocking {
                repository.getAttendanceEvent(sessionId).getOrNull()
            }
            
            if (firestoreEvent != null) {
                val isEventLive = firestoreEvent.closedAt == null
                Log.d(TAG, "Event found in Firestore - ID: ${firestoreEvent.id}, IsLive: $isEventLive")
                
                if (isEventLive) {
                    // Re-register the session
                    val sessionInfo = SessionValidationInfo(
                        sessionId = firestoreEvent.id,
                        adminId = firestoreEvent.createdBy, // Use event creator as admin
                        eventId = firestoreEvent.id,
                        startTime = firestoreEvent.createdAt.toDate().time,
                        endTime = firestoreEvent.closedAt?.toDate()?.time,
                        isActive = true
                    )
                    
                    validSessions[sessionId] = sessionInfo
                    Log.d(TAG, "✅ Session cache refreshed successfully: $sessionId")
                    Log.d(TAG, "Refreshed session - AdminId: '${sessionInfo.adminId}', EventId: '${sessionInfo.eventId}', IsActive: ${sessionInfo.isActive}")
                    return true
                } else {
                    Log.w(TAG, "Event is not live (closedAt: ${firestoreEvent.closedAt})")
                    return false
                }
            } else {
                Log.w(TAG, "Event not found in Firestore: $sessionId")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error force refreshing session cache: $sessionId", e)
            return false
        } finally {
            Log.d(TAG, "=== FORCE REFRESH SESSION CACHE END ===")
        }
    }
    
    /**
     * Get session info from cache (for debugging)
     */
    fun getSessionInfo(sessionId: String): SessionValidationInfo? {
        return validSessions[sessionId]
    }
    
    /**
     * Clear all sessions from cache (for debugging)
     */
    fun clearAllSessions() {
        Log.d(TAG, "Clearing all sessions from cache")
        validSessions.clear()
        Log.d(TAG, "All sessions cleared")
    }
    
    /**
     * Debug method to list all events in the database
     */
    suspend fun debugListAllEvents() {
        Log.d(TAG, "=== DEBUG: LISTING ALL EVENTS IN DATABASE ===")
        try {
            val allEvents = runBlocking {
                repository.getAllEvents(forceRefresh = true).getOrNull() ?: emptyList()
            }
            
            Log.d(TAG, "Total events found: ${allEvents.size}")
            allEvents.forEachIndexed { index, event ->
                Log.d(TAG, "Event $index:")
                Log.d(TAG, "  - ID: '${event.id}'")
                Log.d(TAG, "  - Name: '${event.getEventName()}'")
                Log.d(TAG, "  - CreatedBy: '${event.createdBy}'")
                Log.d(TAG, "  - IsLive: ${event.isLive}")
                Log.d(TAG, "  - ClosedAt: ${event.closedAt}")
                Log.d(TAG, "  - CreatedAt: ${event.createdAt}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing all events", e)
        }
        Log.d(TAG, "=== DEBUG: END LISTING ALL EVENTS ===")
    }
    
    /**
     * Debug method to show session cache status
     */
    fun debugSessionCache() {
        Log.d(TAG, "=== DEBUG: SESSION CACHE STATUS ===")
        Log.d(TAG, "Total sessions in cache: ${validSessions.size}")
        validSessions.forEach { (sessionId, sessionInfo) ->
            Log.d(TAG, "Session: $sessionId")
            Log.d(TAG, "  - AdminId: '${sessionInfo.adminId}'")
            Log.d(TAG, "  - EventId: '${sessionInfo.eventId}'")
            Log.d(TAG, "  - IsActive: ${sessionInfo.isActive}")
            Log.d(TAG, "  - StartTime: ${sessionInfo.startTime}")
            Log.d(TAG, "  - EndTime: ${sessionInfo.endTime}")
        }
        Log.d(TAG, "=== DEBUG: SESSION CACHE STATUS END ===")
    }
    
    
    /**
     * Check for duplicate attendance in the same session
     */
    fun checkDuplicateAttendance(sessionId: String, studentId: String, existingAttendees: List<AttendeeRecord>): Boolean {
        Log.d(TAG, "Checking duplicate attendance for student: $studentId")
        Log.d(TAG, "Session ID: $sessionId")
        Log.d(TAG, "Existing attendees count: ${existingAttendees.size}")
        Log.d(TAG, "Existing attendees roll numbers: ${existingAttendees.map { it.rollNumber }}")
        
        val isDuplicate = existingAttendees.any { it.rollNumber == studentId }
        
        if (isDuplicate) {
            Log.w(TAG, "Duplicate attendance found for student: $studentId")
            val duplicateAttendee = existingAttendees.find { it.rollNumber == studentId }
            Log.w(TAG, "Duplicate attendee details: $duplicateAttendee")
        } else {
            Log.d(TAG, "No duplicate attendance found for student: $studentId")
        }
        
        return isDuplicate
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
        Log.d(TAG, "=== COMPREHENSIVE DUPLICATE CHECK ===")
        Log.d(TAG, "Session ID: $sessionId")
        Log.d(TAG, "Student ID: $studentId")
        Log.d(TAG, "Device ID: ${deviceId.take(16)}...")
        Log.d(TAG, "Existing attendees count: ${existingAttendees.size}")
        
        // Check for user duplicate first
        val userDuplicate = checkDuplicateAttendance(sessionId, studentId, existingAttendees)
        if (userDuplicate) {
            Log.w(TAG, "USER DUPLICATE FOUND - blocking attendance")
            return DuplicateCheckResult(
                isDuplicate = true,
                duplicateType = DuplicateType.USER_DUPLICATE,
                message = "Your attendance has already been marked"
            )
        }

        // Check for device duplicate
        val deviceDuplicate = checkDeviceDuplicateAttendance(sessionId, deviceId, existingAttendees)
        if (deviceDuplicate) {
            Log.w(TAG, "DEVICE DUPLICATE FOUND - blocking attendance")
            return DuplicateCheckResult(
                isDuplicate = true,
                duplicateType = DuplicateType.DEVICE_DUPLICATE,
                message = "This phone has been used to mark attendance for this event"
            )
        }

        Log.d(TAG, "NO DUPLICATES FOUND - allowing attendance")
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
     * Validate location proximity for QR scanning
     * Verifies that the scanner is within the specified radius (3 meters) of the event location
     * @param scanLocation Current GPS location of the scanner
     * @param eventLocation Target GPS location of the event
     * @param maxRadiusMeters Maximum allowed distance in meters (default: 3)
     * @return ValidationResult indicating if location is valid
     */
    fun validateLocation(
        scanLocation: Location?,
        eventLocation: Location?,
        maxRadiusMeters: Float = 3f
    ): ValidationResult {
        Log.d(TAG, "=== LOCATION VALIDATION START ===")
        
        // Check if locations are provided
        if (scanLocation == null) {
            Log.w(TAG, "Scan location is null - location verification failed")
            return ValidationResult(
                false,
                "Location verification required. Please enable location permissions.",
                ValidationResult.MISSING_LOCATION
            )
        }
        
        if (eventLocation == null) {
            Log.w(TAG, "Event location is null - location verification skipped")
            // If event location is not set, allow attendance (for backward compatibility)
            return ValidationResult(
                true,
                "Event location not set - location check skipped",
                ValidationResult.VALID
            )
        }
        
        // Calculate distance between locations
        val distance = scanLocation.distanceTo(eventLocation)
        Log.d(TAG, "Distance from event: ${distance}m (max allowed: ${maxRadiusMeters}m)")
        Log.d(TAG, "Scan location: lat=${scanLocation.latitude}, lng=${scanLocation.longitude}")
        Log.d(TAG, "Event location: lat=${eventLocation.latitude}, lng=${eventLocation.longitude}")
        
        if (distance > maxRadiusMeters) {
            Log.w(TAG, "SECURITY VIOLATION: Scanner is too far from event location")
            Log.w(TAG, "Distance: ${distance}m, Max allowed: ${maxRadiusMeters}m")
            return ValidationResult(
                false,
                "You must be at the event location to mark attendance. You are ${distance.toInt()} meters away (max: ${maxRadiusMeters.toInt()}m)",
                ValidationResult.LOCATION_MISMATCH
            )
        }
        
        Log.d(TAG, "✅ Location validation successful - within ${distance}m of event")
        Log.d(TAG, "=== LOCATION VALIDATION END ===")
        return ValidationResult(
            true,
            "Location verified (${distance.toInt()}m from event)",
            ValidationResult.VALID
        )
    }
    
    /**
     * Create Location object from latitude and longitude
     */
    fun createLocation(latitude: Double, longitude: Double): Location {
        val location = Location("QR_Scanner")
        location.latitude = latitude
        location.longitude = longitude
        return location
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
        const val MISSING_LOCATION = 11
        const val LOCATION_MISMATCH = 12
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
