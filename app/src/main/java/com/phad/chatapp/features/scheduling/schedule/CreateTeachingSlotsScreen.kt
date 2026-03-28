package com.phad.chatapp.features.scheduling.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.phad.chatapp.features.scheduling.models.SubjectAllocation
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
import kotlin.math.roundToInt

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

// Swipe Wheel Picker Component
@Composable
fun SwipeWheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Int = 48
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val coroutineScope = rememberCoroutineScope()
    
    // Detect when scrolling stops and snap to nearest item
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            val firstVisibleOffset = listState.firstVisibleItemScrollOffset
            
            // Determine which item to snap to
            val snapToIndex = if (firstVisibleOffset > itemHeight / 2) {
                (firstVisibleIndex + 1).coerceIn(0, items.size - 1)
            } else {
                firstVisibleIndex
            }
            
            if (snapToIndex != selectedIndex) {
                onSelectedIndexChange(snapToIndex)
            }
            listState.animateScrollToItem(snapToIndex)
        }
    }
    
    Box(
        modifier = modifier
            .height((itemHeight * 3).dp)
            .width(70.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeight.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(items.size) { index ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .height(itemHeight.dp)
                        .fillMaxWidth()
                        .clickable {
                            onSelectedIndexChange(index)
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index],
                        fontSize = if (isSelected) 28.sp else 18.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) YellowAccent else Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        // Selection indicator
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight.dp)
                .border(
                    width = 2.dp,
                    color = YellowAccent.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
        )
    }
}

// Time Wheel Picker combining Hour and Minute wheels
@Composable
fun TimeWheelPicker(
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hours = (0..23).map { String.format("%02d", it) }
    val minutes = (0..59).map { String.format("%02d", it) }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Hour picker
        SwipeWheelPicker(
            items = hours,
            selectedIndex = hour,
            onSelectedIndexChange = { newHour ->
                onTimeChange(newHour, minute)
            }
        )
        
        // Colon separator
        Text(
            text = ":",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = YellowAccent,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        
        // Minute picker
        SwipeWheelPicker(
            items = minutes,
            selectedIndex = minute,
            onSelectedIndexChange = { newMinute ->
                onTimeChange(hour, newMinute)
            }
        )
    }
}

// Data structure for time slot information with both class and free group times
data class ColumnTimeInfo(
    val classTime: String = "",
    val freeGroupTime: String = ""
)

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
    // State for column time information (both class and free group times)
    var columnTimeInfoList by remember { mutableStateOf<List<ColumnTimeInfo>>(emptyList()) }

    var showRenameColumnDialog by remember { mutableStateOf(false) }
    var columnToRenameIndex by remember { mutableStateOf<Int?>(null) }
    var currentColumnName by remember { mutableStateOf("") }

    // Time picker states for Class Time
    var classTimeStartHour by remember { mutableStateOf(9) }
    var classTimeStartMinute by remember { mutableStateOf(0) }
    var classTimeEndHour by remember { mutableStateOf(10) }
    var classTimeEndMinute by remember { mutableStateOf(0) }
    
    // Time picker states for Free Group Time
    var freeGroupTimeStartHour by remember { mutableStateOf(10) }
    var freeGroupTimeStartMinute by remember { mutableStateOf(0) }
    var freeGroupTimeEndHour by remember { mutableStateOf(10) }
    var freeGroupTimeEndMinute by remember { mutableStateOf(30) }
    
    var timePickerError by remember { mutableStateOf<String?>(null) }

    // Time picker UI states
    var showClassTimeStartPicker by remember { mutableStateOf(false) }
    var showClassTimeEndPicker by remember { mutableStateOf(false) }
    var showFreeGroupTimeStartPicker by remember { mutableStateOf(false) }
    var showFreeGroupTimeEndPicker by remember { mutableStateOf(false) }

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
    val schoolCodes = listOf("RP", "AM", "DP", "UB", "FA", "KV", "TPS")
    val classes = listOf("6", "7", "8", "9", "10", "11", "12")
    val sections = listOf("B", "G", "N", "A", "B", "C")

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

    // Subject management states
    var subjectsList by remember { mutableStateOf<List<SubjectAllocation>>(emptyList()) }
    var availableSubjects by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSubjectDialog by remember { mutableStateOf(false) }

    // State to preserve availability data
    var existingAvailability by remember { mutableStateOf<Map<String, Any>?>(null) }

    var showCopyDialog by remember { mutableStateOf(false) }

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

    // Function to fetch subjects from TTW_Subjects collection
    fun fetchSubjects() {
        val db = FirebaseFirestore.getInstance()
        db.collection("TTW_Subjects")
            .get()
            .addOnSuccessListener { documents ->
                val subjects = documents.mapNotNull { it.id }
                availableSubjects = subjects.sorted()
                Log.d("CreateTeachingSlotsScreen", "Fetched ${subjects.size} subjects")
            }
            .addOnFailureListener { e ->
                Log.e("CreateTeachingSlotsScreen", "Error fetching subjects: ${e.message}")
            }
    }

    // Fetch subjects when screen opens
    LaunchedEffect(Unit) {
        fetchSubjects()
    }

    // Subject Management Dialog
    if (showSubjectDialog) {
        // Temporary state for the dialog
        val tempCounts = remember { 
            mutableStateMapOf<String, Int>().apply {
                subjectsList.forEach { put(it.subjectName, it.classCount) }
            }
        }

        AlertDialog(
            onDismissRequest = { showSubjectDialog = false },
            title = {
                Text(
                    "Manage Subjects",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp) 
                ) {
                    items(availableSubjects.size) { index ->
                        val subjectName = availableSubjects[index]
                        val count = tempCounts[subjectName] ?: 0
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = subjectName,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Decrease Button
                                IconButton(
                                    onClick = { 
                                        if (count > 0) tempCounts[subjectName] = count - 1 
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                                ) {
                                   Text(
                                       text = "-",
                                       color = YellowAccent,
                                       fontSize = 20.sp,
                                       fontWeight = FontWeight.Bold
                                   )
                                }

                                Text(
                                    text = count.toString(),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(24.dp), // Fixed width for alignment
                                    textAlign = TextAlign.Center
                                )

                                // Increase Button
                                IconButton(
                                    onClick = { tempCounts[subjectName] = count + 1 },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Increase",
                                        tint = YellowAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        if (index < availableSubjects.size - 1) {
                            Divider(color = Color.White.copy(alpha = 0.1f))
                        }
                    }
                }
            },
            confirmButton = {
                StandardButton(
                    onClick = {
                        // Save changes
                        // Save changes - Preserve order
                        val newSubjectsList = mutableListOf<SubjectAllocation>()
                        val processedSubjects = mutableSetOf<String>()

                        // 1. Keep existing subjects in their original order if they still have count > 0
                        subjectsList.forEach { existing ->
                            val newCount = tempCounts[existing.subjectName] ?: 0
                            if (newCount > 0) {
                                newSubjectsList.add(SubjectAllocation(existing.subjectName, newCount))
                                processedSubjects.add(existing.subjectName)
                            }
                        }

                        // 2. Add new subjects (that weren't in the list before)
                        // Filter availableSubjects to find ones that have count > 0 and weren't processed
                        val newSubjects = availableSubjects.filter { subjectName ->
                             val count = tempCounts[subjectName] ?: 0
                             count > 0 && !processedSubjects.contains(subjectName)
                        }.map { subjectName ->
                             SubjectAllocation(subjectName, tempCounts[subjectName] ?: 0)
                        } // availableSubjects is already sorted, so these will be sorted

                        newSubjectsList.addAll(newSubjects)
                        
                        subjectsList = newSubjectsList
                        
                        showSubjectDialog = false
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceElevated,
            shape = RoundedCornerShape(16.dp)
        )
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

                        // Get column names - support both old (List<String>) and new (List<Map>) formats
                        val columnData = document.get("columnNames")
                        val columnTimeInfos = mutableListOf<ColumnTimeInfo>()
                        
                        when (columnData) {
                            is List<*> -> {
                                // Check if it's old format (List<String>) or new format (List<Map>)
                                if (columnData.isNotEmpty()) {
                                    when (val firstItem = columnData[0]) {
                                        is String -> {
                                            // Old format - migrate by adding default free group time
                                            Log.d("CreateTeachingSlotsScreen", "Migrating old format preset")
                                            columnData.filterIsInstance<String>().forEach { timeStr ->
                                                // Parse the end time and add 30 minutes for free group time
                                                val endTimeMatch = Regex("""(\d{1,2}):(\d{2})$""").find(timeStr)
                                                val freeGroupTime = if (endTimeMatch != null) {
                                                    val endHour = endTimeMatch.groupValues[1].toInt()
                                                    val endMinute = endTimeMatch.groupValues[2].toInt()
                                                    val freeGroupEndHour = if (endMinute + 30 >= 60) endHour + 1 else endHour
                                                    val freeGroupEndMinute = (endMinute + 30) % 60
                                                    String.format("%02d:%02d-%02d:%02d", endHour, endMinute, freeGroupEndHour, freeGroupEndMinute)
                                                } else {
                                                    "10:00-10:30" // Fallback
                                                }
                                                columnTimeInfos.add(ColumnTimeInfo(classTime = timeStr, freeGroupTime = freeGroupTime))
                                            }
                                        }
                                        is Map<*, *> -> {
                                            // New format
                                            Log.d("CreateTeachingSlotsScreen", "Loading new format preset")
                                            columnData.filterIsInstance<Map<String, Any>>().forEach { map ->
                                                val classTime = map["classTime"] as? String ?: ""
                                                val freeGroupTime = map["freeGroupTime"] as? String ?: ""
                                                columnTimeInfos.add(ColumnTimeInfo(classTime, freeGroupTime))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        columnTimeInfoList = columnTimeInfos

                        // Update slot count input based on columns
                        slotCountInput = columnTimeInfos.size.toString()

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
                        
                        // Get subjects and sort by priority
                        val subjectsData = document.get("subjects") as? List<Map<String, Any>> ?: emptyList()
                        subjectsList = subjectsData.mapNotNull { map ->
                            val name = map["subjectName"] as? String
                            val count = (map["classCount"] as? Number)?.toInt() ?: 0
                            val priority = (map["priority"] as? Number)?.toInt() ?: 0
                            if (name != null) SubjectAllocation(name, count, priority) else null
                        }.sortedBy { it.priority }

                        // Preserve availability data
                        existingAvailability = document.get("availability") as? Map<String, Any>
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
            // Update Column Time Info
            val newColumnTimeInfoList = List(numSlots) { index ->
                columnTimeInfoList.getOrNull(index) ?: ColumnTimeInfo() // Preserve existing info or create empty
            }
            columnTimeInfoList = newColumnTimeInfoList

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
            columnTimeInfoList = emptyList()
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
        if (index >= 0 && index < columnTimeInfoList.size) {
            columnToRenameIndex = index
            val currentTimeInfo = columnTimeInfoList[index]

            // Parse class time
            val classTimePattern = Regex("""(\d{1,2}):(\d{2})-(\d{1,2}):(\d{2})""")
            val classTimeMatch = classTimePattern.find(currentTimeInfo.classTime)
            if (classTimeMatch != null) {
                classTimeStartHour = classTimeMatch.groupValues[1].toInt()
                classTimeStartMinute = classTimeMatch.groupValues[2].toInt()
                classTimeEndHour = classTimeMatch.groupValues[3].toInt()
                classTimeEndMinute = classTimeMatch.groupValues[4].toInt()
            } else {
                // Default class time values
                classTimeStartHour = 9
                classTimeStartMinute = 0
                classTimeEndHour = 10
                classTimeEndMinute = 0
            }

            // Parse free group time
            val freeGroupTimeMatch = classTimePattern.find(currentTimeInfo.freeGroupTime)
            if (freeGroupTimeMatch != null) {
                freeGroupTimeStartHour = freeGroupTimeMatch.groupValues[1].toInt()
                freeGroupTimeStartMinute = freeGroupTimeMatch.groupValues[2].toInt()
                freeGroupTimeEndHour = freeGroupTimeMatch.groupValues[3].toInt()
                freeGroupTimeEndMinute = freeGroupTimeMatch.groupValues[4].toInt()
            } else {
                // Default free group time to 30 minutes after class time
                freeGroupTimeStartHour = classTimeEndHour
                freeGroupTimeStartMinute = classTimeEndMinute
                val freeEndMinute = classTimeEndMinute + 30
                freeGroupTimeEndHour = if (freeEndMinute >= 60) classTimeEndHour + 1 else classTimeEndHour
                freeGroupTimeEndMinute = freeEndMinute % 60
            }

            timePickerError = null
            showRenameColumnDialog = true
        }
    }

    fun validateAndSaveTimeRange() {
        // Validate class time
        val classTimeStartMinutes = classTimeStartHour * 60 + classTimeStartMinute
        val classTimeEndMinutes = classTimeEndHour * 60 + classTimeEndMinute

        if (classTimeEndMinutes <= classTimeStartMinutes) {
            timePickerError = "Class end time must be after class start time"
            return
        }

        // Validate free group time
        val freeGroupTimeStartMinutes = freeGroupTimeStartHour * 60 + freeGroupTimeStartMinute
        val freeGroupTimeEndMinutes = freeGroupTimeEndHour * 60 + freeGroupTimeEndMinute

        if (freeGroupTimeEndMinutes <= freeGroupTimeStartMinutes) {
            timePickerError = "Free group end time must be after free group start time"
            return
        }

        // Format both time ranges
        val formattedClassTime = String.format("%02d:%02d-%02d:%02d", classTimeStartHour, classTimeStartMinute, classTimeEndHour, classTimeEndMinute)
        val formattedFreeGroupTime = String.format("%02d:%02d-%02d:%02d", freeGroupTimeStartHour, freeGroupTimeStartMinute, freeGroupTimeEndHour, freeGroupTimeEndMinute)

        columnToRenameIndex?.let { index ->
            if (index >= 0 && index < columnTimeInfoList.size) {
                val updatedColumnTimeInfoList = columnTimeInfoList.toMutableList().apply {
                    this[index] = ColumnTimeInfo(classTime = formattedClassTime, freeGroupTime = formattedFreeGroupTime)
                }
                columnTimeInfoList = updatedColumnTimeInfoList.toList()
            }
        }
        showRenameColumnDialog = false
        columnToRenameIndex = null
        timePickerError = null
    }
    // --- End Column Renaming ---

    fun validateSchedule(): Boolean {
        // Validate Subject Class Counts
        val totalClasses = subjectsList.sumOf { it.classCount }
        
        // Calculate actual slots from grid (counting non-null/active slots)
        val totalSlots = teachingSchedule.sumOf { daySlot -> 
            daySlot.slots.count { it != null } 
        }
        
        if (totalClasses != totalSlots) {
             coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    "Expected $totalSlots classes, but defined $totalClasses."
                )
            }
            return false
        }
        return true
    }

    // --- Save Preset Logic ---
    fun savePreset() {
        if (!validateSchedule()) return

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

        // Convert columnTimeInfoList to saveable format
        val columnNamesData = columnTimeInfoList.map { timeInfo ->
            mapOf(
                "classTime" to timeInfo.classTime,
                "freeGroupTime" to timeInfo.freeGroupTime
            )
        }
        
        // Convert subjectsList to saveable format with priority
        val subjectsData = subjectsList.mapIndexed { index, subject ->
            mapOf(
                "subjectName" to subject.subjectName,
                "classCount" to subject.classCount,
                "priority" to (index + 1)  // Priority starts from 1
            )
        }

        // Create the preset data
        val presetData = hashMapOf(
            "presetName" to nameToUse,
            "columnNames" to columnNamesData,
            "schedule" to scheduleData,
            "subjects" to subjectsData
        )

        // Add back existing availability data if present
        if (existingAvailability != null) {
            presetData["availability"] = existingAvailability!!
        }

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
            snackbarHost = { 
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = 90.dp)
                ) 
            }
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp)
                        .padding(start = 4.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(
                        onClick = { navController.navigateUp() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Title
                    Text(
                        text = if (isEditMode) "Edit Slots Preset" else "Create Slots Preset",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )

                    // Copy Button (Circular)
                    if (!isLoading) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp, end = 4.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(YellowAccent)
                                .clickable { showCopyDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Preset",
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
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
                            .padding(horizontal = 16.dp)
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
                                            .padding(bottom = 4.dp),
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

                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // Step 2: Number of slots per day - Compact UI
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Number of Slots per Day:",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Up arrow
                                        IconButton(
                                            onClick = {
                                                val currentValue = slotCountInput.toIntOrNull() ?: 0
                                                if (currentValue < 8) {
                                                    slotCountInput = (currentValue + 1).toString()
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Increase slots",
                                                tint = YellowAccent,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        
                                        // Number display
                                        Text(
                                            text = slotCountInput,
                                            color = YellowAccent,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.widthIn(min = 32.dp),
                                            textAlign = TextAlign.Center
                                        )
                                        
                                        // Down arrow
                                        IconButton(
                                            onClick = {
                                                val currentValue = slotCountInput.toIntOrNull() ?: 0
                                                if (currentValue > 1) {
                                                    slotCountInput = (currentValue - 1).toString()
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Decrease slots",
                                                tint = YellowAccent,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                            // Subjects Section
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = SurfaceElevated.copy(alpha = 0.6f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Subjects:",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        
                                        IconButton(
                                            onClick = { showSubjectDialog = true },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit subjects",
                                                tint = YellowAccent,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    
                                    if (subjectsList.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        DraggableSubjectGrid(
                                            subjects = subjectsList,
                                            onReorder = { fromIndex, toIndex ->
                                                val mutableList = subjectsList.toMutableList()
                                                // Simple swap operation
                                                val temp = mutableList[fromIndex]
                                                mutableList[fromIndex] = mutableList[toIndex]
                                                mutableList[toIndex] = temp
                                                subjectsList = mutableList
                                            }
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "No subjects added",
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
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
                                                columnTimeInfoList.forEachIndexed { index, timeInfo ->
                                                    Column(
                                                        modifier = Modifier
                                                            .width(100.dp) // Increased width to accommodate two lines
                                                            .padding(horizontal = 2.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Card(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable { openRenameColumnDialog(index) },
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
                                                                    text = timeInfo.classTime.ifEmpty { "--:--" },
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
                                                                    text = timeInfo.freeGroupTime.ifEmpty { "--:--" },
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
                                                                .width(100.dp) // Match header width
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

        // Manually positioned FAB (TTW_UI_plan.md spec)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 24.dp, bottom = 48.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            if (teachingSchedule.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        if (validateSchedule()) {
                            if (isEditMode && presetNameInput.isNotEmpty()) {
                                savePreset()
                            } else {
                                showSaveDialog = true
                            }
                        }
                    },
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = if (isEditMode) "Save" else "Create",
                        modifier = Modifier.size(24.dp)
                    )
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
                        // Class Time Section
                        Text(
                            text = "Class Time",
                            color = YellowAccent,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Class Start Time Column
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "Start Time",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showClassTimeStartPicker = true },
                                    colors = CardDefaults.cardColors(
                                        containerColor = YellowAccent.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, YellowAccent.copy(alpha = 0.3f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            String.format("%02d:%02d", classTimeStartHour, classTimeStartMinute),
                                            color = YellowAccent,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Class End Time Column
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "End Time",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showClassTimeEndPicker = true },
                                    colors = CardDefaults.cardColors(
                                        containerColor = YellowAccent.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, YellowAccent.copy(alpha = 0.3f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            String.format("%02d:%02d", classTimeEndHour, classTimeEndMinute),
                                            color = YellowAccent,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Free Group Time Section
                        Text(
                            text = "Free Group Time",
                            color = YellowAccent,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Free Group Start Time Column
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "Start Time",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showFreeGroupTimeStartPicker = true },
                                    colors = CardDefaults.cardColors(
                                        containerColor = YellowAccent.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, YellowAccent.copy(alpha = 0.3f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            String.format("%02d:%02d", freeGroupTimeStartHour, freeGroupTimeStartMinute),
                                            color = YellowAccent,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Free Group End Time Column
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "End Time",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showFreeGroupTimeEndPicker = true },
                                    colors = CardDefaults.cardColors(
                                        containerColor = YellowAccent.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, YellowAccent.copy(alpha = 0.3f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            String.format("%02d:%02d", freeGroupTimeEndHour, freeGroupTimeEndMinute),
                                            color = YellowAccent,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
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

        // Class Time Start Picker Dialog
        if (showClassTimeStartPicker) {
            var tempHour by remember { mutableStateOf(classTimeStartHour) }
            var tempMinute by remember { mutableStateOf(classTimeStartMinute) }
            
            AlertDialog(
                onDismissRequest = { showClassTimeStartPicker = false },
                title = {
                    Text(
                        "Select Class Start Time",
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
                        TimeWheelPicker(
                            hour = tempHour,
                            minute = tempMinute,
                            onTimeChange = { h, m ->
                                tempHour = h
                                tempMinute = m
                            }
                        )
                    }
                },
                confirmButton = {
                    StandardButton(
                        onClick = {
                            classTimeStartHour = tempHour
                            classTimeStartMinute = tempMinute
                            timePickerError = null
                            showClassTimeStartPicker = false
                        }
                    ) {
                        Text("Set", fontWeight = FontWeight.Medium)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClassTimeStartPicker = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Medium)
                    }
                },
                containerColor = SurfaceElevated,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Class Time End Picker Dialog
        if (showClassTimeEndPicker) {
            var tempHour by remember { mutableStateOf(classTimeEndHour) }
            var tempMinute by remember { mutableStateOf(classTimeEndMinute) }
            
            AlertDialog(
                onDismissRequest = { showClassTimeEndPicker = false },
                title = {
                    Text(
                        "Select Class End Time",
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
                        TimeWheelPicker(
                            hour = tempHour,
                            minute = tempMinute,
                            onTimeChange = { h, m ->
                                tempHour = h
                                tempMinute = m
                            }
                        )
                    }
                },
                confirmButton = {
                    StandardButton(
                        onClick = {
                            classTimeEndHour = tempHour
                            classTimeEndMinute = tempMinute
                            timePickerError = null
                            showClassTimeEndPicker = false
                        }
                    ) {
                        Text("Set", fontWeight = FontWeight.Medium)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClassTimeEndPicker = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Medium)
                    }
                },
                containerColor = SurfaceElevated,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Free Group Time Start Picker Dialog
        if (showFreeGroupTimeStartPicker) {
            var tempHour by remember { mutableStateOf(freeGroupTimeStartHour) }
            var tempMinute by remember { mutableStateOf(freeGroupTimeStartMinute) }
            
            AlertDialog(
                onDismissRequest = { showFreeGroupTimeStartPicker = false },
                title = {
                    Text(
                        "Select Free Group Start Time",
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
                        TimeWheelPicker(
                            hour = tempHour,
                            minute = tempMinute,
                            onTimeChange = { h, m ->
                                tempHour = h
                                tempMinute = m
                            }
                        )
                    }
                },
                confirmButton = {
                    StandardButton(
                        onClick = {
                            freeGroupTimeStartHour = tempHour
                            freeGroupTimeStartMinute = tempMinute
                            timePickerError = null
                            showFreeGroupTimeStartPicker = false
                        }
                    ) {
                        Text("Set", fontWeight = FontWeight.Medium)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showFreeGroupTimeStartPicker = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Medium)
                    }
                },
                containerColor = SurfaceElevated,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Free Group Time End Picker Dialog
        if (showFreeGroupTimeEndPicker) {
            var tempHour by remember { mutableStateOf(freeGroupTimeEndHour) }
            var tempMinute by remember { mutableStateOf(freeGroupTimeEndMinute) }
            
            AlertDialog(
                onDismissRequest = { showFreeGroupTimeEndPicker = false },
                title = {
                    Text(
                        "Select Free Group End Time",
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
                        TimeWheelPicker(
                            hour = tempHour,
                            minute = tempMinute,
                            onTimeChange = { h, m ->
                                tempHour = h
                                tempMinute = m
                            }
                        )
                    }
                },
                confirmButton = {
                    StandardButton(
                        onClick = {
                            freeGroupTimeEndHour = tempHour
                            freeGroupTimeEndMinute = tempMinute
                            timePickerError = null
                            showFreeGroupTimeEndPicker = false
                        }
                    ) {
                        Text("Set", fontWeight = FontWeight.Medium)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showFreeGroupTimeEndPicker = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Medium)
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

    // --- Copy Preset Logic ---
    if (showCopyDialog) {
        CopyPresetDialog(
            onDismiss = { showCopyDialog = false },
            onPresetSelected = { preset ->
                // Apply preset data
                coroutineScope.launch {
                    try {
                        // 1. Set Slot Count and Days
                        slotCountInput = preset.slotCount.toString()
                        selectedDays = preset.days.toSet()
                        
                        // 2. Parse Schedule Data to restore grid
                        val newSchedule = preset.days.map { day ->
                             val daySlots = preset.schedule.find { (it["day"] as? String) == day }
                             val slotFlags = daySlots?.get("slots") as? List<Boolean> ?: List(preset.slotCount) { false }
                             
                             DaySlots(
                                 day = day,
                                 slots = slotFlags.map { active ->
                                     if (active) ScreenTeachingSlot(id = java.util.UUID.randomUUID().toString()) else null
                                 }
                             )
                        }
                        teachingSchedule = newSchedule
                        
                        // 3. Parse Column Names (Time Info)
                        val newTimeInfo = preset.columnNames.map {
                             ColumnTimeInfo(
                                 classTime = it["classTime"] as? String ?: "",
                                 freeGroupTime = it["freeGroupTime"] as? String ?: ""
                             )
                        }
                        columnTimeInfoList = newTimeInfo

                        // 4. Parse Subjects
                        val newSubjects = preset.subjects.map {
                             SubjectAllocation(
                                 subjectName = it["subjectName"] as? String ?: "",
                                 classCount = (it["classCount"] as? Number)?.toInt() ?: 0
                             )
                        }
                        subjectsList = newSubjects

                        showCopyDialog = false
                        snackbarHostState.showSnackbar("Preset '${preset.name}' copied successfully!")
                    } catch (e: Exception) {
                        Log.e("CopyPreset", "Error applying preset", e)
                        snackbarHostState.showSnackbar("Error copying preset: ${e.localizedMessage}")
                    }
                }
            }
        )
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
            .clickable {
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

    



// Helper Data Class for Preset Copying
data class CopyPresetItem(
    val id: String,
    val name: String,
    val days: List<String>,
    val slotCount: Int,
    val schedule: List<Map<String, Any>>,
    val columnNames: List<Map<String, Any>>,
    val subjects: List<Map<String, Any>>
)

@Composable
fun CopyPresetDialog(
    onDismiss: () -> Unit,
    onPresetSelected: (CopyPresetItem) -> Unit
) {
    var presets by remember { mutableStateOf<List<CopyPresetItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(Unit) {
        try {
            val result = db.collection("teachingSlotPresets").get().await()
            presets = result.documents.mapNotNull { doc ->
                try {
                     val scheduleData = doc.get("schedule") as? List<Map<String, Any>> ?: emptyList()
                     val days = scheduleData.mapNotNull { it["day"] as? String }
                     
                     // Helper to handle both legacy (String) and map (Map) columnNames
                     val rawColumns = doc.get("columnNames") as? List<*> ?: emptyList<Any>()
                     val columnNames = rawColumns.map { item ->
                        when(item) {
                            is Map<*, *> -> item as Map<String, Any>
                            is String -> mapOf("classTime" to item, "freeGroupTime" to "")
                            else -> mapOf("classTime" to "", "freeGroupTime" to "")
                        }
                     }

                     val subjects = doc.get("subjects") as? List<Map<String, Any>> ?: emptyList()
                     
                     CopyPresetItem(
                         id = doc.id,
                         name = doc.getString("presetName") ?: "Unnamed",
                         days = days,
                         slotCount = columnNames.size,
                         schedule = scheduleData,
                         columnNames = columnNames,
                         subjects = subjects
                     )
                } catch (e: Exception) {
                    null
                }
            }
            isLoading = false
        } catch (e: Exception) {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Copy From Preset",
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
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                         CircularProgressIndicator(color = YellowAccent)
                    }
                } else if (presets.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No presets available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB0B0B0)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        itemsIndexed(presets) { _, preset ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPresetSelected(preset) },
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                     Text(
                                         preset.name,
                                         color = Color.White,
                                         style = MaterialTheme.typography.titleSmall,
                                         fontWeight = FontWeight.Bold
                                     )
                                     Text(
                                         text = "${preset.days.size} days, ${preset.slotCount} slots",
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
                onClick = onDismiss,
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

@Composable
fun DraggableSubjectGrid(
    subjects: List<SubjectAllocation>,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    
    // Chunk subjects into rows of 3
    val rows = subjects.chunked(3)
    
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEachIndexed { rowIndex, rowSubjects ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowSubjects.forEachIndexed { colIndex, subject ->
                    val globalIndex = rowIndex * 3 + colIndex
                    val isSelected = selectedIndex == globalIndex
                    val shortName = if (subject.subjectName.length > 3) 
                        subject.subjectName.take(3) 
                    else subject.subjectName
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (isSelected) 
                                    YellowAccent  // Selected: yellow background
                                else 
                                    Color.White.copy(alpha = 0.1f),  // Unselected: transparent white
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected)
                                    YellowAccent
                                else
                                    YellowAccent.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .pointerInput(globalIndex, subjects.size, selectedIndex) {
                                detectTapGestures(
                                    onLongPress = {
                                        // Long press to select
                                        selectedIndex = globalIndex
                                    },
                                    onTap = {
                                        // Click to swap with selected
                                        if (selectedIndex != null && selectedIndex != globalIndex) {
                                            onReorder(selectedIndex!!, globalIndex)
                                            selectedIndex = null
                                        }
                                    }
                                )
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$shortName : ${subject.classCount}",
                            color = if (isSelected) Color.Black else Color.White,  // Selected: black text, Unselected: white text
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
                
                // Fill empty spaces if last row has fewer than 3 items
                if (rowSubjects.size < 3) {
                    repeat(3 - rowSubjects.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            
            if (rowIndex < rows.size - 1) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}