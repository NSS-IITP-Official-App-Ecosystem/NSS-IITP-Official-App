package com.phad.chatapp.utils

import com.google.firebase.Timestamp
import com.phad.chatapp.models.AttendanceEvent
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for AttendanceEvent operations including date formatting and document ID generation
 */
object AttendanceEventUtils {
    
    // Month abbreviations for document ID generation
    private val MONTH_ABBREVIATIONS = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    
    /**
     * Generate document ID in format: {day}_{month}_{event_name_with_underscores}
     * @param eventDate The date of the event
     * @param eventName The name of the event
     * @return Formatted document ID
     */
    fun generateDocumentId(eventDate: Date, eventName: String): String {
        val calendar = Calendar.getInstance()
        calendar.time = eventDate
        
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val monthIndex = calendar.get(Calendar.MONTH)
        val monthAbbr = MONTH_ABBREVIATIONS[monthIndex]
        
        // Replace spaces with underscores and remove special characters
        val cleanEventName = eventName.trim()
            .replace(" ", "_")
            .replace(Regex("[^a-zA-Z0-9_]"), "")
        
        return "${day}_${monthAbbr}_${cleanEventName}"
    }
    
    /**
     * Generate document ID using Timestamp
     */
    fun generateDocumentId(eventDate: Timestamp, eventName: String): String {
        return generateDocumentId(eventDate.toDate(), eventName)
    }
    
    /**
     * Extract event name from document ID
     * @param documentId Document ID in format: {day}_{month}_{event_name_with_underscores}
     * @return Event name with spaces
     */
    fun extractEventNameFromId(documentId: String): String {
        val components = documentId.split("_")
        return if (components.size >= 3) {
            components.drop(2).joinToString("_").replace("_", " ")
        } else {
            "Unknown Event"
        }
    }
    
    /**
     * Extract date components from document ID
     * @param documentId Document ID in format: {day}_{month}_{event_name_with_underscores}
     * @return Triple of (day, month, eventName)
     */
    fun parseDocumentId(documentId: String): Triple<String, String, String> {
        val components = documentId.split("_")
        return if (components.size >= 3) {
            val day = components[0]
            val month = components[1]
            val eventName = components.drop(2).joinToString("_").replace("_", " ")
            Triple(day, month, eventName)
        } else {
            Triple("", "", documentId)
        }
    }
    
    /**
     * Convert month abbreviation to full month name
     */
    fun getFullMonthName(monthAbbr: String): String {
        val monthIndex = MONTH_ABBREVIATIONS.indexOf(monthAbbr)
        return if (monthIndex != -1) {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.MONTH, monthIndex)
            SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.time)
        } else {
            monthAbbr
        }
    }
    
    /**
     * Format date for display purposes
     * Uses English locale to ensure consistent 3-letter month abbreviations
     */
    fun formatDateForDisplay(date: Date): String {
        val formatter = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        return formatter.format(date)
    }
    
    /**
     * Format date for display purposes using Timestamp
     */
    fun formatDateForDisplay(timestamp: Timestamp): String {
        return formatDateForDisplay(timestamp.toDate())
    }
    
    /**
     * Create a Date object from day, month abbreviation, and year
     */
    fun createDateFromComponents(day: Int, monthAbbr: String, year: Int): Date? {
        val monthIndex = MONTH_ABBREVIATIONS.indexOf(monthAbbr)
        return if (monthIndex != -1) {
            val calendar = Calendar.getInstance()
            calendar.set(year, monthIndex, day, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.time
        } else {
            null
        }
    }
    
    /**
     * Validate document ID format
     */
    fun isValidDocumentIdFormat(documentId: String): Boolean {
        val components = documentId.split("_")
        if (components.size < 3) return false

        // Check if first component is a valid day (1-31)
        val day = components[0].toIntOrNull()
        if (day == null || day < 1 || day > 31) return false

        // Check if second component is a valid month abbreviation
        if (!MONTH_ABBREVIATIONS.contains(components[1])) return false

        return true
    }

    // ==================== TIME UTILITY FUNCTIONS ====================

    /**
     * Create event time map with opening and closing times (time components only)
     * Stores time as milliseconds since midnight to avoid any date contamination
     */
    fun createEventTimeMap(openingTime: Date, closingTime: Date): Map<String, Any> {
        // Extract time as milliseconds since midnight
        val openingTimeMs = extractTimeAsMilliseconds(openingTime)
        val closingTimeMs = extractTimeAsMilliseconds(closingTime)

        return mapOf(
            "opening" to openingTimeMs,
            "closing" to closingTimeMs
        )
    }

    /**
     * Extract time as milliseconds since midnight (pure time value)
     * This completely eliminates any date component
     */
    private fun extractTimeAsMilliseconds(dateTime: Date): Long {
        val calendar = Calendar.getInstance()
        calendar.time = dateTime

        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)

        // Convert to milliseconds since midnight
        return (hour * 3600L + minute * 60L + second) * 1000L
    }

    /**
     * Extract only the time component (hours, minutes, seconds) from a date
     * Returns a date normalized to epoch (1970-01-01) with only time information in UTC
     * This ensures time storage is independent of date and timezone
     */
    private fun extractTimeComponent(dateTime: Date): Date {
        val localCalendar = Calendar.getInstance() // Local timezone (IST)
        localCalendar.time = dateTime

        val hour = localCalendar.get(Calendar.HOUR_OF_DAY)
        val minute = localCalendar.get(Calendar.MINUTE)
        val second = localCalendar.get(Calendar.SECOND)

        // Create a new date with epoch date (1970-01-01) in UTC but preserve time components
        val timeOnlyCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        timeOnlyCalendar.set(1970, Calendar.JANUARY, 1, hour, minute, second)
        timeOnlyCalendar.set(Calendar.MILLISECOND, 0)

        return timeOnlyCalendar.time
    }

    /**
     * Extract time component and convert to local timezone for storage
     * This maintains the time as it appears to the user in IST
     */
    fun extractTimeComponentLocal(dateTime: Date): Date {
        val calendar = Calendar.getInstance() // Uses local timezone (IST)
        calendar.time = dateTime

        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)

        // Create epoch-based time in local timezone
        val timeOnlyCalendar = Calendar.getInstance()
        timeOnlyCalendar.set(1970, Calendar.JANUARY, 1, hour, minute, second)
        timeOnlyCalendar.set(Calendar.MILLISECOND, 0)

        return timeOnlyCalendar.time
    }

    /**
     * Create default event times (9:00 AM - 5:00 PM) with time components only
     */
    fun createDefaultEventTimes(): Map<String, Any> {
        // Create time-only dates (normalized to epoch date)
        val openingTime = createTimeOnlyDate(9, 0)  // 9:00 AM
        val closingTime = createTimeOnlyDate(17, 0) // 5:00 PM

        return createEventTimeMap(openingTime, closingTime)
    }

    /**
     * Create a date with only time information (normalized to epoch date)
     * Uses local timezone to maintain user's intended time
     */
    private fun createTimeOnlyDate(hour: Int, minute: Int): Date {
        val calendar = Calendar.getInstance() // Uses local timezone (IST)
        calendar.set(1970, Calendar.JANUARY, 1, hour, minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }

    /**
     * Create a time-only date with seconds precision
     */
    fun createTimeOnlyDateWithSeconds(hour: Int, minute: Int, second: Int): Date {
        val calendar = Calendar.getInstance() // Uses local timezone (IST)
        calendar.set(1970, Calendar.JANUARY, 1, hour, minute, second)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }

    /**
     * Normalize a date to midnight in local timezone to store only date information
     * This stores the date at 00:00:00 in the user's local timezone (IST)
     */
    fun normalizeDateToMidnight(date: Date): Date {
        val calendar = Calendar.getInstance() // Uses local timezone (IST)
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }

    /**
     * Normalize a date to midnight in local timezone (IST) for display purposes
     */
    fun normalizeDateToMidnightLocal(date: Date): Date {
        val calendar = Calendar.getInstance() // Uses local timezone (IST)
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }

    /**
     * Validate that closing time is after opening time (time components only)
     * Compares only the time portions, ignoring date components
     */
    fun validateEventTimes(openingTime: Date, closingTime: Date): Boolean {
        val openingMs = extractTimeAsMilliseconds(openingTime)
        val closingMs = extractTimeAsMilliseconds(closingTime)

        return closingMs > openingMs
    }

    /**
     * Validate that opening time is not in the past (for today's events)
     * Properly combines date and time components for validation
     */
    fun validateOpeningTimeNotInPast(eventDate: Date, openingTime: Date): Boolean {
        val today = Calendar.getInstance()
        val eventCalendar = Calendar.getInstance()
        eventCalendar.time = eventDate

        // If event is today, check if opening time is not in the past
        return if (isSameDay(today.time, eventDate)) {
            val now = Calendar.getInstance()

            // Extract time as milliseconds since midnight
            val openingTimeMs = extractTimeAsMilliseconds(openingTime)

            // Create full datetime by combining event date with opening time
            val eventOpeningTime = Calendar.getInstance()
            eventOpeningTime.time = eventDate
            eventOpeningTime.add(Calendar.MILLISECOND, openingTimeMs.toInt())

            // Allow 5 minutes grace period
            eventOpeningTime.add(Calendar.MINUTE, -5)

            now.before(eventOpeningTime)
        } else {
            true // Future dates are always valid
        }
    }

    /**
     * Check if two dates are on the same day
     */
    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal1.time = date1
        cal2.time = date2

        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Allow a grace period of 5 minutes for past opening times
     */
    private fun isWithinGracePeriod(openingTime: Date): Boolean {
        val now = Date()
        val gracePeriodMs = 5 * 60 * 1000 // 5 minutes in milliseconds
        return (now.time - openingTime.time) <= gracePeriodMs
    }

    /**
     * Format time for display in time picker
     */
    fun formatTimeForPicker(date: Date): String {
        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        return formatter.format(date)
    }

    /**
     * Create time from hour and minute
     */
    fun createTimeFromHourMinute(baseDate: Date, hour: Int, minute: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.time = baseDate
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }

    /**
     * Get hour from date
     */
    fun getHourFromDate(date: Date): Int {
        val calendar = Calendar.getInstance()
        calendar.time = date
        return calendar.get(Calendar.HOUR_OF_DAY)
    }

    /**
     * Get minute from date
     */
    fun getMinuteFromDate(date: Date): Int {
        val calendar = Calendar.getInstance()
        calendar.time = date
        return calendar.get(Calendar.MINUTE)
    }

    /**
     * Check if current time is within event's scheduled time window
     */
    fun isEventCurrentlyActive(eventTime: Map<String, Any>?): Boolean {
        if (eventTime == null) return true // Backward compatibility

        val openingTimestamp = eventTime["opening"] as? Timestamp
        val closingTimestamp = eventTime["closing"] as? Timestamp
        val now = Timestamp.now()

        return if (openingTimestamp != null && closingTimestamp != null) {
            now >= openingTimestamp && now <= closingTimestamp
        } else {
            true
        }
    }

    /**
     * Check if event has passed its closing time
     */
    fun hasEventPassedClosingTime(eventTime: Map<String, Any>?): Boolean {
        if (eventTime == null) return false // Backward compatibility

        val closingTimestamp = eventTime["closing"] as? Timestamp
        return if (closingTimestamp != null) {
            Timestamp.now() > closingTimestamp
        } else {
            false
        }
    }

    // ==================== NEW DATE/TIME FORMAT UTILITY FUNCTIONS ====================

    /**
     * Format date as DD MMM YYYY string (e.g., "24 Jan 2025")
     * Uses English locale to ensure consistent 3-letter month abbreviations
     */
    fun formatDateAsString(date: Date): String {
        val formatter = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        return formatter.format(date)
    }

    /**
     * Format time range as "HH:MM AM/PM - HH:MM AM/PM" string
     */
    fun formatTimeRange(openingTime: Date, closingTime: Date): String {
        val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val openingStr = timeFormatter.format(openingTime)
        val closingStr = timeFormatter.format(closingTime)
        return "$openingStr - $closingStr"
    }

    /**
     * Parse date string in DD MMM YYYY format to Date object
     * Also supports backward compatibility with YYYY-MM-DD format
     * Uses English locale for consistent parsing
     */
    fun parseDateString(dateString: String): Date? {
        return try {
            // Try new format first (DD MMM YYYY) with English locale
            val newFormatter = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
            newFormatter.parse(dateString)
        } catch (e: Exception) {
            try {
                // Try with default locale for backward compatibility
                val newFormatterDefault = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                newFormatterDefault.parse(dateString)
            } catch (e2: Exception) {
                try {
                    // Fall back to old format (YYYY-MM-DD) for backward compatibility
                    val oldFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                    oldFormatter.parse(dateString)
                } catch (e3: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Parse time range string "HH:MM AM/PM - HH:MM AM/PM" to opening and closing times
     * Returns Pair<openingTime, closingTime> or null if parsing fails
     */
    fun parseTimeRange(timeRangeString: String): Pair<Date, Date>? {
        return try {
            val parts = timeRangeString.split(" - ")
            if (parts.size == 2) {
                val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val openingTime = timeFormatter.parse(parts[0].trim())
                val closingTime = timeFormatter.parse(parts[1].trim())
                if (openingTime != null && closingTime != null) {
                    Pair(openingTime, closingTime)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Convert legacy eventTime map to new formatted time range string
     */
    fun convertLegacyTimeMapToString(eventTimeMap: Map<String, Any>?): String {
        if (eventTimeMap == null) return "Time not set"

        val openingMs = eventTimeMap["opening"] as? Long
        val closingMs = eventTimeMap["closing"] as? Long

        return if (openingMs != null && closingMs != null) {
            val openingTime = convertMillisecondsToTime(openingMs)
            val closingTime = convertMillisecondsToTime(closingMs)
            "$openingTime - $closingTime"
        } else {
            "Time not set"
        }
    }

    /**
     * Convert milliseconds since midnight to formatted time string (HH:MM AM/PM)
     */
    private fun convertMillisecondsToTime(milliseconds: Long): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.MILLISECOND, milliseconds.toInt())

        val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return formatter.format(calendar.time)
    }

    /**
     * Create new format event with date string and time range string
     */
    fun createNewFormatEventTimeData(eventDate: Date, openingTime: Date, closingTime: Date): Pair<String, String> {
        val dateString = formatDateAsString(eventDate)
        val timeRangeString = formatTimeRange(openingTime, closingTime)
        return Pair(dateString, timeRangeString)
    }

    /**
     * Validate time range string format
     */
    fun isValidTimeRangeFormat(timeRangeString: String): Boolean {
        val timeRangePattern = "^\\d{1,2}:\\d{2} (AM|PM) - \\d{1,2}:\\d{2} (AM|PM)$"
        return timeRangeString.matches(timeRangePattern.toRegex())
    }

    /**
     * Validate date string format (DD MMM YYYY or YYYY-MM-DD for backward compatibility)
     */
    fun isValidDateStringFormat(dateString: String): Boolean {
        // Check new format pattern (DD MMM YYYY)
        val newDatePattern = "^\\d{1,2} [A-Za-z]{3} \\d{4}$"
        // Check old format pattern (YYYY-MM-DD) for backward compatibility
        val oldDatePattern = "^\\d{4}-\\d{2}-\\d{2}$"

        val matchesPattern = dateString.matches(newDatePattern.toRegex()) ||
                           dateString.matches(oldDatePattern.toRegex())

        return matchesPattern && parseDateString(dateString) != null
    }

    /**
     * Validate time range string - check that closing time is after opening time
     */
    fun validateTimeRangeString(timeRangeString: String): Boolean {
        val timePair = parseTimeRange(timeRangeString)
        return if (timePair != null) {
            validateEventTimes(timePair.first, timePair.second)
        } else {
            false
        }
    }

    /**
     * Validate that event with new format is not in the past
     */
    fun validateNewFormatEventNotInPast(dateString: String, timeRangeString: String): Boolean {
        val eventDate = parseDateString(dateString)
        val timePair = parseTimeRange(timeRangeString)

        return if (eventDate != null && timePair != null) {
            validateOpeningTimeNotInPast(eventDate, timePair.first)
        } else {
            false
        }
    }

    // ==================== MIGRATION UTILITY FUNCTIONS ====================

    /**
     * Check if an event has valid date/time format
     */
    fun hasValidDateTimeFormat(event: AttendanceEvent): Boolean {
        return event.hasValidDateTimeFormat()
    }

    // ==================== DATE FORMAT UTILITY FUNCTIONS ====================

    /**
     * Check if a date string is in the old YYYY-MM-DD format
     */
    fun isOldDateFormat(dateString: String): Boolean {
        val oldDatePattern = "^\\d{4}-\\d{2}-\\d{2}$"
        return dateString.matches(oldDatePattern.toRegex())
    }

    /**
     * Check if a date string is in the new DD MMM YYYY format
     */
    fun isNewDateFormat(dateString: String): Boolean {
        val newDatePattern = "^\\d{1,2} [A-Za-z]{3} \\d{4}$"
        return dateString.matches(newDatePattern.toRegex())
    }

    /**
     * Convert old format date string (YYYY-MM-DD) to new format (DD MMM YYYY)
     */
    fun convertOldDateFormatToNew(oldDateString: String): String? {
        return try {
            val oldFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = oldFormatter.parse(oldDateString)
            if (date != null) {
                formatDateAsString(date)
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
