package com.phad.chatapp.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import com.phad.chatapp.models.AttendanceSession
import com.phad.chatapp.models.AttendeeRecord
import com.phad.chatapp.models.AttendanceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Repository for managing QR-based attendance in Firebase Firestore
 * Uses the consolidated NSS_Events_Attendence collection that combines events and attendance data
 */
class AttendanceQRRepository {
    private val TAG = "AttendanceQRRepository"
    private val firestore = FirebaseFirestore.getInstance()

    // Collection references - using single consolidated collection
    private val eventsAttendanceCollection = firestore.collection("NSS_Events_Attendence")
    private val usersCollection = firestore.collection("users")

    // Legacy collection references for migration support
    @Deprecated("Use eventsAttendanceCollection instead")
    private val attendanceSessionsCollection = firestore.collection("NSS_Events_Attendance")
    @Deprecated("Use eventsAttendanceCollection instead")
    private val legacyEventsCollection = firestore.collection("NSS_Events")
    
    /**
     * Create a new attendance session
     */
    suspend fun createAttendanceSession(session: AttendanceSession): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating attendance session: ${session.eventName}")
            
            val sessionId = session.generateSessionId()
            val sessionWithId = session.copy(id = sessionId)
            
            attendanceSessionsCollection.document(sessionId)
                .set(sessionWithId)
                .await()
            
            Log.d(TAG, "Attendance session created successfully with ID: $sessionId")
            return@withContext Result.success(sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating attendance session", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Get an attendance event by ID from the consolidated collection
     */
    suspend fun getAttendanceEvent(eventId: String): Result<AttendanceEvent?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting attendance event from consolidated collection: $eventId")

            val document = eventsAttendanceCollection.document(eventId).get().await()

            return@withContext if (document.exists()) {
                val event = document.toObject(AttendanceEvent::class.java)
                Log.d(TAG, "Attendance event found: ${event?.getEventName()}")
                Result.success(event)
            } else {
                Log.d(TAG, "Attendance event not found")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting attendance event", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Legacy method for backward compatibility
     */
    @Deprecated("Use getAttendanceEvent instead")
    suspend fun getAttendanceSession(sessionId: String): Result<AttendanceSession?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting attendance session (legacy): $sessionId")

            val document = attendanceSessionsCollection.document(sessionId).get().await()

            return@withContext if (document.exists()) {
                val session = document.toObject(AttendanceSession::class.java)
                Log.d(TAG, "Attendance session found: ${session?.eventName}")
                Result.success(session)
            } else {
                Log.d(TAG, "Attendance session not found")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting attendance session", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Update attendance session with new QR code ID
     */
    suspend fun updateSessionQRCode(sessionId: String, qrId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating QR code for session: $sessionId")
            
            val updates = mapOf(
                "current_qr_id" to qrId,
                "qr_refresh_count" to FieldValue.increment(1)
            )
            
            attendanceSessionsCollection.document(sessionId)
                .update(updates)
                .await()
            
            Log.d(TAG, "QR code updated successfully")
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating QR code", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Add attendee to event in the consolidated collection
     */
    suspend fun addAttendeeToEvent(eventId: String, attendee: AttendeeRecord): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== REPOSITORY: ADDING ATTENDEE TO EVENT (CONSOLIDATED) ===")
            Log.d(TAG, "Event ID: $eventId")
            Log.d(TAG, "Attendee: rollNumber=${attendee.rollNumber}, name=${attendee.name}")
            Log.d(TAG, "Scanned from admin: ${attendee.scannedFrom.adminRollNumber}")

            // Check Firebase Auth state
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Log.e(TAG, "No authenticated user found!")
                return@withContext Result.failure(Exception("User not authenticated"))
            }
            Log.d(TAG, "Authenticated user: ${currentUser.email}, UID: ${currentUser.uid}")

            // First check if event exists
            Log.d(TAG, "Checking if event exists...")
            val eventResult = getAttendanceEvent(eventId)
            if (eventResult.isFailure) {
                Log.e(TAG, "Failed to get event: ${eventResult.exceptionOrNull()?.message}")
                return@withContext Result.failure(eventResult.exceptionOrNull() ?: Exception("Failed to get event"))
            }

            val event = eventResult.getOrNull()
            if (event == null) {
                Log.e(TAG, "Event not found: $eventId")
                return@withContext Result.failure(Exception("Event not found: $eventId"))
            }

            Log.d(TAG, "Event found: ${event.getEventName()}, totalMarked=${event.totalMarked}")

            // Check if student already attended
            if (event.hasStudentAttended(attendee.rollNumber)) {
                Log.w(TAG, "Student ${attendee.rollNumber} already attended this event")
                return@withContext Result.failure(Exception("Student has already marked attendance for this event"))
            }

            Log.d(TAG, "Student has not attended yet, proceeding with update...")

            val updates = mapOf(
                "attendees" to FieldValue.arrayUnion(attendee),
                "total_marked" to FieldValue.increment(1)
            )

            Log.d(TAG, "Performing Firestore update with: $updates")

            // Verify document exists before updating
            val docRef = eventsAttendanceCollection.document(eventId)
            val docSnapshot = docRef.get().await()
            if (!docSnapshot.exists()) {
                Log.e(TAG, "Event document does not exist: $eventId")
                return@withContext Result.failure(Exception("Event document not found: $eventId"))
            }

            Log.d(TAG, "Event document exists, proceeding with update...")

            docRef.update(updates).await()

            Log.d(TAG, "✅ Firestore update completed successfully")

            // Update student statistics in Student collection
            try {
                                 Log.d(TAG, "Updating student statistics for: ${attendee.rollNumber}")
                 val studentRef = firestore.collection("Student").document(attendee.rollNumber)
                
                // Get current student document
                val studentDoc = studentRef.get().await()
                if (studentDoc.exists()) {
                    // Get current values
                    val currentEventsAttended = studentDoc.getLong("events_attended") ?: 0L
                    val currentHours = studentDoc.getLong("hours") ?: 0L
                    
                    // Get current events_list
                    @Suppress("UNCHECKED_CAST")
                    val currentEventsList = studentDoc.get("events_list") as? List<String> ?: emptyList()
                    
                    // Get event hours
                    val eventHours = event.hours
                    
                    // Determine semester based on event date
                    val semester = getSemesterFromDate(event.eventDate)
                    val sem1Hours = if (semester == 1) eventHours else 0
                    val sem2Hours = if (semester == 2) eventHours else 0
                    
                    // Add event ID to events_list if not already present
                    val updatedEventsList = if (!currentEventsList.contains(eventId)) {
                        currentEventsList + eventId
                    } else {
                        currentEventsList
                    }
                    
                    // Update student document
                    val studentUpdates = mapOf(
                        "events_attended" to (currentEventsAttended + 1),
                        "hours" to (currentHours + eventHours),
                        "sem1_hours" to FieldValue.increment(sem1Hours.toLong()),
                        "sem2_hours" to FieldValue.increment(sem2Hours.toLong()),
                        "events_list" to updatedEventsList
                    )
                    
                    studentRef.update(studentUpdates).await()
                    Log.d(TAG, "✅ Student statistics and events_list updated successfully")
                } else {
                    Log.w(TAG, "⚠️ Student document not found: ${attendee.rollNumber}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating student statistics: ${e.message}", e)
                // Don't fail the attendance marking if student stats update fails
            }

            // Verify the update was successful by reading back the document
            val updatedDoc = docRef.get().await()
            val updatedEvent = updatedDoc.toObject(AttendanceEvent::class.java)
            if (updatedEvent != null) {
                Log.d(TAG, "Verification: Updated event has ${updatedEvent.totalMarked} attendees")
                Log.d(TAG, "Verification: Attendees list size: ${updatedEvent.attendees.size}")
                val foundAttendee = updatedEvent.attendees.find { it.rollNumber == attendee.rollNumber }
                if (foundAttendee != null) {
                    Log.d(TAG, "✅ Verification: Attendee found in updated event")
                } else {
                    Log.w(TAG, "⚠️ Verification: Attendee NOT found in updated event")
                }
            } else {
                Log.w(TAG, "⚠️ Verification: Could not read updated event")
            }

            Log.d(TAG, "=== REPOSITORY: ATTENDEE ADDED SUCCESSFULLY ===")
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding attendee to event: ${e.message}", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "=== REPOSITORY: ATTENDEE ADDITION FAILED ===")
            return@withContext Result.failure(e)
        }
    }

    /**
     * Legacy method for backward compatibility
     */
    @Deprecated("Use addAttendeeToEvent instead")
    suspend fun addAttendeeToSession(sessionId: String, attendee: AttendeeRecord): Result<Unit> {
        return addAttendeeToEvent(sessionId, attendee)
    }
    
    /**
     * End attendance session (in consolidated schema, this updates the event document)
     */
    suspend fun endAttendanceSession(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Ending attendance session for event: $sessionId")

            // In consolidated schema, sessionId is the event ID
            // We don't need to update anything in the database since the event remains live
            // The session state is managed entirely in the ViewModel
            Log.d(TAG, "Attendance session ended successfully (consolidated schema)")
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error ending attendance session", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Get active sessions for an admin
     */
    suspend fun getActiveSessionsForAdmin(adminId: String): Result<List<AttendanceSession>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting active sessions for admin: $adminId")
            
            val querySnapshot = attendanceSessionsCollection
                .whereEqualTo("admin_id", adminId)
                .whereEqualTo("is_active", true)
                .orderBy("session_start_time", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val sessions = querySnapshot.documents.mapNotNull { 
                it.toObject(AttendanceSession::class.java) 
            }
            
            Log.d(TAG, "Found ${sessions.size} active sessions")
            return@withContext Result.success(sessions)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active sessions", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Get attendance sessions for an event
     */
    suspend fun getSessionsForEvent(eventId: String): Result<List<AttendanceSession>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting sessions for event: $eventId")
            
            val querySnapshot = attendanceSessionsCollection
                .whereEqualTo("event_id", eventId)
                .orderBy("session_start_time", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val sessions = querySnapshot.documents.mapNotNull { 
                it.toObject(AttendanceSession::class.java) 
            }
            
            Log.d(TAG, "Found ${sessions.size} sessions for event")
            return@withContext Result.success(sessions)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sessions for event", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Create a new attendance event in the consolidated collection
     */
    suspend fun createAttendanceEvent(event: AttendanceEvent): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating attendance event in consolidated collection: ${event.getEventName()}")

            // Use the provided document ID (already generated with new format)
            val eventId = event.id.ifEmpty {
                // Fallback to old format if ID is not provided (for backward compatibility)
                "${event.getEventName().replace(" ", "_")}_${System.currentTimeMillis()}"
            }
            val eventWithId = event.copy(id = eventId)

            // Store in the consolidated NSS_Events_Attendence collection
            eventsAttendanceCollection.document(eventId)
                .set(eventWithId)
                .await()

            Log.d(TAG, "Attendance event created successfully in consolidated collection with ID: $eventId")
            return@withContext Result.success(eventId)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating attendance event in consolidated collection", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Update an existing attendance event in the consolidated collection
     */
    suspend fun updateAttendanceEvent(event: AttendanceEvent): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating attendance event in consolidated collection: ${event.getEventName()} (ID: ${event.id})")

            // Prepare data for update. Only update fields that can be changed by admin.
            val updates = mapOf(
                "description" to event.description,
                "eventDate" to event.eventDate,
                "eventTime" to event.eventTime,
                "hours" to event.hours,
                "location" to event.location
                // Do not update 'id', 'createdBy', 'creatorName', 'createdAt', 'attendees', 'closedAt', 'is_live'
            )

            eventsAttendanceCollection.document(event.id)
                .update(updates)
                .await()

            Log.d(TAG, "Attendance event updated successfully in consolidated collection: ${event.id}")
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating attendance event in consolidated collection", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Get available events for attendance from the consolidated collection
     */
    suspend fun getAvailableEvents(): Result<List<AttendanceEvent>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting available events from consolidated collection")

            val querySnapshot = eventsAttendanceCollection
                .get()
                .await()

            val events = querySnapshot.documents.mapNotNull { doc ->
                val event = doc.toObject(AttendanceEvent::class.java)?.copy(id = doc.id)
                event?.let {
                    val rawData = doc.data
                    val isEventLive = if (rawData?.containsKey("is_live") == true) {
                        rawData["is_live"] as? Boolean ?: true
                    } else {
                        it.isLive
                    }
                    if (isEventLive) it else null
                }
            }.sortedByDescending { event -> event.createdAt.toDate().time }

            Log.d(TAG, "Found ${events.size} available events in consolidated collection")
            return@withContext Result.success(events)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available events from consolidated collection", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Get past (non-live) events for history display
     */
    suspend fun getEventHistory(): Result<List<AttendanceEvent>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting past events for history")
            val querySnapshot = eventsAttendanceCollection
                .get()
                .await()

            val events = querySnapshot.documents.mapNotNull { doc ->
                val event = doc.toObject(AttendanceEvent::class.java)?.copy(id = doc.id)
                event?.let {
                    val rawData = doc.data
                    val isEventLive = if (rawData?.containsKey("is_live") == true) {
                        rawData["is_live"] as? Boolean ?: true
                    } else {
                        it.isLive
                    }
                    if (!isEventLive) it else null
                }
            }.sortedByDescending { event -> event.createdAt.toDate().time }

            Log.d(TAG, "Found ${events.size} past events")
            return@withContext Result.success(events)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting past events", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Get user information by roll number
     */
    suspend fun getUserByRollNumber(rollNumber: String): Result<Map<String, Any>?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting user by roll number: $rollNumber")
            
            val document = usersCollection.document(rollNumber).get().await()
            
            return@withContext if (document.exists()) {
                val userData = document.data
                Log.d(TAG, "User found: ${userData?.get("name")}")
                Result.success(userData)
            } else {
                Log.d(TAG, "User not found")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user by roll number", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Get student information by roll number from Student collection
     */
    suspend fun getStudentByRollNumber(rollNumber: String): Result<Map<String, Any>?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting student by roll number: $rollNumber")
            
            val studentCollection = firestore.collection("Student")
            val document = studentCollection.document(rollNumber).get().await()
            
            return@withContext if (document.exists()) {
                val studentData = document.data
                Log.d(TAG, "Student found for $rollNumber: $studentData")
                Log.d(TAG, "Student name field: ${studentData?.get("name")}")
                Log.d(TAG, "Student data keys: ${studentData?.keys}")
                Result.success(studentData)
            } else {
                Log.w(TAG, "Student document does not exist for roll number: $rollNumber")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting student by roll number: $rollNumber", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Listen to real-time updates for an attendance event in the consolidated collection
     */
    fun listenToAttendanceEvent(eventId: String): Flow<AttendanceEvent?> = callbackFlow {
        Log.d(TAG, "Starting real-time listener for event: $eventId")

        val listener = eventsAttendanceCollection.document(eventId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to event updates", error)
                    close(error)
                    return@addSnapshotListener
                }

                val event = snapshot?.toObject(AttendanceEvent::class.java)
                trySend(event)
            }

        awaitClose {
            Log.d(TAG, "Stopping real-time listener for event: $eventId")
            listener.remove()
        }
    }

    /**
     * Close an attendance event manually by setting isLive to false and closedAt timestamp
     */
    suspend fun closeAttendanceEvent(eventId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Closing attendance event: $eventId")

            // Update both isLive field and closedAt timestamp
            // Also remove any duplicate "live" field that might exist
            val updates = mapOf(
                "is_live" to false,
                "closedAt" to com.google.firebase.Timestamp.now(),
                "live" to FieldValue.delete() // Remove duplicate field if it exists
            )

            eventsAttendanceCollection.document(eventId)
                .update(updates)
                .await()

            Log.d(TAG, "Attendance event closed successfully: $eventId")
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error closing attendance event", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Legacy method for backward compatibility
     */
    @Deprecated("Use listenToAttendanceEvent instead")
    fun listenToAttendanceSession(sessionId: String): Flow<AttendanceSession?> = callbackFlow {
        Log.d(TAG, "Starting real-time listener for session (legacy): $sessionId")

        val listener = attendanceSessionsCollection.document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to session updates", error)
                    close(error)
                    return@addSnapshotListener
                }

                val session = snapshot?.toObject(AttendanceSession::class.java)
                trySend(session)
            }

        awaitClose {
            Log.d(TAG, "Stopping real-time listener for session: $sessionId")
            listener.remove()
        }
    }

    /**
     * Check if a device has already been used for attendance in a specific event
     */
    suspend fun hasDeviceAttendedEvent(eventId: String, deviceId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (deviceId.isBlank()) {
                Log.w(TAG, "Device ID is blank, returning false for device attendance check")
                return@withContext Result.success(false)
            }

            Log.d(TAG, "Checking if device $deviceId has attended event $eventId")

            val eventDoc = eventsAttendanceCollection.document(eventId).get().await()
            val event = eventDoc.toObject(AttendanceEvent::class.java)

            if (event == null) {
                Log.w(TAG, "Event $eventId not found")
                return@withContext Result.success(false)
            }

            val deviceAlreadyUsed = event.attendees.any { attendee ->
                attendee.deviceId.isNotBlank() && attendee.deviceId == deviceId
            }

            Log.d(TAG, "Device $deviceId attendance check for event $eventId: $deviceAlreadyUsed")
            return@withContext Result.success(deviceAlreadyUsed)

        } catch (e: Exception) {
            Log.e(TAG, "Error checking device attendance for event $eventId", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Get all attendees who used a specific device ID across all events
     */
    suspend fun getAttendeesByDeviceId(deviceId: String): Result<List<AttendeeRecord>> = withContext(Dispatchers.IO) {
        try {
            if (deviceId.isBlank()) {
                return@withContext Result.success(emptyList())
            }

            Log.d(TAG, "Getting attendees by device ID: $deviceId")

            // Query all events and filter attendees by device ID
            val querySnapshot = eventsAttendanceCollection.get().await()
            val attendeesWithDevice = mutableListOf<AttendeeRecord>()

            for (document in querySnapshot.documents) {
                val event = document.toObject(AttendanceEvent::class.java)
                event?.attendees?.forEach { attendee ->
                    if (attendee.deviceId == deviceId) {
                        attendeesWithDevice.add(attendee)
                    }
                }
            }

            Log.d(TAG, "Found ${attendeesWithDevice.size} attendees with device ID $deviceId")
            return@withContext Result.success(attendeesWithDevice)

        } catch (e: Exception) {
            Log.e(TAG, "Error getting attendees by device ID", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Clean up redundant fields from all events in the NSS_Events_Attendence collection
     * Removes duplicate "totalMarked" and "live" fields that should not exist alongside
     * the proper snake_case fields "total_marked" and "is_live"
     */
    suspend fun cleanupRedundantFields(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting cleanup of redundant fields in NSS_Events_Attendence collection...")

            val allEventsQuery = eventsAttendanceCollection
                .get()
                .await()

            var cleanedCount = 0
            allEventsQuery.documents.forEach { doc ->
                val data = doc.data
                if (data != null) {
                    val updates = mutableMapOf<String, Any>()

                    // Check for redundant "totalMarked" field (should be "total_marked")
                    if (data.containsKey("totalMarked")) {
                        updates["totalMarked"] = FieldValue.delete()
                        Log.d(TAG, "Marking 'totalMarked' field for removal from event: ${doc.id}")
                    }

                    // Check for redundant "live" field (should be "is_live")
                    if (data.containsKey("live")) {
                        updates["live"] = FieldValue.delete()
                        Log.d(TAG, "Marking 'live' field for removal from event: ${doc.id}")
                    }

                    // Apply updates if any redundant fields were found
                    if (updates.isNotEmpty()) {
                        eventsAttendanceCollection.document(doc.id)
                            .update(updates)
                            .await()

                        cleanedCount++
                        Log.d(TAG, "Cleaned redundant fields from event: ${doc.id}")
                    }
                }
            }

            Log.d(TAG, "Cleanup completed. Cleaned $cleanedCount events.")
            return@withContext Result.success(cleanedCount)

        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup of redundant fields", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Determine semester from event date
     */
    private fun getSemesterFromDate(eventDate: String): Int {
        try {
            val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH)
            val date = dateFormat.parse(eventDate)
            if (date != null) {
                val calendar = java.util.Calendar.getInstance()
                calendar.time = date
                
                val month = calendar.get(java.util.Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
                val year = calendar.get(java.util.Calendar.YEAR)
                
                // Semester 1: July 2025 to December 2025
                if (year == 2025 && month in 7..12) {
                    return 1
                }
                // Semester 2: January 2026 to May 2026
                else if (year == 2026 && month in 1..5) {
                    return 2
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing event date: $eventDate", e)
        }
        return 0 // Unknown semester
    }
}
