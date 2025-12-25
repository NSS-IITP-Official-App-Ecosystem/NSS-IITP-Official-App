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

            db.collection("attendances")
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
            // First, get the current liveCount
            val eventSnap = db.collection("NSS_Events_Attendence")
                .document(event.id)
                .get()
                .await()
            val currentLiveCount = (eventSnap.get("liveCount") as? Number)?.toInt() ?: 1
            val newLiveCount = currentLiveCount + 1

            // Update isLive field to true, increment liveCount, and remove closedAt timestamp
            val updates = mapOf(
                "is_live" to true,
                "liveCount" to newLiveCount,
                "closedAt" to FieldValue.delete(), // Remove closedAt timestamp (using correct field name)
                "live" to FieldValue.delete() // Remove duplicate field if it exists
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
}