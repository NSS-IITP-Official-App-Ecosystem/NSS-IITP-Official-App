package com.phad.chatapp.services

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.phad.chatapp.models.QRAttendanceData
import com.phad.chatapp.utils.QRSecurityValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay

/**
 * Service for generating and validating QR codes for attendance system
 * Handles dynamic QR code generation with 2-second refresh intervals
 */
class QRAttendanceService {
    private val TAG = "QRAttendanceService"
    private val securityValidator = QRSecurityValidator.getInstance()
    
    companion object {
        private const val QR_CODE_SIZE = 1000 // Increased from 800 to 1000 for optimal quality at 400dp display size
        private const val QR_REFRESH_INTERVAL_MS = 2000L // 2 seconds
        private const val QR_VALIDITY_WINDOW_MS = 8000L // 3 seconds validity to account for processing delays
    }
    
    /**
     * Generate a QR code bitmap from attendance data
     */
    suspend fun generateQRCodeBitmap(qrData: QRAttendanceData): Result<Bitmap> = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Generating QR code for session: ${qrData.sessionId}")
            
            val qrCodeWriter = QRCodeWriter()
            val hints = hashMapOf<EncodeHintType, Any>().apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 1)
            }
            
            val bitMatrix: BitMatrix = qrCodeWriter.encode(
                qrData.toJson(),
                BarcodeFormat.QR_CODE,
                QR_CODE_SIZE,
                QR_CODE_SIZE,
                hints
            )
            
            val bitmap = createBitmapFromBitMatrix(bitMatrix)
            Log.d(TAG, "QR code generated successfully")
            
            return@withContext Result.success(bitmap)
        } catch (e: WriterException) {
            Log.e(TAG, "Error generating QR code", e)
            return@withContext Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error generating QR code", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Create bitmap from BitMatrix
     */
    private fun createBitmapFromBitMatrix(bitMatrix: BitMatrix): Bitmap {
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        
        return bitmap
    }
    
    /**
     * Generate a flow of dynamic QR codes that refresh every 2 seconds
     */
    fun generateDynamicQRCodes(
        sessionId: String,
        eventId: String,
        adminId: String
    ): Flow<Pair<QRAttendanceData, Bitmap>> = flow {
        Log.d(TAG, "Starting dynamic QR code generation for session: $sessionId")
        Log.d(TAG, "QR Generation - SessionId: $sessionId, EventId: $eventId, AdminId: $adminId")

        while (true) {
            try {
                // Create new QR data with current timestamp
                val qrData = QRAttendanceData.create(sessionId, eventId, adminId)
                Log.d(TAG, "Created QR data - AdminId: ${qrData.adminId}, SessionId: ${qrData.sessionId}, EventId: ${qrData.eventId}")
                
                // Generate QR code bitmap
                val bitmapResult = generateQRCodeBitmap(qrData)
                
                if (bitmapResult.isSuccess) {
                    val bitmap = bitmapResult.getOrThrow()
                    Log.d(TAG, "Emitting new QR code with ID: ${qrData.qrId}")
                    emit(Pair(qrData, bitmap))
                } else {
                    Log.e(TAG, "Failed to generate QR bitmap", bitmapResult.exceptionOrNull())
                }
                
                // Wait for refresh interval
                delay(QR_REFRESH_INTERVAL_MS)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in dynamic QR generation", e)
                // Continue the loop even if there's an error
                delay(QR_REFRESH_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Validate a scanned QR code with comprehensive security checks
     */
    suspend fun validateQRCode(
        qrJsonString: String,
        studentId: String,
        expectedSessionId: String? = null
    ): Result<QRValidationResult> = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Validating QR code for student: $studentId")
            Log.d(TAG, "QR JSON: ${qrJsonString.take(100)}...") // Log first 100 chars

            // Parse QR data
            val qrData = QRAttendanceData.fromJson(qrJsonString)
            if (qrData == null) {
                Log.e(TAG, "Failed to parse QR JSON data")
                return@withContext Result.success(
                    QRValidationResult(
                        isValid = false,
                        reason = "Invalid QR code format",
                        qrData = null
                    )
                )
            }

            Log.d(TAG, "Parsed QR data - SessionId: ${qrData.sessionId}, EventId: ${qrData.eventId}, Age: ${qrData.getAgeInSeconds()}s")

            // Debug admin ID flow if we have session info
            Log.d(TAG, "Expected session ID for validation: $expectedSessionId")

            // Use comprehensive security validation
            Log.d(TAG, "Starting security validation with expectedSessionId: $expectedSessionId")
            val securityValidation = securityValidator.validateQRCode(qrData, studentId, expectedSessionId)
            Log.d(TAG, "Security validation result - Valid: ${securityValidation.isValid}, Message: ${securityValidation.message}")
            Log.d(TAG, "Security validation code: ${securityValidation.code}")

            if (!securityValidation.isValid) {
                return@withContext Result.success(
                    QRValidationResult(
                        isValid = false,
                        reason = securityValidation.message,
                        qrData = qrData
                    )
                )
            }


            Log.d(TAG, "QR code validation successful")
            return@withContext Result.success(
                QRValidationResult(
                    isValid = true,
                    reason = "Valid QR code",
                    qrData = qrData
                )
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating QR code", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Check if a QR code is still within the validity window
     */
    fun isQRCodeStillValid(qrData: QRAttendanceData): Boolean {
        return qrData.isValid() && qrData.isIntegrityValid()
    }
    
    /**
     * Get remaining validity time for a QR code in seconds
     */
    fun getRemainingValidityTime(qrData: QRAttendanceData): Long {
        return qrData.getRemainingValiditySeconds()
    }
    
    /**
     * Create a test QR code for debugging purposes
     */
    suspend fun createTestQRCode(): Result<Bitmap> = withContext(Dispatchers.Default) {
        try {
            val testData = QRAttendanceData.create(
                sessionId = "test_session_123",
                eventId = "test_event_456",
                adminId = "test_admin_789"
            )
            
            return@withContext generateQRCodeBitmap(testData)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating test QR code", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Register a new attendance session for security validation
     */
    fun registerSession(sessionId: String, adminId: String, eventId: String) {
        securityValidator.registerSession(sessionId, adminId, eventId)
    }

    /**
     * End an attendance session
     */
    fun endSession(sessionId: String) {
        securityValidator.endSession(sessionId)
    }

    /**
     * Check for duplicate attendance
     */
    fun checkDuplicateAttendance(sessionId: String, studentId: String, existingAttendees: List<com.phad.chatapp.models.AttendeeRecord>): Boolean {
        return securityValidator.checkDuplicateAttendance(sessionId, studentId, existingAttendees)
    }

    /**
     * Check for device-based duplicate attendance
     */
    fun checkDeviceDuplicateAttendance(sessionId: String, deviceId: String, existingAttendees: List<com.phad.chatapp.models.AttendeeRecord>): Boolean {
        return securityValidator.checkDeviceDuplicateAttendance(sessionId, deviceId, existingAttendees)
    }

    /**
     * Comprehensive duplicate check including both user and device validation
     */
    fun checkComprehensiveDuplicate(
        sessionId: String,
        studentId: String,
        deviceId: String,
        existingAttendees: List<com.phad.chatapp.models.AttendeeRecord>
    ): com.phad.chatapp.utils.DuplicateCheckResult {
        return securityValidator.checkComprehensiveDuplicate(sessionId, studentId, deviceId, existingAttendees)
    }

    /**
     * Get security statistics
     */
    fun getSecurityStats(): com.phad.chatapp.utils.SecurityStats {
        return securityValidator.getSecurityStats()
    }
}

/**
 * Data class representing QR code validation result
 */
data class QRValidationResult(
    val isValid: Boolean,
    val reason: String,
    val qrData: QRAttendanceData?
)

/**
 * Service for scanning QR codes using ML Kit
 */
class QRScannerService {
    private val TAG = "QRScannerService"

    /**
     * Process scanned QR code text and validate it
     */
    suspend fun processScannedQR(
        qrText: String,
        qrAttendanceService: QRAttendanceService,
        studentId: String,
        expectedSessionId: String? = null
    ): Result<QRValidationResult> {
        Log.d(TAG, "Processing scanned QR code")

        return try {
            qrAttendanceService.validateQRCode(qrText, studentId, expectedSessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing scanned QR", e)
            Result.failure(e)
        }
    }



    /**
     * Check if the scanned text looks like a valid QR attendance code
     */
    fun isValidQRFormat(qrText: String): Boolean {
        return try {
            // Try to parse as JSON and check for required fields
            val qrData = QRAttendanceData.fromJson(qrText)
            qrData != null && qrData.isDataComplete()
        } catch (e: Exception) {
            false
        }
    }
}
