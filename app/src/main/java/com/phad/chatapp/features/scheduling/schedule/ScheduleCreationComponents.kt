package com.phad.chatapp.features.scheduling.schedule

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.material3.Badge
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import com.phad.chatapp.features.scheduling.ui.theme.DarkSurface
import com.phad.chatapp.features.scheduling.ui.theme.NeutralCardSurface
import com.phad.chatapp.features.scheduling.ui.theme.YellowAccent

/**
 * Dialog showing all volunteers
 */
@Composable
fun VolunteersListDialog(
    volunteers: List<Volunteer>,
    onDismiss: () -> Unit
) {
    var filterMode by remember { mutableStateOf(0) } // 0=All, 1=Assigned, 2=Unassigned
    
    // Multi-select state
    var selectedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showGroupGridDialog by remember { mutableStateOf(false) }
    
    var sortByGroup by remember { mutableStateOf(false) }

    // Get all unique groups
    val groups = volunteers.map { it.group }.distinct().sortedWith(compareBy {
        try {
            it.toInt()
        } catch (e: NumberFormatException) {
            Int.MAX_VALUE
        }
    })

    // Filter volunteers based on criteria
    val filteredVolunteers = volunteers.filter { volunteer ->
        val assignmentMatch = when (filterMode) {
            1 -> volunteer.assignedSlots.isNotEmpty() || volunteer.assignedSlot != null // Assigned (Any slot)
            2 -> volunteer.assignedSlots.isEmpty() && volunteer.assignedSlot == null // Unassigned (No slots)
            else -> true // All
        }

        val groupMatch = if (selectedGroups.isEmpty()) true else selectedGroups.contains(volunteer.group)

        assignmentMatch && groupMatch
    }.let { filtered ->
        if (sortByGroup) {
            filtered.sortedWith(compareBy(
                // First sort by group numerically
                { vol ->
                    try {
                        vol.group.toInt()
                    } catch (e: NumberFormatException) {
                        Int.MAX_VALUE
                    }
                },
                // Then sort by name
                { it.name }
            ))
        } else {
            filtered.sortedBy { it.name }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize(0.95f),
            colors = CardDefaults.cardColors(
                containerColor = NeutralCardSurface // UI.md standard card background
            ),
            shape = RoundedCornerShape(16.dp), // UI.md card corner radius
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp) // UI.md dialog elevation
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp) // UI.md standard horizontal margins
            ) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp), // UI.md section spacing
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "All Volunteers",
                                style = MaterialTheme.typography.titleLarge, // UI.md typography
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(48.dp) // UI.md touch target
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Top Control Row: Filter Toggle | Group Dropdown | Sort
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp), // UI.md element spacing
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Tri-state Filter Toggle
                            // Cycle: All (Default) -> Assigned -> Unassigned -> All
                            // Button text shows CURRENT view or Target? Usually shows current state.
                            // State 0 (All) -> "All"
                            // State 1 (Assigned) -> "Assigned"
                            // State 2 (Unassigned) -> "Unassigned"
                            
                            val filterLabel = when(filterMode) {
                                1 -> "Assigned"
                                2 -> "Unassigned"
                                else -> "All" // 0
                            }
                            
                            val filterColor = when(filterMode) {
                                1 -> Color(0xFF2E7D32) // Green for Assigned
                                2 -> Color(0xFFC62828) // Red for Unassigned
                                else -> YellowAccent   // Yellow for All
                            }
                            
                            val filterTextColor = when(filterMode) {
                                1, 2 -> Color.White
                                else -> Color.Black
                            }

                            Button(
                                onClick = { 
                                    // Cycle: All (0) -> Assigned (1) -> Unassigned (2) -> All (0)
                                    // User requested: "One click assigned, another click unassigned another click all..."
                                    // implies: Default(All/Assigned?) -> Assigned -> Unassigned -> All
                                    // Let's implement 0->1->2->0
                                    filterMode = (filterMode + 1) % 3
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = filterColor,
                                    contentColor = filterTextColor
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = filterLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // 2. Group Filter Button -> Grid Dialog
                            Box(
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedButton(
                                    onClick = { showGroupGridDialog = true },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White,
                                        containerColor = DarkSurface
                                    ),
                                    border = BorderStroke(1.dp, YellowAccent.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    val buttonText = when {
                                        selectedGroups.isEmpty() -> "All Gps"
                                        selectedGroups.size == 1 -> "Gp ${selectedGroups.first()}"
                                        selectedGroups.size <= 2 -> "Gps ${selectedGroups.joinToString(",")}"
                                        else -> "${selectedGroups.size} Gps"
                                    }
                                    
                                    Text(
                                        text = buttonText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // 3. Sort Button
                            FilterChip(
                                selected = sortByGroup,
                                onClick = { sortByGroup = !sortByGroup },
                                label = {
                                    Text(
                                        "Sort",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier
                                    .weight(0.7f) // Slightly smaller
                                    .heightIn(min = 48.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YellowAccent,
                                    selectedLabelColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Volunteers list count
                        Text(
                            text = "${filteredVolunteers.size} volunteers",
                            color = Color(0xFFB0B0B0), // UI.md secondary text color
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 12.dp) // UI.md element spacing
                        )

                        // Volunteers list
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp), // UI.md card spacing
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            itemsIndexed(filteredVolunteers) { index, volunteer ->
                                VolunteerListItem(
                                    volunteer = volunteer
                                )
                            }
                        }
                    }
            }
        }

    // Grid Dialog for Group Selection (Inside VolunteersListDialog)
    if (showGroupGridDialog) {
        Dialog(
            onDismissRequest = { showGroupGridDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.75f) // Even smaller width
                    .heightIn(max = 450.dp) // Even smaller height
                    .padding(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = NeutralCardSurface
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp) // Reduced padding
                ) {
                    Text(
                        text = "Select Groups",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // Grid of groups
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 40.dp), // Even smaller cells
                        verticalArrangement = Arrangement.spacedBy(6.dp), // Reduced spacing
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                    ) {
                        items(groups) { group ->
                            val isSelected = selectedGroups.contains(group)
                            
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .background(
                                        color = if (isSelected) YellowAccent else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) YellowAccent else Color.Gray,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        selectedGroups = if (isSelected) {
                                            selectedGroups - group
                                        } else {
                                            selectedGroups + group
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = group,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodySmall // Smaller text
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), // Check UI layout
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Clear All
                        TextButton(
                            onClick = { selectedGroups = emptySet() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                        ) {
                            Text("Clear All")
                        }
                        
                        // Done
                        Button(
                            onClick = { showGroupGridDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = YellowAccent,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Volunteer list item with UI.md specifications
 */
@Composable
fun VolunteerListItem(
    volunteer: Volunteer
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = NeutralCardSurface
        ),
        shape = RoundedCornerShape(16.dp), // UI.md card corner radius
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp) // UI.md card elevation
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp) // UI.md card internal padding
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Column 1: Volunteer Info (45%) - Enhanced readability & Centered
                    Column(
                        modifier = Modifier
                            .weight(0.45f)
                            .padding(end = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally // Center alignment
                    ) {
                        // Line 1: Full Name (truncated)
                        Text(
                            text = volunteer.name,
                            color = Color.White, // UI.md primary text color
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 18.sp // Reduced from 20.sp
                            ),
                            fontWeight = FontWeight.Normal, // Explicit unbold
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Line 2: Chips for Group and Roll - Larger font
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Group Chip
                            Surface(
                                color = YellowAccent,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "Gp ${volunteer.group}",
                                    color = Color.Black,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 16.sp // Explicit 16sp
                                    ),
                                    fontWeight = FontWeight.Normal, // Explicit unbold
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // Roll Chip
                            val rollLast4 = volunteer.rollNo.takeLast(4)
                            if (rollLast4.isNotEmpty()) {
                                Surface(
                                    color = YellowAccent,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = rollLast4,
                                        color = Color.Black,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 16.sp // Explicit 16sp
                                        ),
                                        fontWeight = FontWeight.Normal, // Explicit unbold
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Line 3: Class = [ClassCount], Sc = [Score]
                        Text(
                            text = "Class = ${volunteer.classCount}, Sc = ${volunteer.interviewScore}",
                            color = Color(0xFFB0B0B0), // UI.md secondary text color
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Column 2: Status/Preferences (55%) - Centered
                    Column(
                        modifier = Modifier
                            .weight(0.55f)
                            .padding(start = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally // Center alignment
                    ) {
                        if (volunteer.assignedSlots.isNotEmpty() || volunteer.assignedSlot != null) {
                            // Combine list and single slot (fallback)
                            val slotsToShow = if (volunteer.assignedSlots.isNotEmpty()) {
                                volunteer.assignedSlots
                            } else {
                                listOfNotNull(volunteer.assignedSlot)
                            }
                            
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                slotsToShow.forEach { slot ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Assigned State - Subject Name Only, Larger
                                        val rank = if (slot.assignedSubject != null) {
                                            val idx = volunteer.subjectPreferences.indexOfFirst { it.equals(slot.assignedSubject, ignoreCase = true) }
                                            if (idx != -1) " (#${idx + 1})" else ""
                                        } else ""
                                        
                                        Text(
                                            text = (slot.assignedSubject ?: "N/A") + rank,
                                            color = YellowAccent,
                                            style = MaterialTheme.typography.titleLarge.copy( // Larger font for subject
                                                fontSize = 18.sp // Reduced from 20.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                        
                                        Spacer(modifier = Modifier.height(2.dp))
                                        
                                        // Split slot details into two lines
                                        Text(
                                            text = "${slot.schoolName} • ${slot.dayName}",
                                            color = Color(0xFF2E7D32), // Success green
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )

                                        Text(
                                            text = slot.timeLabel,
                                            color = Color(0xFF2E7D32), // Success green
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            // Unassigned State - Just the preferences list
                            val prefsText = if (volunteer.subjectPreferences.isNotEmpty()) {
                                volunteer.subjectPreferences.joinToString(", ") { it.take(3) }
                            } else {
                                "None"
                            }
                            
                            Text(
                                text = prefsText,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                ),
                                maxLines = 2, // Allow 2 lines for preferences
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
}

/**
 * Panel for assigning a volunteer to a slot
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssignmentPanel(
    slot: Slot,
    volunteers: List<Volunteer>,
    onAssignManual: (Volunteer, String) -> Unit,
    onAssignAutomatic: () -> Unit,
    onClose: () -> Unit
) {
    // If volunteer is assigned, show centered card layout
    if (slot.assignedVolunteerId != null) {
        // Full screen overlay with centered content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)), // Semi-transparent background
            contentAlignment = Alignment.Center
        ) {
            // Close button in top-right corner
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp) // UI.md touch target
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Improved assignment display card
            Card(
                modifier = Modifier
                    .wrapContentWidth()
                    .widthIn(min = 320.dp, max = 400.dp), // Better width constraints
                colors = CardDefaults.cardColors(
                    containerColor = NeutralCardSurface // UI.md standard card background
                ),
                shape = RoundedCornerShape(16.dp), // UI.md corner radius
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp) // UI.md elevation
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp), // Reduced padding for compactness
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header with success indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2E7D32), // Success green
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Volunteer Assigned",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp)) // Reduced spacing for compactness

                    // Volunteer information section
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2E7D32).copy(alpha = 0.15f) // Subtle green background
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp), // Reduced padding for compactness
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Display full name
                            val fullName = slot.assignedVolunteerName ?: "Unknown"

                            Text(
                                text = fullName,
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Roll number and Group in separate yellow cards
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Roll number card
                                val fullRollNo = slot.assignedVolunteerRollNo ?: ""
                                if (fullRollNo.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = YellowAccent,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = fullRollNo,
                                            color = Color.Black,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                // Group card
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = YellowAccent,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = "Group ${slot.assignedVolunteerGroup ?: "Unknown"}",
                                        color = Color.Black,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }

                                // Score card
                                val volunteer = volunteers.find { it.id == slot.assignedVolunteerId }
                                if (volunteer != null && volunteer.interviewScore > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = YellowAccent,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = "Score ${volunteer.interviewScore}",
                                            color = Color.Black,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Subject preferences display
                            // Find the volunteer to get their preferences
                            val assignedVolunteer = volunteers.find { it.id == slot.assignedVolunteerId }
                            if (assignedVolunteer != null && assignedVolunteer.subjectPreferences.isNotEmpty()) {
                                // Show subject preferences
                                Text(
                                    text = "Subject Preferences:",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )

                                // Display preferences in a flow layout
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    maxItemsInEachRow = 4
                                ) {
                                    assignedVolunteer.subjectPreferences.forEachIndexed { index, subject ->
                                        val isAssigned = subject.equals(slot.assignedSubject, ignoreCase = true)
                                        
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isAssigned) YellowAccent else Color.Transparent,
                                            border = if (!isAssigned) BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)) else null,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        ) {
                                            Text(
                                                text = subject,
                                                color = if (isAssigned) Color.Black else Color.White,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (isAssigned) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp)) // Reduced spacing for compactness

                    // Compact slot details section
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "Slot Details: ${slot.schoolName}, ${slot.dayName}, ${slot.timeLabel}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp) // Reduced padding for compactness
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp)) // Reduced spacing for compactness

                    // Available groups section
                    if (slot.availableGroups.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = YellowAccent.copy(alpha = 0.1f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp) // Reduced padding for compactness
                            ) {
                                Text(
                                    text = "Available Groups for this Slot",
                                    color = YellowAccent,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 6.dp) // Reduced spacing
                                )

                                // Helper function to expand group ranges for GroupsFlowLayout
                                fun expandGroupRangesForFlow(groupString: String): List<String> {
                                    val expandedGroups = mutableListOf<String>()
                                    val rangePattern = Regex("^(\\d+)-(\\d+)$")
                                    val matchResult = rangePattern.find(groupString.trim())

                                    if (matchResult != null) {
                                        val startGroup = matchResult.groupValues[1].toIntOrNull()
                                        val endGroup = matchResult.groupValues[2].toIntOrNull()
                                        if (startGroup != null && endGroup != null && startGroup <= endGroup) {
                                            for (group in startGroup..endGroup) {
                                                expandedGroups.add(group.toString())
                                            }
                                        } else {
                                            expandedGroups.add(groupString.trim())
                                        }
                                    } else {
                                        expandedGroups.add(groupString.trim())
                                    }
                                    return expandedGroups
                                }

                                fun expandAllGroupRangesForFlow(groups: List<String>): List<String> {
                                    val allExpandedGroups = mutableListOf<String>()
                                    groups.forEach { groupEntry ->
                                        val groupParts = groupEntry.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        groupParts.forEach { part ->
                                            val expandedGroups = expandGroupRangesForFlow(part)
                                            allExpandedGroups.addAll(expandedGroups)
                                        }
                                    }
                                    return allExpandedGroups.distinct()
                                }

                                // Groups in a flexible flow layout that wraps to new lines (with expanded ranges)
                                val expandedGroupsForFlow = expandAllGroupRangesForFlow(slot.availableGroups)
                                GroupsFlowLayout(
                                    groups = expandedGroupsForFlow,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Original layout for unassigned slots
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (slot.assignedVolunteerId != null) "Volunteer Assigned" else "Assign Volunteer",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "${slot.schoolName} • ${slot.dayName} • ${slot.timeLabel}",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }



            // Slot details
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2D2D2D)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // Available Groups section
                    Text(
                        text = "Available Groups:",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    // Helper function to expand group ranges for UI display
                    fun expandGroupRangesForUI(groupString: String): List<String> {
                        val expandedGroups = mutableListOf<String>()
                        val rangePattern = Regex("^(\\d+)-(\\d+)$")
                        val matchResult = rangePattern.find(groupString.trim())

                        if (matchResult != null) {
                            val startGroup = matchResult.groupValues[1].toIntOrNull()
                            val endGroup = matchResult.groupValues[2].toIntOrNull()
                            if (startGroup != null && endGroup != null && startGroup <= endGroup) {
                                for (group in startGroup..endGroup) {
                                    expandedGroups.add(group.toString())
                                }
                            } else {
                                expandedGroups.add(groupString.trim())
                            }
                        } else {
                            expandedGroups.add(groupString.trim())
                        }
                        return expandedGroups
                    }

                    fun expandAllGroupRangesForUI(groups: List<String>): List<String> {
                        val allExpandedGroups = mutableListOf<String>()
                        groups.forEach { groupEntry ->
                            val groupParts = groupEntry.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            groupParts.forEach { part ->
                                val expandedGroups = expandGroupRangesForUI(part)
                                allExpandedGroups.addAll(expandedGroups)
                            }
                        }
                        return allExpandedGroups.distinct()
                    }

                    // Show expanded groups in flow layout
                    val expandedGroups = expandAllGroupRangesForUI(slot.availableGroups)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(expandedGroups) { group ->
                            Surface(
                                modifier = Modifier.padding(vertical = 2.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = YellowAccent.copy(alpha = 0.2f),
                                contentColor = YellowAccent
                            ) {
                                Text(
                                    text = group,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }


                }
            }

            // Assignment buttons for unassigned slots
            if (slot.assignedVolunteerId == null) {
                val availableVolunteers = volunteers.filter { !it.isAssigned }
                var showManualSelection by remember { mutableStateOf(false) }

                if (availableVolunteers.isEmpty()) {
                    // No volunteers available
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFB71C1C)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No volunteers available for assignment",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Assignment buttons
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp) // UI.md button spacing
                    ) {
                        // Manual assignment button
                        Button(
                            onClick = { showManualSelection = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp), // UI.md touch target
                            colors = ButtonDefaults.buttonColors(
                                containerColor = YellowAccent,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp) // UI.md corner radius
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Assign Volunteer Manually",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge // UI.md button text
                            )
                        }

                        // Automatic assignment button
                        OutlinedButton(
                            onClick = onAssignAutomatic,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp), // UI.md touch target
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = YellowAccent,
                                containerColor = Color.Transparent
                            ),
                            border = BorderStroke(1.dp, YellowAccent),
                            shape = RoundedCornerShape(12.dp) // UI.md corner radius
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = YellowAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Assign Volunteer Automatically",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge // UI.md button text
                            )
                        }
                    }
                }

                // Manual volunteer selection dialog
                if (showManualSelection) {
                    ManualVolunteerSelectionDialog(
                        volunteers = volunteers,
                        slot = slot,
                        onDismiss = { showManualSelection = false },
                        onVolunteerSelected = { volunteer, subject ->
                            onAssignManual(volunteer, subject)
                            showManualSelection = false
                        }
                    )
                }
            }
            }
        }
    }
}

/**
 * Dialog for manual volunteer selection
 */
@Composable
fun ManualVolunteerSelectionDialog(
    volunteers: List<Volunteer>,
    slot: Slot,
    conflictingVolunteerIds: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onVolunteerSelected: (Volunteer, String) -> Unit
) {
    // Helper function to expand group ranges (same as in ScheduleGenerationViewModel)
    fun expandGroupRanges(groupString: String): List<String> {
        val expandedGroups = mutableListOf<String>()
        val rangePattern = Regex("^(\\d+)-(\\d+)$")
        val matchResult = rangePattern.find(groupString.trim())

        if (matchResult != null) {
            val startGroup = matchResult.groupValues[1].toIntOrNull()
            val endGroup = matchResult.groupValues[2].toIntOrNull()
            if (startGroup != null && endGroup != null && startGroup <= endGroup) {
                for (group in startGroup..endGroup) {
                    expandedGroups.add(group.toString())
                }
            } else {
                expandedGroups.add(groupString.trim())
            }
        } else {
            expandedGroups.add(groupString.trim())
        }
        return expandedGroups
    }

    fun expandAllGroupRanges(groups: List<String>): List<String> {
        val allExpandedGroups = mutableListOf<String>()
        groups.forEach { groupEntry ->
            val groupParts = groupEntry.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            groupParts.forEach { part ->
                val expandedGroups = expandGroupRanges(part)
                allExpandedGroups.addAll(expandedGroups)
            }
        }
        return allExpandedGroups.distinct()
    }

    // Filter and sort volunteers alphabetically by name
    // Filter and sort volunteers
    val filteredVolunteers = remember(volunteers, slot, conflictingVolunteerIds) {
        val expandedAvailableGroups = expandAllGroupRanges(slot.availableGroups)
        
        // Identify the highest priority subject that still needs classes
        val targetSubject = slot.subjectPriorities
            .sortedBy { it.priority }
            .firstOrNull { it.classCount > 0 }
            ?.subjectName

        volunteers.filter { volunteer ->
            !volunteer.isAssigned && 
            expandedAvailableGroups.contains(volunteer.group) &&
            !conflictingVolunteerIds.contains(volunteer.id)
        }.sortedWith(
            compareBy<Volunteer> { volunteer ->
                // Primary Sort: Preference Level for Target Subject
                // 0 = 1st pref, 1 = 2nd pref, etc.
                // If not found, assign high number to push to bottom
                if (targetSubject != null) {
                    val idx = volunteer.subjectPreferences.indexOfFirst { it.equals(targetSubject, ignoreCase = true) }
                    if (idx != -1) idx else 999
                } else {
                    0 // No target subject, all equal
                }
            }.thenByDescending { 
                // Secondary Sort: Interview Score (High to Low)
                it.interviewScore 
            }.thenBy { 
                // Tertiary Sort: Name (A-Z)
                it.name 
            }
        )
    }



    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.98f) // Increased horizontal width
                .fillMaxHeight(0.85f) // Take up most of the screen
                .padding(horizontal = 4.dp), // Reduced horizontal padding for more width
            colors = CardDefaults.cardColors(
                containerColor = NeutralCardSurface // UI.md standard card background
            ),
            shape = RoundedCornerShape(20.dp), // Larger corner radius for modern look
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp) // Higher elevation for prominence
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                // Header with background
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = DarkSurface, // UI.md standard surface color
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Select Volunteer",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "${slot.schoolName} • ${slot.dayName} • ${slot.timeLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFB0B0B0),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    Color.White.copy(alpha = 0.1f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Content area with padding
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    // Results count and available groups
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = "${filteredVolunteers.size} available volunteers",
                            color = Color(0xFFB0B0B0),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        // Available groups below the count (show expanded groups)
                        if (slot.availableGroups.isNotEmpty()) {
                            val expandedGroups = expandAllGroupRanges(slot.availableGroups)
                            Text(
                                text = "Groups: ${expandedGroups.joinToString(", ")}",
                                color = YellowAccent,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    // Volunteers list - COMPLETELY DISABLE LazyColumn animations
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        if (filteredVolunteers.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    message = "No volunteers available for this slot",
                                    icon = Icons.Default.PersonOff
                                )
                            }
                        } else {
                            itemsIndexed(filteredVolunteers) { index, volunteer ->
                                ManualVolunteerSelectionItem(
                                    volunteer = volunteer,
                                    slot = slot,
                                    onSubjectClick = { subject -> 
                                        onVolunteerSelected(volunteer, subject) 
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

/**
 * Empty state card for when no volunteers are available
 */
@Composable
fun EmptyStateCard(
    message: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface // UI.md standard surface color
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF666666),
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                color = Color(0xFF888888),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Individual volunteer item for manual selection
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualVolunteerSelectionItem(
    volunteer: Volunteer,
    slot: Slot,
    onSubjectClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = NeutralCardSurface // Static background color
            ),
            shape = RoundedCornerShape(16.dp), // UI.md standard corner radius
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp) // UI.md standard elevation
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Column 1: Name, Roll and Group
                Column(
                    modifier = Modifier.weight(0.4f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Line 1: Full Name
                    Text(
                        text = volunteer.name,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Line 2: Roll and Group in separate yellow cards
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Group card
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = YellowAccent
                        ) {
                            Text(
                                text = "Gp ${volunteer.group}",
                                color = Color.Black,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Roll number card
                        val rollLast4 = volunteer.rollNo.takeLast(4)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = YellowAccent
                        ) {
                            Text(
                                text = rollLast4,
                                color = Color.Black,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    
                    // Line 3: Class count and Score in one line
                    Text(
                        text = "Class=${volunteer.classCount}  Scr=${volunteer.interviewScore}",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }

                // Column 2: Subject preferences - show all with valid ones clickable, invalid dimmed
                if (volunteer.subjectPreferences.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.weight(0.55f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        maxItemsInEachRow = 4
                    ) {
                        volunteer.subjectPreferences.forEach { subject ->
                            // Check if this subject is valid for the slot
                            // Check if this subject is valid for the slot AND has classes left
                            val slotSubject = slot.subjectPriorities.find { 
                                it.subjectName.equals(subject, ignoreCase = true) 
                            }
                            val isValid = slotSubject != null && slotSubject.classCount > 0
                            
                            Surface(
                                color = Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isValid) Color.White else Color.Gray.copy(alpha = 0.3f)),
                                modifier = if (isValid) {
                                    Modifier.clickable { onSubjectClick(subject) }
                                } else {
                                    Modifier
                                }
                            ) {
                                Text(
                                    text = subject.take(3),
                                    color = if (isValid) Color.White else Color.Gray.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
}

/**
 * Dialog for finishing the schedule
 */
@Composable
fun FinishDialog(
    onDismiss: () -> Unit,
    onFinish: (presetName: String) -> Unit
) {
    var presetName by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkSurface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Text(
                    text = "Finish Schedule",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Description
                Text(
                    text = "This will save your schedule and automatically create volunteer presets for each teaching slot preset used.",
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Additional info about automatic preset creation
                Text(
                    text = "• Assigned volunteers: Separate preset for each teaching slot preset\n• Unassigned volunteers: Custom preset name below",
                    color = Color.Gray.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Preset name
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Unassigned Volunteers Preset Name",
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = YellowAccent,
                            cursorColor = YellowAccent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        placeholder = { Text("Enter preset name") },
                        singleLine = true
                    )
                }

                // Error message
                if (showError) {
                    Text(
                        text = "Please enter preset name",
                        color = Color.Red,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.Gray)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (presetName.isBlank()) {
                                showError = true
                            } else {
                                onFinish(presetName)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowAccent,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Finish", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Custom flow layout for groups that wraps to new lines based on available width
 */
@Composable
fun GroupsFlowLayout(
    groups: List<String>,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val maxWidth = maxWidth
        val spacing = 6.dp
        val itemPadding = 10.dp

        // Estimate item width (this is approximate - in production you'd measure actual text)
        val density = LocalDensity.current
        val estimatedItemWidth = with(density) {
            // Rough estimation: 8dp per character + padding
            groups.maxOfOrNull { group ->
                (group.length * 8).dp + (itemPadding * 2)
            } ?: 60.dp
        }

        // Calculate how many items can fit per row
        val itemsPerRow = ((maxWidth + spacing) / (estimatedItemWidth + spacing)).toInt().coerceAtLeast(1)

        Column {
            groups.chunked(itemsPerRow).forEachIndexed { rowIndex, rowGroups ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    rowGroups.forEach { group ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = YellowAccent.copy(alpha = 0.2f),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text(
                                text = group,
                                color = YellowAccent,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = itemPadding, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Add vertical spacing between rows (except for the last row)
                if (rowIndex < groups.chunked(itemsPerRow).size - 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}