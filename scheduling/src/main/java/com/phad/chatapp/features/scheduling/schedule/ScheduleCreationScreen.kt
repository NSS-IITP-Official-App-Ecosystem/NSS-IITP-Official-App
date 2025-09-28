package com.phad.chatapp.features.scheduling.schedule

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    var showManualSelection by remember { mutableStateOf(false) }
    var selectedSlotForManualAssignment by remember { mutableStateOf<Slot?>(null) }
    var isSavingSchedule by remember { mutableStateOf(false) }

    // Initialize viewModel
    LaunchedEffect(vpId, vaIds) {
        viewModel.initialize(vpId, vaIdsList)
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
                    .padding(
                        horizontal = 20.dp, // UI.md standard horizontal margins
                        vertical = 8.dp     // UI.md header padding
                    ),
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
                        modifier = Modifier.size(28.dp) // UI.md icon size
                    )
                }

                // Title with proper spacing
                Text(
                    text = "Create Schedule",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp) // UI.md title padding
                )

                // Save button only in header (text only)
                StandardButton(
                    onClick = {
                        if (!isSavingSchedule) { // Prevent multiple save operations
                            showFinishDialog = true
                        }
                    }
                ) {
                    Text(
                        "Save",
                        fontWeight = FontWeight.Medium
                    )
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
                                    // Action buttons section - positioned above schedule content
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp), // UI.md spacing
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Assign button
                                        StandardButton(
                                            onClick = { viewModel.assignLowestTFVSlot() },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                "Assign",
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        // View volunteers button (text only)
                                        StandardButton(
                                            onClick = { showVolunteersList = true },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                "Volunteers",
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

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
                                                    // For unassigned slots, directly show manual selection
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
            // If a slot is selected, show the assignment panel
            currentSlot?.let { slot ->
                AssignmentPanel(
                    slot = slot,
                    volunteers = volunteers,
                    onAssignManual = { volunteer -> viewModel.assignSpecificVolunteer(volunteer) },
                    onAssignAutomatic = { viewModel.assignVolunteer() },
                    onClose = { viewModel.selectSlot(null) }
                )
            }

            // Manual volunteer selection dialog
            if (showManualSelection && selectedSlotForManualAssignment != null) {
                ManualVolunteerSelectionDialog(
                    volunteers = volunteers,
                    slot = selectedSlotForManualAssignment!!,
                    onDismiss = {
                        showManualSelection = false
                        selectedSlotForManualAssignment = null
                    },
                    onVolunteerSelected = { volunteer ->
                        viewModel.assignSpecificVolunteer(volunteer, selectedSlotForManualAssignment!!)
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

                                // Navigate back
                                navController.navigateUp()
                            } catch (e: Exception) {
                                // Hide loading screen and show error
                                isSavingSchedule = false
                                snackbarHostState.showSnackbar("Error saving schedule: ${e.message}")
                            }
                        }
                    }
                )
            }

            // Snackbar host positioned at bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
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
                        // Display first name and last 4 characters of roll number
                        val firstName = slot.assignedVolunteerName?.split(" ")?.firstOrNull() ?: "Assigned"
                        val rollLast4 = slot.assignedVolunteerRollNo?.takeLast(4) ?: ""
                        val displayText = if (rollLast4.isNotEmpty()) "$firstName ($rollLast4)" else firstName
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