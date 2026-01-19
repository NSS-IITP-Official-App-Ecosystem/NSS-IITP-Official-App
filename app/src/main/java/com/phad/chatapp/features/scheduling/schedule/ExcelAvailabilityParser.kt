package com.phad.chatapp.features.scheduling.schedule

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

object ExcelAvailabilityParser {
    private const val TAG = "ExcelDebug"

    // Regex to match groups like "G1", "G1-8", "G12-14", "G 1", "G 1-8"
    // Handles optional space after G. Uses \b to ensure we don't match "Aug 2026" (g 2026).
    private val GROUP_REGEX = Regex("\\bG\\s*(\\d+)(?:\\s*-\\s*(\\d+))?", RegexOption.IGNORE_CASE)

    data class TimeRange(val startMinutes: Int, val endMinutes: Int)

    fun parseExcelAndGetFreeGroups(
        context: Context,
        uri: Uri,
        preset: AvailabilityPreset
    ): List<AvailabilitySlot> {
        Log.d(TAG, "Starting parseExcelAndGetFreeGroups for URI: $uri")
        val inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            Log.e(TAG, "Could not open input stream from URI")
            return emptyList()
        }

        Log.d(TAG, "InputStream opened. Using Native Parser...")
        
        // Use Native Parser instead of POI
        val sheetData = NativeExcelParser.parse(inputStream) // Returns Map<RowIndex, Map<ColIndex, String>>
        
        if (sheetData.isEmpty()) {
            Log.e(TAG, "Parsed data is empty")
            return emptyList()
        }

        Log.d(TAG, "Native parse complete. Proceeding with analysis on ${sheetData.size} rows.")

        // 1. Detect Max Groups
        Log.d(TAG, "Detecting max groups...")
        val maxGroups = detectMaxGroups(sheetData)
        val allGroups = (1..maxGroups).toSet()

        Log.d(TAG, "Detected max groups: $maxGroups")

        // 2. Map Columns to Days
        Log.d(TAG, "Mapping days to columns...")
        val dayColumns = mapDaysToColumns(sheetData)
        Log.d(TAG, "Mapped days: $dayColumns")

        // 3. Map Rows to Time Ranges
        Log.d(TAG, "Mapping rows to times...")
        val rowTimes = mapRowsToTimes(sheetData)
        Log.d(TAG, "Mapped ${rowTimes.size} time rows: $rowTimes")

        val newAvailabilitySlots = mutableListOf<AvailabilitySlot>()

        // 4. Iterate through the App's Preset Schedule
        Log.d(TAG, "Iterating through preset schedule...")
        preset.schedule.forEachIndexed { dayIndex, daySchedule ->
            val dayName = daySchedule.day // e.g., "Mon", "Tue"
            val dayColumnIndex = findColumnForDay(dayColumns, dayName)

            if (dayColumnIndex != null) {
                daySchedule.slots.forEachIndexed { slotIndex, isActive ->
                    if (isActive) {
                        val timeString = preset.freeGroupTimes.getOrNull(slotIndex)?.takeIf { it.isNotEmpty() }
                            ?: preset.columnNames.getOrNull(slotIndex)
                        if (timeString != null) {
                            val appTimeRange = parseTimeRange(timeString)
                            if (appTimeRange != null) {
                                // Find all busy groups for this time range
                                val busyGroups = getBusyGroupsForTimeRange(
                                    sheetData,
                                    dayColumnIndex,
                                    rowTimes,
                                    appTimeRange
                                )

                                // Calculate free groups
                                val freeGroups = allGroups - busyGroups
                                
                                // Convert to string format
                                if (freeGroups.isNotEmpty()) {
                                    val formattedValue = freeGroups.sorted().joinToString(",")
                                    newAvailabilitySlots.add(
                                        AvailabilitySlot(
                                            slotIndex = slotIndex,
                                            dayIndex = dayIndex,
                                            value = formattedValue
                                        )
                                    )
                                }
                            } else {
                                Log.w(TAG, "Could not parse app time string: $timeString")
                            }
                        }
                    }
                }
            } else {
                Log.w(TAG, "No column found for day: $dayName")
            }
        }

        Log.d(TAG, "Finished parsing. Found ${newAvailabilitySlots.size} slots to update.")
        return newAvailabilitySlots
    }
    
    // Helper to match "Mon" to "Monday"
    private fun findColumnForDay(dayColumns: Map<String, Int>, appDay: String): Int? {
        // 1. Exact match
        if (dayColumns.containsKey(appDay)) return dayColumns[appDay]
        
        // 2. Prefix match (Excel "Monday" starts with App "Mon")
        val match = dayColumns.entries.find { it.key.startsWith(appDay, ignoreCase = true) }
        if (match != null) return match.value
        
        // 3. Reverse Alias (App "Mon" might map to Excel "Monday")
        // Just in case
        return null
    }

    private fun detectMaxGroups(data: Map<Int, Map<Int, String>>): Int {
        var maxGroup = 0
        var rowCount = 0
        
        data.values.forEach { rowMap ->
            rowCount++
            rowMap.values.forEach { text ->
                val groups = extractGroupsFromText(text)
                if (groups.isNotEmpty()) {
                    val localMax = groups.maxOrNull() ?: 0
                    // Sanity check: Ignore groups > 50 (likely year or other number)
                    if (localMax > maxGroup && localMax <= 50) {
                        maxGroup = localMax
                    }
                }
            }
        }
        
        Log.d(TAG, "Scanned $rowCount rows for max groups. Max found: $maxGroup")
        return if (maxGroup > 0) maxGroup else 24 // Fallback
    }

    private fun mapDaysToColumns(data: Map<Int, Map<Int, String>>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        // Check first few rows for day names (0 to 5)
        for (rowIndex in 0..5) {
            val row = data[rowIndex] ?: continue
            row.forEach { (colIndex, text) ->
                 val trimmed = text.trim()
                 if (trimmed.isNotEmpty() && isDayName(trimmed)) {
                     // We found a day.
                     // IMPORTANT: Since we don't handle merged cells explicitly with "Native" parser (it's hard to read merge info from another file),
                     // we assume the text is in the first column of the merge (which is how Excel stores it).
                     // So this colIndex IS the start column.
                     map[trimmed] = colIndex
                 }
            }
        }
        
        // Specific check for row 2 (index 1) if loop failed/overwrites
        if (map.isEmpty()) {
            val row = data[1]
            row?.forEach { (colIndex, text) ->
                 val trimmed = text.trim()
                 if (isDayName(trimmed)) {
                     map[trimmed] = colIndex
                 }
            }
        }
        
        return map
    }
    
    // Helper to get ALL columns for a day
    // We assume 3 columns per day as per user description "monday column ... consists of 3 column"
    private fun getColumnsForDay(startCol: Int): List<Int> {
        return listOf(startCol, startCol + 1, startCol + 2)
    }

    private fun isDayName(text: String): Boolean {
        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        return days.any { text.equals(it, ignoreCase = true) }
    }

    private fun mapRowsToTimes(data: Map<Int, Map<Int, String>>): Map<Int, TimeRange> {
        val map = mutableMapOf<Int, TimeRange>()
        // Scan column A (0) for times
        // Image shows times starting from row 4 (index 3)
        // Format: "8 AM - 8.55 AM"
        
        // Iterate all rows
        data.forEach { (rowIndex, rowMap) ->
            if (rowIndex >= 3) {
                // Check column 0
                val text = rowMap[0]?.replace("\n", " ")?.trim() ?: ""
                
                if (text.isNotEmpty()) {
                    val range = parseExcelTimeRange(text)
                    if (range != null) {
                        map[rowIndex] = range
                    }
                }
            }
        }
        return map
    }

    private fun getBusyGroupsForTimeRange(
        data: Map<Int, Map<Int, String>>,
        dayStartCol: Int,
        rowTimes: Map<Int, TimeRange>,
        appSlotRange: TimeRange
    ): Set<Int> {
        val busyGroups = mutableSetOf<Int>()
        val dayColumns = getColumnsForDay(dayStartCol)

        rowTimes.forEach { (rowIndex, rowTimeRange) ->
            // Check for overlap
            if (isOverlapping(appSlotRange, rowTimeRange)) {
                // If times overlap, check the cells in this row for the day's columns
                val rowMap = data[rowIndex]
                if (rowMap != null) {
                    dayColumns.forEach { colIndex ->
                        val text = rowMap[colIndex] ?: ""
                        if (text.isNotEmpty()) {
                            val groups = extractGroupsFromText(text)
                            busyGroups.addAll(groups)
                        }
                    }
                }
            }
        }
        return busyGroups
    }
    
    // Time Logic
    
    // Excel Format: "8 AM - 8.55 AM", "12 Noon - 12.55 PM"
    private fun parseExcelTimeRange(text: String): TimeRange? {
        try {
            // Clean up text
            // Replace "Noon" with "PM" for parsing
            var clean = text.replace("Noon", "PM", ignoreCase = true)
                .replace("noon", "PM", ignoreCase = true)
                .replace(".", ":") // "8.55" -> "8:55"
            
            // Split by "-"
            val parts = clean.split("-")
            if (parts.size != 2) return null
            
            val start = parseTime(parts[0].trim())
            val end = parseTime(parts[1].trim())
            
            if (start != -1 && end != -1) {
                return TimeRange(start, end)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing excel time: $text")
        }
        return null
    }

    private fun parseTimeRange(text: String): TimeRange? {
        try {
             val parts = text.split("-")
             if (parts.size != 2) return null
             
             // Try to parse both with and without AM/PM
             val start = parseAppTime(parts[0].trim())
             val end = parseAppTime(parts[1].trim())
             
             if (start != -1 && end != -1) {
                 return TimeRange(start, end)
             }
        } catch (e: Exception) {
             Log.e(TAG, "Error parsing app time: $text")
        }
        return null
    }
    
    private fun parseAppTime(timeStr: String): Int {
        // Try 24hr first "HH:mm"
        var min = parseTimeFormat(timeStr, "H:mm")
        if (min == -1) min = parseTimeFormat(timeStr, "HH:mm")
        
        // Try 12hr "h:mm a"
        if (min == -1) min = parseTimeFormat(timeStr, "h:mm a")
        if (min == -1) min = parseTimeFormat(timeStr, "hh:mm a")
        
        return min
    }

    private fun parseTime(timeStr: String): Int {
        // Excel formats vary. 
        // "8 AM", "8.55 AM" (already replaced . with :) -> "8:55 AM"
        
        // Try patterns
        val patterns = listOf("h:mm a", "h a", "H:mm", "H")
        
        for (pat in patterns) {
            val m = parseTimeFormat(timeStr, pat)
            if (m != -1) return m
        }
        return -1
    }
    
    private fun parseTimeFormat(timeStr: String, pattern: String): Int {
        try {
            val sdf = SimpleDateFormat(pattern, Locale.US)
            val date = sdf.parse(timeStr)
            if (date != null) {
                val cal = Calendar.getInstance()
                cal.time = date
                return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            }
        } catch (e: Exception) {
            // ignore
        }
        return -1
    }

    private fun isOverlapping(r1: TimeRange, r2: TimeRange): Boolean {
        return max(r1.startMinutes, r2.startMinutes) < kotlin.math.min(r1.endMinutes, r2.endMinutes)
    }
    
    // Helper to extract groups from a cell string like "MA1201 (G1-8)" or "CS1201(G1-4), EE1201(G16-18)"
    fun extractGroupsFromText(text: String): Set<Int> {
        val groups = mutableSetOf<Int>()
        val matches = GROUP_REGEX.findAll(text)
        
        for (match in matches) {
            val start = match.groupValues[1].toIntOrNull() ?: continue
            val end = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: start
            
            if (start <= end) {
                for (i in start..end) {
                    groups.add(i)
                }
            }
        }
        return groups
    }
}
