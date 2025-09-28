package com.phad.chatapp.features.scheduling.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.phad.chatapp.features.scheduling.models.SubjectPreset
import com.phad.chatapp.features.scheduling.models.ScheduleSubjectPairing
import com.phad.chatapp.features.scheduling.ui.components.StandardButton
import com.phad.chatapp.features.scheduling.ui.theme.DarkBackground
import com.phad.chatapp.features.scheduling.ui.theme.DarkSurface
import com.phad.chatapp.features.scheduling.ui.theme.ErrorRed
import com.phad.chatapp.features.scheduling.ui.theme.NeutralCardSurface
import com.phad.chatapp.features.scheduling.ui.theme.YellowAccent
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.features.scheduling.firebase.FirestoreCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.util.Log

// Simple data class for schedule presets
data class SchedulePreset(
    val id: String = "",
    val name: String = "",
    val createdAt: Long = 0
)

@Composable
fun SubjectPresetSelectionScreen(navController: NavController) {
    var schedulePresets by remember { mutableStateOf<List<SchedulePreset>>(emptyList()) }
    var subjectPresets by remember { mutableStateOf<List<SubjectPreset>>(emptyList()) }
    var selectedSchedules by remember { mutableStateOf<Set<String>>(emptySet()) }
    var schedulePairings by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    
    // Delete dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var scheduleToDelete by remember { mutableStateOf<SchedulePreset?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Load data
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val schedules = loadSchedulePresets()
            val subjects = loadSubjectPresets()
            schedulePresets = schedules
            subjectPresets = subjects
            isLoading = false
        }
    }

    // Function to delete a schedule
    fun deleteSchedule(schedule: SchedulePreset) {
        isDeleting = true
        
        coroutineScope.launch {
            try {
                val success = deleteSchedulePreset(schedule.id)
                if (success) {
                    // Remove from local list
                    schedulePresets = schedulePresets.filter { it.id != schedule.id }
                    // Remove from selected schedules if present
                    if (selectedSchedules.contains(schedule.id)) {
                        selectedSchedules = selectedSchedules - schedule.id
                    }
                    // Remove from pairings if present
                    if (schedulePairings.containsKey(schedule.id)) {
                        schedulePairings = schedulePairings - schedule.id
                    }
                    snackbarHostState.showSnackbar("Schedule '${schedule.name}' deleted successfully")
                } else {
                    snackbarHostState.showSnackbar("Failed to delete schedule")
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Error deleting schedule: ${e.message}")
            } finally {
                isDeleting = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header with back button, title, and Next button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.navigateUp() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Text(
                    text = "Add Subjects",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )

                // Next button - only show when selections are complete
                StandardButton(
                    onClick = {
                        // Navigate to Subject Assignment Grid Screen
                        // Pass the selected schedules and their pairings
                        val selectedScheduleIds = selectedSchedules.joinToString(",")
                        val pairingData = schedulePairings.entries.joinToString("|") { "${it.key}:${it.value}" }
                        navController.navigate("subjectAssignmentGrid/$selectedScheduleIds/$pairingData")
                    },
                    enabled = selectedSchedules.isNotEmpty() && schedulePairings.keys.containsAll(selectedSchedules) && !isSaving
                ) {
                    Text(
                        "Assign",
                        color = Color.Black,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = YellowAccent)
                }
            } else if (schedulePresets.isEmpty()) {
                // No schedules available
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NeutralCardSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No Schedules Found",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            "Create schedules first to assign subject presets",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB0B0B0)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        StandardButton(
                            onClick = { navController.navigateUp() }
                        ) {
                            Text(
                                "Create Schedules",
                                color = Color.Black,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else if (subjectPresets.isEmpty()) {
                // No subject presets available
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NeutralCardSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No Subject Presets Found",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            "Create subject presets first to assign to schedules",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB0B0B0)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        StandardButton(
                            onClick = { navController.navigateUp() }
                        ) {
                            Text(
                                "Create Subject Presets",
                                color = Color.Black,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                // Main content
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(start = 0.dp, top = 16.dp, end = 0.dp, bottom = 80.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Schedule Presets
                    items(schedulePresets) { schedule ->
                        SchedulePresetCard(
                            schedule = schedule,
                            isSelected = selectedSchedules.contains(schedule.id),
                            selectedSubjectPresetId = schedulePairings[schedule.id],
                            subjectPresets = subjectPresets,
                            onScheduleToggle = { scheduleId ->
                                selectedSchedules = if (selectedSchedules.contains(scheduleId)) {
                                    selectedSchedules - scheduleId
                                } else {
                                    selectedSchedules + scheduleId
                                }
                            },
                            onSubjectPresetSelected = { scheduleId, subjectPresetId ->
                                schedulePairings = schedulePairings + (scheduleId to subjectPresetId)
                            },
                            onDelete = {
                                scheduleToDelete = schedule
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }
        
        // SnackbarHost for user feedback
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog && scheduleToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                scheduleToDelete = null
            },
            title = {
                Text(
                    "Delete Schedule",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            containerColor = DarkSurface,
            titleContentColor = Color.White,
            textContentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            text = {
                Text(
                    "Are you sure you want to delete '${scheduleToDelete!!.name}'? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0B0B0)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteSchedule(scheduleToDelete!!)
                        showDeleteDialog = false
                        scheduleToDelete = null
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorRed
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "Delete",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scheduleToDelete = null
                    },
                    enabled = !isDeleting
                ) {
                    Text("Cancel", color = YellowAccent)
                }
            }
        )
    }
}

@Composable
fun SchedulePresetCard(
    schedule: SchedulePreset,
    isSelected: Boolean,
    selectedSubjectPresetId: String?,
    subjectPresets: List<SubjectPreset>,
    onScheduleToggle: (String) -> Unit,
    onSubjectPresetSelected: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    var showSubjectDropdown by remember { mutableStateOf(false) }

    val selectedPreset = selectedSubjectPresetId?.let { id ->
        subjectPresets.find { it.id == id }
    }

    val textColor = if (isSelected) Color.Black else Color.White
    val secondaryTextColor = if (isSelected) Color(0xFF333333) else Color(0xFFB0B0B0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onScheduleToggle(schedule.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) YellowAccent else Color(0xFF0F0F0F)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = schedule.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 2.dp)
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                color = Color.Red.copy(alpha = 0.18f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            if (!isSelected && selectedPreset == null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap to assign a subject preset",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0B0B0)
                )
            }
            if (selectedPreset != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Assigned: ${selectedPreset.name} (${selectedPreset.subjects.count { it.value > 0 }} subjects)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor
                )
            }
            if (isSelected) {
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSubjectDropdown = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedSubjectPresetId?.let { id ->
                                    subjectPresets.find { it.id == id }?.name ?: "Select Subject Preset"
                                } ?: "Select Subject Preset",
                                color = Color.Black,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Dropdown",
                                tint = Color.Black
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showSubjectDropdown,
                        onDismissRequest = { showSubjectDropdown = false },
                        modifier = Modifier.background(NeutralCardSurface)
                    ) {
                        subjectPresets.forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        preset.name,
                                        color = Color.White
                                    )
                                },
                                onClick = {
                                    onSubjectPresetSelected(schedule.id, preset.id)
                                    showSubjectDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun loadSchedulePresets(): List<SchedulePreset> {
    return try {
        val db = FirebaseFirestore.getInstance()
        val snapshot = db.collection(FirestoreCollection.GENERATED_SCHEDULES).get().await()
        val presets = snapshot.documents.mapNotNull { doc ->
            val name = doc.getString("name") ?: doc.id
            val createdAt = doc.getLong("createdAt") ?: 0
            SchedulePreset(
                id = doc.id,
                name = name,
                createdAt = createdAt
            )
        }
        
        // Sort presets alphanumerically by name
        presets.sortedWith(compareBy<SchedulePreset> { preset -> 
            // Split the name into parts (e.g., "AM 11B" -> ["AM", "11B"])
            val parts = preset.name.split(" ", "-")
            
            // Extract the school name part
            parts.getOrNull(0) ?: ""
        }.thenBy { preset ->
            // Extract the section part and parse numeric prefix for proper numeric sorting
            val parts = preset.name.split(" ", "-")
            val sectionPart = parts.getOrNull(1) ?: ""
            val numericPrefix = sectionPart.takeWhile { char -> char.isDigit() }
            numericPrefix.toIntOrNull() ?: 0
        }.thenBy { preset ->
            // Extract the alphabetic suffix for final sorting
            val parts = preset.name.split(" ", "-")
            val sectionPart = parts.getOrNull(1) ?: ""
            sectionPart.dropWhile { char -> char.isDigit() }
        })
    } catch (e: Exception) {
        emptyList()
    }
}

private suspend fun loadSubjectPresets(): List<SubjectPreset> {
    return try {
        val db = FirebaseFirestore.getInstance()
        val snapshot = db.collection(FirestoreCollection.SUBJECT_PRESETS).get().await()
        snapshot.documents.mapNotNull { doc ->
            doc.toObject(SubjectPreset::class.java)?.copy(id = doc.id)
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private suspend fun deleteSchedulePreset(scheduleId: String): Boolean {
    return try {
        val db = FirebaseFirestore.getInstance()
        
        // Delete the schedule document
        db.collection(FirestoreCollection.GENERATED_SCHEDULES)
            .document(scheduleId)
            .delete()
            .await()
        
        // Success
        true
    } catch (e: Exception) {
        Log.e("SubjectPresetSelectionScreen", "Error deleting schedule: $scheduleId", e)
        false
    }
}
