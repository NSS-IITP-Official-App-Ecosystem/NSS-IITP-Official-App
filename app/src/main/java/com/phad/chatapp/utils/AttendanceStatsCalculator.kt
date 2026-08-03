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
    
    // Semester date ranges (must match server-side logic in onAttendanceCreate)
    // Semester 1: July 1 – December 10
    // Semester 2: December 11 – June 30
    private val SEM1_START = "01 Jul 2025"
    private val SEM1_END = "10 Dec 2025"
    private val SEM2_START = "11 Dec 2025"
    private val SEM2_END = "30 Jun 2026"
    
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    
    /**
     * Read semester-based statistics for a student directly from users collection
     */
    suspend fun readStudentStatsFromUsers(rollNumber: String): Triple<String, String, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
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
                
                val eventsAttended = (userDoc.get("eventsAttended") as? Number)?.toLong() ?: 0L
                val sem1HoursDouble = (userDoc.get("sem1Hours") as? Number)?.toDouble()
                val sem2HoursDouble = (userDoc.get("sem2Hours") as? Number)?.toDouble()
                
                Log.d(TAG, "Raw values - eventsAttended: $eventsAttended, sem1Hours: ${sem1HoursDouble}, sem2Hours: ${sem2HoursDouble}")
                
                // Also check for old field names in case they exist
                val eventsAttendedOld = (userDoc.get("events_attended") as? Number)?.toLong() ?: 0L
                val sem1HoursOldDouble = (userDoc.get("sem1_hours") as? Number)?.toDouble()
                val sem2HoursOldDouble = (userDoc.get("sem2_hours") as? Number)?.toDouble()
                
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

                var computedSem1 = finalSem1Hours
                var computedSem2 = finalSem2Hours
                var computedEvents = finalEventsAttended

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
                Triple(sem1Stats, sem2Stats, eventsStats)
            } else {
                Log.w(TAG, "User document not found for rollNumber: $rollNumber")
                Log.d(TAG, "=== ATTENDANCE STATS CALCULATOR DEBUG COMPLETE ===")
                Triple("0/0", "0/0", "0/0")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading student stats from users collection", e)
            Log.d(TAG, "=== ATTENDANCE STATS CALCULATOR DEBUG COMPLETE ===")
            Triple("0/0", "0/0", "0/0")
        }
    }
    
    /**
     * Legacy wrapper: Returns the stats directly from the users collection without recalculating.
     */
    suspend fun calculateStudentStats(rollNumber: String): Triple<String, String, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        return@withContext readStudentStatsFromUsers(rollNumber)
    }

    /**
     * Determine semester from event date (supports multi-format parsing & eventId fallback)
     * Semester 1: July 1 - December 10 (any year)
     * Semester 2: December 11 - June 30 (any year)
     */
    private fun getSemesterFromDate(eventDate: String, eventId: String? = null): Int {
        try {
            var month = -1
            var day = -1

            val trimmed = eventDate.trim()
            if (trimmed.isNotEmpty()) {
                val dateFormats = arrayOf(
                    "dd MMM yyyy", "d MMM yyyy",
                    "dd MMMM yyyy", "d MMMM yyyy",
                    "dd MMM", "d MMM",
                    "yyyy-MM-dd", "yyyy/MM/dd",
                    "dd-MM-yyyy", "dd/MM/yyyy"
                )

                for (fmt in dateFormats) {
                    try {
                        val sdf = SimpleDateFormat(fmt, Locale.ENGLISH)
                        sdf.isLenient = true
                        val parsed = sdf.parse(trimmed)
                        if (parsed != null) {
                            val cal = Calendar.getInstance()
                            cal.time = parsed
                            month = cal.get(Calendar.MONTH) + 1
                            day = cal.get(Calendar.DAY_OF_MONTH)
                            break
                        }
                    } catch (_: Exception) {}
                }
            }

            // Fallback: extract date from eventId (e.g. "15_Aug_2025_EventName" or "15_Aug_EventName")
            if ((month == -1 || day == -1) && !eventId.isNullOrBlank()) {
                val parts = eventId.split("_")
                if (parts.size >= 2) {
                    val candidateStr = "${parts[0]} ${parts[1]} ${parts.getOrNull(2) ?: ""}".trim()
                    val fallbackFormats = arrayOf("d MMM yyyy", "dd MMM yyyy", "d MMM", "dd MMM")
                    for (fmt in fallbackFormats) {
                        try {
                            val sdf = SimpleDateFormat(fmt, Locale.ENGLISH)
                            sdf.isLenient = true
                            val parsed = sdf.parse(candidateStr)
                            if (parsed != null) {
                                val cal = Calendar.getInstance()
                                cal.time = parsed
                                month = cal.get(Calendar.MONTH) + 1
                                day = cal.get(Calendar.DAY_OF_MONTH)
                                break
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            if (month != -1 && day != -1) {
                // Semester 1: July 1 - December 10
                if (month in 7..11) return 1
                if (month == 12 && day <= 10) return 1
                // Semester 2: December 11 - June 30
                if (month == 12 && day >= 11) return 2
                if (month in 1..6) return 2
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing semester date: '$eventDate'", e)
        }
        return 0
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
     * Legacy updateStudentStats removed to prevent client-side overwriting of server atomicity.
     */
}
