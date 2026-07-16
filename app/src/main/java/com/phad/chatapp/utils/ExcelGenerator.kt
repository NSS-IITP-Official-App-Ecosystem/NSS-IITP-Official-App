package com.phad.chatapp.utils

import android.content.Context
import android.util.Log
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.models.AttendeeRecord
import com.phad.chatapp.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for generating Excel reports for NSS attendance events
 */
class ExcelGenerator(private val context: Context) {
    
    companion object {
        private const val TAG = "ExcelGenerator"
        
        // Wing Constants
        private const val DNC_WING = com.phad.chatapp.utils.Constants.WING_DCW
        private val SPECIFIC_WINGS = listOf(
<<<<<<< HEAD
            "Environmental Wing",
            "Prerna Wing",
            "Rural Development Wing",
            "Teaching and Technical Wing"
=======
            com.phad.chatapp.utils.Constants.WING_CHN,
            com.phad.chatapp.utils.Constants.WING_ENV,
            com.phad.chatapp.utils.Constants.WING_PRY,
            com.phad.chatapp.utils.Constants.WING_RDW,
            com.phad.chatapp.utils.Constants.WING_TTW
>>>>>>> a840bfda95bbd6c15108c1beb52c041581fb4689
        )
    }
    
    // Enum to determine where hours are claimed
    private enum class ClaimSource {
        OPEN_SHEET,
        WING_SHEET
    }

    private data class ClaimResult(
        val source: ClaimSource,
        val wingName: String? = null // only if WING_SHEET
    )

    /**
     * Generate an Attendance Matrix Excel (.xlsx) file with multiple sheets.
     */
    suspend fun generateAttendanceMatrixReport(
        students: List<User>,
        events: List<AttendanceEvent>,
        perStudentEventHours: Map<String, Map<String, Double>>, // roll -> (eventId -> hours)
        totalHoursPerStudent: Map<String, Double>
    ): String? = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "NSS_Attendance_Matrix_$timestamp.xlsx"

            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val excelDir = File(downloadsDir, "NSS_Reports")
            if (!excelDir.exists()) {
                val created = excelDir.mkdirs()
                if (!created) {
                    Log.e(TAG, "Failed to create directory: ${excelDir.absolutePath}")
                    return@withContext null
                }
            }
            val excelFile = File(excelDir, filename)
            val workbook = XSSFWorkbook()

            // 1. Prepare Data Helpers
            // Identify events for Sheet 1 (Open + DNC)
            val sheet1Events = events.filter { event ->
                event.getDisplayWings() == "Open Event" || event.wings.contains(DNC_WING)
            }.sortedBy { it.getEventDateAsDate() }

            // Logic to check claim source
            fun getClaimResult(studentWings: List<String>, event: AttendanceEvent): ClaimResult {
                // If explicitly open event, it's OPEN_SHEET
                if (event.getDisplayWings() == "Open Event") {
                    return ClaimResult(ClaimSource.OPEN_SHEET)
                }
                
                // Check if student belongs to any of the event's wings (excluding DNC)
                val relevantEventWings = event.wings.filter { it != DNC_WING }
                val intersection = studentWings.intersect(relevantEventWings.toSet()).sorted()
                
                return if (intersection.isNotEmpty()) {
                    // Priority to the first alphabetical wing
                    ClaimResult(ClaimSource.WING_SHEET, intersection.first())
                } else {
                    // Fallback to Open Sheet
                    ClaimResult(ClaimSource.OPEN_SHEET)
                }
            }
            
            // Calculate Wing Hours vs Open Hours for specific student
            fun calculateSplitHours(student: User): Pair<Double, Double> {
                var wingHours = 0.0
                var openHours = 0.0
                val roll = student.rollNumber.ifEmpty { student.id }
                val attendedEvents = perStudentEventHours[roll] ?: return Pair(0.0, 0.0)

                attendedEvents.forEach { (eventId, hours) ->
                    val event = events.find { it.id == eventId }
                    if (event != null) {
                        val claim = getClaimResult(student.wings, event)
                        if (claim.source == ClaimSource.WING_SHEET) {
                            wingHours += hours
                        } else {
                            openHours += hours
                        }
                    }
                }
                return Pair(wingHours, openHours)
            }
            
            // Get extra events (cross-wing events) for a student
            fun getExtraEvents(student: User): String {
                val roll = student.rollNumber.ifEmpty { student.id }
                val attendedEvents = perStudentEventHours[roll] ?: return ""
                val extraEventsList = mutableListOf<String>()
                
                attendedEvents.forEach { (eventId, hours) ->
                    val event = events.find { it.id == eventId }
                    if (event != null) {
                        // Check if this is a cross-wing event (student not in event's wings)
                        val eventWings = event.wings.filter { it != DNC_WING }
                        val hasNoMatchingWing = student.wings.intersect(eventWings.toSet()).isEmpty()
                        
                        // If event has specific wings AND student doesn't belong to any of them
                        // AND event does NOT contain DNC (DNC events are counted in Open Events)
                        if (eventWings.isNotEmpty() && hasNoMatchingWing && 
                            event.getDisplayWings() != "Open Event" && 
                            !event.wings.contains(DNC_WING)) {
                            extraEventsList.add("${event.getEventName()} ($hours)")
                        }
                    }
                }
                return extraEventsList.joinToString("\n")
            }

            // ==================== SHEET 1 generation ====================
            val sheet1 = workbook.createSheet("Open & DNC Events")
            // Header Row
            val headerRow1 = sheet1.createRow(0)
            val headers1 = mutableListOf("Name", "Roll", "Wing", "Wing Hours", "Open Event Hours", "Extra Events")
            headers1.addAll(sheet1Events.map { it.getEventName() })
            
            // Styles
            val headerStyle = workbook.createCellStyle()
            headerStyle.fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            headerStyle.fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = workbook.createFont()
            font.bold = true
            headerStyle.setFont(font)

            headers1.forEachIndexed { index, title ->
                val cell = headerRow1.createCell(index)
                cell.setCellValue(title)
                cell.cellStyle = headerStyle
            }

            // Data Rows (All Students, sorted by Wing)
            val sortedStudents = students.sortedWith(
                compareBy(
                    { it.wings.firstOrNull() ?: "ZZZZ" }, // No wing at end
                    { it.name }
                )
            )

            sortedStudents.forEachIndexed { index, student ->
                val row = sheet1.createRow(index + 1)
                val roll = student.rollNumber.ifEmpty { student.id }
                val (wHours, oHours) = calculateSplitHours(student)

                row.createCell(0).setCellValue(student.name)
                row.createCell(1).setCellValue(roll)
                row.createCell(2).setCellValue(student.wings.joinToString("\n"))
                row.createCell(3).setCellValue(wHours)
                row.createCell(4).setCellValue(oHours)
                row.createCell(5).setCellValue(getExtraEvents(student))

                // Event Columns (now starting at column 6)
                val studentHoursMap = perStudentEventHours[roll] ?: emptyMap()
                
                sheet1Events.forEachIndexed { evtIndex, event ->
                    val cell = row.createCell(6 + evtIndex)
                    val hours = studentHoursMap[event.id]
                    
                    if (hours != null) {
                        val claim = getClaimResult(student.wings, event)
                        if (claim.source == ClaimSource.WING_SHEET) {
                           // Claimed in wing sheet -> Show reference text
                           cell.setCellValue("(Hours given in ${claim.wingName})")
                        } else {
                            // Claimed here
                            cell.setCellValue(hours)
                        }
                    } else {
                        // Absent / No record
                         cell.setCellValue("")
                    }
                }
            }

            // ==================== WING SHEETS generation ====================
            SPECIFIC_WINGS.forEach { wingName ->
                // Create strict name for sheet (Excel limit 31 chars)
                val sheetName = if (wingName.length > 31) wingName.take(31) else wingName
                val sheet = workbook.createSheet(sheetName)
                
                // Filter Events: Conducted by this wing, excluding Open events
                val wingEvents = events.filter { 
                    it.wings.contains(wingName) && it.getDisplayWings() != "Open Event"
                }.sortedBy { it.getEventDateAsDate() }
                
                // Filter Students: Belong to this wing
                val wingStudents = students.filter { it.wings.contains(wingName) }.sortedBy { it.name }

                // Headers
                val wHeaderRow = sheet.createRow(0)
                val wHeaders = mutableListOf("Name", "Roll", "Wing Hours")
                wHeaders.addAll(wingEvents.map { it.getEventName() })
                
                wHeaders.forEachIndexed { idx, title ->
                    val cell = wHeaderRow.createCell(idx)
                    cell.setCellValue(title)
                    cell.cellStyle = headerStyle
                }

                wingStudents.forEachIndexed { sIdx, student ->
                    val row = sheet.createRow(sIdx + 1)
                    val roll = student.rollNumber.ifEmpty { student.id }
                    
                    // Calculate hours claimed specifically in this wing
                    var specificWingTotal = 0.0
                    val studentHoursMap = perStudentEventHours[roll] ?: emptyMap()
                    
                    studentHoursMap.forEach { (eid, h) ->
                         val ev = events.find { it.id == eid }
                         if (ev != null) {
                             val res = getClaimResult(student.wings, ev)
                             if (res.source == ClaimSource.WING_SHEET && res.wingName == wingName) {
                                 specificWingTotal += h
                             }
                         }
                    }

                    row.createCell(0).setCellValue(student.name)
                    row.createCell(1).setCellValue(roll)
                    row.createCell(2).setCellValue(specificWingTotal)
                    
                    // Event Columns
                    wingEvents.forEachIndexed { eIdx, event ->
                        val cell = row.createCell(3 + eIdx)
                        val hours = studentHoursMap[event.id]
                        
                        if (hours != null) {
                            val claim = getClaimResult(student.wings, event)
                            if (claim.source == ClaimSource.WING_SHEET && claim.wingName == wingName) {
                                // Claimed here
                                cell.setCellValue(hours)
                            } else if (claim.source == ClaimSource.WING_SHEET && claim.wingName != wingName) {
                                // Claimed in OTHER wing
                                cell.setCellValue("(Hours given in ${claim.wingName})")
                            } else {
                                // Claimed in Open Sheet
                                cell.setCellValue("(Hours given in Open/DNC)")
                            }
                        } else {
                             cell.setCellValue("")
                        }
                    }
                }
            }

            // Write to file
            val fos = FileOutputStream(excelFile)
            workbook.write(fos)
            fos.close()
            workbook.close()

            Log.d(TAG, "Excel file generated: ${excelFile.absolutePath}")
            return@withContext excelFile.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Error generating attendance matrix Excel", e)
            return@withContext null
        }
    }
    
    // ==================== Other Methods ====================
    
    suspend fun generateStudentEventsList(
        studentName: String,
        rollNumber: String,
        semester: Int,
        rows: List<Triple<String, String, Double>>
    ): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating student events list CSV for $studentName")
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "NSS_S${semester}_${rollNumber}_Events_${timestamp}.csv"
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val excelDir = File(downloadsDir, "NSS_Reports")
            if (!excelDir.exists()) excelDir.mkdirs()
            val excelFile = File(excelDir, filename)
            
            val writer = FileWriter(excelFile)
            writer.write("Semester $semester Events Summary\n\n")
            writer.write("Name:,$studentName\nRoll Number:,$rollNumber\nSemester:,$semester\n")
            writer.write("Total Hours:,${rows.sumOf { it.third }}\nEvents Attended:,${rows.size}\n\n")
            writer.write("Event,Date,Hours\n")
            rows.forEach { (name, date, hours) ->
                writer.write("\"$name\",\"$date\",$hours\n")
            }
            writer.close()
            return@withContext excelFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
            null
        }
    }

    suspend fun generateAttendanceReport(
        event: AttendanceEvent,
        attendees: List<AttendeeRecord>
    ): String? = withContext(Dispatchers.IO) {
         try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "NSS_Attendance_${event.getEventName().replace(" ", "_")}_$timestamp.csv"
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val excelDir = File(downloadsDir, "NSS_Reports")
            if (!excelDir.exists()) excelDir.mkdirs()
            val excelFile = File(excelDir, filename)

            val writer = FileWriter(excelFile)
            writer.write("NSS Attendance Report\n\n")
            writer.write("Event:,${event.getEventName()}\nDate:,${event.getFormattedEventDate()}\n")
            writer.write("Hours:,${event.hours}\n\n")
            
            if (attendees.isNotEmpty()) {
                 writer.write("NSS Group,Name,Roll Number\n")
                 val sorted = attendees.sortedBy { it.name }
                 sorted.forEach { attendee ->
                     val group = "Unknown"
                     writer.write("\"$group\",\"${attendee.name}\",\"${attendee.rollNumber}\"\n")
                 }
            } else {
                writer.write("No attendees.\n")
            }
            writer.close()
            return@withContext excelFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
            null
        }
    }
}