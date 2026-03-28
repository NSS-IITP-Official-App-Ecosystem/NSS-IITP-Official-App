package com.phad.chatapp.features.calendar.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.phad.chatapp.features.calendar.models.CalendarEvent
import com.phad.chatapp.features.calendar.models.EventStatus
import com.phad.chatapp.features.calendar.models.EventType
import com.phad.chatapp.features.calendar.models.LeaveApplication
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.phad.chatapp.features.scheduling.models.AssignmentEntry
import com.phad.chatapp.features.scheduling.models.SubjectAssignmentDetails
import com.phad.chatapp.features.scheduling.models.SubjectConstants
/**
 * Repository for calendar events using Firestore
 */
class CalendarRepository {
    
    private val firestore = FirebaseFirestore.getInstance()
    
    // Collection references
    private val teachingEventsCollection = firestore.collection("teaching_events")
    private val generalEventsCollection = firestore.collection("general_events")
    private val leaveApplicationsCollection = firestore.collection("leave_applications")
    private val availableLeavesCollection = firestore.collection("available_leaves")
    
    private val _events = MutableLiveData<List<CalendarEvent>>(emptyList())
    val events: LiveData<List<CalendarEvent>> = _events
    
    private val _leaveApplications = MutableLiveData<List<LeaveApplication>>(emptyList())
    val leaveApplications: LiveData<List<LeaveApplication>> = _leaveApplications
    
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    init {
        // Load initial data
        loadEvents()
    }
    
    /**
     * Load events from Firestore
     */
    fun loadEvents() {
        // Combine teaching and general events
        val eventsList = mutableListOf<CalendarEvent>()
        
        // Listen for teaching events
        teachingEventsCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CalendarRepository", "Error loading teaching events: ${error.message}")
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    try {
                        // Manually handle document IDs to avoid conflict with field 'id'
                        val teachingEvents = snapshot.documents.map { doc ->
                            val event = doc.toObject(CalendarEvent::class.java) ?: CalendarEvent()
                            // Set document ID manually if needed
                            if (event.id.isEmpty()) {
                                event.copy(id = doc.id)
                            } else {
                                event
                            }
                        }
                        
                        // Get general events as well
                        generalEventsCollection
                            .orderBy("timestamp", Query.Direction.DESCENDING)
                            .get()
                            .addOnSuccessListener { generalSnapshot ->
                                try {
                                    // Handle document IDs the same way
                                    val generalEvents = generalSnapshot.documents.map { doc ->
                                        val event = doc.toObject(CalendarEvent::class.java) ?: CalendarEvent()
                                        // Set document ID manually if needed
                                        if (event.id.isEmpty()) {
                                            event.copy(id = doc.id)
                                        } else {
                                            event
                                        }
                                    }
                                    
                                    // Combine both lists
                                    eventsList.clear()
                                    eventsList.addAll(teachingEvents)
                                    eventsList.addAll(generalEvents)
                                    
                                    // Update LiveData
                                    _events.value = eventsList
                                } catch (e: Exception) {
                                    Log.e("CalendarRepository", "Error processing general events: ${e.message}")
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("CalendarRepository", "Failed to fetch general events: ${e.message}")
                            }
                    } catch (e: Exception) {
                        Log.e("CalendarRepository", "Error processing teaching events: ${e.message}")
                    }
                }
            }
            
        // Listen for leave applications
        leaveApplicationsCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CalendarRepository", "Error loading leave applications: ${error.message}")
                    // Don't return - just continue with empty list if permissions are denied
                    // This prevents the app from crashing due to permission issues
                    _leaveApplications.value = emptyList()
                    return@addSnapshotListener
                }
                
                if (snapshot == null) {
                    _leaveApplications.value = emptyList()
                    return@addSnapshotListener
                }
                
                try {
                    // Manually handle document IDs to avoid conflict with field 'id'
                    val applications = snapshot.documents.map { doc ->
                        val application = doc.toObject(LeaveApplication::class.java) ?: LeaveApplication()
                        // Set document ID manually if needed
                        if (application.id.isEmpty()) {
                            application.copy(id = doc.id)
                        } else {
                            application
                        }
                    }
                    _leaveApplications.value = applications
                    
                    // Log the leave applications by status for debugging
                    val pendingCount = applications.count { it.status == EventStatus.PENDING }
                    val availableCount = applications.count { it.status == EventStatus.APPROVED }
                    val rejectedCount = applications.count { it.status == EventStatus.REJECTED }
                    
                    Log.d("CalendarRepository", "Leave applications loaded - Pending: $pendingCount, Available: $availableCount, Rejected: $rejectedCount")
                } catch (e: Exception) {
                    Log.e("CalendarRepository", "Error converting leave applications: ${e.message}")
                    _leaveApplications.value = emptyList()
                }
            }
    }
    
    /**
     * Get events for a specific day from both collections
     */
    fun getEventsForDay(date: Date): List<CalendarEvent> {
        return _events.value?.filter { isSameDay(it.date, date) } ?: emptyList()
    }
    
    /**
     * Add a teaching or general event to Firestore
     */
    suspend fun addEvent(title: String, description: String, location: String, date: Date, type: EventType) {
        val eventId = UUID.randomUUID().toString()
        val dateKey = dateFormatter.format(date)
        
        val event = CalendarEvent(
            id = eventId,
            date = date,
            title = title,
            description = description,
            location = location,
            eventType = type,
            timeSlot = "9:00 AM - 11:00 AM", // Default time slot
            status = EventStatus.SCHEDULED,
            createdBy = "admin", // In a real app, get the current user's ID
            timestamp = System.currentTimeMillis()
        )
        
        // Choose collection based on event type
        val collection = if (type == EventType.TEACHING) {
            teachingEventsCollection
        } else {
            generalEventsCollection
        }
        
        // Add document with event ID
        collection.document(eventId).set(event).await()
        
        // Also update the in-memory list for immediate UI updates
        val currentEvents = _events.value?.toMutableList() ?: mutableListOf()
        currentEvents.add(event)
        _events.postValue(currentEvents)
    }
    
    /**
     * Delete an event from Firestore
     */
    fun deleteEvent(eventId: String): Boolean {
        try {
            // Check both collections for the event
            teachingEventsCollection.document(eventId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        teachingEventsCollection.document(eventId).delete()
                    }
                }
                
            generalEventsCollection.document(eventId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        generalEventsCollection.document(eventId).delete()
                    }
                }
            
            // Update in-memory list immediately (optimistic update)
            val currentEvents = _events.value?.toMutableList() ?: mutableListOf()
        val initialSize = currentEvents.size
        currentEvents.removeIf { it.id == eventId }
        
        if (currentEvents.size < initialSize) {
                _events.postValue(currentEvents)
            return true
        }
            return false
        
        } catch (e: Exception) {
        return false
        }
    }
    
    /**
     * Update event status in Firestore
     */
    fun updateEventStatus(eventId: String, status: EventStatus): Boolean {
        try {
            // Update in both collections (one will fail silently if not found)
            teachingEventsCollection.document(eventId)
                .update("status", status.toString())
                
            generalEventsCollection.document(eventId)
                .update("status", status.toString())
            
            // Update in-memory list
        val currentEvents = _events.value?.toMutableList() ?: return false
        val eventIndex = currentEvents.indexOfFirst { it.id == eventId }
        
        if (eventIndex == -1) return false
        
        val event = currentEvents[eventIndex]
        val updatedEvent = event.copy(status = status)
        currentEvents[eventIndex] = updatedEvent
            _events.postValue(currentEvents)
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Apply for leave on a teaching event
     */
    suspend fun applyForLeave(
        userId: String,
        userName: String,
        rollNumber: String,
        date: Date,
        slot: String,
        subject: String,
        school: String
    ): Boolean {
        try {
            val leaveId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            
            val leaveApplication = LeaveApplication(
                id = leaveId,
                userId = userId,
                userName = userName,
                rollNumber = rollNumber,
                date = date,
                slot = slot,
                subject = subject,
                school = school,
                status = EventStatus.APPROVED, // Auto-approve per requirements
                timestamp = timestamp
            )
            
            // Add to leave applications collection
            leaveApplicationsCollection.document(leaveId).set(leaveApplication).await()
            
            // Auto-approved: Add to available leaves collection as well
            availableLeavesCollection.document(leaveId).set(leaveApplication).await()
            
            // Update in-memory list
            val currentApplications = _leaveApplications.value?.toMutableList() ?: mutableListOf()
            currentApplications.add(leaveApplication)
            _leaveApplications.postValue(currentApplications)
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Approve or reject a leave application
     */
    suspend fun updateLeaveStatus(leaveId: String, status: EventStatus): Boolean {
        try {
            // Update status in leave applications
            leaveApplicationsCollection.document(leaveId)
                .update("status", status.toString())
                .await()
            
            // If approved, add to available leaves collection
            if (status == EventStatus.APPROVED) {
                val leaveDoc = leaveApplicationsCollection.document(leaveId).get().await()
                if (leaveDoc.exists()) {
                    val leaveApplication = leaveDoc.toObject(LeaveApplication::class.java)
                    if (leaveApplication != null) {
                        availableLeavesCollection.document(leaveId).set(leaveApplication).await()
                    }
                }
            }
            
            // Update in-memory list
            val currentApplications = _leaveApplications.value?.toMutableList() ?: return false
            val applicationIndex = currentApplications.indexOfFirst { it.id == leaveId }
            
            if (applicationIndex == -1) return false
            
            val application = currentApplications[applicationIndex]
            val updatedApplication = application.copy(status = status)
            currentApplications[applicationIndex] = updatedApplication
            _leaveApplications.postValue(currentApplications)
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Book a slot for a general event
     */
    suspend fun bookEventSlot(
        eventId: String,
        userId: String,
        userName: String
    ): Boolean {
        try {
            // Update the general event to indicate booking
            generalEventsCollection.document(eventId)
                .update(
                    mapOf(
                        "bookedBy" to userId,
                        "bookedByName" to userName,
                        "status" to EventStatus.BOOKED.toString()
                    )
                )
                .await()
            
            // Update in-memory list
            val currentEvents = _events.value?.toMutableList() ?: return false
            val eventIndex = currentEvents.indexOfFirst { it.id == eventId }
            
            if (eventIndex == -1) return false
            
            val event = currentEvents[eventIndex]
            val updatedEvent = event.copy(
                bookedBy = userId,
                bookedByName = userName,
                status = EventStatus.BOOKED
            )
            currentEvents[eventIndex] = updatedEvent
            _events.postValue(currentEvents)
        
        return true
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Accept a class (teaching event) with roll number
     */
    suspend fun acceptClass(
        eventId: String,
        rollNumber: String,
        substituteName: String
    ): Boolean {
        try {
            // Determine if this is a teaching or general event
            var isTeachingEvent = false
            var event: CalendarEvent? = null
            
            // Try to get the event from teaching events first
            val teachingDoc = teachingEventsCollection.document(eventId).get().await()
            if (teachingDoc.exists()) {
                isTeachingEvent = true
                event = teachingDoc.toObject(CalendarEvent::class.java)
            } else {
                // If not found, try general events
                val generalDoc = generalEventsCollection.document(eventId).get().await()
                if (generalDoc.exists()) {
                    event = generalDoc.toObject(CalendarEvent::class.java)
                }
            }
            
            if (event == null) {
                Log.e("CalendarRepository", "Event not found with ID: $eventId")
                return false
            }
            
            // Determine which collection to update
            val collection = if (isTeachingEvent) teachingEventsCollection else generalEventsCollection
            
            // Update the event to indicate acceptance
            collection.document(eventId)
                .update(
                    mapOf(
                        "acceptedByRollNumber" to rollNumber,
                        "bookedByName" to substituteName,
                        "status" to EventStatus.ACCEPTED.toString()
                    )
                )
                .await()
            
            // Update in-memory list
            val currentEvents = _events.value?.toMutableList() ?: return false
            val eventIndex = currentEvents.indexOfFirst { it.id == eventId }
            
            if (eventIndex == -1) {
                Log.e("CalendarRepository", "Event found in Firestore but not in local cache: $eventId")
                return false
            }
            
            val updatedEvent = currentEvents[eventIndex].copy(
                acceptedByRollNumber = rollNumber,
                bookedByName = substituteName,
                status = EventStatus.ACCEPTED
            )
            currentEvents[eventIndex] = updatedEvent
            _events.postValue(currentEvents)
            
            Log.d("CalendarRepository", "Class accepted successfully with roll number: $rollNumber")
            return true
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error accepting class: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Get leave applications for a specific date
     */
    fun getLeaveApplicationsForDay(date: Date): List<LeaveApplication> {
        Log.d("CalendarRepository", "Getting leave applications for date: ${dateFormatter.format(date)}")
        
        val allLeaves = _leaveApplications.value ?: emptyList()
        Log.d("CalendarRepository", "Total leave applications in memory: ${allLeaves.size}")
        
        val filteredLeaves = allLeaves.filter { leave -> isSameDay(leave.date, date) }
        Log.d("CalendarRepository", "Filtered leave applications for date ${dateFormatter.format(date)}: ${filteredLeaves.size}")
        
        val approvedLeaves = filteredLeaves.filter { it.status == EventStatus.APPROVED }
        Log.d("CalendarRepository", "Approved leave applications for date ${dateFormatter.format(date)}: ${approvedLeaves.size}")
        
        return filteredLeaves
    }
    
    /**
     * Helper function to compare dates ignoring time
     */
    private fun isSameDay(date1: Date, date2: Date): Boolean {
        if (date1 == null || date2 == null) {
            Log.e("CalendarRepository", "Null date comparison attempt")
            return false
        }
        
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        
        val year1 = cal1.get(Calendar.YEAR)
        val month1 = cal1.get(Calendar.MONTH)
        val day1 = cal1.get(Calendar.DAY_OF_MONTH)
        
        val year2 = cal2.get(Calendar.YEAR)
        val month2 = cal2.get(Calendar.MONTH)
        val day2 = cal2.get(Calendar.DAY_OF_MONTH)
        
        val result = year1 == year2 && month1 == month2 && day1 == day2
        
        // Debug logging
        if (!result) {
            Log.d("CalendarRepository", "Date comparison: ${dateFormatter.format(date1)} vs ${dateFormatter.format(date2)} = $result")
            Log.d("CalendarRepository", "Components: Y($year1=$year2) M($month1=$month2) D($day1=$day2)")
        }
        
        return result
    }
    
    /**
     * Get accepted leaves for a specific date
     */
    suspend fun getAcceptedLeavesForDay(date: Date): List<LeaveApplication> {
        val dateString = dateFormatter.format(date)
        
        try {
            val snapshot = availableLeavesCollection
                .whereEqualTo("status", EventStatus.APPROVED.toString())
                .get()
                .await()
            
            // Manually handle document IDs to avoid conflict with field 'id'
            return snapshot.documents.mapNotNull { doc ->
                doc.toObject(LeaveApplication::class.java)?.let { app ->
                    if (app.id.isEmpty()) app.copy(id = doc.id) else app
                }
            }.filter { isSameDay(it.date, date) }
            
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error loading accepted leaves: ${e.message}")
            return emptyList()
        }
    }
    
    /**
     * Update an existing event in Firestore
     */
    suspend fun updateEvent(event: CalendarEvent) {
        try {
            // Determine collection based on event type
            val collection = if (event.eventType == EventType.TEACHING) {
                teachingEventsCollection
            } else {
                generalEventsCollection
            }
            
            // Update document with event ID
            collection.document(event.id).update(
                mapOf(
                    "title" to event.title,
                    "description" to event.description,
                    "location" to event.location,
                    "timeSlot" to event.timeSlot
                )
            ).await()
            
            // Also update the in-memory list for immediate UI updates
            val currentEvents = _events.value?.toMutableList() ?: mutableListOf()
            val eventIndex = currentEvents.indexOfFirst { it.id == event.id }
            
            if (eventIndex != -1) {
                currentEvents[eventIndex] = event
                _events.postValue(currentEvents)
            }
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error updating event: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Get all approved leaves
     */
    suspend fun getAllApprovedLeaves(): List<LeaveApplication> {
        try {
            val snapshot = availableLeavesCollection
                .whereEqualTo("status", EventStatus.APPROVED.toString())
                .get()
                .await()
            
            // Manually handle document IDs to avoid conflict with field 'id'
            return snapshot.documents.mapNotNull { doc ->
                doc.toObject(LeaveApplication::class.java)?.let { app ->
                    if (app.id.isEmpty()) app.copy(id = doc.id) else app
                }
            }
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error loading all approved leaves: ${e.message}")
            return emptyList()
        }
    }
    
    /**
     * Get all leave applications
     */
    suspend fun getAllLeaveApplications(): List<LeaveApplication> {
        try {
            // Get all leaves regardless of status
            val snapshot = leaveApplicationsCollection
                .get()
                .await()
            
            // Manually handle document IDs to avoid conflict with field 'id'
            return snapshot.documents.map { doc ->
                val application = doc.toObject(LeaveApplication::class.java) ?: LeaveApplication()
                // Set document ID manually if needed
                if (application.id.isEmpty()) {
                    application.copy(id = doc.id)
                } else {
                    application
                }
            }
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error getting all leave applications: ${e.message}")
            return emptyList()
        }
    }
    
    /**
     * Mark a leave as substituted by a student with given roll number
     */
    suspend fun markLeaveAsSubstituted(
        leaveId: String,
        rollNumber: String,
        substituteName: String
    ): Boolean {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val leaveDocRef = leaveApplicationsCollection.document(leaveId)
            val availableLeaveDocRef = availableLeavesCollection.document(leaveId)

            db.runTransaction { transaction ->
                // First perform all reads
                val snapshot = transaction.get(leaveDocRef)
                val availableSnapshot = transaction.get(availableLeaveDocRef)
                
                // Then perform validation
                if (snapshot.exists()) {
                    val status = snapshot.getString("status")
                    val substitutedBy = snapshot.getString("substitutedByRollNumber")
                    
                    if (status == EventStatus.ACCEPTED.toString() || !substitutedBy.isNullOrEmpty()) {
                        throw com.google.firebase.firestore.FirebaseFirestoreException(
                            "Leave already accepted",
                            com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED
                        )
                    }
                }

                // Then perform all writes
                if (snapshot.exists()) {
                    transaction.update(
                        leaveDocRef,
                        mapOf(
                            "substitutedByRollNumber" to rollNumber,
                            "substitutedByName" to substituteName,
                            "status" to EventStatus.ACCEPTED.toString()
                        )
                    )
                }

                if (availableSnapshot.exists()) {
                    transaction.update(
                        availableLeaveDocRef,
                        mapOf(
                            "substitutedByRollNumber" to rollNumber,
                            "substitutedByName" to substituteName,
                            "status" to EventStatus.ACCEPTED.toString()
                        )
                    )
                }
            }.await()
                
            // Update in-memory list
            val currentLeaves = _leaveApplications.value?.toMutableList() ?: return false
            val leaveIndex = currentLeaves.indexOfFirst { it.id == leaveId }
            
            if (leaveIndex != -1) {
                // We need to update the leave application with the substitution information
                val leave = currentLeaves[leaveIndex]
                val updatedLeave = leave.copy(
                    substitutedByRollNumber = rollNumber,
                    substitutedByName = substituteName,
                    status = EventStatus.ACCEPTED
                )
                currentLeaves[leaveIndex] = updatedLeave
                _leaveApplications.postValue(currentLeaves)
            }
            
            Log.d("CalendarRepository", "Leave $leaveId marked as substituted by roll number $rollNumber")
            return true
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error marking leave as substituted: ${e.message}", e)
            return false
        }
    }

    /**
     * Withdraw from a previously accepted substitution
     * Sets status back to APPROVED and clears substitutedByRollNumber
     */
    suspend fun withdrawSubstitution(leaveId: String): Boolean {
        try {
            // Update in leave applications collection
            leaveApplicationsCollection.document(leaveId)
                .update(
                    mapOf(
                        "substitutedByRollNumber" to "",
                        "status" to EventStatus.APPROVED.toString()
                    )
                )
                .await()
            
            // Also update in accepted leaves collection if it exists there
            availableLeavesCollection.document(leaveId)
                .update(
                    mapOf(
                        "substitutedByRollNumber" to "",
                        "status" to EventStatus.APPROVED.toString()
                    )
                )
                .await()
                
            // Update in-memory list
            val currentLeaves = _leaveApplications.value?.toMutableList() ?: return false
            val leaveIndex = currentLeaves.indexOfFirst { it.id == leaveId }
            
            if (leaveIndex != -1) {
                val leave = currentLeaves[leaveIndex]
                val updatedLeave = leave.copy(
                    status = EventStatus.APPROVED,
                    substitutedByRollNumber = ""
                )
                currentLeaves[leaveIndex] = updatedLeave
                _leaveApplications.postValue(currentLeaves)
            }
            
            Log.d("CalendarRepository", "Substitution withdrawn for leave $leaveId")
            return true
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error withdrawing substitution: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Delete a leave application from Firestore
     */
    fun deleteLeaveApplication(leaveId: String): Boolean {
        try {
            // Delete from leave applications collection
            leaveApplicationsCollection.document(leaveId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        leaveApplicationsCollection.document(leaveId).delete()
                    }
                }
                
            // Also delete from accepted leaves collection if it exists there
            availableLeavesCollection.document(leaveId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        availableLeavesCollection.document(leaveId).delete()
                    }
                }
            
            // Update in-memory list immediately (optimistic update)
            val currentLeaves = _leaveApplications.value?.toMutableList() ?: mutableListOf()
            val initialSize = currentLeaves.size
            currentLeaves.removeIf { it.id == leaveId }
            
            if (currentLeaves.size < initialSize) {
                _leaveApplications.postValue(currentLeaves)
                Log.d("CalendarRepository", "Leave application deleted from memory: $leaveId")
                return true
            }
            
            return false
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error deleting leave application: ${e.message}", e)
            return false
        }
    }

    /**
     * Get a specific user's assigned classes from generated schedules
     */
    suspend fun getUserAssignments(rollNumber: String): List<SubjectAssignmentDetails> {
        if (rollNumber.isBlank()) return emptyList()
        
        val result = mutableListOf<SubjectAssignmentDetails>()
        try {
            val snapshot = firestore.collection("generatedSchedules").get().await()
            
            for (document in snapshot.documents) {
                val data = document.data ?: continue
                val scheduleName = data["name"] as? String ?: data["scheduleName"] as? String ?: document.id
                
                val assignmentsList = (data["optimizedAssignments"] as? List<Map<String, Any>>) 
                    ?: (data["assignments"] as? List<Map<String, Any>>)
                
                if (assignmentsList == null) continue
                
                val referenceData = data["referenceData"] as? Map<String, Any>
                val dayNames = (referenceData?.get("dayNames") as? List<String>) ?: emptyList()
                val slotNames = (referenceData?.get("timeSlotNames") as? List<String>) ?: emptyList()
                val standardDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                val isStandardDays = dayNames.any { it in standardDays }
                
                for (assignmentMap in assignmentsList) {
                    val volunteerRollNo = assignmentMap["volunteerRollNo"] as? String ?: ""
                    
                    // Only process assignments for this specific user
                    if (volunteerRollNo != rollNumber) continue
                    
                    val subjectCode = (assignmentMap["assignedSubject"] as? String) 
                        ?: (assignmentMap["subjectCode"] as? String) ?: ""
                    
                    if (subjectCode.isBlank()) continue
                    
                    var schoolName = ""
                    var classAndSection = ""
                    if (scheduleName.contains(" ")) {
                        val parts = scheduleName.split(" ", limit = 2)
                        schoolName = parts[0].trim()
                        classAndSection = if (parts.size > 1) parts[1].trim() else ""
                    } else if (scheduleName.contains("-")) {
                        val parts = scheduleName.split("-", limit = 2)
                        schoolName = parts[0].trim()
                        classAndSection = if (parts.size > 1) parts[1].trim() else ""
                    } else {
                        schoolName = scheduleName.trim()
                    }
                    
                    val dayIndex = (assignmentMap["dayIndex"] as? Number)?.toInt() ?: 0
                    val slotIndex = (assignmentMap["slotIndex"] as? Number)?.toInt() ?: 0
                    
                    val dayName = if (isStandardDays && dayIndex < standardDays.size) {
                        standardDays[dayIndex]
                    } else if (dayIndex < dayNames.size) {
                        dayNames[dayIndex]
                    } else {
                        "Day ${dayIndex + 1}"
                    }
                    
                    val slotName = if (slotIndex < slotNames.size) slotNames[slotIndex] else "Slot ${slotIndex + 1}"
                    val subjectName = SubjectConstants.SUBJECT_NAMES[subjectCode] ?: subjectCode
                    
                    val details = SubjectAssignmentDetails(
                        volunteerName = assignmentMap["volunteerName"] as? String ?: "",
                        volunteerRollNo = volunteerRollNo,
                        volunteerGroup = assignmentMap["volunteerGroup"] as? String ?: "",
                        subjectCode = subjectCode,
                        subjectName = subjectName,
                        dayName = dayName,
                        slotName = slotName,
                        schoolName = schoolName,
                        classAndSection = classAndSection
                    )
                    result.add(details)
                }
            }
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error fetching user assignments: ${e.message}", e)
        }
        return result
    }
} 
