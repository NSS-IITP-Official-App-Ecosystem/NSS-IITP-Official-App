package com.phad.chatapp.features.scheduling.schedule

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.features.scheduling.ui.theme.DarkBackground
import com.phad.chatapp.features.scheduling.ui.theme.DarkSurface
import com.phad.chatapp.features.scheduling.ui.theme.NeutralCardSurface
import com.phad.chatapp.features.scheduling.ui.theme.SchedulingTheme
import com.phad.chatapp.features.scheduling.ui.theme.YellowAccent
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.view.inputmethod.InputMethodManager
import android.app.Activity

private const val TAG = "TeachingSlotsScreen"
private const val TEACHING_SLOT_PRESETS_COLLECTION = "teachingSlotPresets"
private const val VOLUNTEER_AVAILABILITY_COLLECTION = "volunteerAvailability"

// Natural sorting function to handle numbers correctly (AM 9B before AM 10G)
private fun naturalSortKey(text: String): String {
    return text.replace(Regex("\\d+")) { matchResult ->
        matchResult.value.padStart(10, '0')
    }
}

// Model class for teaching slots
data class TeachingSlotItem(
    val id: String,
    val name: String,
    val days: List<String>,
    val slotCount: Int,
    val columnNames: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachingSlotsScreen(navController: NavController) {
    val coroutineScope = rememberCoroutineScope()
    var teachingSlots by remember { mutableStateOf<List<TeachingSlotItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Load teaching slots from Firestore
    LaunchedEffect(refreshTrigger) {
        try {
            isLoading = true
            val presets = fetchTeachingSlots()
            teachingSlots = presets
            isLoading = false
        } catch (e: Exception) {
            errorMessage = "Error loading teaching slots: ${e.message}"
            isLoading = false
        }
    }

    // Background gradient
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            DarkBackground,
            DarkBackground.copy(alpha = 0.95f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = backgroundGradient)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Teaching Slots",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp
                            ),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { navController.navigateUp() },
                            modifier = Modifier
                                .padding(8.dp)
                                .size(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    },
                    actions = {
                        // Standardized "New" button using StandardButton component
                        StandardButton(
                            onClick = { navController.navigate("createTeachingSlots") },
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            Text(
                                text = "New",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    windowInsets = WindowInsets(0, 0, 0, 0)
                )
            },
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                    // Show loading indicator, error, or slot list
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(),
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
                                    .padding(horizontal = 24.dp, vertical = 16.dp)
                                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = DarkSurface
                                )
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
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    // Standardized try again button using StandardButton component
                                    StandardButton(
                                        onClick = { refreshTrigger++ }
                                    ) {
                                        Text(
                                            "Try Again",
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        teachingSlots.isEmpty() -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 16.dp)
                                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = DarkSurface
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No teaching slots found",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Create your first teaching slot preset to get started",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.LightGray,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    // Standardized create button using StandardButton component
                                    StandardButton(
                                        onClick = { navController.navigate("createTeachingSlots") }
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Create New Preset",
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(vertical = 16.dp)
                            ) {
                                itemsIndexed(teachingSlots) { index, slot ->
                                    TeachingSlotCard(
                                        slot = slot,
                                        onClick = {
                                            navController.navigate("createTeachingSlots/${slot.id}")
                                        },
                                        onDelete = {
                                            showDeleteConfirmation = slot.id
                                        },
                                        modifier = Modifier.padding(
                                            top = if (index == 0) 0.dp else 0.dp
                                        )
                                    )
                                }

                                // Add some bottom padding for better UX
                                item {
                                    Spacer(modifier = Modifier.height(80.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

    // Delete confirmation dialog
    showDeleteConfirmation?.let { slotId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = null },
            title = {
                Text(
                    "Delete Teaching Slot",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete this teaching slot preset? This will also delete all associated volunteer availability data. This action cannot be undone.",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                deleteTeachingSlot(slotId)
                                teachingSlots = teachingSlots.filter { it.id != slotId }
                                showDeleteConfirmation = null
                            } catch (e: Exception) {
                                errorMessage = "Failed to delete: ${e.message}"
                                showDeleteConfirmation = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = null }
                ) {
                    Text("Cancel", color = YellowAccent)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}



@Composable
fun TeachingSlotCard(
    slot: TeachingSlotItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = YellowAccent.copy(alpha = 0.2f)
            )
            .clickable {
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = NeutralCardSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header with name and delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Read-only preset name
                Text(
                    text = slot.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

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
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFFF5252), // Red color for delete
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Days display with better stylingF
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Days: ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = if (slot.days.isNotEmpty()) slot.days.joinToString(", ") else "No days selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (slot.days.isNotEmpty()) Color.White else Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Display all slots in a row with wrapping - improved styling
            SlotFlowRow(
                slot = slot,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SlotFlowRow(slot: TeachingSlotItem, modifier: Modifier = Modifier) {
    // Create a simple flow layout using Column and Row
    Column(modifier = modifier) {
        val chunkedSlots = slot.columnNames.chunked(3) // Show 3 slots per row

        chunkedSlots.forEach { rowSlots ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowSlots.forEach { slotName ->
                    SlotPill(
                        slotName = slotName,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Fill remaining space if row is not complete
                repeat(3 - rowSlots.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Show message if no slots
        if (slot.columnNames.isEmpty()) {
            Text(
                text = "No time slots configured",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun SlotPill(
    slotName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(18.dp)
            )
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        YellowAccent,
                        YellowAccent.copy(alpha = 0.8f)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable {
                // Optional: Add haptic feedback or click action
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = slotName,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.Black,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

// Function to fetch teaching slots from Firestore
private suspend fun fetchTeachingSlots(): List<TeachingSlotItem> {
    val db = FirebaseFirestore.getInstance()

    try {
        val querySnapshot = db.collection(TEACHING_SLOT_PRESETS_COLLECTION)
            .get()
            .await()

        val slotsList = mutableListOf<TeachingSlotItem>()

        for (document in querySnapshot.documents) {
            try {
                val id = document.id
                val name = document.getString("presetName") ?: "Unnamed Preset"

                // Extract schedule data to get days
                val scheduleData = document.get("schedule") as? List<Map<String, Any>> ?: emptyList()
                val days = scheduleData.mapNotNull { it["day"] as? String }

                // Get column names for slot count
                val columnNames = document.get("columnNames") as? List<String> ?: emptyList()
                val slotCount = columnNames.size

                slotsList.add(
                    TeachingSlotItem(
                        id = id,
                        name = name,
                        days = days,
                        slotCount = slotCount,
                        columnNames = columnNames
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing document: ${document.id}", e)
            }
        }

        // Sort naturally by preset name (handles numbers correctly: AM 9B before AM 10G)
        slotsList.sortWith(compareBy { naturalSortKey(it.name) })

        return slotsList
    } catch (e: Exception) {
        Log.e(TAG, "Error fetching teaching slots", e)
        throw e
    }
}

// Function to delete a teaching slot preset from Firestore
private suspend fun deleteTeachingSlot(slotId: String) {
    val db = FirebaseFirestore.getInstance()

    try {
        // First, get the teaching slot document to retrieve the preset name
        Log.d(TAG, "Fetching teaching slot document to get preset name: $slotId")

        val teachingSlotDoc = db.collection(TEACHING_SLOT_PRESETS_COLLECTION)
            .document(slotId)
            .get()
            .await()

        if (teachingSlotDoc.exists()) {
            val presetName = teachingSlotDoc.getString("presetName")

            if (!presetName.isNullOrEmpty()) {
                // Delete volunteer availability data using preset name as document ID
                Log.d(TAG, "Deleting volunteer availability data for preset: $presetName")

                val availabilityDocRef = db.collection(VOLUNTEER_AVAILABILITY_COLLECTION)
                    .document(presetName)

                // Check if availability document exists
                val availabilityDoc = availabilityDocRef.get().await()
                if (availabilityDoc.exists()) {
                    availabilityDocRef.delete().await()
                    Log.d(TAG, "Successfully deleted volunteer availability data for preset: $presetName")
                } else {
                    Log.d(TAG, "No volunteer availability data found for preset: $presetName")
                }
            } else {
                Log.w(TAG, "Teaching slot document has no presetName field: $slotId")
            }
        } else {
            Log.w(TAG, "Teaching slot document not found: $slotId")
        }

        // Then delete the teaching slot preset document
        db.collection(TEACHING_SLOT_PRESETS_COLLECTION)
            .document(slotId)
            .delete()
            .await()

        Log.d(TAG, "Teaching slot preset successfully deleted: $slotId")
    } catch (e: Exception) {
        Log.e(TAG, "Error deleting teaching slot preset and associated data: $slotId", e)
        throw e
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun TeachingSlotsScreenPreview() {
    SchedulingTheme {
        TeachingSlotsScreen(navController = rememberNavController())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun SlotPillPreview() {
    SchedulingTheme {
        SlotPill(
            slotName = "10-11"
        )
    }
}