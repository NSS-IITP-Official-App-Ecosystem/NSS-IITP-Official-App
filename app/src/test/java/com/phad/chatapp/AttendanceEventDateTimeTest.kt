package com.phad.chatapp

import com.google.firebase.Timestamp
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.utils.AttendanceEventUtils
import org.junit.Test
import org.junit.Assert.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Unit tests for the new date/time structure in AttendanceEvent
 */
class AttendanceEventDateTimeTest {

    @Test
    fun testNewFormatEventCreation() {
        // Create test dates
        val eventDate = Date()
        val openingTime = createTime(9, 0) // 9:00 AM
        val closingTime = createTime(17, 0) // 5:00 PM

        // Create new format data
        val (dateString, timeRangeString) = AttendanceEventUtils.createNewFormatEventTimeData(
            eventDate, openingTime, closingTime
        )

        // Create event with new format
        val event = AttendanceEvent(
            id = "test_event",
            eventDate = dateString,
            eventTime = timeRangeString,
            description = "Test Event",
            createdBy = "test_admin",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null,
            _isLive = true // Explicitly set for test
        )

        // Verify format is valid
        assertTrue("Event should have valid date/time format", event.hasValidDateTimeFormat())
        assertEquals("Date string should match expected format", dateString, event.getDateString())
        assertEquals("Time range should match expected format", timeRangeString, event.getTimeRangeString())
    }

    @Test
    fun testEmptyDateTimeFields() {
        // Create event with empty date/time fields
        val event = AttendanceEvent(
            id = "empty_event",
            eventDate = "", // Empty date
            eventTime = "", // Empty time
            description = "Empty Event",
            createdBy = "test_admin",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null
        )

        // Verify invalid format is detected
        assertFalse("Event should not have valid date/time format", event.hasValidDateTimeFormat())

        // Verify methods handle empty fields gracefully
        val dateString = event.getDateString()
        val timeRange = event.getTimeRangeString()

        assertEquals("Date string should be empty", "", dateString)
        assertEquals("Time range should show default fallback", "9:00 AM - 5:00 PM", timeRange)
    }

    @Test
    fun testDateTimeFormatValidation() {
        // Test valid new date string format (DD MMM YYYY)
        assertTrue("Valid new date string should pass validation",
            AttendanceEventUtils.isValidDateStringFormat("23 Jul 2025"))

        // Test valid old date string format (YYYY-MM-DD) for backward compatibility
        assertTrue("Valid old date string should pass validation",
            AttendanceEventUtils.isValidDateStringFormat("2025-07-23"))

        assertFalse("Invalid date string should fail validation",
            AttendanceEventUtils.isValidDateStringFormat("23-07-2025"))

        // Test valid time range format
        assertTrue("Valid time range should pass validation",
            AttendanceEventUtils.isValidTimeRangeFormat("09:00 AM - 05:00 PM"))

        assertFalse("Invalid time range should fail validation",
            AttendanceEventUtils.isValidTimeRangeFormat("9:00 - 17:00"))
    }

    @Test
    fun testTimeRangeValidation() {
        // Test valid time range (closing after opening)
        assertTrue("Valid time range should pass validation", 
            AttendanceEventUtils.validateTimeRangeString("09:00 AM - 05:00 PM"))
        
        // Test invalid time range (closing before opening)
        assertFalse("Invalid time range should fail validation", 
            AttendanceEventUtils.validateTimeRangeString("05:00 PM - 09:00 AM"))
    }

    @Test
    fun testValidDateTimeFormatUtility() {
        // Create event with valid date/time in new format
        val validEvent = AttendanceEvent(
            id = "valid_test",
            eventDate = "23 Jul 2025",
            eventTime = "09:00 AM - 05:00 PM",
            description = "Valid Test",
            createdBy = "test_admin",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null
        )

        // Create event with invalid date/time
        val invalidEvent = AttendanceEvent(
            id = "invalid_test",
            eventDate = "",
            eventTime = "",
            description = "Invalid Test",
            createdBy = "test_admin",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null
        )

        // Verify validation works
        assertTrue("Valid event should have valid format",
            AttendanceEventUtils.hasValidDateTimeFormat(validEvent))
        assertFalse("Invalid event should not have valid format",
            AttendanceEventUtils.hasValidDateTimeFormat(invalidEvent))
    }

    @Test
    fun testFormattedDisplayMethods() {
        // Create event with valid date/time in new format
        val event = AttendanceEvent(
            id = "display_test",
            eventDate = "23 Jul 2025",
            eventTime = "09:30 AM - 04:45 PM",
            description = "Display Test",
            createdBy = "test_admin",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null
        )

        // Test formatted display methods
        val formattedDate = event.getFormattedEventDate()
        val formattedTime = event.getFormattedTimeRange()

        assertNotNull("Formatted date should not be null", formattedDate)
        assertNotNull("Formatted time should not be null", formattedTime)
        assertEquals("Date should match input in new format", "23 Jul 2025", formattedDate)
        assertEquals("Time range should match input", "09:30 AM - 04:45 PM", formattedTime)
    }

    @Test
    fun testBackwardCompatibility() {
        // Create event with old date format (YYYY-MM-DD)
        val oldFormatEvent = AttendanceEvent(
            id = "backward_compat_test",
            eventDate = "2025-07-23", // Old format
            eventTime = "09:00 AM - 05:00 PM",
            description = "Backward Compatibility Test",
            createdBy = "test_admin",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null
        )

        // Test that old format is detected
        assertTrue("Event should need date format migration", oldFormatEvent.needsDateFormatMigration())
        assertTrue("Old date format should be detected",
            AttendanceEventUtils.isOldDateFormat("2025-07-23"))
        assertFalse("New date format should not be detected as old",
            AttendanceEventUtils.isOldDateFormat("23 Jul 2025"))

        // Test that display methods work with old format
        val formattedDate = oldFormatEvent.getFormattedEventDate()
        assertEquals("Old format should be converted for display", "23 Jul 2025", formattedDate)

        // Test that parsing works with old format
        val dateAsDate = oldFormatEvent.getEventDateAsDate()
        assertNotNull("Date should be parsed correctly", dateAsDate)

        // Test conversion utility
        val convertedDate = AttendanceEventUtils.convertOldDateFormatToNew("2025-07-23")
        assertEquals("Date should be converted correctly", "23 Jul 2025", convertedDate)

        // Test new format in new event
        val newFormatEvent = AttendanceEvent(
            id = "new_format_test",
            eventDate = "23 Jul 2025", // New format
            eventTime = "09:00 AM - 05:00 PM",
            description = "New Format Test",
            createdBy = "test_admin",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null
        )

        assertFalse("New format event should not need migration", newFormatEvent.needsDateFormatMigration())
        assertTrue("New date format should be detected",
            AttendanceEventUtils.isNewDateFormat("23 Jul 2025"))
    }

    @Test
    fun testFirestorePropertyNameMapping() {
        // Test that the PropertyName annotations match the actual field names used in Firestore
        // This test verifies the fix for the date/time display issue
        val event = AttendanceEvent(
            id = "property_test",
            eventDate = "27 Jul 2025",
            eventTime = "12:00 AM - 01:30 PM",
            description = "Property Test",
            createdBy = "test_admin",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null
        )

        // Verify that the fields are properly populated
        assertEquals("Event date should be preserved", "27 Jul 2025", event.eventDate)
        assertEquals("Event time should be preserved", "12:00 AM - 01:30 PM", event.eventTime)

        // Verify that the display methods return the correct values (not fallback values)
        assertEquals("Formatted date should match stored value", "27 Jul 2025", event.getFormattedEventDate())
        assertEquals("Formatted time should match stored value", "12:00 AM - 01:30 PM", event.getFormattedTimeRange())

        // Verify that the event has valid date/time format
        assertTrue("Event should have valid date/time format", event.hasValidDateTimeFormat())
    }

    @Test
    fun testDateFormatUtilities() {
        // Test format detection
        assertTrue("Should detect old format", AttendanceEventUtils.isOldDateFormat("2025-12-31"))
        assertTrue("Should detect new format", AttendanceEventUtils.isNewDateFormat("31 Dec 2025"))
        assertFalse("Should not detect invalid format as old", AttendanceEventUtils.isOldDateFormat("31-12-2025"))
        assertFalse("Should not detect invalid format as new", AttendanceEventUtils.isNewDateFormat("2025-12-31"))

        // Test conversion
        assertEquals("Should convert old to new format", "31 Dec 2025",
            AttendanceEventUtils.convertOldDateFormatToNew("2025-12-31"))
        assertNull("Should return null for invalid date",
            AttendanceEventUtils.convertOldDateFormatToNew("invalid-date"))

        // Test parsing both formats
        assertNotNull("Should parse old format", AttendanceEventUtils.parseDateString("2025-12-31"))
        assertNotNull("Should parse new format", AttendanceEventUtils.parseDateString("31 Dec 2025"))
        assertNull("Should return null for invalid format", AttendanceEventUtils.parseDateString("invalid-date"))
    }

    // Helper method to create time objects
    private fun createTime(hour: Int, minute: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }
}
