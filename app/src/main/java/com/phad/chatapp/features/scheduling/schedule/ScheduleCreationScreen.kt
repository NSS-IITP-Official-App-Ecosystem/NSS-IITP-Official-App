package com.phad.chatapp.features.scheduling.schedule

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import kotlinx.coroutines.delay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.phad.chatapp.features.scheduling.ui.components.StandardButton
import com.phad.chatapp.features.scheduling.ui.theme.DarkBackground
import com.phad.chatapp.features.scheduling.ui.theme.DarkSurface
import com.phad.chatapp.features.scheduling.ui.theme.NeutralCardSurface
import com.phad.chatapp.features.scheduling.ui.theme.YellowAccent
import kotlinx.coroutines.launch

/**
 * Main screen for schedule creation and volunteer assignment
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleCreationScreen(
    navController: NavController,
    vpId: String,
    vaIds: String
) {
    val viewModel: ScheduleGenerationViewModel = viewModel()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Parse vaIds string
    val vaIdsList = vaIds.split(",")

    // State
    val schools by viewModel.schools.collectAsState()
    val volunteers by viewModel.volunteers.collectAsState()
    val slots by viewModel.slots.collectAsState()
    val assignedVolunteers by viewModel.assignedVolunteers.collectAsState()
    val unassignedVolunteers by viewModel.unassignedVolunteers.collectAsState()
    val currentSlot by viewModel.currentSlot.collectAsState()
    val groupCounts by viewModel.groupCounts.collectAsState()

    // UI state
    var selectedSchoolIndex by remember { mutableStateOf(0) }
    var showVolunteersList by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showManualSelection by remember { mutableStateOf(false) } // Still used if needed, but not for fallback
    var showAutoAssignError by remember { mutableStateOf(false) } // New state for error dialog
    var selectedSlotForManualAssignment by remember { mutableStateOf<Slot?>(null) }
    var isSavingSchedule by remember { mutableStateOf(false) }
    var showAlgorithmLogs by remember { mutableStateOf(false) } // NEW: Log viewer dialog

    // Initialize viewModel
    LaunchedEffect(vpId, vaIds) {
        viewModel.initialize(vpId, vaIdsList)
    }
    
    // Message state
    var showMessage by remember { mutableStateOf(false) }
    var currentMessage by remember { mutableStateOf("") }

    // Observe assignment success message and show Sliding Message
    val assignmentMessage by viewModel.lastAssignmentMessage.collectAsState()
    LaunchedEffect(assignmentMessage) {
        assignmentMessage?.let { message ->
            currentMessage = message
            showMessage = true
            delay(3000) // Show for 3 seconds
            showMessage = false
            delay(300) // Wait for animation
            viewModel.clearAssignmentMessage()
        }
    }

    // Main layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Custom header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp)
                    .padding(start = 4.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button with 48dp touch target
                IconButton(
                    onClick = {
                        if (!isSavingSchedule) { // Prevent navigation while saving
                            navController.navigateUp()
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp) // UI.md icon size
                    )
                }

                // Title with proper spacing
                Text(
                    text = "Create Schedule",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp) // UI.md title padding
                )
                
                // Volunteers button (circular yellow per TTW_UI_plan.md)
                if (!viewModel.isLoading && !isSavingSchedule) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp, end = 8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(YellowAccent)
                            .clickable { showVolunteersList = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Volunteers",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }



                // Log viewer button (circular yellow per TTW_UI_plan.md)
                if (!viewModel.isLoading && !isSavingSchedule) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp, end = 4.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(YellowAccent)
                            .clickable { showAlgorithmLogs = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryBooks,
                            contentDescription = "View Logs",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Loading indicator - shown immediately when loading, outside of animated content
            if (viewModel.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = YellowAccent,
                        modifier = Modifier.size(48.dp), // UI.md loading indicator size
                        strokeWidth = 4.dp // UI.md stroke width
                    )
                }
            }

            // Main content area with proper spacing - only show when not loading
            if (!viewModel.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = 20.dp, // UI.md standard horizontal margins
                            vertical = 16.dp    // UI.md vertical padding
                        )
                        .padding(top = 8.dp)    // Additional top padding
                ) {
                        // Show error state or content
                        when {
                            viewModel.errorMessage != null -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = viewModel.errorMessage ?: "Unknown error",
                                        color = Color.Red,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                            schools.isEmpty() -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No schools found. Please go back and select presets again.",
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                            else -> {
                                Column {
                                    // Schedule content
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f) // This ensures proper height constraints
                                    ) {
                                        // Get the most up-to-date slot data to ensure proper recomposition
                                        val currentSlots by viewModel.slots.collectAsState()

                                        // Recreate the school objects with the latest slot data to force recomposition
                                        val updatedSchools = schools.map { school ->
                                            val updatedDays = school.days.map { day ->
                                                val updatedSlots = day.slots.map { slot ->
                                                    // Find the updated slot with the same identifiers
                                                    currentSlots.find {
                                                        it.schoolId == slot.schoolId &&
                                                        it.dayIndex == slot.dayIndex &&
                                                        it.slotIndex == slot.slotIndex
                                                    } ?: slot
                                                }
                                                day.copy(slots = updatedSlots)
                                            }
                                            school.copy(days = updatedDays)
                                        }

                                        TFVScheduleView(
                                            schools = updatedSchools,
                                            onSlotClick = { slot ->
                                                if (slot.assignedVolunteerId == null) {
                                                    // For unassigned slots, show the list of available volunteers
                                                    selectedSlotForManualAssignment = slot
                                                    showManualSelection = true
                                                } else {
                                                    // For assigned slots, show assignment panel
                                                    viewModel.selectSlot(slot)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        }

        // Overlays positioned outside the main column
        Box(modifier = Modifier.fillMaxSize()) {
            // Sliding Assignment Message (Overlay)
            // Positioned at the top, respecting header space (approx 70-80dp)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp), // Position below header
                contentAlignment = Alignment.TopStart
            ) {
                 AnimatedVisibility(
                    visible = showMessage,
                    enter = slideInHorizontally(initialOffsetX = { -it }),
                    exit = slideOutHorizontally(targetOffsetX = { -it })
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .shadow(8.dp, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = NeutralCardSurface
                        ),
                        border = BorderStroke(1.dp, YellowAccent.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Success Icon
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(YellowAccent.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = YellowAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Message Text
                            Text(
                                text = currentMessage,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // If a slot is selected, show the assignment panel
            currentSlot?.let { slot ->
                if (slot.assignedVolunteerId != null) {
                    AssignmentPanel(
                        slot = slot,
                        volunteers = volunteers,
                        onAssignManual = { volunteer, subject -> viewModel.assignSpecificVolunteer(volunteer, slot, subject) },
                        onAssignAutomatic = { viewModel.assignVolunteerToCurrentSlot() },
                        onClose = { viewModel.selectSlot(null) }
                    )
                }
            }

            // Manual volunteer selection dialog
            if (showManualSelection && selectedSlotForManualAssignment != null) {
                // Determine conflicting volunteers (assigned to other slots at same time)
                val conflictingVolunteerIds = remember(slots, selectedSlotForManualAssignment) {
                    val target = selectedSlotForManualAssignment!!
                    slots.filter { 
                        it.dayName == target.dayName && 
                        it.timeLabel == target.timeLabel && 
                        it.assignedVolunteerId != null &&
                        it.slotIndex != target.slotIndex // Don't count self if editing
                    }.mapNotNull { it.assignedVolunteerId }.toSet()
                }

                ManualVolunteerSelectionDialog(
                    volunteers = volunteers,
                    slot = selectedSlotForManualAssignment!!,
                    conflictingVolunteerIds = conflictingVolunteerIds,
                    onDismiss = {
                        showManualSelection = false
                        selectedSlotForManualAssignment = null
                    },
                    onVolunteerSelected = { volunteer, subject ->
                        viewModel.assignSpecificVolunteer(volunteer, selectedSlotForManualAssignment!!, subject)
                        showManualSelection = false
                        selectedSlotForManualAssignment = null
                    }
                )
            }

            // Volunteers list dialog
            if (showVolunteersList) {
                VolunteersListDialog(
                    volunteers = volunteers,
                    onDismiss = { showVolunteersList = false }
                )
            }

            // Finish dialog
            if (showFinishDialog) {
                FinishDialog(
                    onDismiss = {
                        if (!isSavingSchedule) { // Prevent dismissing while saving
                            showFinishDialog = false
                        }
                    },
                    onFinish = { presetName ->
                        // Start saving process
                        isSavingSchedule = true
                        showFinishDialog = false

                        coroutineScope.launch {
                            try {
                                // Save schedule
                                viewModel.saveSchedule()

                                // Save assigned volunteers presets (one per teaching slot preset)
                                val createdPresetIds = viewModel.saveAssignedVolunteersPresets()

                                // Save unassigned volunteers preset
                                viewModel.saveUnassignedVolunteersPreset(presetName)

                                // Show success message with count of created presets
                                val presetCount = createdPresetIds.size
                                val message = if (presetCount > 0) {
                                    "Schedule saved! Created $presetCount assigned volunteer preset${if (presetCount > 1) "s" else ""} and 1 unassigned preset"
                                } else {
                                    "Schedule and unassigned volunteer preset saved successfully"
                                }
                                snackbarHostState.showSnackbar(message)

                                // Navigate back to the main scheduling dashboard
                                navController.popBackStack("scheduleMaker", inclusive = false)
                            } catch (e: Exception) {
                                // Hide loading screen and show error
                                isSavingSchedule = false
                                snackbarHostState.showSnackbar("Error saving schedule: ${e.message}")
                            }
                        }
                    }
                )
            }

            // FAB for Save action
            if (!viewModel.isLoading && !isSavingSchedule) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 24.dp, bottom = 48.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Assign FAB
                        FloatingActionButton(
                            onClick = {
                                val assigned = viewModel.assignNextSlotInRoundRobin()
                                if (!assigned) {
                                    Log.d("ScheduleCreationScreen", "❌ Step assignment failed - no assignment possible")
                                    showAutoAssignError = true
                                }
                            },
                            containerColor = Color(0xFF00B0FF), // Light Blue A400 (Vibrant but not too light)
                            contentColor = Color.Black
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Assign Automatically",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Save FAB
                        FloatingActionButton(
                            onClick = {
                                if (!isSavingSchedule) { // Prevent multiple save operations
                                    showFinishDialog = true
                                }
                            },
                            containerColor = Color(0xFF4CAF50), // Green color
                            contentColor = Color.White
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Snackbar host positioned above bottom navigation
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = 72.dp), // 72dp to clear bottom nav
                contentAlignment = Alignment.BottomCenter
            ) {
                SnackbarHost(snackbarHostState)
            }

            // Loading screen overlay for schedule saving
            if (isSavingSchedule) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground.copy(alpha = 0.95f)), // Semi-transparent dark background
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = YellowAccent,
                            modifier = Modifier.size(56.dp), // Slightly larger for loading screen
                            strokeWidth = 5.dp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Saving schedule...",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Auto-Assign Error Overlay (HIGHEST Z-ORDER - last child of root Box)
            if (showAutoAssignError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable { /* Consume clicks */ },
                    contentAlignment = Alignment.Center
                ) {
                   Card(
                       colors = CardDefaults.cardColors(containerColor = NeutralCardSurface),
                       shape = RoundedCornerShape(16.dp),
                       modifier = Modifier.padding(32.dp)
                   ) {
                       Column(
                           modifier = Modifier.padding(24.dp),
                           horizontalAlignment = Alignment.CenterHorizontally
                       ) {
                           Text(
                               text = "Assignment Failed",
                               color = Color.White,
                               style = MaterialTheme.typography.titleMedium,
                               fontWeight = FontWeight.Bold,
                               modifier = Modifier.padding(bottom = 16.dp)
                           )
                           
                           Text(
                               text = "No suitable volunteer found that meets all criteria.",
                               color = Color(0xFFB0B0B0),
                               style = MaterialTheme.typography.bodyMedium,
                               textAlign = TextAlign.Center,
                               modifier = Modifier.padding(bottom = 24.dp)
                           )
                           
                           Button(
                               onClick = { showAutoAssignError = false },
                               colors = ButtonDefaults.buttonColors(
                                   containerColor = YellowAccent,
                                   contentColor = Color.Black
                               ),
                               shape = RoundedCornerShape(8.dp)
                           ) {
                               Text(
                                   text = "OK",
                                   fontWeight = FontWeight.Bold
                               )
                           }
                       }
                   }
                }
            }

            // Algorithm Log Viewer Dialog
            if (showAlgorithmLogs) {
                val logs by viewModel.algorithmLogs.collectAsState()
                AlgorithmLogDialog(
                    logs = logs,
                    onDismiss = { showAlgorithmLogs = false },
                    onClear = { viewModel.clearLogs() }
                )
            }
        }
    }
}

/**
 * Slot item in the schedule
 */
@Composable
fun SlotItem(
    slot: Slot,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
            .let {
                if (isSelected) {
                    it.border(2.dp, YellowAccent, RoundedCornerShape(8.dp))
                } else {
                    it
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (slot.assignedVolunteerId != null) {
                Color(0xFF2E7D32) // Green background for assigned slots
            } else {
                // Color based on TFV - lower TFV = more urgent (warmer color)
                when {
                    slot.tfv <= 3 -> Color(0xFFB71C1C) // Red
                    slot.tfv <= 5 -> Color(0xFF4E342E) // Brown
                    slot.tfv <= 10 -> Color(0xFF37474F) // Blue-gray
                    else -> DarkSurface
                }
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time label
                Text(
                    text = slot.timeLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                // TFV or assigned volunteer
                if (slot.assignedVolunteerId != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        // Display first name and last 4 characters of roll number + Subject
                        val firstName = slot.assignedVolunteerName?.split(" ")?.firstOrNull() ?: "Assigned"
                        val rollLast4 = slot.assignedVolunteerRollNo?.takeLast(4) ?: ""
                        val subjectAbbr = slot.assignedSubject?.take(3) ?: ""
                        
                        val detailsText = if (subjectAbbr.isNotEmpty()) {
                            if (rollLast4.isNotEmpty()) "$rollLast4 $subjectAbbr" else subjectAbbr
                        } else rollLast4
                        
                        val displayText = if (detailsText.isNotEmpty()) "$firstName ($detailsText)" else firstName
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                } else {
                    Badge(
                        containerColor = when {
                            slot.tfv <= 3 -> Color(0xFFFF5252) // Red
                            slot.tfv <= 5 -> Color(0xFFFFB74D) // Orange
                            slot.tfv <= 10 -> Color(0xFFFFEE58) // Yellow
                            else -> Color(0xFF66BB6A) // Green
                        },
                        contentColor = Color.Black
                    ) {
                        Text(
                            text = "TFV: ${slot.tfv}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Available groups
            Text(
                text = "Groups: ${slot.availableGroups.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}