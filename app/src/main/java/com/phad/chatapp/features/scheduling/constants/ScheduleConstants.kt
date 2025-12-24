package com.phad.chatapp.features.scheduling.constants

/**
 * Constants for schedule data structure organization
 * Provides centralized reference data for days and time slots
 */
object ScheduleConstants {
    
    /**
     * Standard day names used throughout the scheduling system
     * Index corresponds to dayIndex (0-6)
     */
    val DAY_NAMES = listOf(
        "Mon",    // 0
        "Tue",    // 1
        "Wed",    // 2
        "Thu",    // 3
        "Fri",    // 4
        "Sat",    // 5
        "Sun"     // 6
    )
    
    /**
     * Full day names for display purposes
     */
    val FULL_DAY_NAMES = listOf(
        "Monday",
        "Tuesday", 
        "Wednesday",
        "Thursday",
        "Friday",
        "Saturday",
        "Sunday"
    )
    
    /**
     * Default time slot names when no custom names are provided
     * Index corresponds to slotIndex (0-n)
     */
    val DEFAULT_TIME_SLOT_NAMES = listOf(
        "Slot 1",   // 0
        "Slot 2",   // 1
        "Slot 3",   // 2
        "Slot 4",   // 3
        "Slot 5",   // 4
        "Slot 6",   // 5
        "Slot 7",   // 6
        "Slot 8"    // 7
    )
    
    /**
     * Common time labels used in teaching slot presets
     */
    val COMMON_TIME_LABELS = listOf(
        "8:00", "9:00", "10:00", "11:00", "12:00",
        "1:00", "2:00", "3:00", "4:00", "5:00"
    )
    
    /**
     * Get day name by index
     * @param dayIndex Index of the day (0-6)
     * @return Day name or "Unknown" if index is invalid
     */
    fun getDayName(dayIndex: Int): String {
        return if (dayIndex in 0 until DAY_NAMES.size) {
            DAY_NAMES[dayIndex]
        } else {
            "Unknown"
        }
    }
    
    /**
     * Get full day name by index
     * @param dayIndex Index of the day (0-6)
     * @return Full day name or "Unknown" if index is invalid
     */
    fun getFullDayName(dayIndex: Int): String {
        return if (dayIndex in 0 until FULL_DAY_NAMES.size) {
            FULL_DAY_NAMES[dayIndex]
        } else {
            "Unknown"
        }
    }
    
    /**
     * Get day index by name
     * @param dayName Name of the day (e.g., "Mon", "Tue")
     * @return Day index or -1 if not found
     */
    fun getDayIndex(dayName: String): Int {
        return DAY_NAMES.indexOf(dayName)
    }
    
    /**
     * Get default time slot name by index
     * @param slotIndex Index of the time slot (0-n)
     * @return Time slot name or "Slot X" if index is beyond defaults
     */
    fun getDefaultTimeSlotName(slotIndex: Int): String {
        return if (slotIndex in 0 until DEFAULT_TIME_SLOT_NAMES.size) {
            DEFAULT_TIME_SLOT_NAMES[slotIndex]
        } else {
            "Slot ${slotIndex + 1}"
        }
    }
}
