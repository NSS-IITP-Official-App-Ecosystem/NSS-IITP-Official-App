package com.phad.chatapp.features.scheduling.schedule


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phad.chatapp.features.scheduling.models.SubjectAllocation
import com.phad.chatapp.features.scheduling.ui.theme.DarkSurface
import com.phad.chatapp.features.scheduling.ui.theme.NeutralCardSurface
import com.phad.chatapp.features.scheduling.ui.theme.YellowAccent

/**
 * TFV Grid View component - displays a grid of slots with TFV values
 */
@Composable
fun TFVGridView(
    school: School,
    onSlotClick: (Slot) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // School header - more compact
        Text(
            text = "School: ${school.name}",
            style = MaterialTheme.typography.titleSmall,
            color = YellowAccent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Display each day as a separate grid
        school.days.forEach { day ->
            DayGrid(
                day = day,
                onSlotClick = onSlotClick
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Day Grid component - displays a grid of slots for a specific day
 */
@Composable
fun DayGrid(
    day: Day,
    onSlotClick: (Slot) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Day header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF333333),
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
        ) {
            Text(
                text = day.name,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        // Slots grid
        if (day.slots.isEmpty()) {
            // Show message when no slots are defined
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No slots defined for this day",
                    color = Color.Gray
                )
            }
        } else {
            // Display slots in a grid layout
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NeutralCardSurface // UI.md preferred card background
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Calculate how many slots per row based on screen width
                    // Using a simple approach with rows
                    val chunkedSlots = day.slots.chunked(2) // Display 2 items per row

                    chunkedSlots.forEach { rowSlots ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowSlots.forEach { slot ->
                                Box(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    TFVGridCell(
                                        slot = slot,
                                        onClick = { onSlotClick(slot) }
                                    )
                                }
                            }

                            // If the row doesn't have enough items to fill it, add empty boxes
                            repeat(2 - rowSlots.size) {
                                Box(modifier = Modifier.weight(1f)) {}
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * TFV Grid Cell component - displays a single slot with TFV information
 */
@Composable
fun TFVGridCell(
    slot: Slot,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (slot.assignedVolunteerId != null) {
                Color(0xFF2E7D32) // Green background for assigned slots
            } else {
                // Color based on TFV - lower TFV = more urgent (warmer color)
                when {
                    slot.tfv <= 3 -> Color(0xFFB71C1C) // Red
                    slot.tfv <= 5 -> Color(0xFF4E342E) // Brown
                    slot.tfv <= 10 -> Color(0xFF37474F) // Blue-gray
                    else -> Color(0xFF263238) // Dark blue-gray
                }
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Time label
            Text(
                text = slot.timeLabel,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // TFV or assigned volunteer
            if (slot.assignedVolunteerId != null) {
                // Display full name and full roll number
                val fullName = slot.assignedVolunteerName ?: "Volunteer"
                val fullRollNo = slot.assignedVolunteerRollNo ?: ""

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = fullName,
                        style = MaterialTheme.typography.bodySmall, // Smaller font
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (fullRollNo.isNotEmpty()) {
                        Text(
                            text = fullRollNo,
                            style = MaterialTheme.typography.labelSmall, // Even smaller font for roll number
                            color = Color.White.copy(alpha = 0.8f), // Slightly transparent
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                // Display TFV badge for unassigned slots
                Surface(
                    color = when {
                        slot.tfv <= 3 -> Color(0xFFFF5252).copy(alpha = 0.8f) // Red
                        slot.tfv <= 5 -> Color(0xFFFFB74D).copy(alpha = 0.8f) // Orange
                        slot.tfv <= 10 -> Color(0xFFFFEE58).copy(alpha = 0.8f) // Yellow
                        else -> Color(0xFF66BB6A).copy(alpha = 0.8f) // Green
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "${slot.tfv}",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                // Helper function to expand group ranges for display
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

                // Simplified groups display with expanded ranges
                if (slot.availableGroups.isNotEmpty()) {
                    val expandedGroups = expandAllGroupRanges(slot.availableGroups)
                    Text(
                        text = "Groups: " + expandedGroups.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * TFV Display for the whole schedule
 */
@Composable
fun TFVScheduleView(
    schools: List<School>,
    onSlotClick: (Slot) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp), // UI.md card spacing
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = 100.dp // Extra padding to avoid bottom navigation bar
        )
    ) {
        // Process each school
        items(schools) { school ->
            SchoolCard(
                school = school,
                onSlotClick = onSlotClick
            )
        }
    }
}

/**
 * School card component
 */
@Composable
fun SchoolCard(
    school: School,
    onSlotClick: (Slot) -> Unit
) {
    var showSubjectInfoDialog by remember { mutableStateOf(false) }
    
    // Extract subjects from first slot (all slots have same subjects for a school)
    val subjects = school.days.firstOrNull()?.slots?.firstOrNull()?.subjectPriorities ?: emptyList()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeutralCardSurface, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // School name header with info button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = school.name,
                style = MaterialTheme.typography.titleLarge,
                color = YellowAccent,
                fontWeight = FontWeight.Bold
            )
            
            // Show info button only if subjects are available
            if (subjects.isNotEmpty()) {
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
        }

        // Convert the school's days and slots into a table structure
        TableScheduleView(
            school = school,
            onSlotClick = onSlotClick
        )
    }
    
    // Subject info dialog
    if (showSubjectInfoDialog) {
        SchoolSubjectInfoDialog(
            schoolName = school.name,
            subjects = subjects,
            onDismiss = { showSubjectInfoDialog = false }
        )
    }
}

@Composable
fun TableScheduleView(
    school: School,
    onSlotClick: (Slot) -> Unit
) {
    // First, organize all unique slot times across all days
    val slotMap = mutableMapOf<Int, String>() // Map of slotIndex to timeLabel
    school.days.forEach { day ->
        day.slots.forEach { slot ->
            slotMap[slot.slotIndex] = slot.timeLabel
        }
    }
    
    // Sort slots by their slotIndex and get the corresponding timeLabels
    val allSlotTimes = slotMap.entries.sortedBy { it.key }.map { it.value }

    // Shared scroll state for synchronized horizontal scrolling
    val tableScrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Header row with synchronized horizontal scrolling
        Row(
            modifier = Modifier
                .wrapContentWidth() // Only take up space needed for content
                .background(Color(0xFF333333), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fixed day column header (doesn't scroll)
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .padding(4.dp)
            ) {
                Text(
                    text = "Day",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Horizontally scrollable slot headers using shared scroll state
            Row(
                modifier = Modifier
                    .wrapContentWidth() // Only take up space needed for content
                    .horizontalScroll(tableScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Slot column headers with fixed dimensions
                allSlotTimes.forEach { timeLabel ->
                    Box(
                        modifier = Modifier
                            .width(80.dp) // Fixed width for consistent sizing
                            .padding(horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = timeLabel,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }

        // Day rows
        school.days.forEachIndexed { dayIndex, day ->
            TableRow(
                day = day,
                allSlotTimes = allSlotTimes,
                onSlotClick = onSlotClick,
                isLastRow = dayIndex == school.days.size - 1,
                tableScrollState = tableScrollState // Pass scroll state for synchronization
            )
        }
    }
}

/**
 * Table row component
 */
@Composable
fun TableRow(
    day: Day,
    allSlotTimes: List<String>,
    onSlotClick: (Slot) -> Unit,
    isLastRow: Boolean,
    tableScrollState: ScrollState
) {
    Row(
        modifier = Modifier
            .wrapContentWidth() // Only take up space needed for content
            .background(
                NeutralCardSurface,
                if (isLastRow) RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp) else RoundedCornerShape(0.dp)
            )
            .padding(vertical = 2.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Fixed day name column (doesn't scroll)
        Box(
            modifier = Modifier
                .width(60.dp)
                .padding(4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = day.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Horizontally scrollable slot cells using shared scroll state
        Row(
            modifier = Modifier
                .wrapContentWidth() // Only take up space needed for content
                .horizontalScroll(tableScrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Slot cells for this day with fixed dimensions
            allSlotTimes.forEach { timeLabel ->
                // Find slot for this time if it exists
                val slot = day.slots.find { it.timeLabel == timeLabel }

                if (slot != null) {
                    TableCell(
                        slot = slot,
                        modifier = Modifier
                            .width(80.dp) // Fixed width for consistent sizing
                            .padding(horizontal = 2.dp),
                        onClick = { onSlotClick(slot) }
                    )
                } else {
                    // Empty cell with fixed dimensions
                    Box(
                        modifier = Modifier
                            .width(80.dp) // Fixed width for consistent sizing
                            .height(48.dp)
                            .padding(horizontal = 2.dp)
                            .background(Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun TableCell(
    slot: Slot,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Simplified color scheme with single TFV color
    val backgroundColor = when {
        slot.assignedVolunteerId != null -> Color(0xFF2E7D32) // Rich green for assigned slots
        else -> Color(0xFF1A1A1A) // Dark background for all unassigned slots
    }

    val badgeColor = when {
        slot.assignedVolunteerId != null -> Color(0xFF4CAF50) // Lighter green for badge
        else -> Color(0xFFFFD600) // Single yellow color for all TFV badges
    }

    Card(
        modifier = modifier
            .height(52.dp) // Slightly taller for better touch target
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(8.dp), // Rounded corners for modern look
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (slot.assignedVolunteerId == null) {
                // Enhanced TFV badge with better styling
                Surface(
                    color = badgeColor,
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = "${slot.tfv}",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 6.dp
                        )
                    )
                }
            } else {
                // Display first name and last 4 digits of roll number for assigned slots
                val firstName = slot.assignedVolunteerName?.split(" ")?.firstOrNull() ?: "Volunteer"
                val rollLast4 = slot.assignedVolunteerRollNo?.takeLast(4) ?: ""

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = firstName,
                        style = MaterialTheme.typography.bodySmall, // Smaller font
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (rollLast4.isNotEmpty()) {
                        Text(
                            text = rollLast4,
                            style = MaterialTheme.typography.labelSmall, // Even smaller font for roll number
                            color = Color.White.copy(alpha = 0.8f), // Slightly transparent
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ColorLegendItem(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, shape = RoundedCornerShape(2.dp))
        )
        Text(
            text = text,
            color = Color.LightGray,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp
        )
    }
}

/**
 * Dialog displaying subject information for a school in Create Schedule screen
 */
@Composable
fun SchoolSubjectInfoDialog(
    schoolName: String,
    subjects: List<SubjectAllocation>,
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
                        text = "Classes",
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

                // Sort subjects by:
                // 1. Available classes (count > 0) first
                // 2. Then by priority (ascending, 1 = highest)
                val sortedSubjects = subjects.sortedWith(
                    compareBy(
                        { if (it.classCount > 0) 0 else 1 }, // Available subjects first
                        { it.priority } // Then by priority (1 = highest)
                    )
                )

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
                        items(sortedSubjects) { subject ->
                            SchoolSubjectInfoItem(
                                subjectName = subject.subjectName,
                                classCount = subject.classCount,
                                priority = subject.priority
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual subject info item for school subjects
 */
@Composable
fun SchoolSubjectInfoItem(
    subjectName: String,
    classCount: Int,
    priority: Int
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
                    text = "Priority: $priority",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0B0B0)
                )
            }

            // Class count badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (classCount > 0) YellowAccent else Color(0xFF9E9E9E)
            ) {
                Text(
                    text = classCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}