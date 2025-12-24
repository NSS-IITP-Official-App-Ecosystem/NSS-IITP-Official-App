package com.phad.chatapp.features.scheduling.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.phad.chatapp.features.scheduling.firebase.FirebaseManager
import com.phad.chatapp.features.scheduling.firebase.FirebaseManager.FirestoreCollection
import com.phad.chatapp.features.scheduling.models.TeachingSlot
import com.phad.chatapp.features.scheduling.ui.theme.SchedulingTheme
import com.phad.chatapp.features.scheduling.ui.theme.DarkBackground
import com.phad.chatapp.features.scheduling.ui.theme.DarkSurface
import com.phad.chatapp.features.scheduling.ui.theme.YellowAccent
import com.phad.chatapp.features.scheduling.ui.theme.BlueAccent
import com.phad.chatapp.features.scheduling.ui.theme.TealAccent
import com.phad.chatapp.features.scheduling.ui.theme.SuccessGreen
import com.phad.chatapp.features.scheduling.ui.theme.ErrorRed
import com.phad.chatapp.features.scheduling.ui.theme.NeutralGray
import com.phad.chatapp.features.scheduling.ui.theme.DarkGray
import com.phad.chatapp.features.scheduling.ui.theme.SurfaceElevated
import com.phad.chatapp.features.scheduling.ui.theme.NeutralCardSurface
import com.phad.chatapp.features.scheduling.ui.theme.SlotActiveColor
import com.phad.chatapp.features.scheduling.ui.theme.SlotInactiveColor
import kotlinx.coroutines.launch
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.filled.ArrowBack

// Standardized Button Design System
object StandardButtonDefaults {
    val Height = 44.dp
    val CornerRadius = 10.dp
    val Elevation = 3.dp
    val HorizontalPadding = 20.dp
    val VerticalPadding = 10.dp
    val MinimumWidth = 80.dp

    @Composable
    fun buttonColors() = ButtonDefaults.buttonColors(
        containerColor = YellowAccent,
        contentColor = Color.Black,
        disabledContainerColor = NeutralGray.copy(alpha = 0.5f),
        disabledContentColor = Color.Black.copy(alpha = 0.5f)
    )

    @Composable
    fun buttonElevation() = ButtonDefaults.buttonElevation(
        defaultElevation = Elevation,
        pressedElevation = (Elevation.value - 1).dp,
        disabledElevation = 0.dp
    )

    val buttonShape = RoundedCornerShape(CornerRadius)

    val contentPadding = PaddingValues(
        horizontal = HorizontalPadding,
        vertical = VerticalPadding
    )
}

@Composable
fun StandardButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = StandardButtonDefaults.Height)
            .widthIn(min = StandardButtonDefaults.MinimumWidth),
        enabled = enabled,
        colors = StandardButtonDefaults.buttonColors(),
        elevation = StandardButtonDefaults.buttonElevation(),
        shape = StandardButtonDefaults.buttonShape,
        contentPadding = StandardButtonDefaults.contentPadding,
        content = content
    )
}

// Data structure for a slot - Simplified, only needs existence status maybe?
// Keep ID for potential future use, but name is now per-column.
data class ScreenTeachingSlot(val id: String)

// Data structure for a day's row - Contains a list of nullable slots
data class DaySlots(val day: String, var slots: List<ScreenTeachingSlot?>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTeachingSlotsScreen(
    navController: NavController,
    // Extract ID parameter from navigation for edit functionality
    presetId: String? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var selectedDays by remember { mutableStateOf(setOf<String>()) }
    var slotCountInput by remember { mutableStateOf("4") }
    var teachingSchedule by remember { mutableStateOf<List<DaySlots>>(emptyList()) }
    // State for column names
    var columnNames by remember { mutableStateOf<List<String>>(emptyList()) }

    var showRenameColumnDialog by remember { mutableStateOf(false) }
    var columnToRenameIndex by remember { mutableStateOf<Int?>(null) }
    var currentColumnName by remember { mutableStateOf("") }

    // Time picker states using Material3 TimePicker
    // Mutable state for the hour and minute values
    var startTimeHour by remember { mutableStateOf(9) }
    var startTimeMinute by remember { mutableStateOf(0) }
    var endTimeHour by remember { mutableStateOf(10) }
    var endTimeMinute by remember { mutableStateOf(0) }
    
    var timePickerError by remember { mutableStateOf<String?>(null) }

    // Time picker UI states
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    // Dropdown states for structured naming
    var selectedSchoolCode by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf("") }
    var selectedSection by remember { mutableStateOf("") }
    var schoolCodeExpanded by remember { mutableStateOf(false) }
    var classExpanded by remember { mutableStateOf(false) }
    var sectionExpanded by remember { mutableStateOf(false) }

    // Dropdown options
    val schoolCodes = listOf("RP", "AM", "UB", "FA", "KV", "TPS")
    val classes = listOf("6", "7", "8", "9", "10", "11", "12")
    val sections = listOf("B", "G", "N")

    // Generate preset name from selections
    val generatedPresetName = if (selectedSchoolCode.isNotEmpty() && selectedClass.isNotEmpty() && selectedSection.isNotEmpty()) {
        "$selectedSchoolCode $selectedClass$selectedSection"
    } else {
        ""
    }

    // Add state for save operation feedback
    var showSavingIndicator by remember { mutableStateOf(false) }
    var showSaveSuccessMessage by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }

    // State to track if screen is in edit mode
    var isEditMode by remember { mutableStateOf(presetId != null) }
    var isLoading by remember { mutableStateOf(isEditMode) }

    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri")

    // Function to parse existing preset name and set dropdown values
    fun parsePresetName(name: String) {
        val regex = Regex("^([A-Z]{2,3})\\s+(\\d{1,2})([A-Z])$")
        val matchResult = regex.find(name)
        if (matchResult != null) {
            val (schoolCode, classNum, section) = matchResult.destructured
            if (schoolCode in schoolCodes && classNum in classes && section in sections) {
                selectedSchoolCode = schoolCode
                selectedClass = classNum
                selectedSection = section
                return
            }
        }
        // If parsing fails, reset to empty values
        selectedSchoolCode = ""
        selectedClass = ""
        selectedSection = ""
    }

    // Function to reset dropdown selections
    fun resetDropdownSelections() {
        selectedSchoolCode = ""
        selectedClass = ""
        selectedSection = ""
        schoolCodeExpanded = false
        classExpanded = false
        sectionExpanded = false
    }



    // Function to load existing preset data
    fun loadPresetData(id: String) {
        Log.d("CreateTeachingSlotsScreen", "Loading preset data for ID: $id")
        isLoading = true

        val db = FirebaseFirestore.getInstance()
        val presetRef = db.collection("teachingSlotPresets").document(id)

        presetRef.get()
            .addOnSuccessListener { document ->
                try {
                    if (document.exists()) {
                    // Get preset name
                        val name = document.getString("presetName") ?: ""
                        presetNameInput = name
                        parsePresetName(name)

                        // Get column names
                        val columns = document.get("columnNames") as? List<String> ?: emptyList()
                        columnNames = columns.toMutableList()

                        // Update slot count input based on columns
                        slotCountInput = columns.size.toString()

                        // Get schedule days and slots
                        val days = mutableSetOf<String>()
                        val scheduleData = document.get("schedule") as? List<Map<String, Any>> ?: emptyList()
                        val loadedSchedule = mutableListOf<DaySlots>()

                        for (dayMap in scheduleData) {
                            val day = dayMap["day"] as? String ?: ""
                            val slots = dayMap["slots"] as? List<Boolean> ?: emptyList()

                            days.add(day)
                            loadedSchedule.add(DaySlots(day, slots.map { if (it) ScreenTeachingSlot(id = "${day}_$it") else null }.toMutableList()))
                        }

                        selectedDays = days
                        teachingSchedule = loadedSchedule
                    } else {
                        Log.e("CreateTeachingSlotsScreen", "Document does not exist for ID: $id")
                        saveErrorMessage = "Preset not found"
                    }
                } catch (e: Exception) {
                    Log.e("CreateTeachingSlotsScreen", "Error parsing data: ${e.message}")
                    saveErrorMessage = "Error loading preset: ${e.message}"
                }

                isLoading = false
            }
            .addOnFailureListener { e ->
                Log.e("CreateTeachingSlotsScreen", "Error loading data: ${e.message}")
                saveErrorMessage = "Error loading preset: ${e.message}"
                isLoading = false
            }
    }

    // Function to initialize or update the entire grid structure and column names
    fun updateGridStructure() {
        // Skip if loading data
        if (isLoading) return

        val numSlots = slotCountInput.toIntOrNull() ?: 0
        if (numSlots > 0) {
            // Update Column Names
            val newColumnNames = List(numSlots) { index ->
                columnNames.getOrNull(index) ?: "Slot ${index + 1}" // Preserve existing names
            }
            columnNames = newColumnNames

            // Update Schedule Grid
            if (selectedDays.isNotEmpty()) {
                val currentPresetMap = teachingSchedule.associate { it.day to it.slots }
                teachingSchedule = selectedDays.toList().sortedBy { daysOfWeek.indexOf(it) }.map { day ->
                    val existingSlots = currentPresetMap[day]

                    val newSlots = List<ScreenTeachingSlot?>(numSlots) { index ->
                        existingSlots?.getOrNull(index) // This preserves null values
                            ?: if (existingSlots == null) {
                                // New day with default slots
                                ScreenTeachingSlot(id = "${day}_$index")
                            } else {
                                // Out of bounds but day exists - don't create by default
                                null
                            }
                    }

                    DaySlots(day = day, slots = newSlots)
                }
            } else {
                teachingSchedule = emptyList()
            }
        } else {
            // Clear everything if slot count is 0
            columnNames = emptyList()
            teachingSchedule = emptyList()
        }
    }

    // Remove a slot (make it null) and ensure recomposition
    fun removeSlot(dayIndex: Int, slotIndex: Int) {
        if (dayIndex < 0 || dayIndex >= teachingSchedule.size) return
        val dayToUpdate = teachingSchedule[dayIndex]
        if (slotIndex < 0 || slotIndex >= dayToUpdate.slots.size) return

        // Create a new list of slots with the specific one nulled out
        val updatedSlots = dayToUpdate.slots.toMutableList().apply {
            this[slotIndex] = null
        }.toList() // Make immutable again

        // Create a new DaySlots object with the updated slots list
        val updatedDaySlots = dayToUpdate.copy(slots = updatedSlots)

        // Create a new overall schedule list replacing the updated DaySlots object
        teachingSchedule = teachingSchedule.toMutableList().apply {
            this[dayIndex] = updatedDaySlots
        }.toList() // Make immutable again
    }

    // --- Column Renaming Logic ---
    fun openRenameColumnDialog(index: Int) {
        if (index >= 0 && index < columnNames.size) {
            columnToRenameIndex = index
            currentColumnName = columnNames[index]

            // Parse existing time range if it exists
            val timePattern = Regex("""(\d{1,2}):(\d{2})-(\d{1,2}):(\d{2})""")
            val match = timePattern.find(currentColumnName)
            if (match != null) {
                val parsedStartHour = match.groupValues[1].toInt()
                val parsedStartMinute = match.groupValues[2].toInt()
                val parsedEndHour = match.groupValues[3].toInt()
                val parsedEndMinute = match.groupValues[4].toInt()
                // Update the time state variables with parsed values
                startTimeHour = parsedStartHour
                startTimeMinute = parsedStartMinute
                endTimeHour = parsedEndHour
                endTimeMinute = parsedEndMinute
            } else {
                // Default times
                startTimeHour = 9
                startTimeMinute = 0
                endTimeHour = 10
                endTimeMinute = 0
            }

            timePickerError = null
            showRenameColumnDialog = true
        }
    }

    fun validateAndSaveTimeRange() {
        val startTimeMinutes = startTimeHour * 60 + startTimeMinute
        val endTimeMinutes = endTimeHour * 60 + endTimeMinute

        if (endTimeMinutes <= startTimeMinutes) {
            timePickerError = "End time must be after start time"
            return
        }

        val formattedTimeRange = String.format("%02d:%02d-%02d:%02d", startTimeHour, startTimeMinute, endTimeHour, endTimeMinute)

        columnToRenameIndex?.let { index ->
            if (index >= 0 && index < columnNames.size) {
                val updatedColumnNames = columnNames.toMutableList().apply {
                    this[index] = formattedTimeRange
                }
                columnNames = updatedColumnNames.toList()
            }
        }
        showRenameColumnDialog = false
        columnToRenameIndex = null
        timePickerError = null
    }
    // --- End Column Renaming ---

    // --- Save Preset Logic ---
    fun savePreset() {
        val nameToUse = if (generatedPresetName.isNotEmpty()) generatedPresetName else presetNameInput
        if (nameToUse.isBlank()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Please select all dropdown options or enter a preset name")
            }
            return
        }

        if (selectedDays.isEmpty()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Please select at least one day")
            }
            return
        }

        showSavingIndicator = true
        saveErrorMessage = null

        // Create schedule data
        val scheduleData = teachingSchedule.map { daySlot ->
            mapOf(
                "day" to daySlot.day,
                "slots" to daySlot.slots.map { it != null }
            )
        }

        // Create the preset data
        val presetData = hashMapOf(
            "presetName" to nameToUse,
            "columnNames" to columnNames,
            "schedule" to scheduleData
        )

        try {
            // Always use preset name as document ID for both create and update operations
            Log.d("CreateTeachingSlotsScreen", "Saving preset with name as document ID: $nameToUse")
            val db = FirebaseFirestore.getInstance()
            val presetRef = db.collection("teachingSlotPresets").document(nameToUse)

            // Use set() to create new or replace existing preset with same name
            presetRef.set(presetData)
                .addOnSuccessListener {
                    showSavingIndicator = false
                    showSaveSuccessMessage = true
                    val actionText = if (isEditMode) "updated" else "created"
                    Log.d("CreateTeachingSlotsScreen", "Preset $actionText successfully with name: $nameToUse")
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Preset $actionText successfully!")
                    }
                    navController.navigateUp()
                }
                .addOnFailureListener { e ->
                    showSavingIndicator = false
                    saveErrorMessage = "Failed to save preset: ${e.message}"
                    Log.e("CreateTeachingSlotsScreen", "Save failed: ${e.message}", e)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Failed to save preset: ${e.message}")
                    }
                }
        } catch (e: Exception) {
            showSavingIndicator = false
            saveErrorMessage = "Error: ${e.message}"
            Log.e("CreateTeachingSlotsScreen", "General error: ${e.message}", e)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Error: ${e.message}")
            }
        }
    }
    // --- End Save Preset ---

    // Load preset data if in edit mode
    LaunchedEffect(presetId) {
        if (presetId != null) {
            loadPresetData(presetId)
        }
    }

    // Initialize grid structure after loading preset or when inputs change
    LaunchedEffect(selectedDays, slotCountInput, isLoading) {
        if (!isLoading) {
            updateGridStructure()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp)
            ) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(
                        onClick = { navController.navigateUp() },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    // Title
                    Text(
                        text = if (isEditMode) "Edit Slots Preset" else "Create Slots Preset",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    // Save button - only show when grid exists
                    if (teachingSchedule.isNotEmpty()) {
                        StandardButton(
                            onClick = {
                                if (isEditMode && presetNameInput.isNotEmpty()) {
                                    savePreset()
                                } else {
                                    showSaveDialog = true
                                }
                            }
                        ) {
                            Text(
                                text = if (isEditMode) "Save" else "Create",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Show loading indicator when loading preset data
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = YellowAccent)
                    }
                } else {
                    // Main content
                    // Always use vertical scrolling, no vertical centering
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 120.dp), // Extra padding to clear bottom navigation bar
                        verticalArrangement = Arrangement.Top
                    ) {
                        // Main content area with rounded card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = NeutralCardSurface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                // Step 1: Select Days
                                Column {
                                    Text(
                                        text = "Select Days:",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    // Single row of day checkboxes
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        daysOfWeek.forEach { day ->
                                            DayCheckbox(
                                                day = day,
                                                isSelected = selectedDays.contains(day),
                                                onSelectionChanged = { isSelected: Boolean ->
                                                    selectedDays = if (isSelected) {
                                                        selectedDays + day
                                                    } else {
                                                        selectedDays - day
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(28.dp))
                                }

                                // Step 2: Number of slots per day
                                Column {
                                    Text(
                                        text = "Number of Slots per Day:",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    // Slot count input with increment/decrement buttons
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Decrement button with consistent styling
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .shadow(
                                                    elevation = 2.dp,
                                                    shape = CircleShape,
                                                    spotColor = YellowAccent.copy(alpha = 0.5f)
                                                )
                                                .background(
                                                    color = YellowAccent,
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    val currentValue = slotCountInput.toIntOrNull() ?: 0
                                                    if (currentValue > 1) {
                                                        slotCountInput = (currentValue - 1).toString()
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "−", // Unicode minus sign
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp,
                                                color = Color.Black
                                            )
                                        }

                                        // Number input field
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(56.dp)
                                                .padding(horizontal = 16.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = SurfaceElevated
                                            ),
                                            elevation = CardDefaults.cardElevation(
                                                defaultElevation = 2.dp
                                            )
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = slotCountInput,
                                                    color = Color.White,
                                                    fontSize = 28.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }

                                        // Increment button with consistent styling
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .shadow(
                                                    elevation = 2.dp,
                                                    shape = CircleShape,
                                                    spotColor = YellowAccent.copy(alpha = 0.5f)
                                                )
                                                .background(
                                                    color = YellowAccent,
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    val currentValue = slotCountInput.toIntOrNull() ?: 0
                                                    if (currentValue < 8) {
                                                        slotCountInput = (currentValue + 1).toString()
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Increase",
                                                tint = Color.Black,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                            // Footer instructions
                            if (teachingSchedule.isEmpty() && selectedDays.isNotEmpty() && slotCountInput.toIntOrNull() ?: 0 > 0) {
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "Select days and enter slot count to generate the grid.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                        // Generated Grid
                        if (teachingSchedule.isNotEmpty()) {
                            Column {
                                Spacer(modifier = Modifier.height(28.dp))

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
                                    // Shared scroll state for synchronized scrolling
                                    val tableScrollState = rememberScrollState()

                                    // Table content with consistent background
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
                                                .padding(vertical = 12.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Fixed day column header (doesn't scroll)
                                            Text(
                                                text = "Day",
                                                modifier = Modifier
                                                    .width(48.dp) // Increased for better alignment
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
                                                columnNames.forEachIndexed { index, columnName ->
                                                    Column(
                                                        modifier = Modifier
                                                            .width(80.dp) // Fixed width for consistent sizing
                                                            .padding(horizontal = 2.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Card(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable { openRenameColumnDialog(index) },
                                                            shape = RoundedCornerShape(8.dp),
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = Color.Transparent
                                                            )
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(vertical = 4.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = columnName,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Clip,
                                                                    color = YellowAccent,
                                                                    textAlign = TextAlign.Center
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Schedule table rows with synchronized horizontal scrolling
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
                                        teachingSchedule.forEachIndexed { dayIndex, daySlots ->
                                            Row(
                                                modifier = Modifier
                                                    .wrapContentWidth() // Only take up space needed for content
                                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Fixed day name column (doesn't scroll)
                                                Text(
                                                    text = daySlots.day,
                                                    modifier = Modifier
                                                        .width(48.dp) // Match header width
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
                                                    daySlots.slots.forEachIndexed { slotIndex, slot ->
                                                        val isActive = slot != null

                                                        Card(
                                                            modifier = Modifier
                                                                .width(80.dp) // Fixed width for consistent sizing
                                                                .height(60.dp) // Fixed height for consistent sizing
                                                                .clickable {
                                                                    if (slot != null) {
                                                                        removeSlot(dayIndex, slotIndex)
                                                                    } else {
                                                                        // Re-add a slot (make it non-null)
                                                                        val updatedDaySlots = teachingSchedule[dayIndex]
                                                                        val updatedSlots = updatedDaySlots.slots.toMutableList().apply {
                                                                            this[slotIndex] = ScreenTeachingSlot(id = "${daySlots.day}_$slotIndex")
                                                                        }
                                                                        teachingSchedule = teachingSchedule.toMutableList().apply {
                                                                            this[dayIndex] = updatedDaySlots.copy(slots = updatedSlots)
                                                                        }
                                                                    }
                                                                },
                                                            shape = RoundedCornerShape(8.dp),
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = if (isActive) YellowAccent else DarkGray
                                                            ),
                                                            elevation = CardDefaults.cardElevation(
                                                                defaultElevation = if (isActive) 2.dp else 0.dp
                                                            )
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                if (isActive) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Close,
                                                                        contentDescription = "Remove slot",
                                                                        tint = Color.Black,
                                                                        modifier = Modifier.size(18.dp)
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
                            }
                        }
                    }
                }
            }

        // Dialog for setting time range
        if (showRenameColumnDialog) {
            AlertDialog(
                onDismissRequest = {
                    showRenameColumnDialog = false
                    timePickerError = null
                },
                title = {
                    Text(
                        "Set Time Range",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Time Range Section - Horizontal Layout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Start Time Column
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "Start Time",
                                    color = YellowAccent,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Start Time Picker Card
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showStartTimePicker = true },
                                    colors = CardDefaults.cardColors(
                                        containerColor = YellowAccent.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, YellowAccent.copy(alpha = 0.3f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            String.format("%02d:%02d", startTimeHour, startTimeMinute),
                                            color = YellowAccent,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // End Time Column
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "End Time",
                                    color = YellowAccent,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // End Time Picker Card
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showEndTimePicker = true },
                                    colors = CardDefaults.cardColors(
                                        containerColor = YellowAccent.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, YellowAccent.copy(alpha = 0.3f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            String.format("%02d:%02d", endTimeHour, endTimeMinute),
                                            color = YellowAccent,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Preview
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = YellowAccent.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    "Preview:",
                                    color = YellowAccent,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    String.format("%02d:%02d-%02d:%02d", startTimeHour, startTimeMinute, endTimeHour, endTimeMinute),
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Error message
                        timePickerError?.let { error ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = ErrorRed.copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    error,
                                    color = ErrorRed,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    StandardButton(
                        onClick = { validateAndSaveTimeRange() }
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
                            showRenameColumnDialog = false
                            timePickerError = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            "Cancel",
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                containerColor = SurfaceElevated,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Start Time Picker Dialog
        if (showStartTimePicker) {
            // Create a new TimePickerState with current values when dialog opens
            val dialogTimePickerState = rememberTimePickerState(
                initialHour = startTimeHour,
                initialMinute = startTimeMinute,
                is24Hour = true
            )
            
            AlertDialog(
                onDismissRequest = { showStartTimePicker = false },
                title = {
                    Text(
                        "Select Start Time",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Material3 TimePicker with yellow accent colors
                        TimePicker(
                            state = dialogTimePickerState,
                            colors = TimePickerDefaults.colors(
                                clockDialColor = SurfaceElevated,
                                clockDialSelectedContentColor = Color.Black,
                                clockDialUnselectedContentColor = Color.White,
                                selectorColor = YellowAccent,
                                containerColor = SurfaceElevated,
                                periodSelectorBorderColor = YellowAccent,
                                periodSelectorSelectedContainerColor = YellowAccent,
                                periodSelectorUnselectedContainerColor = Color.Transparent,
                                periodSelectorSelectedContentColor = Color.Black,
                                periodSelectorUnselectedContentColor = Color.White,
                                timeSelectorSelectedContainerColor = YellowAccent,
                                timeSelectorUnselectedContainerColor = Color.Transparent,
                                timeSelectorSelectedContentColor = Color.Black,
                                timeSelectorUnselectedContentColor = Color.White
                            )
                        )
                    }
                },
                confirmButton = {
                    StandardButton(
                        onClick = { 
                            startTimeHour = dialogTimePickerState.hour
                            startTimeMinute = dialogTimePickerState.minute
                            timePickerError = null
                            showStartTimePicker = false
                        }
                    ) {
                        Text(
                            "Set",
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showStartTimePicker = false },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            "Cancel",
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                containerColor = SurfaceElevated,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // End Time Picker Dialog
        if (showEndTimePicker) {
            // Create a new TimePickerState with current values when dialog opens
            val dialogTimePickerState = rememberTimePickerState(
                initialHour = endTimeHour,
                initialMinute = endTimeMinute,
                is24Hour = true
            )
            
            AlertDialog(
                onDismissRequest = { showEndTimePicker = false },
                title = {
                    Text(
                        "Select End Time",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Material3 TimePicker with yellow accent colors
                        TimePicker(
                            state = dialogTimePickerState,
                            colors = TimePickerDefaults.colors(
                                clockDialColor = SurfaceElevated,
                                clockDialSelectedContentColor = Color.Black,
                                clockDialUnselectedContentColor = Color.White,
                                selectorColor = YellowAccent,
                                containerColor = SurfaceElevated,
                                periodSelectorBorderColor = YellowAccent,
                                periodSelectorSelectedContainerColor = YellowAccent,
                                periodSelectorUnselectedContainerColor = Color.Transparent,
                                periodSelectorSelectedContentColor = Color.Black,
                                periodSelectorUnselectedContentColor = Color.White,
                                timeSelectorSelectedContainerColor = YellowAccent,
                                timeSelectorUnselectedContainerColor = Color.Transparent,
                                timeSelectorSelectedContentColor = Color.Black,
                                timeSelectorUnselectedContentColor = Color.White
                            )
                        )
                    }
                },
                confirmButton = {
                    StandardButton(
                        onClick = { 
                            endTimeHour = dialogTimePickerState.hour
                            endTimeMinute = dialogTimePickerState.minute
                            timePickerError = null
                            showEndTimePicker = false
                        }
                    ) {
                        Text(
                            "Set",
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showEndTimePicker = false },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            "Cancel",
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                containerColor = SurfaceElevated,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Dialog for saving the preset
        if (showSaveDialog) {
            // Reset dropdown selections when dialog opens (for new presets)
            LaunchedEffect(showSaveDialog) {
                if (!isEditMode) {
                    resetDropdownSelections()
                    presetNameInput = ""
                }
            }

            Dialog(onDismissRequest = {
                showSaveDialog = false
                resetDropdownSelections()
                presetNameInput = ""
            }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp), // Increased corner radius for modern look
                    colors = CardDefaults.cardColors(
                        containerColor = NeutralCardSurface // Using UI.md consistent color
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 12.dp // Increased elevation for prominence
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp), // Further reduced padding to maximize space for dropdowns
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Enhanced title
                        Text(
                            text = "Save Schedule Preset",
                            style = MaterialTheme.typography.headlineSmall, // Larger title
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp)) // Reduced spacing since no section header

                        // Enhanced dropdowns
                        PresetDropdown(
                            label = "School Code",
                            value = selectedSchoolCode,
                            options = schoolCodes,
                            expanded = schoolCodeExpanded,
                            onExpandedChange = { schoolCodeExpanded = it },
                            onValueChange = { selectedSchoolCode = it },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp)) // Increased spacing

                        // Class and Section in a row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp) // Further reduced spacing to maximize dropdown width
                        ) {
                            PresetDropdown(
                                label = "Class",
                                value = selectedClass,
                                options = classes,
                                expanded = classExpanded,
                                onExpandedChange = { classExpanded = it },
                                onValueChange = { selectedClass = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(min = 170.dp) // Further increased minimum width for full word visibility
                            )

                            PresetDropdown(
                                label = "Section",
                                value = selectedSection,
                                options = sections,
                                expanded = sectionExpanded,
                                onExpandedChange = { sectionExpanded = it },
                                onValueChange = { selectedSection = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(min = 170.dp) // Further increased minimum width for full word visibility
                            )
                        }

                        // Enhanced preview section
                        if (generatedPresetName.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(20.dp)) // Increased spacing
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = YellowAccent
                                ),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "Preview:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Black,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = generatedPresetName,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Fallback text input for legacy names
                        if (generatedPresetName.isEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Or enter custom name:",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeutralGray,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = presetNameInput,
                                onValueChange = { presetNameInput = it },
                                label = { Text("Custom Preset Name") },
                                placeholder = { Text("Enter a custom name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = YellowAccent,
                                    unfocusedBorderColor = NeutralGray,
                                    focusedLabelColor = YellowAccent,
                                    unfocusedLabelColor = NeutralGray,
                                    cursorColor = YellowAccent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        if (saveErrorMessage != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = ErrorRed.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(end = 8.dp)
                                )
                                Text(
                                    text = saveErrorMessage!!,
                                    color = ErrorRed,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (showSavingIndicator) {
                            Spacer(modifier = Modifier.height(24.dp))
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Saving...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp)) // Increased spacing

                        // Enhanced buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp) // Increased spacing
                        ) {
                                // Enhanced Cancel button
                                OutlinedButton(
                                    onClick = {
                                        showSaveDialog = false
                                        resetDropdownSelections()
                                        presetNameInput = ""
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp), // Increased height for better touch target
                                    shape = RoundedCornerShape(12.dp), // Consistent with UI.md
                                    border = BorderStroke(
                                        width = 1.5.dp, // Slightly thicker border
                                        color = NeutralGray
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White,
                                        containerColor = Color.Transparent
                                    ),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        "Cancel",
                                        fontWeight = FontWeight.SemiBold, // Bolder text
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }

                                // Enhanced Save button (text only)
                                StandardButton(
                                    onClick = { savePreset() },
                                    enabled = !showSavingIndicator && (generatedPresetName.isNotEmpty() || presetNameInput.isNotBlank()),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp) // Consistent height
                                ) {
                                    Text(
                                        if (showSavingIndicator) "Saving..." else "Save",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetDropdown(
    label: String,
    value: String,
    options: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier
    ) {
        // Enhanced dropdown field with better styling and text wrapping prevention
        OutlinedTextField(
            value = value,
            onValueChange = { },
            readOnly = true,
            label = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall, // Reduced font size for better fit
                    fontWeight = FontWeight.Medium,
                    maxLines = 1, // Prevent label text wrapping
                    overflow = TextOverflow.Ellipsis, // Handle overflow gracefully
                    softWrap = false // Disable soft wrapping
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = YellowAccent,
                unfocusedBorderColor = NeutralGray.copy(alpha = 0.7f),
                focusedLabelColor = YellowAccent,
                unfocusedLabelColor = NeutralGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedTrailingIconColor = YellowAccent,
                unfocusedTrailingIconColor = NeutralGray,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(14.dp), // Slightly more rounded
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium
            ),
            singleLine = true, // Ensure value text stays on single line
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .heightIn(min = 56.dp) // Minimum height for better touch target
                .widthIn(min = 160.dp) // Further increased minimum width to ensure "Section" displays fully
        )

        // Enhanced dropdown menu with better styling
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier
                .background(
                    color = NeutralCardSurface,
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = YellowAccent.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (option == value) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    onClick = {
                        onValueChange(option)
                        onExpandedChange(false)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = Color.White,
                        leadingIconColor = YellowAccent,
                        trailingIconColor = YellowAccent
                    ),
                    modifier = Modifier
                        .background(
                            if (option == value) YellowAccent.copy(alpha = 0.1f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
fun DayCheckbox(
    day: String,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    // Yellow styling for selected state - instant state changes without transitions
    val backgroundColor = if (isSelected) YellowAccent else Color.Transparent
    val textColor = if (isSelected) Color.Black else Color.White

    Card(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .width(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                // No interaction source to prevent any press feedback animations
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onSelectionChanged(!isSelected)
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 0.dp
        ),
        border = if (isSelected) BorderStroke(
            width = 1.dp,
            color = YellowAccent.copy(alpha = 0.8f)
        ) else null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center
            )
        }
    }
}



@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 360, heightDp = 740)
@Composable
fun CreateTeachingSlotsScreenPreview() {
    SchedulingTheme {
        Surface(
            color = DarkBackground,
            modifier = Modifier.fillMaxSize()
        ) {
            CreateTeachingSlotsScreen(rememberNavController())
        }
    }
}