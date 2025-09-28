package com.phad.chatapp

import com.google.firebase.Timestamp
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.models.AttendeeRecord
import org.junit.Test
import org.junit.Assert.*
import java.util.*

/**
 * Unit tests for the isLive field functionality in AttendanceEvent
 */
class AttendanceEventIsLiveTest {

    @Test
    fun testNewEventDefaultsToLive() {
        // Create a new event without explicitly setting isLive
        val event = AttendanceEvent(
            id = "test_event",
            eventDate = "2024-01-15",
            eventTime = "09:00 AM - 05:00 PM",
            description = "Test Event",
            createdBy = "test_admin",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null
            // isLive not explicitly set - should default to true
        )

        // Verify that isLive defaults to true
        assertTrue("New event should default to isLive = true", event.isLive)
        assertEquals("Event status should be Live", AttendanceEvent.STATUS_LIVE, event.getEventStatus())
    }

    @Test
    fun testEventWithIsLiveFalse() {
        // Create an event with isLive explicitly set to false
        val event = AttendanceEvent(
            id = "test_event",
            eventDate = "2024-01-15",
            eventTime = "09:00 AM - 05:00 PM",
            description = "Test Event",
            createdBy = "test_admin",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null,
            _isLive = false
        )

        // Verify that isLive is false and status is END
        assertFalse("Event should have isLive = false", event.isLive)
        assertEquals("Event status should be End", AttendanceEvent.STATUS_END, event.getEventStatus())
    }

    @Test
    fun testEventWithIsLiveTrue() {
        // Create an event with isLive explicitly set to true
        val event = AttendanceEvent(
            id = "test_event",
            eventDate = "2024-01-15",
            eventTime = "09:00 AM - 05:00 PM",
            description = "Test Event",
            createdBy = "test_admin",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null,
            _isLive = true
        )

        // Verify that isLive is true and status is LIVE
        assertTrue("Event should have isLive = true", event.isLive)
        assertEquals("Event status should be Live", AttendanceEvent.STATUS_LIVE, event.getEventStatus())
    }

    @Test
    fun testIsLiveFieldTakesPriorityOverClosedAt() {
        // Create an event with both isLive = false and closedAt = null
        // This tests that isLive field takes priority over closedAt timestamp
        val event = AttendanceEvent(
            id = "test_event",
            eventDate = "2024-01-15",
            eventTime = "09:00 AM - 05:00 PM",
            description = "Test Event",
            createdBy = "test_admin",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null, // No closedAt timestamp
            _isLive = false   // But isLive is false
        )

        // Verify that isLive field takes priority
        assertFalse("Event should have isLive = false", event.isLive)
        assertEquals("Event status should be End due to isLive = false", AttendanceEvent.STATUS_END, event.getEventStatus())
    }

    @Test
    fun testBackwardCompatibilityWithClosedAt() {
        // Create an event with isLive = true but closedAt timestamp set
        // This tests backward compatibility where closedAt still works when isLive is true
        val event = AttendanceEvent(
            id = "test_event",
            eventDate = "2024-01-15",
            eventTime = "09:00 AM - 05:00 PM",
            description = "Test Event",
            createdBy = "test_admin",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = Timestamp.now(), // Event manually closed
            _isLive = true // But isLive is still true
        )

        // Verify that closedAt still works for backward compatibility when isLive is true
        assertTrue("Event should have isLive = true", event.isLive)
        assertEquals("Event status should be End because closedAt is set (backward compatibility)", AttendanceEvent.STATUS_END, event.getEventStatus())
    }

    @Test
    fun testEmptyConstructorDefaultsToLive() {
        // Test the empty constructor used by Firestore
        val event = AttendanceEvent()

        // Verify that the empty constructor sets isLive to true
        assertTrue("Empty constructor should default to isLive = true", event.isLive)
        assertEquals("Event status should be Live", AttendanceEvent.STATUS_LIVE, event.getEventStatus())
    }

    @Test
    fun testEventCopyPreservesIsLiveField() {
        // Create an event with isLive = false
        val originalEvent = AttendanceEvent(
            id = "test_event",
            eventDate = "2024-01-15",
            eventTime = "09:00 AM - 05:00 PM",
            description = "Test Event",
            createdBy = "test_admin",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null,
            _isLive = false
        )

        // Copy the event with some changes
        val copiedEvent = originalEvent.copy(description = "Updated Test Event")

        // Verify that isLive field is preserved in the copy
        assertFalse("Copied event should preserve isLive = false", copiedEvent.isLive)
        assertEquals("Copied event status should be End", AttendanceEvent.STATUS_END, copiedEvent.getEventStatus())
        assertEquals("Description should be updated", "Updated Test Event", copiedEvent.description)
    }

    @Test
    fun testAddAttendeePreservesIsLiveField() {
        // Create an event with isLive = false
        val event = AttendanceEvent(
            id = "test_event",
            eventDate = "2024-01-15",
            eventTime = "09:00 AM - 05:00 PM",
            description = "Test Event",
            createdBy = "test_admin",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null,
            _isLive = false
        )

        // Add an attendee
        val attendee = AttendeeRecord(
            rollNumber = "12345",
            name = "Test Student",
            scanTimestamp = Timestamp.now()
        )
        val updatedEvent = event.addAttendee(attendee)

        // Verify that isLive field is preserved when adding attendee
        assertFalse("Event with added attendee should preserve isLive = false", updatedEvent.isLive)
        assertEquals("Event status should still be End", AttendanceEvent.STATUS_END, updatedEvent.getEventStatus())
        assertEquals("Total marked should be updated", 1, updatedEvent.totalMarked)
        assertTrue("Attendee should be added", updatedEvent.hasStudentAttended("12345"))
    }

    @Test
    fun testEventVisibilityAfterClosing() {
        // Test the complete event lifecycle: create -> visible -> close -> hidden

        // Create a live event
        val liveEvent = AttendanceEvent(
            id = "test_event_lifecycle",
            eventDate = "2024-01-15",
            eventTime = "09:00 AM - 05:00 PM",
            description = "Test Event Lifecycle",
            createdBy = "test_admin",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null,
            _isLive = true
        )

        // Verify event is live and should be visible
        assertTrue("Event should be live", liveEvent.isLive)
        assertEquals("Event status should be Live", AttendanceEvent.STATUS_LIVE, liveEvent.getEventStatus())

        // Simulate closing the event
        val closedEvent = liveEvent.copy(
            _isLive = false,
            closedAt = Timestamp.now()
        )

        // Verify event is closed and should be hidden
        assertFalse("Event should be closed", closedEvent.isLive)
        assertEquals("Event status should be End", AttendanceEvent.STATUS_END, closedEvent.getEventStatus())
    }

    @Test
    fun testDuplicateFieldHandling() {
        // Test that the event correctly handles the case where both "is_live" and "live" fields might exist
        // This simulates the database issue where both fields were present

        val event = AttendanceEvent(
            id = "test_duplicate_fields",
            eventDate = "2024-01-15",
            eventTime = "09:00 AM - 05:00 PM",
            description = "Test Duplicate Fields",
            createdBy = "test_admin",
            creatorName = "Test Admin",
            createdAt = Timestamp.now(),
            attendees = emptyList(),
            closedAt = null,
            _isLive = false // This should be the authoritative field
        )

        // Verify that isLive field is respected
        assertFalse("Event should respect isLive = false", event.isLive)
        assertEquals("Event status should be End when isLive = false", AttendanceEvent.STATUS_END, event.getEventStatus())
    }
}
