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
import com.phad.chatapp.models.AttendanceSubmission
import com.phad.chatapp.models.User
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.utils.NotificationHelper
import com.phad.chatapp.utils.ChatMessagingService
import com.phad.chatapp.utils.AttendanceUtils
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

    private val _pendingSubmissions = MutableStateFlow<List<AttendanceSubmission>>(emptyList())
    val pendingSubmissions: StateFlow<List<AttendanceSubmission>> = _pendingSubmissions

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

    suspend fun getLiveEvents(): List<AttendanceEvent> {
        return try {
            // Get all events first for backward compatibility with older events that don't have isLive field
            val allEventsQuery = db.collection("attendances")
                .get()
                .await()

            val allEvents = mutableListOf<AttendanceEvent>()

            // Process all events and filter based on isLive field (defaults to true for backward compatibility)
            allEventsQuery.documents.forEach { doc ->
                val event = doc.toObject(AttendanceEvent::class.java)?.copy(id = doc.id)
                event?.let {
                    // Check the raw document data to ensure we're reading the correct field
                    // Priority: "is_live" field > fallback to deserialized isLive property
                    val rawData = doc.data
                    val isEventLive = if (rawData?.containsKey("is_live") == true) {
                        rawData["is_live"] as? Boolean ?: true // Default to true for backward compatibility
                    } else {
                        it.isLive // Fallback to deserialized property
                    }

                    if (isEventLive) {
                        // Add live events
                        allEvents.add(it)
                    } else {
                        // For ended events, only include if they have pending submissions
                        val pendingSubmissions = db.collection("attendances")
                            .document(doc.id)
                            .collection("marked_attendance")
                            .whereEqualTo("approvalStatus", "Pending")
                            .get()
                            .await()

                        if (!pendingSubmissions.isEmpty) {
                            allEvents.add(it)
                        }
                    }
                }
            }

            allEvents
        } catch (e: Exception) {
            Log.e(TAG, "Error getting events", e)
            emptyList()
        }
    }

    suspend fun createAttendanceEvent(event: AttendanceEvent) {
        try {
            val eventId = event.id.ifEmpty { "${event.getEventName()}_${event.createdAt.seconds}" }
            // This method is for the legacy face recognition attendance system
            db.collection("attendances")
                .document(eventId)
                .set(event.copy(id = eventId))
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating attendance event", e)
            throw e
        }
    }

    suspend fun submitAttendance(submission: AttendanceSubmission, onSuccess: () -> Unit = {}) {
        try {
            // Get the current user's roll number from session manager
            val sessionRollNumber = sessionManager.fetchRollNumber()
            if (sessionRollNumber == null) {
                throw Exception("User session not found")
            }

            // Check if submitted roll number matches session roll number (case-insensitive)
            if (!submission.rollNumber.equals(sessionRollNumber, ignoreCase = true)) {
                throw Exception("Roll number mismatch. Please use your correct roll number: $sessionRollNumber")
            }

            // First get the event document to get its ID
            val eventQuery = db.collection("attendances")
                .get()
                .await()

            // Find the event by name and check if it's live
            val eventDoc = eventQuery.documents.find { doc ->
                val event = doc.toObject(AttendanceEvent::class.java)?.copy(id = doc.id)
                event?.getEventName() == submission.eventName && event?.getEventStatus() == AttendanceEvent.STATUS_LIVE
            }

            if (eventDoc == null) {
                throw Exception("Event not found or not live")
            }

            val eventId = eventDoc.id
            
            // Use the session roll number for the document ID to ensure consistency
            val submissionDocRef = db.collection("attendances")
                .document(eventId)
                .collection("marked_attendance")
                .document(sessionRollNumber)
            
            // Check if the submission document already exists
            val submissionDoc = submissionDocRef.get().await()
            val submissionExists = submissionDoc.exists()
            
            // Create a new submission with the correct roll number
            val correctedSubmission = submission.copy(
                rollNumber = sessionRollNumber,
                studentRollNumber = sessionRollNumber
            )
            
            // Add or update the submission document
            submissionDocRef.set(correctedSubmission).await()
            
            // Increment totalMarked only if it's a new submission (for legacy face recognition system)
            if (!submissionExists) {
                db.collection("attendances").document(eventId).update("totalMarked", com.google.firebase.firestore.FieldValue.increment(1))
                    .await()
            }

            // Call the success callback
            onSuccess()

        } catch (e: Exception) {
            Log.e(TAG, "Error submitting attendance", e)
            throw e
        }
    }

    suspend fun getPendingSubmissions(eventName: String): List<AttendanceSubmission> {
        return try {
            Log.d(TAG, "Looking for pending submissions for event: $eventName")

            // Get all event documents from attendances collection
            val allEventsSnapshot = db.collection("attendances").get().await()

            Log.d(TAG, "Found ${allEventsSnapshot.documents.size} total events in collection")

            // Find the event document whose getEventName() matches the requested eventName
            var targetEventId: String? = null
            for (doc in allEventsSnapshot.documents) {
                try {
                    val event = doc.toObject(AttendanceEvent::class.java)
                    if (event != null) {
                        val extractedEventName = event.getEventName()
                        Log.d(TAG, "Event ID: ${doc.id}, Extracted name: '$extractedEventName', Looking for: '$eventName'")

                        if (extractedEventName.equals(eventName, ignoreCase = true)) {
                            targetEventId = doc.id
                            Log.d(TAG, "Found matching event with ID: $targetEventId")
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing event document ${doc.id}", e)
                }
            }

            if (targetEventId == null) {
                Log.d(TAG, "No event found with name: $eventName")
                return emptyList()
            }

            // Now fetch pending submissions from the marked_attendance subcollection of the found event
            val snapshot = db.collection("attendances")
                .document(targetEventId)
                .collection("marked_attendance")
                .whereEqualTo("approvalStatus", "Pending")
                .get()
                .await()

            Log.d(TAG, "Fetched ${snapshot.documents.size} documents from marked_attendance subcollection for event ID: $targetEventId")

            val submissionList = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(AttendanceSubmission::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing submission document ${doc.id}", e)
                    null
                }
            }

            Log.d(TAG, "Mapped ${submissionList.size} documents to AttendanceSubmission objects")

            return submissionList
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending submissions", e)
            emptyList()
        }
    }

    /**
     * Helper method to find event document ID by event name
     */
    private suspend fun findEventIdByName(eventName: String): String? {
        return try {
            Log.d(TAG, "Looking for event with name: $eventName")

            // Get all event documents from attendances collection
            val allEventsSnapshot = db.collection("attendances").get().await()

            // Find the event document whose getEventName() matches the requested eventName
            for (doc in allEventsSnapshot.documents) {
                try {
                    val event = doc.toObject(AttendanceEvent::class.java)
                    if (event != null) {
                        val extractedEventName = event.getEventName()
                        Log.d(TAG, "Event ID: ${doc.id}, Extracted name: '$extractedEventName', Looking for: '$eventName'")

                        if (extractedEventName.equals(eventName, ignoreCase = true)) {
                            Log.d(TAG, "Found matching event with ID: ${doc.id}")
                            return doc.id
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing event document ${doc.id}", e)
                }
            }

            Log.d(TAG, "No event found with name: $eventName")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error finding event by name", e)
            null
        }
    }

    suspend fun approveAttendance(submission: AttendanceSubmission) {
        try {
            // Ensure current user is loaded
            if (_currentUser.value == null) {
                _currentUser.value = getCurrentUser()
            }

            // Find the event document to get its ID using the helper method
            val eventId = findEventIdByName(submission.eventName)
            if (eventId == null) {
                Log.e(TAG, "Event not found for submission approval: ${submission.eventName}")
                throw Exception("Event not found for submission approval: ${submission.eventName}")
            }

            // Get the current admin user's roll number
            val adminRollNumber = _currentUser.value?.rollNumber
            if (adminRollNumber == null) {
                Log.e(TAG, "Admin user not loaded or roll number is null")
                throw IllegalStateException("Admin user not loaded or roll number is null")
            }

            // First update the attendance status
            val submissionRef = db.collection("attendances")
                .document(eventId)
                .collection("marked_attendance")
                .document(submission.rollNumber)
            
            submissionRef.update(
                mapOf(
                    "approvalStatus" to "Approved",
                    "approvedBy" to adminRollNumber,
                    "approvalTimestamp" to Timestamp.now()
                )
            )
                .await()
            Log.d(TAG, "Attendance approved for ${submission.rollNumber}")

            // Increment student's event_attendance
            AttendanceUtils.incrementStudentAttendance(submission.rollNumber)

            // Then send notification to the student
            try {
                // Get the event document for notification
                val eventDoc = db.collection("attendances").document(eventId).get().await()
                val event = eventDoc.toObject(AttendanceEvent::class.java)?.copy(id = eventDoc.id)
                val message = if (event?.getEventStatus() == AttendanceEvent.STATUS_LIVE) {
                    "Your attendance is approved for ${submission.eventName}."
                } else {
                    "Your attendance is approved for ${submission.eventName}."
                }

                // Create notification data
                val notificationId = UUID.randomUUID().toString()
                val notificationData = hashMapOf(
                    "type" to "ATTENDANCE_APPROVED",
                    "message" to message,
                    "senderRollNumber" to adminRollNumber,
                    "receiverRollNumber" to submission.rollNumber,
                    "eventName" to submission.eventName,
                    "timestamp" to Timestamp.now(),
                    "processed" to false,
                    "notificationId" to notificationId,
                    "isFromCurrentUser" to "false"  // Convert boolean to string
                )

                // Store notification in Firestore
                db.collection("notifications")
                    .document(notificationId)
                    .set(notificationData)
                    .await()

                // Also directly show notification for current device if recipient is active here
                val currentUserId = sessionManager.fetchUserId()

                // If recipient is on this device and it's not from the current user
                if (submission.rollNumber == currentUserId && adminRollNumber != currentUserId) {
                    val messagingService = ChatMessagingService()
                    
                    if (ChatMessagingService.areNotificationsEnabled(application)) {
                        // Prepare notification data
                        val data = mapOf(
                            "message" to message,
                            "senderName" to "Attendance System",
                            "senderId" to adminRollNumber,
                            "groupId" to "attendance", // Use "attendance" as a virtual group
                            "groupName" to "Attendance",
                            "isFromCurrentUser" to "false"  // Convert boolean to string
                        )
                        
                        // Show the notification
                        messagingService.showNotificationFromData(application, data)
                        Log.d(TAG, "Directly showed notification on current device")
                    } else {
                        Log.d(TAG, "Notifications are disabled by user settings")
                    }
                } else {
                    Log.d(TAG, "Not showing notification - recipient not on this device or sender is current user")
                }
            } catch (e: Exception) {
                // Log the error but don't throw it since the attendance was already approved
                Log.e(TAG, "Error sending notification for approved attendance", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error approving attendance", e)
            throw e
        }
    }

    suspend fun rejectAttendance(submission: AttendanceSubmission) {
        try {
            // Ensure current user is loaded
            if (_currentUser.value == null) {
                _currentUser.value = getCurrentUser()
            }

            // Find the event document to get its ID using the helper method
            val eventId = findEventIdByName(submission.eventName)
            if (eventId == null) {
                Log.e(TAG, "Event not found for submission rejection: ${submission.eventName}")
                throw Exception("Event not found for submission rejection: ${submission.eventName}")
            }

            // Get the current admin user's roll number
            val adminRollNumber = _currentUser.value?.rollNumber
            if (adminRollNumber == null) {
                Log.e(TAG, "Admin user not loaded or roll number is null")
                throw IllegalStateException("Admin user not loaded or roll number is null")
            }

            // First update the attendance status
            val submissionRef = db.collection("attendances")
                .document(eventId)
                .collection("marked_attendance")
                .document(submission.rollNumber)
            
            submissionRef.update(
                mapOf(
                    "approvalStatus" to "Rejected",
                    "approvedBy" to adminRollNumber,
                    "approvalTimestamp" to Timestamp.now()
                )
            )
                .await()
            Log.d(TAG, "Attendance rejected for ${submission.rollNumber}")

            // Then send notification to the student
            try {
                // Get the event document for notification
                val eventDoc = db.collection("attendances").document(eventId).get().await()
                val event = eventDoc.toObject(AttendanceEvent::class.java)?.copy(id = eventDoc.id)
                val message = if (event?.getEventStatus() == AttendanceEvent.STATUS_LIVE) {
                    "Your attendance is rejected for ${submission.eventName}. Please resubmit your attendance."
                } else {
                    "Your attendance is rejected for ${submission.eventName}. Please contact $adminRollNumber in the chat section to mark your attendance."
                }

                // Create notification data
                val notificationId = UUID.randomUUID().toString()
                val notificationData = hashMapOf(
                    "type" to "ATTENDANCE_REJECTED",
                    "message" to message,
                    "senderRollNumber" to adminRollNumber,
                    "receiverRollNumber" to submission.rollNumber,
                    "eventName" to submission.eventName,
                    "timestamp" to Timestamp.now(),
                    "processed" to false,
                    "notificationId" to notificationId,
                    "isFromCurrentUser" to "false"  // Convert boolean to string
                )

                // Store notification in Firestore
                db.collection("notifications")
                    .document(notificationId)
                    .set(notificationData)
                    .await()

                // Also directly show notification for current device if recipient is active here
                val currentUserId = sessionManager.fetchUserId()

                // If recipient is on this device and it's not from the current user
                if (submission.rollNumber == currentUserId && adminRollNumber != currentUserId) {
                    val messagingService = ChatMessagingService()
                    
                    if (ChatMessagingService.areNotificationsEnabled(application)) {
                        // Prepare notification data
                        val data = mapOf(
                            "message" to message,
                            "senderName" to "Attendance System",
                            "senderId" to adminRollNumber,
                            "groupId" to "attendance", // Use "attendance" as a virtual group
                            "groupName" to "Attendance",
                            "isFromCurrentUser" to "false"  // Convert boolean to string
                        )
                        
                        // Show the notification
                        messagingService.showNotificationFromData(application, data)
                        Log.d(TAG, "Directly showed notification on current device")
                    } else {
                        Log.d(TAG, "Notifications are disabled by user settings")
                    }
                } else {
                    Log.d(TAG, "Not showing notification - recipient not on this device or sender is current user")
                }
            } catch (e: Exception) {
                // Log the error but don't throw it since the attendance was already rejected
                Log.e(TAG, "Error sending notification for rejected attendance", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting attendance", e)
            throw e
        }
    }

    suspend fun closeEvent(event: AttendanceEvent) {
        try {
            // Update both isLive field and closedAt timestamp for consistency
            // Also remove any duplicate "live" field that might exist
            val updates = mapOf(
                "is_live" to false,
                "closed_at" to com.google.firebase.Timestamp.now(),
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
            // Update isLive field to true and remove closedAt timestamp
            val updates = mapOf(
                "is_live" to true,
                "closed_at" to FieldValue.delete(), // Remove closedAt timestamp
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

    fun loadPendingAttendanceSubmissions(eventName: String) {
        viewModelScope.launch {
            try {
                val submissions = getPendingSubmissions(eventName)
                _pendingSubmissions.value = submissions
            } catch (e: Exception) {
                Log.e(TAG, "Error loading pending submissions", e)
            }
        }
    }
} 