package com.phad.chatapp.features.calendar.models

/**
 * Enum for event status
 */
enum class EventStatus {
    SCHEDULED,    // Default status for events
    AVAILABLE,    // Available time slot
    PENDING,      // For leave applications awaiting approval
    APPROVED,     // For approved leave applications
    REJECTED,     // For rejected leave applications
    LEAVE_REQUESTED, // Leave has been requested
    LEAVE_APPROVED,  // Leave has been approved
    SLOT_BOOKED,     // Slot has been booked
    NON_TEACHING,    // Non-teaching days
    BOOKED,       // For booked event slots
    CANCELED,     // For canceled events
    ACCEPTED      // For accepted teaching events (when student accepts a class)
} 
