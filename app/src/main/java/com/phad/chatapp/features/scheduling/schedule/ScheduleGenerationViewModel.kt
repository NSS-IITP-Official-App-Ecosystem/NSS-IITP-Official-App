package com.phad.chatapp.features.scheduling.schedule

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.features.scheduling.constants.ScheduleConstants
import com.phad.chatapp.features.scheduling.models.OptimizedVolunteerAssignment
import com.phad.chatapp.features.scheduling.models.GroupAvailability
import com.phad.chatapp.features.scheduling.models.ScheduleReferenceData
import com.phad.chatapp.features.scheduling.models.SubjectAllocation
import com.phad.chatapp.features.scheduling.models.VolunteerDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

private const val TAG = "ScheduleGenerationViewModel"
private const val ALGO_TAG = "ScheduleAlgo"
private const val VOLUNTEER_PRESETS_COLLECTION = "volunteerPresets"
private const val AVAILABILITY_COLLECTION = "volunteerAvailability"
private const val TEACHING_SLOT_PRESETS_COLLECTION = "teachingSlotPresets"
private const val GENERATED_SCHEDULES_COLLECTION = "generatedSchedules"
private const val GENERATE_SCHEDULE_COLLECTION = "generateSchedule"
private const val STUDENTS_COLLECTION = "ttwStudents"

// School represents a VA preset
data class School(
    val id: String,
    val name: String,
    val days: List<Day>
)

// Day represents a day in the schedule
data class Day(
    val name: String,
    val slots: List<Slot>
)

// Slot represents a time slot with group availability
data class Slot(
    val slotIndex: Int,
    val dayIndex: Int,
    val schoolId: String,
    val schoolName: String,
    val dayName: String,
    val timeLabel: String,
    val availableGroups: List<String>,
    val subjectPriorities: List<SubjectAllocation> = emptyList(),  // Subjects needed for this slot with priorities
    var tfv: Int = 0,
    var assignedVolunteerId: String? = null,
    var assignedVolunteerName: String? = null,
    var assignedVolunteerGroup: String? = null,
    var assignedVolunteerRollNo: String? = null,
    var assignedSubject: String? = null  // Track which subject was assigned
)

// Volunteer represents a volunteer from the VP preset
data class Volunteer(
    val id: String,
    val name: String,
    val rollNo: String,
    val group: String,
    var classCount: Int = 0,  // Track remaining classes to assign
    val interviewScore: Int = 0,  // For ranking volunteers (higher is better)
    val subjectPreferences: List<String> = emptyList(),  // Array of subject preferences (1st = index 0, 2nd = index 1, etc.)
    var isAssigned: Boolean = false,
    var assignedSlot: Slot? = null,
    var assignedSlots: List<Slot> = emptyList() // Track all assigned slots
)

// Group count information for TFV calculation
data class GroupCount(
    val group: String,
    val count: Int
)

/**
 * Log entry for the algorithm viewer
 * @param type Used to color-code the entry in UI
 * @param message The log message to display
 */
data class LogEntry(
    val type: LogType,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LogType {
    ATTEMPT,        // Blue - Starting to try a slot
    ASSIGNED,       // Green - Successful assignment
    SKIP_SAME_DAY,  // Orange - Skipped due to same-day subject rule
    SKIP_NO_PREF,   // Yellow - Skipped due to no preference match
    SKIP_ADJACENCY, // Red - Skipped due to adjacency/daily limit
    SKIP_OTHER,     // Gray - Skipped for other reasons
    ROUND_START,    // Cyan - Starting a new preference round
    ROUND_END,      // Purple - Ending a preference round
    INFO,           // White - General info
    ITERATION_START // Separator for new Assign button click
}


class ScheduleGenerationViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    // State
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    // Selected presets
    private var volunteerPresetId by mutableStateOf("")
    private val availabilityPresetIds = mutableStateListOf<String>()

    // Data
    private val _schools = MutableStateFlow<List<School>>(emptyList())
    val schools: StateFlow<List<School>> = _schools.asStateFlow()

    private val _volunteers = MutableStateFlow<List<Volunteer>>(emptyList())
    val volunteers: StateFlow<List<Volunteer>> = _volunteers.asStateFlow()

    private val _slots = MutableStateFlow<List<Slot>>(emptyList())
    val slots: StateFlow<List<Slot>> = _slots.asStateFlow()

    private val _assignedVolunteers = MutableStateFlow<List<Volunteer>>(emptyList())
    val assignedVolunteers: StateFlow<List<Volunteer>> = _assignedVolunteers.asStateFlow()

    private val _unassignedVolunteers = MutableStateFlow<List<Volunteer>>(emptyList())
    val unassignedVolunteers: StateFlow<List<Volunteer>> = _unassignedVolunteers.asStateFlow()

    // Current slot for assignment
    private val _currentSlot = MutableStateFlow<Slot?>(null)
    val currentSlot: StateFlow<Slot?> = _currentSlot.asStateFlow()

    // Group counts for TFV calculation
    private val _groupCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val groupCounts: StateFlow<Map<String, Int>> = _groupCounts.asStateFlow()

    // NEW: StateFlow for manual selection dialog
    private val _showManualSelectionDialog = MutableStateFlow(false)
    val showManualSelectionDialog: StateFlow<Boolean> = _showManualSelectionDialog.asStateFlow()

    private val _dialogAvailableVolunteers = MutableStateFlow<List<Volunteer>>(emptyList())
    val dialogAvailableVolunteers: StateFlow<List<Volunteer>> = _dialogAvailableVolunteers.asStateFlow()

    private val _dialogSubjectToAssign = MutableStateFlow("")
    val dialogSubjectToAssign: StateFlow<String> = _dialogSubjectToAssign.asStateFlow()
    
    // NEW: Adjacency tracking for same-day slot constraints  
    // Map: volunteerId → Map<dayName, List<assignedSlots>>
    // This tracks which volunteers have assignments on which days and their slot details
    private val volunteerDayAssignments = mutableMapOf<String, MutableMap<String, MutableList<Slot>>>()
    
    // NEW: Last assignment message for UI feedback
    private val _lastAssignmentMessage = MutableStateFlow<String?>(null)
    val lastAssignmentMessage: StateFlow<String?> = _lastAssignmentMessage.asStateFlow()
    
    fun clearAssignmentMessage() {
        _lastAssignmentMessage.value = null
    }
    
    // Algorithm log entries for UI display
    private val _algorithmLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val algorithmLogs: StateFlow<List<LogEntry>> = _algorithmLogs.asStateFlow()
    
    private fun addLog(type: LogType, message: String) {
        _algorithmLogs.value = _algorithmLogs.value + LogEntry(type, message)
        Log.d(ALGO_TAG, "[${type.name}] $message")
    }
    
    fun clearLogs() {
        _algorithmLogs.value = emptyList()
    }

    /**
     * Dismiss the manual selection dialog
     */
    fun dismissManualSelectionDialog() {
        _showManualSelectionDialog.value = false
        _dialogAvailableVolunteers.value = emptyList()
        _dialogSubjectToAssign.value = ""
    }

    /**
     * Track that a volunteer has been assigned to a slot on a specific day
     */
    private fun trackVolunteerDayAssignment(volunteer: Volunteer, slot: Slot) {
        val volunteerId = volunteer.id
        val dayName = slot.dayName
        
        // Initialize maps if needed
        if (!volunteerDayAssignments.containsKey(volunteerId)) {
            volunteerDayAssignments[volunteerId] = mutableMapOf()
        }
        if (!volunteerDayAssignments[volunteerId]!!.containsKey(dayName)) {
            volunteerDayAssignments[volunteerId]!![dayName] = mutableListOf()
        }
        
        // Add this slot to volunteer's day assignments
        volunteerDayAssignments[volunteerId]!![dayName]!!.add(slot)
        
        Log.d(TAG, "📅 Tracked assignment: ${volunteer.name} on $dayName (${slot.timeLabel})")
    }

    /**
     * Manually select a volunteer from the dialog
     */
    fun selectVolunteerManually(volunteer: Volunteer) {
        val subject = _dialogSubjectToAssign.value
        if (subject.isNotEmpty()) {
            performAssignment(volunteer, subject)
            dismissManualSelectionDialog()
        }
    }
    
    /**
     * Extract school prefix (first 2 characters) from school name
     * Example: "RP 8G" → "RP", "AM 10B" → "AM"
     */
    private fun getSchoolPrefix(schoolName: String): String {
        return schoolName.trim().take(2)
    }
    
    /**
     * Check if two time slots are adjacent (consecutive) with a tolerance of 20 minutes
     * Constraints:
     * - Gap between End1 and Start2 must be >= 0 and <= 20 minutes
     * - OR Gap between End2 and Start1 must be >= 0 and <= 20 minutes
     */
    private fun areTimeSlotsAdjacent(time1: String, time2: String): Boolean {
        // Parse time format: "HH:MM-HH:MM"
        val time1Parts = time1.split("-")
        val time2Parts = time2.split("-")
        
        if (time1Parts.size != 2 || time2Parts.size != 2) {
            Log.e(TAG, "Invalid time format: $time1 or $time2")
            return false
        }
        
        val t1Start = parseTimeToMinutes(time1Parts[0])
        val t1End = parseTimeToMinutes(time1Parts[1])
        val t2Start = parseTimeToMinutes(time2Parts[0])
        val t2End = parseTimeToMinutes(time2Parts[1])
        
        if (t1Start == -1 || t1End == -1 || t2Start == -1 || t2End == -1) {
            return false
        }
        
        // Check if t2 follows t1 (t1End -> t2Start)
        val gap1 = t2Start - t1End
        if (gap1 in 0..20) {
            Log.d(TAG, "   ✅ Adjacent Match: $time1 -> $time2 (Gap: ${gap1}m)")
            return true
        }
        
        // Check if t1 follows t2 (t2End -> t1Start)
        val gap2 = t1Start - t2End
        if (gap2 in 0..20) {
            Log.d(TAG, "   ✅ Adjacent Match: $time2 -> $time1 (Gap: ${gap2}m)")
            return true
        }
        
        return false
    }

    /**
     * Helper to parse "HH:MM" into minutes from midnight
     */
    private fun parseTimeToMinutes(timeStr: String): Int {
        return try {
            val parts = timeStr.trim().split(":")
            if (parts.size != 2) return -1
            val hours = parts[0].toInt()
            val minutes = parts[1].toInt()
            (hours * 60) + minutes
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing time: $timeStr", e)
            -1
        }
    }
    
    /**
     * Check if a volunteer can be assigned to a slot on a given day
     * Enforces:
     * 1. Max 2 classes per day
     * 2. Adjacency rule (gap <= 20 mins)
     * 3. Same School rule
     */
    private fun isAdjacentSlotAllowed(volunteer: Volunteer, newSlot: Slot): Boolean {
        val volunteerId = volunteer.id
        val dayName = newSlot.dayName
        
        // Get existing assignments for this volunteer on this day
        val existingSlots = volunteerDayAssignments[volunteerId]?.get(dayName)
        
        // If no existing assignments on this day, allow assignment
        if (existingSlots == null || existingSlots.isEmpty()) {
            Log.d(TAG, "✅ Adjacency check: ${volunteer.name} has no assignments on $dayName - ALLOWED")
            return true
        }
        
        // 1. Max 2 classes per day check
        if (existingSlots.size >= 2) {
            Log.d(TAG, "❌ Daily Limit Reached: ${volunteer.name} already has ${existingSlots.size} classes on $dayName")
            return false
        }
        
        // Volunteer already has assignment(s) on this day
        // Check school prefix and time adjacency for each existing slot
        val newSchoolPrefix = getSchoolPrefix(newSlot.schoolName)
        
        for (existingSlot in existingSlots) {
            val existingSchoolPrefix = getSchoolPrefix(existingSlot.schoolName)
            
            // 2. Check if same school (First 2 chars)
            if (newSchoolPrefix != existingSchoolPrefix) {
                Log.d(TAG, "❌ Adjacency wait: Different school ($newSchoolPrefix vs $existingSchoolPrefix)")
                return false
            }
            
            // 3. Check if adjacent time (0-20 mins gap)
            if (!areTimeSlotsAdjacent(newSlot.timeLabel, existingSlot.timeLabel)) {
                Log.d(TAG, "❌ Adjacency wait: Not adjacent times (${newSlot.timeLabel} vs ${existingSlot.timeLabel})")
                return false
            }
        }
        
        Log.d(TAG, "✅ Adjacency & School check PASSED")
        return true
    }

    /**
     * Check if a subject is already assigned to ANY slot on the given day for the given school
     * This enforces the rule: A subject can only be taught once per day per class
     */
    private fun isSubjectAlreadyAssignedOnDay(schoolId: String, dayName: String, subject: String): Boolean {
        // Get all slots for this school and day
        val slotsOnDay = _slots.value.filter {
            it.schoolId == schoolId && it.dayName == dayName
        }

        // Check if any slot has this subject assigned
        return slotsOnDay.any { slot ->
            slot.assignedSubject != null && slot.assignedSubject.equals(subject, ignoreCase = true)
        }
    }

    /**
     * Initialize the ViewModel with the selected presets
     */
    fun initialize(vpId: String, vaIds: List<String>) {
        viewModelScope.launch {
            try {
                isLoading = true
                errorMessage = null

                volunteerPresetId = vpId
                availabilityPresetIds.clear()
                availabilityPresetIds.addAll(vaIds)
                currentPreferenceRound = 0 // Reset for new session

                // Load volunteer preset
                val volunteers = loadVolunteers(vpId)
                _volunteers.value = volunteers
                _unassignedVolunteers.value = volunteers

                // Compute group counts for TFV calculation
                val groupCountsMap = volunteers
                    .groupBy { it.group }
                    .mapValues { it.value.size }

                Log.d(TAG, "📊 Group counts: $groupCountsMap")

                _groupCounts.value = groupCountsMap

                // Load availability presets and teaching slot presets
                val schools = mutableListOf<School>()
                val allSlots = mutableListOf<Slot>()

                for (presetId in vaIds) {
                    // Load the school directly from the teaching slot preset
                    // The preset ID passed here (from ScheduleGenerationScreen) is the teaching slot preset ID
                    val school = loadSchool(presetId)
                    if (school != null) {
                        schools.add(school)

                        // Extract slots from school
                        val schoolSlots = extractSlotsFromSchool(school)
                        allSlots.addAll(schoolSlots)
                    } else {
                        Log.e(TAG, "❌ Failed to load school for preset ID: $presetId")
                    }
                }

                _schools.value = schools
                _slots.value = allSlots

                // Calculate TFV for all slots
                calculateTFV()

                isLoading = false
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing view model", e)
                errorMessage = "Error loading data: ${e.message}"
                isLoading = false
            }
        }
    }

    /**
     * Load a school from a single teaching slot preset which now contains availability data
     */
    private suspend fun loadSchool(presetId: String): School? {
        try {
            val db = FirebaseFirestore.getInstance()
            val doc = db.collection(TEACHING_SLOT_PRESETS_COLLECTION).document(presetId).get().await()

            if (!doc.exists()) {
                Log.e(TAG, "❌ Preset document $presetId not found")
                return null
            }

            val presetName = doc.getString("presetName") ?: "Unknown"
            val rawColumns = doc.get("columnNames") as? List<*> ?: emptyList<Any>()
            val columnNames = mutableListOf<String>()
            
            // Handle both String list and Map list (legacy/new format support)
            rawColumns.forEach { item ->
                when (item) {
                    is String -> columnNames.add(item)
                    is Map<*, *> -> columnNames.add(item["classTime"] as? String ?: "")
                }
            }
            
            val scheduleData = doc.get("schedule") as? List<Map<String, Any>> ?: emptyList()

            // Get availability data from the same document
            val availabilityMap = doc.get("availability") as? Map<String, Map<String, String>> ?: emptyMap()
            
            // NEW: Load subjects from the teaching slot preset
            val subjectsData = doc.get("subjects") as? List<Map<String, Any>> ?: emptyList()
            val subjects = subjectsData.map { subjectMap ->
                SubjectAllocation(
                    subjectName = subjectMap["subjectName"] as? String ?: "",
                    classCount = (subjectMap["classCount"] as? Number)?.toInt() ?: 0,
                    priority = (subjectMap["priority"] as? Number)?.toInt() ?: 0
                )
            }

            Log.d(TAG, "📋 Loading school from preset: $presetId ($presetName)")
            Log.d(TAG, "📋 Availability map size: ${availabilityMap.size}")
            Log.d(TAG, "📋 Loaded ${subjects.size} subjects: ${subjects.joinToString { "${it.subjectName}(p${it.priority})" }}")

            val days = scheduleData.mapIndexed { index, dayMap ->
                val dayName = dayMap["day"] as? String ?: "Day $index"
                val slots = dayMap["slots"] as? List<Boolean> ?: emptyList()

                val daySlots = slots.mapIndexedNotNull { slotIndex, isActive ->
                    if (isActive) {
                        val timeLabel = if (slotIndex < columnNames.size) columnNames[slotIndex] else "Slot $slotIndex"

                        // Get available groups from the availability map
                        val dayAvailability = availabilityMap[dayName] ?: emptyMap()
                        val groupsString = dayAvailability[slotIndex.toString()] ?: ""

                        // Process groups string
                        val groups = if (groupsString.isNotEmpty()) {
                            // Using expandGroupRanges directly here instead of just split
                            // Actually the existing code used split, and then calculation used expandAllGroupRanges
                            // Let's keep consistency. The string is stored as "1-5,7" or "1,2,3".
                            // The previous code split by comma then trimmed.
                            val splitGroups = groupsString.split(",").map { it.trim() }
                            splitGroups
                        } else emptyList()

                        Slot(
                            slotIndex = slotIndex,
                            dayIndex = index,
                            schoolId = presetId,
                            schoolName = presetName,
                            dayName = dayName,
                            timeLabel = timeLabel,
                            availableGroups = groups,
                            subjectPriorities = subjects  // NEW: Add subjects to each slot
                        )
                    } else null
                }

                Day(
                    name = dayName,
                    slots = daySlots
                )
            }

            return School(
                id = presetId,
                name = presetName,
                days = days
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading school from preset: $presetId", e)
            return null
        }
    }

    /**
     * Load volunteers from the selected VP preset
     */
    private suspend fun loadVolunteers(presetId: String): List<Volunteer> {
        val db = FirebaseFirestore.getInstance()

        Log.d(TAG, "🔍 Attempting to load volunteer preset with ID: $presetId")

        try {
        val presetDoc = db.collection(VOLUNTEER_PRESETS_COLLECTION).document(presetId).get().await()

        if (!presetDoc.exists()) {
                Log.e(TAG, "❌ Volunteer preset with ID $presetId not found in Firestore")
            throw Exception("Volunteer preset not found")
        }

            // Log the entire document for debugging
            Log.d(TAG, "📄 Preset document data: ${presetDoc.data}")

            // Try to get volunteers from the standard "volunteers" field
            val volunteersData = presetDoc.get("volunteers") as? List<Map<String, Any>>

            if (volunteersData != null && volunteersData.isNotEmpty()) {
                Log.d(TAG, "✅ Found standard 'volunteers' field with ${volunteersData.size} entries")
                return processVolunteersData(volunteersData)
            }

            // Fallback: Check if volunteers might be stored in a different format
            Log.d(TAG, "⚠️ Standard 'volunteers' field not found or empty, trying alternative approaches")

            // Try with volunteersList field (alternative field name)
            val volunteersListData = presetDoc.get("volunteersList") as? List<Map<String, Any>>
            if (volunteersListData != null && volunteersListData.isNotEmpty()) {
                Log.d(TAG, "✅ Found alternative 'volunteersList' field with ${volunteersListData.size} entries")
                return processVolunteersData(volunteersListData)
            }

            // If there's a volunteerIds field, we might need to fetch volunteers individually
            val volunteerIds = presetDoc.get("volunteerIds") as? List<String>
            if (volunteerIds != null && volunteerIds.isNotEmpty()) {
                Log.d(TAG, "🔄 Found 'volunteerIds' field with ${volunteerIds.size} entries. Attempting to load individual volunteers")

                // Try to reconstruct volunteers using volunteer IDs and other available data
                val volunteers = mutableListOf<Volunteer>()

                // Check if we have names or need to generate them
                val volunteerNames = presetDoc.get("volunteerNames") as? List<String>
                val volunteerGroups = presetDoc.get("volunteerGroups") as? List<String> ?:
                                      presetDoc.get("groups") as? List<String>

                for (i in volunteerIds.indices) {
                    val id = volunteerIds[i]
                    val name = if (volunteerNames != null && i < volunteerNames.size) {
                        volunteerNames[i]
                    } else {
                        "Volunteer ${i+1}"
                    }

                    val group = if (volunteerGroups != null && i < volunteerGroups.size) {
                        volunteerGroups[i]
                    } else {
                        "1" // Default group
                    }

                    volunteers.add(
                        Volunteer(
                            id = id,
                            name = name,
                            rollNo = "",
                            group = group
                        )
                    )
                }

                if (volunteers.isNotEmpty()) {
                    Log.d(TAG, "✅ Successfully reconstructed ${volunteers.size} volunteers from IDs")
                    return volunteers
                }
            }

            // Last resort: Try to parse the entire document as a structure and extract volunteers
            try {
                Log.d(TAG, "🔄 Attempting to parse entire document for volunteer data")
                val allVolunteers = mutableListOf<Volunteer>()

                presetDoc.data?.forEach { (key, value) ->
                    // Look for map entries that might be volunteers
                    if (value is Map<*, *> && value.containsKey("name") && value.containsKey("group")) {
                        val id = key
                        val name = value["name"].toString()
                        val group = value["group"].toString()
                        // Fix roll number issue: prioritize rollNo field
                        val rollNo = value["rollNo"]?.toString() ?: value["rollNumber"]?.toString() ?: id

                        allVolunteers.add(
                            Volunteer(
                                id = id,
                                name = name,
                                rollNo = rollNo,
                                group = group
                            )
                        )
                    }
                }

                if (allVolunteers.isNotEmpty()) {
                    Log.d(TAG, "✅ Successfully extracted ${allVolunteers.size} volunteers from document fields")
                    return allVolunteers
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error attempting to parse document for volunteers", e)
            }

            Log.e(TAG, "❌ Could not find or reconstruct any volunteers data for preset $presetId")
            return emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading volunteers from preset $presetId", e)
            throw e
        }
    }

    /**
     * Process volunteers data from a list of maps
     * OPTIMIZED: Batch fetch all student details in one query
     */
    private suspend fun processVolunteersData(volunteersData: List<Map<String, Any>>): List<Volunteer> {
        Log.d(TAG, "🔍 Processing ${volunteersData.size} volunteer entries")
        
        val db = FirebaseFirestore.getInstance()
        
        // Step 1: Extract all roll numbers
        val rollNumbers = volunteersData.mapNotNull { it["rollNo"] as? String }
        
        // Step 2: Batch fetch all student details at once (MUCH FASTER!)
        val studentDetailsMap = mutableMapOf<String, Map<String, Any>>()
        try {
            // Firebase allows max 10 items per 'in' query, so we batch in chunks of 10
            rollNumbers.chunked(10).forEach { chunk ->
                val querySnapshot = db.collection(STUDENTS_COLLECTION)
                    .whereIn("rollNumber", chunk)  // Batch query
                    .get()
                    .await()
                
                querySnapshot.documents.forEach { doc ->
                    val rollNo = doc.getString("rollNumber") ?: return@forEach
                    studentDetailsMap[rollNo] = doc.data ?: emptyMap()
                }
            }
            Log.d(TAG, "📊 Batch fetched details for ${studentDetailsMap.size} students")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Error batch fetching student details", e)
        }

        // Step 3: Process volunteers with the cached student data
        val volunteers = volunteersData.mapIndexedNotNull { index, volunteerMap ->
            try {
                val id = volunteerMap["rollNo"] as? String ?: volunteerMap["id"] as? String
                if (id == null) {
                    Log.e(TAG, "❌ Volunteer $index is missing 'rollNo' or 'id' field")
                    return@mapIndexedNotNull null
                }

                val name = volunteerMap["name"] as? String
                if (name == null) {
                    Log.e(TAG, "❌ Volunteer $index (id: $id) is missing 'name' field")
                    return@mapIndexedNotNull null
                }

                val rollNo = volunteerMap["rollNo"] as? String ?: volunteerMap["rollNumber"] as? String ?: id
                val groupRaw = volunteerMap["group"]
                if (groupRaw == null) {
                    Log.e(TAG, "❌ Volunteer $index (id: $id, name: $name) is missing 'group' field")
                    return@mapIndexedNotNull null
                }

                val group = groupRaw.toString()
                val classCount = (volunteerMap["classCount"] as? Number)?.toInt() ?: 0
                
                // Get student details from cached map
                val studentData = studentDetailsMap[rollNo]
                val interviewScore = (studentData?.get("interviewScore") as? Number)?.toInt() ?: 0
                
                // Get subject preferences array (can be any length)
                val subjectPreferences = (studentData?.get("subjectPreferences") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: emptyList()

                if (index < 5) {  // Log first 5 for verification
                    Log.d(TAG, "👤 Processed: $name, Roll: $rollNo, Group: $group, ClassCount: $classCount, Score: $interviewScore, Subjects: $subjectPreferences")
                }

                Volunteer(
                    id = id,
                    name = name,
                    rollNo = rollNo,
                    group = group,
                    classCount = classCount,
                    interviewScore = interviewScore,
                    subjectPreferences = subjectPreferences
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing volunteer $index", e)
                null
            }
        }

        Log.d(TAG, "✅ Successfully processed ${volunteers.size} volunteers")
        return volunteers
    }



    /**
     * Extract slots from a school for easier access
     */
    private fun extractSlotsFromSchool(school: School): List<Slot> {
        val slots = mutableListOf<Slot>()

        for (day in school.days) {
            slots.addAll(day.slots)
        }

        return slots
    }

    /**
     * Calculate TFV for all slots
     */
    fun calculateTFV() {
        Log.d(TAG, "⚙️ Calculating TFV for ${_slots.value.size} slots")
        Log.d(TAG, "🔢 Current group counts: ${_groupCounts.value}")

        // Create a normalized version of the group counts map that works with both string and integer group IDs
        val normalizedGroupCounts = mutableMapOf<String, Int>()
        _groupCounts.value.forEach { (group, count) ->
            normalizedGroupCounts[group.toString()] = count
        }

        Log.d(TAG, "🔢 Normalized group counts: $normalizedGroupCounts")

        val updatedSlots = _slots.value.map { slot ->
            // Get the available groups for this slot and expand ranges
            val rawGroups = slot.availableGroups
            val expandedGroups = expandAllGroupRanges(rawGroups)

            Log.d(TAG, "🔍 Slot ${slot.dayName} - ${slot.timeLabel}, Available groups (raw): $rawGroups")
            Log.d(TAG, "🔍 Slot ${slot.dayName} - ${slot.timeLabel}, Available groups (expanded): $expandedGroups")

            // Calculate TFV by summing the counts of each expanded available group
            val tfv = expandedGroups.sumOf { group ->
                // Try both the original group and the normalized version
                val countFromOriginal = _groupCounts.value[group] ?: 0
                val countFromNormalized = normalizedGroupCounts[group] ?: 0
                val count = maxOf(countFromOriginal, countFromNormalized)

                Log.d(TAG, "   Group $group has $count volunteers (original: $countFromOriginal, normalized: $countFromNormalized)")
                count
            }

            Log.d(TAG, "📝 Slot TFV calculated: $tfv")

            slot.copy(tfv = tfv)
        }

        _slots.value = updatedSlots

        // Update current slot (if any)
        _currentSlot.value = _currentSlot.value?.let { current ->
            updatedSlots.find { it.slotIndex == current.slotIndex &&
                                it.dayIndex == current.dayIndex &&
                                it.schoolId == current.schoolId }
        }
    }

    /**
     * Select a slot for assignment
     */
    fun selectSlot(slot: Slot?) {
        _currentSlot.value = slot
    }

    /**
     * Get slots for a specific school
     */
    fun getSlotsForSchool(schoolId: String): List<Slot> {
        return _slots.value.filter { it.schoolId == schoolId }
    }

    /**
     * Get the slot with the lowest TFV
     */
    fun getLowestTFVSlot(): Slot? {
        return _slots.value
            .filter { it.assignedVolunteerId == null && it.tfv > 0 }
            .minByOrNull { it.tfv }
    }

    /**
     * Auto assign volunteers to slots using round-robin preference-based approach
     * NEW ALGORITHM:
     * - Process ALL unassigned slots at preference level 1 first
     * - Then process ALL remaining unassigned slots at preference level 2
     * - Continue for levels 3, 4, 5... until no more preferences or no more slots
     */
    /**
     * Auto assign volunteers to slots using round-robin preference-based approach
     * IMPROVED ALGORITHM:
     * - For each preference level (1st, 2nd, etc.):
     *   - Iterate through ALL unassigned slots sorted by TFV (Hardest first).
     *   - If a slot finds a match at this level -> Assign, Recalculate TFV, RESTART this level's scan.
     *   - If a slot fails -> Skip to next hardest slot.
     *   - Only move to next preference level when NO slot can be filled at current level.
     */
    // Track current preference round for stepwise assignment
    private var currentPreferenceRound = 0

    /**
     * Auto assign volunteers to ALL slots (Batch Mode)
     * Resets round counter to 0 and runs full algorithm.
     */
    fun autoAssignVolunteers(): Int {
        currentPreferenceRound = 0 // Reset for batch run
        addLog(LogType.ITERATION_START, "━━━━━━ New Assignment Run ━━━━━━")
        addLog(LogType.INFO, "Starting auto-assignment...")
        Log.d(TAG, "🚀 Starting auto-assignment with IMPROVED round-robin preference approach")
        
        // Find the maximum number of preferences any volunteer has
        val maxPreferenceLevel = _unassignedVolunteers.value.maxOfOrNull { it.subjectPreferences.size } ?: 0
        
        if (maxPreferenceLevel == 0) {
            Log.e(TAG, "❌ No volunteers have subject preferences - cannot auto-assign")
            return 0
        }
        
        Log.d(TAG, "📊 Max preference levels to check: $maxPreferenceLevel")
        
        var totalAssignments = 0

        // Process each preference level (round) in order
        for (preferenceLevel in 0 until maxPreferenceLevel) {
            // ... (rest of logic same as before)
            Log.d(TAG, "\n" + "=".repeat(80))
            Log.d(TAG, "🔄 STARTING PREFERENCE ROUND ${preferenceLevel + 1}")
            Log.d(TAG, "=".repeat(80))
            addLog(LogType.ROUND_START, "── Round ${preferenceLevel + 1} (Pref #${preferenceLevel + 1}) ──")
            
            var matchFoundInThisPass = true
            var assignmentsInThisRound = 0
            
            // Loop until we can no longer make ANY assignments at this preference level
            while (matchFoundInThisPass) {
                matchFoundInThisPass = false
                
                // Get ALL unassigned slots sorted by TFV (lowest/hardest first)
                val unassignedSlots = _slots.value
                    .filter { it.assignedVolunteerId == null && it.tfv > 0 }
                    .sortedBy { it.tfv }
                
                Log.d(TAG, "📋 Scanning ${unassignedSlots.size} slots at Preference Level ${preferenceLevel + 1}")

                for (slot in unassignedSlots) {
                    _currentSlot.value = slot
                    val assigned = assignVolunteer(preferenceLevel)
                    if (assigned) {
                        assignmentsInThisRound++
                        totalAssignments++
                        matchFoundInThisPass = true
                        break 
                    }
                }
            }
            Log.d(TAG, "📈 Round ${preferenceLevel + 1} complete. Total assignments: $assignmentsInThisRound")
            addLog(LogType.ROUND_END, "Round ${preferenceLevel + 1} done: $assignmentsInThisRound assigned")
        }
        return totalAssignments
    }

    /**
     * Assign exactly ONE slot using Round-Robin logic (Stepwise Mode)
     * Resumes from currentPreferenceRound.
     * Returns TRUE if a slot was assigned, FALSE if no more assignments possible.
     */
    fun assignNextSlotInRoundRobin(): Boolean {
        Log.d(TAG, "👣 Stepwise assignment: Starting at Round ${currentPreferenceRound + 1}")
        
        val maxPreferenceLevel = _unassignedVolunteers.value.maxOfOrNull { it.subjectPreferences.size } ?: 0
        if (maxPreferenceLevel == 0) return false

        // NEW: If we already exhausted rounds in previous clicks, RESET to 0 to try again
        // This handles cases where manual assignments might have opened up new possibilities (though unlikely for strict priority)
        // or effectively allows "wrapping around" or searching again.
        if (currentPreferenceRound >= maxPreferenceLevel) {
            Log.d(TAG, "🔄 Rounds exhausted previously. Resetting to Round 1 for new attempt.")
            addLog(LogType.INFO, "Restarting search from Preference Level 1...")
            currentPreferenceRound = 0
        }

        // Loop through rounds starting from current
        while (currentPreferenceRound < maxPreferenceLevel) {
            // Get unassigned slots sorted by TFV (Hardest First)
            val unassignedSlots = _slots.value
                .filter { it.assignedVolunteerId == null && it.tfv > 0 }
                .sortedBy { it.tfv }

            if (unassignedSlots.isEmpty()) {
                Log.d(TAG, "✅ No unassigned slots left!")
                return false
            }

            Log.d(TAG, "📋 Checking ${unassignedSlots.size} slots at Round ${currentPreferenceRound + 1}")

            // Try to find ONE assignment in this round
            for (slot in unassignedSlots) {
                _currentSlot.value = slot
                val assigned = assignVolunteer(currentPreferenceRound)
                
                if (assigned) {
                    addLog(LogType.INFO, "✓ Single step assignment complete")
                    
                    // Set success message for UI with updated details
                    val updatedSlot = _slots.value.find { 
                        it.slotIndex == slot.slotIndex && 
                        it.dayIndex == slot.dayIndex && 
                        it.schoolId == slot.schoolId 
                    }
                    
                    val volunteerName = updatedSlot?.assignedVolunteerName ?: "Volunteer"
                    val rollNo = updatedSlot?.assignedVolunteerRollNo ?: ""
                    val slotInfo = "${slot.schoolName} ${slot.dayName} ${slot.timeLabel}"
                    
                    val volunteerDisplay = if (rollNo.isNotEmpty()) "$volunteerName ($rollNo)" else volunteerName
                    _lastAssignmentMessage.value = "Assigned: $volunteerDisplay to $slotInfo"
                    
                    _currentSlot.value = null
                    
                    // Reset to Round 1 for next click - always try 1st preferences first
                    currentPreferenceRound = 0
                    
                    return true // STOP after 1 assignment
                }
            }

            // If we get here, NO slots could be filled at this preference level
            Log.d(TAG, "⏩ Round ${currentPreferenceRound + 1} exhausted. Moving to next round.")
            addLog(LogType.ROUND_END, "Round ${currentPreferenceRound + 1} exhausted (No matches)")
            currentPreferenceRound++ // Advance round
        }

        Log.d(TAG, "❌ All rounds exhausted. No assignment possible.")
        addLog(LogType.INFO, "❌ Auto-assign failed: No matches found in any round")
        return false
    }
    
    /**
     * Auto-assign a volunteer to the CURRENT slot only
     * Tries all preference levels (1st, 2nd, 3rd...) until a match is found
     * Used when user clicks "Assign Volunteer Automatically" button for a specific slot
     */
    fun assignVolunteerToCurrentSlot(): Boolean {
        val currentSlot = _currentSlot.value
        if (currentSlot == null) {
            Log.e(TAG, "No slot selected for assignment")
            return false
        }
        
        Log.d(TAG, "🎯 Attempting auto-assignment for slot: ${currentSlot.schoolName} ${currentSlot.dayName} ${currentSlot.timeLabel}")
        
        // Find max preference levels available
        val maxPreferenceLevel = _unassignedVolunteers.value.maxOfOrNull { it.subjectPreferences.size } ?: 0
        
        // Try each preference level until we find a match
        for (preferenceLevel in 0 until maxPreferenceLevel) {
            Log.d(TAG, "   Trying preference level ${preferenceLevel + 1}...")
            
            val assigned = assignVolunteer(preferenceLevel)
            if (assigned) {
                Log.d(TAG, "   ✅ Successfully assigned at preference level ${preferenceLevel + 1}")
                return true
            }
        }
        
        // No matches at any preference level
        Log.d(TAG, "   ❌ No volunteers available at any preference level")
        return false
    }

    /**
     * Assign a volunteer to the next available slot, trying slots in TFV order
     * Returns true if assignment successful, false if ALL slots fail
     * NEW: Iterates through ALL unassigned slots sorted by TFV until one succeeds
     */
    fun assignLowestTFVSlot(): Boolean {
        // Get all unassigned slots sorted by TFV (lowest first)
        val unassignedSlots = _slots.value
            .filter { it.assignedVolunteerId == null && it.tfv > 0 }
            .sortedBy { it.tfv }
        
        if (unassignedSlots.isEmpty()) {
            Log.d(TAG, "No unassigned slots available")
            return false
        }
        
        Log.d(TAG, "🎯 Attempting auto-assign for ${unassignedSlots.size} slots in TFV order...")
        
        // Try each slot in TFV order until we find one that works
        for ((index, slot) in unassignedSlots.withIndex()) {
            _currentSlot.value = slot
            
            Log.d(TAG, "📋 Trying slot ${index + 1}/${unassignedSlots.size}: ${slot.schoolName} ${slot.dayName} ${slot.timeLabel} (TFV: ${slot.tfv})")
            
            // Try automatic assignment through all preference levels
            val assigned = assignVolunteerToCurrentSlot()
            
            if (assigned) {
                // Assignment successful - get updated slot info for the message
                val updatedSlot = _slots.value.find { 
                    it.slotIndex == slot.slotIndex && 
                    it.dayIndex == slot.dayIndex && 
                    it.schoolId == slot.schoolId 
                }
                
                // Set success message
                val volunteerName = updatedSlot?.assignedVolunteerName ?: "Volunteer"
                val subjectName = updatedSlot?.assignedSubject ?: "Subject"
                val slotInfo = "${slot.schoolName} ${slot.dayName} ${slot.timeLabel}"
                _lastAssignmentMessage.value = "$volunteerName assigned to $slotInfo for $subjectName"
                
                _currentSlot.value = null
                Log.d(TAG, "✅ Assignment successful for slot ${index + 1}!")
                return true
            } else {
                Log.d(TAG, "⏭️ Slot ${index + 1} failed, trying next...")
            }
        }
        
        // All slots failed
        Log.d(TAG, "❌ No suitable match found in any of the ${unassignedSlots.size} slots")
        _currentSlot.value = null
        return false
    }

    /**
     * Helper function to expand group ranges (e.g., "11-18" becomes ["11", "12", "13", "14", "15", "16", "17", "18"])
     * Examples:
     * "11-18" -> ["11", "12", "13", "14", "15", "16", "17", "18"]
     * "1-3" -> ["1", "2", "3"]
     * "5" -> ["5"]
     * "1-3,11-18" -> ["1", "2", "3", "11", "12", "13", "14", "15", "16", "17", "18"]
     */
    private fun expandGroupRanges(groupString: String): List<String> {
        val expandedGroups = mutableListOf<String>()

        // Check if this is a range pattern (e.g., "11-18", "1-3")
        val rangePattern = Regex("^(\\d+)-(\\d+)$")
        val matchResult = rangePattern.find(groupString.trim())

        if (matchResult != null) {
            // This is a range, expand it
            val startGroup = matchResult.groupValues[1].toIntOrNull()
            val endGroup = matchResult.groupValues[2].toIntOrNull()

            if (startGroup != null && endGroup != null && startGroup <= endGroup) {
                // Expand the range into individual groups
                for (group in startGroup..endGroup) {
                    expandedGroups.add(group.toString())
                }
            } else {
                // Invalid range, treat as single group
                expandedGroups.add(groupString.trim())
            }
        } else {
            // Not a range, treat as individual group
            expandedGroups.add(groupString.trim())
        }

        return expandedGroups
    }

    /**
     * Helper function to expand all group ranges in a list of group strings
     */
    private fun expandAllGroupRanges(groups: List<String>): List<String> {
        val allExpandedGroups = mutableListOf<String>()

        groups.forEach { groupEntry ->
            // Handle comma-separated entries like "1-3,11-18,20"
            val groupParts = groupEntry.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            groupParts.forEach { part ->
                val expandedGroups = expandGroupRanges(part)
                allExpandedGroups.addAll(expandedGroups)
            }
        }

        return allExpandedGroups.distinct() // Remove duplicates
    }

    /**
     * Test function to validate group range expansion logic
     * This can be called during development to verify the fix is working
     */
    fun testGroupRangeExpansion() {
        Log.d(TAG, "🧪 Testing group range expansion logic...")

        // Test cases
        val testCases = listOf(
            "11-18" to listOf("11", "12", "13", "14", "15", "16", "17", "18"),
            "1-3" to listOf("1", "2", "3"),
            "5" to listOf("5"),
            "1-3,11-18" to listOf("1", "2", "3", "11", "12", "13", "14", "15", "16", "17", "18"),
            "5,11-18,20" to listOf("5", "11", "12", "13", "14", "15", "16", "17", "18", "20")
        )

        testCases.forEach { (input, expected) ->
            val result = expandAllGroupRanges(listOf(input))
            val passed = result == expected
            Log.d(TAG, "   Test: '$input' -> $result (Expected: $expected) ${if (passed) "✅ PASS" else "❌ FAIL"}")
        }

        Log.d(TAG, "🧪 Group range expansion tests completed")
    }



    /**
     * Test function to demonstrate the new optimized data structure
     * This shows the improved efficiency and organization of the index-based storage
     */
    fun testOptimizedDataStructure() {
        Log.d(TAG, "🧪 Testing optimized data structure...")

        // Create sample data to demonstrate the optimized structure
        val sampleVolunteers = listOf(
            Volunteer(
                id = "vol1",
                name = "John Doe",
                rollNo = "2301CS01",
                group = "1"
            ).apply {
                assignedSlot = Slot(
                    slotIndex = 0,
                    dayIndex = 0,
                    schoolId = "school1",
                    schoolName = "AM 10B",
                    dayName = "Mon",
                    timeLabel = "8:00",
                    availableGroups = listOf("1", "2")
                )
            },
            Volunteer(
                id = "vol2",
                name = "Jane Smith",
                rollNo = "2301CS02",
                group = "2"
            ).apply {
                assignedSlot = Slot(
                    slotIndex = 1,
                    dayIndex = 0,
                    schoolId = "school1",
                    schoolName = "AM 10B",
                    dayName = "Mon",
                    timeLabel = "9:00",
                    availableGroups = listOf("2", "3")
                )
            }
        )

        // Test optimized data creation (commented out since createPresetDocumentData is now suspend)
        // val optimizedDocumentData = createPresetDocumentData(
        //     presetName = "AM 10B",
        //     volunteers = sampleVolunteers,
        //     scheduleId = "test-schedule-id"
        // )

        Log.d(TAG, "📊 Optimized data structure test (commented out due to suspend function):")

        // Log reference data (commented out)
        // val referenceData = optimizedDocumentData["referenceData"] as? Map<String, Any>
        // Log.d(TAG, "   Reference Data:")
        // Log.d(TAG, "     Day Names: ${referenceData?.get("dayNames")}")
        // Log.d(TAG, "     Time Slot Names: ${referenceData?.get("timeSlotNames")}")

        // Log group availability (commented out)
        // val groupAvailability = optimizedDocumentData["groupAvailability"] as? List<Map<String, Any>>
        // Log.d(TAG, "   Group Availability (${groupAvailability?.size} entries):")
        // groupAvailability?.forEach { availability ->
        //     Log.d(TAG, "     Day ${availability["dayIndex"]}, Slot ${availability["slotIndex"]}: ${availability["availableGroups"]}")
        // }

        // Log optimized assignments (commented out)
        // val optimizedAssignments = optimizedDocumentData["optimizedAssignments"] as? List<Map<String, Any>>
        // Log.d(TAG, "   Optimized Assignments (${optimizedAssignments?.size} entries):")
        // optimizedAssignments?.forEach { assignment ->
        //     Log.d(TAG, "     ${assignment["volunteerName"]} -> Day ${assignment["dayIndex"]}, Slot ${assignment["slotIndex"]}")
        // }

        // Test conversion back to readable format
        val slots = sampleVolunteers.mapNotNull { it.assignedSlot }
        val referenceDataObj = extractReferenceDataFromSlots(slots)
        val optimizedAssignmentsList = sampleVolunteers.map { volunteer ->
            val slot = volunteer.assignedSlot!!
            OptimizedVolunteerAssignment(
                volunteerName = volunteer.name,
                volunteerRollNo = volunteer.rollNo,
                volunteerGroup = volunteer.group,
                dayIndex = slot.dayIndex,
                slotIndex = slot.slotIndex,
                interviewScore = 0, // Default for test data
                assignedSubject = null
            )
        }

        val readableData = convertOptimizedDataToReadable(optimizedAssignmentsList, referenceDataObj)
        Log.d(TAG, "   Converted back to readable format:")
        readableData.forEach { readable ->
            Log.d(TAG, "     ${readable["volunteerName"]} -> ${readable["dayName"]} ${readable["timeSlotName"]}")
        }

        Log.d(TAG, "🎯 Data size comparison:")
        Log.d(TAG, "   Legacy structure would store day/time strings ${sampleVolunteers.size} times")
        Log.d(TAG, "   Optimized structure stores day/time strings only once as reference data")
        Log.d(TAG, "   Volunteer IDs removed from assignment data")
        Log.d(TAG, "   Group availability separated for better organization")

        Log.d(TAG, "🧪 Optimized data structure test completed")
    }

    /**
     * Assign a volunteer to the currently selected slot at a specific preference level
     * NEW: Round-robin approach - only checks volunteers with matching preference at specified level
     * 
     * @param preferenceLevel Which preference level to check (0 = 1st preference, 1 = 2nd, etc.)
     * @return true if assignment successful, false if no match found at this preference level
     */
    fun assignVolunteer(preferenceLevel: Int = 0): Boolean {
        val currentSlot = _currentSlot.value ?: return false

        Log.d(ALGO_TAG, "--------------------------------------------------")
        Log.d(ALGO_TAG, "🎬 START: Attempting assignment for slot: ${currentSlot.schoolName} | ${currentSlot.dayName} | ${currentSlot.timeLabel}")
        Log.d(ALGO_TAG, "📊 Slot Info: TFV=${currentSlot.tfv}, PrefLevel=${preferenceLevel + 1}")
        addLog(LogType.ATTEMPT, "${currentSlot.schoolName} | ${currentSlot.dayName} | ${currentSlot.timeLabel} (TFV=${currentSlot.tfv}, Pref=${preferenceLevel + 1})")

        // Get available groups for this slot and expand any ranges
        val rawAvailableGroups = currentSlot.availableGroups
        if (rawAvailableGroups.isEmpty()) {
            Log.d(ALGO_TAG, "❌ STOP: No available groups for this slot")
            return false
        }

        // Expand group ranges (e.g., "11-18" becomes ["11", "12", "13", "14", "15", "16", "17", "18"])
        val expandedAvailableGroups = expandAllGroupRanges(rawAvailableGroups)
        Log.d(ALGO_TAG, "👥 Groups: Raw=$rawAvailableGroups -> Expanded=$expandedAvailableGroups")

        // Get available volunteers from these groups who still have classes to teach
        val availableVolunteers = _unassignedVolunteers.value.filter { volunteer ->
            volunteer.classCount > 0 && expandedAvailableGroups.contains(volunteer.group)
        }

        Log.d(ALGO_TAG, "🔍 Candidates: Found ${availableVolunteers.size} volunteers in groups $expandedAvailableGroups with classes > 0")

        if (availableVolunteers.isEmpty()) {
            Log.d(ALGO_TAG, "❌ STOP: No eligible volunteers found (Group match + ClassCount > 0)")
            return false  // No volunteers available
        }

        // Get highest priority subject that still needs classes (with classCount > 0)
        Log.d(ALGO_TAG, "📚 Selecting Subject (Strict Priority)...")
        
        // Strictly pick the FIRST subject with classCount > 0
        val topPrioritySubject = currentSlot.subjectPriorities
            .sortedBy { it.priority }
            .firstOrNull { it.classCount > 0 }

        if (topPrioritySubject == null) {
            Log.d(ALGO_TAG, "❌ STOP: No subjects need classes for this slot")
            return false
        }

        // Validate the top priority subject against the Same-Day constraint
        val alreadyAssigned = isSubjectAlreadyAssignedOnDay(
            currentSlot.schoolId, 
            currentSlot.dayName, 
            topPrioritySubject.subjectName
        )
        
        if (alreadyAssigned) {
            Log.d(ALGO_TAG, "⛔ STOP: Top priority subject '${topPrioritySubject.subjectName}' (Priority ${topPrioritySubject.priority}) is blocked by Same-Day rule.")
            Log.d(ALGO_TAG, "   Strict Priority Rule: We DO NOT fallback to lower priority subjects.")
            Log.d(ALGO_TAG, "   Action: Skipping this slot entirely for now.")
            if (preferenceLevel == 0) {
                addLog(LogType.SKIP_SAME_DAY, "Skipped: '${topPrioritySubject.subjectName}' blocked (Same-Day rule)")
            }
            return false
        }

        val subjectToAssign = topPrioritySubject
        Log.d(ALGO_TAG, "✅ Selected '${subjectToAssign.subjectName}' (Priority ${subjectToAssign.priority}, Needs ${subjectToAssign.classCount})")

        // NEW: Only check volunteers with matching preference at THIS specific level
        val matchingVolunteers = availableVolunteers.filter { volunteer ->
            preferenceLevel < volunteer.subjectPreferences.size &&
            volunteer.subjectPreferences[preferenceLevel].equals(subjectToAssign.subjectName, ignoreCase = true)
        }.sortedWith(compareBy<Volunteer> { it.assignedSlots.size }.thenByDescending { it.interviewScore })

        if (matchingVolunteers.isEmpty()) {
             Log.d(ALGO_TAG, "⏩ SKIP: No volunteers have '${subjectToAssign.subjectName}' as Preference #${preferenceLevel + 1}")
            addLog(LogType.SKIP_NO_PREF, "Skipped: No volunteer has '${subjectToAssign.subjectName}' as Pref #${preferenceLevel + 1}")
            // No matches at this preference level - that's OK in round-robin approach
            return false
        }

        Log.d(ALGO_TAG, "🎯 Found ${matchingVolunteers.size} match(es) for '${subjectToAssign.subjectName}' (Pref #${preferenceLevel + 1})")

        // NEW: Try each matching volunteer, checking adjacency constraints
        for (volunteer in matchingVolunteers) {
            // Check if adjacency rule allows this assignment
            if (!isAdjacentSlotAllowed(volunteer, currentSlot)) {
                Log.d(ALGO_TAG, "   🚫 Validating ${volunteer.name}: Adjacency FAIL (Has conflict or gap)")
                continue  // Try next volunteer
            }

            // Adjacency check passed - perform assignment!
            Log.d(ALGO_TAG, "   ✅ Validating ${volunteer.name}: PASS (Score ${volunteer.interviewScore}) -> ASSIGNING")
            
            // Determine preference position for log
            val prefPos = volunteer.subjectPreferences.indexOfFirst { it.equals(subjectToAssign.subjectName, ignoreCase = true) } + 1
            val prefLabel = when(prefPos) { 1 -> "1st" 2 -> "2nd" 3 -> "3rd" else -> "${prefPos}th" }
            addLog(LogType.ASSIGNED, "✓ ${volunteer.name} (${volunteer.rollNo}) | ${subjectToAssign.subjectName} ($prefLabel Pref) | Score=${volunteer.interviewScore}")
            
            performAssignment(volunteer, subjectToAssign.subjectName)
            
            // NEW: Track this assignment for future adjacency checks
            trackVolunteerDayAssignment(volunteer, currentSlot)
            
            return true
        }

        // All matching volunteers failed adjacency check
        Log.d(ALGO_TAG, "⚠️ All ${matchingVolunteers.size} matching volunteers failed adjacency check")
        addLog(LogType.SKIP_ADJACENCY, "Skipped: ${matchingVolunteers.size} volunteer(s) failed adjacency/daily limit")
        return false
    }

    /**
     * Internal method to perform the actual assignment
     */
    private fun performAssignment(volunteer: Volunteer, subject: String) {
        val currentSlot = _currentSlot.value ?: return

        // Update slot with assignment
        val updatedSlot = currentSlot.copy(
            assignedVolunteerId = volunteer.id,
            assignedVolunteerName = volunteer.name,
            assignedVolunteerGroup = volunteer.group,
            assignedVolunteerRollNo = volunteer.rollNo,
            assignedSubject = subject  // NEW: Track assigned subject
        )

        // Update volunteer - decrement class count
        val updatedVolunteer = volunteer.copy(
            classCount = volunteer.classCount - 1,  // NEW: Decrement
            isAssigned = if (volunteer.classCount - 1 == 0) true else volunteer.isAssigned,  // Only mark fully assigned if no classes left
            assignedSlot = updatedSlot,
            assignedSlots = volunteer.assignedSlots + updatedSlot
        )

        Log.d(ALGO_TAG, "📉 UPDATE STATE:")
        Log.d(ALGO_TAG, "   Volunteer: ${volunteer.name} classes ${volunteer.classCount} -> ${updatedVolunteer.classCount}")

        // Update slots list - IMPORTANT: Must update subject counts for ALL slots of this school
        _slots.value = _slots.value.map { slot ->
            // Check if this slot belongs to the same school
            if (slot.schoolId == currentSlot.schoolId) {
                // 1. Decrement the specific subject count for this slot's view of the priorities
                val updatedPriorities = slot.subjectPriorities.map { subj ->
                    if (subj.subjectName == subject) {
                        val newCount = subj.classCount - 1
                        // Log only for the current slot to avoid spam, or log generally
                        if (slot.slotIndex == currentSlot.slotIndex && slot.dayIndex == currentSlot.dayIndex) {
                             Log.d(ALGO_TAG, "   Subject ${subj.subjectName}: Needs ${subj.classCount} -> $newCount (Global update for school ${slot.schoolName})")
                        }
                        subj.copy(classCount = newCount)
                    } else subj
                }
                
                // 2. If this is the CURRENT assigned slot, update its assignment details too
                if (slot.slotIndex == currentSlot.slotIndex && slot.dayIndex == currentSlot.dayIndex) {
                    updatedSlot.copy(subjectPriorities = updatedPriorities)
                } else {
                    // For other slots in the same school, just update the subject priorities (counts)
                    slot.copy(subjectPriorities = updatedPriorities)
                }
            } else {
                // Different school, no changes
                slot
            }
        }

        // Update volunteers list
        _volunteers.value = _volunteers.value.map {
            if (it.id == volunteer.id) updatedVolunteer else it
        }

        // Update assigned/unassigned lists
        if (updatedVolunteer.classCount == 0) {
            // Volunteer has no more classes - move to assigned
            _assignedVolunteers.value = _assignedVolunteers.value + updatedVolunteer
            _unassignedVolunteers.value = _unassignedVolunteers.value.filter { it.id != volunteer.id }
            Log.d(ALGO_TAG, "   Status: Volunteer fully assigned (removed from pool)")
        } else {
            // Volunteer still has classes - update in unassigned list
            _unassignedVolunteers.value = _unassignedVolunteers.value.map {
                if (it.id == volunteer.id) updatedVolunteer else it
            }
            Log.d(ALGO_TAG, "   Status: Volunteer remains in pool")
        }

        // Update _currentSlot value to reflect the new counts
        // We need to find the updated version of the current slot from the _slots list
        val refreshedCurrentSlot = _slots.value.find { 
            it.slotIndex == currentSlot.slotIndex && 
            it.dayIndex == currentSlot.dayIndex && 
            it.schoolId == currentSlot.schoolId 
        }
        
        if (refreshedCurrentSlot != null) {
            _currentSlot.value = refreshedCurrentSlot
        } else {
            // Fallback (should not happen)
            _currentSlot.value = updatedSlot
        }

        // Decrement the group count ONLY IF volunteer has no more classes
        if (updatedVolunteer.classCount == 0) {
            val volunteerGroup = volunteer.group
            val updatedGroupCounts = _groupCounts.value.toMutableMap()
            val currentCount = updatedGroupCounts[volunteerGroup] ?: 0
            if (currentCount > 0) {
                updatedGroupCounts[volunteerGroup] = currentCount - 1
                _groupCounts.value = updatedGroupCounts
                Log.d(ALGO_TAG, "   Group ${volunteerGroup}: Available count ${currentCount} -> ${currentCount - 1} (Volunteer fully assigned)")
            }
        } else {
            Log.d(ALGO_TAG, "   Group ${volunteer.group}: Count unchanged (Volunteer still has ${updatedVolunteer.classCount} classes)")
        }



        // Recalculate TFV for all slots
        calculateTFV()
        
        Log.d(TAG, "✅ Assignment complete!")
    }

    /**
     * Attempts to auto-assign a volunteer to the given slot.
     * Returns true if successful, false otherwise.
     */
    fun attemptAutoAssign(slot: Slot): Boolean {
        _currentSlot.value = slot
        val success = assignVolunteerToCurrentSlot() // Try all preference levels
        
        // Always clear current slot after attempt to prevent UI from showing the "choice" dialog
        _currentSlot.value = null
        
        return success
    }

    /**
     * Assign a specific volunteer to the currently selected slot (manual assignment)
     */
    fun assignSpecificVolunteer(volunteer: Volunteer) {
        val currentSlot = _currentSlot.value ?: return
        assignSpecificVolunteerToSlot(volunteer, currentSlot)
    }

    /**
     * Assign a specific volunteer to a specific slot (manual assignment)
     */
    fun assignSpecificVolunteer(volunteer: Volunteer, slot: Slot, subject: String? = null) {
        assignSpecificVolunteerToSlot(volunteer, slot, subject)
    }

    /**
     * Internal method to assign a specific volunteer to a slot
     */
    private fun assignSpecificVolunteerToSlot(volunteer: Volunteer, targetSlot: Slot, subject: String? = null) {
        // Log the manual assignment attempt in same format as auto-assignments
        addLog(LogType.ATTEMPT, "${targetSlot.schoolName} | ${targetSlot.dayName} | ${targetSlot.timeLabel} (TFV=${targetSlot.tfv}, MANUAL)")

        // Check if volunteer is already assigned
        if (volunteer.isAssigned) {
            Log.d(TAG, "❌ Volunteer ${volunteer.name} is already assigned")
            return
        }

        // Check if volunteer's group is available for this slot (expand ranges first)
        val expandedAvailableGroups = expandAllGroupRanges(targetSlot.availableGroups)
        if (!expandedAvailableGroups.contains(volunteer.group)) {
            Log.d(TAG, "❌ Volunteer ${volunteer.name} from group ${volunteer.group} is not available for this slot")
            Log.d(TAG, "   Available groups (raw): ${targetSlot.availableGroups}")
            Log.d(TAG, "   Available groups (expanded): $expandedAvailableGroups")
            // Allow manual override? For now, we return, but manual usually implies force.
            // But the UI filters volunteers, so this check is a safeguard.
            return
        }

        // Check if subject has available classes
        if (subject != null) {
            val slotSubject = targetSlot.subjectPriorities.find { it.subjectName == subject }
            if (slotSubject != null && slotSubject.classCount <= 0) {
                 Log.d(TAG, "❌ Cannot assign ${volunteer.name} to $subject: Subject classes fully occupied")
                 addLog(LogType.INFO, "❌ Failed: $subject requirement already met for this slot")
                 return
            }
        }

        Log.d(TAG, "👤 Manually assigning volunteer ${volunteer.name} from group ${volunteer.group} (Subject: $subject)")
        
        // Log successful manual assignment in same format as auto assignments
        // Calculate preference position if subject is provided
        val prefInfo = if (subject != null) {
            val prefPos = volunteer.subjectPreferences.indexOfFirst { it.equals(subject, ignoreCase = true) } + 1
            val prefLabel = when(prefPos) { 1 -> "1st" 2 -> "2nd" 3 -> "3rd" else -> if (prefPos > 0) "${prefPos}th" else "Unknown" }
            " | $subject ($prefLabel Pref)"
        } else ""
        addLog(LogType.ASSIGNED, "✓ [MANUAL] ${volunteer.name} (${volunteer.rollNo})$prefInfo | Score=${volunteer.interviewScore}")

        // Update the slot
        val updatedSlot = targetSlot.copy(
            assignedVolunteerId = volunteer.id,
            assignedVolunteerName = volunteer.name,
            assignedVolunteerGroup = volunteer.group,
            assignedVolunteerRollNo = volunteer.rollNo,
            assignedSubject = subject
        )

        // Update volunteer
        val newClassCount = if (subject != null) volunteer.classCount - 1 else volunteer.classCount
        val updatedVolunteer = volunteer.copy(
            classCount = newClassCount,
            isAssigned = if (newClassCount <= 0) true else volunteer.isAssigned,
            assignedSlot = updatedSlot,
            assignedSlots = volunteer.assignedSlots + updatedSlot
        )
        
        // NEW: Track this assignment for future adjacency checks
        trackVolunteerDayAssignment(updatedVolunteer, updatedSlot)

        // Update slots list
        _slots.value = _slots.value.map { slot ->
            // Update subject priorities for all slots of this school if a subject was assigned
            val updatedPriorities = if (subject != null && slot.schoolId == targetSlot.schoolId) {
                slot.subjectPriorities.map { subj ->
                    if (subj.subjectName == subject) {
                        subj.copy(classCount = subj.classCount - 1)
                    } else subj
                }
            } else slot.subjectPriorities

            if (slot.slotIndex == targetSlot.slotIndex &&
                slot.dayIndex == targetSlot.dayIndex &&
                slot.schoolId == targetSlot.schoolId) {
                updatedSlot.copy(subjectPriorities = updatedPriorities)
            } else {
                 if (slot.schoolId == targetSlot.schoolId) {
                    slot.copy(subjectPriorities = updatedPriorities)
                 } else {
                    slot
                 }
            }
        }

        // Update volunteers list
        _volunteers.value = _volunteers.value.map {
            if (it.id == volunteer.id) updatedVolunteer else it
        }

        // Update assigned/unassigned lists
        _assignedVolunteers.value = _assignedVolunteers.value + updatedVolunteer
        _unassignedVolunteers.value = _unassignedVolunteers.value.filter { it.id != volunteer.id }

        // Update current slot only if it matches the target slot
        if (_currentSlot.value?.let { current ->
            current.slotIndex == targetSlot.slotIndex &&
            current.dayIndex == targetSlot.dayIndex &&
            current.schoolId == targetSlot.schoolId
        } == true) {
            _currentSlot.value = updatedSlot
        }

        // Decrement the group count ONLY IF volunteer has no more classes
        val volunteerGroup = volunteer.group
        if (updatedVolunteer.classCount <= 0) {
            val updatedGroupCounts = _groupCounts.value.toMutableMap()
            val currentCount = updatedGroupCounts[volunteerGroup] ?: 0
            if (currentCount > 0) {
                updatedGroupCounts[volunteerGroup] = currentCount - 1
                _groupCounts.value = updatedGroupCounts

                Log.d(TAG, "📊 Updated group counts after manual assignment: Group $volunteerGroup count reduced to ${currentCount - 1} (Volunteer fully assigned)")
            }
        } else {
            Log.d(TAG, "📊 Group counts unchanged after manual assignment: Group $volunteerGroup (Volunteer still has ${updatedVolunteer.classCount} classes)")
        }

        // Recalculate TFV for all slots
        calculateTFV()
    }

    /**
     * Save the generated schedule using optimized data structure
     * Uses indices instead of redundant string data and removes volunteer IDs
     */
    suspend fun saveSchedule(): String {
        val db = FirebaseFirestore.getInstance()
        val scheduleId = UUID.randomUUID().toString()

        // Group assignments by teaching slot preset (school name) based on SLOTS instead of volunteers
        // This ensures that volunteers with multiple classes have ALL their classes saved
        val assignedSlots = _slots.value.filter { it.assignedVolunteerId != null }
        val slotsByPreset = assignedSlots.groupBy { it.schoolName }

        Log.d(TAG, "💾 Saving optimized schedule with document-level preset organization")
        Log.d(TAG, "📊 Found ${slotsByPreset.size} teaching slot presets with ${assignedSlots.size} total assignments")

        // Create a batch to save all preset documents
        val batch = db.batch()

        for ((presetName, slots) in slotsByPreset) {
            if (presetName == "Unknown" || slots.isEmpty()) {
                Log.w(TAG, "⚠️ Skipping preset '$presetName' with ${slots.size} slots")
                continue
            }

            Log.d(TAG, "🏫 Processing preset '$presetName' with ${slots.size} assigned slots")

            // Create optimized preset document data using slots
            val presetDocumentData = createPresetDocumentData(presetName, slots, scheduleId)

            // Create document reference with preset name as document ID
            val presetDocRef = db.collection(GENERATED_SCHEDULES_COLLECTION).document(presetName)
            batch.set(presetDocRef, presetDocumentData)

            Log.d(TAG, "📝 Adding optimized preset document '$presetName' with ${slots.size} assignments")
        }

        // Commit all preset documents in a single batch
        batch.commit().await()

        Log.d(TAG, "🎉 Successfully saved optimized schedule with ${slotsByPreset.size} preset documents")
        return scheduleId
    }

    /**
     * Parse volunteer details from Firebase document data
     * Supports both students collection and generateSchedule collection field formats
     */
    private fun parseVolunteerDetails(data: Map<String, Any>, rollNo: String): VolunteerDetails {
        Log.d(TAG, "📋 All field names: ${data.keys.joinToString(", ")}")
        // Log all fields that contain "preference" or "Preference"
        data.keys.filter { it.contains("preference", ignoreCase = true) }.forEach { key ->
            Log.d(TAG, "📋 Preference field: '$key' = '${data[key]}'")
        }

        // New ttwStudents fields
        val subjectPref1 = data["subjectPreference1"] as? String
            ?: data["SubjectPreference1"] as? String
            ?: ""

        val subjectPref2 = data["subjectPreference2"] as? String
            ?: data["SubjectPreference2"] as? String
            ?: ""

        val subjectPref3 = data["subjectPreference3"] as? String
            ?: data["SubjectPreference3"] as? String
            ?: ""

        // Interview score may be absent in new collection
        val interviewScore = (data["interviewScore"] as? Number)?.toInt() ?: 0

        val name = data["name"] as? String ?: ""

        // Map academicGroup (stringified) to group
        val group = (data["academicGroup"] ?: data["AcademicGroup"])?.toString() ?: ""

        Log.d(TAG, "📋 Subject preferences found: 1='$subjectPref1', 2='$subjectPref2', 3='$subjectPref3'")
        Log.d(TAG, "📋 Interview score: $interviewScore")
        Log.d(TAG, "📋 Raw field values: interviewScore=${data["interviewScore"]}")

        return VolunteerDetails(
            name = name,
            rollNo = rollNo,
            group = group,
            interviewScore = interviewScore,
            subjectPreference1 = subjectPref1,
            subjectPreference2 = subjectPref2,
            subjectPreference3 = subjectPref3
        )
    }

    /**
     * Fetch volunteer details from students collection
     * Falls back to generateSchedule collection if not found in students
     */
    private suspend fun fetchVolunteerDetails(rollNo: String): VolunteerDetails? {
        return try {
            val db = FirebaseFirestore.getInstance()

            Log.d(TAG, "📋 Searching for volunteer: $rollNo in students collection")

            // First try students collection - direct document lookup by roll number
            val studentsDirectDoc = db.collection(STUDENTS_COLLECTION).document(rollNo).get().await()
            if (studentsDirectDoc.exists()) {
                val data = studentsDirectDoc.data ?: return null
                Log.d(TAG, "📋 Found volunteer via direct lookup in students collection for $rollNo: ${data.keys}")
                return parseVolunteerDetails(data, rollNo)
            }

            // Search in students collection using various possible field names
            val studentsQuery = db.collection(STUDENTS_COLLECTION)
                .whereEqualTo("rollNumber", rollNo)
                .limit(1)
                .get()
                .await()

            if (studentsQuery.documents.isNotEmpty()) {
                val studentDoc = studentsQuery.documents.first()
                val data = studentDoc.data ?: return null
                Log.d(TAG, "📋 Found volunteer via rollNumber query in students collection for $rollNo: ${data.keys}")
                return parseVolunteerDetails(data, rollNo)
            }

            // Try alternative field names in students collection
            val studentsAltQuery = db.collection(STUDENTS_COLLECTION)
                .whereEqualTo("Roll_No", rollNo)
                .limit(1)
                .get()
                .await()

            if (studentsAltQuery.documents.isNotEmpty()) {
                val studentDoc = studentsAltQuery.documents.first()
                val data = studentDoc.data ?: return null
                Log.d(TAG, "📋 Found volunteer via Roll_No query in students collection for $rollNo: ${data.keys}")
                return parseVolunteerDetails(data, rollNo)
            }

            Log.d(TAG, "📋 Not found in students collection, trying generateSchedule collection as fallback")

            // Fallback to generateSchedule collection for backward compatibility
            val generateDirectDoc = db.collection(GENERATE_SCHEDULE_COLLECTION).document(rollNo).get().await()
            if (generateDirectDoc.exists()) {
                val data = generateDirectDoc.data ?: return null
                Log.d(TAG, "📋 Found volunteer via direct lookup in generateSchedule collection for $rollNo: ${data.keys}")
                return parseVolunteerDetails(data, rollNo)
            }

            // Search in generateSchedule collection using various possible field names
            val generateQuery = db.collection(GENERATE_SCHEDULE_COLLECTION)
                .whereEqualTo("Roll_No", rollNo)
                .limit(1)
                .get()
                .await()

            if (generateQuery.documents.isNotEmpty()) {
                val volunteerDoc = generateQuery.documents.first()
                val data = volunteerDoc.data ?: return null
                Log.d(TAG, "📋 Found volunteer via Roll_No query in generateSchedule collection for $rollNo: ${data.keys}")
                return parseVolunteerDetails(data, rollNo)
            }

            Log.w(TAG, "⚠️ No volunteer details found for roll number: $rollNo in both students and generateSchedule collections")
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching volunteer details for $rollNo from students/generateSchedule collections", e)
            null
        }
    }

    /**
     * Create optimized preset document data using indices instead of redundant strings
     */
    /**
     * Create optimized preset document data using indices instead of redundant strings
     * Updated to accept Slots instead of Volunteers to handle multiple classes per volunteer correctly
     */
    private suspend fun createPresetDocumentData(
        presetName: String,
        slots: List<Slot>,
        scheduleId: String
    ): Map<String, Any> {
        // Extract reference data from the slots
        val referenceData = if (slots.isNotEmpty()) {
            extractReferenceDataFromSlots(slots)
        } else {
            ScheduleReferenceData()
        }

        Log.d(TAG, "🔍 Fetching enhanced volunteer details for ${slots.size} assignments")

        // Create optimized volunteer assignments with enhanced data from students collection
        val optimizedAssignments = slots.map { slot ->
            val rollNo = slot.assignedVolunteerRollNo ?: ""
            val name = slot.assignedVolunteerName ?: ""
            val group = slot.assignedVolunteerGroup ?: ""

            // Fetch additional volunteer details from students collection (with generateSchedule fallback)
            val volunteerDetails = try {
                if (rollNo.isNotEmpty()) {
                    fetchVolunteerDetails(rollNo)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching details for volunteer $rollNo", e)
                null
            }

<<<<<<< HEAD
            if (name.isNotEmpty()) {
                Log.d(TAG, "📋 Assignment: ${name} (${rollNo}) -> ${slot.schoolName} ${slot.timeLabel}")
            }
=======
            Log.d(TAG, "📋 Volunteer ${volunteer.name} (${volunteer.rollNo}): " +
                    "Interview Score: ${volunteerDetails?.interviewScore ?: 0}")
>>>>>>> Attendance

            OptimizedVolunteerAssignment(
                volunteerName = name,
                volunteerRollNo = rollNo,
                volunteerGroup = group,
                dayIndex = slot.dayIndex,
                slotIndex = slot.slotIndex,
                interviewScore = volunteerDetails?.interviewScore ?: 0,
                assignedSubject = slot.assignedSubject
            )
        }



        // Create group availability data
        val groupAvailabilityData = extractGroupAvailabilityData(slots)

        return mapOf(
            "name" to presetName,
            "totalVolunteers" to slots.mapNotNull { it.assignedVolunteerId }.distinct().size,
            "totalAssignments" to slots.size,

            // Reference data (stored once per preset) - removed schoolName field
            "referenceData" to mapOf(
                "dayNames" to referenceData.dayNames,
                "timeSlotNames" to referenceData.timeSlotNames,
                "totalDays" to referenceData.totalDays,
                "totalSlots" to referenceData.totalSlots
            ),

            // Group availability data (separate from assignments)
            "groupAvailability" to groupAvailabilityData.map { availability ->
                mapOf(
                    "dayIndex" to availability.dayIndex,
                    "slotIndex" to availability.slotIndex,
                    "availableGroups" to availability.availableGroups
                )
            },

            // Optimized assignments with enhanced volunteer data
            "optimizedAssignments" to optimizedAssignments.map { assignment ->
                mapOf(
                    "volunteerName" to assignment.volunteerName,
                    "volunteerRollNo" to assignment.volunteerRollNo,
                    "volunteerGroup" to assignment.volunteerGroup,
                    "dayIndex" to assignment.dayIndex,
                    "slotIndex" to assignment.slotIndex,
                    "interviewScore" to assignment.interviewScore,
                    "assignedSubject" to assignment.assignedSubject
                )
            }
        )
    }



    /**
     * Save unassigned volunteers as a new preset
     */
    suspend fun saveUnassignedVolunteersPreset(presetName: String): String {
        val db = FirebaseFirestore.getInstance()
        val presetId = UUID.randomUUID().toString()

        Log.d(TAG, "💾 Creating unassigned volunteers preset '$presetName' with ${_unassignedVolunteers.value.size} volunteers")

        // Create volunteers list
        val volunteersList = _unassignedVolunteers.value.map { volunteer ->
            mapOf(
                "rollNo" to volunteer.rollNo,
                "name" to volunteer.name,
                "group" to volunteer.group,
                "classCount" to volunteer.classCount
            )
        }

        // Group counts
        val groupCounts = _unassignedVolunteers.value
            .groupBy { it.group }
            .mapValues { it.value.size }

        // Use preset name as document ID
        val documentId = presetName

        // Create preset document (excluding id field as per user preference)
        val presetData = mapOf(
            "name" to presetName,
            "volunteerCount" to _unassignedVolunteers.value.size,
            "groupCounts" to groupCounts,
            "volunteers" to volunteersList
        )

        // Save to Firestore using preset name as document ID
        db.collection(VOLUNTEER_PRESETS_COLLECTION)
            .document(documentId)
            .set(presetData)
            .await()

        Log.d(TAG, "✅ Created unassigned volunteers preset '$presetName' with ID: $documentId and ${_unassignedVolunteers.value.size} volunteers")
        return documentId
    }

    /**
     * Save assigned volunteers as separate presets for each teaching slot preset
     */
    suspend fun saveAssignedVolunteersPresets(): List<String> {
        val db = FirebaseFirestore.getInstance()
        val createdPresetIds = mutableListOf<String>()

        // Group assigned volunteers by their teaching slot preset (school name)
        val volunteersByPreset = _assignedVolunteers.value.groupBy { volunteer ->
            volunteer.assignedSlot?.schoolName ?: "Unknown"
        }

        Log.d(TAG, "📋 Creating volunteer presets for ${volunteersByPreset.size} teaching slot presets")

        // Debug: Log the grouping details
        volunteersByPreset.forEach { (presetName, volunteers) ->
            Log.d(TAG, "📊 Teaching slot preset '$presetName' has ${volunteers.size} assigned volunteers:")
            volunteers.forEach { volunteer ->
                Log.d(TAG, "   👤 ${volunteer.name} (Group: ${volunteer.group}) -> Slot: ${volunteer.assignedSlot?.dayName} ${volunteer.assignedSlot?.timeLabel}")
            }
        }

        for ((presetName, volunteers) in volunteersByPreset) {
            if (volunteers.isEmpty() || presetName == "Unknown") {
                Log.w(TAG, "⚠️ Skipping preset creation for '$presetName' with ${volunteers.size} volunteers")
                continue
            }

            Log.d(TAG, "👥 Creating volunteer preset '$presetName' with ${volunteers.size} volunteers")

            // Use preset name as document ID for consistent naming
            // Use preset name as document ID for consistent naming
            // Prefix with "Z_" to keep auto-generated presets at the bottom
            val documentId = "Z_$presetName"

            // Create volunteers list for this teaching slot preset
            // For assigned volunteers, classCount should be 1 (they were assigned 1 class)
            // Using volunteer.id as it's populated from rollNo during loading
            val volunteersList = volunteers.map { volunteer ->
                mapOf(
                    "rollNo" to volunteer.rollNo,
                    "name" to volunteer.name,
                    "group" to volunteer.group,
                    "classCount" to 1 // Each volunteer was assigned 1 class slot
                )
            }

            // Group counts for volunteers in this teaching slot preset
            val groupCounts = volunteers
                .groupBy { it.group }
                .mapValues { it.value.size }

            // Create preset document (excluding id field as per user preference)
            val presetData = mapOf(
                "name" to documentId, // Use the Z_ prefixed name
                "volunteerCount" to volunteers.size,
                "groupCounts" to groupCounts,
                "volunteers" to volunteersList
            )

            // Save to Firestore using preset name as document ID (this will overwrite if preset already exists)
            db.collection(VOLUNTEER_PRESETS_COLLECTION)
                .document(documentId)
                .set(presetData)
                .await()

            createdPresetIds.add(documentId)
            Log.d(TAG, "✅ Created volunteer preset '$presetName' with ID: $documentId and ${volunteers.size} volunteers")
        }

        Log.d(TAG, "🎉 Successfully created ${createdPresetIds.size} volunteer presets")
        return createdPresetIds
    }



    /**
     * Extract reference data (day names, time slot names) from slots
     */
    private fun extractReferenceDataFromSlots(slots: List<Slot>): ScheduleReferenceData {
        if (slots.isEmpty()) {
            return ScheduleReferenceData()
        }

        // Get unique day names and time slot names, sorted by their indices
        val dayMap = slots.map { it.dayIndex to it.dayName }.toSet().sortedBy { it.first }
        val timeSlotMap = slots.map { it.slotIndex to it.timeLabel }.toSet().sortedBy { it.first }

        val dayNames = dayMap.map { it.second }
        val timeSlotNames = timeSlotMap.map { it.second }
        val schoolName = slots.firstOrNull()?.schoolName ?: ""

        return ScheduleReferenceData(
            dayNames = dayNames,
            timeSlotNames = timeSlotNames,
            schoolName = schoolName,
            totalDays = dayNames.size,
            totalSlots = timeSlotNames.size
        )
    }

    /**
     * Extract group availability data from slots
     */
    private fun extractGroupAvailabilityData(slots: List<Slot>): List<GroupAvailability> {
        return slots.map { slot ->
            GroupAvailability(
                dayIndex = slot.dayIndex,
                slotIndex = slot.slotIndex,
                availableGroups = slot.availableGroups
            )
        }.distinctBy { "${it.dayIndex}_${it.slotIndex}" } // Remove duplicates
    }

    /**
     * Utility function to convert optimized data back to readable format
     * Useful for displaying data or backward compatibility
     */
    fun convertOptimizedDataToReadable(
        optimizedAssignments: List<OptimizedVolunteerAssignment>,
        referenceData: ScheduleReferenceData
    ): List<Map<String, Any>> {
        return optimizedAssignments.map { assignment ->
            val dayName = if (assignment.dayIndex < referenceData.dayNames.size) {
                referenceData.dayNames[assignment.dayIndex]
            } else {
                ScheduleConstants.getDayName(assignment.dayIndex)
            }

            val timeSlotName = if (assignment.slotIndex < referenceData.timeSlotNames.size) {
                referenceData.timeSlotNames[assignment.slotIndex]
            } else {
                ScheduleConstants.getDefaultTimeSlotName(assignment.slotIndex)
            }

            mapOf(
                "volunteerName" to assignment.volunteerName,
                "volunteerRollNo" to assignment.volunteerRollNo,
                "volunteerGroup" to assignment.volunteerGroup,
                "dayIndex" to assignment.dayIndex,
                "slotIndex" to assignment.slotIndex,
                "dayName" to dayName,
                "timeSlotName" to timeSlotName,
                "schoolName" to referenceData.schoolName,
                "interviewScore" to assignment.interviewScore,
                "assignedSubject" to (assignment.assignedSubject ?: "")
            )
        }
    }
}