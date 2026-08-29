package com.phad.chatapp.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.FirebaseApp
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.models.User
import com.phad.chatapp.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class AttendanceViewModel(private val application: Application) : ViewModel() {
    private val db = Firebase.firestore
    private val TAG = "AttendanceViewModel"
    private val sessionManager = SessionManager(application)

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _liveEvents = MutableStateFlow<List<AttendanceEvent>>(emptyList())
    val liveEvents: StateFlow<List<AttendanceEvent>> = _liveEvents

    init {
        // Load current user when ViewModel is created
        viewModelScope.launch { 
            _currentUser.value = getCurrentUser() // Use the existing getCurrentUser function
        }
    }

    suspend fun getCurrentUser(): User? {
        return try {
            val rollNumber = sessionManager.fetchUserId()
            Log.d(TAG, "Fetching user data for roll number: $rollNumber")
            val doc = db.collection("users").document(rollNumber).get().await()
            if (!doc.exists()) {
                Log.e(TAG, "User document does not exist for roll number: $rollNumber")
                return null
            }
            val user = doc.toObject(User::class.java)
            if (user == null) {
                Log.e(TAG, "Failed to convert document to User object for roll number: $rollNumber")
            } else {
                Log.d(TAG, "Successfully fetched user: ${user.rollNumber}, Type: ${user.userType}")
            }
            // Do NOT set _currentUser.value here, it's done in the init block
            user
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user", e)
            null
        }
    }

    // Legacy methods (getLiveEvents, createAttendanceEvent, submitAttendance) removed during cleanup

    suspend fun closeEvent(event: AttendanceEvent) {
        try {
            // Update both isLive field and closedAt timestamp for consistency
            // Also remove any duplicate "live" field that might exist
            val updates = mapOf(
                "is_live" to false,
                "closedAt" to com.google.firebase.Timestamp.now(),
                "live" to FieldValue.delete() // Remove duplicate field if it exists
            )

            db.collection("NSS_Events_Attendence")
                .document(event.id)
                .update(updates)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing event", e)
            throw e
        }
    }

    suspend fun makeEventLive(event: AttendanceEvent) {
        try {
            // Use atomic increment to avoid race condition if two admins
            // tap "Make Live" simultaneously (previously was read-then-write).
            // NOTE: We do NOT reset 'penaltyEverApplied' here. That flag persists
            // across reopens so the per-student refund check in addAttendeeToEvent
            // still works correctly even after absentPenaltyApplied is reset to false.
            val updates = mapOf(
                "is_live" to true,
                "liveCount" to FieldValue.increment(1),  // atomic — race-condition-safe
                "closedAt" to FieldValue.delete(),
                "live" to FieldValue.delete(),
                "absentPenaltyApplied" to false
                // penaltyEverApplied intentionally NOT reset here
            )

            db.collection("NSS_Events_Attendence")
                .document(event.id)
                .update(updates)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error making event live", e)
            throw e
        }
    }

    suspend fun testDatabaseConnection(): String {
        return try {
            Log.d(TAG, "=== Testing database connection ===")
            
            // Show Firebase project info
            val app = FirebaseApp.getInstance()
            Log.d(TAG, "Firebase app name: ${app.name}")
            Log.d(TAG, "Firebase app options project ID: ${app.options.projectId}")
            
            // Test different possible collection names
            val possibleCollections = listOf(
                "NSS_Events_Attendence",  // Repository uses this (with typo)
                "NSS_Events_Attendance",  // Database screenshot shows this
                "attendances",
                "events",
                "NSS_Events"
            )
            
            for (collectionName in possibleCollections) {
                try {
                    val testQuery = db.collection(collectionName).limit(1).get().await()
                    Log.d(TAG, "Collection '$collectionName': Found ${testQuery.documents.size} documents")
                    if (testQuery.documents.isNotEmpty()) {
                        val doc = testQuery.documents[0]
                        Log.d(TAG, "Sample document from '$collectionName': ${doc.id}")
                        Log.d(TAG, "Sample document data keys: ${doc.data?.keys}")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Collection '$collectionName': Error - ${e.message}")
                }
            }
            
            // Test basic connection with the main collection (try repository's collection first)
            val testQuery = db.collection("NSS_Events_Attendence").limit(1).get().await()
            Log.d(TAG, "Database connection successful")
            Log.d(TAG, "Collection exists and accessible")
            Log.d(TAG, "Found ${testQuery.documents.size} documents (limited to 1)")
            
            if (testQuery.documents.isNotEmpty()) {
                val doc = testQuery.documents[0]
                Log.d(TAG, "Sample document ID: ${doc.id}")
                Log.d(TAG, "Sample document data keys: ${doc.data?.keys}")
            }
            
            "Connected to project: ${app.options.projectId}. Found ${testQuery.documents.size} documents."
        } catch (e: Exception) {
            Log.e(TAG, "Database connection failed", e)
            "Database connection failed: ${e.message}"
        }
    }

    suspend fun getClosedEvents(): List<AttendanceEvent> {
        return try {
            Log.d(TAG, "=== getClosedEvents() START ===")
            Log.d(TAG, "Querying collection: NSS_Events_Attendence")
            
            val allEventsQuery = db.collection("NSS_Events_Attendence")
                .get()
                .await()

            Log.d(TAG, "Total documents found: ${allEventsQuery.documents.size}")
            
            val closedEvents = mutableListOf<AttendanceEvent>()

            allEventsQuery.documents.forEach { doc ->
                Log.d(TAG, "Processing document: ${doc.id}")
                val event = doc.toObject(AttendanceEvent::class.java)?.copy(id = doc.id)
                event?.let {
                    val rawData = doc.data
                    Log.d(TAG, "Document ${doc.id} raw data keys: ${rawData?.keys}")
                    Log.d(TAG, "Document ${doc.id} has is_live field: ${rawData?.containsKey("is_live")}")
                    
                    val isEventLive = if (rawData?.containsKey("is_live") == true) {
                        val isLiveValue = rawData["is_live"] as? Boolean ?: true
                        Log.d(TAG, "Document ${doc.id} is_live value: $isLiveValue")
                        isLiveValue
                    } else {
                        Log.d(TAG, "Document ${doc.id} using fallback isLive: ${it.isLive}")
                        it.isLive
                    }

                    Log.d(TAG, "Document ${doc.id} final isEventLive: $isEventLive")
                    
                    if (!isEventLive) {
                        Log.d(TAG, "Adding closed event: ${doc.id}")
                        closedEvents.add(it)
                    } else {
                        Log.d(TAG, "Skipping live event: ${doc.id}")
                    }
                } ?: Log.w(TAG, "Failed to parse document: ${doc.id}")
            }

            Log.d(TAG, "Found ${closedEvents.size} closed events")
            closedEvents.forEach { event ->
                Log.d(TAG, "Closed event: ${event.id} - ${event.description}")
            }
            
              // Sort by date and time in descending order (newest first)
              val sortedEvents = closedEvents.sortedWith(compareByDescending<AttendanceEvent> { event ->
                  try {
                      // Parse date string (format: "dd MMM yyyy" like "05 Nov 2025")
                      val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH)
                      dateFormat.parse(event.eventDate)?.time ?: 0L
                  } catch (e: Exception) {
                      Log.w(TAG, "Failed to parse date: ${event.eventDate}", e)
                      0L
                  }
              }.thenByDescending { event ->
                  try {
                      // Parse time string (format: "hh:mm a" like "09:00 am")
                      val timeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH)
                      timeFormat.parse(event.eventTime)?.time ?: 0L
                  } catch (e: Exception) {
                      Log.w(TAG, "Failed to parse time: ${event.eventTime}", e)
                      0L
                  }
              })
              
              Log.d(TAG, "=== getClosedEvents() END - Returning ${sortedEvents.size} events ===")
              Log.d(TAG, "Events sorted by date and time (newest first):")
              sortedEvents.forEachIndexed { index, event ->
                  Log.d(TAG, "${index + 1}. ${event.eventDate} ${event.eventTime} - ${event.description}")
              }
              sortedEvents
        } catch (e: Exception) {
            Log.e(TAG, "Error getting closed events", e)
            Log.e(TAG, "Exception details: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Clean up duplicate "live" fields from all events in the database
     * This should be called once to fix existing events that have both "is_live" and "live" fields
     */
    suspend fun cleanupDuplicateLiveFields() {
        try {
            Log.d(TAG, "Starting cleanup of duplicate 'live' fields...")

            val allEventsQuery = db.collection("attendances")
                .get()
                .await()

            var cleanedCount = 0
            allEventsQuery.documents.forEach { doc ->
                val data = doc.data
                if (data != null && data.containsKey("live")) {
                    // Remove the duplicate "live" field
                    val updates = mapOf(
                        "live" to FieldValue.delete()
                    )

                    db.collection("attendances")
                        .document(doc.id)
                        .update(updates)
                        .await()

                    cleanedCount++
                    Log.d(TAG, "Removed duplicate 'live' field from event: ${doc.id}")
                }
            }

            Log.d(TAG, "Cleanup completed. Cleaned $cleanedCount events.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup of duplicate live fields", e)
            throw e
        }
    }

    private fun getCurrentUserId(): String {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.e(TAG, "No user is currently signed in")
            throw IllegalStateException("No user is currently signed in")
        }
        return currentUser.uid
    }

    suspend fun applyAbsentPenalty(
        eventId: String,
        positiveRollNumbers: List<String>? = null,
        negativeRollNumbers: List<String>? = null,
        zeroRollNumbers: List<String>? = null
    ): Result<String> {
        return try {
            val auth = FirebaseAuth.getInstance()
            val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
                ?: return Result.failure(Exception("Not authenticated"))

            val projId = try {
                com.google.firebase.FirebaseApp.getInstance().options.projectId
            } catch (e: Exception) { null } ?: "nssiitp-app"
            // Use production Cloud Functions URL
            val url = "https://asia-south1-$projId.cloudfunctions.net/applyAbsentPenalty"
            
            withContext(Dispatchers.IO) {
                val client = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                client.requestMethod = "POST"
                client.setRequestProperty("Content-Type", "application/json")
                client.setRequestProperty("Authorization", "Bearer $idToken")
                client.doOutput = true

                // Build JSON request manually to avoid external serialization dependencies
                val bodyBuilder = java.lang.StringBuilder()
                bodyBuilder.append("{")
                bodyBuilder.append("\"eventId\":\"$eventId\"")
                
                if (positiveRollNumbers != null) {
                    val posArray = positiveRollNumbers.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }
                    bodyBuilder.append(",\"positiveRollNumbers\":$posArray")
                }
                if (negativeRollNumbers != null) {
                    val negArray = negativeRollNumbers.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }
                    bodyBuilder.append(",\"negativeRollNumbers\":$negArray")
                }
                if (zeroRollNumbers != null) {
                    val zeroArray = zeroRollNumbers.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }
                    bodyBuilder.append(",\"zeroRollNumbers\":$zeroArray")
                }
                bodyBuilder.append("}")

                val body = bodyBuilder.toString()
                client.outputStream.write(body.toByteArray())

                val responseCode = client.responseCode
                val response = if (responseCode == 200) {
                    client.inputStream.bufferedReader().readText()
                } else {
                    client.errorStream?.bufferedReader()?.readText() ?: "Error code: $responseCode"
                }
                
                Log.d(TAG, "applyAbsentPenalty response ($responseCode): $response")
                
                if (responseCode == 200) {
                    if (response.contains("\"ok\":true") || response.contains("\"ok\": true")) {
                        Result.success("Penalty settings applied successfully!")
                    } else {
                        // Extract reason if present
                        val reasonRegex = "\"reason\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                        val match = reasonRegex.find(response)
                        val reason = match?.groupValues?.get(1) ?: "Unknown error from server"
                        Result.failure(Exception(reason))
                    }
                } else {
                    Result.failure(Exception("Failed: $response"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying penalty", e)
            Result.failure(e)
        }
    }

    suspend fun getVolunteersForPenalty(eventId: String): List<VolunteerPenaltyState> {
        return getVolunteersForPenaltyWithError(eventId).getOrDefault(emptyList())
    }

    suspend fun getVolunteersForPenaltyWithError(eventId: String): Result<List<VolunteerPenaltyState>> {
        return try {
            Log.d(TAG, "=== getVolunteersForPenaltyWithError() START for event: $eventId ===")

            val eventDoc = db.collection("NSS_Events_Attendence").document(eventId).get().await()
            if (!eventDoc.exists()) {
                return Result.failure(Exception("Event not found: $eventId"))
            }

            val eventWings = (eventDoc.get("wings") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            Log.d(TAG, "Event Wings: $eventWings")

            // 1. Collect all roll numbers who submitted attendance by ANY method:
            // a) From subcollection 'attendance'
            val attendanceSnap = db.collection("NSS_Events_Attendence").document(eventId)
                .collection("attendance").get().await()
            val subcollectionRolls = attendanceSnap.documents.map { it.id.uppercase() }.toSet()

            // b) From embedded 'attendees' list on event doc
            @Suppress("UNCHECKED_CAST")
            val embeddedAttendees = (eventDoc.get("attendees") as? List<Map<String, Any>>) ?: emptyList()
            val embeddedRolls = embeddedAttendees.mapNotNull { 
                (it["rollNumber"] as? String ?: it["roll_number"] as? String)?.uppercase() 
            }.toSet()

            // c) From PhotoAttendanceLog for this event (photo submissions)
            val photoLogSnap = try {
                db.collection("PhotoAttendanceLog")
                    .whereEqualTo("eventId", eventId)
                    .get().await()
            } catch (e: Exception) {
                null
            }
            val photoLogRolls = photoLogSnap?.documents?.mapNotNull { 
                (it.getString("rollNumber") ?: it.getString("roll_number"))?.uppercase() 
            }?.toSet() ?: emptySet()

            val allAttendedOrSubmittedRolls = subcollectionRolls + embeddedRolls + photoLogRolls
            Log.d(TAG, "Total students who submitted attendance through any way: ${allAttendedOrSubmittedRolls.size}")

            // 2. Fetch users matching wing criteria
            val usersSnap = if (eventWings.isNotEmpty()) {
                db.collection("users").whereArrayContainsAny("wings", eventWings).get().await()
            } else {
                db.collection("users").whereIn("userType", listOf("student", "Student")).get().await()
            }
            Log.d(TAG, "Raw users fetched: ${usersSnap.documents.size}")

            // 3. Extract previously exempted/zero-penalty roll numbers from previous rounds
            @Suppress("UNCHECKED_CAST")
            val previouslyExempted = ((eventDoc.get("exemptedRollNumbers") as? List<*>)?.filterIsInstance<String>() ?: emptyList())
                .plus((eventDoc.get("zeroPenaltyRollNumbers") as? List<*>)?.filterIsInstance<String>() ?: emptyList())
                .map { it.uppercase() }
                .toSet()

            // 4. Include ONLY users who have NOT submitted attendance through any way (truly absent)
            val volunteers = usersSnap.documents.mapNotNull { doc ->
                val userType = doc.getString("userType") ?: "student"
                if (userType.equals("Admin", ignoreCase = true)) return@mapNotNull null

                val rollNumber = doc.id.uppercase()
                val name = doc.getString("name") ?: "Unknown"
                val userWings = (doc.get("wings") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                if (eventWings.isNotEmpty() && userWings.none { it in eventWings }) return@mapNotNull null

                // Filter out anyone who has submitted attendance through ANY method
                if (allAttendedOrSubmittedRolls.contains(rollNumber)) {
                    Log.d(TAG, "Excluding $rollNumber ($name) from penalty dialog — attendance already submitted")
                    return@mapNotNull null
                }

                // Truly absent volunteer with memory of previous round exemptions
                val initialSelection = if (rollNumber in previouslyExempted) {
                    PenaltySelection.ZERO
                } else {
                    PenaltySelection.NEGATIVE
                }

                VolunteerPenaltyState(
                    rollNumber = rollNumber,
                    name = name,
                    isAbsent = true,
                    selection = initialSelection
                )
            }.sortedBy { it.rollNumber }

            Log.d(TAG, "Returning ${volunteers.size} absent volunteers for penalty dialog")
            Result.success(volunteers)
        } catch (e: Exception) {
            Log.e(TAG, "getVolunteersForPenaltyWithError FAILED: ${e.message}", e)
            Result.failure(e)
        }
    }
}

enum class PenaltySelection { POSITIVE, ZERO, NEGATIVE }

data class VolunteerPenaltyState(
    val rollNumber: String,
    val name: String,
    val isAbsent: Boolean,
    val selection: PenaltySelection
)
