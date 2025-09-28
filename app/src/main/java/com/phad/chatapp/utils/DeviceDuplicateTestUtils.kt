package com.phad.chatapp.utils

import android.content.Context
import android.util.Log
import com.phad.chatapp.models.AttendeeRecord
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.repositories.AttendanceQRRepository
import com.phad.chatapp.services.QRAttendanceService
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Test utilities for device-based duplicate prevention system
 * Provides comprehensive testing for device identification and duplicate detection
 */
object DeviceDuplicateTestUtils {
    private const val TAG = "DeviceDuplicateTest"
    
    /**
     * Test device identification functionality
     */
    suspend fun testDeviceIdentification(context: Context): TestResult = withContext(Dispatchers.Default) {
        val results = mutableListOf<String>()
        var success = true
        
        try {
            results.add("=== DEVICE IDENTIFICATION TEST ===")
            
            // Test 1: Generate device ID
            results.add("Test 1: Generating device ID...")
            val deviceId1 = DeviceIdentificationUtils.getDeviceId(context)
            results.add("✓ Device ID generated: ${deviceId1.take(16)}...")
            results.add("✓ Device ID length: ${deviceId1.length}")
            results.add("✓ Device ID valid: ${DeviceIdentificationUtils.isValidDeviceId(deviceId1)}")
            
            // Test 2: Consistency check
            results.add("\nTest 2: Checking device ID consistency...")
            val deviceId2 = DeviceIdentificationUtils.getDeviceId(context)
            val isConsistent = deviceId1 == deviceId2
            results.add("✓ Device ID consistency: $isConsistent")
            if (!isConsistent) {
                results.add("✗ ERROR: Device IDs should be consistent!")
                success = false
            }
            
            // Test 3: Device info
            results.add("\nTest 3: Getting device information...")
            val deviceInfo = DeviceIdentificationUtils.getDeviceInfo(context)
            deviceInfo.forEach { (key, value) ->
                results.add("  - $key: $value")
            }
            
            // Test 4: Clear and regenerate
            results.add("\nTest 4: Testing clear and regenerate...")
            DeviceIdentificationUtils.clearStoredDeviceId(context)
            val deviceId3 = DeviceIdentificationUtils.getDeviceId(context)
            results.add("✓ New device ID after clear: ${deviceId3.take(16)}...")
            results.add("✓ New device ID valid: ${DeviceIdentificationUtils.isValidDeviceId(deviceId3)}")
            
        } catch (e: Exception) {
            results.add("✗ ERROR: ${e.message}")
            success = false
            Log.e(TAG, "Device identification test failed", e)
        }
        
        val report = results.joinToString("\n")
        Log.d(TAG, report)
        
        return@withContext TestResult(success, report)
    }
    
    /**
     * Test duplicate detection logic
     */
    suspend fun testDuplicateDetection(context: Context): TestResult = withContext(Dispatchers.Default) {
        val results = mutableListOf<String>()
        var success = true
        
        try {
            results.add("=== DUPLICATE DETECTION TEST ===")
            
            val qrService = QRAttendanceService()
            val deviceId = DeviceIdentificationUtils.getDeviceId(context)
            val sessionId = "test_session_${System.currentTimeMillis()}"
            val studentId1 = "TEST001"
            val studentId2 = "TEST002"
            
            // Test 1: No duplicates
            results.add("Test 1: No duplicates scenario...")
            val emptyAttendees = emptyList<AttendeeRecord>()
            val noDuplicateResult = qrService.checkComprehensiveDuplicate(
                sessionId, studentId1, deviceId, emptyAttendees
            )
            results.add("✓ No duplicate check: ${!noDuplicateResult.isDuplicate}")
            results.add("✓ Duplicate type: ${noDuplicateResult.duplicateType}")
            
            // Test 2: User duplicate
            results.add("\nTest 2: User duplicate scenario...")
            val attendeesWithUser = listOf(
                AttendeeRecord(
                    rollNumber = studentId1,
                    name = "Test Student 1",
                    deviceId = "different_device_id",
                    scanTimestamp = Timestamp.now()
                )
            )
            val userDuplicateResult = qrService.checkComprehensiveDuplicate(
                sessionId, studentId1, deviceId, attendeesWithUser
            )
            results.add("✓ User duplicate detected: ${userDuplicateResult.isDuplicate}")
            results.add("✓ Duplicate type: ${userDuplicateResult.duplicateType}")
            if (userDuplicateResult.duplicateType != DuplicateType.USER_DUPLICATE) {
                results.add("✗ ERROR: Expected USER_DUPLICATE")
                success = false
            }
            
            // Test 3: Device duplicate
            results.add("\nTest 3: Device duplicate scenario...")
            val attendeesWithDevice = listOf(
                AttendeeRecord(
                    rollNumber = studentId2,
                    name = "Test Student 2",
                    deviceId = deviceId,
                    scanTimestamp = Timestamp.now()
                )
            )
            val deviceDuplicateResult = qrService.checkComprehensiveDuplicate(
                sessionId, studentId1, deviceId, attendeesWithDevice
            )
            results.add("✓ Device duplicate detected: ${deviceDuplicateResult.isDuplicate}")
            results.add("✓ Duplicate type: ${deviceDuplicateResult.duplicateType}")
            if (deviceDuplicateResult.duplicateType != DuplicateType.DEVICE_DUPLICATE) {
                results.add("✗ ERROR: Expected DEVICE_DUPLICATE")
                success = false
            }
            
            // Test 4: Both duplicates (user takes precedence)
            results.add("\nTest 4: Both user and device duplicate scenario...")
            val attendeesWithBoth = listOf(
                AttendeeRecord(
                    rollNumber = studentId1,
                    name = "Test Student 1",
                    deviceId = deviceId,
                    scanTimestamp = Timestamp.now()
                )
            )
            val bothDuplicateResult = qrService.checkComprehensiveDuplicate(
                sessionId, studentId1, deviceId, attendeesWithBoth
            )
            results.add("✓ Both duplicates - user precedence: ${bothDuplicateResult.isDuplicate}")
            results.add("✓ Duplicate type: ${bothDuplicateResult.duplicateType}")
            if (bothDuplicateResult.duplicateType != DuplicateType.USER_DUPLICATE) {
                results.add("✗ ERROR: Expected USER_DUPLICATE to take precedence")
                success = false
            }
            
        } catch (e: Exception) {
            results.add("✗ ERROR: ${e.message}")
            success = false
            Log.e(TAG, "Duplicate detection test failed", e)
        }
        
        val report = results.joinToString("\n")
        Log.d(TAG, report)
        
        return@withContext TestResult(success, report)
    }
    
    /**
     * Test repository device queries
     */
    suspend fun testRepositoryDeviceQueries(context: Context): TestResult = withContext(Dispatchers.Default) {
        val results = mutableListOf<String>()
        var success = true
        
        try {
            results.add("=== REPOSITORY DEVICE QUERIES TEST ===")
            
            val repository = AttendanceQRRepository()
            val deviceId = DeviceIdentificationUtils.getDeviceId(context)
            val testEventId = "test_event_${System.currentTimeMillis()}"
            
            // Test 1: Check device attendance for non-existent event
            results.add("Test 1: Check device attendance for non-existent event...")
            val nonExistentResult = repository.hasDeviceAttendedEvent("non_existent_event", deviceId)
            if (nonExistentResult.isSuccess) {
                results.add("✓ Non-existent event check: ${nonExistentResult.getOrNull()}")
            } else {
                results.add("✓ Non-existent event handled gracefully")
            }
            
            // Test 2: Get attendees by device ID
            results.add("\nTest 2: Get attendees by device ID...")
            val attendeesResult = repository.getAttendeesByDeviceId(deviceId)
            if (attendeesResult.isSuccess) {
                val attendees = attendeesResult.getOrNull() ?: emptyList()
                results.add("✓ Attendees found: ${attendees.size}")
            } else {
                results.add("✗ ERROR: Failed to get attendees by device ID")
                success = false
            }
            
            // Test 3: Empty device ID handling
            results.add("\nTest 3: Empty device ID handling...")
            val emptyDeviceResult = repository.hasDeviceAttendedEvent(testEventId, "")
            if (emptyDeviceResult.isSuccess && emptyDeviceResult.getOrNull() == false) {
                results.add("✓ Empty device ID handled correctly")
            } else {
                results.add("✗ ERROR: Empty device ID not handled correctly")
                success = false
            }
            
        } catch (e: Exception) {
            results.add("✗ ERROR: ${e.message}")
            success = false
            Log.e(TAG, "Repository device queries test failed", e)
        }
        
        val report = results.joinToString("\n")
        Log.d(TAG, report)
        
        return@withContext TestResult(success, report)
    }
    
    /**
     * Run all device duplicate prevention tests
     */
    suspend fun runAllTests(context: Context): TestResult = withContext(Dispatchers.Default) {
        val results = mutableListOf<String>()
        var overallSuccess = true
        
        results.add("=== DEVICE DUPLICATE PREVENTION - COMPREHENSIVE TEST SUITE ===")
        results.add("Started at: ${System.currentTimeMillis()}")
        results.add("")
        
        // Run all tests
        val deviceIdTest = testDeviceIdentification(context)
        results.add(deviceIdTest.report)
        results.add("")
        if (!deviceIdTest.success) overallSuccess = false
        
        val duplicateTest = testDuplicateDetection(context)
        results.add(duplicateTest.report)
        results.add("")
        if (!duplicateTest.success) overallSuccess = false
        
        val repositoryTest = testRepositoryDeviceQueries(context)
        results.add(repositoryTest.report)
        results.add("")
        if (!repositoryTest.success) overallSuccess = false
        
        // Summary
        results.add("=== TEST SUITE SUMMARY ===")
        results.add("Device Identification: ${if (deviceIdTest.success) "PASS" else "FAIL"}")
        results.add("Duplicate Detection: ${if (duplicateTest.success) "PASS" else "FAIL"}")
        results.add("Repository Queries: ${if (repositoryTest.success) "PASS" else "FAIL"}")
        results.add("Overall Result: ${if (overallSuccess) "PASS" else "FAIL"}")
        results.add("Completed at: ${System.currentTimeMillis()}")
        
        val finalReport = results.joinToString("\n")
        Log.d(TAG, finalReport)
        
        return@withContext TestResult(overallSuccess, finalReport)
    }
    
    /**
     * Test result data class
     */
    data class TestResult(
        val success: Boolean,
        val report: String
    )
}
