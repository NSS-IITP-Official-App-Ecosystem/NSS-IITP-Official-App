package com.phad.chatapp.utils

import android.content.Context
import android.util.Log
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.font.PdfFont
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.models.AttendeeRecord
import com.phad.chatapp.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class StudentEventReportRow(
    val name: String, 
    val date: String, 
    val hours: Double, 
    val wings: String
)

/**
 * Utility class for generating PDF reports for NSS attendance events
 */
class PDFGenerator(private val context: Context) {
    
    companion object {
        private const val TAG = "PDFGenerator"
        private const val FONT_SIZE_HEADER = 16f
        private const val FONT_SIZE_TITLE = 14f
        private const val FONT_SIZE_NORMAL = 10f
        private const val FONT_SIZE_SMALL = 8f
    }
    
    /**
     * Generate a PDF for a student's attended events list for a semester
     */
    suspend fun generateStudentEventsList(
        studentName: String,
        rollNumber: String,
        semester: Int,
        wingEvents: List<StudentEventReportRow>,
        openEvents: List<StudentEventReportRow>,
        wingHours: Double,
        openHours: Double,
        eventsAttendedCount: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating student events list PDF for $studentName ($rollNumber), semester $semester")
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "NSS_S${semester}_${rollNumber}_Events_${timestamp}.pdf"

            // Use Downloads directory instead of cache
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val pdfDir = File(downloadsDir, "NSS_Reports")
            if (!pdfDir.exists()) {
                val created = pdfDir.mkdirs()
                if (!created) {
                    Log.e(TAG, "Failed to create directory: ${pdfDir.absolutePath}")
                    return@withContext null
                }
            }
            val pdfFile = File(pdfDir, filename)

            val pdfWriter = PdfWriter(FileOutputStream(pdfFile))
            val pdfDocument = PdfDocument(pdfWriter)
            val document = Document(pdfDocument)

            val font = PdfFontFactory.createFont()
            val boldFont = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD)

            // Title
            document.add(
                Paragraph("Semester $semester Events Summary")
                    .setFont(boldFont)
                    .setFontSize(FONT_SIZE_HEADER)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(16f)
            )

            // Student details
            val details = Table(2).setWidth(UnitValue.createPercentValue(100f)).setMarginBottom(16f)
            details.addCell(createCell("Name:", boldFont, true))
            details.addCell(createCell(studentName, font, false))
            details.addCell(createCell("Roll Number:", boldFont, true))
            details.addCell(createCell(rollNumber, font, false))
            details.addCell(createCell("Semester:", boldFont, true))
            details.addCell(createCell(semester.toString(), font, false))
            
            val totalHours = wingHours + openHours
            details.addCell(createCell("Total Hours:", boldFont, true))
            details.addCell(createCell(AttendanceEventUtils.formatHours(totalHours), font, false))
            details.addCell(createCell("Events Attended:", boldFont, true))
            details.addCell(createCell(eventsAttendedCount.toString(), font, false))
            document.add(details)

            // 1. Wing Events Section
            if (wingEvents.isNotEmpty()) {
                document.add(
                    Paragraph("Wing Events Hours = ${AttendanceEventUtils.formatHours(wingHours)}")
                        .setFont(boldFont)
                        .setFontSize(FONT_SIZE_TITLE)
                        .setMarginBottom(8f)
                )

                val wingTable = Table(4).setWidth(UnitValue.createPercentValue(100f)).setMarginBottom(16f)
                wingTable.addCell(createHeaderCell("Event", boldFont))
                wingTable.addCell(createHeaderCell("Date", boldFont))
                wingTable.addCell(createHeaderCell("Wing", boldFont))
                wingTable.addCell(createHeaderCell("Hours", boldFont))

                wingEvents.forEach { (name, date, hours, wings) ->
                    wingTable.addCell(createCell(name, font, false))
                    wingTable.addCell(createCell(date, font, false))
                    wingTable.addCell(createCell(wings, font, false))
                    wingTable.addCell(createCell(AttendanceEventUtils.formatHours(hours), font, false))
                }
                document.add(wingTable)
            }

            // 2. Open Events Section
            if (openEvents.isNotEmpty()) {
                document.add(
                    Paragraph("Open Event Hours = ${AttendanceEventUtils.formatHours(openHours)}")
                        .setFont(boldFont)
                        .setFontSize(FONT_SIZE_TITLE)
                        .setMarginBottom(8f)
                )

                val openTable = Table(4).setWidth(UnitValue.createPercentValue(100f)).setMarginBottom(16f)
                openTable.addCell(createHeaderCell("Event", boldFont))
                openTable.addCell(createHeaderCell("Date", boldFont))
                openTable.addCell(createHeaderCell("Wing", boldFont))
                openTable.addCell(createHeaderCell("Hours", boldFont))

                openEvents.forEach { (name, date, hours, wings) ->
                    openTable.addCell(createCell(name, font, false))
                    openTable.addCell(createCell(date, font, false))
                    openTable.addCell(createCell(wings, font, false))
                    openTable.addCell(createCell(AttendanceEventUtils.formatHours(hours), font, false))
                }
                document.add(openTable)
            }

            document.close()

            Log.d(TAG, "Student events list PDF generated: ${pdfFile.absolutePath}")
            return@withContext pdfFile.absolutePath
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for file access", e)
            return@withContext null
        } catch (e: java.io.IOException) {
            Log.e(TAG, "File I/O error during PDF generation", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error generating student events list PDF", e)
            return@withContext null
        }
    }

    /**
     * Generate an Attendance Matrix PDF with columns: Name, Roll, Wing, Total Hours, and all Event Names.
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
            val filename = "NSS_Attendance_Matrix_${timestamp}.pdf"

            // Use Downloads directory instead of cache
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val pdfDir = File(downloadsDir, "NSS_Reports")
            if (!pdfDir.exists()) {
                val created = pdfDir.mkdirs()
                if (!created) {
                    Log.e(TAG, "Failed to create directory: ${pdfDir.absolutePath}")
                    return@withContext null
                }
            }
            val pdfFile = File(pdfDir, filename)

            val pdfWriter = PdfWriter(FileOutputStream(pdfFile))
            val pdfDocument = PdfDocument(pdfWriter)
            val document = Document(pdfDocument)

            val font = PdfFontFactory.createFont()
            val boldFont = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD)

            // Title
            document.add(
                Paragraph("NSS Attendance Matrix")
                    .setFont(boldFont)
                    .setFontSize(FONT_SIZE_HEADER)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(16f)
            )

            // Subtitle: total events
            document.add(
                Paragraph("Events: ${events.size} | Students: ${students.size}")
                    .setFont(font)
                    .setFontSize(FONT_SIZE_NORMAL)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(12f)
            )

            // Build table: fixed first 4 columns + dynamic events
            val totalColumns = 4 + events.size
            val table = Table(totalColumns)
                .setWidth(UnitValue.createPercentValue(100f))

            // Header cells
            table.addCell(createHeaderCell("Name", boldFont))
            table.addCell(createHeaderCell("Roll", boldFont))
            table.addCell(createHeaderCell("Wing", boldFont))
            table.addCell(createHeaderCell("Total Hours", boldFont))

            events.forEach { event ->
                table.addCell(createHeaderCell(event.getEventName(), boldFont))
            }

            // Rows per student
            for (student in students) {
                val roll = student.rollNumber.ifEmpty { student.id }
                val wing = if (student.wings.isNotEmpty()) student.wings.joinToString(",") else ""
                val totalHours = totalHoursPerStudent[roll]?.toString() ?: "0"

                table.addCell(createCell(student.name, font, false))
                table.addCell(createCell(roll, font, false))
                table.addCell(createCell(wing, font, false))
                table.addCell(createCell(totalHours, font, false))

                val perEvent = perStudentEventHours[roll] ?: perStudentEventHours[student.rollNumber] ?: emptyMap()
                events.forEach { event ->
                    val hours = perEvent[event.id]
                    table.addCell(createCell(hours?.toString() ?: "", font, false))
                }
            }

            document.add(table)
            document.close()

            Log.d(TAG, "Attendance matrix PDF generated: ${pdfFile.absolutePath}")
            return@withContext pdfFile.absolutePath
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for file access", e)
            return@withContext null
        } catch (e: java.io.IOException) {
            Log.e(TAG, "File I/O error during PDF generation", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error generating attendance matrix PDF", e)
            return@withContext null
        }
    }

    /**
     * Generate a detailed PDF report for a single event with attendee list
     */
    suspend fun generateAttendanceReport(
        event: AttendanceEvent,
        attendees: List<AttendeeRecord>
    ): String? = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val safeEventName = event.getEventName().replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val filename = "NSS_Report_${safeEventName}_${timestamp}.pdf"

            // Use Downloads directory instead of cache
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val pdfDir = File(downloadsDir, "NSS_Reports")
            if (!pdfDir.exists()) {
                val created = pdfDir.mkdirs()
                if (!created) {
                    Log.e(TAG, "Failed to create directory: ${pdfDir.absolutePath}")
                    return@withContext null
                }
            }
            val pdfFile = File(pdfDir, filename)

            val pdfWriter = PdfWriter(FileOutputStream(pdfFile))
            val pdfDocument = PdfDocument(pdfWriter)
            val document = Document(pdfDocument)

            val font = PdfFontFactory.createFont()
            val boldFont = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD)

            // Title
            document.add(
                Paragraph("NSS Attendance Report")
                    .setFont(boldFont)
                    .setFontSize(FONT_SIZE_HEADER)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(16f)
            )

            addEventDetails(document, event, font, boldFont)
            addAttendeesTable(document, attendees, font, boldFont)
            addSummary(document, event, attendees, font, boldFont)

            document.close()
            Log.d(TAG, "Attendance report PDF generated: ${pdfFile.absolutePath}")
            return@withContext pdfFile.absolutePath
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for file access", e)
            return@withContext null
        } catch (e: java.io.IOException) {
            Log.e(TAG, "File I/O error during PDF generation", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error generating attendance report PDF", e)
            return@withContext null
        }
    }

    /**
     * Add event details to the PDF
     */
    private fun addEventDetails(
        document: Document,
        event: AttendanceEvent,
        font: PdfFont,
        boldFont: PdfFont
    ) {
        // Event name
        document.add(Paragraph("Event: ${event.getEventName()}")
            .setFont(boldFont)
            .setFontSize(FONT_SIZE_TITLE)
            .setMarginBottom(10f))
        
        // Event details in a table format
        val detailsTable = Table(2)
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(20f)
        
        // Event date
        detailsTable.addCell(createCell("Date:", boldFont, true))
        detailsTable.addCell(createCell(event.getFormattedEventDate(), font, false))
        
        // Event time
        detailsTable.addCell(createCell("Time:", boldFont, true))
        detailsTable.addCell(createCell(event.getFormattedTimeRange(), font, false))
        
        // Location
        detailsTable.addCell(createCell("Location:", boldFont, true))
        detailsTable.addCell(createCell(event.location.ifEmpty { "Not specified" }, font, false))
        
        // Hours
        detailsTable.addCell(createCell("Hours:", boldFont, true))
        detailsTable.addCell(createCell(AttendanceEventUtils.formatHours(event.hours), font, false))
        
        // Description
        if (event.description.isNotEmpty()) {
            detailsTable.addCell(createCell("Description:", boldFont, true))
            detailsTable.addCell(createCell(event.description, font, false))
        }
        
        document.add(detailsTable)
    }
    
    /**
     * Add attendees table sorted by Wing
     */
    private fun addAttendeesTable(
        document: Document,
        attendees: List<AttendeeRecord>,
        font: PdfFont,
        boldFont: PdfFont
    ) {
        if (attendees.isEmpty()) {
            document.add(Paragraph("No attendees recorded for this event.")
                .setFont(font)
                .setFontSize(FONT_SIZE_NORMAL)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20f))
            return
        }
        
        // Group attendees by Wing
        val groupedAttendees = runBlockingGroupBy(attendees)
        
        // Create table for attendees
        val attendeesTable = Table(3)
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(20f)
        
        // Add header
        // Add header
        attendeesTable.addCell(createHeaderCell("Wing", boldFont))
        attendeesTable.addCell(createHeaderCell("Name", boldFont))
        attendeesTable.addCell(createHeaderCell("Roll Number", boldFont))
        
        // Add attendees sorted by Wing
        groupedAttendees.forEach { (wing, groupAttendees) ->
            groupAttendees.forEach { attendee ->
                attendeesTable.addCell(createCell(wing, font, false))
                attendeesTable.addCell(createCell(attendee.name, font, false))
                attendeesTable.addCell(createCell(attendee.rollNumber, font, false))
            }
        }
        
        document.add(attendeesTable)
    }
    
    /**
     * Add summary section
     */
    private fun addSummary(
        document: Document,
        event: AttendanceEvent,
        attendees: List<AttendeeRecord>,
        font: PdfFont,
        boldFont: PdfFont
    ) {
        val summaryTable = Table(2)
            .setWidth(UnitValue.createPercentValue(100f))
        
        // Total attendees
        summaryTable.addCell(createCell("Total Attendees:", boldFont, true))
        summaryTable.addCell(createCell(attendees.size.toString(), font, false))
        
        // NSS groups count - REMOVED as per request
        // val nssGroups = runBlockingGroupBy(attendees).keys.size
        // summaryTable.addCell(createCell("NSS Groups Represented:", boldFont, true))
        // summaryTable.addCell(createCell(nssGroups.toString(), font, false))
        
        // Report generation time
        val currentTime = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date())
        summaryTable.addCell(createCell("Report Generated:", boldFont, true))
        summaryTable.addCell(createCell(currentTime, font, false))
        
        document.add(summaryTable)
    }
    
    /**
     * Group attendees by their Wing
     */
    private fun runBlockingGroupBy(attendees: List<AttendeeRecord>): Map<String, List<AttendeeRecord>> {
        // Since we are already on Dispatchers.IO, block for simplicity inside PDF generation
        return kotlinx.coroutines.runBlocking {
            groupAttendeesByWing(attendees)
        }
    }

    private suspend fun groupAttendeesByWing(attendees: List<AttendeeRecord>): Map<String, List<AttendeeRecord>> {
        val groupedAttendees = mutableMapOf<String, MutableList<AttendeeRecord>>()
        
        for (attendee in attendees) {
            val wing = getStudentWing(attendee.rollNumber)
            if (!groupedAttendees.containsKey(wing)) {
                groupedAttendees[wing] = mutableListOf()
            }
            groupedAttendees[wing]?.add(attendee)
        }
        
        // Sort each group by name
        groupedAttendees.forEach { (_, groupAttendees) ->
            groupAttendees.sortBy { it.name }
        }
        
        // Sort groups alphabetically
        return groupedAttendees.toSortedMap()
    }
    
    /**
     * Get Wing for a student by their roll number
     */
    private suspend fun getStudentWing(rollNumber: String): String {
        return try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            // Modified to fetch from 'users' collection instead of 'Student'
            val studentDoc = db.collection("users").document(rollNumber).get().await()
            
            if (studentDoc.exists()) {
                val wings = studentDoc.get("wings")
                if (wings is List<*>) {
                   if (wings.isNotEmpty()) wings.joinToString(", ") else "None"
                } else {
                   "None"
                }
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Wing for student $rollNumber", e)
            "Unknown"
        }
    }
    
    /**
     * Create a table cell with improved color visibility
     */
    private fun createCell(text: String, font: PdfFont, isBold: Boolean): Cell {
        val cell = Cell().add(Paragraph(text).setFont(font).setFontSize(FONT_SIZE_NORMAL))
        if (isBold) {
            // Label cells: light blue background with dark text for better visibility
            cell.setBackgroundColor(com.itextpdf.kernel.colors.DeviceRgb(0.9f, 0.95f, 1.0f))
        } else {
            // Data cells: white background for clean appearance
            cell.setBackgroundColor(ColorConstants.WHITE)
        }
        return cell
    }
    
    /**
     * Create a header table cell with high contrast colors
     */
    private fun createHeaderCell(text: String, font: PdfFont): Cell {
        return Cell()
            .add(Paragraph(text).setFont(font).setFontSize(FONT_SIZE_NORMAL).setFontColor(ColorConstants.WHITE))
            .setBackgroundColor(com.itextpdf.kernel.colors.DeviceRgb(0.2f, 0.4f, 0.6f)) // Professional blue
            .setTextAlignment(TextAlignment.CENTER)
    }
}