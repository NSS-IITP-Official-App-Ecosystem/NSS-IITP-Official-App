package com.phad.chatapp.utils

import android.util.Log
import com.phad.chatapp.models.QRAttendanceData
import com.phad.chatapp.services.QRAttendanceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Debug utilities for QR attendance system
 * Helps diagnose issues with QR code generation and validation
 */
object QRAttendanceDebugUtils {
    private const val TAG = "QRAttendanceDebug"
    
    /**
     * Test QR code generation and validation flow
     */
    suspend fun testQRFlow(
        sessionId: String,
        eventId: String,
        adminId: String,
        studentId: String,
        qrService: QRAttendanceService
    ): TestResult = withContext(Dispatchers.Default) {
        val results = mutableListOf<String>()
        var success = true
        
        try {
            results.add("=== QR Attendance Flow Test ===")
            results.add("Session ID: $sessionId")
            results.add("Event ID: $eventId")
            results.add("Admin ID: $adminId")
            results.add("Student ID: $studentId")
            results.add("")
            
            // Step 1: Register session
            results.add("Step 1: Registering session...")
            qrService.registerSession(sessionId, adminId, eventId)
            results.add("✓ Session registered successfully")
            results.add("")
            
            // Step 2: Generate QR code
            results.add("Step 2: Generating QR code...")
            val qrData = QRAttendanceData.create(sessionId, eventId, adminId)
            results.add("✓ QR data created:")
            results.add("  - QR ID: ${qrData.qrId}")
            results.add("  - Timestamp: ${qrData.timestamp}")
            results.add("  - Age: ${qrData.getAgeInSeconds()}s")
            results.add("  - Valid: ${qrData.isValid()}")
            results.add("  - Integrity: ${qrData.isIntegrityValid()}")
            results.add("  - Data Complete: ${qrData.isDataComplete()}")
            results.add("")
            
            // Step 3: Convert to JSON
            results.add("Step 3: Converting to JSON...")
            val qrJson = qrData.toJson()
            results.add("✓ JSON generated (${qrJson.length} chars)")
            results.add("JSON preview: ${qrJson.take(100)}...")
            results.add("")
            
            // Step 4: Parse JSON back
            results.add("Step 4: Parsing JSON back...")
            val parsedData = QRAttendanceData.fromJson(qrJson)
            if (parsedData != null) {
                results.add("✓ JSON parsed successfully")
                results.add("  - Matches original: ${parsedData == qrData}")
            } else {
                results.add("✗ Failed to parse JSON")
                success = false
            }
            results.add("")
            
            // Step 5: Validate QR code
            results.add("Step 5: Validating QR code...")
            val validationResult = qrService.validateQRCode(qrJson, studentId)
            if (validationResult.isSuccess) {
                val result = validationResult.getOrNull()!!
                results.add("✓ Validation completed")
                results.add("  - Valid: ${result.isValid}")
                results.add("  - Reason: ${result.reason}")
                if (!result.isValid) {
                    success = false
                }
            } else {
                results.add("✗ Validation failed: ${validationResult.exceptionOrNull()?.message}")
                success = false
            }
            results.add("")
            
            // Step 6: Generate bitmap
            results.add("Step 6: Generating QR bitmap...")
            val bitmapResult = qrService.generateQRCodeBitmap(qrData)
            if (bitmapResult.isSuccess) {
                val bitmap = bitmapResult.getOrNull()!!
                results.add("✓ Bitmap generated successfully")
                results.add("  - Size: ${bitmap.width}x${bitmap.height} (Expected: 1000x1000)")
                results.add("  - Config: ${bitmap.config}")
                results.add("  - Size verification: ${if (bitmap.width == 1000 && bitmap.height == 1000) "✓ CORRECT" else "✗ INCORRECT"}")
                results.add("  - Display size: 400dp (optimized for projection)")
            } else {
                results.add("✗ Bitmap generation failed: ${bitmapResult.exceptionOrNull()?.message}")
                success = false
            }
            results.add("")
            
            results.add("=== Test Summary ===")
            results.add(if (success) "✓ All tests passed!" else "✗ Some tests failed!")
            
        } catch (e: Exception) {
            results.add("✗ Test failed with exception: ${e.message}")
            Log.e(TAG, "Test failed", e)
            success = false
        }
        
        val report = results.joinToString("\n")
        Log.d(TAG, report)
        
        return@withContext TestResult(success, report)
    }
    
    /**
     * Test session validation specifically
     */
    suspend fun testSessionValidation(
        sessionId: String,
        eventId: String,
        adminId: String,
        qrService: QRAttendanceService
    ): String = withContext(Dispatchers.Default) {
        val results = mutableListOf<String>()
        
        try {
            results.add("=== Session Validation Test ===")
            
            // Register session
            qrService.registerSession(sessionId, adminId, eventId)
            results.add("✓ Session registered")
            
            // Create QR data
            val qrData = QRAttendanceData.create(sessionId, eventId, adminId)
            results.add("✓ QR data created")
            
            // Test validation without expected session
            val validation1 = qrService.validateQRCode(qrData.toJson(), "test_student")
            results.add("Validation without expected session:")
            results.add("  - Success: ${validation1.isSuccess}")
            if (validation1.isSuccess) {
                val result = validation1.getOrNull()!!
                results.add("  - Valid: ${result.isValid}")
                results.add("  - Reason: ${result.reason}")
            }
            
            // Test validation with expected session
            val validation2 = qrService.validateQRCode(qrData.toJson(), "test_student", sessionId)
            results.add("Validation with expected session:")
            results.add("  - Success: ${validation2.isSuccess}")
            if (validation2.isSuccess) {
                val result = validation2.getOrNull()!!
                results.add("  - Valid: ${result.isValid}")
                results.add("  - Reason: ${result.reason}")
            }
            
        } catch (e: Exception) {
            results.add("✗ Test failed: ${e.message}")
            Log.e(TAG, "Session validation test failed", e)
        }
        
        val report = results.joinToString("\n")
        Log.d(TAG, report)
        return@withContext report
    }
    
    /**
     * Log current QR data details
     */
    fun logQRDataDetails(qrData: QRAttendanceData, tag: String = TAG) {
        Log.d(tag, "=== QR Data Details ===")
        Log.d(tag, "Session ID: ${qrData.sessionId}")
        Log.d(tag, "Event ID: ${qrData.eventId}")
        Log.d(tag, "Admin ID: ${qrData.adminId}")
        Log.d(tag, "QR ID: ${qrData.qrId}")
        Log.d(tag, "Timestamp: ${qrData.timestamp}")
        Log.d(tag, "Age: ${qrData.getAgeInSeconds()}s")
        Log.d(tag, "Remaining validity: ${qrData.getRemainingValiditySeconds()}s")
        Log.d(tag, "Is valid: ${qrData.isValid()}")
        Log.d(tag, "Is expired: ${qrData.isExpired()}")
        Log.d(tag, "Integrity valid: ${qrData.isIntegrityValid()}")
        Log.d(tag, "Data complete: ${qrData.isDataComplete()}")
        Log.d(tag, "JSON: ${qrData.toJson()}")
        Log.d(tag, "========================")
    }

    /**
     * Debug timing issues by logging detailed timing information
     */
    fun debugQRTiming(qrData: QRAttendanceData, tag: String = TAG) {
        val currentTime = System.currentTimeMillis()
        val qrTime = qrData.timestamp
        val age = currentTime - qrTime

        Log.d(tag, "=== QR Timing Debug ===")
        Log.d(tag, "Current system time: $currentTime")
        Log.d(tag, "QR timestamp: $qrTime")
        Log.d(tag, "Age in milliseconds: $age")
        Log.d(tag, "Age in seconds: ${age / 1000.0}")
        Log.d(tag, "Max allowed age (ms): 4000")
        Log.d(tag, "Max allowed age (s): 4.0")
        Log.d(tag, "Time until expiry (ms): ${4000 - age}")
        Log.d(tag, "Time until expiry (s): ${(4000 - age) / 1000.0}")
        Log.d(tag, "Is within valid window: ${age >= -1000 && age <= 4000}")
        Log.d(tag, "QR isValid(): ${qrData.isValid()}")
        Log.d(tag, "QR isExpired(): ${qrData.isExpired()}")
        Log.d(tag, "========================")
    }

    /**
     * Debug session validation issues
     */
    fun debugSessionValidation(qrData: QRAttendanceData, tag: String = TAG) {
        Log.d(tag, "=== Session Validation Debug ===")
        Log.d(tag, "QR Session ID: ${qrData.sessionId}")
        Log.d(tag, "QR Event ID: ${qrData.eventId}")
        Log.d(tag, "QR Admin ID: ${qrData.adminId}")
        Log.d(tag, "QR ID: ${qrData.qrId}")
        Log.d(tag, "QR Timestamp: ${qrData.timestamp}")
        Log.d(tag, "QR Validation Token: ${qrData.validationToken}")
        Log.d(tag, "QR Version: ${qrData.version}")
        Log.d(tag, "QR JSON: ${qrData.toJson()}")
        Log.d(tag, "===================================")
    }

    /**
     * Debug admin ID flow and mismatches
     */
    fun debugAdminIdFlow(
        sessionAdminId: String,
        qrAdminId: String,
        eventCreatedBy: String,
        tag: String = TAG
    ) {
        Log.d(tag, "=== Admin ID Flow Debug ===")
        Log.d(tag, "Session Admin ID: '$sessionAdminId'")
        Log.d(tag, "QR Admin ID: '$qrAdminId'")
        Log.d(tag, "Event Created By: '$eventCreatedBy'")
        Log.d(tag, "Session == QR: ${sessionAdminId == qrAdminId}")
        Log.d(tag, "Session == Event: ${sessionAdminId == eventCreatedBy}")
        Log.d(tag, "QR == Event: ${qrAdminId == eventCreatedBy}")
        Log.d(tag, "Admin ID lengths - Session: ${sessionAdminId.length}, QR: ${qrAdminId.length}, Event: ${eventCreatedBy.length}")
        Log.d(tag, "============================")
    }
    
    data class TestResult(
        val success: Boolean,
        val report: String
    )
}
