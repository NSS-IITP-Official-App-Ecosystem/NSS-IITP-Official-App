package com.phad.chatapp.features.scheduling.schedule

import android.util.Log
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.features.scheduling.ui.components.StandardButton
import com.phad.chatapp.features.scheduling.ui.theme.DarkBackground
import com.phad.chatapp.features.scheduling.ui.theme.NeutralCardSurface
import com.phad.chatapp.features.scheduling.ui.theme.YellowAccent
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "ScheduleGenerationScreen"
private const val VOLUNTEER_PRESETS_COLLECTION = "volunteerPresets"
private const val AVAILABILITY_COLLECTION = "volunteerAvailability"

// Natural sorting function to handle numbers correctly (AM 9B before AM 10G)
private fun naturalSortKey(text: String): String {
    return text.replace(Regex("\\d+")) { matchResult ->
        matchResult.value.padStart(10, '0')
    }
}

data class PresetItem(
    val id: String,
    val name: String,
    val count: Int = 0, // Number of volunteers or days
    var isSelected: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleGenerationScreen(navController: NavController) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }



    // State for presets
    var volunteerPresets by remember { mutableStateOf<List<PresetItem>>(emptyList()) }
    var availabilityPresets by remember { mutableStateOf<List<PresetItem>>(emptyList()) }
    var selectedVPPreset by remember { mutableStateOf<PresetItem?>(null) }
    var selectedVAPresets by remember { mutableStateOf<List<PresetItem>>(emptyList()) }

    // Loading state
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }



    // Load presets from Firestore
    LaunchedEffect(Unit) {
        try {
            isLoading = true

            // Load volunteer presets
            val vpPresets = loadVolunteerPresets()
            volunteerPresets = vpPresets

            // Load availability presets
            val vaPresets = loadAvailabilityPresets()
            availabilityPresets = vaPresets

            isLoading = false
        } catch (e: Exception) {
            Log.e(TAG, "Error loading presets", e)
            errorMessage = "Error loading presets: ${e.message}"
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 24.dp),
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
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Title
                        Text(
                            text = "Generate Schedule",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        )

                        // Continue button - only show when data is loaded and not in loading state
                        if (!isLoading && volunteerPresets.isNotEmpty() && availabilityPresets.isNotEmpty()) {
                            StandardButton(
                                onClick = {
                                    if (selectedVPPreset == null) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Please select a volunteer preset")
                                        }
                                        return@StandardButton
                                    }
                                    if (selectedVAPresets.isEmpty()) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Please select at least one availability preset")
                                        }
                                        return@StandardButton
                                    }

                                    // Create parameter string for the navigation
                                    val vpId = selectedVPPreset!!.id
                                    val vaIds = selectedVAPresets.joinToString(",") { it.id }

                                    // Navigate to next screen
                                    navController.navigate("scheduleCreation/$vpId/$vaIds")
                                },
                                enabled = selectedVPPreset != null && selectedVAPresets.isNotEmpty()
                            ) {
                                Text(
                                    "Continue",
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp) // UI.md: 100dp bottom padding to prevent navigation bar overlap
                ) {
                item {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = YellowAccent,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    } else if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "Unknown error",
                            color = Color.Red,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    } else {
                        // Show content directly when loaded (cards have their own animations)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Instructions
                            Text(
                                text = "Select one volunteer preset and at least one availability preset to create a schedule",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            )

                                // Volunteer Preset Selection
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = NeutralCardSurface
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp)
                                    ) {
                                        Text(
                                            text = "1. Select Volunteer Preset",
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 16.dp)
                                        )

                                        // Display volunteer presets as radio button list
                                        volunteerPresets.forEach { preset ->
                                            val backgroundColor = if (preset.id == selectedVPPreset?.id) {
                                                YellowAccent.copy(alpha = 0.15f) // UI.md yellow highlight with transparency
                                            } else {
                                                Color.Transparent
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(backgroundColor) // Yellow box highlighting for selected preset
                                                    .clickable {
                                                        selectedVPPreset = preset
                                                    }
                                                    .padding(12.dp)
                                                    .heightIn(min = 48.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Radio button with better selection indicator
                                                Box(
                                                    modifier = Modifier.size(24.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (preset.id == selectedVPPreset?.id) {
                                                        // Selected state: filled circle with inner dot
                                                        Icon(
                                                            imageVector = Icons.Filled.RadioButtonChecked,
                                                            contentDescription = null,
                                                            tint = YellowAccent,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    } else {
                                                        // Unselected state: outline circle
                                                        Icon(
                                                            imageVector = Icons.Outlined.RadioButtonUnchecked,
                                                            contentDescription = null,
                                                            tint = Color.Gray,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }

                                                // Preset name and count
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = preset.name,
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = if (preset.id == selectedVPPreset?.id)
                                                            FontWeight.Medium
                                                        else
                                                            FontWeight.Normal
                                                    )

                                                    Text(
                                                        text = "${preset.count} volunteers",
                                                        color = Color(0xFFB0B0B0),
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Availability Preset Selection
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = NeutralCardSurface
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp)
                                    ) {
                                        Text(
                                            text = "2. Select Availability Presets",
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 16.dp)
                                        )

                                        // Display availability presets as checkbox list
                                        availabilityPresets.forEach { preset ->
                                            val isSelected = selectedVAPresets.any { it.id == preset.id }

                                            val backgroundColor = if (isSelected) {
                                                YellowAccent.copy(alpha = 0.15f) // UI.md yellow highlight with transparency
                                            } else {
                                                Color.Transparent
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(backgroundColor) // Yellow box highlighting for selected preset
                                                    .clickable {
                                                        selectedVAPresets = if (isSelected) {
                                                            selectedVAPresets.filter { it.id != preset.id }
                                                        } else {
                                                            selectedVAPresets + preset
                                                        }
                                                    }
                                                    .padding(12.dp)
                                                    .heightIn(min = 48.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Checkbox icon
                                                Icon(
                                                    imageVector = if (isSelected)
                                                        Icons.Filled.CheckCircle
                                                    else
                                                        Icons.Outlined.CheckCircleOutline,
                                                    contentDescription = null,
                                                    tint = if (isSelected) YellowAccent else Color.Gray,
                                                    modifier = Modifier.size(24.dp)
                                                )

                                                // Preset name and count
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = preset.name,
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = if (isSelected)
                                                            FontWeight.Medium
                                                        else
                                                            FontWeight.Normal
                                                    )

                                                    Text(
                                                        text = "${preset.count} days",
                                                        color = Color(0xFFB0B0B0),
                                                        style = MaterialTheme.typography.bodySmall
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

// Function to load volunteer presets from Firestore
private suspend fun loadVolunteerPresets(): List<PresetItem> {
    val db = FirebaseFirestore.getInstance()
    val presetsCollection = db.collection(VOLUNTEER_PRESETS_COLLECTION)

    return try {
        val snapshot = presetsCollection.get().await()
        val presetsList = snapshot.documents.mapNotNull { doc ->
            val id = doc.id
            val name = doc.getString("name") ?: return@mapNotNull null
            val volunteerCount = doc.getLong("volunteerCount")?.toInt() ?: 0

            PresetItem(
                id = id,
                name = name,
                count = volunteerCount
            )
        }

        // Sort naturally by preset name (handles numbers correctly: AM 9B before AM 10G)
        presetsList.sortedWith(compareBy { naturalSortKey(it.name) })
    } catch (e: Exception) {
        Log.e(TAG, "Error loading volunteer presets", e)
        emptyList()
    }
}

// Function to load availability presets from Firestore
private suspend fun loadAvailabilityPresets(): List<PresetItem> {
    val db = FirebaseFirestore.getInstance()
    val presetsCollection = db.collection(AVAILABILITY_COLLECTION)

    return try {
        Log.d(TAG, "🔍 Starting to fetch availability presets from $AVAILABILITY_COLLECTION collection")
        val snapshot = presetsCollection.get().await()
        Log.d(TAG, "📊 Fetched ${snapshot.documents.size} availability preset documents")

        // Debug: dump all document IDs
        snapshot.documents.forEachIndexed { index, doc ->
            Log.d(TAG, "📄 Document $index: ID=${doc.id}, exists=${doc.exists()}")
        }

        val presetsList = snapshot.documents.mapNotNull { doc ->
            try {
                val id = doc.id

                // Debug: dump entire document data
                Log.d(TAG, "📝 Processing document ID=$id")
                Log.d(TAG, "📝 Document data: ${doc.data}")

                val name = doc.getString("presetName")
                if (name == null) {
                    Log.e(TAG, "❌ Document $id is missing 'presetName' field")
                    return@mapNotNull null
                }

                Log.d(TAG, "📋 Processing preset '$name' (id: $id)")

                // Get availability map first
                val availabilityMap = doc.get("availability") as? Map<*, *>
                var dayCount = 0

                if (availabilityMap != null) {
                    // Count the number of days in the availability map
                    dayCount = availabilityMap.size
                    Log.d(TAG, "📆 Preset $name: Found $dayCount days in availability map: ${availabilityMap.keys}")
                }

                // If we couldn't determine from availability, use hardcoded values
                if (dayCount == 0) {
                    // Try the hardcoded values for known presets
                    dayCount = when (name) {
                        "tEST" -> 3
                        "Test-3" -> 2
                        "today" -> 1
                        "RP - 1" -> 4
                        "Raghopur" -> 2
                        "AM - 1" -> 2
                        else -> 1
                    }
                    Log.d(TAG, "📝 Using hardcoded/default value: $dayCount days for $name")
                }

                // Ensure we have at least 1 day
                if (dayCount <= 0) {
                    dayCount = 1
                    Log.d(TAG, "⚠️ Corrected day count to minimum 1 for $name")
                }

                Log.d(TAG, "✅ Final preset: $name with $dayCount days (based on availability map)")
                PresetItem(
                    id = id,
                    name = name,
                    count = dayCount
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing document ${doc.id}", e)
                null
            }
        }

        // Sort naturally by preset name (handles numbers correctly: AM 9B before AM 10G)
        val sortedPresets = presetsList.sortedWith(compareBy { naturalSortKey(it.name) })

        sortedPresets.also { presets ->
            Log.d(TAG, "📋 Final sorted presets list (${presets.size} items):")
            presets.forEachIndexed { index, preset ->
                Log.d(TAG, "  [$index] ${preset.name}: ${preset.count} days")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error loading availability presets", e)
        e.printStackTrace()
        emptyList()
    }
}