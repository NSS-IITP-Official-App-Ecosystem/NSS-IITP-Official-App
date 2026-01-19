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
import com.phad.chatapp.features.scheduling.models.VolunteerDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

private const val TAG = "ScheduleViewModel"
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
    var tfv: Int = 0,
    var assignedVolunteerId: String? = null,
    var assignedVolunteerName: String? = null,
    var assignedVolunteerGroup: String? = null,
    var assignedVolunteerRollNo: String? = null
)

// Volunteer represents a volunteer from the VP preset
data class Volunteer(
    val id: String,
    val name: String,
    val rollNo: String,
    val group: String,
    var isAssigned: Boolean = false,
    var assignedSlot: Slot? = null
)

// Group count information for TFV calculation
data class GroupCount(
    val group: String,
    val count: Int
)



class ScheduleGenerationViewModel : ViewModel() {

    // State
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    // Selected presets
    private var volunteerPresetId by mutableStateOf("")
    private var availabilityPresetIds = mutableStateListOf<String>()

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

            Log.d(TAG, "📋 Loading school from preset: $presetId ($presetName)")
            Log.d(TAG, "📋 Availability map size: ${availabilityMap.size}")

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
                            availableGroups = groups
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
     */
    private fun processVolunteersData(volunteersData: List<Map<String, Any>>): List<Volunteer> {
        Log.d(TAG, "🔍 Processing ${volunteersData.size} volunteer entries")

        val volunteers = volunteersData.mapIndexedNotNull { index, volunteerMap ->
            try {
                Log.d(TAG, "👤 Processing volunteer $index: $volunteerMap")

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

            // Fix roll number issue: prioritize rollNo field
            val rollNo = volunteerMap["rollNo"] as? String ?: volunteerMap["rollNumber"] as? String ?: id

            // Handle group which could be a string or an integer
                val groupRaw = volunteerMap["group"]
                if (groupRaw == null) {
                    Log.e(TAG, "❌ Volunteer $index (id: $id, name: $name) is missing 'group' field")
                    return@mapIndexedNotNull null
                }

            val group = groupRaw.toString()

                Log.d(TAG, "👤 Successfully processed volunteer: $name, Roll Number: $rollNo, Group raw: $groupRaw (${groupRaw.javaClass.name}), Group: $group")

            Volunteer(
                id = id,
                name = name,
                rollNo = rollNo,
                group = group
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
     * Auto assign volunteers to slots based on lowest TFV
     */
    fun autoAssignVolunteers() {
        // Keep assigning until there are no more slots with non-zero TFV or no more volunteers
        while (_unassignedVolunteers.value.isNotEmpty()) {
            val lowestTFVSlot = getLowestTFVSlot() ?: break
            _currentSlot.value = lowestTFVSlot
            assignVolunteer()
        }
    }

    /**
     * Assign a single volunteer to the lowest TFV slot
     */
    fun assignLowestTFVSlot() {
        val lowestTFVSlot = getLowestTFVSlot() ?: return
        _currentSlot.value = lowestTFVSlot
        assignVolunteer()
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
                subjectPreference1 = "",
                subjectPreference2 = "",
                subjectPreference3 = ""
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
     * Assign a volunteer to the currently selected slot
     */
    fun assignVolunteer() {
        val currentSlot = _currentSlot.value ?: return

        // Get available groups for this slot and expand any ranges
        val rawAvailableGroups = currentSlot.availableGroups
        if (rawAvailableGroups.isEmpty()) {
            return
        }

        // Expand group ranges (e.g., "11-18" becomes ["11", "12", "13", "14", "15", "16", "17", "18"])
        val expandedAvailableGroups = expandAllGroupRanges(rawAvailableGroups)

        Log.d(TAG, "🔍 Raw available groups: $rawAvailableGroups")
        Log.d(TAG, "🔍 Expanded available groups: $expandedAvailableGroups")

        // Count unassigned volunteers per available group
        val groupVolunteerCounts = mutableMapOf<String, Int>()
        val groupToVolunteersMap = mutableMapOf<String, List<Volunteer>>()

        expandedAvailableGroups.forEach { group ->
            val volunteersInGroup = _unassignedVolunteers.value.filter { it.group == group }
            groupVolunteerCounts[group] = volunteersInGroup.size
            groupToVolunteersMap[group] = volunteersInGroup

            Log.d(TAG, "📊 Group $group has ${volunteersInGroup.size} unassigned volunteers")
        }

        // Find the group with the maximum unassigned volunteers
        val groupWithMaxVolunteers = groupVolunteerCounts.maxByOrNull { it.value }

        if (groupWithMaxVolunteers == null || groupWithMaxVolunteers.value == 0) {
            Log.d(TAG, "❌ No available volunteers found for any of the groups: $expandedAvailableGroups")
            return
        }

        val selectedGroup = groupWithMaxVolunteers.key
        Log.d(TAG, "✅ Selected group $selectedGroup with ${groupWithMaxVolunteers.value} volunteers")

        // Get volunteers from the selected group
        val volunteersFromSelectedGroup = groupToVolunteersMap[selectedGroup] ?: emptyList()

        if (volunteersFromSelectedGroup.isEmpty()) {
            return
        }

        // Select the first volunteer from the selected group
        val volunteer = volunteersFromSelectedGroup.first()

        Log.d(TAG, "👤 Assigning volunteer ${volunteer.name} from group $selectedGroup")

        // Update the slot
        val updatedSlot = currentSlot.copy(
            assignedVolunteerId = volunteer.id,
            assignedVolunteerName = volunteer.name,
            assignedVolunteerGroup = volunteer.group,
            assignedVolunteerRollNo = volunteer.rollNo
        )

        // Update volunteer
        val updatedVolunteer = volunteer.copy(
            isAssigned = true,
            assignedSlot = updatedSlot
        )

        // Update slots list
        _slots.value = _slots.value.map {
            if (it.slotIndex == currentSlot.slotIndex &&
                it.dayIndex == currentSlot.dayIndex &&
                it.schoolId == currentSlot.schoolId) {
                updatedSlot
            } else {
                it
            }
        }

        // Update volunteers list
        _volunteers.value = _volunteers.value.map {
            if (it.id == volunteer.id) updatedVolunteer else it
        }

        // Update assigned/unassigned lists
        _assignedVolunteers.value = _assignedVolunteers.value + updatedVolunteer
        _unassignedVolunteers.value = _unassignedVolunteers.value.filter { it.id != volunteer.id }

        // Update current slot
        _currentSlot.value = updatedSlot

        // Decrement the group count for the assigned volunteer's group
        val volunteerGroup = volunteer.group
        val updatedGroupCounts = _groupCounts.value.toMutableMap()
        val currentCount = updatedGroupCounts[volunteerGroup] ?: 0
        if (currentCount > 0) {
            updatedGroupCounts[volunteerGroup] = currentCount - 1
            _groupCounts.value = updatedGroupCounts

            Log.d(TAG, "📊 Updated group counts after assignment: Group $volunteerGroup count reduced to ${currentCount - 1}")
        }

        // Recalculate TFV for all slots
        calculateTFV()
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
    fun assignSpecificVolunteer(volunteer: Volunteer, slot: Slot) {
        assignSpecificVolunteerToSlot(volunteer, slot)
    }

    /**
     * Internal method to assign a specific volunteer to a slot
     */
    private fun assignSpecificVolunteerToSlot(volunteer: Volunteer, targetSlot: Slot) {

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
            return
        }

        Log.d(TAG, "👤 Manually assigning volunteer ${volunteer.name} from group ${volunteer.group}")

        // Update the slot
        val updatedSlot = targetSlot.copy(
            assignedVolunteerId = volunteer.id,
            assignedVolunteerName = volunteer.name,
            assignedVolunteerGroup = volunteer.group,
            assignedVolunteerRollNo = volunteer.rollNo
        )

        // Update volunteer
        val updatedVolunteer = volunteer.copy(
            isAssigned = true,
            assignedSlot = updatedSlot
        )

        // Update slots list
        _slots.value = _slots.value.map {
            if (it.slotIndex == targetSlot.slotIndex &&
                it.dayIndex == targetSlot.dayIndex &&
                it.schoolId == targetSlot.schoolId) {
                updatedSlot
            } else {
                it
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

        // Decrement the group count for the assigned volunteer's group
        val volunteerGroup = volunteer.group
        val updatedGroupCounts = _groupCounts.value.toMutableMap()
        val currentCount = updatedGroupCounts[volunteerGroup] ?: 0
        if (currentCount > 0) {
            updatedGroupCounts[volunteerGroup] = currentCount - 1
            _groupCounts.value = updatedGroupCounts

            Log.d(TAG, "📊 Updated group counts after manual assignment: Group $volunteerGroup count reduced to ${currentCount - 1}")
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

        // Group assignments by teaching slot preset (school name)
        val assignmentsByPreset = _assignedVolunteers.value.groupBy { volunteer ->
            volunteer.assignedSlot?.schoolName ?: "Unknown"
        }

        Log.d(TAG, "💾 Saving optimized schedule with document-level preset organization")
        Log.d(TAG, "📊 Found ${assignmentsByPreset.size} teaching slot presets with assignments")

        // Create a batch to save all preset documents
        val batch = db.batch()

        for ((presetName, volunteers) in assignmentsByPreset) {
            if (presetName == "Unknown" || volunteers.isEmpty()) {
                Log.w(TAG, "⚠️ Skipping preset '$presetName' with ${volunteers.size} volunteers")
                continue
            }

            Log.d(TAG, "🏫 Processing preset '$presetName' with ${volunteers.size} volunteers")

            // Create optimized preset document data
            val presetDocumentData = createPresetDocumentData(presetName, volunteers, scheduleId)

            // Create document reference with preset name as document ID
            val presetDocRef = db.collection(GENERATED_SCHEDULES_COLLECTION).document(presetName)
            batch.set(presetDocRef, presetDocumentData)

            Log.d(TAG, "📝 Adding optimized preset document '$presetName' with ${volunteers.size} total volunteers")
        }

        // Commit all preset documents in a single batch
        batch.commit().await()

        Log.d(TAG, "🎉 Successfully saved optimized schedule with ${assignmentsByPreset.size} preset documents")
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
    private suspend fun createPresetDocumentData(
        presetName: String,
        volunteers: List<Volunteer>,
        scheduleId: String
    ): Map<String, Any> {
        // Extract reference data from the first volunteer's slot
        val firstSlot = volunteers.firstOrNull()?.assignedSlot
        val referenceData = if (firstSlot != null) {
            extractReferenceDataFromSlots(volunteers.mapNotNull { it.assignedSlot })
        } else {
            ScheduleReferenceData()
        }

        Log.d(TAG, "🔍 Fetching enhanced volunteer details for ${volunteers.size} volunteers")

        // Create optimized volunteer assignments with enhanced data from students collection
        val optimizedAssignments = volunteers.map { volunteer ->
            val slot = volunteer.assignedSlot!!

            // Fetch additional volunteer details from students collection (with generateSchedule fallback)
            val volunteerDetails = try {
                fetchVolunteerDetails(volunteer.rollNo)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching details for volunteer ${volunteer.rollNo}", e)
                null
            }

            Log.d(TAG, "📋 Volunteer ${volunteer.name} (${volunteer.rollNo}): " +
                    "Interview Score: ${volunteerDetails?.interviewScore ?: 0}, " +
                    "Preferences: [${volunteerDetails?.subjectPreference1 ?: ""}, " +
                    "${volunteerDetails?.subjectPreference2 ?: ""}, " +
                    "${volunteerDetails?.subjectPreference3 ?: ""}]")

            OptimizedVolunteerAssignment(
                volunteerName = volunteer.name,
                volunteerRollNo = volunteer.rollNo,
                volunteerGroup = volunteer.group,
                dayIndex = slot.dayIndex,
                slotIndex = slot.slotIndex,
                interviewScore = volunteerDetails?.interviewScore ?: 0,
                subjectPreference1 = volunteerDetails?.subjectPreference1 ?: "",
                subjectPreference2 = volunteerDetails?.subjectPreference2 ?: "",
                subjectPreference3 = volunteerDetails?.subjectPreference3 ?: ""
            )
        }

        // Create group availability data
        val groupAvailabilityData = extractGroupAvailabilityData(volunteers.mapNotNull { it.assignedSlot })

        // Group optimized assignments by slot for organized structure
        val optimizedSlotAssignments = optimizedAssignments.groupBy { assignment ->
            mapOf(
                "dayIndex" to assignment.dayIndex,
                "slotIndex" to assignment.slotIndex
            )
        }.map { (slotInfo, slotAssignments) ->
            mapOf(
                "dayIndex" to slotInfo["dayIndex"],
                "slotIndex" to slotInfo["slotIndex"],
                "volunteerCount" to slotAssignments.size,
                "assignments" to slotAssignments.map { assignment ->
                    mapOf(
                        "volunteerName" to assignment.volunteerName,
                        "volunteerRollNo" to assignment.volunteerRollNo,
                        "volunteerGroup" to assignment.volunteerGroup,
                        "interviewScore" to assignment.interviewScore,
                        "subjectPreference1" to assignment.subjectPreference1,
                        "subjectPreference2" to assignment.subjectPreference2,
                        "subjectPreference3" to assignment.subjectPreference3
                    )
                }
            )
        }

        return mapOf(
            "name" to presetName,
            "totalVolunteers" to volunteers.size,

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
                    "subjectPreference1" to assignment.subjectPreference1,
                    "subjectPreference2" to assignment.subjectPreference2,
                    "subjectPreference3" to assignment.subjectPreference3
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
                "rollNo" to volunteer.id,
                "name" to volunteer.name,
                "group" to volunteer.group
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
            val documentId = presetName

            // Create volunteers list for this teaching slot preset
            val volunteersList = volunteers.map { volunteer ->
                mapOf(
                    "rollNo" to volunteer.id,
                    "name" to volunteer.name,
                    "group" to volunteer.group
                )
            }

            // Group counts for volunteers in this teaching slot preset
            val groupCounts = volunteers
                .groupBy { it.group }
                .mapValues { it.value.size }

            // Create preset document (excluding id field as per user preference)
            val presetData = mapOf(
                "name" to presetName,
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
                "subjectPreference1" to assignment.subjectPreference1,
                "subjectPreference2" to assignment.subjectPreference2,
                "subjectPreference3" to assignment.subjectPreference3
            )
        }
    }
}