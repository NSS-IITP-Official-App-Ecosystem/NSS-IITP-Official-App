package com.phad.chatapp.features.scheduling.schedule

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    var filterAssigned by remember { mutableStateOf(false) }
    var filterUnassigned by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var sortByGroup by remember { mutableStateOf(false) }

    // Get all unique groups
    val groups = volunteers.map { it.group }.distinct().sortedWith(compareBy {
        // Try to parse as int for numerical sorting, fall back to string if not a number
        try {
            it.toInt()
        } catch (e: NumberFormatException) {
            Int.MAX_VALUE // Non-numeric groups will be placed at the end
        }
    })

    // Filter volunteers based on criteria
    val filteredVolunteers = volunteers.filter { volunteer ->
        val assignmentMatch = when {
            filterAssigned && !filterUnassigned -> volunteer.isAssigned
            !filterAssigned && filterUnassigned -> !volunteer.isAssigned
            else -> true
        }

        val groupMatch = selectedGroup?.let { volunteer.group == it } ?: true

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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
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

                        // Filters - Optimized for single row layout
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp), // UI.md element spacing
                            horizontalArrangement = Arrangement.spacedBy(6.dp) // Reduced spacing for better fit
                        ) {
                            // Assignment filters
                            FilterChip(
                                selected = filterAssigned,
                                onClick = { filterAssigned = !filterAssigned },
                                label = {
                                    Text(
                                        "Assigned",
                                        style = MaterialTheme.typography.bodySmall, // Smaller text for better fit
                                        maxLines = 1,
                                        fontSize = 12.sp // Explicit smaller font size
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f) // Equal weight distribution
                                    .heightIn(min = 48.dp), // UI.md touch target maintained
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YellowAccent,
                                    selectedLabelColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp) // UI.md corner radius
                            )

                            FilterChip(
                                selected = filterUnassigned,
                                onClick = { filterUnassigned = !filterUnassigned },
                                label = {
                                    Text(
                                        "Unassigned",
                                        style = MaterialTheme.typography.bodySmall, // Smaller text for better fit
                                        maxLines = 1,
                                        fontSize = 12.sp // Explicit smaller font size
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f) // Equal weight distribution
                                    .heightIn(min = 48.dp), // UI.md touch target maintained
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YellowAccent,
                                    selectedLabelColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp) // UI.md corner radius
                            )

                            // Sort by group filter
                            FilterChip(
                                selected = sortByGroup,
                                onClick = { sortByGroup = !sortByGroup },
                                label = {
                                    Text(
                                        "Sort",
                                        style = MaterialTheme.typography.bodySmall, // Smaller text for better fit
                                        maxLines = 1,
                                        fontSize = 12.sp // Explicit smaller font size
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f) // Equal weight distribution
                                    .heightIn(min = 48.dp), // UI.md touch target maintained
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YellowAccent,
                                    selectedLabelColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp) // UI.md corner radius
                            )
                        }

                        // Group filter dropdown
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp) // UI.md element spacing
                        ) {
                            var expanded by remember { mutableStateOf(false) }

                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp), // UI.md touch target
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White,
                                    containerColor = DarkSurface
                                ),
                                border = BorderStroke(1.dp, YellowAccent.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp) // UI.md corner radius
                            ) {
                                Text(
                                    selectedGroup ?: "All Groups",
                                    style = MaterialTheme.typography.bodyLarge // UI.md interactive text
                                )
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier
                                    .background(NeutralCardSurface) // UI.md card background
                                    .widthIn(min = 170.dp) // UI.md minimum width for visibility
                            ) {
                                // "All Groups" option
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "All Groups",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    onClick = {
                                        selectedGroup = null
                                        expanded = false
                                    },
                                    leadingIcon = if (selectedGroup == null) {
                                        { Icon(Icons.Default.Check, null, tint = YellowAccent) }
                                    } else null
                                )

                                HorizontalDivider(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = DarkSurface
                                )

                                // Group options
                                groups.forEach { group ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Group $group",
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        },
                                        onClick = {
                                            selectedGroup = group
                                            expanded = false
                                        },
                                        leadingIcon = if (selectedGroup == group) {
                                            { Icon(Icons.Default.Check, null, tint = YellowAccent) }
                                        } else null
                                    )
                                }
                            }
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
                    .padding(20.dp) // UI.md card internal padding
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Display first name only
                        val firstName = volunteer.name.split(" ").firstOrNull() ?: volunteer.name
                        Text(
                            text = firstName,
                            color = Color.White, // UI.md primary text color
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyLarge // UI.md interactive text
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Display group and last 4 characters of roll number
                        val rollLast4 = volunteer.rollNo.takeLast(4)
                        Text(
                            text = "Group ${volunteer.group} • $rollLast4",
                            color = Color(0xFFB0B0B0), // UI.md secondary text color
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Assignment status badge with UI.md specifications
                    if (volunteer.isAssigned) {
                        Surface(
                            modifier = Modifier.padding(start = 12.dp),
                            shape = RoundedCornerShape(12.dp), // UI.md badge corner radius
                            color = Color(0xFF2E7D32), // Success green
                            shadowElevation = 1.dp // UI.md badge elevation
                        ) {
                            Text(
                                text = "Assigned",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp) // UI.md badge padding
                            )
                        }
                    } else {
                        Surface(
                            modifier = Modifier.padding(start = 12.dp),
                            shape = RoundedCornerShape(12.dp), // UI.md badge corner radius
                            color = Color(0xFF9E9E9E), // UI.md neutral gray
                            shadowElevation = 1.dp // UI.md badge elevation
                        ) {
                            Text(
                                text = "Unassigned",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp) // UI.md badge padding
                            )
                        }
                    }
                }

                // Assignment details if assigned
                if (volunteer.isAssigned && volunteer.assignedSlot != null) {
                    val slot = volunteer.assignedSlot!!

                    Spacer(modifier = Modifier.height(12.dp)) // UI.md element spacing

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurface.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "${slot.schoolName} • ${slot.dayName} • ${slot.timeLabel}",
                            color = Color(0xFFB0B0B0), // UI.md secondary text color
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
}

/**
 * Panel for assigning a volunteer to a slot
 */
@Composable
fun AssignmentPanel(
    slot: Slot,
    volunteers: List<Volunteer>,
    onAssignManual: (Volunteer) -> Unit,
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
                            // Display full name and full roll number
                            val fullName = slot.assignedVolunteerName ?: "Unknown"
                            val fullRollNo = slot.assignedVolunteerRollNo ?: ""

                            Text(
                                text = fullName,
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            if (fullRollNo.isNotEmpty()) {
                                Text(
                                    text = fullRollNo,
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp)) // Reduced spacing

                            // Group badge
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = YellowAccent,
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                Text(
                                    text = "Group ${slot.assignedVolunteerGroup ?: "Unknown"}",
                                    color = Color.Black,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp) // Reduced padding
                                )
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
                        onVolunteerSelected = { volunteer ->
                            onAssignManual(volunteer)
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
    onDismiss: () -> Unit,
    onVolunteerSelected: (Volunteer) -> Unit
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

    // Filter and sort volunteers alphabetically by name (with group range expansion)
    val filteredVolunteers = remember(volunteers, slot) {
        val expandedAvailableGroups = expandAllGroupRanges(slot.availableGroups)
        volunteers.filter { volunteer ->
            !volunteer.isAssigned && expandedAvailableGroups.contains(volunteer.group)
        }.sortedBy { it.name } // Alphabetical sorting by name
    }



    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f) // Take up most of the screen
                .padding(horizontal = 16.dp), // UI.md screen margins
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
                            .padding(horizontal = 24.dp, vertical = 20.dp), // UI.md header padding
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Select Volunteer",
                                style = MaterialTheme.typography.headlineSmall, // Larger header text
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "${slot.schoolName} • ${slot.dayName} • ${slot.timeLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFB0B0B0), // UI.md secondary text color
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp) // UI.md touch target
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
                                    onClick = { onVolunteerSelected(volunteer) }
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
@Composable
fun ManualVolunteerSelectionItem(
    volunteer: Volunteer,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
            colors = CardDefaults.cardColors(
                containerColor = NeutralCardSurface // Static background color
            ),
            shape = RoundedCornerShape(16.dp), // UI.md standard corner radius
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp) // UI.md standard elevation
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp) // Better padding
            ) {
                // Display first name only
                val firstName = volunteer.name.split(" ").firstOrNull() ?: volunteer.name
                Text(
                    text = firstName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = YellowAccent, // UI.md standard accent color for badges
                        shape = RoundedCornerShape(8.dp) // Slightly larger corner radius for modern look
                    ) {
                        Text(
                            text = "Group ${volunteer.group}",
                            color = Color.Black, // UI.md standard: black text on yellow background
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp) // Better padding
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Display last 4 characters of roll number
                    val rollLast4 = volunteer.rollNo.takeLast(4)
                    Text(
                        text = rollLast4,
                        color = Color(0xFFB0B0B0), // UI.md secondary text color
                        style = MaterialTheme.typography.bodyMedium
                    )
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