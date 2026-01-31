package com.phad.chatapp.features.scheduling.schedule

import android.util.Log

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.features.scheduling.ui.theme.DarkBackground
import com.phad.chatapp.features.scheduling.ui.theme.DarkSurface
import com.phad.chatapp.features.scheduling.ui.theme.NeutralCardSurface
import com.phad.chatapp.features.scheduling.ui.theme.SurfaceElevated
import com.phad.chatapp.features.scheduling.ui.theme.TealAccent
import com.phad.chatapp.features.scheduling.ui.theme.YellowAccent
import com.phad.chatapp.features.scheduling.ui.theme.ErrorRed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "TeachingSlotsOptionsScreen"
private const val TEACHING_SLOT_PRESETS_COLLECTION = "teachingSlotPresets"
private const val VOLUNTEER_AVAILABILITY_COLLECTION = "volunteerAvailability"

// Natural sorting function to handle numbers correctly (AM 9B before AM 10G)
private fun naturalSortKey(text: String): String {
    return text.replace(Regex("\\d+")) { matchResult ->
        matchResult.value.padStart(10, '0')
    }
}

// Model classes for UI display - renamed to avoid conflict
data class TeachingSlotPresetItem(
    val id: String,
    val name: String,
    val createdAt: Long = 0,
    val columnCount: Int = 0,
    val dayCount: Int = 0,
    var hasAvailabilityData: Boolean = false,
    val groupFrequencies: Map<String, Int> = emptyMap() // Map of group number to frequency count
)

// Function to delete availability data
suspend fun deleteAvailabilityData(
    presetId: String,
    presetName: String,
    onSuccess: (String, String) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection(TEACHING_SLOT_PRESETS_COLLECTION).document(presetId)

        // Delete the availability field using FieldValue.delete()
        docRef.update("availability", com.google.firebase.firestore.FieldValue.delete()).await()
        onSuccess(presetId, presetName)

    } catch (e: Exception) {
        Log.e(TAG, "Error deleting availability data", e)
        onError("Error: ${e.message}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachingSlotsOptionsScreen(
    navController: NavController,
    destination: String? = null
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var presets by remember { mutableStateOf<List<TeachingSlotPresetItem>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()





    // Load teaching slot presets from Firestore
    LaunchedEffect(Unit) {
        fetchTeachingSlotsWithAvailabilityData(
            onSuccess = { fetchedPresets ->
                presets = fetchedPresets
                isLoading = false
            },
            onError = { error ->
                errorMessage = error
                isLoading = false
            }
        )
    }



    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground) // Using UI.md theme color instead of hardcoded black
    ) {
        Scaffold(
            topBar = {
                // Header layout matching VolunteerPresetsScreen
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp) // TTW UI plan: 24dp icon size
                        )
                    }

                    // Title
                    Text(
                        text = "Teaching Slots Presets",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                }
            },
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp, vertical = 4.dp) // Reduced vertical padding to minimize space between header and cards
            ) {



                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = YellowAccent, // UI.md theme color
                                    modifier = Modifier.size(48.dp), // UI.md specification
                                    strokeWidth = 4.dp
                                )
                            }
                        }

                        errorMessage != null -> {
                            Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    shape = RoundedCornerShape(16.dp), // UI.md standard
                                    colors = CardDefaults.cardColors(
                                        containerColor = DarkSurface // UI.md theme color
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp), // UI.md card padding
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Error: $errorMessage",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = ErrorRed, // UI.md theme color
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(24.dp))

                                        // Using StandardButton component (need to import)
                                        Button(
                                            onClick = { navController.navigateUp() },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = YellowAccent // UI.md theme color
                                            ),
                                            shape = RoundedCornerShape(10.dp), // UI.md button corner radius
                                            modifier = Modifier.heightIn(min = 44.dp) // UI.md button height
                                        ) {
                                            Text(
                                                "Go Back",
                                                color = Color.Black,
                                                fontWeight = FontWeight.Medium,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                }
                        }

                        presets.isEmpty() -> {
                            Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    shape = RoundedCornerShape(16.dp), // UI.md standard
                                    colors = CardDefaults.cardColors(
                                        containerColor = DarkSurface // UI.md theme color
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp), // UI.md card padding
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "No teaching slot presets found",
                                            style = MaterialTheme.typography.titleMedium, // UI.md typography
                                            color = Color.White,
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = "Create a new preset to get started",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.LightGray,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                        }

                        else -> {
                            LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(16.dp), // UI.md card spacing
                                    contentPadding = PaddingValues(vertical = 4.dp) // Reduced vertical padding to minimize space between header and cards
                                ) {
                                    items(presets.size) { index ->
                                        val preset = presets[index]

                                        EnhancedPresetCard(
                                            preset = preset,
                                            onClick = {
                                                if (destination == "setAvailability") {
                                                    // Navigate to SetAvailabilityScreen with preset ID
                                                    navController.navigate("setAvailability/${preset.id}")
                                                } else {
                                                    // Navigate to edit the preset
                                                    navController.navigate("createTeachingSlots/${preset.id}")
                                                }
                                            },
                                            onDeleteClick = if (preset.hasAvailabilityData) {
                                                {
                                                    coroutineScope.launch {
                                                        deleteAvailabilityData(
                                                            preset.id,
                                                            preset.name,
                                                            onSuccess = { presetId, presetName ->
                                                                // Update local list
                                                                presets = presets.map {
                                                                    if (it.id == presetId) it.copy(hasAvailabilityData = false) else it
                                                                }
                                                                coroutineScope.launch {
                                                                    snackbarHostState.showSnackbar("Availability data deleted for $presetName")
                                                                }
                                                            },
                                                            onError = { error ->
                                                                coroutineScope.launch {
                                                                    snackbarHostState.showSnackbar(error)
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            } else null,
                                            onEditClick = {
                                                // Navigate to edit the preset (same as main click for now)
                                                navController.navigate("createTeachingSlots/${preset.id}")
                                            }
                                        )
                                    }

                                    // Add bottom padding for better UX
                                    item {
                                        Spacer(modifier = Modifier.height(80.dp))
                                    }
                                }
                        }
                    }
            }
        }
    }
}

// Helper composable to display group frequency information
@Composable
fun GroupFrequencyDisplay(
    groupFrequencies: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    if (groupFrequencies.isNotEmpty()) {
        // Filter out groups with zero frequency and sort numerically
        val nonZeroGroups = groupFrequencies.entries
            .filter { it.value > 0 }
            .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }

        if (nonZeroGroups.isNotEmpty()) {
            // Display groups in a flow layout matching the reference image
            // Hard limit of 5 groups per line for consistency
            val maxGroupsPerLine = 5
            val chunkedGroups = nonZeroGroups.chunked(maxGroupsPerLine)

            Column(modifier = modifier) {
                chunkedGroups.forEachIndexed { rowIndex, lineGroups ->
                    // Use left-aligned Row with consistent spacing to fix layout issues
                    Row(
                        modifier = Modifier.wrapContentWidth(Alignment.Start), // Left-align the row content
                        horizontalArrangement = Arrangement.spacedBy(6.dp) // Consistent 6dp spacing between chips
                    ) {
                        lineGroups.forEach { (group, count) ->
                            // Group frequency chip - matching VolunteerPresetsScreen styling
                            Surface(
                                shape = RoundedCornerShape(8.dp), // TTW UI plan: 8dp corner radius
                                color = YellowAccent, // TTW UI plan: YellowAccent background
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .width(60.dp) // TTW UI plan: 60dp width
                                    .height(28.dp) // TTW UI plan: 28dp height
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center, // TTW UI plan: centered text
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = "Gp $group: $count",
                                        style = MaterialTheme.typography.bodySmall, // Matching VolunteerPresetsScreen
                                        color = Color.Black, // TTW UI plan: black text on yellow background
                                        fontWeight = FontWeight.Medium, // Matching VolunteerPresetsScreen
                                        textAlign = TextAlign.Center, // TTW UI plan: centered alignment
                                        maxLines = 1, // Ensure single line
                                        overflow = TextOverflow.Ellipsis // Handle overflow gracefully
                                    )
                                }
                            }
                        }
                    }

                    // Add spacing between rows (except after the last row)
                    if (rowIndex < chunkedGroups.size - 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedPresetCard(
    preset: TeachingSlotPresetItem,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    onEditClick: (() -> Unit)? = null
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp), // UI.md standard corner radius
        colors = CardDefaults.cardColors(
            containerColor = NeutralCardSurface // UI.md preferred card background
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp) // UI.md standard elevation
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp) // UI.md card padding (20dp-24dp)
        ) {
            // Header row with preset name and delete button space (always consistent layout)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Preset name
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium, // UI.md typography for card titles
                    color = Color.White,
                    fontWeight = FontWeight.Bold, // UI.md font weight for titles
                    modifier = Modifier.weight(1f)
                )

                // Always reserve space for delete button to maintain consistent card sizing
                Box(
                    modifier = Modifier.size(36.dp), // UI.md small icon container size
                    contentAlignment = Alignment.Center
                ) {
                    // Only show delete button if onDeleteClick is provided
                    if (onDeleteClick != null) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .size(36.dp) // UI.md small icon container for card delete buttons
                                .background(
                                    color = ErrorRed.copy(alpha = 0.2f), // UI.md error color
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete availability data",
                                tint = ErrorRed, // UI.md error color for destructive actions
                                modifier = Modifier.size(18.dp) // UI.md small icon size for card delete buttons
                            )
                        }
                    }
                    // If no delete button, the Box still reserves the 36dp space but shows nothing
                }
            }

            // Group frequency information - only show if availability data exists and groups are present
            if (preset.hasAvailabilityData && preset.groupFrequencies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp)) // UI.md element spacing

                GroupFrequencyDisplay(
                    groupFrequencies = preset.groupFrequencies,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// Backward compatibility - simple PresetCard that delegates to EnhancedPresetCard
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetCard(
    preset: TeachingSlotPresetItem,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    onEditClick: (() -> Unit)? = null
) {
    EnhancedPresetCard(
        preset = preset,
        onClick = onClick,
        onDeleteClick = onDeleteClick,
        onEditClick = onEditClick
    )
}

// Helper function to expand group ranges and handle both old and new formats
// Examples:
// "4-8" -> ["4", "5", "6", "7", "8"] (old compressed format)
// "1,2,3,4" -> ["1", "2", "3", "4"] (new expanded format)
// "5" -> ["5"] (single number)
// "10-12" -> ["10", "11", "12"] (old compressed format)
private fun expandGroupRanges(groupString: String): List<String> {
    val expandedGroups = mutableListOf<String>()

    // Check if this is a range pattern (e.g., "4-8", "1-3") - old format
    val rangePattern = Regex("^(\\d+)-(\\d+)$")
    val matchResult = rangePattern.find(groupString.trim())

    if (matchResult != null) {
        // This is a range, expand it (backward compatibility)
        val startGroup = matchResult.groupValues[1].toIntOrNull()
        val endGroup = matchResult.groupValues[2].toIntOrNull()

        if (startGroup != null && endGroup != null && startGroup <= endGroup) {
            // Expand the range into individual groups
            for (group in startGroup..endGroup) {
                expandedGroups.add(group.toString())
            }
        } else {
            // Invalid range, treat as single group
            expandedGroups.add(groupString.trim())
        }
    } else {
        // Not a range, treat as individual group (handles both single numbers and new expanded format)
        expandedGroups.add(groupString.trim())
    }

    return expandedGroups
}

// Helper function to calculate group frequencies from availability map
fun calculateGroupFrequenciesFromMap(availabilityMap: Map<String, Map<String, String>>): Map<String, Int> {
    val groupFrequencies = mutableMapOf<String, Int>()

    try {
        // Iterate through each day's availability
        for ((_, dayAvailability) in availabilityMap) {
            // Iterate through each slot in the day
            for ((_, groupsString) in dayAvailability) {
                if (groupsString.isNotEmpty()) {
                    // Split the groups string and process each entry
                    val groupEntries = groupsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                    for (groupEntry in groupEntries) {
                        // Expand ranges and count each individual group
                        val expandedGroups = expandGroupRanges(groupEntry)
                        for (group in expandedGroups) {
                            if (group.isNotEmpty()) {
                                groupFrequencies[group] = (groupFrequencies[group] ?: 0) + 1
                            }
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error calculating group frequencies", e)
    }

    return groupFrequencies
}

// Function to fetch teaching slot presets from Firestore
suspend fun fetchTeachingSlotsWithAvailabilityData(
    onSuccess: (List<TeachingSlotPresetItem>) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val db = FirebaseFirestore.getInstance()
        val result = db.collection(TEACHING_SLOT_PRESETS_COLLECTION)
            .get()
            .await()

        val presetList = mutableListOf<TeachingSlotPresetItem>()

        for (document in result.documents) {
            try {
                val id = document.id
                val name = document.getString("presetName") ?: "Unnamed Preset"
                val createdAt = document.getLong("createdAt") ?: 0L

                // Get column names for slot count
                val columnNames = document.get("columnNames") as? List<String> ?: emptyList()

                // Get schedule data for day count
                val scheduleData = document.get("schedule") as? List<Map<String, Any>> ?: emptyList()

                // Check for availability data directly in the document
                val availabilityMap = document.get("availability") as? Map<String, Map<String, String>>
                val hasAvailabilityData = availabilityMap != null && availabilityMap.isNotEmpty()

                // Calculate group frequencies if availability data exists
                val groupFrequencies = if (hasAvailabilityData && availabilityMap != null) {
                    calculateGroupFrequenciesFromMap(availabilityMap)
                } else {
                    emptyMap()
                }

                // Create preset item
                val presetItem = TeachingSlotPresetItem(
                    id = id,
                    name = name,
                    createdAt = createdAt,
                    columnCount = columnNames.size,
                    dayCount = scheduleData.size,
                    hasAvailabilityData = hasAvailabilityData,
                    groupFrequencies = groupFrequencies
                )

                presetList.add(presetItem)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing document: ${document.id}", e)
            }
        }

        // Sort naturally by preset name (handles numbers correctly: AM 9B before AM 10G)
        presetList.sortWith(compareBy { naturalSortKey(it.name) })

        onSuccess(presetList)
    } catch (e: Exception) {
        Log.e(TAG, "Error fetching teaching slot presets", e)
        onError("Failed to load presets: ${e.message}")
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
fun TeachingSlotsOptionsScreenPreview() {
    MaterialTheme {
        val navController = androidx.navigation.compose.rememberNavController()
        TeachingSlotsOptionsScreen(navController = navController)
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
fun TeachingSlotsOptionsScreenWithDestinationPreview() {
    MaterialTheme {
        val navController = androidx.navigation.compose.rememberNavController()
        TeachingSlotsOptionsScreen(navController = navController, destination = "setAvailability")
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF121212)
fun TeachingSlotsOptionsPresetCardPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkBackground)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preview with availability data - testing various row scenarios
            val presetWithData = TeachingSlotPresetItem(
                id = "preset123",
                name = "AM 9B",
                createdAt = System.currentTimeMillis(),
                columnCount = 5,
                dayCount = 3,
                hasAvailabilityData = true,
                groupFrequencies = mapOf(
                    // First row: 5 groups (full row)
                    "1" to 5,
                    "2" to 3,
                    "3" to 7,
                    "4" to 6,
                    "5" to 6,
                    // Second row: 5 groups (full row)
                    "6" to 4,
                    "7" to 3,
                    "8" to 6,
                    "9" to 2,
                    "10" to 4,
                    // Third row: 5 groups (full row)
                    "11" to 5,
                    "12" to 6,
                    "13" to 5,
                    "14" to 5,
                    "15" to 6,
                    // Fourth row: 5 groups (full row)
                    "16" to 6,
                    "17" to 8,
                    "18" to 10,
                    "19" to 7,
                    "20" to 5,
                    // Fifth row: 5 groups (full row)
                    "21" to 3,
                    "22" to 3,
                    "23" to 4,
                    "24" to 1,
                    "25" to 1,
                    // Sixth row: 1 group (partial row to test alignment)
                    "27" to 1
                )
            )

            EnhancedPresetCard(
                preset = presetWithData,
                onClick = {},
                onDeleteClick = {},
                onEditClick = {}
            )

            // Preview with fewer groups to test partial row alignment
            val presetWithFewerGroups = TeachingSlotPresetItem(
                id = "preset456",
                name = "RP 9G",
                createdAt = System.currentTimeMillis() - 86400000,
                columnCount = 3,
                dayCount = 2,
                hasAvailabilityData = true,
                groupFrequencies = mapOf(
                    // First row: 3 groups (partial row)
                    "2" to 4,
                    "5" to 2,
                    "8" to 1,
                    // Second row: 2 groups (partial row)
                    "12" to 3,
                    "15" to 1
                )
            )

            EnhancedPresetCard(
                preset = presetWithFewerGroups,
                onClick = {},
                onDeleteClick = {},
                onEditClick = {}
            )

            // Preview without availability data
            val presetWithoutData = TeachingSlotPresetItem(
                id = "preset789",
                name = "Test Preset",
                createdAt = System.currentTimeMillis() - 86400000,
                columnCount = 3,
                dayCount = 2,
                hasAvailabilityData = false
            )

            EnhancedPresetCard(
                preset = presetWithoutData,
                onClick = {},
                onDeleteClick = null,
                onEditClick = {}
            )
        }
    }
}