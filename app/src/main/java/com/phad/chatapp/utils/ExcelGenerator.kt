package com.phad.chatapp.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.models.AttendeeRecord
import com.phad.chatapp.models.User
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for generating Excel-compatible CSV reports for NSS attendance events
 */
class ExcelGenerator(private val context: Context) {
    
    companion object {
        private const val TAG = "ExcelGenerator"
    }
    
    /**
     * Generate an Attendance Matrix CSV file with columns: Name, Roll, Wing, Total Hours, and all Event Names.
     * For each student row, place event hours if present, blank if absent.
     */
    suspend fun generateAttendanceMatrixReport(
        students: List<User>,
        events: List<AttendanceEvent>,
        perStudentEventHours: Map<String, Map<String, Double>>, // roll -> (eventId -> hours)
        totalHoursPerStudent: Map<String, Double>
    ): String? = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "NSS_Attendance_Matrix_${timestamp}.csv"

            val cacheDir = context.cacheDir
            val excelDir = File(cacheDir, "images")
            if (!excelDir.exists()) excelDir.mkdirs()
            val excelFile = File(excelDir, filename)

            val writer = FileWriter(excelFile)
            
            // Write title
            writer.write("NSS Attendance Matrix\n")
            writer.write("Events: ${events.size} | Students: ${students.size}\n")
            writer.write("\n")

            // Write headers
            val headers = mutableListOf("Name", "Roll", "Wing", "Total Hours")
            headers.addAll(events.map { it.getEventName() })
            writer.write(headers.joinToString(","))
            writer.write("\n")

            // Write data rows
            students.forEach { student ->
                val roll = student.rollNumber.ifEmpty { student.id }
                val wing = if (student.wings.isNotEmpty()) student.wings.joinToString(",") else ""
                val totalHours = totalHoursPerStudent[roll] ?: 0

                val row = mutableListOf(
                    escapeCsvValue(student.name),
                    escapeCsvValue(roll),
                    escapeCsvValue(wing),
                    totalHours.toString()
                )

                // Event columns
                val perEvent = perStudentEventHours[roll] ?: perStudentEventHours[student.rollNumber] ?: emptyMap()
                events.forEach { event ->
                    val hours = perEvent[event.id]
                    row.add(hours?.toString() ?: "")
                }

                writer.write(row.joinToString(","))
                writer.write("\n")
            }

            writer.close()

            Log.d(TAG, "CSV file generated: ${excelFile.absolutePath}")
            return@withContext excelFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error generating attendance matrix CSV", e)
            return@withContext null
        }
    }

    /**
     * Generate a student's attended events list for a semester
     */
    suspend fun generateStudentEventsList(
        studentName: String,
        rollNumber: String,
        semester: Int,
        rows: List<Triple<String, String, Double>>
    ): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating student events list CSV for $studentName ($rollNumber), semester $semester")
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "NSS_S${semester}_${rollNumber}_Events_${timestamp}.csv"

            val cacheDir = context.cacheDir
            val excelDir = File(cacheDir, "images")
            if (!excelDir.exists()) {
                excelDir.mkdirs()
            }
            val excelFile = File(excelDir, filename)

            val writer = FileWriter(excelFile)
            
            // Write title
            writer.write("Semester $semester Events Summary\n")
            writer.write("\n")

            // Write student details
            writer.write("Name:,$studentName\n")
            writer.write("Roll Number:,$rollNumber\n")
            writer.write("Semester:,$semester\n")
            writer.write("Total Hours:,${rows.sumOf { it.third }}\n")
            writer.write("Events Attended:,${rows.size}\n")
            writer.write("\n")

            // Write table headers
            writer.write("Event,Date,Hours\n")

            // Write data rows
            rows.forEach { (name, date, hours) ->
                writer.write("${escapeCsvValue(name)},${escapeCsvValue(date)},$hours\n")
            }

            writer.close()

            Log.d(TAG, "Student events list CSV generated: ${excelFile.absolutePath}")
            return@withContext excelFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error generating student events list CSV", e)
            return@withContext null
        }
    }

    /**
     * Generate CSV report for attendance event with attendees sorted by NSS group
     */
    suspend fun generateAttendanceReport(
        event: AttendanceEvent,
        attendees: List<AttendeeRecord>
    ): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating CSV report for event: ${event.getEventName()}")
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "NSS_Attendance_${event.getEventName().replace(" ", "_")}_$timestamp.csv"
            
            val cacheDir = context.cacheDir
            val excelDir = File(cacheDir, "images")
            if (!excelDir.exists()) {
                excelDir.mkdirs()
            }
            val excelFile = File(excelDir, filename)

            val writer = FileWriter(excelFile)
            
            // Write title
            writer.write("NSS Attendance Report\n")
            writer.write("\n")

            // Write event details
            writer.write("Event:,${event.getEventName()}\n")
            writer.write("Date:,${event.getFormattedEventDate()}\n")
            writer.write("Time:,${event.getFormattedTimeRange()}\n")
            writer.write("Location:,${event.location.ifEmpty { "Not specified" }}\n")
            writer.write("Hours:,${event.hours}\n")
            if (event.description.isNotEmpty()) {
                writer.write("Description:,${event.description}\n")
            }
            writer.write("\n")

            if (attendees.isNotEmpty()) {
                // Group attendees by NSS group
                val groupedAttendees = runBlockingGroupBy(attendees)

                // Write table headers
                writer.write("NSS Group,Name,Roll Number\n")

                // Write data rows
                groupedAttendees.forEach { (nssGroup, groupAttendees) ->
                    groupAttendees.forEach { attendee ->
                        writer.write("${escapeCsvValue(nssGroup)},${escapeCsvValue(attendee.name)},${escapeCsvValue(attendee.rollNumber)}\n")
                    }
                }

                writer.write("\n")
                
                // Write summary
                writer.write("Total Attendees:,${attendees.size}\n")
                writer.write("NSS Groups Represented:,${groupedAttendees.keys.size}\n")
                val currentTime = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date())
                writer.write("Report Generated:,$currentTime\n")
            } else {
                writer.write("No attendees recorded for this event.\n")
            }

            writer.close()

            Log.d(TAG, "CSV file generated: ${excelFile.absolutePath}")
            return@withContext excelFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error generating CSV report", e)
            return@withContext null
        }
    }

    /**
     * Group attendees by their NSS group
     */
    private fun runBlockingGroupBy(attendees: List<AttendeeRecord>): Map<String, List<AttendeeRecord>> {
        return kotlinx.coroutines.runBlocking {
            groupAttendeesByNSSGroup(attendees)
        }
    }

    private suspend fun groupAttendeesByNSSGroup(attendees: List<AttendeeRecord>): Map<String, List<AttendeeRecord>> {
        val groupedAttendees = mutableMapOf<String, MutableList<AttendeeRecord>>()
        
        for (attendee in attendees) {
            val nssGroup = getNSSGroupForStudent(attendee.rollNumber)
            if (!groupedAttendees.containsKey(nssGroup)) {
                groupedAttendees[nssGroup] = mutableListOf()
            }
            groupedAttendees[nssGroup]?.add(attendee)
        }
        
        // Sort each group by name
        groupedAttendees.forEach { (_, groupAttendees) ->
            groupAttendees.sortBy { it.name }
        }
        
        // Sort groups alphabetically
        return groupedAttendees.toSortedMap()
    }
    
    /**
     * Get NSS group for a student by their roll number
     */
    private suspend fun getNSSGroupForStudent(rollNumber: String): String {
        return try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val studentDoc = db.collection("Student").document(rollNumber).get().await()
            
            if (studentDoc.exists()) {
                studentDoc.getString("NSS_gro") ?: "Unknown"
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching NSS group for student $rollNumber", e)
            "Unknown"
        }
    }

    /**
     * Escape CSV values to handle commas, quotes, and newlines
     */
    private fun escapeCsvValue(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}