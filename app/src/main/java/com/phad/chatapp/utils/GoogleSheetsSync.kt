package com.phad.chatapp.utils

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Utility class to sync dynamic attendance matrix data to a Google Sheet
 * via Google Apps Script Web App
 */
object GoogleSheetsSync {
    private const val TAG = "GoogleSheetsSync"

    private enum class ClaimSource {
        OPEN_SHEET,
        WING_SHEET
    }

    private data class ClaimResult(
        val source: ClaimSource,
        val wingName: String? = null
    )

    /**
     * Compile and sync the entire attendance matrix to the configured Google Sheet URL
     */
    suspend fun sync(
        context: Context,
        students: List<User>,
        events: List<AttendanceEvent>,
        perStudentEventHours: Map<String, Map<String, Double>>, // roll -> (eventId -> hours)
        totalHoursPerStudent: Map<String, Double>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching Google Sheets Web App URL from Firestore...")
            val db = FirebaseFirestore.getInstance()
            val settingsSnap = db.collection("meta").document("settings").get().await()
            val syncUrl = settingsSnap.getString("googleSheetsSyncUrl")
            
            if (syncUrl.isNullOrBlank()) {
                Log.w(TAG, "googleSheetsSyncUrl not found in meta/settings")
                return@withContext Result.failure(
                    Exception("Google Sheets Sync URL is not configured.\n\nPlease save your Web App URL in Firestore: meta/settings -> 'googleSheetsSyncUrl'.")
                )
            }

            Log.d(TAG, "Compiling sheet data...")
            val root = JSONObject()
            val sheetsObj = JSONObject()

            // 1. Filter events for Sheet 1 (Open + DNC)
            val sheet1Events = events.filter { event ->
                event.getDisplayWings() == "Open Event" || event.wings.contains("Design and Curation Wing")
            }.sortedBy { it.getEventDateAsDate().time }

            // Helper function to check claim source (mirroring ExcelGenerator)
            fun getClaimResult(studentWings: List<String>, event: AttendanceEvent): ClaimResult {
                if (event.getDisplayWings() == "Open Event") {
                    return ClaimResult(ClaimSource.OPEN_SHEET)
                }
                
                val relevantEventWings = event.wings.filter { it != "Design and Curation Wing" }
                val intersection = studentWings.intersect(relevantEventWings.toSet()).sorted()
                
                return if (intersection.isNotEmpty()) {
                    ClaimResult(ClaimSource.WING_SHEET, intersection.first())
                } else {
                    ClaimResult(ClaimSource.OPEN_SHEET)
                }
            }

            // Helper to calculate wing vs open hours split
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

            // Helper to get extra events summary
            fun getExtraEvents(student: User): String {
                val roll = student.rollNumber.ifEmpty { student.id }
                val attendedEvents = perStudentEventHours[roll] ?: return ""
                val extraEventsList = mutableListOf<String>()
                
                attendedEvents.forEach { (eventId, hours) ->
                    val event = events.find { it.id == eventId }
                    if (event != null) {
                        val eventWings = event.wings.filter { it != "Design and Curation Wing" }
                        val hasNoMatchingWing = student.wings.intersect(eventWings.toSet()).isEmpty()
                        
                        if (eventWings.isNotEmpty() && hasNoMatchingWing && 
                            event.getDisplayWings() != "Open Event" && 
                            !event.wings.contains("Design and Curation Wing")) {
                            extraEventsList.add("${event.getEventName()} ($hours)")
                        }
                    }
                }
                return extraEventsList.joinToString("\n")
            }

            // Generate Sheet 1 (Open & DNC Events)
            val sheet1Array = JSONArray()
            val headers1 = mutableListOf("Name", "Roll", "Wing", "Wing Hours", "Open Event Hours", "Extra Events")
            headers1.addAll(sheet1Events.map { it.getEventName() })
            
            val headerRow1 = JSONArray()
            headers1.forEach { headerRow1.put(it) }
            sheet1Array.put(headerRow1)

            val sortedStudents = students.sortedWith(
                compareBy(
                    { it.wings.firstOrNull() ?: "ZZZZ" },
                    { it.name }
                )
            )

            sortedStudents.forEach { student ->
                val row = JSONArray()
                val roll = student.rollNumber.ifEmpty { student.id }
                val (wHours, oHours) = calculateSplitHours(student)

                row.put(student.name)
                row.put(roll)
                row.put(student.wings.joinToString("\n"))
                row.put(wHours)
                row.put(oHours)
                row.put(getExtraEvents(student))

                val studentHoursMap = perStudentEventHours[roll] ?: emptyMap()
                sheet1Events.forEach { event ->
                    val hours = studentHoursMap[event.id]
                    if (hours != null) {
                        val claim = getClaimResult(student.wings, event)
                        if (claim.source == ClaimSource.WING_SHEET) {
                            row.put("(Hours given in ${claim.wingName})")
                        } else {
                            row.put(hours)
                        }
                    } else {
                        row.put("")
                    }
                }
                sheet1Array.put(row)
            }
            sheetsObj.put("Open & DNC Events", sheet1Array)

            // Generate Wing Sheets
            val specificWings = listOf(
                com.phad.chatapp.utils.Constants.WING_ENV,
                com.phad.chatapp.utils.Constants.WING_PRN,
                com.phad.chatapp.utils.Constants.WING_RDW,
                com.phad.chatapp.utils.Constants.WING_TTW
            )

            specificWings.forEach { wingName ->
                val wingEvents = events.filter { 
                    it.wings.contains(wingName) && it.getDisplayWings() != "Open Event"
                }.sortedBy { it.getEventDateAsDate().time }
                
                val wingStudents = students.filter { it.wings.contains(wingName) }.sortedBy { it.name }

                val wingArray = JSONArray()
                val wHeaders = mutableListOf("Name", "Roll", "Wing Hours")
                wHeaders.addAll(wingEvents.map { it.getEventName() })
                
                val wHeaderRow = JSONArray()
                wHeaders.forEach { wHeaderRow.put(it) }
                wingArray.put(wHeaderRow)

                wingStudents.forEach { student ->
                    val row = JSONArray()
                    val roll = student.rollNumber.ifEmpty { student.id }
                    
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

                    row.put(student.name)
                    row.put(roll)
                    row.put(specificWingTotal)

                    wingEvents.forEach { event ->
                        val hours = studentHoursMap[event.id]
                        if (hours != null) {
                            val claim = getClaimResult(student.wings, event)
                            if (claim.source == ClaimSource.WING_SHEET && claim.wingName == wingName) {
                                row.put(hours)
                            } else if (claim.source == ClaimSource.WING_SHEET && claim.wingName != wingName) {
                                row.put("(Hours given in ${claim.wingName})")
                            } else {
                                row.put("(Hours given in Open/DNC)")
                            }
                        } else {
                            row.put("")
                        }
                    }
                    wingArray.put(row)
                }
                
                val sheetName = if (wingName.length > 31) wingName.take(31) else wingName
                sheetsObj.put(sheetName, wingArray)
            }

            root.put("sheets", sheetsObj)

            Log.d(TAG, "Sending HTTP request to Google Apps Script...")
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = root.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(syncUrl)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d(TAG, "Response from Apps Script: $body")
                
                if (response.isSuccessful) {
                    val respJson = JSONObject(body)
                    if (respJson.optBoolean("success", false)) {
                        Log.d(TAG, "Sync to Google Sheets successful!")
                        Result.success(Unit)
                    } else {
                        val error = respJson.optString("error", "Unknown error from Google Sheet Script")
                        Result.failure(Exception(error))
                    }
                } else {
                    Result.failure(Exception("HTTP error ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Sheets sync", e)
            Result.failure(e)
        }
    }
}
