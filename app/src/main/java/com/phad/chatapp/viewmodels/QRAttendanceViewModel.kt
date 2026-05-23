package com.phad.chatapp.viewmodels

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.models.AttendanceSession
import com.phad.chatapp.models.AttendeeRecord
import com.phad.chatapp.models.QRAttendanceData
import com.phad.chatapp.repositories.AttendanceQRRepository
import com.phad.chatapp.services.QRAttendanceService
import com.phad.chatapp.services.QRValidationResult
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.utils.DeviceIdentificationUtils
import com.phad.chatapp.utils.DuplicateType

import com.phad.chatapp.utils.PDFGenerator
import com.phad.chatapp.utils.AttendanceStatsUpdater
import com.phad.chatapp.utils.QRSecurityValidator
import com.phad.chatapp.services.LocationService
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import android.location.Location
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * ViewModel for managing QR-based attendance system
 * Follows MVVM pattern established in the app
 */
class QRAttendanceViewModel(private val application: Application) : ViewModel() {
    private val TAG = "QRAttendanceViewModel"
    
    // Dependencies
    private val repository = AttendanceQRRepository()
    private val qrService = QRAttendanceService()
    private val sessionManager = SessionManager(application)
    
    init {
        // Set SessionManager in repository for token refresh functionality
        repository.setSessionManager(sessionManager)
    }
    
    // UI State for Admin (Take Attendance)
    private val _adminUiState = MutableStateFlow(AdminQRUiState())
    val adminUiState: StateFlow<AdminQRUiState> = _adminUiState.asStateFlow()
    
    // UI State for Student (Give Attendance)
    private val _studentUiState = MutableStateFlow(StudentQRUiState())
    val studentUiState: StateFlow<StudentQRUiState> = _studentUiState.asStateFlow()
    
    // Current QR generation job
    private var qrGenerationJob: Job? = null
    
    // Current session listener job
    private var sessionListenerJob: Job? = null

    // Periodic student location refresh while on scan screen
    private var studentLocationJob: Job? = null

    fun startStudentLocationUpdates() {
        if (studentLocationJob?.isActive == true) return
        val locationService = LocationService(application)
        studentLocationJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val deferred = CompletableDeferred<android.location.Location?>()
                    // Force a fresh high-accuracy fix each tick
                    locationService.getFreshHighAccuracyLocation { loc -> deferred.complete(loc) }
                    val loc = withTimeout(30000) { deferred.await() }
                    if (loc != null) {
                        val gp = GeoPoint(loc.latitude, loc.longitude)
                        val now = System.currentTimeMillis()
                        val current = _studentUiState.value
                        _studentUiState.value = current.copy(
                            lastLocation = gp,
                            lastLocationTimestampMs = now
                        )
                    } else {
                    }
                } catch (_: Exception) {
                }
                delay(10_000)
            }
        }
    }

    fun stopStudentLocationUpdates() {
        studentLocationJob?.cancel()
        studentLocationJob = null
    }
    
    init {
        Log.d(TAG, "QRAttendanceViewModel initialized")
        loadUserInfo()
    }


    /** Clear roll operation results dialog */
    fun clearRollResults() {
        _adminUiState.value = _adminUiState.value.copy(
            rollResults = emptyList(),
            showRollResults = false,
            rollOperationTitle = null
        )
    }
    
    /**
     * Load current user information
     */
    private fun loadUserInfo() {
        val userType = sessionManager.fetchUserType()
        val userId = sessionManager.fetchUserId()
        val userName = sessionManager.fetchUserName()

        Log.d(TAG, "User info - Type: '$userType', ID: '$userId', Name: '$userName'")

        // Check admin status with detailed logging (case-insensitive)
        val isAdmin = userType.equals("Admin", ignoreCase = true)
        Log.d(TAG, "Admin check: userType='$userType', isAdmin=$isAdmin")
        Log.d(TAG, "Admin check: ${userType.equals("Admin", ignoreCase = true)}")

        _adminUiState.value = _adminUiState.value.copy(
            adminId = userId,
            adminName = userName,
            isAdmin = isAdmin
        )

        _studentUiState.value = _studentUiState.value.copy(
            studentId = userId,
            studentName = userName,
            isStudent = userType.equals("Student", ignoreCase = true)
        )

        // Log final student UI state
        Log.d(TAG, "Final StudentQRUiState: studentId='${_studentUiState.value.studentId}', studentName='${_studentUiState.value.studentName}', isStudent=${_studentUiState.value.isStudent}")

        // Log final UI state
        Log.d(TAG, "Final AdminQRUiState: isAdmin=${_adminUiState.value.isAdmin}, isSessionActive=${_adminUiState.value.isSessionActive}")
    }
    
    /**
     * Load available events for attendance
     */
    fun loadAvailableEvents(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                _adminUiState.value = _adminUiState.value.copy(isLoading = true)
                
                val result = repository.getAvailableEvents(forceRefresh)
                if (result.isSuccess) {
                    val events = result.getOrNull() ?: emptyList()
                    _adminUiState.value = _adminUiState.value.copy(
                        availableEvents = events,
                        isLoading = false
                    )
                    Log.d(TAG, "Loaded ${events.size} available events (forceRefresh=$forceRefresh)")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to load events"
                    _adminUiState.value = _adminUiState.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                    Log.e(TAG, "Error loading events: $error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading events", e)
                _adminUiState.value = _adminUiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Unknown error"
                )
            }
        }
    }
    
    /**
     * Force refresh available events (bypasses cache)
     */
    fun refreshAvailableEvents() {
        loadAvailableEvents(forceRefresh = true)
    }

    /**
     * Create a new attendance event
     */
    fun createAttendanceEvent(name: String, description: String, location: String, eventDate: java.util.Date, openingTime: java.util.Date, closingTime: java.util.Date, hours: Double, isMandatory: Boolean = false, negativeHours: Double = 0.0, wings: List<String> = emptyList(), visibleOnlyToPresent: Boolean = false) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Creating attendance event: $name")
                _adminUiState.value = _adminUiState.value.copy(
                    isCreatingEvent = true,
                    errorMessage = null
                )

                // Generate document ID using the new format
                val documentId = com.phad.chatapp.utils.AttendanceEventUtils.generateDocumentId(eventDate, name)

                // Create new date/time format data
                val (dateString, timeRangeString) = com.phad.chatapp.utils.AttendanceEventUtils.createNewFormatEventTimeData(eventDate, openingTime, closingTime)

                Log.d(TAG, "Creating event with:")
                Log.d(TAG, "  Date string: $dateString")
                Log.d(TAG, "  Time range: $timeRangeString")
                Log.d(TAG, "  Wings: $wings")

                val event = AttendanceEvent(
                    id = documentId,
                    eventDate = dateString,
                    eventTime = timeRangeString,
                    hours = hours,
                    isMandatory = isMandatory,
                    negativeHours = negativeHours,
                    location = location.trim(),
                    description = description.trim(),
                    wings = wings,
                    createdBy = _adminUiState.value.adminId,
                    creatorName = _adminUiState.value.adminName,
                    createdAt = com.google.firebase.Timestamp.now(),
                    attendees = emptyList(),
                    closedAt = null,
                    _isLive = true, // Explicitly set to true for new events
                    visibleOnlyToPresent = visibleOnlyToPresent
                )

                val result = repository.createAttendanceEvent(event)
                result.fold(
                    onSuccess = { eventId ->
                        Log.d(TAG, "Event created successfully with ID: $eventId")
                        
                        // Send Notification for the event
                        // VOTA (Visible Only To Attendees) events are private — skip notifications
                        // entirely to avoid confusion for users who aren't attending.
                        if (!visibleOnlyToPresent) {
                            try {
                                val eventType = if (isMandatory) "Mandatory Event" else "Event"
                                val title = "$eventType: $name"
                                val message = "A new ${if (isMandatory) "mandatory " else ""}event '$name' has been scheduled on $dateString from $timeRangeString. Location: $location"

                                // An event is "open" (targets all) if wings list covers all wings by SIZE >= 6 or if it's a Design and Curation Wing event
                                val isOpenEvent = wings.isEmpty() || wings.size >= com.phad.chatapp.models.AttendanceEvent.ALL_WINGS.size || wings.contains("Design and Curation Wing")
                                val targetTopics = if (isOpenEvent) {
                                    listOf("all")
                                } else {
                                    wings.map { "wing_" + it.lowercase().replace(" ", "_").replace("&", "and") }
                                }

                                val notificationData = hashMapOf<String, Any>(
                                    "title" to title,
                                    "body" to message,
                                    "targetRole" to "all",
                                    "targetWing" to "all",
                                    "targetTopics" to targetTopics,
                                    "type" to "EVENT_NOTIFICATION",
                                    "creatorId" to _adminUiState.value.adminId,
                                    "isRead" to false,
                                    "timestamp" to com.google.firebase.Timestamp.now()
                                )
                                com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("app_notifications").add(notificationData)
                                    .addOnSuccessListener { Log.d(TAG, "Successfully created app_notification for event broadcast") }
                                    .addOnFailureListener { e -> Log.e(TAG, "Failed to create app_notification for event", e) }

                                // Trigger Vercel FCM Push
                                targetTopics.forEach { topic ->
                                    viewModelScope.launch {
                                        com.phad.chatapp.utils.FcmSender.sendToTopic(
                                            topic = topic,
                                            title = title,
                                            body = message
                                        )
                                    }
                                }
                                // Schedule 1-hour reminder for mandatory events
                                if (isMandatory) {
                                    val delayMs = openingTime.time - System.currentTimeMillis() - (60 * 60 * 1000L)
                                    if (delayMs > 0) {
                                        val reminderTitle = "⏰ Mandatory Event Starting Soon: $name"
                                        val reminderBody = "The mandatory event '$name' starts in 1 hour at $timeRangeString. Location: $location"
                                        val reminderWork = OneTimeWorkRequestBuilder<com.phad.chatapp.workers.EventReminderWorker>()
                                            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                                            .setInputData(workDataOf(
                                                "title" to reminderTitle,
                                                "body" to reminderBody,
                                                "topics" to targetTopics.joinToString(",")
                                            ))
                                            .addTag("event_reminder_$eventId")
                                            .build()
                                        WorkManager.getInstance(application).enqueue(reminderWork)
                                        Log.d(TAG, "Scheduled 1-hr reminder for mandatory event '$name' in ${delayMs / 60000} min")
                                    } else {
                                        Log.d(TAG, "Skipping reminder: event starts in less than 1 hour")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error creating event notification or reminder", e)
                            }
                        } else {
                            Log.d(TAG, "Skipping notification for VOTA event '$name' — visible only to attendees")
                        }

                        _adminUiState.value = _adminUiState.value.copy(
                            isCreatingEvent = false,
                            showCreateEventDialog = false,
                            createEventSuccess = true,
                            errorMessage = null
                        )
                        // Force refresh events list to show the newly created event immediately
                        loadAvailableEvents(forceRefresh = true)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error creating event", error)
                        _adminUiState.value = _adminUiState.value.copy(
                            isCreatingEvent = false,
                            errorMessage = "Failed to create event: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error creating event", e)
                _adminUiState.value = _adminUiState.value.copy(
                    isCreatingEvent = false,
                    errorMessage = "Error creating event: ${e.message}"
                )
            }
        }
    }

    /**
     * Show create event dialog
     */
    fun showCreateEventDialog() {
        _adminUiState.value = _adminUiState.value.copy(
            showCreateEventDialog = true,
            createEventSuccess = false,
            errorMessage = null
        )
    }

    /**
     * Hide create event dialog
     */
    fun hideCreateEventDialog() {
        _adminUiState.value = _adminUiState.value.copy(
            showCreateEventDialog = false,
            createEventSuccess = false,
            errorMessage = null
        )
    }

    /**
     * Clear create event success state
     */
    fun clearCreateEventSuccess() {
        _adminUiState.value = _adminUiState.value.copy(
            createEventSuccess = false
        )
    }

    /**
     * Show edit event dialog
     */
    fun showEditEventDialog(event: AttendanceEvent) {
        _adminUiState.value = _adminUiState.value.copy(
            showEditEventDialog = true,
            editingEvent = event,
            editEventSuccess = false,
            errorMessage = null
        )
    }

    /**
     * Hide edit event dialog
     */
    fun hideEditEventDialog() {
        _adminUiState.value = _adminUiState.value.copy(
            showEditEventDialog = false,
            editingEvent = null,
            editEventSuccess = false,
            errorMessage = null
        )
    }

    /**
     * Update an existing attendance event
     */
    fun updateAttendanceEvent(name: String, description: String, location: String, eventDate: java.util.Date, openingTime: java.util.Date, closingTime: java.util.Date, hours: Double, isMandatory: Boolean, negativeHours: Double, wings: List<String>, visibleOnlyToPresent: Boolean, eventId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Updating attendance event: $name (ID: $eventId)")
                _adminUiState.value = _adminUiState.value.copy(
                    isUpdatingEvent = true,
                    errorMessage = null
                )

                // Get the current event to check if name or date changed
                val currentEvent = _adminUiState.value.editingEvent
                if (currentEvent == null) {
                    _adminUiState.value = _adminUiState.value.copy(
                        isUpdatingEvent = false,
                        errorMessage = "No event selected for update"
                    )
                    return@launch
                }

                // Generate new document ID using the updated name and date
                val newDocumentId = com.phad.chatapp.utils.AttendanceEventUtils.generateDocumentId(eventDate, name)
                
                // Check if name or date changed (which requires recreation)
                val nameChanged = currentEvent.getEventName() != name.trim()
                val dateChanged = currentEvent.getEventDateAsDate() != eventDate
                val needsRecreation = nameChanged || dateChanged

                // Create new date/time format data
                val (dateString, timeRangeString) = com.phad.chatapp.utils.AttendanceEventUtils.createNewFormatEventTimeData(eventDate, openingTime, closingTime)

                val updatedEvent = currentEvent.copy(
                    id = newDocumentId,
                    eventDate = dateString,
                    eventTime = timeRangeString,
                    hours = hours,
                    isMandatory = isMandatory,
                    negativeHours = negativeHours,
                    location = location.trim(),
                    description = description.trim(),
                    wings = wings,
                    visibleOnlyToPresent = visibleOnlyToPresent
                )

                val result = if (needsRecreation) {
                    Log.d(TAG, "Name or date changed, recreating event: $eventId -> $newDocumentId")
                    repository.recreateAttendanceEvent(eventId, updatedEvent)
                } else {
                    Log.d(TAG, "Only other fields changed, updating in place")
                    repository.updateAttendanceEvent(updatedEvent)
                }

                result.fold(
                    onSuccess = { newEventId ->
                        Log.d(TAG, "Event ${if (needsRecreation) "recreated" else "updated"} successfully: $eventId -> $newEventId")
                        _adminUiState.value = _adminUiState.value.copy(
                            isUpdatingEvent = false,
                            showEditEventDialog = false,
                            editingEvent = null,
                            editEventSuccess = true,
                            errorMessage = null
                        )
                        // Invalidate caches and force refresh to reflect edits immediately
                        repository.invalidateEventCaches()
                        loadAvailableEvents(forceRefresh = true)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error ${if (needsRecreation) "recreating" else "updating"} event", error)
                        _adminUiState.value = _adminUiState.value.copy(
                            isUpdatingEvent = false,
                            errorMessage = "Failed to ${if (needsRecreation) "recreate" else "update"} event: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error updating event", e)
                _adminUiState.value = _adminUiState.value.copy(
                    isUpdatingEvent = false,
                    errorMessage = "Error updating event: ${e.message}"
                )
            }
        }
    }

    /**
     * Force refresh user info and UI state (for debugging)
     */
    fun refreshUserInfo() {
        Log.d(TAG, "Force refreshing user info...")
        loadUserInfo()
    }

    /**
     * Start attendance session for selected event (now simplified for consolidated schema)
     */
    fun startAttendanceSession(event: AttendanceEvent) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting attendance session for event: ${event.getEventName()}")

                _adminUiState.value = _adminUiState.value.copy(isLoading = true)

                // In the consolidated schema, we don't create separate session documents
                // The event itself serves as the "session"
                val sessionId = event.id // Use event ID as session ID

                // Enforce GPS must be ON to start attendance
                Log.d(TAG, "Capturing GPS location for attendance session (mandatory)")
                val locationService = LocationService(application)
                val locationDeferred = kotlinx.coroutines.CompletableDeferred<android.location.Location?>()
                locationService.getCurrentLocation { location ->
                    locationDeferred.complete(location)
                }
                val adminLocation = try {
                    withTimeout(30000) { // 30s timeout
                        locationDeferred.await()
                    }
                } catch (e: TimeoutCancellationException) {
                    null
                }

                if (adminLocation == null) {
                    Log.w(TAG, "❌ Start Attendance blocked: location unavailable or permission off")
                    _adminUiState.value = _adminUiState.value.copy(
                        isLoading = false,
                        errorMessage = "Turn on location and grant permission to start attendance"
                    )
                    return@launch
                }

                // Persist event location; if this fails, do not start session
                val adminIdForLocation = _adminUiState.value.adminId
                val updateResult = repository.updateEventAttendanceLocation(
                    eventId = event.id,
                    latitude = adminLocation.latitude,
                    longitude = adminLocation.longitude,
                    setByAdminId = adminIdForLocation
                )
                if (updateResult.isFailure) {
                    Log.e(TAG, "❌ Failed to update event location: ${updateResult.exceptionOrNull()?.message}")
                    _adminUiState.value = _adminUiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to set event location. Please try again."
                    )
                    return@launch
                }

                _adminUiState.value = _adminUiState.value.copy(
                    selectedEvent = event,
                    isSessionActive = true,
                    isLoading = false
                )

                // Register session for security validation
                Log.d(TAG, "=== ADMIN ID FLOW DEBUG ===")
                Log.d(TAG, "Current admin ID from UI state: ${_adminUiState.value.adminId}")
                Log.d(TAG, "Event created by: ${event.createdBy}")
                Log.d(TAG, "Event creator name: ${event.creatorName}")
                Log.d(TAG, "Session manager user ID: ${sessionManager.fetchUserId()}")
                Log.d(TAG, "Session manager user type: ${sessionManager.fetchUserType()}")
                Log.d(TAG, "Session manager user name: ${sessionManager.fetchUserName()}")
                Log.d(TAG, "============================")

                Log.d(TAG, "Registering session - SessionId: $sessionId, AdminId: ${_adminUiState.value.adminId}, EventId: ${event.id}")
                qrService.registerSession(sessionId, _adminUiState.value.adminId, event.id)
                Log.d(TAG, "Session registration completed")
                
                // Verify session registration immediately
                val sessionInfo = QRSecurityValidator.getInstance().getSessionInfo(sessionId)
                if (sessionInfo != null) {
                    Log.d(TAG, "✅ Session registration verified immediately:")
                    Log.d(TAG, "  - SessionId: ${sessionInfo.sessionId}")
                    Log.d(TAG, "  - AdminId: '${sessionInfo.adminId}'")
                    Log.d(TAG, "  - EventId: '${sessionInfo.eventId}'")
                    Log.d(TAG, "  - IsActive: ${sessionInfo.isActive}")
                } else {
                    Log.e(TAG, "❌ Session registration verification failed - session not found in cache")
                    Log.d(TAG, "This indicates a session registration issue")
                }
                
                // Add a small delay to ensure session registration is complete before QR generation
                delay(500) // 500ms delay to allow for session registration to complete
                Log.d(TAG, "Delay completed, proceeding with QR generation")
                
                // Force re-register session to ensure it persists
                Log.d(TAG, "Force re-registering session to ensure persistence")
                qrService.registerSession(sessionId, _adminUiState.value.adminId, event.id)
                Log.d(TAG, "Session re-registration completed")
                
                // Verify session registration again after delay
                val sessionInfoAfterDelay = QRSecurityValidator.getInstance().getSessionInfo(sessionId)
                if (sessionInfoAfterDelay != null) {
                    Log.d(TAG, "✅ Session still exists after delay:")
                    Log.d(TAG, "  - SessionId: ${sessionInfoAfterDelay.sessionId}")
                    Log.d(TAG, "  - AdminId: '${sessionInfoAfterDelay.adminId}'")
                    Log.d(TAG, "  - EventId: '${sessionInfoAfterDelay.eventId}'")
                    Log.d(TAG, "  - IsActive: ${sessionInfoAfterDelay.isActive}")
                } else {
                    Log.e(TAG, "❌ Session disappeared after delay - this is the problem!")
                }

                // Start QR code generation
                // Use event creator's ID for QR codes, not current admin's ID
                // This ensures QR codes work even if started by a different admin
                Log.d(TAG, "Starting QR generation with SessionId: $sessionId, EventId: ${event.id}")
                Log.d(TAG, "Using event creator ID for QR: ${event.createdBy} (not current admin: ${_adminUiState.value.adminId})")
                startQRGeneration(sessionId, event.id, event.createdBy)

                // Start listening to event updates (instead of session updates)
                startEventListener(event.id)

                Log.d(TAG, "Attendance session started successfully for consolidated event")
            } catch (e: Exception) {
                Log.e(TAG, "Exception starting session", e)
                _adminUiState.value = _adminUiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Unknown error"
                )
            }
        }
    }
    
    /**
     * Start dynamic QR code generation
     * @param adminId The admin ID to use in QR codes (should be event creator's ID)
     */
    private fun startQRGeneration(sessionId: String, eventId: String, adminId: String) {
        qrGenerationJob?.cancel()
        qrGenerationJob = viewModelScope.launch {
            try {
                qrService.generateDynamicQRCodes(
                    sessionId = sessionId,
                    eventId = eventId,
                    adminId = adminId // Use event creator's ID, not current admin's ID
                ).collectLatest { (qrData, bitmap) ->
                    _adminUiState.value = _adminUiState.value.copy(
                        currentQRCode = bitmap,
                        currentQRData = qrData,
                        qrRefreshCount = _adminUiState.value.qrRefreshCount + 1
                    )

                    // Update repository with new QR ID
                    repository.updateSessionQRCode(sessionId, qrData.qrId)

                    Log.d(TAG, "QR code updated - ID: ${qrData.qrId}")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Don't show error for intentional cancellation (e.g., when ending session)
                Log.d(TAG, "QR generation cancelled (intentional)")
                throw e // Re-throw to properly handle coroutine cancellation
            } catch (e: Exception) {
                // Only show error for actual failures, not cancellation
                Log.e(TAG, "Error in QR generation", e)
                _adminUiState.value = _adminUiState.value.copy(
                    errorMessage = "QR generation failed: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Start listening to event updates for real-time attendance count
     */
    private fun startEventListener(eventId: String) {
        sessionListenerJob?.cancel()
        sessionListenerJob = viewModelScope.launch {
            try {
                repository.listenToAttendanceEvent(eventId).collectLatest { event ->
                    event?.let {
                        _adminUiState.value = _adminUiState.value.copy(
                            selectedEvent = it,
                            attendeeCount = it.totalMarked
                        )
                        Log.d(TAG, "Event updated - Attendees: ${it.totalMarked}")
                        Log.d(TAG, "Event attendees list: ${it.attendees.map { "${it.rollNumber} (${it.name})" }}")
                        Log.d(TAG, "Event attendees count: ${it.attendees.size}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listening to event updates", e)
            }
        }
    }

    /**
     * Legacy method for backward compatibility
     */
    @Deprecated("Use startEventListener instead")
    private fun startSessionListener(sessionId: String) {
        startEventListener(sessionId)
    }
    
    /**
     * End current attendance session
     */
    fun endAttendanceSession() {
        viewModelScope.launch {
            try {
                // Use selectedEvent.id as sessionId since in consolidated schema, event ID = session ID
                val sessionId = _adminUiState.value.selectedEvent?.id
                if (sessionId != null) {
                    Log.d(TAG, "Ending attendance session: $sessionId")

                    // Clear any existing error messages first to prevent stale errors from showing
                    _adminUiState.value = _adminUiState.value.copy(errorMessage = null)

                    // Stop QR generation and session listener FIRST to prevent cancellation errors
                    qrGenerationJob?.cancel()
                    sessionListenerJob?.cancel()

                    // End session in security validator
                    qrService.endSession(sessionId)

                    val result = repository.endAttendanceSession(sessionId)
                    if (result.isSuccess) {
                        _adminUiState.value = _adminUiState.value.copy(
                            isSessionActive = false,
                            selectedEvent = null,
                            currentQRCode = null,
                            currentQRData = null,
                            errorMessage = null // Ensure no error messages remain
                        )

                        Log.d(TAG, "Attendance session ended successfully")

                        // Invalidate caches and force-refresh events so the list reflects latest counts/state
                        repository.invalidateEventCaches()
                        loadAvailableEvents(forceRefresh = true)
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Failed to end session"
                        _adminUiState.value = _adminUiState.value.copy(errorMessage = error)
                        Log.e(TAG, "Error ending session: $error")
                    }
                } else {
                    Log.w(TAG, "No active session to end - selectedEvent is null")
                    // Still update UI state to ensure clean state
                    qrGenerationJob?.cancel()
                    sessionListenerJob?.cancel()

                    _adminUiState.value = _adminUiState.value.copy(
                        isSessionActive = false,
                        selectedEvent = null,
                        currentQRCode = null,
                        currentQRData = null,
                        errorMessage = null // Clear any existing errors
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception ending session", e)
                _adminUiState.value = _adminUiState.value.copy(
                    errorMessage = e.message ?: "Unknown error"
                )
            }
        }
    }
    
    /**
     * Process scanned QR code for student attendance
     */
    fun processScannedQR(qrText: String) {
        viewModelScope.launch {
            try {
                val processingStartTime = System.currentTimeMillis()
                Log.d(TAG, "Processing scanned QR code for student: ${_studentUiState.value.studentId}")
                Log.d(TAG, "QR Text length: ${qrText.length}")
                Log.d(TAG, "QR Text (first 200 chars): ${qrText.take(200)}")
                Log.d(TAG, "Processing started at: $processingStartTime")
                
                // Debug: Check session cache status before validation
                Log.d(TAG, "=== DEBUG: SESSION CACHE STATUS BEFORE VALIDATION ===")
                QRSecurityValidator.getInstance().debugSessionCache()
                Log.d(TAG, "=== DEBUG: SESSION CACHE STATUS BEFORE VALIDATION END ===")

                // Reset state for new processing
                _studentUiState.value = _studentUiState.value.copy(
                    isProcessing = true,
                    scanResult = null,
                    cameraExited = false,
                    navigatedToResult = false
                )

                val validationStartTime = System.currentTimeMillis()
                Log.d(TAG, "Starting QR validation at: $validationStartTime")
                Log.d(TAG, "Time from processing start to validation: ${validationStartTime - processingStartTime}ms")
                
                // Debug: Parse QR data to get session ID and check cache
                try {
                    val qrData = QRAttendanceData.fromJson(qrText)
                    if (qrData != null) {
                        Log.d(TAG, "=== DEBUG: CHECKING SPECIFIC SESSION IN CACHE ===")
                        Log.d(TAG, "QR Data parsed - SessionId: '${qrData.sessionId}', EventId: '${qrData.eventId}', AdminId: '${qrData.adminId}'")
                        
                        val sessionInfo = QRSecurityValidator.getInstance().getSessionInfo(qrData.sessionId)
                        if (sessionInfo != null) {
                            Log.d(TAG, "✅ Session found in cache before validation:")
                            Log.d(TAG, "  - SessionId: '${sessionInfo.sessionId}'")
                            Log.d(TAG, "  - AdminId: '${sessionInfo.adminId}'")
                            Log.d(TAG, "  - EventId: '${sessionInfo.eventId}'")
                            Log.d(TAG, "  - IsActive: ${sessionInfo.isActive}")
                        } else {
                            Log.w(TAG, "❌ Session NOT found in cache before validation: '${qrData.sessionId}'")
                            Log.d(TAG, "This is why validation will fail!")
                        }
                        Log.d(TAG, "=== DEBUG: CHECKING SPECIFIC SESSION IN CACHE END ===")
                    } else {
                        Log.e(TAG, "❌ Failed to parse QR data for debug")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing QR data for debug: ${e.message}")
                }

                val result = qrService.validateQRCode(qrText, _studentUiState.value.studentId)

                val validationEndTime = System.currentTimeMillis()
                Log.d(TAG, "QR validation completed at: $validationEndTime")
                Log.d(TAG, "Validation took: ${validationEndTime - validationStartTime}ms")
                Log.d(TAG, "Total processing time: ${validationEndTime - processingStartTime}ms")

                if (result.isSuccess) {
                    val validationResult = result.getOrNull()!!

                    Log.d(TAG, "QR validation result - Valid: ${validationResult.isValid}, Reason: ${validationResult.reason}")

                    if (validationResult.qrData != null) {
                        Log.d(TAG, "QR Data - SessionId: ${validationResult.qrData.sessionId}, EventId: ${validationResult.qrData.eventId}, AdminId: ${validationResult.qrData.adminId}")
                        Log.d(TAG, "QR Data - QrId: ${validationResult.qrData.qrId}, Timestamp: ${validationResult.qrData.timestamp}")
                        Log.d(TAG, "QR Data - Age: ${validationResult.qrData.getAgeInSeconds()}s, Remaining: ${validationResult.qrData.getRemainingValiditySeconds()}s")
                    }

                    if (validationResult.isValid && validationResult.qrData != null) {
                        Log.d(TAG, "QR validation successful, marking attendance")
                        // Mark attendance
                        markStudentAttendance(validationResult.qrData)
                    } else {
                        // QR validation failed - reject the scan attempt
                        _studentUiState.value = _studentUiState.value.copy(
                            isProcessing = false,
                            scanResult = ScanResult.Error(validationResult.reason)
                        )
                        Log.w(TAG, "QR validation failed: ${validationResult.reason}")

                        // Log security-related failures for monitoring
                        if (validationResult.reason.contains("expired", ignoreCase = true)) {
                            Log.w(TAG, "SECURITY: Expired QR code rejected - Age exceeded validity window")
                        } else if (validationResult.reason.contains("already used", ignoreCase = true)) {
                            Log.w(TAG, "SECURITY: Replay attack detected - QR code already used")
                        } else if (validationResult.reason.contains("Session has ended", ignoreCase = true)) {
                            // Deep diagnostics: event appears active but validator says ended
                            try {
                                val debugData = try { QRAttendanceData.fromJson(qrText) } catch (_: Exception) { null }
                                val sessionIdDbg = debugData?.sessionId
                                val eventIdDbg = debugData?.eventId
                                Log.w(TAG, "SESSION_ENDED DEBUG (ViewModel): sessionId='${sessionIdDbg}', eventId='${eventIdDbg}'")
                                // Dump validator cache for this session
                                if (sessionIdDbg != null) {
                                    val cacheInfo = QRSecurityValidator.getInstance().getSessionInfo(sessionIdDbg)
                                    if (cacheInfo != null) {
                                        Log.w(TAG, "Cache sessionInfo: adminId='${cacheInfo.adminId}', eventId='${cacheInfo.eventId}', start=${cacheInfo.startTime}, end=${cacheInfo.endTime}, isActive=${cacheInfo.isActive}")
                                    } else {
                                        Log.w(TAG, "Cache sessionInfo: null for session '${sessionIdDbg}'")
                                    }
                                }
                                // Fetch Firestore event with server read preferred
                                if (eventIdDbg != null) {
                                    val event = repository.getAttendanceEventForDuplicateCheck(eventIdDbg).getOrNull()
                                        ?: repository.getAttendanceEvent(eventIdDbg).getOrNull()
                                    if (event != null) {
                                        Log.w(TAG, "Firestore event: id='${event.id}', isLive=${event.isLive}, closedAt=${event.closedAt}, createdAt=${event.createdAt}, createdBy='${event.createdBy}'")
                                        Log.w(TAG, "Student side state: isStudent=${_studentUiState.value.isStudent}, lastLocTs=${_studentUiState.value.lastLocationTimestampMs}")
                                    } else {
                                        Log.w(TAG, "Firestore event fetch returned null for '${eventIdDbg}'")
                                    }
                                }
                                // Dump admin session active flag if set
                                Log.w(TAG, "Admin UI state: isSessionActive=${_adminUiState.value.isSessionActive}, selectedEventId='${_adminUiState.value.selectedEvent?.id}'")
                            } catch (e: Exception) {
                                Log.e(TAG, "SESSION_ENDED DEBUG (ViewModel) failed", e)
                            }
                        }
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Validation failed"
                    _studentUiState.value = _studentUiState.value.copy(
                        isProcessing = false,
                        scanResult = ScanResult.Error(error)
                    )
                    Log.e(TAG, "Error validating QR: $error", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception processing QR", e)
                _studentUiState.value = _studentUiState.value.copy(
                    isProcessing = false,
                    scanResult = ScanResult.Error(e.message ?: "Unknown error")
                )
            }
        }
    }
    
    /**
     * Mark student attendance in the session
     */
    private suspend fun markStudentAttendance(qrData: QRAttendanceData) {
        try {
            Log.d(TAG, "=== STARTING ATTENDANCE MARKING PROCESS ===")
            Log.d(TAG, "Student ID: ${_studentUiState.value.studentId}")
            Log.d(TAG, "Student Name: ${_studentUiState.value.studentName}")
            Log.d(TAG, "Session ID: ${qrData.sessionId}")
            Log.d(TAG, "Event ID: ${qrData.eventId}")
            Log.d(TAG, "QR Code ID: ${qrData.qrId}")

            // Validate student data
            if (_studentUiState.value.studentId.isBlank()) {
                Log.e(TAG, "Student ID is blank!")
                _studentUiState.value = _studentUiState.value.copy(
                    isProcessing = false,
                    scanResult = ScanResult.Error("Student ID not found")
                )
                return
            }

            // Get device ID for duplicate prevention
            val deviceId = DeviceIdentificationUtils.getDeviceId(application)
            Log.d(TAG, "Device ID: ${deviceId.take(16)}...")

            // Comprehensive duplicate check (user + device)
            try {
                Log.d(TAG, "=== FETCHING EVENT FOR DUPLICATE CHECK ===")
                Log.d(TAG, "Event ID: ${qrData.eventId}")
                Log.d(TAG, "Session ID: ${qrData.sessionId}")
                Log.d(TAG, "Student ID: ${_studentUiState.value.studentId}")
                
                // Get the event to check existing attendees (force server read for fresh data)
                val eventResult = repository.getAttendanceEventForDuplicateCheck(qrData.eventId)
                if (eventResult.isSuccess) {
                    val event = eventResult.getOrNull()
                    if (event != null) {
                        val existingAttendees = event.attendees
                        Log.d(TAG, "Event fetched successfully")
                        Log.d(TAG, "Event attendees count: ${existingAttendees.size}")
                        Log.d(TAG, "Event attendees: ${existingAttendees.map { "${it.rollNumber} (${it.name})" }}")
                        
                        val duplicateCheck = QRSecurityValidator.getInstance().checkComprehensiveDuplicate(
                            qrData.sessionId,
                            _studentUiState.value.studentId,
                            deviceId,
                            existingAttendees
                        )
                        
                        if (duplicateCheck.isDuplicate) {
                            Log.w(TAG, "Duplicate attendance attempt blocked: ${duplicateCheck.duplicateType}")
                            Log.w(TAG, "Blocking message: ${duplicateCheck.message}")
                            _studentUiState.value = _studentUiState.value.copy(
                                isProcessing = false,
                                // Treat already-marked attendance as a non-error outcome for better UX
                                scanResult = ScanResult.Success(duplicateCheck.message)
                            )
                            return
                        } else {
                            Log.d(TAG, "No duplicates found, proceeding with attendance marking")
                        }
                    } else {
                        Log.w(TAG, "Event is null after successful fetch")
                    }
                } else {
                    Log.w(TAG, "Failed to get event for duplicate check: ${eventResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected error during comprehensive duplicate check", e)
            }

            // Location verification for QR scanning (mandatory)
            Log.d(TAG, "=== LOCATION VERIFICATION FOR QR SCAN ===")
            var scanLocation: Location? = null
            var locationGeoPoint: GeoPoint? = null
            
            // Prefer cached location if it's fresh (<= 10s); else fetch once
            val nowTs = System.currentTimeMillis()
            val cached = _studentUiState.value.lastLocation
            val cachedTs = _studentUiState.value.lastLocationTimestampMs
            if (cached != null && (nowTs - cachedTs) <= 10_000) {
                val loc = Location("cached")
                loc.latitude = cached.latitude
                loc.longitude = cached.longitude
                scanLocation = loc
                locationGeoPoint = GeoPoint(cached.latitude, cached.longitude)
                Log.d(TAG, "Using cached location (fresh): lat=${loc.latitude}, lng=${loc.longitude}")
            } else {
                val locationService = LocationService(application)
                try {
                    // Retry loop for location fetching
                    var attempt = 1
                    while (attempt <= 2 && scanLocation == null) {
                        try {
                            if (attempt > 1) {
                                // Inform user about retry if possible, or just log
                                Log.d(TAG, "Refining location... Attempt $attempt")
                                // Wait a bit before retry to let GPS warm up
                                delay(1000) 
                            }
                            
                            val locationDeferred = CompletableDeferred<Location?>()
                            locationService.getFreshHighAccuracyLocation { location ->
                                locationDeferred.complete(location)
                            }
                            scanLocation = withContext(Dispatchers.IO) {
                                try {
                                    withTimeout(30000) { locationDeferred.await() }
                                } catch (e: TimeoutCancellationException) { null }
                            }
                            
                            if (scanLocation != null) {
                                locationGeoPoint = GeoPoint(scanLocation.latitude, scanLocation.longitude)
                                Log.d(TAG, "Location captured on attempt $attempt: lat=${scanLocation.latitude}, lng=${scanLocation.longitude}")
                                break // Success!
                            } else {
                                Log.w(TAG, "Attempt $attempt failed to capture location")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in location attempt $attempt: ${e.message}")
                        }
                        attempt++
                    }

                    if (scanLocation == null) {
                        Log.w(TAG, "Could not capture location after 2 attempts - blocking attendance")
                        
                        // Check if location is actually disabled to show correct error
                        val locationManager = application.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                        val isLocationEnabled = try {
                            locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) || 
                            locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                        } catch (e: Exception) {
                            false
                        }
                        
                        val errorMessage = if (!isLocationEnabled) {
                            "Turn on location and grant permission to mark attendance"
                        } else {
                            "Weak GPS signal. Please move near a window or open area and try again."
                        }
                        
                        _studentUiState.value = _studentUiState.value.copy(
                            isProcessing = false,
                            scanResult = ScanResult.Error(errorMessage)
                        )
                        return
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting location: ${e.message}", e)
                    _studentUiState.value = _studentUiState.value.copy(
                        isProcessing = false,
                        scanResult = ScanResult.Error("Unable to get location: ${e.message}")
                    )
                    return
                }
            }
            
            // Verify location proximity (only if event has location set)
            val eventForLocationCheck = try {
                repository.getAttendanceEventForDuplicateCheck(qrData.eventId).getOrNull()
            } catch (e: Exception) {
                null
            }
            
            if (eventForLocationCheck != null) {
                val eventLat = eventForLocationCheck.attendanceLocationLatitude
                val eventLng = eventForLocationCheck.attendanceLocationLongitude
                
                if (eventLat != null && eventLng != null) {
                    // Event has location set - verify proximity
                    val eventLocation = QRSecurityValidator.getInstance().createLocation(eventLat, eventLng)
                    
                    // DEBUG: Log location details
                    Log.d(TAG, "=== LOCATION VALIDATION DEBUG ===")
                    Log.d(TAG, "Student Location: lat=${scanLocation.latitude}, lng=${scanLocation.longitude}, accuracy=±${scanLocation.accuracy}m")
                    Log.d(TAG, "Event Location: lat=${eventLat}, lng=${eventLng}")
                    Log.d(TAG, "Max Allowed Distance: 100m")
                    
                    val locationValidation = QRSecurityValidator.getInstance().validateLocation(
                        scanLocation = scanLocation,
                        eventLocation = eventLocation,
                        maxRadiusMeters = 100f // Changed from 3f to 100f for indoor GPS accuracy
                    )
                    
                    if (!locationValidation.isValid) {
                        Log.w(TAG, "❌ Location verification failed: ${locationValidation.message}")
                        Log.w(TAG, "Location validation code: ${locationValidation.code}")
                        _studentUiState.value = _studentUiState.value.copy(
                            isProcessing = false,
                            scanResult = ScanResult.Error(locationValidation.message)
                        )
                        return
                    }
                    
                    Log.d(TAG, "✅ Location validation passed")
                    
                    Log.d(TAG, "✅ Location verification passed")
                } else {
                    Log.w(TAG, "Event location not set - blocking attendance until admin sets location")
                    _studentUiState.value = _studentUiState.value.copy(
                        isProcessing = false,
                        scanResult = ScanResult.Error("Event location not set. Ask admin to start attendance with location ON.")
                    )
                    return
                }
            } else {
                Log.w(TAG, "Could not fetch event for location check - blocking attendance")
                _studentUiState.value = _studentUiState.value.copy(
                    isProcessing = false,
                    scanResult = ScanResult.Error("Unable to fetch event for location check. Please try again.")
                )
                return
            }

            // Get admin name from database using the admin ID from QR data
            val adminName = try {
                Log.d(TAG, "Fetching admin data for ID: ${qrData.adminId}")
                Log.d(TAG, "QR Data - SessionId: ${qrData.sessionId}, EventId: ${qrData.eventId}, AdminId: ${qrData.adminId}")
                
                // Try case-insensitive lookup first
                val adminResult = repository.getUserByRollNumberCaseInsensitive(qrData.adminId)
                if (adminResult.isSuccess) {
                    val adminData = adminResult.getOrNull()
                    Log.d(TAG, "Admin data retrieved: $adminData")
                    Log.d(TAG, "Admin data keys: ${adminData?.keys}")
                    
                    val name = adminData?.get("name") as? String
                    Log.d(TAG, "Extracted admin name: '$name'")
                    
                    if (name.isNullOrBlank()) {
                        Log.w(TAG, "Admin name is blank, trying alternative field names")
                        // Try alternative field names
                        val altName = adminData?.get("Name") as? String
                            ?: adminData?.get("student_name") as? String
                            ?: adminData?.get("full_name") as? String
                            ?: adminData?.get("studentName") as? String
                            ?: adminData?.get("fullName") as? String
                            ?: "Unknown Admin"
                        Log.d(TAG, "Alternative name found: '$altName'")
                        altName
                    } else {
                        name
                    }
                } else {
                    Log.w(TAG, "Failed to fetch admin data for ID: ${qrData.adminId}, error: ${adminResult.exceptionOrNull()?.message}")
                    "Unknown Admin"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching admin name for ID: ${qrData.adminId}", e)
                "Unknown Admin"
            }

            Log.d(TAG, "Admin lookup: adminId='${qrData.adminId}', adminName='$adminName'")
            
            // Additional validation: if the admin name matches the student name, something is wrong
            if (adminName == _studentUiState.value.studentName) {
                Log.w(TAG, "WARNING: Admin name matches student name! This suggests a data issue.")
                Log.w(TAG, "Student name: ${_studentUiState.value.studentName}")
                Log.w(TAG, "Admin name: $adminName")
                Log.w(TAG, "Admin ID: ${qrData.adminId}")
            }

            val attendee = AttendeeRecord(
                rollNumber = _studentUiState.value.studentId,
                name = _studentUiState.value.studentName.ifBlank { "Unknown Student" },
                scanTimestamp = com.google.firebase.Timestamp.now(),
                scannedFrom = com.phad.chatapp.models.ScannedFromAdmin(
                    adminRollNumber = qrData.adminId,
                    adminName = adminName
                ),
                deviceId = deviceId,
                scanLocation = locationGeoPoint, // GPS location where QR was scanned
                isManualEntry = false // QR scan, not manual entry
            )

            Log.d(TAG, "Created AttendeeRecord: rollNumber=${attendee.rollNumber}, studentName=${attendee.name}, deviceId=${attendee.deviceId}")
            Log.d(TAG, "AttendeeRecord validation: isDataValid=${attendee.isDataValid()}")

            Log.d(TAG, "Marking attendance directly to Firestore...")
            try {
                // Add attendee directly to Firestore (no Cloud Functions needed)
                val result = repository.addAttendeeToEvent(qrData.eventId, attendee)
                
                if (result.isSuccess) {
                    Log.d(TAG, "✅ Attendance marked successfully in Firestore")
                    
                    // Get event name for success message
                    val eventName = _adminUiState.value.selectedEvent?.getEventName() ?: "the event"
                    
                    _studentUiState.value = _studentUiState.value.copy(
                        isProcessing = false,
                        scanResult = ScanResult.Success("Attendance marked successfully for $eventName")
                    )
                    
                    // Update attendance stats in session
                    try {
                        AttendanceStatsUpdater.updateAttendanceStatsInSession(application)
                        Log.d(TAG, "✅ Attendance stats updated in session")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Failed to update attendance stats in session: ${e.message}")
                    }
                    
                    Log.d(TAG, "✅ Attendance marked successfully for ${_studentUiState.value.studentId}")
                } else {
                    // Firestore write failed
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "=== FIRESTORE ATTENDANCE MARKING FAILED ===")
                    Log.e(TAG, "Error Type: ${error?.javaClass?.simpleName}")
                    Log.e(TAG, "Error Message: ${error?.message}")
                    Log.e(TAG, "Student ID: ${_studentUiState.value.studentId}")
                    Log.e(TAG, "Event ID: ${qrData.eventId}")
                    Log.e(TAG, "Session ID: ${qrData.sessionId}")
                    if (error != null) {
                        Log.e(TAG, "Stack Trace:", error)
                    }
                    
                    _studentUiState.value = _studentUiState.value.copy(
                        isProcessing = false,
                        scanResult = ScanResult.Error(error?.message ?: "Failed to mark attendance")
                    )
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "=== EXCEPTION DURING ATTENDANCE MARKING ===")
                Log.e(TAG, "Error Type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Error Message: ${e.message}")
                Log.e(TAG, "Student ID: ${_studentUiState.value.studentId}")
                Log.e(TAG, "Event ID: ${qrData.eventId}")
                Log.e(TAG, "Session ID: ${qrData.sessionId}")
                Log.e(TAG, "Stack Trace:", e)
                
                _studentUiState.value = _studentUiState.value.copy(
                    isProcessing = false,
                    scanResult = ScanResult.Error(e.message ?: "Failed to mark attendance")
                )
                return
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception marking attendance", e)
            _studentUiState.value = _studentUiState.value.copy(
                isProcessing = false,
                scanResult = ScanResult.Error(e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * Clear error messages
     */
    fun clearError() {
        _adminUiState.value = _adminUiState.value.copy(errorMessage = null)
        _studentUiState.value = _studentUiState.value.copy(scanResult = null)
    }
    
    /**
     * Clear success message
     */
    fun clearSuccessMessage() {
        _adminUiState.value = _adminUiState.value.copy(successMessage = null)
    }

    // dismissSuccessDialog method removed - no longer needed with dedicated result screens

    /**
     * Mark that camera has been exited
     */
    fun markCameraExited() {
        _studentUiState.value = _studentUiState.value.copy(
            cameraExited = true
        )
    }

    /**
     * Mark that navigation to result screen has occurred
     */
    fun markNavigatedToResult() {
        _studentUiState.value = _studentUiState.value.copy(
            navigatedToResult = true
        )
    }

    // clearNavigationFlag method removed - no longer needed with dedicated result screens

    /**
     * Test method to verify attendance marking works
     */
    fun testAttendanceMarking() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== TESTING ATTENDANCE MARKING ===")

                // Create a test attendee record
                val testAttendee = AttendeeRecord(
                    rollNumber = "TEST123",
                    name = "Test Student"
                )

                // Get the current session ID from admin UI state (use selectedEvent.id)
                val selectedEvent = _adminUiState.value.selectedEvent
                if (selectedEvent == null) {
                    Log.e(TAG, "No active session for testing")
                    return@launch
                }

                Log.d(TAG, "Testing with session: ${selectedEvent.id}")

                val result = repository.addAttendeeToSession(selectedEvent.id, testAttendee)
                if (result.isSuccess) {
                    Log.d(TAG, "✅ Test attendance marking successful")
                } else {
                    Log.e(TAG, "❌ Test attendance marking failed: ${result.exceptionOrNull()?.message}")
                }

                Log.d(TAG, "=== TEST COMPLETED ===")
            } catch (e: Exception) {
                Log.e(TAG, "Exception during test", e)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        qrGenerationJob?.cancel()
        sessionListenerJob?.cancel()
        Log.d(TAG, "QRAttendanceViewModel cleared")
    }


    
    /**
     * Debug method to check session cache status
     */
    fun debugSessionCache() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== SESSION CACHE DEBUG START ===")
                
                val selectedEvent = _adminUiState.value.selectedEvent
                if (selectedEvent != null) {
                    val sessionId = selectedEvent.id
                    Log.d(TAG, "Current selected event: ${selectedEvent.getEventName()}")
                    Log.d(TAG, "Session ID: $sessionId")
                    
                    // Check session in cache
                    val sessionInfo = QRSecurityValidator.getInstance().getSessionInfo(sessionId)
                    if (sessionInfo != null) {
                        Log.d(TAG, "✅ Session found in cache:")
                        Log.d(TAG, "  - SessionId: ${sessionInfo.sessionId}")
                        Log.d(TAG, "  - AdminId: '${sessionInfo.adminId}'")
                        Log.d(TAG, "  - EventId: '${sessionInfo.eventId}'")
                        Log.d(TAG, "  - IsActive: ${sessionInfo.isActive}")
                        Log.d(TAG, "  - StartTime: ${sessionInfo.startTime}")
                        Log.d(TAG, "  - EndTime: ${sessionInfo.endTime}")
                    } else {
                        Log.w(TAG, "❌ Session not found in cache: $sessionId")
                        
                        // Try to force refresh
                        Log.d(TAG, "Attempting to force refresh session cache...")
                        val refreshSuccess = QRSecurityValidator.getInstance().forceRefreshSessionCache(sessionId)
                        Log.d(TAG, "Force refresh result: $refreshSuccess")
                        
                        if (refreshSuccess) {
                            val refreshedSessionInfo = QRSecurityValidator.getInstance().getSessionInfo(sessionId)
                            Log.d(TAG, "✅ Session refreshed successfully:")
                            Log.d(TAG, "  - SessionId: ${refreshedSessionInfo?.sessionId}")
                            Log.d(TAG, "  - AdminId: '${refreshedSessionInfo?.adminId}'")
                            Log.d(TAG, "  - EventId: '${refreshedSessionInfo?.eventId}'")
                            Log.d(TAG, "  - IsActive: ${refreshedSessionInfo?.isActive}")
                        }
                    }
                } else {
                    Log.w(TAG, "No selected event - cannot check session cache")
                }
                
                Log.d(TAG, "=== SESSION CACHE DEBUG END ===")
            } catch (e: Exception) {
                Log.e(TAG, "Error in session cache debug", e)
            }
        }
    }
    
    /**
     * Debug method to clear session cache (for testing)
     */
    fun debugClearSessionCache() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== CLEARING SESSION CACHE ===")
                QRSecurityValidator.getInstance().clearAllSessions()
                Log.d(TAG, "Session cache cleared")
                Log.d(TAG, "=== SESSION CACHE CLEARED ===")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing session cache", e)
            }
        }
    }
    
    /**
     * Debug method to re-register current session
     */
    fun debugReregisterSession() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== RE-REGISTERING SESSION ===")
                
                val selectedEvent = _adminUiState.value.selectedEvent
                if (selectedEvent != null) {
                    val sessionId = selectedEvent.id
                    val adminId = _adminUiState.value.adminId
                    val eventId = selectedEvent.id
                    
                    Log.d(TAG, "Re-registering session - SessionId: $sessionId, AdminId: $adminId, EventId: $eventId")
                    qrService.registerSession(sessionId, adminId, eventId)
                    Log.d(TAG, "Session re-registration completed")
                    
                    // Verify registration
                    delay(100)
                    val sessionInfo = QRSecurityValidator.getInstance().getSessionInfo(sessionId)
                    if (sessionInfo != null) {
                        Log.d(TAG, "✅ Session re-registration verified:")
                        Log.d(TAG, "  - SessionId: ${sessionInfo.sessionId}")
                        Log.d(TAG, "  - AdminId: '${sessionInfo.adminId}'")
                        Log.d(TAG, "  - EventId: '${sessionInfo.eventId}'")
                        Log.d(TAG, "  - IsActive: ${sessionInfo.isActive}")
                    } else {
                        Log.e(TAG, "❌ Session re-registration verification failed")
                    }
                } else {
                    Log.w(TAG, "No selected event - cannot re-register session")
                }
                
                Log.d(TAG, "=== SESSION RE-REGISTRATION END ===")
            } catch (e: Exception) {
                Log.e(TAG, "Error re-registering session", e)
            }
        }
    }
    
    /**
     * Debug method to list all events in the database
     */
    fun debugListAllEvents() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== DEBUG: LISTING ALL EVENTS ===")
                QRSecurityValidator.getInstance().debugListAllEvents()
                Log.d(TAG, "=== DEBUG: LISTING ALL EVENTS COMPLETED ===")
            } catch (e: Exception) {
                Log.e(TAG, "Error listing all events", e)
            }
        }
    }
    
    /**
     * Debug method to check if a specific event exists
     */
    fun debugCheckEventExists(eventId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== DEBUG: CHECKING EVENT EXISTS ===")
                Log.d(TAG, "Checking if event exists: '$eventId'")
                
                val result = repository.getAttendanceEvent(eventId)
                if (result.isSuccess) {
                    val event = result.getOrNull()
                    if (event != null) {
                        Log.d(TAG, "✅ Event found:")
                        Log.d(TAG, "  - ID: '${event.id}'")
                        Log.d(TAG, "  - Name: '${event.getEventName()}'")
                        Log.d(TAG, "  - CreatedBy: '${event.createdBy}'")
                        Log.d(TAG, "  - IsLive: ${event.isLive}")
                        Log.d(TAG, "  - ClosedAt: ${event.closedAt}")
                    } else {
                        Log.w(TAG, "❌ Event not found: '$eventId'")
                    }
                } else {
                    Log.e(TAG, "❌ Error checking event: ${result.exceptionOrNull()?.message}")
                }
                
                Log.d(TAG, "=== DEBUG: CHECKING EVENT EXISTS COMPLETED ===")
            } catch (e: Exception) {
                Log.e(TAG, "Error checking event exists", e)
            }
        }
    }
    
    /**
     * Debug method to check available events for admin
     */
    fun debugCheckAvailableEvents() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== DEBUG: CHECKING AVAILABLE EVENTS ===")
                Log.d(TAG, "Current admin UI state:")
                Log.d(TAG, "  - AdminId: '${_adminUiState.value.adminId}'")
                Log.d(TAG, "  - AdminName: '${_adminUiState.value.adminName}'")
                Log.d(TAG, "  - IsAdmin: ${_adminUiState.value.isAdmin}")
                Log.d(TAG, "  - Available events count: ${_adminUiState.value.availableEvents.size}")
                
                _adminUiState.value.availableEvents.forEachIndexed { index, event ->
                    Log.d(TAG, "Available event $index:")
                    Log.d(TAG, "  - ID: '${event.id}'")
                    Log.d(TAG, "  - Name: '${event.getEventName()}'")
                    Log.d(TAG, "  - CreatedBy: '${event.createdBy}'")
                    Log.d(TAG, "  - IsLive: ${event.isLive}")
                    Log.d(TAG, "  - ClosedAt: ${event.closedAt}")
                }
                
                Log.d(TAG, "=== DEBUG: CHECKING AVAILABLE EVENTS COMPLETED ===")
            } catch (e: Exception) {
                Log.e(TAG, "Error checking available events", e)
            }
        }
    }
    
    /**
     * Debug method to check session cache status
     */
    fun debugSessionCacheStatus() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== DEBUG: SESSION CACHE STATUS ===")
                QRSecurityValidator.getInstance().debugSessionCache()
                Log.d(TAG, "=== DEBUG: SESSION CACHE STATUS COMPLETED ===")
            } catch (e: Exception) {
                Log.e(TAG, "Error checking session cache status", e)
            }
        }
    }
    
    /**
     * Debug method to check if a specific session exists in cache
     */
    fun debugCheckSessionInCache(sessionId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== DEBUG: CHECKING SESSION IN CACHE ===")
                Log.d(TAG, "Checking if session exists in cache: '$sessionId'")
                
                val sessionInfo = QRSecurityValidator.getInstance().getSessionInfo(sessionId)
                if (sessionInfo != null) {
                    Log.d(TAG, "✅ Session found in cache:")
                    Log.d(TAG, "  - SessionId: '${sessionInfo.sessionId}'")
                    Log.d(TAG, "  - AdminId: '${sessionInfo.adminId}'")
                    Log.d(TAG, "  - EventId: '${sessionInfo.eventId}'")
                    Log.d(TAG, "  - IsActive: ${sessionInfo.isActive}")
                    Log.d(TAG, "  - StartTime: ${sessionInfo.startTime}")
                } else {
                    Log.w(TAG, "❌ Session not found in cache: '$sessionId'")
                    Log.d(TAG, "This means the session was never registered or was cleared")
                }
                
                Log.d(TAG, "=== DEBUG: CHECKING SESSION IN CACHE COMPLETED ===")
            } catch (e: Exception) {
                Log.e(TAG, "Error checking session in cache", e)
            }
        }
    }

    /**
     * Close an attendance event manually
     */
    fun closeEvent(event: AttendanceEvent) {
        viewModelScope.launch {
            try {
                val result = repository.closeAttendanceEvent(event.id)
                if (result.isSuccess) {
                    // Invalidate caches and force reload to reflect the closed event immediately
                    repository.invalidateEventCaches()
                    loadAvailableEvents(forceRefresh = true)
                    Log.d(TAG, "Event closed successfully: ${event.getEventName()}")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to close event"
                    _adminUiState.value = _adminUiState.value.copy(errorMessage = error)
                    Log.e(TAG, "Error closing event: $error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error closing event", e)
                _adminUiState.value = _adminUiState.value.copy(
                    errorMessage = e.message ?: "Unknown error occurred"
                )
            }
        }
    }



    /**
     * Clean up redundant fields from all events in the database
     * This removes duplicate "totalMarked" and "live" fields that should not exist
     * alongside the proper snake_case fields "total_marked" and "is_live"
     */
    fun cleanupRedundantFields() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting cleanup of redundant fields...")
                _adminUiState.value = _adminUiState.value.copy(isLoading = true)

                val result = repository.cleanupRedundantFields()
                result.fold(
                    onSuccess = { cleanedCount ->
                        Log.d(TAG, "Successfully cleaned $cleanedCount events")
                        _adminUiState.value = _adminUiState.value.copy(
                            isLoading = false,
                            errorMessage = "Cleanup completed. Cleaned $cleanedCount events."
                        )
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error during cleanup", error)
                        _adminUiState.value = _adminUiState.value.copy(
                            isLoading = false,
                            errorMessage = "Cleanup failed: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in cleanup", e)
                _adminUiState.value = _adminUiState.value.copy(
                    isLoading = false,
                    errorMessage = "Cleanup failed: ${e.message}"
                )
            }
        }
    }

    // ========== Zoom Control Methods ==========

    /**
     * Update zoom level for camera
     */
    fun updateZoomLevel(zoomLevel: Float) {
        val currentState = _studentUiState.value
        val clampedZoom = zoomLevel.coerceIn(currentState.minZoomLevel, currentState.maxZoomLevel)

        Log.d(TAG, "updateZoomLevel called: input=${String.format("%.1f", zoomLevel)}x, " +
                "clamped=${String.format("%.1f", clampedZoom)}x, " +
                "limits=${String.format("%.1f", currentState.minZoomLevel)}x-${String.format("%.1f", currentState.maxZoomLevel)}x")

        _studentUiState.value = currentState.copy(
            currentZoomLevel = clampedZoom
        )

        Log.d(TAG, "Zoom level state updated to: ${String.format("%.1f", clampedZoom)}x")
    }

    /**
     * Set zoom limits based on camera capabilities
     */
    fun setZoomLimits(minZoom: Float, maxZoom: Float) {
        val currentState = _studentUiState.value
        val safeMinZoom = minZoom.coerceAtLeast(1.0f)
        val safeMaxZoom = maxZoom.coerceAtMost(10.0f).coerceAtLeast(safeMinZoom)

        _studentUiState.value = currentState.copy(
            minZoomLevel = safeMinZoom,
            maxZoomLevel = safeMaxZoom,
            currentZoomLevel = currentState.currentZoomLevel.coerceIn(safeMinZoom, safeMaxZoom)
        )

        Log.d(TAG, "Zoom limits set: ${String.format("%.1f", safeMinZoom)}x - ${String.format("%.1f", safeMaxZoom)}x")
    }

    /**
     * Set zooming state for visual feedback
     */
    fun setZoomingState(isZooming: Boolean) {
        Log.d(TAG, "setZoomingState called with isZooming: $isZooming")
        _studentUiState.value = _studentUiState.value.copy(isZooming = isZooming)
    }

    /**
     * Reset zoom to default level
     */
    fun resetZoom() {
        val currentState = _studentUiState.value
        _studentUiState.value = currentState.copy(currentZoomLevel = currentState.minZoomLevel)
        Log.d(TAG, "Zoom reset to ${String.format("%.1f", currentState.minZoomLevel)}x")
    }
    
    /**
     * Generate PDF report for attendance event
     */
    fun generateAttendancePDF(event: AttendanceEvent) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== PDF GENERATION DEBUG START ===")
                Log.d(TAG, "Generating PDF for event: ${event.getEventName()}")
                Log.d(TAG, "Event ID: ${event.id}")
                Log.d(TAG, "Event object: $event")
                Log.d(TAG, "Event totalMarked: ${event.totalMarked}")
                Log.d(TAG, "Event attendees count: ${event.attendees.size}")
                
                // Update UI state to show loading
                _adminUiState.value = _adminUiState.value.copy(isLoading = true)
                
                // First, let's try to get all events to see what's available
                Log.d(TAG, "=== FETCHING ALL EVENTS FOR DEBUG ===")
                try {
                    val allEventsSnapshot = FirebaseFirestore.getInstance()
                        .collection("NSS_Events_Attendence")
                        .get()
                        .await()
                    
                    Log.d(TAG, "Total events in database: ${allEventsSnapshot.size()}")
                    for (doc in allEventsSnapshot) {
                        Log.d(TAG, "Available event ID: '${doc.id}'")
                        val docData = doc.data
                        val attendeesCount = (docData["attendees"] as? List<*>)?.size ?: 0
                        Log.d(TAG, "Event '${doc.id}' has $attendeesCount attendees")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching all events", e)
                }
                
                // Fetch attendees directly from Firestore database
                Log.d(TAG, "=== FETCHING ATTENDEES FOR EVENT: ${event.id} ===")
                val attendees = fetchEventAttendeesFromDatabase(event.id)
                
                if (attendees.isEmpty()) {
                    Log.w(TAG, "No attendees found for event: ${event.getEventName()}")
                    Log.w(TAG, "Event ID used: '${event.id}'")
                    _adminUiState.value = _adminUiState.value.copy(
                        isLoading = false,
                        errorMessage = "No attendees found for this event. Event ID: ${event.id}"
                    )
                    return@launch
                }
                
                Log.d(TAG, "Found ${attendees.size} attendees for PDF generation")
                
                // Generate PDF
                val pdfGenerator = PDFGenerator(application)
                val pdfPath = pdfGenerator.generateAttendanceReport(event, attendees)
                
                if (pdfPath != null) {
                    Log.d(TAG, "PDF generated successfully: $pdfPath")
                    
                    // Show success message with path
                    _adminUiState.value = _adminUiState.value.copy(
                        isLoading = false,
                        successMessage = "PDF saved to Downloads/NSS_Reports"
                    )
                } else {
                    Log.e(TAG, "Failed to generate PDF")
                    _adminUiState.value = _adminUiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to generate PDF report"
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error generating PDF", e)
                _adminUiState.value = _adminUiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error generating PDF: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Fetch attendees directly from Firestore database
     */
    private suspend fun fetchEventAttendeesFromDatabase(eventId: String): List<AttendeeRecord> {
        return try {
            Log.d(TAG, "=== FETCHING ATTENDEES DEBUG START ===")
            Log.d(TAG, "Looking for event ID: '$eventId'")
            Log.d(TAG, "Event ID length: ${eventId.length}")
            Log.d(TAG, "Event ID bytes: ${eventId.toByteArray().contentToString()}")
            
            // Get the document from NSS_Events_Attendence collection
            val document = FirebaseFirestore.getInstance()
                .collection("NSS_Events_Attendence")
                .document(eventId)
                .get()
                .await()
            
            Log.d(TAG, "Document exists: ${document.exists()}")
            Log.d(TAG, "Document ID: ${document.id}")
            Log.d(TAG, "Document reference: ${document.reference.path}")
            
            if (!document.exists()) {
                Log.w(TAG, "Event document not found: '$eventId'")
                Log.d(TAG, "Trying to find document by searching all events...")
                
                // Try to find the document by searching all events
                val allEvents = FirebaseFirestore.getInstance()
                    .collection("NSS_Events_Attendence")
                    .get()
                    .await()
                
                Log.d(TAG, "Found ${allEvents.size()} total events in database")
                for (doc in allEvents) {
                    Log.d(TAG, "Available event ID: '${doc.id}'")
                    Log.d(TAG, "Comparing: '$eventId' vs '${doc.id}'")
                    Log.d(TAG, "Are they equal: ${eventId == doc.id}")
                    Log.d(TAG, "Contains check: ${doc.id.contains(eventId)}")
                }
                
                return emptyList()
            }
            
            val data = document.data
            Log.d(TAG, "Document data keys: ${data?.keys}")
            Log.d(TAG, "Document data: $data")
            
            val attendeesField = data?.get("attendees")
            Log.d(TAG, "Attendees field type: ${attendeesField?.javaClass?.simpleName}")
            Log.d(TAG, "Attendees field value: $attendeesField")
            
            val attendeesArray = attendeesField as? List<Map<String, Any>>
            Log.d(TAG, "Attendees array type: ${attendeesArray?.javaClass?.simpleName}")
            Log.d(TAG, "Attendees array size: ${attendeesArray?.size}")
            
            if (attendeesArray == null || attendeesArray.isEmpty()) {
                Log.w(TAG, "No attendees array found in document")
                Log.d(TAG, "Attendees field value: ${data?.get("attendees")}")
                Log.d(TAG, "Attendees field is null: ${attendeesArray == null}")
                Log.d(TAG, "Attendees array is empty: ${attendeesArray?.isEmpty()}")
                return emptyList()
            }
            
            Log.d(TAG, "Found ${attendeesArray.size} attendees in database")
            Log.d(TAG, "Attendees data: $attendeesArray")
            
            // Convert to AttendeeRecord objects
            Log.d(TAG, "=== PARSING ATTENDEES ===")
            val parsedAttendees = attendeesArray.mapNotNull { attendeeData ->
                try {
                    Log.d(TAG, "Parsing attendee data: $attendeeData")
                    val name = attendeeData["name"] as? String ?: "Unknown"
                    val rollNumber = attendeeData["rollNumber"] as? String ?: "Unknown"
                    val deviceId = attendeeData["deviceId"] as? String ?: ""
                    
                    // Handle scanTimestamp - it could be a Timestamp object or a string
                    val scanTimestamp = try {
                        val timestampValue = attendeeData["scanTimestamp"]
                        when (timestampValue) {
                            is com.google.firebase.Timestamp -> {
                                Log.d(TAG, "Found Firebase Timestamp object")
                                timestampValue
                            }
                            is String -> {
                                Log.d(TAG, "Found timestamp string: $timestampValue")
                                if (timestampValue.isNotEmpty()) {
                                    val dateFormat = java.text.SimpleDateFormat("dd MMMM yyyy 'at' HH:mm:ss 'UTC+5:30'", java.util.Locale.ENGLISH)
                                    val date = dateFormat.parse(timestampValue) ?: java.util.Date()
                                    com.google.firebase.Timestamp(date)
                                } else {
                                    com.google.firebase.Timestamp.now()
                                }
                            }
                            else -> {
                                Log.w(TAG, "Unknown timestamp type: ${timestampValue?.javaClass?.simpleName}")
                                com.google.firebase.Timestamp.now()
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing timestamp", e)
                        com.google.firebase.Timestamp.now()
                    }
                    
                    Log.d(TAG, "Parsed attendee: name='$name', rollNumber='$rollNumber', scanTimestamp='${scanTimestamp.toDate()}', deviceId='$deviceId'")
                    
                    // Get scannedFrom data
                    val scannedFromData = attendeeData["scannedFrom"] as? Map<String, Any>
                    val adminName = scannedFromData?.get("adminName") as? String ?: "Unknown Admin"
                    val adminRollNumber = scannedFromData?.get("adminRollNumber") as? String ?: "Unknown"
                    
                    AttendeeRecord(
                        name = name,
                        rollNumber = rollNumber,
                        scanTimestamp = scanTimestamp,
                        deviceId = deviceId,
                        scannedFrom = com.phad.chatapp.models.ScannedFromAdmin(
                            adminRollNumber = adminRollNumber,
                            adminName = adminName
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing attendee data", e)
                    null
                }
            }
            
            Log.d(TAG, "=== PARSING COMPLETE ===")
            Log.d(TAG, "Successfully parsed ${parsedAttendees.size} attendees")
            Log.d(TAG, "Parsed attendees: $parsedAttendees")
            
            return parsedAttendees
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching attendees from database", e)
            emptyList()
        }
    }

    /**
     * Add manual attendance for one or multiple roll numbers
     */
    fun addManualAttendance(eventId: String, rollNumbersText: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Adding manual attendance for event: $eventId")
                Log.d(TAG, "Roll numbers text: $rollNumbersText")

                // Parse roll numbers from the input text
                val rollNumbers = parseRollNumbers(rollNumbersText)
                if (rollNumbers.isEmpty()) {
                    _adminUiState.value = _adminUiState.value.copy(
                        errorMessage = "No valid roll numbers found. Please check your input."
                    )
                    return@launch
                }

                Log.d(TAG, "Parsed roll numbers: $rollNumbers")

                // initialize progress for adding
                _adminUiState.value = _adminUiState.value.copy(
                    showRollProgress = true,
                    rollProgressProcessed = 0,
                    rollProgressTotal = rollNumbers.size,
                    rollProgressTitle = "Adding Attendance"
                )
                // Allow UI a moment to render the progress dialog before heavy work
                delay(50)

                // Get current admin information
                val adminId = _adminUiState.value.adminId
                val adminName = _adminUiState.value.adminName

                if (adminId.isBlank()) {
                    _adminUiState.value = _adminUiState.value.copy(
                        errorMessage = "Admin information not found. Please try again."
                    )
                    return@launch
                }

                var successCount = 0
                var errorCount = 0
                val errors = mutableListOf<String>()
                val perRollResults = mutableListOf<RollOperationResult>()

                // Process each roll number
                for ((idx, rollNumber) in rollNumbers.withIndex()) {
                    try {
                        Log.d(TAG, "Processing roll number: $rollNumber")
                        
                        // Check if roll number is already in the event (case-insensitive) by fetching current event data
                        Log.d(TAG, "=== CHECKING DUPLICATE FOR ROLL NUMBER: $rollNumber ===")
                        Log.d(TAG, "Event ID: $eventId")
                        
                        val currentEventResult = repository.getAttendanceEvent(eventId)
                        if (currentEventResult.isSuccess) {
                            val currentEvent = currentEventResult.getOrNull()
                            if (currentEvent != null) {
                                val existingAttendees = currentEvent.attendees
                                Log.d(TAG, "Event fetched successfully for duplicate check")
                                Log.d(TAG, "Current attendees count: ${existingAttendees.size}")
                                Log.d(TAG, "Current attendees: ${existingAttendees.map { "${it.rollNumber} (${it.name})" }}")
                                
                                val isAlreadyAttending = existingAttendees.any { 
                                    it.rollNumber.equals(rollNumber, ignoreCase = true) 
                                }
                                
                                Log.d(TAG, "Checking if $rollNumber is already attending: $isAlreadyAttending")
                                
                                if (isAlreadyAttending) {
                                    val duplicateAttendee = existingAttendees.find { 
                                        it.rollNumber.equals(rollNumber, ignoreCase = true) 
                                    }
                                    Log.w(TAG, "DUPLICATE FOUND: Roll number $rollNumber is already marked for attendance")
                                    Log.w(TAG, "Duplicate attendee details: $duplicateAttendee")
                                    errorCount++
                                    errors.add("Already marked: $rollNumber")
                                    perRollResults.add(RollOperationResult(rollNumber, false, "Already marked"))
                                    continue
                                } else {
                                    Log.d(TAG, "No duplicate found for $rollNumber, proceeding with attendance marking")
                                }
                            } else {
                                Log.w(TAG, "Event is null after successful fetch")
                            }
                        } else {
                            Log.w(TAG, "Could not fetch current event data for duplicate check: ${currentEventResult.exceptionOrNull()?.message}")
                        }
                        
                        // Prefer unified users collection; fallback to legacy Student collection (case-insensitive)
                        val userResult = repository.getUserByRollNumberCaseInsensitive(rollNumber)
                        val legacyStudentResult = if (userResult.getOrNull() == null) repository.getStudentByRollNumberCaseInsensitive(rollNumber) else Result.success(null)

                        if (userResult.isFailure) {
                            Log.e(TAG, "Failed to get user data for roll number: $rollNumber, error: ${userResult.exceptionOrNull()?.message}")
                        }

                        val studentData = userResult.getOrNull() ?: legacyStudentResult.getOrNull()
                        if (studentData == null) {
                            Log.w(TAG, "User/Student document not found for roll number: $rollNumber")
                            errorCount++
                            errors.add("Student not found: $rollNumber")
                            perRollResults.add(RollOperationResult(rollNumber, false, "Student not found"))
                            continue
                        }

                        Log.d(TAG, "Student data retrieved for $rollNumber: $studentData")
                        
                        // Try different possible field names for student name
                        val studentName = studentData["Name"] as? String 
                            ?: studentData["name"] as? String
                            ?: studentData["student_name"] as? String
                            ?: studentData["full_name"] as? String
                            ?: studentData["studentName"] as? String
                            ?: studentData["fullName"] as? String
                            ?: "Unknown Student"
                        
                        Log.d(TAG, "Extracted student name for $rollNumber: '$studentName'")
                        
                        // If still "Unknown Student", log all available fields for debugging
                        if (studentName == "Unknown Student") {
                            Log.w(TAG, "Could not find name field for student $rollNumber. Available fields: ${studentData.keys}")
                        }

                        // Create AttendeeRecord for manual entry
                        val attendee = AttendeeRecord(
                            rollNumber = rollNumber,
                            name = studentName,
                            scanTimestamp = com.google.firebase.Timestamp.now(),
                            scannedFrom = com.phad.chatapp.models.ScannedFromAdmin(
                                adminRollNumber = adminId,
                                adminName = adminName
                            ),
                            deviceId = "", // No device ID for manual entry
                            scanLocation = null, // No location for manual entry
                            isManualEntry = true // Mark as manual entry (no location check)
                        )

                        // Add attendee to event
                        val result = repository.addAttendeeToEvent(eventId, attendee)
                        if (result.isSuccess) {
                            successCount++
                            Log.d(TAG, "Successfully added manual attendance for: $rollNumber")
                            perRollResults.add(RollOperationResult(rollNumber, true, null))
                        } else {
                            errorCount++
                            val error = result.exceptionOrNull()?.message ?: "Unknown error"
                            errors.add("$rollNumber: $error")
                            Log.e(TAG, "Failed to add attendance for $rollNumber: $error")
                            perRollResults.add(RollOperationResult(rollNumber, false, error))
                        }

                    } catch (e: Exception) {
                        errorCount++
                        errors.add("$rollNumber: ${e.message}")
                        perRollResults.add(RollOperationResult(rollNumber, false, e.message))
                        Log.e(TAG, "Error processing roll number $rollNumber", e)
                    }

                    // emit progress after each roll
                    _adminUiState.value = _adminUiState.value.copy(
                        rollProgressProcessed = idx + 1
                    )
                }

                // Update UI state with results
                val message = if (errorCount == 0) {
                    "Successfully added attendance for $successCount student(s)"
                } else if (successCount == 0) {
                    "Failed to add attendance for all students. Errors: ${errors.joinToString(", ")}"
                } else {
                    "Added attendance for $successCount student(s). Failed for $errorCount: ${errors.joinToString(", ")}"
                }

                // Sort results: unsuccessful first, then successful
                val sortedResults = perRollResults.sortedBy { it.success }

                _adminUiState.value = _adminUiState.value.copy(
                    successMessage = null, // prefer detailed dialog
                    rollResults = sortedResults,
                    showRollResults = true,
                    rollOperationTitle = "Add Attendance Results",
                    attendeeCount = _adminUiState.value.attendeeCount, // unchanged, but explicit to avoid accidental reset
                    showRollProgress = false
                )

                // Refresh the current event data if we're in an active session
                if (_adminUiState.value.isSessionActive && _adminUiState.value.selectedEvent?.id == eventId) {
                    startEventListener(eventId)
                }
                
                // Always refresh the events list to show updated attendee counts
                repository.invalidateEventCaches()
                loadAvailableEvents(forceRefresh = true)

            } catch (e: Exception) {
                Log.e(TAG, "Error adding manual attendance", e)
                _adminUiState.value = _adminUiState.value.copy(
                    errorMessage = "Error adding manual attendance: ${e.message}"
                )
            }
        }
    }

    /**
     * Mark students as absent by removing their attendance records
     */
    fun markStudentsAbsent(eventId: String, rollNumbersText: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Marking students absent for event: $eventId")
                Log.d(TAG, "Roll numbers text: $rollNumbersText")

                // Parse roll numbers from the input text
                val rollNumbers = parseRollNumbers(rollNumbersText)
                if (rollNumbers.isEmpty()) {
                    _adminUiState.value = _adminUiState.value.copy(
                        errorMessage = "No valid roll numbers found. Please check your input."
                    )
                    return@launch
                }

                Log.d(TAG, "Parsed roll numbers: $rollNumbers")

                // initialize progress for deletion
                _adminUiState.value = _adminUiState.value.copy(
                    showRollProgress = true,
                    rollProgressProcessed = 0,
                    rollProgressTotal = rollNumbers.size,
                    rollProgressTitle = "Deleting / Marking Absent"
                )
                // Allow UI a moment to render the progress dialog before heavy work
                delay(50)

                var successCount = 0
                var errorCount = 0
                val errors = mutableListOf<String>()
                val perRollResults = mutableListOf<RollOperationResult>()

                // Process each roll number
                for ((idx, rollNumber) in rollNumbers.withIndex()) {
                    try {
                        Log.d(TAG, "Processing roll number for absent marking: $rollNumber")
                        
                        // Remove attendee from event
                        val result = repository.removeAttendeeFromEvent(eventId, rollNumber)
                        if (result.isSuccess) {
                            successCount++
                            Log.d(TAG, "Successfully marked absent for: $rollNumber")
                            perRollResults.add(RollOperationResult(rollNumber, true, null))
                        } else {
                            errorCount++
                            val error = result.exceptionOrNull()?.message ?: "Unknown error"
                            errors.add("$rollNumber: $error")
                            Log.e(TAG, "Failed to mark absent for $rollNumber: $error")
                            perRollResults.add(RollOperationResult(rollNumber, false, error))
                        }

                    } catch (e: Exception) {
                        errorCount++
                        errors.add("$rollNumber: ${e.message}")
                        perRollResults.add(RollOperationResult(rollNumber, false, e.message))
                        Log.e(TAG, "Error processing roll number $rollNumber for absent marking", e)
                    }

                    // emit progress after each roll
                    _adminUiState.value = _adminUiState.value.copy(
                        rollProgressProcessed = idx + 1
                    )
                }

                // Update UI state with results
                val message = if (errorCount == 0) {
                    "Successfully marked absent for $successCount student(s)"
                } else if (successCount == 0) {
                    "Failed to mark absent for all students. Errors: ${errors.joinToString(", ")}"
                } else {
                    "Marked absent for $successCount student(s). Failed for $errorCount: ${errors.joinToString(", ")}"
                }

                // Sort results: unsuccessful first, then successful
                val sortedResults = perRollResults.sortedBy { it.success }

                _adminUiState.value = _adminUiState.value.copy(
                    successMessage = null,
                    rollResults = sortedResults,
                    showRollResults = true,
                    rollOperationTitle = "Delete / Mark Absent Results",
                    showRollProgress = false
                )

                // Refresh the current event data if we're in an active session
                if (_adminUiState.value.isSessionActive && _adminUiState.value.selectedEvent?.id == eventId) {
                    startEventListener(eventId)
                }
                
                // Always refresh the events list to show updated attendee counts
                repository.invalidateEventCaches()
                loadAvailableEvents(forceRefresh = true)

            } catch (e: Exception) {
                Log.e(TAG, "Error marking students absent", e)
                _adminUiState.value = _adminUiState.value.copy(
                    errorMessage = "Error marking students absent: ${e.message}"
                )
            }
        }
    }

    /**
     * Parse roll numbers from input text
     * Supports comma, line, space, and comma-space separators
     * Automatically converts roll numbers to uppercase for consistency
     */
    private fun parseRollNumbers(input: String): List<String> {
        if (input.isBlank()) return emptyList()

        return input
            .split(Regex("[,;\\n\\r\\s]+")) // Split by comma, semicolon, newline, carriage return, or whitespace
            .map { it.trim().uppercase() } // Convert to uppercase for consistency
            .filter { it.isNotBlank() }
            .distinct()
    }
}

/**
 * UI State for Admin QR Attendance interface
 */
data class AdminQRUiState(
    val isLoading: Boolean = false,
    val adminId: String = "",
    val adminName: String = "",
    val isAdmin: Boolean = false,
    val availableEvents: List<AttendanceEvent> = emptyList(),
    val selectedEvent: AttendanceEvent? = null,
    val currentSession: AttendanceSession? = null,
    val isSessionActive: Boolean = false,
    val currentQRCode: Bitmap? = null,
    val currentQRData: QRAttendanceData? = null,
    val qrRefreshCount: Int = 0,
    val attendeeCount: Int = 0,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    // Event creation states
    val showCreateEventDialog: Boolean = false,
    val isCreatingEvent: Boolean = false,
    val createEventSuccess: Boolean = false,
    // Event editing states
    val showEditEventDialog: Boolean = false,
    val editingEvent: AttendanceEvent? = null,
    val isUpdatingEvent: Boolean = false,
    val editEventSuccess: Boolean = false,
    // Roll operations dialog state
    val rollResults: List<RollOperationResult> = emptyList(),
    val showRollResults: Boolean = false,
    val rollOperationTitle: String? = null,
    // Roll batch progress
    val showRollProgress: Boolean = false,
    val rollProgressProcessed: Int = 0,
    val rollProgressTotal: Int = 0,
    val rollProgressTitle: String? = null
)

/**
 * UI State for Student QR Attendance interface
 */
data class StudentQRUiState(
    val isProcessing: Boolean = false,
    val studentId: String = "",
    val studentName: String = "",
    val isStudent: Boolean = false,
    val scanResult: ScanResult? = null,
    val isCameraPermissionGranted: Boolean = false,

    val cameraExited: Boolean = false,
    val navigatedToResult: Boolean = false,

    // Zoom functionality
    val currentZoomLevel: Float = 1.0f,
    val minZoomLevel: Float = 1.0f,
    val maxZoomLevel: Float = 4.0f,
    val isZooming: Boolean = false,

    // Live location cache for faster proximity checks
    val lastLocation: GeoPoint? = null,
    val lastLocationTimestampMs: Long = 0L
)

/**
 * Sealed class representing scan results
 */
sealed class ScanResult {
    data class Success(val message: String) : ScanResult()
    data class Error(val message: String) : ScanResult()
}

/** Per-roll result model for add/delete operations */
data class RollOperationResult(
    val rollNumber: String,
    val success: Boolean,
    val errorMessage: String?
)
