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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

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
        rows: List<Triple<String, String, Int>>
    ): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating student events list PDF for $studentName ($rollNumber), semester $semester")
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "NSS_S${semester}_${rollNumber}_Events_${timestamp}.pdf"

            val cacheDir = context.cacheDir
            val pdfDir = File(cacheDir, "images")
            if (!pdfDir.exists()) {
                pdfDir.mkdirs()
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
            val totalHours = rows.sumOf { it.third }
            details.addCell(createCell("Total Hours:", boldFont, true))
            details.addCell(createCell(totalHours.toString(), font, false))
            details.addCell(createCell("Events Attended:", boldFont, true))
            details.addCell(createCell(rows.size.toString(), font, false))
            document.add(details)

            // Table header
            val table = Table(3).setWidth(UnitValue.createPercentValue(100f))
            table.addCell(createHeaderCell("Event", boldFont))
            table.addCell(createHeaderCell("Date", boldFont))
            table.addCell(createHeaderCell("Hours", boldFont))

            // Rows
            rows.forEach { (name, date, hours) ->
                table.addCell(createCell(name, font, false))
                table.addCell(createCell(date, font, false))
                table.addCell(createCell(hours.toString(), font, false))
            }
            document.add(table)

            document.close()

            Log.d(TAG, "Student events list PDF generated: ${pdfFile.absolutePath}")
            return@withContext pdfFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error generating student events list PDF", e)
            return@withContext null
        }
    }

    /**
     * Generate PDF report for attendance event with attendees sorted by NSS group
     */
    suspend fun generateAttendanceReport(
        event: AttendanceEvent,
        attendees: List<AttendeeRecord>
    ): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating PDF report for event: ${event.getEventName()}")
            
            // Create filename with event name and timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "NSS_Attendance_${event.getEventName().replace(" ", "_")}_$timestamp.pdf"
            
            // Use cache directory for better accessibility via FileProvider
            val cacheDir = context.cacheDir
            val pdfDir = File(cacheDir, "images")
            if (!pdfDir.exists()) {
                pdfDir.mkdirs()
            }
            val pdfFile = File(pdfDir, filename)
            
            Log.d(TAG, "PDF directory: ${pdfDir.absolutePath}")
            Log.d(TAG, "PDF file path: ${pdfFile.absolutePath}")
            Log.d(TAG, "PDF directory exists: ${pdfDir.exists()}")
            Log.d(TAG, "PDF directory writable: ${pdfDir.canWrite()}")
            Log.d(TAG, "Android version: ${android.os.Build.VERSION.SDK_INT}")
            
            // Create PDF writer
            val pdfWriter = PdfWriter(FileOutputStream(pdfFile))
            val pdfDocument = PdfDocument(pdfWriter)
            val document = Document(pdfDocument)
            
            // Set up fonts
            val font = PdfFontFactory.createFont()
            val boldFont = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD)
            
            // Add title
            val title = Paragraph("NSS Attendance Report")
                .setFont(boldFont)
                .setFontSize(FONT_SIZE_HEADER)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20f)
            document.add(title)
            
            // Add event details
            addEventDetails(document, event, font, boldFont)
            
            // Add attendees table
            addAttendeesTable(document, attendees, font, boldFont)
            
            // Add summary
            addSummary(document, event, attendees, font, boldFont)
            
            // Close document
            document.close()
            
            // Log the file location for debugging
            Log.d(TAG, "PDF saved to cache directory: ${pdfFile.absolutePath}")
            
            Log.d(TAG, "PDF generated successfully: ${pdfFile.absolutePath}")
            Log.d(TAG, "File size: ${pdfFile.length()} bytes")
            Log.d(TAG, "File exists: ${pdfFile.exists()}")
            Log.d(TAG, "File readable: ${pdfFile.canRead()}")
            
            // Log the file location for debugging
            Log.d(TAG, "PDF saved to app internal storage: ${pdfFile.absolutePath}")
            
            return@withContext pdfFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating PDF report", e)
            return@withContext null
        }
    }
    
    /**
     * Add event details section to the PDF
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
        detailsTable.addCell(createCell(event.hours.toString(), font, false))
        
        // Description
        if (event.description.isNotEmpty()) {
            detailsTable.addCell(createCell("Description:", boldFont, true))
            detailsTable.addCell(createCell(event.description, font, false))
        }
        
        document.add(detailsTable)
    }
    
    /**
     * Add attendees table sorted by NSS group
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
        
        // Group attendees by NSS group
        val groupedAttendees = runBlockingGroupBy(attendees)
        
        // Create table for attendees
        val attendeesTable = Table(3)
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(20f)
        
        // Add header
        attendeesTable.addCell(createHeaderCell("NSS Group", boldFont))
        attendeesTable.addCell(createHeaderCell("Name", boldFont))
        attendeesTable.addCell(createHeaderCell("Roll Number", boldFont))
        
        // Add attendees sorted by NSS group
        groupedAttendees.forEach { (nssGroup, groupAttendees) ->
            groupAttendees.forEach { attendee ->
                attendeesTable.addCell(createCell(nssGroup, font, false))
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
        
        // NSS groups count
        val nssGroups = runBlockingGroupBy(attendees).keys.size
        summaryTable.addCell(createCell("NSS Groups Represented:", boldFont, true))
        summaryTable.addCell(createCell(nssGroups.toString(), font, false))
        
        // Report generation time
        val currentTime = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date())
        summaryTable.addCell(createCell("Report Generated:", boldFont, true))
        summaryTable.addCell(createCell(currentTime, font, false))
        
        document.add(summaryTable)
    }
    
    /**
     * Group attendees by their NSS group
     */
    private fun runBlockingGroupBy(attendees: List<AttendeeRecord>): Map<String, List<AttendeeRecord>> {
        // Since we are already on Dispatchers.IO, block for simplicity inside PDF generation
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
     * Create a table cell
     */
    private fun createCell(text: String, font: PdfFont, isBold: Boolean): Cell {
        val cell = Cell().add(Paragraph(text).setFont(font).setFontSize(FONT_SIZE_NORMAL))
        if (isBold) {
            cell.setBackgroundColor(ColorConstants.LIGHT_GRAY)
        }
        return cell
    }
    
    /**
     * Create a header table cell
     */
    private fun createHeaderCell(text: String, font: PdfFont): Cell {
        return Cell()
            .add(Paragraph(text).setFont(font).setFontSize(FONT_SIZE_NORMAL))
            .setBackgroundColor(ColorConstants.DARK_GRAY)
            .setTextAlignment(TextAlignment.CENTER)
    }
}