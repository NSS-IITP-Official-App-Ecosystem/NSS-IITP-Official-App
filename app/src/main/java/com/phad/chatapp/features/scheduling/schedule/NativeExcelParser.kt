package com.phad.chatapp.features.scheduling.schedule

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * A lightweight XLSX parser that uses standard Android APIs (ZipInputStream, XmlPullParser).
 * Bypasses Apache POI to avoid XML compatibility issues on Android.
 */
typealias SheetData = Map<Int, Map<Int, String>>

object NativeExcelParser {
    private const val TAG = "NativeExcelParser"

    private data class RawCell(val value: String, val type: String)

    fun parse(inputStream: InputStream): SheetData {
        Log.d(TAG, "Starting native parse (Buffered Strategy)")
        val zipInputStream = ZipInputStream(inputStream)
        
        var sharedStrings: List<String> = emptyList()
        var rawSheetData: Map<Int, Map<Int, RawCell>> = emptyMap()

        try {
            var entry: ZipEntry? = zipInputStream.nextEntry
            while (entry != null) {
                val name = entry.name
                // Log.d(TAG, "Found zip entry: $name")

                if (name == "xl/sharedStrings.xml") {
                    Log.d(TAG, "Found sharedStrings. Reading into memory...")
                    val bytes = zipInputStream.readBytes() // Reads *current entry* until EOF (-1)
                    Log.d(TAG, "Read ${bytes.size} bytes for sharedStrings")
                    sharedStrings = parseSharedStrings(ByteArrayInputStream(bytes))
                    Log.d(TAG, "Loaded ${sharedStrings.size} shared strings")
                    
                } else if (name == "xl/worksheets/sheet1.xml") {
                    Log.d(TAG, "Found sheet1. Reading into memory...")
                    val bytes = zipInputStream.readBytes()
                    Log.d(TAG, "Read ${bytes.size} bytes for sheet1")
                    rawSheetData = parseSheetRaw(ByteArrayInputStream(bytes))
                    Log.d(TAG, "Parsed raw sheet with ${rawSheetData.size} rows")
                }

                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in native parse", e)
        } finally {
            inputStream.close()
        }
        
        Log.d(TAG, "Zip parse complete. Resolving data...")
        return resolveData(rawSheetData, sharedStrings)
    }

    private fun resolveData(
        rawData: Map<Int, Map<Int, RawCell>>, 
        sharedStrings: List<String>
    ): SheetData {
        val resolved = mutableMapOf<Int, MutableMap<Int, String>>()
        
        rawData.forEach { (rowIndex, rowCells) ->
            val resolvedRow = mutableMapOf<Int, String>()
            rowCells.forEach { (colIndex, rawCell) ->
                var finalVal = rawCell.value
                if (rawCell.type == "s") {
                    val idx = rawCell.value.toIntOrNull()
                    if (idx != null && idx >= 0 && idx < sharedStrings.size) {
                        finalVal = sharedStrings[idx]
                    } else {
                        // Log.w(TAG, "Unresolved shared string index: $idx (Total: ${sharedStrings.size})")
                        finalVal = "" 
                    }
                }
                if (finalVal.isNotEmpty()) {
                    resolvedRow[colIndex] = finalVal
                }
            }
            if (resolvedRow.isNotEmpty()) {
                resolved[rowIndex] = resolvedRow
            }
        }
        return resolved
    }

    private fun parseSharedStrings(inputStream: InputStream): List<String> {
        val strings = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var currentText = StringBuilder()
        var isToString = false 

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "t") {
                        isToString = true
                        currentText.setLength(0) 
                    }
                }
                XmlPullParser.TEXT -> {
                    if (isToString) {
                        currentText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "t") {
                        isToString = false
                    } else if (parser.name == "si") {
                        strings.add(currentText.toString())
                        currentText.setLength(0) 
                    }
                }
            }
            eventType = parser.next()
        }
        return strings
    }

    private fun parseSheetRaw(inputStream: InputStream): Map<Int, Map<Int, RawCell>> {
        val data = mutableMapOf<Int, MutableMap<Int, RawCell>>()
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        
        var currentRowIndex = 0
        var currentColIndex = 0
        var cellType = "" 
        var cellValue = ""
        var isValue = false 
        
        // Debugging Counters
        // var tagCount = 0

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    // tagCount++
                    // if (tagCount < 20) Log.d(TAG, "Tag: ${parser.name}")
                    
                    val name = parser.name
                    if (name == "row") {
                        val r = parser.getAttributeValue(null, "r")
                        currentRowIndex = r?.toIntOrNull()?.minus(1) ?: currentRowIndex + 1 
                        currentColIndex = -1 // Reset column counter for new row
                    } else if (name == "c") {
                        val r = parser.getAttributeValue(null, "r") 
                        val t = parser.getAttributeValue(null, "t") 
                        
                        if (r != null) {
                            currentColIndex = getColIndex(r)
                        } else {
                            currentColIndex++ 
                        }
                        
                        cellType = t ?: ""
                        cellValue = ""
                    } else if (name == "v") {
                        isValue = true
                        cellValue = ""
                    } else if (name == "t" && cellType == "inlineStr") {
                        isValue = true 
                        cellValue = ""
                    }
                }
                XmlPullParser.TEXT -> {
                    if (isValue) {
                        cellValue += parser.text
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    if (name == "v" || (name == "t" && cellType == "inlineStr")) {
                        isValue = false
                    } else if (name == "c") {
                        if (cellValue.isNotEmpty()) {
                            if (!data.containsKey(currentRowIndex)) {
                                data[currentRowIndex] = mutableMapOf()
                            }
                            data[currentRowIndex]!![currentColIndex] = RawCell(cellValue, cellType)
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return data
    }
    
    // Convert "A1", "AB12" to 0-based column index
    private fun getColIndex(ref: String): Int {
        var colStr = ""
        for (char in ref) {
            if (char.isLetter()) {
                colStr += char
            } else {
                break
            }
        }
        return colNameToIndex(colStr)
    }

    private fun colNameToIndex(col: String): Int {
        var index = 0
        var mul = 1
        for (i in col.length - 1 downTo 0) {
            val c = col[i]
            index += (c.uppercaseChar() - 'A' + 1) * mul
            mul *= 26
        }
        return index - 1
    }
}
