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
import com.phad.chatapp.utils.SessionManager
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
    private var sessionManager: SessionManager? = null

    // Collection references - using single consolidated collection
    private val eventsAttendanceCollection = firestore.collection("NSS_Events_Attendence")
    private val usersCollection = firestore.collection("users")
    
    /**
     * Set SessionManager for token refresh functionality
     */
    fun setSessionManager(sessionManager: SessionManager) {
        this.sessionManager = sessionManager
    }
    
    /**
     * Ensure Firebase authentication is valid before database operations
     * Attempts to refresh token if needed
     */
    private suspend fun ensureValidFirebaseAuth(): Boolean {
        try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Log.e(TAG, "No Firebase user found - attempting token refresh")
                
                // Try to refresh token using SessionManager if available
                sessionManager?.let { manager ->
                    val refreshSuccess = manager.ensureValidFirebaseToken()
                    if (refreshSuccess) {
                        Log.d(TAG, "✅ Token refreshed successfully")
                        return true
                    }
                }
                
                Log.e(TAG, "❌ Failed to refresh Firebase token")
                return false
            }
            
            // Check if token is valid
            try {
                val tokenResult = currentUser.getIdToken(false).await()
                if (tokenResult.token != null) {
                    Log.d(TAG, "Firebase token is valid")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Token check failed, attempting refresh: ${e.message}")
            }
            
            // Try to refresh token
            sessionManager?.let { manager ->
                val refreshSuccess = manager.refreshFirebaseToken()
                if (refreshSuccess) {
                    Log.d(TAG, "✅ Token refreshed successfully")
                    return true
                }
            }
            
            Log.e(TAG, "❌ Failed to ensure valid Firebase authentication")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring Firebase auth: ${e.message}", e)
            return false
        }
    }

    // Legacy collection references for migration support
    @Deprecated("Use eventsAttendanceCollection instead")
    private val attendanceSessionsCollection = firestore.collection("NSS_Events_Attendance")
    @Deprecated("Use eventsAttendanceCollection instead")
    private val legacyEventsCollection = firestore.collection("NSS_Events")
    
    // Simple in-memory cache to avoid duplicate reads during a short window
    // Keeps last fetched list and timestamp
    private var cachedAllEvents: List<AttendanceEvent>? = null
    private var cacheTimestampMs: Long = 0L
    private val cacheTtlMs: Long = 600_000 // 10 minutes TTL; safe default
    private var cachedAvailableEvents: List<AttendanceEvent>? = null
    private var availableCacheTimestampMs: Long = 0L
    
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

            // Try local cache first to avoid server reads when possible
            val cacheDoc = eventsAttendanceCollection.document(eventId)
                .get(com.google.firebase.firestore.Source.CACHE)
                .await()
            if (cacheDoc.exists()) {
                val event = cacheDoc.toObject(AttendanceEvent::class.java)
                Log.d(TAG, "Attendance event served from CACHE: ${event?.getEventName()}")
                return@withContext Result.success(event)
            }

            // Fallback to server
            val document = eventsAttendanceCollection.document(eventId)
                .get(com.google.firebase.firestore.Source.DEFAULT)
                .await()

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
     * Fetch all events with a single collection read.
     * Uses a short-lived in-memory cache to prevent repeated reads while navigating.
     */
    suspend fun getAllEvents(forceRefresh: Boolean = false): Result<List<AttendanceEvent>> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val cached = cachedAllEvents
            if (!forceRefresh && cached != null && now - cacheTimestampMs <= cacheTtlMs) {
                Log.d(TAG, "Returning events from in-memory cache: ${cached.size}")
                return@withContext Result.success(cached)
            }

            Log.d(TAG, "Fetching ALL events with a single collection read (forceRefresh=$forceRefresh)")

            if (!forceRefresh) {
                // Try from local cache first
                val cacheSnapshot = eventsAttendanceCollection
                    .get(com.google.firebase.firestore.Source.CACHE)
                    .await()

                val fromCache = cacheSnapshot.documents.mapNotNull { doc ->
                    doc.toObject(AttendanceEvent::class.java)?.copy(id = doc.id)
                }

                if (fromCache.isNotEmpty()) {
                    val sorted = fromCache.sortedByDescending { event -> event.createdAt.toDate().time }
                    cachedAllEvents = sorted
                    cacheTimestampMs = now
                    Log.d(TAG, "Served ${sorted.size} events from local CACHE")
                    return@withContext Result.success(sorted)
                }
            }

            // Fallback to server
            val serverSnapshot = eventsAttendanceCollection
                .get(com.google.firebase.firestore.Source.DEFAULT)
                .await()

            val events = serverSnapshot.documents.mapNotNull { doc ->
                doc.toObject(AttendanceEvent::class.java)?.copy(id = doc.id)
            }.sortedByDescending { event -> event.createdAt.toDate().time }

            cachedAllEvents = events
            cacheTimestampMs = now

            Log.d(TAG, "Fetched ${events.size} total events")
            return@withContext Result.success(events)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all events", e)
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

            // Ensure Firebase Auth is valid (with token refresh if needed)
            val authValid = ensureValidFirebaseAuth()
            if (!authValid) {
                Log.e(TAG, "Firebase authentication failed - token may be expired")
                return@withContext Result.failure(Exception("Authentication expired. Please logout and login again."))
            }
            
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Log.e(TAG, "No authenticated user found after token refresh!")
                return@withContext Result.failure(Exception("User not authenticated"))
            }
            Log.d(TAG, "Authenticated user: ${currentUser.email}, UID: ${currentUser.uid}")

            // Attempt idempotent update without pre-reads.
            // We rely on UI/device checks to minimize duplicates; server uses atomic increments.
            val updates = mapOf(
                "attendees" to FieldValue.arrayUnion(attendee),
                "total_marked" to FieldValue.increment(1)
            )

            Log.d(TAG, "Performing Firestore update with: $updates")

            val docRef = eventsAttendanceCollection.document(eventId)
            // Write subcollection attendance document (idempotent: onCreate triggers only on first time)
            val attendanceDocRef = docRef.collection("attendance").document(attendee.rollNumber)
            attendanceDocRef.set(attendee).await()

            // Update parent event counters and embedded list for backward compatibility
            docRef.update(updates).await()

            Log.d(TAG, "✅ Firestore update completed successfully")

            // Update student statistics in users collection without prior reads using atomic increments
            try {
                Log.d(TAG, "Updating student statistics for: ${attendee.rollNumber}")
                val userRef = firestore.collection("users").document(attendee.rollNumber)
                // Compute increments from event data without fetching the user
                val eventSnapshot = docRef.get().await()
                val event = eventSnapshot.toObject(AttendanceEvent::class.java)
                if (event != null) {
                    val eventHours = event.hours
                    val semester = getSemesterFromDate(event.eventDate)
                    val sem1Hours = if (semester == 1) eventHours else 0
                    val sem2Hours = if (semester == 2) eventHours else 0
                    val userUpdates = mapOf(
                        "eventsAttended" to FieldValue.increment(1),
                        "hours" to FieldValue.increment(eventHours.toLong()),
                        "sem1Hours" to FieldValue.increment(sem1Hours.toLong()),
                        "sem2Hours" to FieldValue.increment(sem2Hours.toLong()),
                        "eventsList" to FieldValue.arrayUnion(eventId)
                    )
                    userRef.set(userUpdates, com.google.firebase.firestore.SetOptions.merge()).await()
                    Log.d(TAG, "✅ User statistics updated atomically")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating user statistics: ${e.message}", e)
                // Don't fail the attendance marking if user stats update fails
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
     * Remove attendee from event in the consolidated collection
     */
    suspend fun removeAttendeeFromEvent(eventId: String, rollNumber: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== REPOSITORY: REMOVING ATTENDEE FROM EVENT (CONSOLIDATED) ===")
            Log.d(TAG, "Event ID: $eventId")
            Log.d(TAG, "Roll Number: $rollNumber")

            // Check Firebase Auth state
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Log.e(TAG, "No authenticated user found!")
                return@withContext Result.failure(Exception("User not authenticated"))
            }
            Log.d(TAG, "Authenticated user: ${currentUser.email}, UID: ${currentUser.uid}")

            val docRef = eventsAttendanceCollection.document(eventId)
            
            // First, get the current event to find the attendee record to remove
            val eventSnapshot = docRef.get().await()
            val event = eventSnapshot.toObject(AttendanceEvent::class.java)
            
            if (event == null) {
                Log.e(TAG, "Event not found: $eventId")
                return@withContext Result.failure(Exception("Event not found"))
            }

            // Find the attendee record to remove
            val attendeeToRemove = event.attendees.find { it.rollNumber == rollNumber }
            if (attendeeToRemove == null) {
                Log.e(TAG, "Attendee not found in event: $rollNumber")
                return@withContext Result.failure(Exception("Attendee not found in event"))
            }

            // Remove from attendees array and decrement total_marked
            val updates = mapOf(
                "attendees" to FieldValue.arrayRemove(attendeeToRemove),
                "total_marked" to FieldValue.increment(-1)
            )

            Log.d(TAG, "Performing Firestore update with: $updates")

            // Remove from subcollection attendance document
            val attendanceDocRef = docRef.collection("attendance").document(rollNumber)
            attendanceDocRef.delete().await()

            // Update parent event counters and embedded list
            docRef.update(updates).await()

            Log.d(TAG, "✅ Firestore update completed successfully")

            // Update student statistics in users collection (reverse the increments)
            try {
                Log.d(TAG, "Updating student statistics for: $rollNumber")
                val userRef = firestore.collection("users").document(rollNumber)
                val eventHours = event.hours
                val semester = getSemesterFromDate(event.eventDate)
                val sem1Hours = if (semester == 1) eventHours else 0
                val sem2Hours = if (semester == 2) eventHours else 0
                val userUpdates = mapOf(
                    "eventsAttended" to FieldValue.increment(-1),
                    "hours" to FieldValue.increment(-eventHours.toLong()),
                    "sem1Hours" to FieldValue.increment(-sem1Hours.toLong()),
                    "sem2Hours" to FieldValue.increment(-sem2Hours.toLong()),
                    "eventsList" to FieldValue.arrayRemove(eventId)
                )
                userRef.set(userUpdates, com.google.firebase.firestore.SetOptions.merge()).await()
                Log.d(TAG, "✅ User statistics updated atomically")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating user statistics: ${e.message}", e)
                // Don't fail the attendance removal if user stats update fails
            }

            Log.d(TAG, "=== REPOSITORY: ATTENDEE REMOVED SUCCESSFULLY ===")
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error removing attendee from event: ${e.message}", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "=== REPOSITORY: ATTENDEE REMOVAL FAILED ===")
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

            // 1) Fetch the existing event to compute differences
            val eventDocRef = eventsAttendanceCollection.document(event.id)
            val existingSnapshot = eventDocRef.get().await()
            val oldEvent = existingSnapshot.toObject(AttendanceEvent::class.java)

            if (oldEvent == null) {
                Log.w(TAG, "Event not found when attempting update: ${event.id}")
                return@withContext Result.failure(IllegalStateException("Event not found: ${event.id}"))
            }

            val oldHours = oldEvent.hours
            val newHours = event.hours
            val hoursDelta = newHours - oldHours

            val oldSemester = getSemesterFromDate(oldEvent.eventDate)
            val newSemester = getSemesterFromDate(event.eventDate)

            Log.d(TAG, "Old hours=$oldHours, New hours=$newHours, Delta=$hoursDelta; Old semester=$oldSemester, New semester=$newSemester")

            // 2) Build a batched write: update the event, then adjust user stats for attendees
            val batch = firestore.batch()

            // Update event fields that are editable
            val eventUpdates = mapOf(
                "description" to event.description,
                "eventDate" to event.eventDate,
                "eventTime" to event.eventTime,
                "hours" to event.hours,
                "location" to event.location,
                // Ensure mandatory penalty config is persisted on edit
                "mandatory" to event.isMandatory,
                "negativeHours" to event.negativeHours
            )
            batch.update(eventDocRef, eventUpdates)

            // If there are attendees, propagate hour/semester changes to their user docs
            if ((hoursDelta != 0.0 || oldSemester != newSemester) && oldEvent.attendees.isNotEmpty()) {
                oldEvent.attendees.forEach { attendee ->
                    val userRef = usersCollection.document(attendee.rollNumber)

                    // Prepare per-user updates depending on delta and semester change
                    val userUpdates = mutableMapOf<String, Any>()

                    // Always adjust total hours if hours changed
                    if (hoursDelta != 0.0) {
                        userUpdates["hours"] = FieldValue.increment(hoursDelta)
                    }

                    // Adjust semester-specific hours
                    if (oldSemester == newSemester) {
                        // Same semester: just increment that semester by delta
                        when (newSemester) {
                            1 -> userUpdates["sem1Hours"] = FieldValue.increment(hoursDelta)
                            2 -> userUpdates["sem2Hours"] = FieldValue.increment(hoursDelta)
                        }
                    } else {
                        // Semester moved: subtract old hours from old semester, add new hours to new semester
                        when (oldSemester) {
                            1 -> userUpdates["sem1Hours"] = FieldValue.increment(-oldHours)
                            2 -> userUpdates["sem2Hours"] = FieldValue.increment(-oldHours)
                        }
                        when (newSemester) {
                            1 -> userUpdates.merge("sem1Hours", FieldValue.increment(newHours)) { _, new -> new }
                            2 -> userUpdates.merge("sem2Hours", FieldValue.increment(newHours)) { _, new -> new }
                        }
                    }

                    if (userUpdates.isNotEmpty()) {
                        batch.update(userRef, userUpdates)
                    }
                }
            }

            // 2b) Handle mandatory penalties for ABSENTEES when event settings change
            // Compute old/new penalty values
            val oldPenalty = if (oldEvent.isMandatory && oldEvent.negativeHours > 0.0) oldEvent.negativeHours else 0.0
            val newPenalty = if (event.isMandatory && event.negativeHours > 0.0) event.negativeHours else 0.0

            if (oldPenalty != newPenalty || oldSemester != newSemester) {
                // Determine absentee set: prefer stored metadata, else recompute
                val attendeeRolls = (oldEvent.attendees.map { it.rollNumber } + event.attendees.map { it.rollNumber }).toSet()
                val allUsersDocs = usersCollection.get().await().documents
                val absentees = allUsersDocs.map { it.id }.filter { it.isNotBlank() && !attendeeRolls.contains(it) }

                // If semester unchanged, apply delta in place
                if (oldSemester == newSemester) {
                    val delta = newPenalty - oldPenalty
                    if (delta != 0.0 && absentees.isNotEmpty()) {
                        val inc = -delta // negative to deduct; positive to refund
                        val chunkSize = 400
                        absentees.chunked(chunkSize).forEach { chunk ->
                            val penaltyBatch = firestore.batch()
                            chunk.forEach { roll ->
                                val ref = usersCollection.document(roll)
                                val updates = mutableMapOf<String, Any>(
                                    "hours" to FieldValue.increment(inc)
                                )
                                when (newSemester) {
                                    1 -> updates["sem1Hours"] = FieldValue.increment(inc)
                                    2 -> updates["sem2Hours"] = FieldValue.increment(inc)
                                }
                                penaltyBatch.update(ref, updates)
                            }
                            penaltyBatch.commit().await()
                        }
                    }
                } else {
                    // Semester moved: refund old semester penalty and apply new semester penalty
                    if (oldPenalty != 0.0 && absentees.isNotEmpty()) {
                        val incRefund = oldPenalty // refund adds back (since old was deducted)
                        val chunkSize = 400
                        absentees.chunked(chunkSize).forEach { chunk ->
                            val refundBatch = firestore.batch()
                            chunk.forEach { roll ->
                                val ref = usersCollection.document(roll)
                                val updates = mutableMapOf<String, Any>(
                                    "hours" to FieldValue.increment(incRefund)
                                )
                                when (oldSemester) {
                                    1 -> updates["sem1Hours"] = FieldValue.increment(incRefund)
                                    2 -> updates["sem2Hours"] = FieldValue.increment(incRefund)
                                }
                                refundBatch.update(ref, updates)
                            }
                            refundBatch.commit().await()
                        }
                    }

                    if (newPenalty != 0.0 && absentees.isNotEmpty()) {
                        val incApply = -newPenalty
                        val chunkSize = 400
                        absentees.chunked(chunkSize).forEach { chunk ->
                            val applyBatch = firestore.batch()
                            chunk.forEach { roll ->
                                val ref = usersCollection.document(roll)
                                val updates = mutableMapOf<String, Any>(
                                    "hours" to FieldValue.increment(incApply)
                                )
                                when (newSemester) {
                                    1 -> updates["sem1Hours"] = FieldValue.increment(incApply)
                                    2 -> updates["sem2Hours"] = FieldValue.increment(incApply)
                                }
                                applyBatch.update(ref, updates)
                            }
                            applyBatch.commit().await()
                        }
                    }
                }
            }

            // Commit batch
            batch.commit().await()

            Log.d(TAG, "Attendance event and user stats updated successfully: ${event.id}")
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating attendance event in consolidated collection", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Get available events for attendance from the consolidated collection
     */
    suspend fun getAvailableEvents(forceRefresh: Boolean = false): Result<List<AttendanceEvent>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting available events from consolidated collection (cache-first)")

            val now = System.currentTimeMillis()
            val cached = cachedAvailableEvents
            if (!forceRefresh && cached != null && now - availableCacheTimestampMs <= cacheTtlMs) {
                Log.d(TAG, "Serving ${cached.size} available events from in-memory cache")
                return@withContext Result.success(cached)
            }

            if (!forceRefresh) {
                // Try local cache first
                val cacheSnapshot = eventsAttendanceCollection
                    .get(com.google.firebase.firestore.Source.CACHE)
                    .await()

                val fromCache = cacheSnapshot.documents.mapNotNull { doc ->
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

                if (fromCache.isNotEmpty()) {
                    cachedAvailableEvents = fromCache
                    availableCacheTimestampMs = now
                    Log.d(TAG, "Served ${fromCache.size} available events from local CACHE")
                    return@withContext Result.success(fromCache)
                }
            }

            // Fallback to server
            val serverSnapshot = eventsAttendanceCollection
                .get(com.google.firebase.firestore.Source.DEFAULT)
                .await()

            val events = serverSnapshot.documents.mapNotNull { doc ->
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

            cachedAvailableEvents = events
            availableCacheTimestampMs = now

            Log.d(TAG, "Found ${events.size} available events from server")
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
     * Get student information by roll number from users collection (legacy support)
     */
    suspend fun getStudentByRollNumber(rollNumber: String): Result<Map<String, Any>?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting student by roll number from users collection: $rollNumber")
            
            val usersCollection = firestore.collection("users")
            val document = usersCollection.document(rollNumber).get().await()
            
            return@withContext if (document.exists()) {
                val userData = document.data
                Log.d(TAG, "User found for $rollNumber: $userData")
                Log.d(TAG, "User name field: ${userData?.get("name")}")
                Log.d(TAG, "User data keys: ${userData?.keys}")
                Result.success(userData)
            } else {
                Log.w(TAG, "User document does not exist for roll number: $rollNumber")
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

            // Fetch event to check mandatory and negative hours
            val eventSnap = eventsAttendanceCollection.document(eventId).get().await()
            val event = eventSnap.toObject(AttendanceEvent::class.java)

            // Read penalty metadata
            val penaltyApplied = (eventSnap.get("absentPenaltyApplied") as? Boolean) == true
            val previouslyUsedPenalty = (eventSnap.get("penaltyHoursUsed") as? Number)?.toDouble() ?: 0.0
            @Suppress("UNCHECKED_CAST")
            val storedAbsentees: List<String> = (eventSnap.get("absentee_roll_numbers") as? List<String>) ?: emptyList()

            if (event != null) {
                val isMandatory = try { eventSnap.getBoolean("mandatory") ?: event.isMandatory } catch (e: Exception) { event.isMandatory }
                val negativeHours = try { (eventSnap.get("negativeHours") as? Number)?.toDouble() ?: event.negativeHours } catch (e: Exception) { event.negativeHours }

                Log.d(TAG, "Close event penalty check: isMandatory=$isMandatory, negativeHours=$negativeHours, alreadyApplied=$penaltyApplied")

                if (isMandatory && negativeHours > 0.0 && !penaltyApplied) {
                    // First-time application
                    val attendeeRolls = event.attendees.map { it.rollNumber }.toSet()
                    val semester = getSemesterFromDate(event.eventDate)

                    var allUsers = usersCollection.get().await().documents
                    if (allUsers.isEmpty()) {
                        // Fallback to ttwStudents if users is empty
                        Log.w(TAG, "users collection is empty; falling back to ttwStudents")
                        val fallback = firestore.collection("ttwStudents").get().await().documents
                        allUsers = fallback
                    }

                    val absentees = allUsers.map { it.id }
                        .filter { it.isNotBlank() && !attendeeRolls.contains(it) }

                    Log.d(TAG, "Found ${absentees.size} absentees for initial penalty application")

                    val chunkSize = 400
                    absentees.chunked(chunkSize).forEachIndexed { idx, chunk ->
                        val batch = firestore.batch()
                        chunk.forEach { roll ->
                            val userRef = usersCollection.document(roll)
                            val userUpdates = mutableMapOf<String, Any>(
                                "hours" to FieldValue.increment(-negativeHours as Double)
                            )
                            when (semester) {
                                1 -> userUpdates["sem1Hours"] = FieldValue.increment(-negativeHours as Double)
                                2 -> userUpdates["sem2Hours"] = FieldValue.increment(-negativeHours as Double)
                            }
                            batch.update(userRef, userUpdates)
                        }
                        batch.commit().await()
                        Log.d(TAG, "Penalty batch ${idx + 1} committed with ${chunk.size} users")
                    }

                    // Persist metadata to support future delta adjustments
                    eventsAttendanceCollection.document(eventId)
                        .update(
                            mapOf(
                                "absentPenaltyApplied" to true,
                                "penaltyHoursUsed" to negativeHours,
                                "absenteeRollNumbers" to absentees,
                                "absentPenaltyCount" to absentees.size
                            )
                        )
                        .await()

                    Log.d(TAG, "Absent penalties applied (first time) and metadata stored for event: $eventId")
                } else if (isMandatory && penaltyApplied) {
                    // Adjust existing penalties via delta if hours changed
                    val delta = negativeHours - previouslyUsedPenalty
                    Log.d(TAG, "Penalty delta check: previous=$previouslyUsedPenalty, current=$negativeHours, delta=$delta")
                    if (delta != 0.0) {
                        val semester = getSemesterFromDate(event.eventDate)
                        val targetRolls = if (storedAbsentees.isNotEmpty()) storedAbsentees else run {
                            // Fallback: recompute from all users and attendees
                            val attendeeRolls = event.attendees.map { it.rollNumber }.toSet()
                            val allUsers = usersCollection.get().await().documents
                            allUsers.map { it.id }.filter { it.isNotBlank() && !attendeeRolls.contains(it) }
                        }

                        val inc = -delta // if reduced hours (delta negative), this becomes positive to refund
                        Log.d(TAG, "Applying delta=$delta to ${targetRolls.size} users (increment=$inc)")

                        val chunkSize = 400
                        targetRolls.chunked(chunkSize).forEachIndexed { idx, chunk ->
                            val batch = firestore.batch()
                            chunk.forEach { roll ->
                                val userRef = usersCollection.document(roll)
                                val userUpdates = mutableMapOf<String, Any>(
                                    "hours" to FieldValue.increment(inc as Double)
                                )
                                when (semester) {
                                    1 -> userUpdates["sem1Hours"] = FieldValue.increment(inc as Double)
                                    2 -> userUpdates["sem2Hours"] = FieldValue.increment(inc as Double)
                                }
                                batch.update(userRef, userUpdates)
                            }
                            batch.commit().await()
                            Log.d(TAG, "Delta batch ${idx + 1} committed with ${chunk.size} users")
                        }

                        // Update metadata to reflect the new penalty hours
                        eventsAttendanceCollection.document(eventId)
                            .update(mapOf("penaltyHoursUsed" to negativeHours))
                            .await()
                        Log.d(TAG, "Penalty metadata updated to $negativeHours for event: $eventId")
                    }
                }
            }

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
