package com.phad.chatapp.features.scheduling.schedule

import android.util.Log

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.Arrangement
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.features.scheduling.firebase.FirebaseManager
import com.phad.chatapp.features.scheduling.models.VolunteerPreset as ModelVolunteerPreset
import com.phad.chatapp.features.scheduling.ui.theme.*
import com.phad.chatapp.features.scheduling.schedule.StandardButton
import kotlinx.coroutines.launch
import com.google.firebase.firestore.WriteBatch
import java.text.SimpleDateFormat
import java.util.*

// Local VolunteerPreset class for this file
data class VolunteerPreset(
    val id: String = "",
    val name: String = "",
    val createdAt: Long = 0,
    val volunteerCount: Int = 0,
    val groupCounts: Map<String, Int> = emptyMap()
)

private const val TAG = "VolunteerPresetsScreen"

// Natural sorting function to handle numbers correctly (AM 9B before AM 10G)
private fun naturalSortKey(text: String): String {
    return text.replace(Regex("\\d+")) { matchResult ->
        matchResult.value.padStart(10, '0')
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerPresetsScreen(navController: NavController) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // State for presets from database
    var presets by remember { mutableStateOf<List<VolunteerPreset>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Dialog states
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var presetToRename by remember { mutableStateOf<VolunteerPreset?>(null) }
    var presetToDelete by remember { mutableStateOf<VolunteerPreset?>(null) }
    var newPresetName by remember { mutableStateOf("") }

    // Merge presets dialog states
    var showMergeSelectionDialog by remember { mutableStateOf(false) }
    var showMergeConfirmationDialog by remember { mutableStateOf(false) }
    var selectedPresetsForMerge by remember { mutableStateOf(setOf<String>()) }
    var mergedPresetName by remember { mutableStateOf("") }
    var mergePreviewText by remember { mutableStateOf("") }



    // Function to load presets from Firestore
    fun loadPresets(createDefaultIfEmpty: Boolean = false) {
        isLoading = true
        errorMessage = null

        FirebaseManager.getInstance().getCollection(
            "volunteerPresets",
            onSuccess = { snapshot ->
                try {
                    val presetsList = mutableListOf<VolunteerPreset>()

                    for (document in snapshot.documents) {
                        val id = document.id
                        val name = document.getString("name") ?: "Unnamed Preset"
                        val createdAt = document.getLong("createdAt") ?: System.currentTimeMillis()
                        val volunteerCount = document.getLong("volunteerCount")?.toInt() ?: 0

                        // Get group counts from Firestore if available
                        var groupCounts = emptyMap<String, Int>()
                        val groupCountsData = document.get("groupCounts") as? Map<*, *>
                        if (groupCountsData != null) {
                            // Convert the data to the correct type
                            groupCounts = groupCountsData.mapKeys { it.key.toString() }
                                .mapValues {
                                    when (val value = it.value) {
                                        is Long -> value.toInt()
                                        is Int -> value
                                        else -> 0
                                    }
                                }
                            Log.d(TAG, "Loaded group counts from Firestore: $groupCounts")
                        }

                        // Create the preset with all the info
                        val preset = VolunteerPreset(
                            id = id,
                            name = name,
                            createdAt = createdAt,
                            volunteerCount = volunteerCount,
                            groupCounts = groupCounts
                        )

                        presetsList.add(preset)

                        // If group counts are not available in Firestore, calculate them from the volunteers array
                        if (groupCounts.isEmpty()) {
                            // Get volunteers array from the preset document
                            val volunteersData = document.get("volunteers") as? List<Map<String, Any>>
                            if (volunteersData != null) {
                                val newGroupCounts = mutableMapOf<String, Int>()

                                for (volunteerMap in volunteersData) {
                                    val group = volunteerMap["group"] as? String ?: "0"
                                    newGroupCounts[group] = (newGroupCounts[group] ?: 0) + 1
                                }

                                // Update the preset with group statistics
                                val updatedPresets = presets.map {
                                    if (it.id == preset.id) {
                                        it.copy(groupCounts = newGroupCounts)
                                    } else {
                                        it
                                    }
                                }

                                presets = updatedPresets
                            }
                        }
                    }

                    // Sort alphabetically by name (natural sorting for names with numbers)
                    presets = presetsList.sortedWith(compareBy { naturalSortKey(it.name) })
                    isLoading = false

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing presets: ${e.message}", e)
                    errorMessage = "Error loading presets: ${e.message}"
                    isLoading = false
                }
            },
            onFailure = { e ->
                Log.e(TAG, "Firestore error: ${e.message}", e)
                errorMessage = "Firestore error: ${e.message}"
                isLoading = false
            }
        )
    }

    // Function to create a new preset using name as document ID
    fun createNewPreset(name: String) {
        isLoading = true

        val presetData = hashMapOf(
            "name" to name
        )

        FirebaseManager.getInstance().setDocument(
            "volunteerPresets/$name",
            presetData,
            onSuccess = {
                isLoading = false
                loadPresets(false) // Don't create default preset to avoid infinite loop
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Preset created")
                }
            },
            onFailure = { e ->
                isLoading = false
                Log.e(TAG, "Error creating preset: ${e.message}", e)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Failed to create preset: ${e.message}")
                }
            }
        )
    }

    // Function to delete a preset
    fun deletePreset(preset: VolunteerPreset) {
        isLoading = true

        // Delete the preset document
        FirebaseManager.getInstance().deleteDocument(
            "volunteerPresets/${preset.id}",
            onSuccess = {
                // Remove from local list
                presets = presets.filter { it.id != preset.id }
                isLoading = false

                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Preset deleted")
                }
            },
            onFailure = { e ->
                isLoading = false
                Log.e(TAG, "Error deleting preset: ${e.message}", e)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Failed to delete preset: ${e.message}")
                }
            }
        )
    }

    // Function to rename a preset (requires creating new document with new name and deleting old one)
    fun renamePreset(preset: VolunteerPreset, newName: String) {
        if (newName.isBlank()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Preset name cannot be empty")
            }
            return
        }

        if (newName == preset.name) {
            // No change needed
            return
        }

        isLoading = true

        // First, get the current preset data
        FirebaseManager.getInstance().getDocument(
            "volunteerPresets/${preset.id}",
            onSuccess = { documentSnapshot ->
                val currentData = documentSnapshot.data?.toMutableMap() ?: mutableMapOf()

                // Update the data with new name and ID
                currentData["name"] = newName
                currentData["id"] = newName

                // Create new document with new name as ID
                FirebaseManager.getInstance().setDocument(
                    "volunteerPresets/$newName",
                    currentData,
                    onSuccess = {
                        // Delete the old document
                        FirebaseManager.getInstance().deleteDocument(
                            "volunteerPresets/${preset.id}",
                            onSuccess = {
                                isLoading = false
                                loadPresets(false) // Refresh the list
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Preset renamed")
                                }
                            },
                            onFailure = { e ->
                                isLoading = false
                                Log.e(TAG, "Error deleting old preset: ${e.message}", e)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Preset renamed but old version may still exist")
                                }
                            }
                        )
                    },
                    onFailure = { e ->
                        isLoading = false
                        Log.e(TAG, "Error creating renamed preset: ${e.message}", e)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Failed to rename preset: ${e.message}")
                        }
                    }
                )
            },
            onFailure = { e ->
                isLoading = false
                Log.e(TAG, "Error getting preset data: ${e.message}", e)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Failed to rename preset: ${e.message}")
                }
            }
        )
    }

    // Function to merge selected presets
    fun mergePresets(selectedPresetIds: Set<String>, mergedName: String) {
        if (selectedPresetIds.size < 2) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Please select at least 2 presets to merge")
            }
            return
        }

        if (mergedName.isBlank()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Merged preset name cannot be empty")
            }
            return
        }

        isLoading = true

        // Get all selected presets data
        val selectedPresets = presets.filter { it.id in selectedPresetIds }
        // Use a map to track volunteers by roll number for merging
        val mergedVolunteersMap = mutableMapOf<String, com.project.thephadproject.models.VolunteerInfo>()
        var duplicateCount = 0

        // Counter to track completed preset data fetches
        var completedFetches = 0
        val totalFetches = selectedPresets.size

        selectedPresets.forEach { preset ->
            FirebaseManager.getInstance().getDocument(
                "volunteerPresets/${preset.id}",
                onSuccess = { document ->
                    val volunteersData = document.get("volunteers") as? List<Map<String, Any>>
                    volunteersData?.forEach { volunteerMap ->
                        val rollNo = volunteerMap["rollNo"] as? String ?: ""
                        val name = volunteerMap["name"] as? String ?: ""
                        val group = volunteerMap["group"] as? String ?: ""
                        // Extract classCount, default to 0 if missing
                        val classCountObj = volunteerMap["classCount"]
                        val classCount = (classCountObj as? Number)?.toInt() ?: 0

                        if (rollNo.isNotEmpty()) {
                            if (mergedVolunteersMap.containsKey(rollNo)) {
                                // Update existing volunteer: sum class counts
                                val existing = mergedVolunteersMap[rollNo]!!
                                mergedVolunteersMap[rollNo] = existing.copy(
                                    classCount = existing.classCount + classCount
                                )
                                duplicateCount++
                            } else {
                                // Add new volunteer
                                mergedVolunteersMap[rollNo] = com.project.thephadproject.models.VolunteerInfo(
                                    rollNo = rollNo,
                                    name = name,
                                    group = group,
                                    classCount = classCount
                                )
                            }
                        }
                    }

                    completedFetches++

                    // When all fetches are complete, create the merged preset
                    if (completedFetches == totalFetches) {
                        val mergedVolunteersList = mergedVolunteersMap.values.toList()
                        
                        // Calculate total volunteer count (sum of all class counts)
                        val totalVolunteerCount = mergedVolunteersList.sumOf { it.classCount }

                        // Calculate group counts (sum of class counts per group)
                        val groupCounts = mergedVolunteersList
                            .groupBy { it.group }
                            .mapValues { entry -> entry.value.sumOf { it.classCount } }

                        val mergedPresetData = hashMapOf(
                            "name" to mergedName,
                            "volunteerCount" to totalVolunteerCount,
                            "groupCounts" to groupCounts,
                            "volunteers" to mergedVolunteersList
                        )

                        FirebaseManager.getInstance().setDocument(
                            "volunteerPresets/$mergedName",
                            mergedPresetData,
                            onSuccess = {
                                isLoading = false
                                loadPresets(false)
                                coroutineScope.launch {
                                    val message = if (duplicateCount > 0) {
                                        "Preset merged successfully. Class counts merged for $duplicateCount duplicates."
                                    } else {
                                        "Preset merged successfully"
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            onFailure = { e ->
                                isLoading = false
                                Log.e(TAG, "Error creating merged preset: ${e.message}", e)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Failed to create merged preset: ${e.message}")
                                }
                            }
                        )
                    }
                },
                onFailure = { e ->
                    isLoading = false
                    Log.e(TAG, "Error fetching preset data: ${e.message}", e)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Failed to fetch preset data: ${e.message}")
                    }
                }
            )
        }
    }

    // Load presets when screen is visited
    LaunchedEffect(Unit) {
        loadPresets()
    }

    Scaffold(
        topBar = {
                // Header layout with "New" button following UI.md header save button pattern
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp) // UI.md TopAppBar padding
                        .padding(start = 4.dp, end = 20.dp), // Less padding at start for Back button
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(
                        onClick = { navController.navigateUp() },
                        modifier = Modifier.size(48.dp) // UI.md touch target
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp) // UI.md icon size
                        )
                    }

                    // Title
                    Text(
                        text = "Volunteer Presets",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )

                    // Merge Presets Button (Header Action) - only show when 2 or more presets exist
                    if (!isLoading && presets.size >= 2) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp, end = 4.dp)
                                .size(40.dp) // Circular button size
                                .clip(CircleShape)
                                .background(YellowAccent)
                                .clickable {
                                    selectedPresetsForMerge = setOf()
                                    showMergeSelectionDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallMerge,
                                contentDescription = "Merge Presets",
                                tint = Color.Black, // Black icon on yellow background
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = { paddingValues ->
            // Background gradient following UI.md specifications
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground)
                    .padding(paddingValues)
            ) {
                // Content
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 8.dp) // Reduced vertical spacing to minimize gap between header and content
                    ) {
                        // Show loading indicator, error, or preset list with enhanced transitions
                        when {
                            isLoading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = YellowAccent,
                                        modifier = Modifier.size(48.dp),
                                        strokeWidth = 4.dp
                                    )
                                }
                            }

                            errorMessage != null -> {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = DarkSurface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = errorMessage ?: "Unknown error",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = ErrorRed,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(24.dp))

                                        StandardButton(
                                            onClick = { loadPresets() }
                                        ) {
                                            Text(
                                                "Try Again",
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            presets.isEmpty() -> {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = DarkSurface
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Group,
                                                contentDescription = null,
                                                modifier = Modifier.size(72.dp),
                                                tint = NeutralGray
                                            )

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Text(
                                                text = "No volunteer presets found",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White,
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.Medium
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Text(
                                                text = "Create your first volunteer preset to get started",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFFB0B0B0), // Secondary text color from UI.md
                                                textAlign = TextAlign.Center
                                            )

                                            Spacer(modifier = Modifier.height(24.dp))
                                            
                                            // FAB is used for creation now, just show text or arrow pointing to it?
                                            // Or keep text simple.
                                        }
                                    }
                            }

                            else -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        // Merge Button removed from here, moved to Header

                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(16.dp), // UI.md card spacing
                                            contentPadding = PaddingValues(bottom = 100.dp) // UI.md bottom padding to prevent navigation bar overlap
                                        ) {
                                        items(presets) { preset ->
                                            PresetCard(
                                                preset = preset,
                                                onEdit = { navController.navigate("manageVolunteers/${preset.id}") },
                                                onRename = {
                                                    presetToRename = preset
                                                    newPresetName = preset.name
                                                    showRenameDialog = true
                                                },
                                                onDelete = {
                                                    presetToDelete = preset
                                                    showDeleteDialog = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } // End of Column

                // Floating Action Button for New Preset (Manual positioning to match ManageVolunteersScreen)
                if (!isLoading) {
                    FloatingActionButton(
                        onClick = {
                            navController.navigate("manageVolunteers/new_preset")
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 24.dp, bottom = 48.dp), // Margin from edges
                        containerColor = Color(0xFF4CAF50), // Green color
                        contentColor = Color.White
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Preset",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    )

    // Create New Preset dialog with UI.md specifications
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                presetToRename = null
            },
            title = {
                Text(
                    text = if (presetToRename == null) "Create New Preset" else "Rename Preset",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            containerColor = NeutralCardSurface, // UI.md dialog background
            titleContentColor = Color.White,
            textContentColor = Color.White,
            shape = RoundedCornerShape(16.dp), // UI.md dialog corner radius
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp), // UI.md minimum height
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YellowAccent, // UI.md focused color
                        unfocusedBorderColor = NeutralGray.copy(alpha = 0.7f), // UI.md unfocused color
                        focusedLabelColor = YellowAccent,
                        unfocusedLabelColor = NeutralGray,
                        cursorColor = YellowAccent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp) // UI.md input field corner radius
                )
            },
            confirmButton = {
                StandardButton(
                    onClick = {
                        if (presetToRename != null) {
                            renamePreset(presetToRename!!, newPresetName)
                        } else {
                            createNewPreset(newPresetName)
                        }
                        showRenameDialog = false
                        newPresetName = ""
                        presetToRename = null
                    }
                ) {
                    Text(
                        if (presetToRename == null) "Create" else "Rename",
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        presetToRename = null
                        newPresetName = ""
                    }
                ) {
                    Text("Cancel", color = YellowAccent) // UI.md cancel button color
                }
            }
        )
    }

    // Delete confirmation dialog with UI.md specifications
    if (showDeleteDialog && presetToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                presetToDelete = null
            },
            title = {
                Text(
                    "Delete Preset",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            containerColor = DarkSurface, // UI.md dialog background
            titleContentColor = Color.White,
            textContentColor = Color.White,
            shape = RoundedCornerShape(16.dp), // UI.md dialog corner radius
            text = {
                Text(
                    "Are you sure you want to delete '${presetToDelete!!.name}'? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0B0B0) // UI.md secondary text color
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deletePreset(presetToDelete!!)
                        showDeleteDialog = false
                        presetToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorRed // UI.md error color
                    ),
                    shape = RoundedCornerShape(8.dp) // UI.md button corner radius
                ) {
                    Text(
                        "Delete",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        presetToDelete = null
                    }
                ) {
                    Text("Cancel", color = YellowAccent) // UI.md cancel button color
                }
            }
        )
    }

    // Merge Presets Selection Dialog
    if (showMergeSelectionDialog) {
        AlertDialog(
            onDismissRequest = {
                showMergeSelectionDialog = false
                selectedPresetsForMerge = setOf()
            },
            title = {
                Text(
                    text = "Select Presets (2+) to Merge",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            containerColor = NeutralCardSurface,
            titleContentColor = Color.White,
            textContentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {


                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(presets) { preset ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPresetsForMerge = if (selectedPresetsForMerge.contains(preset.id)) {
                                            selectedPresetsForMerge - preset.id
                                        } else {
                                            selectedPresetsForMerge + preset.id
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedPresetsForMerge.contains(preset.id),
                                    onCheckedChange = { checked ->
                                        selectedPresetsForMerge = if (checked) {
                                            selectedPresetsForMerge + preset.id
                                        } else {
                                            selectedPresetsForMerge - preset.id
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = YellowAccent,
                                        uncheckedColor = NeutralGray,
                                        checkmarkColor = Color.Black
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${preset.volunteerCount} volunteers",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB0B0B0)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                StandardButton(
                    onClick = {
                        if (selectedPresetsForMerge.size >= 2) {
                            // Generate preview text
                            val selectedNames = presets.filter { it.id in selectedPresetsForMerge }.map { it.name }
                            mergePreviewText = "Merging: ${selectedNames.joinToString(", ")}"
                            mergedPresetName = ""
                            showMergeSelectionDialog = false
                            showMergeConfirmationDialog = true
                        }
                    },
                    enabled = selectedPresetsForMerge.size >= 2
                ) {
                    Text(
                        "Next",
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMergeSelectionDialog = false
                        selectedPresetsForMerge = setOf()
                    }
                ) {
                    Text("Cancel", color = YellowAccent)
                }
            }
        )
    }

    // Merge Presets Confirmation Dialog
    if (showMergeConfirmationDialog) {
        AlertDialog(
            onDismissRequest = {
                showMergeConfirmationDialog = false
                mergedPresetName = ""
                mergePreviewText = ""
            },
            title = {
                Text(
                    text = "Confirm Merge",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            containerColor = NeutralCardSurface,
            titleContentColor = Color.White,
            textContentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = mergePreviewText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB0B0B0),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = mergedPresetName,
                        onValueChange = { mergedPresetName = it },
                        label = { Text("New Preset Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YellowAccent,
                            unfocusedBorderColor = NeutralGray.copy(alpha = 0.7f),
                            focusedLabelColor = YellowAccent,
                            unfocusedLabelColor = NeutralGray,
                            cursorColor = YellowAccent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                StandardButton(
                    onClick = {
                        mergePresets(selectedPresetsForMerge, mergedPresetName)
                        showMergeConfirmationDialog = false
                        selectedPresetsForMerge = setOf()
                        mergedPresetName = ""
                        mergePreviewText = ""
                    },
                    enabled = mergedPresetName.isNotBlank()
                ) {
                    Text(
                        "Merge",
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMergeConfirmationDialog = false
                        mergedPresetName = ""
                        mergePreviewText = ""
                    }
                ) {
                    Text("Cancel", color = YellowAccent)
                }
            }
        )
    }
}

@Composable
fun GroupChipsFlowLayout(
    groups: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    // Hard limit of 5 groups per line
    val maxGroupsPerLine = 5

    // Split the groups into chunks of 5
    val chunkedGroups = groups.chunked(maxGroupsPerLine)

    Column(modifier = modifier) {
        chunkedGroups.forEach { lineGroups ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                lineGroups.forEach { (group, count) ->
                    GroupChip(group = group, count = count)
                }
            }

            if (chunkedGroups.last() != lineGroups) {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun PresetCard(
    preset: VolunteerPreset,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp), // UI.md corner radius
        colors = CardDefaults.cardColors(
            containerColor = NeutralCardSurface // UI.md preferred card background
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp) // UI.md elevation
    ) {
        Column(
            modifier = Modifier.padding(20.dp) // UI.md card padding
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Clickable preset name for rename functionality
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onRename() }
                        .padding(vertical = 8.dp) // Add padding for better touch target
                )

                // Delete button with UI.md small icon specifications (36dp container, 18dp icon) to match TeachingSlotScreen
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = Color.Red.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFFF5252), // Red color for delete
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp)) // UI.md element spacing

            // Show volunteer count as a detail line with UI.md secondary text color
            Text(
                text = "${preset.volunteerCount} volunteer${if (preset.volunteerCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB0B0B0) // UI.md secondary text color
            )

            // Show volunteer distribution by academic group
            if (preset.groupCounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp)) // UI.md element spacing

                // Filter out groups with zero volunteers and sort numerically
                val nonZeroGroups = preset.groupCounts.entries
                    .filter { it.value > 0 }
                    .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
                    .map { it.key to it.value }

                if (nonZeroGroups.isNotEmpty()) {
                    GroupChipsFlowLayout(groups = nonZeroGroups)
                }
            }
        }
    }
}

@Composable
fun GroupChip(group: String, count: Int) {
    // Group frequency chip with standardized dimensions matching TeachingSlotsOptionsScreen
    Surface(
        shape = RoundedCornerShape(8.dp), // UI.md corner radius for compact look
        color = YellowAccent, // UI.md yellow accent color to match reference
        modifier = Modifier
            .padding(vertical = 2.dp)
            .width(60.dp) // Standardized width for all chips
            .height(28.dp) // Standardized height for all chips
    ) {
        Box(
            contentAlignment = Alignment.Center, // Center text within the standardized chip
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "Gp $group: $count",
                style = MaterialTheme.typography.bodySmall, // UI.md typography for secondary info
                color = Color.Black, // Black text on yellow background for contrast
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, // Center align text
                maxLines = 1, // Ensure single line
                overflow = TextOverflow.Ellipsis // Handle overflow gracefully
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun VolunteerPresetsScreenPreview() {
    MaterialTheme {
        VolunteerPresetsScreen(rememberNavController())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PresetCardPreview() {
    MaterialTheme {
        PresetCard(
            preset = VolunteerPreset(
                id = "1",
                name = "Morning Volunteers",
                createdAt = System.currentTimeMillis(),
                volunteerCount = 25,
                groupCounts = mapOf(
                    "1" to 5,
                    "2" to 8,
                    "3" to 12
                )
            ),
            onEdit = {},
            onRename = {},
            onDelete = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun GroupChipPreview() {
    MaterialTheme {
        GroupChip(group = "3", count = 12)
    }
}