package com.phad.chatapp.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.phad.chatapp.features.scheduling.models.SubjectAssignmentDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScheduleExcelGenerator {
    private const val TAG = "ScheduleExcelGenerator"

    suspend fun generateAndShareExcel(
        context: Context,
        assignments: List<SubjectAssignmentDetails>,
        selectedSchool: String?
    ): String = withContext(Dispatchers.IO) {
        try {
            // Group assignments by school and section
            val groupedAssignments = assignments.groupBy { "${it.schoolName} - ${it.classAndSection}" }
            
            // Create a custom comparator for alphanumeric sorting (reused logic)
            val alphanumericComparator = Comparator<String> { a, b ->
                val aParts = a.split(" - ", limit = 2)
                val bParts = b.split(" - ", limit = 2)
                
                val schoolA = aParts[0]
                val schoolB = bParts[0]
                val schoolCompare = schoolA.compareTo(schoolB)
                
                if (schoolCompare != 0) return@Comparator schoolCompare
                
                val sectionA = if (aParts.size > 1) aParts[1] else ""
                val sectionB = if (bParts.size > 1) bParts[1] else ""
                
                val numA = sectionA.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                val numB = sectionB.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                
                val numCompare = numA.compareTo(numB)
                if (numCompare != 0) return@Comparator numCompare
                
                val suffixA = sectionA.dropWhile { it.isDigit() }
                val suffixB = sectionB.dropWhile { it.isDigit() }
                suffixA.compareTo(suffixB)
            }
            
            val sortedEntries = groupedAssignments.entries
                .sortedWith(compareBy<Map.Entry<String, List<SubjectAssignmentDetails>>, String>(alphanumericComparator) { it.key })

            val workbook = XSSFWorkbook()
            
            // Styles
            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                borderBottom = BorderStyle.THIN
                borderTop = BorderStyle.THIN
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
                setFont(workbook.createFont().apply {
                    bold = true
                    fontName = "Arial"
                    fontHeightInPoints = 12.toShort()
                })
            }
            
            val dayStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.LEMON_CHIFFON.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                borderBottom = BorderStyle.THIN
                borderTop = BorderStyle.THIN
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
                setFont(workbook.createFont().apply {
                    bold = true
                    fontName = "Arial"
                })
            }
            
            val cellStyle = workbook.createCellStyle().apply {
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                wrapText = true
                borderBottom = BorderStyle.THIN
                borderTop = BorderStyle.THIN
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
                setFont(workbook.createFont().apply {
                    fontName = "Arial"
                    fontHeightInPoints = 10.toShort()
                })
            }

            // Create a sheet for the schedule
            // Note: Sheet names must be unique and not too long.
            val sheetName = if (selectedSchool != null) "Schedule - $selectedSchool" else "Teaching Schedule"
            // Sanitize sheet name
            val safeSheetName = sheetName.replace(Regex("[\\\\/*?\\[\\]]"), "_").take(31)
            val sheet = workbook.createSheet(safeSheetName)

            var rowIndex = 0

            for ((displayName, sectionAssignments) in sortedEntries) {
                // Section Header
                val sectionRow = sheet.createRow(rowIndex++)
                val sectionCell = sectionRow.createCell(0)
                sectionCell.setCellValue(displayName)
                sectionCell.cellStyle = headerStyle
                
                // Determine days and slots
                val unsortedDayNames = sectionAssignments.map { it.dayName }.distinct()
                val slotNames = sectionAssignments.map { it.slotName }.distinct().sorted()
                
                val standardDayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                val dayNames = unsortedDayNames.sortedWith { day1, day2 ->
                    val index1 = standardDayOrder.indexOf(day1)
                    val index2 = standardDayOrder.indexOf(day2)
                    when {
                        index1 >= 0 && index2 >= 0 -> index1.compareTo(index2)
                        index1 >= 0 -> -1
                        index2 >= 0 -> 1
                        else -> day1.compareTo(day2)
                    }
                }

                // Table Header Row (Day/Time + Slots)
                val headerRow = sheet.createRow(rowIndex++)
                val dayTimeCell = headerRow.createCell(0)
                dayTimeCell.setCellValue("Day/Time")
                dayTimeCell.cellStyle = headerStyle

                slotNames.forEachIndexed { index, slot ->
                    val cell = headerRow.createCell(index + 1)
                    cell.setCellValue(slot)
                    cell.cellStyle = headerStyle
                    sheet.setColumnWidth(index + 1, 20 * 256) // ~20 chars width
                }
                sheet.setColumnWidth(0, 15 * 256) // Day column width

                // Data Rows
                for (dayName in dayNames) {
                    val row = sheet.createRow(rowIndex++)
                    val dayCell = row.createCell(0)
                    dayCell.setCellValue(dayName)
                    dayCell.cellStyle = dayStyle
                    
                    for ((slotIndex, slotName) in slotNames.withIndex()) {
                        val assignment = sectionAssignments.find { 
                            it.dayName == dayName && it.slotName == slotName 
                        }
                        
                        val cell = row.createCell(slotIndex + 1)
                        cell.cellStyle = cellStyle
                        
                        if (assignment != null) {
                            val text = "${assignment.volunteerName}\n(${assignment.volunteerRollNo})\n${assignment.subjectName}"
                            cell.setCellValue(text)
                        } else {
                            cell.setCellValue("-")
                        }
                    }
                    // Estimate row height based on content? Default is usually fine with wrapText, 
                    // but we might want to ensure at least some height.
                    row.heightInPoints = 60f 
                }
                
                rowIndex++ // Spacer row between sections
            }

            // Save file
            val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
            val dateString = dateFormat.format(Date())
            val fileName = "Teaching_Schedule_$dateString.xlsx".replace(" ", "_")
            
            val cacheDir = context.cacheDir
            val exportDir = File(cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()
            
            val file = File(exportDir, fileName)
            FileOutputStream(file).use { workbook.write(it) }
            workbook.close()
            
            // Share
            withContext(Dispatchers.Main) {
                val uri = FileProvider.getUriForFile(
                    context,
                    context.applicationContext.packageName + ".fileprovider",
                    file
                )
                
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Schedule"))
                }
            }
            
            return@withContext "Excel generated and opened"

        } catch (e: Exception) {
            Log.e(TAG, "Error generating Excel", e)
            throw e
        }
    }
}
