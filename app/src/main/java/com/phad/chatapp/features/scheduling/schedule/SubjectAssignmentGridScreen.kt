package com.phad.chatapp.features.scheduling.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import android.util.Log
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.navigation.NavController
import com.phad.chatapp.features.scheduling.models.OptimizedVolunteerAssignment
import com.phad.chatapp.features.scheduling.models.GroupAvailability
import com.phad.chatapp.features.scheduling.models.ScheduleReferenceData
import com.phad.chatapp.features.scheduling.models.SubjectPreset
import com.phad.chatapp.features.scheduling.models.SubjectConstants
import com.phad.chatapp.features.scheduling.models.VolunteerDetails
import com.phad.chatapp.features.scheduling.ui.theme.DarkBackground
import com.phad.chatapp.features.scheduling.ui.theme.NeutralCardSurface
import com.phad.chatapp.features.scheduling.ui.theme.YellowAccent
import com.phad.chatapp.features.scheduling.firebase.FirestoreCollection
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.window.DialogProperties

data class ScheduleGridData(
    val referenceData: ScheduleReferenceData,
    val volunteerAssignments: List<OptimizedVolunteerAssignment>,
    val groupAvailability: List<GroupAvailability>,
    val subjectPreset: SubjectPreset
)

data class GridCell(
    val dayIndex: Int,
    val slotIndex: Int,
    val volunteerName: String,
    val volunteerRollNo: String,
    val volunteerGroup: String,
    val assignedSubject: String? = null,
    val isHighlighted: Boolean = false,
    val scheduleId: String = "", // Track which schedule this cell belongs to
    val scheduleName: String = "", // Track schedule name for display
    // Enhanced volunteer data from generateSchedule collection
    val interviewScore: Int = 0,
    val subjectPreference1: String = "",
    val subjectPreference2: String = "",
    val subjectPreference3: String = ""
)

@Composable
fun SubjectAssignmentGridScreen(
    navController: NavController,
    scheduleIds: String,
    pairingData: String
) {
    var isLoading by remember { mutableStateOf(true) }
    var scheduleData by remember { mutableStateOf<List<ScheduleGridData>>(emptyList()) }
    var selectedCell by remember { mutableStateOf<GridCell?>(null) }
    var highlightedCells by remember { mutableStateOf<Set<Triple<Int, Int, String>>>(emptySet()) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var detailsCell by remember { mutableStateOf<GridCell?>(null) }
    var isSwapping by remember { mutableStateOf(false) }
    var showSubjectAssignmentDialog by remember { mutableStateOf(false) }
    var subjectAssignmentCell by remember { mutableStateOf<GridCell?>(null) }
    var topTalentHighlightedCell by remember { mutableStateOf<GridCell?>(null) }
    var showTopTalentSnackbar by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var subjectAssignments by remember { mutableStateOf<MutableMap<String, MutableMap<Pair<Int, Int>, String>>>(mutableMapOf()) }
    // Add variables to track remaining subject counts
    var remainingSubjectCounts by remember { mutableStateOf<MutableMap<String, MutableMap<String, Int>>>(mutableMapOf()) }
    // Add variable to show warning dialog for duplicate subject assignments
    var showDuplicateSubjectWarning by remember { mutableStateOf(false) }
    var duplicateSubjectInfo by remember { mutableStateOf<Triple<GridCell, String, Boolean>?>(null) } // Cell, subject, forceProceed flag

    // New state variable for tracking local volunteer swaps
    var localVolunteerSwaps by remember { mutableStateOf<MutableMap<String, MutableMap<Pair<Int, Int>, GridCell>>>(mutableMapOf()) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Add this effect to clear highlighting when selected cell changes
    LaunchedEffect(selectedCell) {
        // If selected cell is null, make sure highlighting is cleared
        if (selectedCell == null) {
            highlightedCells = emptySet()
            
            // If top talent was cleared during selection, find and restore it
            if (topTalentHighlightedCell == null) {
                val nextTopTalent = findTopTalentWithoutSubject(scheduleData, subjectAssignments, localVolunteerSwaps)
                if (nextTopTalent != null) {
                    topTalentHighlightedCell = nextTopTalent
                    showTopTalentSnackbar = true
                }
            }
        }
    }

    // Define the assignSubjectToCell function here at the top level of the composable
    // to make it accessible throughout the function
    val assignSubjectToCell = { cell: GridCell, subjectCode: String ->
        val scheduleName = cell.scheduleName
        val cellKey = cell.dayIndex to cell.slotIndex
        
        // Get previous subject code if any
        val previousSubjectCode = subjectAssignments[scheduleName]?.get(cellKey)
        
        Log.d("SubjectAssignment", "Assigning subject $subjectCode to ${cell.volunteerName} at ($cellKey), previous subject: $previousSubjectCode")
        
        // Initialize map for this schedule if it doesn't exist
        if (!subjectAssignments.containsKey(scheduleName)) {
            subjectAssignments[scheduleName] = mutableMapOf()
        }
        
        // Handle unassignment (empty subject code)
        if (subjectCode.isEmpty() && previousSubjectCode != null) {
            // Remove the assignment
            subjectAssignments[scheduleName]!!.remove(cellKey)
            
            // Return previously assigned subject to the pool
            if (remainingSubjectCounts[scheduleName]?.containsKey(previousSubjectCode) == true) {
                remainingSubjectCounts[scheduleName]!![previousSubjectCode] = 
                    (remainingSubjectCounts[scheduleName]!![previousSubjectCode] ?: 0) + 1
            }
            
            Log.d("SubjectAssignment", "Unassigned subject from ${cell.volunteerName}")
        } else if (subjectCode.isNotEmpty()) {
            // Add or update subject assignment
            subjectAssignments[scheduleName]!![cellKey] = subjectCode
            
            // Update remaining subject counts
            if (!remainingSubjectCounts.containsKey(scheduleName)) {
                remainingSubjectCounts[scheduleName] = mutableMapOf()
            }
            
            // Return previously assigned subject to the pool if any
            if (previousSubjectCode != null && remainingSubjectCounts[scheduleName]?.containsKey(previousSubjectCode) == true) {
                remainingSubjectCounts[scheduleName]!![previousSubjectCode] = 
                    (remainingSubjectCounts[scheduleName]!![previousSubjectCode] ?: 0) + 1
            }
            
            // Decrease count for newly assigned subject
            if (remainingSubjectCounts[scheduleName]?.containsKey(subjectCode) == true) {
                remainingSubjectCounts[scheduleName]!![subjectCode] = 
                    (remainingSubjectCounts[scheduleName]!![subjectCode] ?: 1) - 1
            }
        }
        
        // Log the updated assignments for this schedule
        Log.d("SubjectAssignment", "Updated assignments for $scheduleName: ${subjectAssignments[scheduleName]}")
        
        // Update UI and close all dialogs
        showSubjectAssignmentDialog = false
        showDetailsDialog = false  // Close the info dialog
        
        // Always find and highlight the next top talent immediately
        val nextTopTalent = findTopTalentWithoutSubject(scheduleData, subjectAssignments, localVolunteerSwaps)
        
        // Always update the top talent highlight, even if null
        topTalentHighlightedCell = nextTopTalent
        
        // Show top talent snackbar if we found one
        if (nextTopTalent != null) {
            showTopTalentSnackbar = true
        }
        
        // Show feedback in snackbar
        coroutineScope.launch {
            if (subjectCode.isEmpty()) {
                snackbarHostState.showSnackbar("Subject unassigned from ${cell.volunteerName}")
            } else {
                snackbarHostState.showSnackbar("Subject $subjectCode assigned to ${cell.volunteerName}")
            }
        }
    }

    // Parse navigation parameters
    val scheduleIdList = scheduleIds.split(",").filter { it.isNotBlank() }
    val pairings = pairingData.split("|").associate { pair ->
        val parts = pair.split(":")
        if (parts.size == 2) parts[0] to parts[1] else "" to ""
    }.filterKeys { it.isNotEmpty() }

    Log.d("SubjectAssignmentGrid", "Received scheduleIds: $scheduleIds")
    Log.d("SubjectAssignmentGrid", "Received pairingData: $pairingData")
    Log.d("SubjectAssignmentGrid", "Parsed schedule list: $scheduleIdList")
    Log.d("SubjectAssignmentGrid", "Parsed pairings: $pairings")

    // Load schedule data
    LaunchedEffect(scheduleIds, pairingData) {
        loadScheduleData(scheduleIdList, pairings) { data ->
            scheduleData = data
            isLoading = false
            Log.d("SubjectAssignmentGrid", "Data loading completed. Loaded ${data.size} schedules")
            
            // Initialize subject assignments from loaded data
            data.forEach { schedule ->
                val scheduleName = schedule.referenceData.schoolName
                val assignmentsForSchedule = mutableMapOf<Pair<Int, Int>, String>()
                
                schedule.volunteerAssignments.forEach { assignment ->
                    if (assignment.assignedSubject != null) {
                        assignmentsForSchedule[assignment.dayIndex to assignment.slotIndex] = assignment.assignedSubject
                    }
                }
                
                if (assignmentsForSchedule.isNotEmpty()) {
                    subjectAssignments[scheduleName] = assignmentsForSchedule
                }
                
                // Initialize remaining subject counts
                val subjectCounts = schedule.subjectPreset.subjects.toMutableMap()
                
                // Reduce counts for already assigned subjects
                assignmentsForSchedule.values.forEach { subjectCode ->
                    if (subjectCounts.containsKey(subjectCode)) {
                        subjectCounts[subjectCode] = (subjectCounts[subjectCode] ?: 0) - 1
                    }
                }
                
                remainingSubjectCounts[scheduleName] = subjectCounts
            }
        }
    }

    // Add this LaunchedEffect to highlight top talent when data is loaded
    LaunchedEffect(isLoading, scheduleData) {
        if (!isLoading && scheduleData.isNotEmpty()) {
            // Find and highlight the top talent volunteer
            val topTalent = findTopTalentWithoutSubject(scheduleData, subjectAssignments, localVolunteerSwaps)
            if (topTalent != null) {
                // Clear any previous selections
                selectedCell = null
                highlightedCells = emptySet()
                
                // Store the full cell data to properly identify and highlight it
                topTalentHighlightedCell = topTalent
                showTopTalentSnackbar = true
            }
        }
    }
    
    // Handle top talent snackbar display
    LaunchedEffect(showTopTalentSnackbar) {
        if (showTopTalentSnackbar) {
            topTalentHighlightedCell?.let { cell ->
                snackbarHostState.showSnackbar("Highlighted top talent: ${cell.volunteerName} (Score: ${cell.interviewScore}/100)")
            }
            showTopTalentSnackbar = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
        ) {
            // Header - updated to match ViewAssignmentsScreen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    
                    Text(
                        text = "Assign Subject",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .weight(1f)
                    )
                    
                    // Save button with text and rounded rectangle
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isSaving = true
                                val success = saveSubjectAssignments(scheduleData, subjectAssignments, localVolunteerSwaps)
                                isSaving = false
                                if (success) {
                                    snackbarHostState.showSnackbar("Subject assignments and volunteer swaps saved successfully")
                                    
                                    // Always find and highlight the top talent after saving
                                    val nextTopTalent = findTopTalentWithoutSubject(scheduleData, subjectAssignments, localVolunteerSwaps)
                                    
                                    // Always update the top talent highlight, even if null
                                    topTalentHighlightedCell = nextTopTalent
                                    
                                    // Show top talent snackbar if we found one
                                    if (nextTopTalent != null) {
                                        showTopTalentSnackbar = true
                                    }
                                } else {
                                    snackbarHostState.showSnackbar("Failed to save assignments")
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowAccent,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "Save",
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = YellowAccent)
                }
            } else {
                if (scheduleData.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp) // Increased bottom padding
                    ) {
                        scheduleData.forEach { schedule ->
                            item {
                                ScheduleGridCard(
                                    scheduleData = schedule,
                                    selectedCell = selectedCell,
                                    highlightedCells = highlightedCells,
                                    topTalentHighlightedCell = topTalentHighlightedCell,
                                    subjectAssignments = subjectAssignments[schedule.referenceData.schoolName] ?: mutableMapOf(),
                                    onCellLongPress = { cell ->
                                        // Allow long press if cell doesn't have a subject assigned OR if it's the top talent
                                        val currentTopTalent = topTalentHighlightedCell
                                        val isTopTalent = currentTopTalent != null &&
                                                        cell.volunteerName == currentTopTalent.volunteerName &&
                                                        cell.volunteerRollNo == currentTopTalent.volunteerRollNo

                                        if (cell.assignedSubject == null || isTopTalent) {
                                            // Store the current top talent before selection (already captured above)
                                            
                                            // Check if this is the top talent cell
                                            val isTopTalentCell = currentTopTalent != null &&
                                                                 cell.volunteerName == currentTopTalent.volunteerName &&
                                                                 cell.volunteerRollNo == currentTopTalent.volunteerRollNo
                                            
                                            selectedCell = cell
                                            
                                            // If this is the top talent cell, make sure we preserve the reference
                                            if (isTopTalentCell) {
                                                // Keep the top talent reference
                                                topTalentHighlightedCell = currentTopTalent
                                            }
                                            
                                            // Use inter-schedule swap validation for better functionality
                                            highlightedCells = findValidSwapCellsAcrossSchedules(cell, scheduleData, subjectAssignments)
                                        }
                                    },
                                    onCellClick = { cell ->
                                        // Fixed indentation and braces to ensure proper flow
                                        if (selectedCell != null && highlightedCells.contains(Triple(cell.dayIndex, cell.slotIndex, cell.scheduleName))) {
                                            // Perform swap (supports both intra and inter-schedule)
                                            isSwapping = true
                                            
                                            // Store the current top talent info to check if it's being swapped
                                            val currentTopTalent = topTalentHighlightedCell
                                            val isTopTalentInvolved = currentTopTalent != null && 
                                                ((currentTopTalent.volunteerName == selectedCell!!.volunteerName && 
                                                  currentTopTalent.volunteerRollNo == selectedCell!!.volunteerRollNo) ||
                                                 (currentTopTalent.volunteerName == cell.volunteerName && 
                                                  currentTopTalent.volunteerRollNo == cell.volunteerRollNo))
                                            
                                            // Check for self-swap (same cell)
                                            val isSelfSwap = selectedCell!!.dayIndex == cell.dayIndex && 
                                                            selectedCell!!.slotIndex == cell.slotIndex && 
                                                            selectedCell!!.scheduleName == cell.scheduleName
                                            
                                            // Immediately clear the selection and highlighting before the swap operation
                                            val tempSelectedCell = selectedCell
                                            selectedCell = null
                                            highlightedCells = emptySet() // Explicitly clear highlighted cells
                                            
                                            // For self-swap, preserve the top talent highlight
                                            if (isSelfSwap && isTopTalentInvolved) {
                                                // No need to perform actual swap, just show a message
                                                isSwapping = false
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("No changes needed - same cell selected")
                                                }
                                            } else {
                                                coroutineScope.launch {
                                                    val success = performLocalVolunteerSwap(tempSelectedCell!!, cell, scheduleData, localVolunteerSwaps)
                                                    
                                                    // Always find and highlight the top talent immediately after swap
                                                    val nextTopTalent = findTopTalentWithoutSubject(scheduleData, subjectAssignments, localVolunteerSwaps)
                                                    
                                                    // Always update the top talent highlight, even if null
                                                    topTalentHighlightedCell = nextTopTalent
                                                    
                                                    isSwapping = false
                                                    if (success) {
                                                        val swapType = if (tempSelectedCell.scheduleName == cell.scheduleName) "intra-schedule" else "inter-schedule"
                                                        snackbarHostState.showSnackbar("Volunteers swapped successfully ($swapType)")
                                                        
                                                        // Always show the top talent snackbar after a successful swap
                                                        if (nextTopTalent != null) {
                                                            showTopTalentSnackbar = true
                                                        }
                                                    } else {
                                                        snackbarHostState.showSnackbar("Failed to swap volunteers")
                                                    }
                                                }
                                            }
                                        } else {
                                            // Make sure we're getting the most current data
                                            val updatedCell = findCurrentCellData(cell, scheduleData, localVolunteerSwaps) ?: cell
                                            
                                            // Handle regular cell click (no swapping)
                                            if (updatedCell.volunteerName.isNotEmpty()) {
                                                // Show subject assignment dialog for all volunteers, whether they have a subject assigned or not
                                                subjectAssignmentCell = updatedCell
                                                showSubjectAssignmentDialog = true
                                            } else {
                                                // Show details dialog for empty cells
                                                detailsCell = updatedCell
                                                showDetailsDialog = true
                                            }
                                            selectedCell = null
                                            highlightedCells = emptySet()
                                            
                                            // Make sure top talent stays highlighted
                                            if (topTalentHighlightedCell == null) {
                                                val nextTopTalent = findTopTalentWithoutSubject(scheduleData, subjectAssignments, localVolunteerSwaps)
                                                if (nextTopTalent != null) {
                                                    topTalentHighlightedCell = nextTopTalent
                                                }
                                            }
                                        }
                                    },
                                    localVolunteerSwaps = localVolunteerSwaps[schedule.referenceData.schoolName] ?: emptyMap(),
                                    remainingSubjectCounts = remainingSubjectCounts[schedule.referenceData.schoolName] ?: emptyMap()
                                )
                            }
                        }
                    }
                } else if (!isLoading) {
                    // Empty state with debug info
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No schedule data available",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Schedule IDs: $scheduleIdList",
                                color = Color(0xFFB0B0B0),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Pairings: $pairings",
                                color = Color(0xFFB0B0B0),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // SnackbarHost for user feedback
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Loading overlay for swapping
        if (isSwapping || isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = YellowAccent)
                    Text(
                        text = if (isSwapping) "Swapping volunteers..." else "Saving subject assignments...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    // Volunteer details dialog
    if (showDetailsDialog && detailsCell != null) {
        VolunteerDetailsDialog(
            cell = detailsCell!!,
            scheduleData = scheduleData.firstOrNull(),
            localVolunteerSwaps = localVolunteerSwaps, // Add this parameter
            onDismiss = { showDetailsDialog = false }
        )
    }

    // Subject assignment dialog
    if (showSubjectAssignmentDialog && subjectAssignmentCell != null) {
        val scheduleForCell = scheduleData.find { schedule ->
            schedule.referenceData.schoolName == subjectAssignmentCell!!.scheduleName
        }
        if (scheduleForCell != null) {
            SubjectAssignmentDialog(
                cell = subjectAssignmentCell!!,
                subjectPreset = scheduleForCell.subjectPreset,
                scheduleData = scheduleForCell,
                remainingSubjectCounts = remainingSubjectCounts[scheduleForCell.referenceData.schoolName] ?: mutableMapOf(),
                localVolunteerSwaps = localVolunteerSwaps, // Add this parameter
                onDismiss = { showSubjectAssignmentDialog = false },
                onSubjectAssigned = { cell, subjectCode ->
                    // First check if this subject is already assigned in the same day
                    val isDuplicate = checkForDuplicateSubjectOnSameDay(
                        cell.scheduleName,
                        cell.dayIndex,
                        subjectCode,
                        subjectAssignments
                    )
                    
                    if (isDuplicate) {
                        // Show warning dialog
                        showDuplicateSubjectWarning = true
                        duplicateSubjectInfo = Triple(cell, subjectCode, false)
                        // Don't close the assignment dialog yet
                    } else {
                        // Proceed with assignment
                        assignSubjectToCell(cell, subjectCode)
                    }
                }
            )
        }
    }
    
    // Duplicate subject warning dialog
    if (showDuplicateSubjectWarning && duplicateSubjectInfo != null) {
        val (cell, subjectCode, forceProceed) = duplicateSubjectInfo!!
        
        AlertDialog(
            onDismissRequest = {
                showDuplicateSubjectWarning = false
                duplicateSubjectInfo = null
            },
            title = {
                Text(
                    text = "Warning: Duplicate Subject",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Subject '$subjectCode' is already assigned to another volunteer on the same day. Do you want to proceed with this assignment?",
                    color = Color.White
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Force proceed with assignment
                        assignSubjectToCell(cell, subjectCode)
                        showDuplicateSubjectWarning = false
                        duplicateSubjectInfo = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Proceed Anyway")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDuplicateSubjectWarning = false
                        duplicateSubjectInfo = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = YellowAccent)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1E1E1E),
            textContentColor = Color.White
        )
    }
}

@Composable
fun ScheduleGridCard(
    scheduleData: ScheduleGridData,
    selectedCell: GridCell?,
    highlightedCells: Set<Triple<Int, Int, String>>,
    topTalentHighlightedCell: GridCell?,
    subjectAssignments: Map<Pair<Int, Int>, String>,
    onCellLongPress: (GridCell) -> Unit,
    onCellClick: (GridCell) -> Unit,
    localVolunteerSwaps: Map<Pair<Int, Int>, GridCell> = emptyMap(),
    remainingSubjectCounts: Map<String, Int> = emptyMap()
) {
    var showSubjectInfoDialog by remember { mutableStateOf(false) }
    
    // Log the current subject assignments for debugging
    LaunchedEffect(subjectAssignments) {
        Log.d("ScheduleGridCard", "Rendering with ${subjectAssignments.size} subject assignments for ${scheduleData.referenceData.schoolName}")
        subjectAssignments.forEach { (key, subject) ->
            val (dayIndex, slotIndex) = key
            Log.d("ScheduleGridCard", "Assignment at ($dayIndex,$slotIndex): $subject")
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF121212) // Darker background to match the picture
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Schedule name header with info button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${scheduleData.referenceData.schoolName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = YellowAccent,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(
                    onClick = { showSubjectInfoDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Show subject information",
                        tint = YellowAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Grid table
            ScheduleTable(
                scheduleData = scheduleData,
                selectedCell = selectedCell,
                highlightedCells = highlightedCells,
                topTalentHighlightedCell = topTalentHighlightedCell,
                subjectAssignments = subjectAssignments,
                localVolunteerSwaps = localVolunteerSwaps,
                onCellLongPress = { cell ->
                    // Check if there's an in-memory subject assignment before allowing long press
                    val cellKey = cell.dayIndex to cell.slotIndex
                    val inMemorySubject = subjectAssignments[cellKey]

                    val effectiveCell = if (inMemorySubject != null) {
                        // Create a new cell with the in-memory subject assignment
                        Log.d("ScheduleGridCard", "Cell has in-memory subject: $inMemorySubject")
                        cell.copy(assignedSubject = inMemorySubject)
                    } else {
                        cell
                    }

                    // Check if this is the top talent
                    val currentTopTalent = topTalentHighlightedCell
                    val isTopTalent = currentTopTalent != null &&
                                    effectiveCell.volunteerName == currentTopTalent.volunteerName &&
                                    effectiveCell.volunteerRollNo == currentTopTalent.volunteerRollNo

                    // Allow long press if the effective cell has no subject OR if it's the top talent
                    if (effectiveCell.assignedSubject == null || isTopTalent) {
                        Log.d("ScheduleGridCard", "Long press allowed on ${effectiveCell.volunteerName} (isTopTalent: $isTopTalent)")
                        onCellLongPress(effectiveCell)
                    } else {
                        Log.d("ScheduleGridCard", "Long press denied - subject assigned: ${effectiveCell.assignedSubject}")
                    }
                },
                onCellClick = { cell ->
                    // Apply any in-memory subject assignments before handling the click
                    val cellKey = cell.dayIndex to cell.slotIndex
                    val inMemorySubject = subjectAssignments[cellKey]
                    
                    val effectiveCell = if (inMemorySubject != null) {
                        // Create a new cell with the in-memory subject assignment
                        Log.d("ScheduleGridCard", "Cell has in-memory subject: $inMemorySubject")
                        cell.copy(assignedSubject = inMemorySubject)
                    } else {
                        cell
                    }
                    
                    onCellClick(effectiveCell)
                }
            )
        }
    }
    
    // Subject info dialog
    if (showSubjectInfoDialog) {
        SubjectInfoDialog(
            schoolName = scheduleData.referenceData.schoolName,
            subjectPreset = scheduleData.subjectPreset,
            remainingSubjectCounts = remainingSubjectCounts,
            onDismiss = { showSubjectInfoDialog = false }
        )
    }
}

@Composable
fun ScheduleTable(
    scheduleData: ScheduleGridData,
    selectedCell: GridCell?,
    highlightedCells: Set<Triple<Int, Int, String>>,
    topTalentHighlightedCell: GridCell?,
    subjectAssignments: Map<Pair<Int, Int>, String>,
    localVolunteerSwaps: Map<Pair<Int, Int>, GridCell> = emptyMap(),
    onCellLongPress: (GridCell) -> Unit,
    onCellClick: (GridCell) -> Unit
) {
    val referenceData = scheduleData.referenceData
    val assignments = scheduleData.volunteerAssignments
    val scheduleName = scheduleData.referenceData.schoolName
    
    // Shared scroll state for synchronized scrolling
    val tableScrollState = rememberScrollState()
    
    // Create grid data structure
    val gridCells = mutableMapOf<Pair<Int, Int>, GridCell>()
    Log.d("SubjectAssignmentGrid", "Creating grid with ${assignments.size} assignments")

    // Process assignments and create grid cells
    assignments.forEach { assignment ->
        val key = assignment.dayIndex to assignment.slotIndex
        
        // Check if there's a local swap for this cell
        val swappedCell = localVolunteerSwaps[key]
        
        // Use in-memory subject assignments if available, otherwise use the one from the original data
        val cellSubjectCode = subjectAssignments[key] ?: assignment.assignedSubject
        
        // Log subject assignment for debugging
        Log.d("SubjectAssignmentGrid", "Creating cell at ($key) for ${if (swappedCell != null) swappedCell.volunteerName else assignment.volunteerName}, Subject: $cellSubjectCode")
        
        // Check if this cell is highlighted for swapping - use Triple with scheduleName
        val isHighlightedForSwap = highlightedCells.contains(Triple(assignment.dayIndex, assignment.slotIndex, scheduleName))
        
        if (swappedCell != null) {
            // If we have a swapped volunteer, use that data instead
            gridCells[key] = GridCell(
                dayIndex = assignment.dayIndex,
                slotIndex = assignment.slotIndex,
                volunteerName = swappedCell.volunteerName,
                volunteerRollNo = swappedCell.volunteerRollNo,
                volunteerGroup = swappedCell.volunteerGroup,
                assignedSubject = cellSubjectCode,
                isHighlighted = isHighlightedForSwap,
                scheduleId = "", // Will be set when we have access to schedule ID
                scheduleName = scheduleName,
                // Include enhanced volunteer data from swappedCell
                interviewScore = swappedCell.interviewScore,
                subjectPreference1 = swappedCell.subjectPreference1,
                subjectPreference2 = swappedCell.subjectPreference2,
                subjectPreference3 = swappedCell.subjectPreference3
            )
        } else {
            // Normal cell without swap
            gridCells[key] = GridCell(
                dayIndex = assignment.dayIndex,
                slotIndex = assignment.slotIndex,
                volunteerName = assignment.volunteerName,
                volunteerRollNo = assignment.volunteerRollNo,
                volunteerGroup = assignment.volunteerGroup,
                assignedSubject = cellSubjectCode,
                isHighlighted = isHighlightedForSwap,
                scheduleId = "", // Will be set when we have access to schedule ID
                scheduleName = scheduleName,
                // Include enhanced volunteer data from generateSchedule collection
                interviewScore = assignment.interviewScore,
                subjectPreference1 = assignment.subjectPreference1,
                subjectPreference2 = assignment.subjectPreference2,
                subjectPreference3 = assignment.subjectPreference3
            )
        }
    }

    Log.d("SubjectAssignmentGrid", "Grid created with ${gridCells.size} cells")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF121212))
    ) {
        // Header row with time slots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF121212))
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Empty cell for day column - fixed width
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Day/\nTime",
                    color = YellowAccent,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
            }
            
            // Time slot headers - scrollable
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(tableScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Time slot headers
                referenceData.timeSlotNames.forEach { timeSlot ->
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = timeSlot,
                            color = YellowAccent,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Day rows with 8dp spacing between rows
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            referenceData.dayNames.forEachIndexed { dayIndex, dayName ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Day name cell - fixed width
                    Box(
                        modifier = Modifier
                            .width(50.dp)
                            .height(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    
                    // Time slot cells - scrollable with the same shared scroll state
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(tableScrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Time slot cells
                        referenceData.timeSlotNames.forEachIndexed { slotIndex, _ ->
                            val cellKey = dayIndex to slotIndex
                            val cell = gridCells[cellKey]
                            
                            if (cell != null) {
                                // Check if this cell contains the top talent volunteer by comparing name and roll number
                                val currentTopTalent = topTalentHighlightedCell
                                val isTopTalent = currentTopTalent != null &&
                                                cell.volunteerName == currentTopTalent.volunteerName &&
                                                cell.volunteerRollNo == currentTopTalent.volunteerRollNo
                                
                                // Check if this is the currently selected cell (including schedule name check)
                                val isThisSelectedCell = selectedCell != null &&
                                                        selectedCell.dayIndex == dayIndex &&
                                                        selectedCell.slotIndex == slotIndex &&
                                                        selectedCell.scheduleName == scheduleName
                                                
                                VolunteerCell(
                                    cell = cell,
                                    isSelected = isThisSelectedCell,
                                    isTopTalent = isTopTalent,
                                    onLongPress = { onCellLongPress(cell) },
                                    onClick = { onCellClick(cell) },
                                    modifier = Modifier.width(100.dp)
                                )
                            } else {
                                // Empty cell - clickable for details
                                Box(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(60.dp)
                                        .background(Color(0xFF232323), RoundedCornerShape(8.dp))
                                        .clip(RoundedCornerShape(8.dp))
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onTap = {
                                                    // Create empty cell for details
                                                    val emptyCell = GridCell(
                                                        dayIndex = dayIndex,
                                                        slotIndex = slotIndex,
                                                        volunteerName = "",
                                                        volunteerRollNo = "",
                                                        volunteerGroup = "",
                                                        assignedSubject = null, // Added assignedSubject
                                                        scheduleId = "",
                                                        scheduleName = scheduleName,
                                                        interviewScore = 0,
                                                        subjectPreference1 = "",
                                                        subjectPreference2 = "",
                                                        subjectPreference3 = ""
                                                    )
                                                    onCellClick(emptyCell)
                                                }
                                            )
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VolunteerCell(
    cell: GridCell,
    isSelected: Boolean,
    isTopTalent: Boolean = false,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Log cell data for debugging when it's a top talent
    LaunchedEffect(isTopTalent) {
        if (isTopTalent) {
            Log.d("VolunteerCell", "Rendering top talent cell: ${cell.volunteerName} (${cell.dayIndex},${cell.slotIndex}) with score: ${cell.interviewScore}")
        }
    }

    val hasSubject = cell.assignedSubject != null

    val containerColor = when {
        isTopTalent -> Color(0xFF4CAF50)
        cell.isHighlighted -> Color(0xFF64B5F6).copy(alpha = 0.8f) // Light blue for highlighted cells
        isSelected -> YellowAccent.copy(alpha = 0.4f)
        hasSubject -> YellowAccent // Yellow only for cells with subjects assigned
        else -> Color(0xFF232323) // Dark gray for cells without subjects
    }
    
    // Determine text color based on background
    val (textColor, subTextColor) = when {
        isTopTalent -> Color.Black to Color.Black.copy(alpha = 0.8f)
        cell.isHighlighted || isSelected -> Color.Black to Color.Black.copy(alpha = 0.7f)
        hasSubject -> Color.Black to Color.Black.copy(alpha = 0.7f)
        else -> Color.White to Color(0xFFB0B0B0)
    }

    // Extract last 4 digits of the roll number
    val last4Digits = if (cell.volunteerRollNo.length > 4) {
        cell.volunteerRollNo.takeLast(4)
    } else {
        cell.volunteerRollNo
    }

    Card(
        modifier = modifier
            .height(60.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        // Allow long press if no subject is assigned OR if it's the top talent
                        if (!hasSubject || isTopTalent) {
                            onLongPress()
                        }
                    },
                    onTap = { onClick() }
                )
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        // Remove border to match the picture
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // First name only
            Text(
                text = cell.volunteerName.split(" ").firstOrNull() ?: "",
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            
            // Last 4 digits of roll number
            if (hasSubject) {
                // If subject is assigned, show roll + subject code in brackets
                Text(
                    text = "$last4Digits (${cell.assignedSubject})",
                    color = subTextColor,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            } else if (isTopTalent) {
                // If top talent, show roll + score in brackets
                Text(
                    text = "$last4Digits (${cell.interviewScore})",
                    color = subTextColor,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            } else {
                // Otherwise just show roll number
                Text(
                    text = last4Digits,
                    color = subTextColor,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Helper functions
private suspend fun loadScheduleData(
    scheduleIds: List<String>,
    pairings: Map<String, String>,
    onResult: (List<ScheduleGridData>) -> Unit
) {
    try {
        Log.d("SubjectAssignmentGrid", "Loading schedule data for IDs: $scheduleIds")
        Log.d("SubjectAssignmentGrid", "Pairings: $pairings")

        val db = FirebaseFirestore.getInstance()
        val scheduleDataList = mutableListOf<ScheduleGridData>()

        for (scheduleId in scheduleIds) {
            val subjectPresetId = pairings[scheduleId] ?: continue
            Log.d("SubjectAssignmentGrid", "Processing schedule: $scheduleId with subject preset: $subjectPresetId")

            // Load schedule data
            val scheduleDoc = db.collection(FirestoreCollection.GENERATED_SCHEDULES)
                .document(scheduleId)
                .get()
                .await()

            // Load subject preset data
            val subjectPresetDoc = db.collection(FirestoreCollection.SUBJECT_PRESETS)
                .document(subjectPresetId)
                .get()
                .await()

            Log.d("SubjectAssignmentGrid", "Schedule doc exists: ${scheduleDoc.exists()}")
            Log.d("SubjectAssignmentGrid", "Subject preset doc exists: ${subjectPresetDoc.exists()}")

            if (scheduleDoc.exists() && subjectPresetDoc.exists()) {
                Log.d("SubjectAssignmentGrid", "Schedule document data keys: ${scheduleDoc.data?.keys}")
                val scheduleData = parseScheduleDocument(scheduleDoc.data!!)
                val subjectPreset = parseSubjectPresetDocument(subjectPresetDoc.data!!)

                Log.d("SubjectAssignmentGrid", "Parsed ${scheduleData.second.size} volunteer assignments")
                Log.d("SubjectAssignmentGrid", "Reference data: days=${scheduleData.first.dayNames}, slots=${scheduleData.first.timeSlotNames}")

                scheduleDataList.add(
                    ScheduleGridData(
                        referenceData = scheduleData.first,
                        volunteerAssignments = scheduleData.second,
                        groupAvailability = scheduleData.third,
                        subjectPreset = subjectPreset
                    )
                )
            }
        }

        Log.d("SubjectAssignmentGrid", "Loaded ${scheduleDataList.size} schedule data items")
        onResult(scheduleDataList)
    } catch (e: Exception) {
        Log.e("SubjectAssignmentGrid", "Error loading schedule data", e)
        onResult(emptyList())
    }
}

private fun parseScheduleDocument(data: Map<String, Any>): Triple<ScheduleReferenceData, List<OptimizedVolunteerAssignment>, List<GroupAvailability>> {
    Log.d("SubjectAssignmentGrid", "Parsing schedule document with keys: ${data.keys}")

    val referenceData = (data["referenceData"] as? Map<String, Any>)?.let { ref ->
        Log.d("SubjectAssignmentGrid", "Reference data keys: ${ref.keys}")
        ScheduleReferenceData(
            dayNames = (ref["dayNames"] as? List<String>) ?: emptyList(),
            timeSlotNames = (ref["timeSlotNames"] as? List<String>) ?: emptyList(),
            schoolName = data["name"] as? String ?: "",
            totalDays = (ref["totalDays"] as? Number)?.toInt() ?: 0,
            totalSlots = (ref["totalSlots"] as? Number)?.toInt() ?: 0
        )
    } ?: ScheduleReferenceData()

    // Try both data structures - optimizedAssignments (flat) and optimizedSlotAssignments (grouped)
    val assignments = mutableListOf<OptimizedVolunteerAssignment>()

    // First try the flat structure (optimizedAssignments)
    val flatAssignments = data["optimizedAssignments"] as? List<Map<String, Any>>
    Log.d("SubjectAssignmentGrid", "Flat assignments found: ${flatAssignments != null}, size: ${flatAssignments?.size}")

    if (flatAssignments != null) {
        assignments.addAll(flatAssignments.map { assignment ->
            Log.d("SubjectAssignmentGrid", "Processing assignment: ${assignment.keys}")
            OptimizedVolunteerAssignment(
                volunteerName = assignment["volunteerName"] as? String ?: "",
                volunteerRollNo = assignment["volunteerRollNo"] as? String ?: assignment["volunteerRollNumber"] as? String ?: "",
                volunteerGroup = assignment["volunteerGroup"] as? String ?: "",
                dayIndex = (assignment["dayIndex"] as? Number)?.toInt() ?: 0,
                slotIndex = (assignment["slotIndex"] as? Number)?.toInt() ?: 0,
                interviewScore = (assignment["interviewScore"] as? Number)?.toInt() ?: 0,
                subjectPreference1 = assignment["subjectPreference1"] as? String ?: "",
                subjectPreference2 = assignment["subjectPreference2"] as? String ?: "",
                subjectPreference3 = assignment["subjectPreference3"] as? String ?: "",
                assignedSubject = assignment["assignedSubject"] as? String
            )
        })
    } else {
        // Fallback to grouped structure (optimizedSlotAssignments)
        val groupedAssignments = data["optimizedSlotAssignments"] as? List<Map<String, Any>>
        Log.d("SubjectAssignmentGrid", "Grouped assignments found: ${groupedAssignments != null}, size: ${groupedAssignments?.size}")

        groupedAssignments?.forEach { slotGroup ->
            val dayIndex = (slotGroup["dayIndex"] as? Number)?.toInt() ?: 0
            val slotIndex = (slotGroup["slotIndex"] as? Number)?.toInt() ?: 0
            val slotAssignments = slotGroup["assignments"] as? List<Map<String, Any>> ?: emptyList()

            slotAssignments.forEach { assignment ->
                assignments.add(
                    OptimizedVolunteerAssignment(
                        volunteerName = assignment["volunteerName"] as? String ?: "",
                        volunteerRollNo = assignment["volunteerRollNo"] as? String ?: assignment["volunteerRollNumber"] as? String ?: "",
                        volunteerGroup = assignment["volunteerGroup"] as? String ?: "",
                        dayIndex = dayIndex,
                        slotIndex = slotIndex,
                        interviewScore = (assignment["interviewScore"] as? Number)?.toInt() ?: 0,
                        subjectPreference1 = assignment["subjectPreference1"] as? String ?: "",
                        subjectPreference2 = assignment["subjectPreference2"] as? String ?: "",
                        subjectPreference3 = assignment["subjectPreference3"] as? String ?: "",
                        assignedSubject = assignment["assignedSubject"] as? String
                    )
                )
            }
        }
    }

    Log.d("SubjectAssignmentGrid", "Total assignments parsed: ${assignments.size}")

    val groupAvailability = (data["groupAvailability"] as? List<Map<String, Any>>)?.map { availability ->
        GroupAvailability(
            dayIndex = (availability["dayIndex"] as? Number)?.toInt() ?: 0,
            slotIndex = (availability["slotIndex"] as? Number)?.toInt() ?: 0,
            availableGroups = (availability["availableGroups"] as? List<String>) ?: emptyList()
        )
    } ?: emptyList()

    return Triple(referenceData, assignments, groupAvailability)
}

private fun parseSubjectPresetDocument(data: Map<String, Any>): SubjectPreset {
    return SubjectPreset(
        id = "",
        name = data["name"] as? String ?: "",
        subjects = (data["subjects"] as? Map<String, Any>)?.mapValues {
            (it.value as? Number)?.toInt() ?: 0
        } ?: emptyMap()
    )
}

private fun getSubjectForCell(assignment: OptimizedVolunteerAssignment, subjectPreset: SubjectPreset): String? {
    // Return the assigned subject code if it exists in the assignment
    return assignment.assignedSubject
}

// Helper function to find valid swap cells across all schedules
private fun findValidSwapCellsAcrossSchedules(
    selectedCell: GridCell, 
    allScheduleData: List<ScheduleGridData>,
    allSubjectAssignments: MutableMap<String, MutableMap<Pair<Int, Int>, String>>
): Set<Triple<Int, Int, String>> {
    val validCells = mutableSetOf<Triple<Int, Int, String>>()

    // Find the schedule containing the selected cell
    val selectedSchedule = allScheduleData.find { schedule ->
        schedule.referenceData.schoolName == selectedCell.scheduleName
    } ?: return validCells

    // Immediate check for subject on selected cell
    if (selectedCell.assignedSubject != null) {
        Log.d("SubjectAssignmentGrid", "Selected cell has subject assigned, can't swap: ${selectedCell.assignedSubject}")
        return validCells
    }

    // Check for in-memory subject assignment on selected cell
    val selectedCellKey = selectedCell.dayIndex to selectedCell.slotIndex
    val selectedScheduleName = selectedCell.scheduleName
    val inMemorySelectedCellSubject = allSubjectAssignments[selectedScheduleName]?.get(selectedCellKey)
    
    if (inMemorySelectedCellSubject != null) {
        Log.d("SubjectAssignmentGrid", "Selected cell has in-memory subject assigned, can't swap: $inMemorySelectedCellSubject")
        return validCells
    }

    // Find the selected volunteer's available groups
    val selectedVolunteerAvailability = getVolunteerAvailableGroups(selectedCell, selectedSchedule)

    // Check cells in all schedules for swap validity
    allScheduleData.forEach { scheduleData ->
        val scheduleName = scheduleData.referenceData.schoolName
        Log.d("SubjectAssignmentGrid", "Checking cells in schedule: $scheduleName")
        
        scheduleData.volunteerAssignments.forEach { assignment ->
            val cellKey = assignment.dayIndex to assignment.slotIndex
            
            // Get the in-memory subject assignments for this schedule
            val inMemoryAssignments = allSubjectAssignments[scheduleName]
            val inMemorySubjectCode = inMemoryAssignments?.get(cellKey)
            
            // Consider a cell to have a subject if either the database or in-memory assignment has it
            val hasSubjectAssigned = assignment.assignedSubject != null || inMemorySubjectCode != null
            
            if (hasSubjectAssigned) {
                Log.d("SubjectAssignmentGrid", "Cell at $cellKey has subject: ${assignment.assignedSubject ?: inMemorySubjectCode}, skipping")
                return@forEach  // Skip this cell entirely if it has a subject assigned
            }
            
            // Skip the selected cell itself and cells with assigned subjects
            if (!(assignment.dayIndex == selectedCell.dayIndex &&
                  assignment.slotIndex == selectedCell.slotIndex &&
                  scheduleName == selectedCell.scheduleName)) {

                val otherCell = GridCell(
                    dayIndex = assignment.dayIndex,
                    slotIndex = assignment.slotIndex,
                    volunteerName = assignment.volunteerName,
                    volunteerRollNo = assignment.volunteerRollNo,
                    volunteerGroup = assignment.volunteerGroup,
                    scheduleId = "",
                    scheduleName = scheduleName
                )

                val otherVolunteerAvailability = getVolunteerAvailableGroups(otherCell, scheduleData)

                // Check if swap is valid (cross-schedule validation)
                if (canSwapVolunteersAcrossSchedules(selectedCell, otherCell, selectedVolunteerAvailability, otherVolunteerAvailability, selectedSchedule, scheduleData)) {
                    // Include scheduleName in the highlighted cells set
                    validCells.add(Triple(assignment.dayIndex, assignment.slotIndex, scheduleName))
                    Log.d("SubjectAssignmentGrid", "Adding valid swap cell: $cellKey in schedule $scheduleName for ${assignment.volunteerName}")
                }
            }
        }
    }

    Log.d("SubjectAssignmentGrid", "Found ${validCells.size} valid swap targets")
    return validCells
}

// Add back the helper functions that were removed
private fun getVolunteerAvailableGroups(cell: GridCell, scheduleData: ScheduleGridData): List<String> {
    // This would typically come from volunteer availability data
    // For now, return the volunteer's own group as available
    return listOf(cell.volunteerGroup)
}

// Add back the helper function for cross-schedule volunteer swapping validation
private fun canSwapVolunteersAcrossSchedules(
    cell1: GridCell,
    cell2: GridCell,
    cell1Availability: List<String>,
    cell2Availability: List<String>,
    schedule1Data: ScheduleGridData,
    schedule2Data: ScheduleGridData
): Boolean {
    // Get the groups required for each cell's time slot in their respective schedules
    val cell1RequiredGroups = schedule1Data.groupAvailability
        .find { it.dayIndex == cell1.dayIndex && it.slotIndex == cell1.slotIndex }
        ?.availableGroups ?: emptyList()

    val cell2RequiredGroups = schedule2Data.groupAvailability
        .find { it.dayIndex == cell2.dayIndex && it.slotIndex == cell2.slotIndex }
        ?.availableGroups ?: emptyList()

    // Check if cell1's volunteer can work in cell2's slot and vice versa
    val cell1VolunteerCanWorkInCell2 = cell1Availability.any { it in cell2RequiredGroups }
    val cell2VolunteerCanWorkInCell1 = cell2Availability.any { it in cell1RequiredGroups }

    return cell1VolunteerCanWorkInCell2 && cell2VolunteerCanWorkInCell1
}

// Modified performVolunteerSwapAcrossSchedules to ensure UI shows correct volunteer info after swap
private suspend fun performLocalVolunteerSwap(
    selectedCell: GridCell,
    targetCell: GridCell,
    allScheduleData: List<ScheduleGridData>,
    localVolunteerSwaps: MutableMap<String, MutableMap<Pair<Int, Int>, GridCell>>
): Boolean {
    try {
        Log.d("SubjectAssignmentGrid", "Performing local swap between ${selectedCell.scheduleName}(${selectedCell.dayIndex},${selectedCell.slotIndex}) and ${targetCell.scheduleName}(${targetCell.dayIndex},${targetCell.slotIndex})")

        // Do an immediate check for subjects before proceeding
        if (selectedCell.assignedSubject != null || targetCell.assignedSubject != null) {
            Log.e("SubjectAssignmentGrid", "Cannot swap cells with assigned subjects (early check)")
            return false
        }
        
        // Initialize maps for schedules if they don't exist
        if (!localVolunteerSwaps.containsKey(selectedCell.scheduleName)) {
            localVolunteerSwaps[selectedCell.scheduleName] = mutableMapOf()
        }
        
        if (!localVolunteerSwaps.containsKey(targetCell.scheduleName)) {
            localVolunteerSwaps[targetCell.scheduleName] = mutableMapOf()
        }
        
        // Get the selected and target schedule swap maps
        val selectedScheduleSwaps = localVolunteerSwaps[selectedCell.scheduleName]!!
        val targetScheduleSwaps = localVolunteerSwaps[targetCell.scheduleName]!!
        
        // Get the original data for both cells, checking existing swaps first
        val selectedCellKey = selectedCell.dayIndex to selectedCell.slotIndex
        val targetCellKey = targetCell.dayIndex to targetCell.slotIndex
        
        // Get the current volunteer in the selected cell (considering previous swaps)
        val currentSelectedVolunteer = selectedScheduleSwaps[selectedCellKey]?.let { swappedVolunteer ->
            // There's already a swap for this cell, use the swapped volunteer data
            swappedVolunteer
        } ?: run {
            // No swap yet, get original volunteer from schedule data
            val originalVolunteer = allScheduleData
                .find { it.referenceData.schoolName == selectedCell.scheduleName }
                ?.volunteerAssignments
                ?.find { it.dayIndex == selectedCell.dayIndex && it.slotIndex == selectedCell.slotIndex }
                
            if (originalVolunteer == null) {
                Log.e("SubjectAssignmentGrid", "Could not find original volunteer data for selected cell")
                return false
            }
            
            // Create a GridCell from the original volunteer
            GridCell(
                dayIndex = selectedCell.dayIndex,
                slotIndex = selectedCell.slotIndex,
                volunteerName = originalVolunteer.volunteerName,
                volunteerRollNo = originalVolunteer.volunteerRollNo,
                volunteerGroup = originalVolunteer.volunteerGroup,
                assignedSubject = null,
                scheduleId = "",
                scheduleName = selectedCell.scheduleName,
                interviewScore = originalVolunteer.interviewScore,
                subjectPreference1 = originalVolunteer.subjectPreference1,
                subjectPreference2 = originalVolunteer.subjectPreference2,
                subjectPreference3 = originalVolunteer.subjectPreference3
            )
        }
        
        // Get the current volunteer in the target cell (considering previous swaps)
        val currentTargetVolunteer = targetScheduleSwaps[targetCellKey]?.let { swappedVolunteer ->
            // There's already a swap for this cell, use the swapped volunteer data
            swappedVolunteer
        } ?: run {
            // No swap yet, get original volunteer from schedule data
            val originalVolunteer = allScheduleData
                .find { it.referenceData.schoolName == targetCell.scheduleName }
                ?.volunteerAssignments
                ?.find { it.dayIndex == targetCell.dayIndex && it.slotIndex == targetCell.slotIndex }
                
            if (originalVolunteer == null) {
                Log.e("SubjectAssignmentGrid", "Could not find original volunteer data for target cell")
                return false
            }
            
            // Create a GridCell from the original volunteer
            GridCell(
                dayIndex = targetCell.dayIndex,
                slotIndex = targetCell.slotIndex,
                volunteerName = originalVolunteer.volunteerName,
                volunteerRollNo = originalVolunteer.volunteerRollNo,
                volunteerGroup = originalVolunteer.volunteerGroup,
                assignedSubject = null,
                scheduleId = "",
                scheduleName = targetCell.scheduleName,
                interviewScore = originalVolunteer.interviewScore,
                subjectPreference1 = originalVolunteer.subjectPreference1,
                subjectPreference2 = originalVolunteer.subjectPreference2,
                subjectPreference3 = originalVolunteer.subjectPreference3
            )
        }
        
        // Now swap the volunteers
        // For the selected cell position, store the target volunteer with selected cell's coordinates
        selectedScheduleSwaps[selectedCellKey] = currentTargetVolunteer.copy(
            dayIndex = selectedCell.dayIndex,
            slotIndex = selectedCell.slotIndex,
            scheduleName = selectedCell.scheduleName
        )
        
        // For the target cell position, store the selected volunteer with target cell's coordinates
        targetScheduleSwaps[targetCellKey] = currentSelectedVolunteer.copy(
            dayIndex = targetCell.dayIndex,
            slotIndex = targetCell.slotIndex,
            scheduleName = targetCell.scheduleName
        )
        
        Log.d("SubjectAssignmentGrid", "Local swap completed successfully")
        return true
    } catch (e: Exception) {
        Log.e("SubjectAssignmentGrid", "Error performing local swap", e)
        return false
    }
}

// Modified save function to handle both subject assignments and volunteer swaps
private suspend fun saveSubjectAssignments(
    scheduleData: List<ScheduleGridData>,
    subjectAssignments: Map<String, Map<Pair<Int, Int>, String>>,
    localVolunteerSwaps: Map<String, Map<Pair<Int, Int>, GridCell>> = emptyMap()
): Boolean {
    return try {
        val db = FirebaseFirestore.getInstance()

        // Create a document for each schedule
        for ((scheduleName, assignments) in subjectAssignments) {
            if (assignments.isEmpty() && (localVolunteerSwaps[scheduleName]?.isEmpty() != false)) {
                continue // Skip schedules with no assignments and no swaps
            }
            
            // Find the schedule data
            val schedule = scheduleData.find { it.referenceData.schoolName == scheduleName }
                ?: continue
            
            // Convert assignments to a format suitable for Firestore
            val assignmentsList = mutableListOf<Map<String, Any>>()
            
            // Get local swaps for this schedule
            val localSwaps = localVolunteerSwaps[scheduleName] ?: emptyMap()
            
            // Process all assignments (including subject assignments and volunteer swaps)
            val processedCells = mutableSetOf<Pair<Int, Int>>()
            
            // First add all cells with subject assignments
            for ((cellKey, subjectCode) in assignments) {
                val (dayIndex, slotIndex) = cellKey
                processedCells.add(cellKey)
                
                // Find the volunteer assignment - check for swapped volunteer first
                val swappedVolunteer = localSwaps[cellKey]
                val volunteerAssignment = if (swappedVolunteer != null) {
                    // Use the swapped volunteer data
                OptimizedVolunteerAssignment(
                        volunteerName = swappedVolunteer.volunteerName,
                        volunteerRollNo = swappedVolunteer.volunteerRollNo,
                        volunteerGroup = swappedVolunteer.volunteerGroup,
                        dayIndex = dayIndex,
                        slotIndex = slotIndex,
                        interviewScore = swappedVolunteer.interviewScore,
                        subjectPreference1 = swappedVolunteer.subjectPreference1,
                        subjectPreference2 = swappedVolunteer.subjectPreference2,
                        subjectPreference3 = swappedVolunteer.subjectPreference3,
                        assignedSubject = subjectCode
                    )
                } else {
                    // Use original volunteer
                    schedule.volunteerAssignments.find { 
                        it.dayIndex == dayIndex && it.slotIndex == slotIndex 
                    }?.copy(assignedSubject = subjectCode) ?: continue
                }
                
                assignmentsList.add(
                    mapOf(
                        "dayIndex" to dayIndex,
                        "slotIndex" to slotIndex,
                        "volunteerName" to volunteerAssignment.volunteerName,
                        "volunteerRollNo" to volunteerAssignment.volunteerRollNo,
                        "volunteerGroup" to volunteerAssignment.volunteerGroup,
                        "assignedSubject" to subjectCode
                    )
                )
            }
            
            // Then add any swapped volunteers that don't have subject assignments
            for ((cellKey, swappedVolunteer) in localSwaps) {
                if (cellKey in processedCells) continue // Skip already processed cells
                
                val (dayIndex, slotIndex) = cellKey
                
                assignmentsList.add(
                    mapOf(
                        "dayIndex" to dayIndex,
                        "slotIndex" to slotIndex,
                        "volunteerName" to swappedVolunteer.volunteerName,
                        "volunteerRollNo" to swappedVolunteer.volunteerRollNo,
                        "volunteerGroup" to swappedVolunteer.volunteerGroup,
                        "assignedSubject" to ""  // Changed from null to empty string
                    )
                )
            }
            
            // Create the document data - removed scheduleId and createdAt fields
            val documentData = mapOf(
                "scheduleName" to scheduleName,
                "assignments" to assignmentsList
            )
            
            // Save to the SUBJECT_ASSIGNMENTS collection using schedule name as document ID
            db.collection(FirestoreCollection.SUBJECT_ASSIGNMENTS)
                .document(scheduleName) // Use schedule name as document ID
                .set(documentData)
                .await()

            Log.d("SubjectAssignmentGrid", "Saved ${assignmentsList.size} assignments for $scheduleName")
        }
        
        true
    } catch (e: Exception) {
        Log.e("SubjectAssignmentGrid", "Error saving assignments", e)
        false
    }
}

@Composable
fun VolunteerDetailsDialog(
    cell: GridCell,
    scheduleData: ScheduleGridData?,
    localVolunteerSwaps: Map<String, Map<Pair<Int, Int>, GridCell>> = emptyMap(),
    onDismiss: () -> Unit
) {
    val cellKey = cell.dayIndex to cell.slotIndex
    
    // Check if there's a local swap for this cell, or get the most up-to-date data
    val currentCell = localVolunteerSwaps[cell.scheduleName]?.get(cellKey) ?: scheduleData?.volunteerAssignments?.find {
        it.dayIndex == cell.dayIndex && it.slotIndex == cell.slotIndex
    }?.let { assignment ->
        GridCell(
            dayIndex = assignment.dayIndex,
            slotIndex = assignment.slotIndex,
            volunteerName = assignment.volunteerName,
            volunteerRollNo = assignment.volunteerRollNo,
            volunteerGroup = assignment.volunteerGroup,
            assignedSubject = assignment.assignedSubject,
            isHighlighted = cell.isHighlighted,
            scheduleId = cell.scheduleId,
            scheduleName = cell.scheduleName,
            interviewScore = assignment.interviewScore,
            subjectPreference1 = assignment.subjectPreference1,
            subjectPreference2 = assignment.subjectPreference2,
            subjectPreference3 = assignment.subjectPreference3
        )
    } ?: cell

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false // This ensures dialog can be fullscreen
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Full screen dark background with high opacity
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)) // Pure black with 80% opacity for full dimming
                    .clickable(onClick = onDismiss) // Allow clicking outside to dismiss
            )
            
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF222222))
                    .padding(horizontal = 10.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (currentCell.volunteerName.isNotEmpty()) "Volunteer Details" else "Empty Slot Details",
                    color = YellowAccent,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentCell.volunteerName.isNotEmpty()) {
                        // Assigned volunteer details - all data immediately available from GridCell
                        DetailRow("Volunteer Name", currentCell.volunteerName)
                        DetailRow("Roll Number", currentCell.volunteerRollNo)
                        DetailRow("Group", currentCell.volunteerGroup)

                        // Interview score - immediately available from generateSchedule collection data
                        if (currentCell.interviewScore > 0) {
                            DetailRow("Interview Score", "${currentCell.interviewScore}/100")
                        } else {
                            DetailRow("Interview Score", "Not available")
                        }

                        // Subject preferences section - immediately available from generateSchedule collection data
                        if (currentCell.subjectPreference1.isNotEmpty() ||
                            currentCell.subjectPreference2.isNotEmpty() ||
                            currentCell.subjectPreference3.isNotEmpty()) {

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Subject Preferences:",
                                color = YellowAccent,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            if (currentCell.subjectPreference1.isNotEmpty()) {
                                DetailRow("1st Preference", currentCell.subjectPreference1)
                            }
                            if (currentCell.subjectPreference2.isNotEmpty()) {
                                DetailRow("2nd Preference", currentCell.subjectPreference2)
                            }
                            if (currentCell.subjectPreference3.isNotEmpty()) {
                                DetailRow("3rd Preference", currentCell.subjectPreference3)
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Subject Preferences:",
                                color = YellowAccent,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Not available",
                                color = Color(0xFFB0B0B0),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        // Empty slot details
                        Text(
                            text = "No volunteer assigned to this slot",
                            color = Color(0xFFB0B0B0),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Available groups for this time slot
                    scheduleData?.let { data ->
                        val availableGroups = data.groupAvailability
                            .find { it.dayIndex == currentCell.dayIndex && it.slotIndex == currentCell.slotIndex }
                            ?.availableGroups ?: emptyList()

                        if (availableGroups.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Available Groups:",
                                color = YellowAccent,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = availableGroups.joinToString(", "),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Subject assignment (if any)
                    currentCell.assignedSubject?.let { subject ->
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRow("Assigned Subject", subject)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowAccent,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            color = Color(0xFFB0B0B0),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// Helper function to find current cell data after swaps
private fun findCurrentCellData(cell: GridCell, scheduleData: List<ScheduleGridData>, localVolunteerSwaps: Map<String, Map<Pair<Int, Int>, GridCell>> = emptyMap()): GridCell? {
    val scheduleName = cell.scheduleName
    val cellKey = cell.dayIndex to cell.slotIndex
    
    // First check if there's a local swap for this cell
    val swappedCell = localVolunteerSwaps[scheduleName]?.get(cellKey)
    if (swappedCell != null) {
        Log.d("SubjectAssignmentGrid", "Found swapped cell for ($cellKey) in $scheduleName: ${swappedCell.volunteerName}")
        return swappedCell
    }
    
    // If no swap found, get original data from schedule
    val schedule = scheduleData.find { it.referenceData.schoolName == scheduleName }
    val assignment = schedule?.volunteerAssignments?.find {
        it.dayIndex == cell.dayIndex && it.slotIndex == cell.slotIndex
    } ?: return null
    
    return GridCell(
        dayIndex = assignment.dayIndex,
        slotIndex = assignment.slotIndex,
        volunteerName = assignment.volunteerName,
        volunteerRollNo = assignment.volunteerRollNo,
        volunteerGroup = assignment.volunteerGroup,
        assignedSubject = assignment.assignedSubject,
        isHighlighted = cell.isHighlighted,
        scheduleId = cell.scheduleId,
        scheduleName = cell.scheduleName,
        interviewScore = assignment.interviewScore,
        subjectPreference1 = assignment.subjectPreference1,
        subjectPreference2 = assignment.subjectPreference2,
        subjectPreference3 = assignment.subjectPreference3
    )
}

// Helper function to find top talent without subject assignment
private fun findTopTalentWithoutSubject(
    scheduleData: List<ScheduleGridData>,
    subjectAssignments: Map<String, Map<Pair<Int, Int>, String>>,
    localVolunteerSwaps: Map<String, Map<Pair<Int, Int>, GridCell>> = emptyMap()
): GridCell? {
    var topTalentCell: GridCell? = null
    var highestScore = -1

    Log.d("SubjectAssignmentGrid", "Looking for top talent without subject assignment...")
    
    // First, collect all volunteers without subject assignments
    val candidateVolunteers = mutableListOf<GridCell>()
    
    scheduleData.forEach { schedule ->
        val scheduleName = schedule.referenceData.schoolName
        
        schedule.volunteerAssignments.forEach { assignment ->
            val cellKey = assignment.dayIndex to assignment.slotIndex
            
            // Check if there's a local swap for this cell
            val swappedCell = localVolunteerSwaps[scheduleName]?.get(cellKey)
            
            // Check for subject assignment in both the original data and the in-memory assignments
            val hasSubjectInMemory = subjectAssignments[scheduleName]?.get(cellKey) != null
            val originalSubject = assignment.assignedSubject
            
            if (swappedCell != null) {
                // Use the swapped volunteer data
                val effectiveSubject = if (hasSubjectInMemory) {
                    subjectAssignments[scheduleName]?.get(cellKey)
                } else {
                    originalSubject
                }
                
                // Only add to candidates if no subject is assigned
                if (effectiveSubject == null) {
                    candidateVolunteers.add(swappedCell)
                    Log.d("SubjectAssignmentGrid", "Candidate (swapped): ${swappedCell.volunteerName}, Score: ${swappedCell.interviewScore}")
                }
            } else {
                // Use original volunteer data
                // Only add to candidates if no subject is assigned
                if (originalSubject == null && !hasSubjectInMemory) {
                    val cell = GridCell(
                        dayIndex = assignment.dayIndex,
                        slotIndex = assignment.slotIndex,
                        volunteerName = assignment.volunteerName,
                        volunteerRollNo = assignment.volunteerRollNo,
                        volunteerGroup = assignment.volunteerGroup,
                        assignedSubject = null, // Explicitly set to null since we're checking for unassigned volunteers
                        isHighlighted = false,
                        scheduleId = "",
                        scheduleName = scheduleName,
                        interviewScore = assignment.interviewScore,
                        subjectPreference1 = assignment.subjectPreference1,
                        subjectPreference2 = assignment.subjectPreference2,
                        subjectPreference3 = assignment.subjectPreference3
                    )
                    candidateVolunteers.add(cell)
                    Log.d("SubjectAssignmentGrid", "Candidate (original): ${cell.volunteerName}, Score: ${cell.interviewScore}")
                }
            }
        }
    }
    
    // Now find the volunteer with the highest interview score
    candidateVolunteers.forEach { cell ->
        if (cell.interviewScore > highestScore) {
            highestScore = cell.interviewScore
            topTalentCell = cell
            Log.d("SubjectAssignmentGrid", "New top talent found: ${cell.volunteerName} with score ${highestScore}")
        }
    }

    // Log the final result - use a local variable to avoid smart cast issues
    val finalTopTalent = topTalentCell
    if (finalTopTalent != null) {
        Log.d("SubjectAssignmentGrid", "Top talent selected: ${finalTopTalent.volunteerName} with score ${highestScore}")
    } else {
        Log.d("SubjectAssignmentGrid", "No eligible top talent found among ${candidateVolunteers.size} candidates")
    }

    return topTalentCell
}

// Helper function to check for duplicate subject assignments on the same day
private fun checkForDuplicateSubjectOnSameDay(
    scheduleName: String,
    dayIndex: Int,
    subjectCode: String,
    subjectAssignments: Map<String, Map<Pair<Int, Int>, String>>
): Boolean {
    val assignmentsForSchedule = subjectAssignments[scheduleName] ?: return false
    
    // Log the assignments we're checking
    Log.d("SubjectAssignment", "Checking for duplicates of $subjectCode on day $dayIndex in $scheduleName")
    Log.d("SubjectAssignment", "Current assignments: $assignmentsForSchedule")
    
    // Check all slots for this day
    val hasDuplicate = assignmentsForSchedule.any { (cellKey, assignedSubject) ->
        val (cellDayIndex, _) = cellKey
        val isDuplicate = cellDayIndex == dayIndex && assignedSubject == subjectCode
        if (isDuplicate) {
            Log.d("SubjectAssignment", "Found duplicate at $cellKey")
        }
        isDuplicate
    }
    
    Log.d("SubjectAssignment", "Duplicate found: $hasDuplicate")
    return hasDuplicate
}

// Subject Assignment Dialog
@Composable
fun SubjectAssignmentDialog(
    cell: GridCell,
    subjectPreset: SubjectPreset,
    scheduleData: ScheduleGridData? = null,
    remainingSubjectCounts: MutableMap<String, Int>,
    localVolunteerSwaps: Map<String, Map<Pair<Int, Int>, GridCell>>,
    onDismiss: () -> Unit,
    onSubjectAssigned: (GridCell, String) -> Unit
) {
    // Check if there's a local swap for this cell
    val cellKey = cell.dayIndex to cell.slotIndex
    val swappedCell = localVolunteerSwaps[cell.scheduleName]?.get(cellKey)
    
    // Use swapped data if available, otherwise get the most current data from schedule
    val currentCell = if (swappedCell != null) {
        // Use the swapped cell data
        swappedCell
    } else {
        // Fall back to original data
        scheduleData?.volunteerAssignments?.find {
            it.dayIndex == cell.dayIndex && it.slotIndex == cell.slotIndex
        }?.let { assignment ->
            cell.copy(
                volunteerName = assignment.volunteerName,
                volunteerRollNo = assignment.volunteerRollNo, // Use full roll number instead of last 4 digits
                volunteerGroup = assignment.volunteerGroup,
                interviewScore = assignment.interviewScore,
                subjectPreference1 = assignment.subjectPreference1,
                subjectPreference2 = assignment.subjectPreference2,
                subjectPreference3 = assignment.subjectPreference3,
                assignedSubject = cell.assignedSubject // Keep subject code from original cell passed in
            )
        } ?: cell
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false // This ensures dialog can be fullscreen
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Full screen dark background with high opacity
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)) // Pure black with 80% opacity
                    .clickable(onClick = onDismiss) // Allow clicking outside to dismiss
            )
            
            // Dialog content
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF222222))
                    .padding(horizontal = 6.dp, vertical = 16.dp)
                    .heightIn(max = 600.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2E7D32))
                        .padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentCell.volunteerName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(YellowAccent)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = currentCell.volunteerRollNo,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Black,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (currentCell.assignedSubject != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(YellowAccent)
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = SubjectConstants.SUBJECT_NAMES[currentCell.assignedSubject]
                                        ?: currentCell.assignedSubject!!,
                                    color = Color.Black,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Interview Score - added outside the green box
                if (currentCell.interviewScore > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Interview Score: ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Text(
                            text = "${currentCell.interviewScore}/100",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = YellowAccent
                        )
                    }
                }

                // Slot Details
                scheduleData?.let {
                    val dayName = it.referenceData.dayNames.getOrNull(currentCell.dayIndex) ?: ""
                    val timeSlot = it.referenceData.timeSlotNames.getOrNull(currentCell.slotIndex) ?: ""
                    Text(
                        text = "Slot: ${it.referenceData.schoolName}, $dayName, $timeSlot",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB0B0B0),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Subject Preferences
                if (currentCell.subjectPreference1.isNotEmpty()) {
                    Text(
                        text = "Subject Preferences",
                        color = YellowAccent,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        val preferences = listOf(
                            currentCell.subjectPreference1,
                            currentCell.subjectPreference2,
                            currentCell.subjectPreference3
                        ).filter { it.isNotEmpty() }
                        preferences.forEachIndexed { index, pref ->
                            // Highlight if this is the assigned subject or if it matches the subject preference
                            val isPrimary = pref == currentCell.assignedSubject || 
                                (currentCell.assignedSubject != null && pref == currentCell.assignedSubject)
                            InfoPill(text = pref, isPrimary = isPrimary) // Removed index numbers
                        }
                    }
                }

                // Available groups
                scheduleData?.let { data ->
                    val availableGroups = data.groupAvailability
                        .find { it.dayIndex == currentCell.dayIndex && it.slotIndex == currentCell.slotIndex }
                        ?.availableGroups ?: emptyList()
                    if (availableGroups.isNotEmpty()) {
                        Text(
                            text = "Available Groups for this Slot",
                            color = YellowAccent,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            availableGroups.forEach { group ->
                                val isPrimary = group == currentCell.volunteerGroup
                                InfoPill(text = group, isPrimary = isPrimary)
                            }
                        }
                        
                        // Add minimal spacing and divider right after groups
                        Spacer(modifier = Modifier.height(4.dp))
                        Divider(color = Color(0xFF444444), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // Show available subjects section (always show, even if a subject is assigned)
                val allSubjects = subjectPreset.subjects

                if (allSubjects.isNotEmpty()) {
                    // Only add divider if it wasn't already added after groups
                    if (scheduleData?.let { data ->
                            data.groupAvailability
                                .find { it.dayIndex == currentCell.dayIndex && it.slotIndex == currentCell.slotIndex }
                                ?.availableGroups?.isEmpty()
                        } == true) {
                        Divider(color = Color(0xFF444444), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Text(
                        text = "Available Subjects:",
                        color = YellowAccent,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    // Grid layout for subjects - 3 per row
                    val chunkedSubjects = allSubjects.toList().chunked(3)
                    Column(
                        modifier = Modifier
                            .heightIn(max = 250.dp)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        chunkedSubjects.forEach { rowSubjects ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                rowSubjects.forEach { (subjectCode, _) ->
                                    val remainingCount = remainingSubjectCounts[subjectCode] ?: 0
                                    val isCurrentlyAssigned = currentCell.assignedSubject == subjectCode
                                    val isAvailable = remainingCount > 0 || isCurrentlyAssigned

                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp)
                                            .clickable {
                                                if (isCurrentlyAssigned) {
                                                    // Unassign the subject
                                                    onSubjectAssigned(currentCell, "")
                                                } else if (isAvailable) {
                                                    // Assign the subject
                                                    onSubjectAssigned(currentCell, subjectCode)
                                                }
                                            }
                                            .then(
                                                if (!isAvailable && !isCurrentlyAssigned) {
                                                    Modifier.alpha(0.5f)
                                                } else {
                                                    Modifier
                                                }
                                            ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = when {
                                                isCurrentlyAssigned -> YellowAccent
                                                else -> Color(0xFF2A2A2A)
                                            }
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Only show subject code
                                            Text(
                                                text = subjectCode,
                                                color = if (isCurrentlyAssigned) Color.Black else Color.White,
                                                fontWeight = FontWeight.Medium,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "$remainingCount",
                                                color = if (isCurrentlyAssigned) Color.Black else YellowAccent,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                                // Add placeholder cards if there are fewer than 3 subjects in a row
                                repeat(3 - rowSubjects.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // Close button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowAccent,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (currentCell.assignedSubject != null) "Close" else "Cancel")
                }
            }
        }
    }
}

@Composable
private fun InfoPill(text: String, isPrimary: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isPrimary) YellowAccent else Color(0xFF3A3A3A))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (isPrimary) Color.Black else Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Dialog displaying subject information for a schedule
 */
@Composable
fun SubjectInfoDialog(
    schoolName: String,
    subjectPreset: SubjectPreset,
    remainingSubjectCounts: Map<String, Int>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(
                containerColor = NeutralCardSurface
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Subject Information",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = schoolName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = YellowAccent,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Subject list header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Subject",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB0B0B0),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB0B0B0),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(80.dp)
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 12.dp),
                    color = Color(0xFF3A3A3A)
                )

                // Subject list sorted by remaining count (descending)
                val sortedSubjects = remainingSubjectCounts.entries
                    .sortedByDescending { it.value }

                if (sortedSubjects.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No subjects available",
                            color = Color(0xFFB0B0B0),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sortedSubjects) { (subjectCode, remainingCount) ->
                            SubjectInfoItem(
                                assignedSubject = subjectCode,
                                subjectName = SubjectConstants.SUBJECT_NAMES[subjectCode] ?: subjectCode,
                                remainingCount = remainingCount
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual subject info item
 */
@Composable
fun SubjectInfoItem(
    assignedSubject: String,
    subjectName: String,
    remainingCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E1E1E)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subjectName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = assignedSubject,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0B0B0)
                )
            }

            // Remaining count badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (remainingCount > 0) YellowAccent else Color(0xFF9E9E9E)
            ) {
                Text(
                    text = remainingCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

