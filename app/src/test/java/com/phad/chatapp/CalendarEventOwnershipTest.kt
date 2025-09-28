package com.phad.chatapp

import com.phad.chatapp.models.AttendanceEvent
import com.google.firebase.Timestamp
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

/**
 * Test class to verify the calendar day styling logic based on event ownership
 */
class CalendarEventOwnershipTest {

    /**
     * Helper function to simulate the getAdminEventStatusForDay logic
     * This mirrors the logic from NssCalendarFragment
     */
    private fun getAdminEventStatusForDay(
        date: LocalDate,
        events: List<AttendanceEvent>,
        isFutureDate: Boolean,
        currentUserRollNumber: String
    ): AdminEventStatus {
        if (events.isEmpty()) {
            return if (isFutureDate) {
                AdminEventStatus.MISSING_EVENTS
            } else {
                AdminEventStatus.NO_EVENTS_NEEDED
            }
        }

        // Check if all events on this day were created by the current user
        val allEventsCreatedByCurrentUser = events.all { event ->
            event.createdBy == currentUserRollNumber
        }

        return if (allEventsCreatedByCurrentUser) {
            AdminEventStatus.ALL_EVENTS_CREATED // Green - all events created by current user
        } else {
            AdminEventStatus.MISSING_EVENTS // Orange - some events created by others
        }
    }

    private enum class AdminEventStatus {
        ALL_EVENTS_CREATED, // All events on this day were created by the current user
        MISSING_EVENTS,     // Some events on this day were created by other users
        NO_EVENTS_NEEDED    // No events are needed for this day
    }

    @Test
    fun testAllEventsCreatedByCurrentUser() {
        val currentUser = "admin123"
        val testDate = LocalDate.of(2025, 8, 22)
        
        // Create events all created by the current user
        val events = listOf(
            AttendanceEvent(
                id = "22_Aug_Event1",
                eventDate = "22 Aug 2025",
                eventTime = "09:00 AM - 11:00 AM",
                description = "Event 1",
                createdBy = currentUser,
                creatorName = "Current Admin",
                createdAt = Timestamp.now(),
                attendees = emptyList(),
                closedAt = null,
                _isLive = true
            ),
            AttendanceEvent(
                id = "22_Aug_Event2",
                eventDate = "22 Aug 2025",
                eventTime = "02:00 PM - 04:00 PM",
                description = "Event 2",
                createdBy = currentUser,
                creatorName = "Current Admin",
                createdAt = Timestamp.now(),
                attendees = emptyList(),
                closedAt = null,
                _isLive = true
            )
        )

        val status = getAdminEventStatusForDay(testDate, events, false, currentUser)
        assertEquals("Should return ALL_EVENTS_CREATED when all events are created by current user", 
                    AdminEventStatus.ALL_EVENTS_CREATED, status)
    }

    @Test
    fun testMixedEventOwnership() {
        val currentUser = "admin123"
        val otherUser = "admin456"
        val testDate = LocalDate.of(2025, 8, 22)
        
        // Create events with mixed ownership
        val events = listOf(
            AttendanceEvent(
                id = "22_Aug_Event1",
                eventDate = "22 Aug 2025",
                eventTime = "09:00 AM - 11:00 AM",
                description = "Event 1",
                createdBy = currentUser,
                creatorName = "Current Admin",
                createdAt = Timestamp.now(),
                attendees = emptyList(),
                closedAt = null,
                _isLive = true
            ),
            AttendanceEvent(
                id = "22_Aug_Event2",
                eventDate = "22 Aug 2025",
                eventTime = "02:00 PM - 04:00 PM",
                description = "Event 2",
                createdBy = otherUser,
                creatorName = "Other Admin",
                createdAt = Timestamp.now(),
                attendees = emptyList(),
                closedAt = null,
                _isLive = true
            )
        )

        val status = getAdminEventStatusForDay(testDate, events, false, currentUser)
        assertEquals("Should return MISSING_EVENTS when events have mixed ownership", 
                    AdminEventStatus.MISSING_EVENTS, status)
    }

    @Test
    fun testAllEventsCreatedByOtherUser() {
        val currentUser = "admin123"
        val otherUser = "admin456"
        val testDate = LocalDate.of(2025, 8, 22)
        
        // Create events all created by another user
        val events = listOf(
            AttendanceEvent(
                id = "22_Aug_Event1",
                eventDate = "22 Aug 2025",
                eventTime = "09:00 AM - 11:00 AM",
                description = "Event 1",
                createdBy = otherUser,
                creatorName = "Other Admin",
                createdAt = Timestamp.now(),
                attendees = emptyList(),
                closedAt = null,
                _isLive = true
            ),
            AttendanceEvent(
                id = "22_Aug_Event2",
                eventDate = "22 Aug 2025",
                eventTime = "02:00 PM - 04:00 PM",
                description = "Event 2",
                createdBy = otherUser,
                creatorName = "Other Admin",
                createdAt = Timestamp.now(),
                attendees = emptyList(),
                closedAt = null,
                _isLive = true
            )
        )

        val status = getAdminEventStatusForDay(testDate, events, false, currentUser)
        assertEquals("Should return MISSING_EVENTS when all events are created by other users", 
                    AdminEventStatus.MISSING_EVENTS, status)
    }

    @Test
    fun testNoEventsOnPastDate() {
        val currentUser = "admin123"
        val testDate = LocalDate.of(2025, 8, 20) // Past date
        val events = emptyList<AttendanceEvent>()

        val status = getAdminEventStatusForDay(testDate, events, false, currentUser)
        assertEquals("Should return NO_EVENTS_NEEDED for past dates with no events", 
                    AdminEventStatus.NO_EVENTS_NEEDED, status)
    }

    @Test
    fun testNoEventsOnFutureDate() {
        val currentUser = "admin123"
        val testDate = LocalDate.of(2025, 8, 25) // Future date
        val events = emptyList<AttendanceEvent>()

        val status = getAdminEventStatusForDay(testDate, events, true, currentUser)
        assertEquals("Should return MISSING_EVENTS for future dates with no events", 
                    AdminEventStatus.MISSING_EVENTS, status)
    }

    @Test
    fun testSingleEventCreatedByCurrentUser() {
        val currentUser = "admin123"
        val testDate = LocalDate.of(2025, 8, 22)
        
        // Create single event created by current user
        val events = listOf(
            AttendanceEvent(
                id = "22_Aug_Single_Event",
                eventDate = "22 Aug 2025",
                eventTime = "09:00 AM - 11:00 AM",
                description = "Single Event",
                createdBy = currentUser,
                creatorName = "Current Admin",
                createdAt = Timestamp.now(),
                attendees = emptyList(),
                closedAt = null,
                _isLive = true
            )
        )

        val status = getAdminEventStatusForDay(testDate, events, false, currentUser)
        assertEquals("Should return ALL_EVENTS_CREATED for single event created by current user", 
                    AdminEventStatus.ALL_EVENTS_CREATED, status)
    }

    @Test
    fun testEmptyCreatedByField() {
        val currentUser = "admin123"
        val testDate = LocalDate.of(2025, 8, 22)
        
        // Create event with empty createdBy field
        val events = listOf(
            AttendanceEvent(
                id = "22_Aug_Empty_Creator",
                eventDate = "22 Aug 2025",
                eventTime = "09:00 AM - 11:00 AM",
                description = "Event with empty creator",
                createdBy = "", // Empty creator
                creatorName = "",
                createdAt = Timestamp.now(),
                attendees = emptyList(),
                closedAt = null,
                _isLive = true
            )
        )

        val status = getAdminEventStatusForDay(testDate, events, false, currentUser)
        assertEquals("Should return MISSING_EVENTS for events with empty createdBy field", 
                    AdminEventStatus.MISSING_EVENTS, status)
    }
}
