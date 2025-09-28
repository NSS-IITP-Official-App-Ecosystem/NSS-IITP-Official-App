package com.phad.chatapp

import com.phad.chatapp.models.AttendanceEvent
import com.google.firebase.Timestamp
import org.junit.Test
import org.junit.Assert.*

/**
 * Test class to verify the Hours field integration in the Create New Event form
 */
class HoursFieldIntegrationTest {

    @Test
    fun testAttendanceEventWithHours() {
        // Test creating an AttendanceEvent with hours field
        val event = AttendanceEvent(
            id = "27_07_test_event",
            eventDate = "27 Jul 2025",
            eventTime = "09:00 AM - 01:00 PM",
            hours = 5,
            description = "Test event with hours",
            createdBy = "admin123",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null,
            _isLive = true
        )

        // Verify hours field is properly set
        assertEquals("Hours field should be set correctly", 5, event.hours)
        assertEquals("Event should have correct ID", "27_07_test_event", event.id)
        assertEquals("Event should have correct date", "27 Jul 2025", event.eventDate)
        assertEquals("Event should have correct time", "09:00 AM - 01:00 PM", event.eventTime)
        assertTrue("Event should be live", event.isLive)
    }

    @Test
    fun testAttendanceEventWithZeroHours() {
        // Test creating an AttendanceEvent with zero hours (valid case)
        val event = AttendanceEvent(
            id = "27_07_zero_hours_event",
            eventDate = "27 Jul 2025",
            eventTime = "10:00 AM - 12:00 PM",
            hours = 0,
            description = "Event with zero hours",
            createdBy = "admin123",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null,
            _isLive = true
        )

        // Verify zero hours is valid
        assertEquals("Zero hours should be valid", 0, event.hours)
        assertEquals("Event should have correct description", "Event with zero hours", event.description)
    }

    @Test
    fun testAttendanceEventDefaultConstructor() {
        // Test that default constructor sets hours to 0
        val event = AttendanceEvent()
        
        assertEquals("Default hours should be 0", 0, event.hours)
        assertEquals("Default ID should be empty", "", event.id)
        assertEquals("Default description should be empty", "", event.description)
        assertTrue("Default event should be live", event.isLive)
    }

    @Test
    fun testHoursFieldValidation() {
        // Test various hours values
        val validHours = listOf(0, 1, 5, 10, 24, 100)
        
        for (hours in validHours) {
            val event = AttendanceEvent(
                id = "test_event_$hours",
                eventDate = "27 Jul 2025",
                eventTime = "09:00 AM - 01:00 PM",
                hours = hours,
                description = "Test event with $hours hours",
                createdBy = "admin123",
                creatorName = "Test Admin",
                createdAt = Timestamp.now(),
                attendees = emptyList(),
                closedAt = null,
                _isLive = true
            )
            
            assertEquals("Hours should be set correctly for value $hours", hours, event.hours)
            assertTrue("Hours should be non-negative", event.hours >= 0)
        }
    }

    @Test
    fun testEventCreationWithAllFields() {
        // Test comprehensive event creation with all fields including hours
        val event = AttendanceEvent(
            id = "27_07_comprehensive_event",
            eventDate = "27 Jul 2025",
            eventTime = "09:00 AM - 05:00 PM",
            hours = 8,
            description = "Comprehensive test event with all fields",
            createdBy = "admin123",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null,
            _isLive = true
        )

        // Verify all fields are set correctly
        assertNotNull("Event should not be null", event)
        assertEquals("ID should be correct", "27_07_comprehensive_event", event.id)
        assertEquals("Date should be correct", "27 Jul 2025", event.eventDate)
        assertEquals("Time should be correct", "09:00 AM - 05:00 PM", event.eventTime)
        assertEquals("Hours should be correct", 8, event.hours)
        assertEquals("Description should be correct", "Comprehensive test event with all fields", event.description)
        assertEquals("Created by should be correct", "admin123", event.createdBy)
        assertEquals("Creator name should be correct", "Test Admin", event.creatorName)
        assertNotNull("Created at should not be null", event.createdAt)
        assertTrue("Attendees should be empty", event.attendees.isEmpty())
        assertNull("Closed at should be null", event.closedAt)
        assertTrue("Event should be live", event.isLive)
    }
}
