package com.phad.chatapp.features.scheduling.models

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Data model for volunteer information
 */
data class VolunteerInfo(
    val rollNo: String? = null,
    val name: String = "",
    val group: String = "",
    val classCount: Int = 0  // Number of classes this volunteer can teach
)

/**
 * Optimized volunteer assignment data structure
 * Uses indices instead of redundant string data for better efficiency
 */
data class OptimizedVolunteerAssignment(
    val volunteerName: String = "",
    val volunteerRollNo: String = "",
    val volunteerGroup: String = "",
    val dayIndex: Int = 0,        // Index into DAY_NAMES array (0-6)
    val slotIndex: Int = 0,       // Index into time slot array (0-n)
    val interviewScore: Int = 0,  // Interview score from students collection
    val subjectPreference1: String = "", // First subject preference
    val subjectPreference2: String = "", // Second subject preference
    val subjectPreference3: String = "", // Third subject preference
    val subjectCode: String? = null      // Assigned subject code
)

/**
 * Group availability data structure
 * Maps day and time slot indices to available group numbers
 */
data class GroupAvailability(
    val dayIndex: Int = 0,
    val slotIndex: Int = 0,
    val availableGroups: List<String> = emptyList()
)

/**
 * Schedule reference data containing day names and time slot names
 * Stored separately from assignment data to avoid redundancy
 */
data class ScheduleReferenceData(
    val dayNames: List<String> = emptyList(),           // e.g., ["Mon", "Tue", "Wed"]
    val timeSlotNames: List<String> = emptyList(),      // e.g., ["8:00", "9:00", "10:00"]
    val schoolName: String = "",                        // e.g., "AM 10B"
    val totalDays: Int = 0,
    val totalSlots: Int = 0
)

/**
 * Data model for volunteer details from students collection
 * Contains enhanced information including interview scores and subject preferences
 */
data class VolunteerDetails(
    val name: String,
    val rollNo: String,
    val group: String,
    val interviewScore: Int,
    val subjectPreference1: String = "",
    val subjectPreference2: String = "",
    val subjectPreference3: String = ""
)

/**
 * Data model for availability slots
 */
data class AvailabilitySlot(
    val dayOfWeek: DayOfWeek? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null
)

/**
 * Data model for volunteer presets
 */
data class VolunteerPreset(
    val id: String? = null,
    val name: String = "",
    val availability: List<AvailabilitySlot> = emptyList(),
    val preferences: List<String> = emptyList()
)

/**
 * Data model for schedule
 */
data class Schedule(
    val id: String? = null,
    val name: String = "",
    val date: LocalDate? = null,
    val slots: List<ScheduledSlot> = emptyList()
)

/**
 * Data model for scheduled slot
 */
data class ScheduledSlot(
    val slotId: String = "",
    val volunteerId: String = "",
    val volunteerName: String = "",
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val dayOfWeek: DayOfWeek? = null
)

/**
 * Data model for day schedule
 */
data class DaySchedule(
    val dayOfWeek: DayOfWeek,
    val slots: List<TeachingSlot> = emptyList()
) 