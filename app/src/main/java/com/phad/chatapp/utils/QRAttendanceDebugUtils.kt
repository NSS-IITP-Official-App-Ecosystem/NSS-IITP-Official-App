package com.phad.chatapp.utils

import android.util.Log
import com.phad.chatapp.models.QRAttendanceData
import com.phad.chatapp.services.QRAttendanceService
import com.phad.chatapp.services.QRValidationResult
import kotlinx.coroutines.delay

/**
 * Debug utilities for QR attendance system
 * Provides testing and debugging capabilities
 */
object QRAttendanceDebugUtils {
    private const val TAG = "QRAttendanceDebugUtils"
    
    /**
     * Test the complete QR attendance flow
     */
    suspend fun testQRFlow(
        sessionId: String,
        eventId: String,
        adminId: String,
        studentId: String,
        qrService: QRAttendanceService
    ): DebugTestResult {
        Log.d(TAG, "=== QR ATTENDANCE FLOW TEST START ===")
        Log.d(TAG, "Test parameters:")
        Log.d(TAG, "  - SessionId: $sessionId")
        Log.d(TAG, "  - EventId: $eventId")
        Log.d(TAG, "  - AdminId: $adminId")
        Log.d(TAG, "  - StudentId: $studentId")
        
        val report = StringBuilder()
        var success = true
        
        try {
            // Step 1: Register session
            Log.d(TAG, "Step 1: Registering session...")
            report.appendLine("Step 1: Registering session...")
            QRSecurityValidator.getInstance().registerSession(sessionId, adminId, eventId)
            report.appendLine("✅ Session registered successfully")
            Log.d(TAG, "✅ Session registered successfully")
            
            // Step 2: Generate QR code
            Log.d(TAG, "Step 2: Generating QR code...")
            report.appendLine("Step 2: Generating QR code...")
            val qrData = QRAttendanceData.create(sessionId, eventId, adminId)
            report.appendLine("✅ QR code data created successfully")
            report.appendLine("  - QR ID: ${qrData.qrId}")
            report.appendLine("  - Timestamp: ${qrData.timestamp}")
            report.appendLine("  - SessionId: ${qrData.sessionId}")
            report.appendLine("  - EventId: ${qrData.eventId}")
            report.appendLine("  - AdminId: ${qrData.adminId}")
            Log.d(TAG, "✅ QR code data created successfully - ID: ${qrData.qrId}")
            
            // Step 3: Validate QR code
            Log.d(TAG, "Step 3: Validating QR code...")
            report.appendLine("Step 3: Validating QR code...")
            
            // Add small delay to simulate real-world timing
            delay(100)
            
            val validationResult = qrService.validateQRCode(qrData.toJson(), studentId)
            if (validationResult.isSuccess) {
                val result = validationResult.getOrNull()!!
                if (result.isValid) {
                    report.appendLine("✅ QR code validation successful")
                    Log.d(TAG, "✅ QR code validation successful")
                } else {
                    report.appendLine("❌ QR code validation failed: ${result.reason}")
                    Log.e(TAG, "❌ QR code validation failed: ${result.reason}")
                    success = false
                }
            } else {
                val error = validationResult.exceptionOrNull()?.message ?: "Unknown error"
                report.appendLine("❌ QR code validation error: $error")
                Log.e(TAG, "❌ QR code validation error: $error")
                success = false
            }
            
            // Step 4: Check session cache
            Log.d(TAG, "Step 4: Checking session cache...")
            report.appendLine("Step 4: Checking session cache...")
            val sessionInfo = QRSecurityValidator.getInstance().getSessionInfo(sessionId)
            if (sessionInfo != null) {
                report.appendLine("✅ Session found in cache:")
                report.appendLine("  - AdminId: ${sessionInfo.adminId}")
                report.appendLine("  - EventId: ${sessionInfo.eventId}")
                report.appendLine("  - IsActive: ${sessionInfo.isActive}")
                Log.d(TAG, "✅ Session found in cache - IsActive: ${sessionInfo.isActive}")
            } else {
                report.appendLine("❌ Session not found in cache")
                Log.e(TAG, "❌ Session not found in cache")
                success = false
            }
            
        } catch (e: Exception) {
            report.appendLine("❌ Test failed with exception: ${e.message}")
            Log.e(TAG, "❌ Test failed with exception", e)
            success = false
        }
        
        Log.d(TAG, "=== QR ATTENDANCE FLOW TEST END ===")
        Log.d(TAG, "Test result: ${if (success) "SUCCESS" else "FAILED"}")
        
        return DebugTestResult(
            success = success,
            report = report.toString()
        )
    }
    
    /**
     * Test session registration and validation
     */
    fun testSessionRegistration(
        sessionId: String,
        adminId: String,
        eventId: String
    ): DebugTestResult {
        Log.d(TAG, "=== SESSION REGISTRATION TEST START ===")
        
        val report = StringBuilder()
        var success = true
        
        try {
            // Register session
            Log.d(TAG, "Registering session: $sessionId")
            QRSecurityValidator.getInstance().registerSession(sessionId, adminId, eventId)
            report.appendLine("✅ Session registered: $sessionId")
            
            // Verify registration
            val sessionInfo = QRSecurityValidator.getInstance().getSessionInfo(sessionId)
            if (sessionInfo != null) {
                report.appendLine("✅ Session verification successful:")
                report.appendLine("  - AdminId: ${sessionInfo.adminId}")
                report.appendLine("  - EventId: ${sessionInfo.eventId}")
                report.appendLine("  - IsActive: ${sessionInfo.isActive}")
                Log.d(TAG, "✅ Session verification successful")
            } else {
                report.appendLine("❌ Session verification failed")
                Log.e(TAG, "❌ Session verification failed")
                success = false
            }
            
        } catch (e: Exception) {
            report.appendLine("❌ Session registration test failed: ${e.message}")
            Log.e(TAG, "❌ Session registration test failed", e)
            success = false
        }
        
        Log.d(TAG, "=== SESSION REGISTRATION TEST END ===")
        return DebugTestResult(success, report.toString())
    }
    
    /**
     * Test QR code generation and validation
     */
    suspend fun testQRGenerationAndValidation(
        sessionId: String,
        eventId: String,
        adminId: String,
        studentId: String,
        qrService: QRAttendanceService
    ): DebugTestResult {
        Log.d(TAG, "=== QR GENERATION AND VALIDATION TEST START ===")
        
        val report = StringBuilder()
        var success = true
        
        try {
            // Generate QR code
            Log.d(TAG, "Generating QR code...")
            val qrData = QRAttendanceData.create(sessionId, eventId, adminId)
            report.appendLine("✅ QR code data created:")
            report.appendLine("  - QR ID: ${qrData.qrId}")
            report.appendLine("  - SessionId: ${qrData.sessionId}")
            report.appendLine("  - EventId: ${qrData.eventId}")
            report.appendLine("  - AdminId: ${qrData.adminId}")
            report.appendLine("  - Timestamp: ${qrData.timestamp}")
            Log.d(TAG, "✅ QR code data created - ID: ${qrData.qrId}")
            
            // Validate QR code
            Log.d(TAG, "Validating QR code...")
            val validationResult = qrService.validateQRCode(qrData.toJson(), studentId)
            if (validationResult.isSuccess) {
                val result = validationResult.getOrNull()!!
                if (result.isValid) {
                    report.appendLine("✅ QR code validation successful")
                    Log.d(TAG, "✅ QR code validation successful")
                } else {
                    report.appendLine("❌ QR code validation failed: ${result.reason}")
                    Log.e(TAG, "❌ QR code validation failed: ${result.reason}")
                    success = false
                }
            } else {
                val error = validationResult.exceptionOrNull()?.message ?: "Unknown error"
                report.appendLine("❌ QR code validation error: $error")
                Log.e(TAG, "❌ QR code validation error: $error")
                success = false
            }
            
        } catch (e: Exception) {
            report.appendLine("❌ QR generation and validation test failed: ${e.message}")
            Log.e(TAG, "❌ QR generation and validation test failed", e)
            success = false
        }
        
        Log.d(TAG, "=== QR GENERATION AND VALIDATION TEST END ===")
        return DebugTestResult(success, report.toString())
    }
    
    /**
     * Get security statistics
     */
    fun getSecurityStats(): String {
        val stats = QRSecurityValidator.getInstance().getSecurityStats()
        return """
            Security Statistics:
            - Total used QR codes: ${stats.totalUsedQRCodes}
            - Active sessions: ${stats.activeSessions}
            - Total sessions: ${stats.totalSessions}
            - Students with attempts: ${stats.studentsWithAttempts}
        """.trimIndent()
    }
}

/**
 * Result of debug test
 */
data class DebugTestResult(
    val success: Boolean,
    val report: String
)