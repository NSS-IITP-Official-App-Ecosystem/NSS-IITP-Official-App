package com.phad.chatapp.features.scheduling.schedule


import android.util.Log

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.phad.chatapp.features.scheduling.ui.theme.*
import com.phad.chatapp.features.scheduling.ui.theme.SchedulingTheme
import com.phad.chatapp.features.scheduling.schedule.StandardButton


private const val TAG = "SetAvailabilityScreen"
private const val TEACHING_SLOT_PRESETS_COLLECTION = "teachingSlotPresets"
private const val AVAILABILITY_COLLECTION = "volunteerAvailability"

// Natural sorting function to handle numbers correctly (AM 9B before AM 10G)
private fun naturalSortKey(text: String): String {
    return text.replace(Regex("\\d+")) { matchResult ->
        matchResult.value.padStart(10, '0')
    }
}

// Data classes for the UI - renamed to avoid conflicts
data class AvailabilityDaySchedule(val day: String, val slots: List<Boolean>)

data class AvailabilityPreset(
    val id: String = "",
    val presetName: String = "",
    val columnNames: List<String> = emptyList(),
    val freeGroupTimes: List<String> = emptyList(),
    val schedule: List<AvailabilityDaySchedule> = emptyList()
)

// Data class for storing availability data
data class AvailabilitySlot(
    val slotIndex: Int,
    val dayIndex: Int,
    val value: String = ""
)



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetAvailabilityScreen(navController: NavController, presetId: String = "new") {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()



    // State
    var isLoading by remember { mutableStateOf(true) }
    var preset by remember { mutableStateOf<AvailabilityPreset?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Availability data
    var availabilityData by remember { mutableStateOf<List<AvailabilitySlot>>(emptyList()) }

    // Selected slot state for dialog
    var showInputDialog by remember { mutableStateOf(false) }
    var selectedSlot by remember { mutableStateOf<AvailabilitySlot?>(null) }
    var selectedNumbers by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Saving state
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    // Copy feature state
    var showCopyDialog by remember { mutableStateOf(false) }
    var availablePresets by remember { mutableStateOf<List<AvailabilityPreset>>(emptyList()) }
    var isLoadingPresets by remember { mutableStateOf(false) }

    // Excel Upload State
    // MIME type for .xlsx
    val excelLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            coroutineScope.launch {
                Log.d("ExcelDebug", "Launcher callback triggered. URI: $it")
                isLoading = true
                try {
                     Log.d("ExcelDebug", "Calling parseExcelAndGetFreeGroups...")
                     val newSlots = com.phad.chatapp.features.scheduling.schedule.ExcelAvailabilityParser.parseExcelAndGetFreeGroups(
                         navController.context,
                         it,
                         preset!!
                     )
                     Log.d("ExcelDebug", "Parser returned ${newSlots.size} slots")
                     
                     if (newSlots.isNotEmpty()) {
                        // Merge new slots: Update existing ones or add new ones
                        val currentList = availabilityData.toMutableList()
                        var updateCount = 0
                        
                        newSlots.forEach { newSlot ->
                            val index = currentList.indexOfFirst { 
                                it.dayIndex == newSlot.dayIndex && it.slotIndex == newSlot.slotIndex 
                            }
                            
                            if (index != -1) {
                                // Update existing
                                currentList[index] = newSlot
                            } else {
                                // Add new
                                currentList.add(newSlot)
                            }
                            updateCount++
                        }
                        
                        availabilityData = currentList
                        Log.d("ExcelDebug", "UI Updated with $updateCount slots")
                        isLoading = false
                        snackbarHostState.showSnackbar("Updated $updateCount slots from Excel")
                     } else {
                        Log.w("ExcelDebug", "No matching slots found in Excel")
                        isLoading = false
                        snackbarHostState.showSnackbar("No matching slots found in Excel")
                     }
                } catch (e: Exception) {
                    Log.e("ExcelDebug", "Error in launcher coroutine", e)
                    isLoading = false
                    snackbarHostState.showSnackbar("Error parsing Excel: ${e.message}")
                }
            }
        }
    }

    // Update the LaunchedEffect to load the preset
    LaunchedEffect(presetId) {
        isLoading = true
        errorMessage = null

        val db = FirebaseFirestore.getInstance()
        if (presetId == "new") {
            isLoading = false
            errorMessage = "Please select a preset first"
            return@LaunchedEffect
        }

        val presetRef = db.collection(TEACHING_SLOT_PRESETS_COLLECTION).document(presetId)

        try {
            val document = presetRef.get().await()
            if (document.exists()) {
                // Get preset name
                val name = document.getString("presetName") ?: ""

                // Get column names with safe parsing for both String and Map types
                val rawColumns = document.get("columnNames") as? List<*> ?: emptyList<Any>()
                val columnNames = mutableListOf<String>()
                val freeGroupTimes = mutableListOf<String>()

                rawColumns.forEach { item ->
                    when (item) {
                        is String -> {
                            columnNames.add(item)
                            freeGroupTimes.add(item)
                        }
                        is Map<*, *> -> {
                            columnNames.add(item["classTime"] as? String ?: "")
                            val freeTime = item["freeGroupTime"] as? String
                            freeGroupTimes.add(if (!freeTime.isNullOrEmpty()) freeTime else item["classTime"] as? String ?: "")
                        }
                    }
                }

                // Get schedule days and slots
                val scheduleList = mutableListOf<AvailabilityDaySchedule>()
                val scheduleData = document.get("schedule") as? List<Map<String, Any>> ?: emptyList()

                for (dayMap in scheduleData) {
                    val day = dayMap["day"] as? String ?: ""
                    val slots = dayMap["slots"] as? List<Boolean> ?: emptyList()

                    scheduleList.add(AvailabilityDaySchedule(day, slots))
                }

                // Create the preset object
                preset = AvailabilityPreset(
                    id = presetId,
                    presetName = name,
                    columnNames = columnNames,
                    freeGroupTimes = freeGroupTimes,
                    schedule = scheduleList
                )

                // Get availability data from the same document
                val availabilityMap = document.get("availability") as? Map<String, Map<String, String>>
                
                if (availabilityMap != null) {
                    try {
                        Log.d("SetAvailability", "Found availability data in preset")

                        // Get the availability data
                        val updatedSlots = mutableListOf<AvailabilitySlot>()

                        // Process each day in the preset
                        scheduleList.forEachIndexed { dayIndex, daySchedule ->
                            val day = daySchedule.day
                            val dayData = availabilityMap[day]

                            if (dayData != null) {
                                Log.d("SetAvailability", "Found data for day: $day - $dayData")

                                // Process each slot in the day
                                daySchedule.slots.forEachIndexed { slotIndex, active ->
                                    if (active) {
                                        val slotKey = slotIndex.toString()
                                        val slotValue = dayData[slotKey] ?: ""

                                        // Keep the compressed format for display
                                        val formattedValue = slotValue

                                        updatedSlots.add(
                                            AvailabilitySlot(
                                                slotIndex = slotIndex,
                                                dayIndex = dayIndex,
                                                value = formattedValue
                                            )
                                        )

                                        Log.d("SetAvailability", "Loaded slot data: Day=$day, Slot=$slotIndex, Value=$formattedValue")
                                    }
                                }
                            } else {
                                Log.d("SetAvailability", "No data found for day: $day")
                            }
                        }

                        // Update the UI with loaded data
                        if (updatedSlots.isNotEmpty()) {
                            availabilityData = updatedSlots
                            Log.d("SetAvailability", "Updated UI with ${updatedSlots.size} slots")
                        } else {
                            Log.d("SetAvailability", "No slots were loaded - empty data")
                        }
                    } catch (e: Exception) {
                        Log.e("SetAvailability", "Error parsing existing availability data", e)
                        e.printStackTrace()
                    }
                } else {
                    Log.d("SetAvailability", "No existing availability data found in preset")
                }
            } else {
                errorMessage = "Preset not found"
            }
        } catch (e: Exception) {
            errorMessage = "Error loading preset: ${e.message}"
        }

            isLoading = false
    }



    // Function to convert numbers to expanded format for database storage
    fun numbersToExpandedFormat(numbers: List<Int>): String {
        if (numbers.isEmpty()) return ""
        return numbers.sorted().joinToString(",")
    }

    // Function to compress consecutive numbers into ranges for UI display
    // Only compresses ranges of 3 or more consecutive numbers
    fun compressNumberRangesForDisplay(numbers: List<Int>): String {
        if (numbers.isEmpty()) return ""

        val sorted = numbers.sorted()
        val ranges = mutableListOf<String>()
        var start = sorted[0]
        var end = sorted[0]

        for (i in 1 until sorted.size) {
            if (sorted[i] == end + 1) {
                // Consecutive number, extend the range
                end = sorted[i]
            } else {
                // Non-consecutive, finalize current range
                // Only compress if range has 3 or more numbers
                if (end - start >= 2) {
                    ranges.add("$start-$end")
                } else if (start == end) {
                    ranges.add(start.toString())
                } else {
                    // Range of 2 numbers - don't compress
                    ranges.add("$start,$end")
                }
                start = sorted[i]
                end = sorted[i]
            }
        }

        // Add the final range
        // Only compress if range has 3 or more numbers
        if (end - start >= 2) {
            ranges.add("$start-$end")
        } else if (start == end) {
            ranges.add(start.toString())
        } else {
            // Range of 2 numbers - don't compress
            ranges.add("$start,$end")
        }

        return ranges.joinToString(",")
    }

    // Function to expand both compressed ranges and expanded format back to individual numbers
    // Handles both old format (e.g., "1-4") and new format (e.g., "1,2,3,4")
    fun expandNumberRanges(value: String): Set<Int> {
        if (value.isEmpty()) return emptySet()

        val numbers = mutableSetOf<Int>()
        val parts = value.split(",")

        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                // Handle range like "1-5" (for backward compatibility)
                val rangeParts = trimmed.split("-")
                if (rangeParts.size == 2) {
                    val start = rangeParts[0].toIntOrNull()
                    val end = rangeParts[1].toIntOrNull()
                    if (start != null && end != null && start <= end) {
                        for (i in start..end) {
                            numbers.add(i)
                        }
                    }
                }
            } else {
                // Handle single number (works for both old and new format)
                trimmed.toIntOrNull()?.let { numbers.add(it) }
            }
        }

        return numbers
    }

    // Function to load available presets for copying
    fun loadAvailablePresets() {
        isLoadingPresets = true
        coroutineScope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val snapshot = db.collection(TEACHING_SLOT_PRESETS_COLLECTION).get().await()

                val presetsList = mutableListOf<AvailabilityPreset>()

                for (document in snapshot.documents) {
                    try {
                        val id = document.id
                        val name = document.getString("presetName") ?: ""
                        val rawColumns = document.get("columnNames") as? List<*> ?: emptyList<Any>()
                        val columnNames = mutableListOf<String>()
                        val freeGroupTimes = mutableListOf<String>()

                        rawColumns.forEach { item ->
                            when (item) {
                                is String -> {
                                    columnNames.add(item)
                                    freeGroupTimes.add(item)
                                }
                                is Map<*, *> -> {
                                    columnNames.add(item["classTime"] as? String ?: "")
                                    val freeTime = item["freeGroupTime"] as? String
                                    freeGroupTimes.add(if (!freeTime.isNullOrEmpty()) freeTime else item["classTime"] as? String ?: "")
                                }
                            }
                        }

                        // Get schedule days and slots
                        val scheduleList = mutableListOf<AvailabilityDaySchedule>()
                        val scheduleData = document.get("schedule") as? List<Map<String, Any>> ?: emptyList()

                        for (dayMap in scheduleData) {
                            val day = dayMap["day"] as? String ?: ""
                            val slots = dayMap["slots"] as? List<Boolean> ?: emptyList()
                            scheduleList.add(AvailabilityDaySchedule(day, slots))
                        }

                        // Only add presets that are different from current preset
                        if (id != presetId && name.isNotEmpty()) {
                            presetsList.add(
                                AvailabilityPreset(
                                    id = id,
                                    presetName = name,
                                    columnNames = columnNames,
                                    freeGroupTimes = freeGroupTimes,
                                    schedule = scheduleList
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing preset document: ${document.id}", e)
                    }
                }

                availablePresets = presetsList.sortedWith(compareBy { naturalSortKey(it.presetName) })
                isLoadingPresets = false
            } catch (e: Exception) {
                Log.e(TAG, "Error loading available presets", e)
                isLoadingPresets = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Failed to load presets: ${e.message}")
                }
            }
        }
    }

    // Function to copy data from selected preset
    fun copyFromPreset(selectedPreset: AvailabilityPreset) {
        coroutineScope.launch {
            try {
                val db = FirebaseFirestore.getInstance()

                // Load availability data from the selected preset document directly
                // Note: availablePresets list might not have full details, so we fetch the doc or use if available?
                // Actually loadAvailablePresets parses the doc. But check if we need to re-fetch to get 'availability' field 
                // since AvailabilityPreset data class doesn't store the raw availability map.
                
                val presetDoc = db.collection(TEACHING_SLOT_PRESETS_COLLECTION)
                    .document(selectedPreset.id) // Use ID for reliability
                    .get()
                    .await()

                if (presetDoc.exists()) {
                    val sourceAvailabilityMap = presetDoc.get("availability") as? Map<String, Map<String, String>> ?: emptyMap()

                    if (sourceAvailabilityMap.isNotEmpty()) {
                         // Convert the source data to match current preset structure
                        val copiedSlots = mutableListOf<AvailabilitySlot>()

                        preset?.schedule?.forEachIndexed { dayIndex, daySchedule ->
                            val day = daySchedule.day
                            val sourceDayData = sourceAvailabilityMap[day]

                            if (sourceDayData != null) {
                                daySchedule.slots.forEachIndexed { slotIndex, active ->
                                    if (active) {
                                        val slotKey = slotIndex.toString()
                                        val slotValue = sourceDayData[slotKey] ?: ""

                                        if (slotValue.isNotEmpty()) {
                                            copiedSlots.add(
                                                AvailabilitySlot(
                                                    slotIndex = slotIndex,
                                                    dayIndex = dayIndex,
                                                    value = slotValue
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Update the current availability data
                        availabilityData = copiedSlots

                        snackbarHostState.showSnackbar("Copied data from '${selectedPreset.presetName}'")
                    } else {
                        snackbarHostState.showSnackbar("No availability data found in '${selectedPreset.presetName}'")
                    }
                } else {
                     snackbarHostState.showSnackbar("Preset '${selectedPreset.presetName}' not found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error copying preset data", e)
                snackbarHostState.showSnackbar("Failed to copy data: ${e.message}")
            }
        }
    }

    // Function to update the availability value
    fun updateAvailabilityValue(slot: AvailabilitySlot, selectedValues: Set<Int>) {
        val updatedList = availabilityData.toMutableList()
        val slotIndex = updatedList.indexOfFirst {
            it.dayIndex == slot.dayIndex && it.slotIndex == slot.slotIndex
        }

        // Convert selected numbers to expanded format for storage
        val valueStr = if (selectedValues.isEmpty()) "" else
            numbersToExpandedFormat(selectedValues.toList())

        if (slotIndex != -1) {
            // Update existing slot
            updatedList[slotIndex] = slot.copy(value = valueStr)
        } else {
            // Add new slot to the list
            updatedList.add(slot.copy(value = valueStr))
        }

        availabilityData = updatedList
    }

    // Function to save availability data
    fun saveAvailabilityData() {
        Log.d(TAG, "saveAvailabilityData called - Starting save operation")
        isSaving = true
        saveError = null

        try {
            val db = FirebaseFirestore.getInstance()
            Log.d(TAG, "Got Firestore instance")

            // Create a map for availability data
            val availabilityMap = mutableMapOf<String, MutableMap<String, String>>()

            // Process each saved availability slot
            Log.d(TAG, "Processing ${availabilityData.size} availability slots")
            for (slot in availabilityData) {
                if (slot.value.isNotEmpty()) {
                    val dayIndex = slot.dayIndex
                    val slotIndex = slot.slotIndex

                    // Get the day name from the preset
                    val dayName = preset!!.schedule.getOrNull(dayIndex)?.day
                    if (dayName == null) {
                        Log.e(TAG, "Invalid day index: $dayIndex")
                        continue
                    }

                    // Get or create the map for this day
                    val slotMap = availabilityMap.getOrElse(dayName) { mutableMapOf() }

                    // Use the compressed format directly (already without spaces)
                    val storageValue = slot.value

                    // Add this slot's value to the map
                    slotMap[slotIndex.toString()] = storageValue
                    Log.d(TAG, "Adding slot data: Day=$dayName, Slot=$slotIndex, Value=$storageValue")

                    // Update the map for this day
                    availabilityMap[dayName] = slotMap
                }
            }

            if (availabilityMap.isEmpty()) {
                Log.w(TAG, "No availability data to save! availabilityMap is empty.")
                isSaving = false
                    coroutineScope.launch {
                    snackbarHostState.showSnackbar("No data to save - please select some groups")
                }
                return
            }

            // Prepare data to save
            // Perform update on the teaching slot preset document
            db.collection(TEACHING_SLOT_PRESETS_COLLECTION).document(preset!!.id)
                .update("availability", availabilityMap)
                .addOnSuccessListener {
                    isSaving = false
                    Log.d(TAG, "✅ SUCCESS: Availability data updated in preset: ${preset!!.id}")

                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Availability data saved successfully!")
                    }

                    // Navigate back immediately
                    navController.navigateUp()
                }
                .addOnFailureListener { e ->
                    isSaving = false
                    saveError = "Failed to save: ${e.message}"
                    Log.e(TAG, "❌ ERROR: Failed to update availability data", e)
                    e.printStackTrace()

                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Failed to save: ${e.message}")
                    }
                }
        } catch (e: Exception) {
            isSaving = false
            saveError = "Error: ${e.message}"
            Log.e(TAG, "❌ EXCEPTION: Error saving availability data", e)
            e.printStackTrace()

            coroutineScope.launch {
                snackbarHostState.showSnackbar("Error: ${e.message}")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                // Removed padding here to allow custom padding for header and content
        ) {
            // Top bar with back button and title - matching ManageVolunteersScreen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp) // TTW UI plan TopAppBar padding
                    .padding(start = 4.dp, end = 20.dp), // Less padding at start for Back button
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                IconButton(
                    onClick = { navController.navigateUp() },
                    modifier = Modifier.size(48.dp) // TTW UI plan: 48dp touch target
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp) // TTW UI plan: 24dp icon size
                    )
                }

                // Title with proper typography
                Text(
                    "Set Availability",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )

                // Copy Preset button in header - circular yellow button (TTW UI plan pattern)
                if (preset != null && !isLoading) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp, end = 4.dp)
                            .size(40.dp) // Circular button size
                            .clip(CircleShape)
                            .background(YellowAccent)
                            .clickable {
                                loadAvailablePresets()
                                showCopyDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Preset",
                            tint = Color.Black, // Black icon on yellow background
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Show loading indicator when loading preset data
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = YellowAccent,
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )
                }
            } else if (errorMessage != null) {
                // Error state
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DarkSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage ?: "Unknown error",
                            color = ErrorRed,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        StandardButton(
                            onClick = { navController.navigateUp() }
                        ) {
                            Text(
                                "Go Back",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else if (preset != null) {
                // Show preset schedule for setting availability
                Column(
                    modifier = Modifier
                        .fillMaxWidth() // Changed from fillMaxSize to allow content-based height
                        .padding(horizontal = 20.dp) // Restore standard padding for content
                        .verticalScroll(rememberScrollState())
                ) {
                        // Preset name display
                        Text(
                            text = preset!!.presetName,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        )

                        // Schedule grid
                        if (preset!!.schedule.isNotEmpty() && preset!!.columnNames.isNotEmpty()) {

                                // Shared scroll state for synchronized scrolling
                                val tableScrollState = rememberScrollState()

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth() // Always fill width for horizontal centering
                                        .shadow(
                                            elevation = 4.dp,
                                            shape = RoundedCornerShape(16.dp)
                                        ),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = NeutralCardSurface
                                    )
                                ) {
                                    // Table content with consistent background and horizontal centering
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth() // Always fill width for horizontal centering
                                            .background(
                                                YellowAccent.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                            ),
                                        horizontalAlignment = Alignment.CenterHorizontally // Always center horizontally
                                    ) {
                                    // Schedule table header with synchronized horizontal scrolling
                                    Row(
                                        modifier = Modifier
                                            .wrapContentWidth() // Only take up space needed for content
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Fixed day column header (doesn't scroll)
                                        Text(
                                            text = "Day",
                                            modifier = Modifier
                                                .width(50.dp)
                                                .padding(horizontal = 4.dp),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = YellowAccent
                                        )

                                        // Horizontally scrollable slot headers using shared scroll state
                                        Row(
                                            modifier = Modifier
                                                .wrapContentWidth() // Only take up space needed for content
                                                .horizontalScroll(tableScrollState),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Slot column headers with fixed dimensions
                                            preset!!.columnNames.forEachIndexed { index, classTime ->
                                                val freeTime = preset!!.freeGroupTimes.getOrElse(index) { "" }

                                                Column(
                                                    modifier = Modifier
                                                        .width(100.dp) // Increased width for richer header
                                                        .padding(horizontal = 2.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Card(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = YellowAccent.copy(alpha = 0.1f)
                                                        )
                                                    ) {
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 6.dp, horizontal = 4.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                                        ) {
                                                            // Class Time
                                                            Text(
                                                                text = "Class:",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Medium,
                                                                color = YellowAccent,
                                                                fontSize = 10.sp
                                                            )
                                                            Text(
                                                                text = classTime,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White,
                                                                textAlign = TextAlign.Center,
                                                                fontSize = 11.sp,
                                                                maxLines = 1
                                                            )

                                                            Spacer(modifier = Modifier.height(4.dp))

                                                            // Free Group Time
                                                            Text(
                                                                text = "Free:",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Medium,
                                                                color = YellowAccent,
                                                                fontSize = 10.sp
                                                            )
                                                            Text(
                                                                text = freeTime.ifEmpty { "--:--" },
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White,
                                                                textAlign = TextAlign.Center,
                                                                fontSize = 11.sp,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                        // Schedule table rows
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth() // Always fill width for horizontal centering
                                                .background(
                                                    NeutralCardSurface,
                                                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                                                )
                                                .padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally // Always center horizontally
                                        ) {
                            preset!!.schedule.forEachIndexed { dayIndex, daySchedule ->
                                    Row(
                                        modifier = Modifier
                                            .wrapContentWidth() // Only take up space needed for content
                                            .padding(vertical = 2.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Fixed day name column (doesn't scroll)
                                        Text(
                                            text = daySchedule.day,
                                            modifier = Modifier
                                                .width(50.dp)
                                                .padding(horizontal = 4.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )

                                        // Horizontally scrollable slot cells using shared scroll state
                                        Row(
                                            modifier = Modifier
                                                .wrapContentWidth() // Only take up space needed for content
                                                .horizontalScroll(tableScrollState),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Slots - toggleable cells with fixed dimensions
                                            daySchedule.slots.forEachIndexed { slotIndex, active ->
                                                Box(
                                                    modifier = Modifier
                                                    .width(100.dp) // Match header width
                                                        .height(60.dp) // Fixed height for consistent sizing
                                                    .background(
                                                        color = if (active) {
                                                            // Use different color if has value
                                                            val hasValue = availabilityData.any {
                                                                it.dayIndex == dayIndex &&
                                                                it.slotIndex == slotIndex &&
                                                                it.value.isNotEmpty()
                                                            }
                                                            if (hasValue) {
                                                                YellowAccent
                                                            } else {
                                                                // Much lighter shade for editable cells - more pronounced distinction
                                                                Color(0xFF4A4A4A) // Lighter gray for better visibility
                                                            }
                                                        } else {
                                                            // Darker shade for non-editable cells
                                                            DarkSurface.copy(alpha = 0.3f)
                                                        },
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = NeutralGray.copy(alpha = 0.5f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                    .clickable(enabled = active) {
                                                        if (active) {
                                                            // Create a new slot if one doesn't exist
                                                            val slot = availabilityData.find {
                                                                it.dayIndex == dayIndex && it.slotIndex == slotIndex
                                                            } ?: AvailabilitySlot(
                                                                slotIndex = slotIndex,
                                                                dayIndex = dayIndex,
                                                                value = ""
                                                            )

                                                            // Now we always set selectedSlot and show dialog
                                                            selectedSlot = slot

                                                            // Parse existing compressed values to initialize dialog
                                                            selectedNumbers = expandNumberRanges(slot.value)
                                                            showInputDialog = true
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (active) {
                                                    // Show value if it exists
                                                    val value = availabilityData.find {
                                                        it.dayIndex == dayIndex && it.slotIndex == slotIndex
                                                    }?.value

                                                    if (!value.isNullOrEmpty()) {
                                                        // Convert stored value to display format with new compression rules
                                                        val numbers = expandNumberRanges(value)
                                                        val displayValue = compressNumberRangesForDisplay(numbers.toList())
                                        Text(
                                                            text = displayValue,
                                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                                            textAlign = TextAlign.Center,
                                                            fontSize = 11.sp,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                        }
                                    }
                                }
                            }
                        }
                        }





                        // Upload Excel button - at bottom of grid
                        Spacer(modifier = Modifier.height(16.dp))
                        // Upload Excel Button - centered and compact
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            StandardButton(
                                onClick = {
                                    try {
                                        excelLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                    } catch (e: Exception) {
                                        excelLauncher.launch("*/*")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth(0.7f) // 70% width for more compact look
                                    .padding(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.UploadFile,
                                    contentDescription = "Upload Excel",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Upload Excel",
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }


                        // Bottom spacing for better layout and FAB clearance
                        Spacer(modifier = Modifier.height(80.dp))
                }
        }

        // Dialog for selecting numbers with UI.md compliance
    if (showInputDialog && selectedSlot != null) {
        AlertDialog(
            onDismissRequest = {
                showInputDialog = false
                selectedSlot = null
            },
            title = {
                Text(
                    "Select Academic Groups",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            containerColor = NeutralCardSurface,
            titleContentColor = Color.White,
            textContentColor = Color.White,
            text = {
                Column {
                    Text(
                        "Tap to select groups available for this time slot:",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Display currently selected numbers
                    if (selectedNumbers.isNotEmpty()) {
                        Text(
                            "Available Groups: ${selectedNumbers.sorted().joinToString(", ")}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = YellowAccent,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    // Grid of numbers for selection with proper touch targets
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                    ) {
                        items((1..30).toList()) { number ->
                            val isSelected = selectedNumbers.contains(number)
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(48.dp) // Minimum 48dp touch target
                                    .background(
                                        color = if (isSelected)
                                            YellowAccent
                                        else
                                            DarkSurface,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected)
                                            YellowAccent
                                        else
                                            NeutralGray,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedNumbers = if (isSelected) {
                                            selectedNumbers - number
                                        } else {
                                            selectedNumbers + number
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = number.toString(),
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                StandardButton(
                    onClick = {
                        selectedSlot?.let { slot ->
                            updateAvailabilityValue(slot, selectedNumbers)
                        }
                        showInputDialog = false
                        selectedSlot = null
                    }
                ) {
                    Text(
                        "Save",
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showInputDialog = false
                        selectedSlot = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = YellowAccent
                    )
                ) {
                    Text(
                        "Cancel",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }

    // Saving indicator with UI.md compliance
    if (isSaving) {
        Dialog(onDismissRequest = {}) {
            Card(
                modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = NeutralCardSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .width(200.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = YellowAccent,
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Saving availability...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    // Floating Action Button for Save - TTW UI plan specifications
    if (preset != null && !isLoading) {
         FloatingActionButton(
            onClick = { saveAvailabilityData() },
            containerColor = Color(0xFF4CAF50), // TTW UI plan: Green
            contentColor = Color.White, // TTW UI plan: White icon
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 48.dp) // TTW UI plan: end=24dp, bottom=48dp
        ) {
            Icon(
                Icons.Default.Save,
                contentDescription = "Save",
                modifier = Modifier.size(24.dp)
            )
        }
    }

    // Copy preset selection dialog
    if (showCopyDialog) {
        AlertDialog(
            onDismissRequest = {
                showCopyDialog = false
                availablePresets = emptyList()
            },
            title = {
                Text(
                    "Copy Availability Data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            containerColor = NeutralCardSurface,
            titleContentColor = Color.White,
            textContentColor = Color.White,
            text = {
                Column {
                    if (isLoadingPresets) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = YellowAccent,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                        }
                    } else if (availablePresets.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No other presets available to copy from",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFB0B0B0),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availablePresets) { preset ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            copyFromPreset(preset)
                                            showCopyDialog = false
                                            availablePresets = emptyList()
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = DarkSurface
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = preset.presetName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )

                                        Text(
                                            text = "${preset.schedule.size} days, ${preset.columnNames.size} slots",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFB0B0B0),
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showCopyDialog = false
                        availablePresets = emptyList()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = YellowAccent
                    )
                ) {
                    Text(
                        "Cancel",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun SetAvailabilityScreenPreview() {
    SchedulingTheme {
        val mockPresetId = "previewPreset"
        SetAvailabilityScreen(rememberNavController(), mockPresetId)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun AvailabilitySlotPreview() {
    SchedulingTheme {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(DarkBackground)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        color = YellowAccent,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = NeutralGray.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "1, 2, 3",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp
                )
            }
        }
    }
}