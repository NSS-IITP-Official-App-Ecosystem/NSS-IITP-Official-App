package com.phad.chatapp.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.models.AttendanceEvent
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class to calculate semester-based attendance statistics
 * from the NSS_Events_Attendance collection
 */
object AttendanceStatsCalculator {
    private const val TAG = "AttendanceStatsCalculator"
    private val db = FirebaseFirestore.getInstance()
    
    // Semester date ranges
    private val SEM1_START = "01 Jul 2025"
    private val SEM1_END = "31 Dec 2025"
    private val SEM2_START = "01 Jan 2026"
    private val SEM2_END = "31 May 2026"
    
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    
    /**
     * Read semester-based statistics for a student directly from users collection
     */
    suspend fun readStudentStatsFromUsers(rollNumber: String): Triple<String, String, String> {
        try {
            Log.d(TAG, "=== ATTENDANCE STATS CALCULATOR DEBUG ===")
            Log.d(TAG, "Reading stats from users collection for student: $rollNumber")
            
            val userRef = db.collection("users").document(rollNumber)
            Log.d(TAG, "User reference created: ${userRef.path}")
            
            val userDoc = userRef.get().await()
            Log.d(TAG, "User document retrieved, exists: ${userDoc.exists()}")
            
            if (userDoc.exists()) {
                Log.d(TAG, "User document exists, reading fields...")
                Log.d(TAG, "Document data: ${userDoc.data}")
                
                // Check all available fields
                val allFields = userDoc.data?.keys ?: emptySet()
                Log.d(TAG, "Available fields in document: $allFields")
                
                val eventsAttended = userDoc.getLong("eventsAttended") ?: 0L
                val sem1HoursDouble = userDoc.getDouble("sem1Hours")
                val sem2HoursDouble = userDoc.getDouble("sem2Hours")
                
                Log.d(TAG, "Raw values - eventsAttended: $eventsAttended, sem1Hours: ${sem1HoursDouble}, sem2Hours: ${sem2HoursDouble}")
                
                // Also check for old field names in case they exist
                val eventsAttendedOld = userDoc.getLong("events_attended") ?: 0L
                val sem1HoursOldDouble = userDoc.getDouble("sem1_hours")
                val sem2HoursOldDouble = userDoc.getDouble("sem2_hours")
                
                Log.d(TAG, "Old field values - events_attended: $eventsAttendedOld, sem1_hours: ${sem1HoursOldDouble}, sem2_hours: ${sem2HoursOldDouble}")
                
                // Use the values that are not zero
                val finalEventsAttended = if (eventsAttended > 0) eventsAttended else eventsAttendedOld
                val finalSem1Hours = (sem1HoursDouble ?: sem1HoursOldDouble ?: 0.0)
                val finalSem2Hours = (sem2HoursDouble ?: sem2HoursOldDouble ?: 0.0)
                
                Log.d(TAG, "Final values to use - eventsAttended: $finalEventsAttended, sem1Hours: $finalSem1Hours, sem2Hours: $finalSem2Hours")
                
                // Determine totals
                // Total events should be the count of all docs in NSS_Events_Attendence
                val totalEventsSnapshot = db.collection("NSS_Events_Attendence").get().await()
                
                // Calculate custom total events based on wings logic
                val userWings = (userDoc.get("wings") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                var totalRelevantEvents = 0L
                
                totalEventsSnapshot.documents.forEach { doc ->
                    val event = doc.toObject(AttendanceEvent::class.java)
                    if (event != null) {
                        val eventWings = event.wings
                        val isDnc = eventWings.contains("Design and Curation Wing")
                        val isUserWing = eventWings.any { it in userWings }
                        val attended = event.attendees.any { it.rollNumber == rollNumber }
                        
                        if (event.visibleOnlyToPresent) {
                            if (attended) {
                                totalRelevantEvents++
                            }
                        } else {
                            val eventWings = event.wings
                            val isDnc = eventWings.contains("Design and Curation Wing")
                            val isUserWing = eventWings.any { it in userWings }
                            
                            if (isDnc || isUserWing || attended) {
                                totalRelevantEvents++
                            }
                        }
                    }
                }
                val totalEvents = totalRelevantEvents

                // Calculate total available hours for each semester using the same logic as legacy method
                val totalSem1Hours = calculateTotalSemesterHours(totalEventsSnapshot.documents, 1, rollNumber)
                val totalSem2Hours = calculateTotalSemesterHours(totalEventsSnapshot.documents, 2, rollNumber)

                // If any of the values look missing (zero), compute fallbacks by scanning events
                var computedSem1 = finalSem1Hours
                var computedSem2 = finalSem2Hours
                var computedEvents = finalEventsAttended
                if (computedSem1 == 0.0 || computedSem2 == 0.0 || computedEvents == 0L) {
                    var s1 = 0.0
                    var s2 = 0.0
                    var evCount = 0L
                    totalEventsSnapshot.documents.forEach { doc ->
                        val event = doc.toObject(com.phad.chatapp.models.AttendanceEvent::class.java)
                        if (event != null) {
                            val attended = event.attendees.any { it.rollNumber == rollNumber }
                            val semester = getSemesterFromDate(event.eventDate)
                            if (attended) {
                                evCount++
                                when (semester) {
                                    1 -> s1 += event.hours
                                    2 -> s2 += event.hours
                                }
                            } else if (!event.visibleOnlyToPresent && event.isMandatory && event.negativeHours > 0.0 && event.absentPenaltyApplied) {
                                val eventWings = event.wings
                                val isDnc = eventWings.contains("Design and Curation Wing")
                                val isUserWing = eventWings.any { it in userWings }
                                
                                if (isDnc || isUserWing) {
                                    when (semester) {
                                        1 -> s1 -= event.negativeHours
                                        2 -> s2 -= event.negativeHours
                                    }
                                }
                            }
                        }
                    }
                    if (computedSem1 == 0.0) computedSem1 = s1
                    if (computedSem2 == 0.0) computedSem2 = s2
                    if (computedEvents == 0L) computedEvents = evCount
                }

                Log.d(TAG, "Totals - events=$totalEvents, SEM1 total=$totalSem1Hours, SEM2 total=$totalSem2Hours")

                val sem1Formatted = AttendanceEventUtils.formatHours(computedSem1)
                val sem2Formatted = AttendanceEventUtils.formatHours(computedSem2)
                val totalSem1Formatted = AttendanceEventUtils.formatHours(totalSem1Hours)
                val totalSem2Formatted = AttendanceEventUtils.formatHours(totalSem2Hours)
                val sem1Stats = "${sem1Formatted}/$totalSem1Formatted"
                val sem2Stats = "${sem2Formatted}/$totalSem2Formatted"
                val eventsStats = "$computedEvents/$totalEvents"

                
                Log.d(TAG, "Final stats - SEM1=$sem1Stats, SEM2=$sem2Stats, Events=$eventsStats")
                Log.d(TAG, "=== ATTENDANCE STATS CALCULATOR DEBUG COMPLETE ===")
                return Triple(sem1Stats, sem2Stats, eventsStats)
            } else {
                Log.w(TAG, "User document not found for rollNumber: $rollNumber")
                Log.d(TAG, "=== ATTENDANCE STATS CALCULATOR DEBUG COMPLETE ===")
                return Triple("0/0", "0/0", "0/0")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading student stats from users collection", e)
            Log.d(TAG, "=== ATTENDANCE STATS CALCULATOR DEBUG COMPLETE ===")
            return Triple("0/0", "0/0", "0/0")
        }
    }

    /**
     * Calculate semester-based statistics for a student (legacy method - now uses users collection)
     */
    suspend fun calculateStudentStats(rollNumber: String): Triple<String, String, String> {
        try {
            Log.d(TAG, "Calculating stats for student: $rollNumber")
            
            // Get all events from NSS_Events_Attendence collection
            val eventsSnapshot = db.collection("NSS_Events_Attendence").get().await()
            
            var sem1Hours = 0.0
            var sem2Hours = 0.0
            var eventsAttended = 0
            var totalEvents = 0
            
            // Fetch user wings for total calculation
            val userDoc = db.collection("users").document(rollNumber).get().await()
            val userWings = (userDoc.get("wings") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            
            eventsSnapshot.documents.forEach { doc ->
                val event = doc.toObject(AttendanceEvent::class.java)
                if (event != null) {
                    // Check if event counts towards total
                    val eventWings = event.wings
                    val isDnc = eventWings.contains("Design and Curation Wing")
                    val isUserWing = eventWings.any { it in userWings }
                    val attended = event.attendees.any { it.rollNumber == rollNumber }

                    if (event.visibleOnlyToPresent) {
                        // STRICT CHECK: If visibleOnlyToPresent is true, ONLY count if attended. 
                        // Wing membership does NOT matter.
                        if (attended) {
                            totalEvents++
                        }
                    } else if (isDnc || isUserWing || attended) {
                        // Standard logic: Count if DNC, User's Wing, or Attended
                        totalEvents++
                    }

                    Log.d(TAG, "Processing event: ${event.id}, date: ${event.eventDate}, hours: ${event.hours}")
                    Log.d(TAG, "Event attendees: ${event.attendees.map { it.rollNumber }}")
                    
                    // Check if student attended this event
                    if (attended) {
                        eventsAttended++
                        Log.d(TAG, "Student $rollNumber attended event ${event.id}")
                        
                        // Determine semester based on event date
                        val semester = getSemesterFromDate(event.eventDate)
                        Log.d(TAG, "Event ${event.id} is in semester $semester")
                        when (semester) {
                            1 -> sem1Hours += event.hours
                            2 -> sem2Hours += event.hours
                        }
                    } else if (!event.visibleOnlyToPresent && event.isMandatory && event.negativeHours > 0.0 && event.absentPenaltyApplied) {
                        val eventWings = event.wings
                        val isDnc = eventWings.contains("Design and Curation Wing")
                        val isUserWing = eventWings.any { it in userWings }
                        
                        if (isDnc || isUserWing) {
                            val semester = getSemesterFromDate(event.eventDate)
                            when (semester) {
                                1 -> sem1Hours -= event.negativeHours
                                2 -> sem2Hours -= event.negativeHours
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "Failed to parse event document: ${doc.id}")
                }
            }
            
            // Calculate total hours for each semester
            val totalSem1Hours = calculateTotalSemesterHours(eventsSnapshot.documents, 1, rollNumber)
            val totalSem2Hours = calculateTotalSemesterHours(eventsSnapshot.documents, 2, rollNumber)
            
            val totalSem1Formatted = AttendanceEventUtils.formatHours(totalSem1Hours)
            val totalSem2Formatted = AttendanceEventUtils.formatHours(totalSem2Hours)
            
            val sem1Formatted = AttendanceEventUtils.formatHours(sem1Hours)
            val sem2Formatted = AttendanceEventUtils.formatHours(sem2Hours)
            
            val sem1Stats = "$sem1Formatted/$totalSem1Formatted"
            val sem2Stats = "$sem2Formatted/$totalSem2Formatted"
            val eventsStats = "$eventsAttended/$totalEvents"
            
            Log.d(TAG, "Stats calculated - SEM1: $sem1Stats, SEM2: $sem2Stats, Events: $eventsStats")
            
            return Triple(sem1Stats, sem2Stats, eventsStats)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating student stats", e)
            return Triple("0/0", "0/0", "0/0")
        }
    }
    
    /**
     * Determine semester from event date
     * Semester 1: July 1 - December 10 (any year)
     * Semester 2: December 11 - June 30 (any year)
     */
    private fun getSemesterFromDate(eventDate: String): Int {
        try {
            Log.d(TAG, "Parsing date: '$eventDate'")
            val date = dateFormat.parse(eventDate)
            if (date != null) {
                val calendar = Calendar.getInstance()
                calendar.time = date
                
                val month = calendar.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                
                Log.d(TAG, "Parsed date: month=$month, day=$day")
                
                // Semester 1: July 1 - December 10
                if (month in 7..11) {
                    Log.d(TAG, "Date belongs to Semester 1")
                    return 1
                } else if (month == 12 && day <= 10) {
                    Log.d(TAG, "Date belongs to Semester 1")
                    return 1
                }
                // Semester 2: December 11 - June 30
                else if (month == 12 && day >= 11) {
                    Log.d(TAG, "Date belongs to Semester 2")
                    return 2
                } else if (month in 1..6) {
                    Log.d(TAG, "Date belongs to Semester 2")
                    return 2
                } else {
                    Log.d(TAG, "Date does not belong to any semester")
                }
            } else {
                Log.w(TAG, "Failed to parse date: '$eventDate'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing date: '$eventDate'", e)
        }
        
        return 0 // Not in any semester
    }
    
    /**
     * Calculate total hours for a specific semester
     */
    private fun calculateTotalSemesterHours(documents: List<com.google.firebase.firestore.DocumentSnapshot>, semester: Int, rollNumber: String? = null): Double {
        var totalHours = 0.0
        
        documents.forEach { doc ->
            val event = doc.toObject(AttendanceEvent::class.java)
            if (event != null) {
                val eventSemester = getSemesterFromDate(event.eventDate)
                if (eventSemester == semester) {
                    if (event.visibleOnlyToPresent) {
                        if (rollNumber != null && event.hasStudentAttended(rollNumber)) {
                            totalHours += event.hours
                        }
                    } else {
                        totalHours += event.hours
                    }
                }
            }
        }
        
        return totalHours
    }
    
    /**
     * Update student document with new statistics
     */
    suspend fun updateStudentStats(rollNumber: String) {
        try {
            val (sem1Stats, sem2Stats, eventsStats) = calculateStudentStats(rollNumber)
            
            // Parse the stats to get individual values
            val sem1Hours = sem1Stats.split("/")[0].toDoubleOrNull() ?: 0.0
            val sem2Hours = sem2Stats.split("/")[0].toDoubleOrNull() ?: 0.0
            val eventsAttended = eventsStats.split("/")[0].toIntOrNull() ?: 0
            
            // Update user document
            val userRef = db.collection("users").document(rollNumber)
            val updates = mapOf(
                "sem1Hours" to sem1Hours,
                "sem2Hours" to sem2Hours,
                "eventsAttended" to eventsAttended,
                "hours" to (sem1Hours + sem2Hours) // Keep the old field updated
            )
            
            userRef.update(updates).await()
            Log.d(TAG, "Updated user stats for $rollNumber: $updates")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating student stats", e)
        }
    }
}
