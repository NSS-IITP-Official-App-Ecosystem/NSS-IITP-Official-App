package com.phad.chatapp.utils

import com.phad.chatapp.models.QRAttendanceData
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import android.location.Location
import com.phad.chatapp.models.AttendeeRecord
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow

import kotlinx.coroutines.test.runTest
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import com.phad.chatapp.repositories.AttendanceQRRepository
import com.phad.chatapp.models.AttendanceEvent
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QRSecurityValidatorTest {

    private lateinit var validator: QRSecurityValidator
    private val testAdminId = "admin_123"
    private val testEventId = "event_123"
    private val testSessionId = testEventId // Based on consolidated schema

    @Before
    fun setup() = runTest {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setApplicationId("1:1234567890:android:abcdef")
                .setApiKey("fake-api-key")
                .setProjectId("fake-project-id")
                .build()
            com.google.firebase.FirebaseApp.initializeApp(context, options)
        }
        try {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().useEmulator("127.0.0.1", 8080)
        } catch (e: IllegalStateException) {
            // Already using emulator
        }
        
        // Reset singleton for fresh test state if possible, or register a fresh session
        
        validator = QRSecurityValidator.getInstance()
        val mockRepo = mock(AttendanceQRRepository::class.java)
        validator.repository = mockRepo
        
        val fakeEvent = AttendanceEvent(
            id = testEventId,
            createdBy = testAdminId
            )
        `when`(mockRepo.getAttendanceEventForDuplicateCheck(org.mockito.ArgumentMatchers.anyString())).thenReturn(Result.success(fakeEvent))
        `when`(mockRepo.getAttendanceEvent(org.mockito.ArgumentMatchers.anyString())).thenReturn(Result.success(fakeEvent))

        validator.registerSession(testSessionId, testAdminId, testEventId)
    
    @Test
    fun testDeviceDuplicate_ComprehensiveDuplicateCheck() = runTest {
        val existingAttendees = listOf(
            AttendeeRecord(
                rollNumber = "student1",
                name = "Student One",
                deviceId = "DEVICE_XYZ_123"
            )
        )
        
        // Test same user, different device (User Duplicate)
        val result1 = validator.checkComprehensiveDuplicate(
            sessionId = testSessionId,
            studentId = "student1",
            deviceId = "DEVICE_ABC_456",
            existingAttendees = existingAttendees
        )
        assertEquals(true, result1.isDuplicate)
        assertEquals(DuplicateType.USER_DUPLICATE, result1.duplicateType)
        
        // Test different user, SAME device (Device Duplicate)
        val result2 = validator.checkComprehensiveDuplicate(
            sessionId = testSessionId,
            studentId = "student2",
            deviceId = "DEVICE_XYZ_123",
            existingAttendees = existingAttendees
        )
        assertEquals(true, result2.isDuplicate)
        assertEquals(DuplicateType.DEVICE_DUPLICATE, result2.duplicateType)
        
        // Test different user, different device (No Duplicate)
        val result3 = validator.checkComprehensiveDuplicate(
            sessionId = testSessionId,
            studentId = "student3",
            deviceId = "DEVICE_NEW_789",
            existingAttendees = existingAttendees
        )
        assertEquals(false, result3.isDuplicate)
        assertEquals(DuplicateType.NO_DUPLICATE, result3.duplicateType)
    }

    @Test
    fun testGeofencingLocationValidation() = runTest {
        val scanLoc = mock(Location::class.java)
        val eventLoc = mock(Location::class.java)
        
        // Test 50m (Valid)
        `when`(scanLoc.distanceTo(eventLoc)).thenReturn(50f)
        var result = validator.validateLocation(scanLoc, eventLoc, 100f)
        assertEquals(true, result.isValid)
        
        // Test exactly 100m (Valid)
        `when`(scanLoc.distanceTo(eventLoc)).thenReturn(100f)
        result = validator.validateLocation(scanLoc, eventLoc, 100f)
        assertEquals(true, result.isValid)
        
        // Test 101m (Invalid)
        `when`(scanLoc.distanceTo(eventLoc)).thenReturn(101f)
        result = validator.validateLocation(scanLoc, eventLoc, 100f)
        assertEquals(false, result.isValid)
        assertEquals(ValidationResult.LOCATION_MISMATCH, result.code)
        
        // Test missing event location (Valid)
        result = validator.validateLocation(scanLoc, null, 100f)
        assertEquals(true, result.isValid)
        
        // Test missing scan location (Invalid)
        result = validator.validateLocation(null, eventLoc, 100f)
        assertEquals(false, result.isValid)
        assertEquals(ValidationResult.MISSING_LOCATION, result.code)
    }

    @Test
    fun testServerCommunicationBreak_OfflineCacheFallback() = runTest {
        // Setup cache with a valid session
        val qrData = generateValidQRData()
        validator.registerSession(testSessionId, testAdminId, testEventId)
        
        // Simulate network failure by throwing exception on getAttendanceEvent
        val mockRepo = validator.repository as AttendanceQRRepository
        `when`(mockRepo.getAttendanceEvent(any())).thenThrow(RuntimeException("Network Offline"))
        
        // Even though repository throws, the validator should use the cached session and succeed
        val result = validator.validateQRCode(qrData, "student1", testSessionId)
        assertEquals(ValidationResult.VALID, result.code)
        assertEquals(true, result.isValid)
    }
}

    private fun generateValidQRData(
        timestamp: Long = System.currentTimeMillis(),
        qrId: String = java.util.UUID.randomUUID().toString(),
        adminId: String = testAdminId,
        sessionId: String = testSessionId,
        eventId: String = testEventId
    ): QRAttendanceData {
        val dataString = "$sessionId:$eventId:$adminId:$qrId:$timestamp:NSS_QR_SECRET"
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(dataString.toByteArray())
        val token = bytes.joinToString("") { "%02x".format(it) }.take(16)
        
        return QRAttendanceData(
            eventId = eventId,
            adminId = adminId,
            timestamp = timestamp,
            qrId = qrId,
            sessionId = sessionId,
            version = "1.0",
            validationToken = token 
        )
    }

    @Test
    fun testRateLimiting_NormalScan() = runTest {
        val qrData = generateValidQRData()
        val result = validator.validateQRCode(qrData, "2501PH07", testSessionId)
        
        assertEquals(com.phad.chatapp.utils.ValidationResult.VALID, result.code)
    }

    @Test
    fun testRateLimiting_SpamPrevention() = runTest {
        val studentId = "2501PH08"
        // Simulate 10 scans within a minute
        for (i in 1..10) {
            val data = generateValidQRData()
            validator.validateQRCode(data, studentId, testSessionId)
        }
        val data = generateValidQRData()
        val result = validator.validateQRCode(data, studentId, testSessionId)
        assertEquals(com.phad.chatapp.utils.ValidationResult.RATE_LIMITED, result.code)
    }

    @Test
    fun testReplayAttack_SingleDevice() = runTest {
        val qrData = generateValidQRData()
        // First scan
        validator.validateQRCode(qrData, "student1", testSessionId)
        // Second scan with same QR
        val result2 = validator.validateQRCode(qrData, "student1", testSessionId)
        
        assertEquals(com.phad.chatapp.utils.ValidationResult.REPLAY_ATTACK, result2.code)
    }
    
    @Test
    fun testFutureTimestampSpoofing() = runTest {
        val futureTime = System.currentTimeMillis() + 60000 // 1 minute in future
        val qrData = generateValidQRData(timestamp = futureTime)
        
        val result = validator.validateQRCode(qrData, "student1", testSessionId)
        assertEquals(com.phad.chatapp.utils.ValidationResult.INVALID_TIMESTAMP, result.code)
    }
    
    @Test
    fun testStaleQRExpired() = runTest {
        val pastTime = System.currentTimeMillis() - 15000 // 15 seconds in past (MAX is 10s)
        val qrData = generateValidQRData(timestamp = pastTime)
        
        val result = validator.validateQRCode(qrData, "student1", testSessionId)
        assertEquals(com.phad.chatapp.utils.ValidationResult.EXPIRED, result.code)
    }
    
    @Test
    fun testSessionAdminAuthorization_Mismatch() = runTest {
        // Admin ownership mismatch
        val qrData = generateValidQRData(adminId = "wrong_admin")
        
        val result = validator.validateQRCode(qrData, "student1")
        assertEquals(com.phad.chatapp.utils.ValidationResult.INVALID_SESSION, result.code)
    }
}
