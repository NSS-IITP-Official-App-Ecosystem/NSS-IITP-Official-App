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
    val timeSlots: List<TimeSlotInfo> = emptyList(),
    val subjects: List<SubjectInfo> = emptyList()
)

data class TimeSlotInfo(
    val classTime: String,
    val freeGroupTime: String
)

data class SubjectInfo(
    val name: String,
    val count: Int
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
                // Custom Header Row as per TTW_UI_plan.md
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp, start = 4.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button
                    IconButton(
                        onClick = { navController.navigateUp() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Title
                    Text(
                        text = "Teaching Slots",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                }
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

        // Floating Action Button (Manual Positioning as per plan)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp, end = 24.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = { navController.navigate("createTeachingSlots") },
                containerColor = Color(0xFF4CAF50), // Green as per plan
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Create New Preset",
                    modifier = Modifier.size(24.dp)
                )
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
                    color = YellowAccent,
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

            // Days display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "Days: ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = YellowAccent, // Header Yellow
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(80.dp)
                )

                Text(
                    text = if (slot.days.isNotEmpty()) slot.days.joinToString(", ") else "No days selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (slot.days.isNotEmpty()) Color.White else Color.Gray, // Content White
                    fontWeight = FontWeight.Medium
                )
            }

            // Times Display
            if (slot.timeSlots.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "Times: ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = YellowAccent, // Header Yellow
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(80.dp)
                    )

                    Column {
                        slot.timeSlots.forEach { timeInfo ->
                            Text(
                                text = "${timeInfo.classTime}  (FG: ${timeInfo.freeGroupTime})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White, // Content White
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }
            }

            // Subjects Display
            if (slot.subjects.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "Subjects: ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = YellowAccent, // Header Yellow
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(80.dp)
                    )

                    Column {
                        val chunkedSubjects = slot.subjects.chunked(4)
                        chunkedSubjects.forEach { rowSubjects ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowSubjects.forEach { subject ->
                                    val shortName = subject.name.take(3)
                                    Box(
                                        modifier = Modifier
                                            .width(54.dp)
                                            .height(26.dp)
                                            .background(
                                                color = YellowAccent,
                                                shape = RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$shortName : ${subject.count}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = Color.Black,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                repeat(4 - rowSubjects.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
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

                // Extract time slots
                val rawColumns = document.get("columnNames") as? List<*> ?: emptyList<Any>()
                val timeSlots = rawColumns.mapNotNull { item ->
                     when (item) {
                        is Map<*, *> -> {
                            val c = item["classTime"] as? String ?: ""
                            val f = item["freeGroupTime"] as? String ?: ""
                            TimeSlotInfo(c, f)
                        }
                        is String -> TimeSlotInfo(item, "N/A") // Legacy support
                        else -> null
                     }
                }
                
                // Extract subjects
                val subjectsData = document.get("subjects") as? List<Map<String, Any>> ?: emptyList()
                val subjects = subjectsData.mapNotNull {
                    val subName = it["subjectName"] as? String
                    // Handle Number type safely (Firestore numbers can be Long)
                    val count = (it["classCount"] as? Number)?.toInt()
                    
                    if (subName != null && count != null) {
                        SubjectInfo(subName, count)
                    } else null
                }

                val slotCount = timeSlots.size

                slotsList.add(
                    TeachingSlotItem(
                        id = id,
                        name = name,
                        days = days,
                        slotCount = slotCount,
                        timeSlots = timeSlots,
                        subjects = subjects
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing document: ${document.id}", e)
            }
        }

        // Sort naturally by preset name
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
        // Just delete the teaching slot preset document
        // This will automatically remove any availability data stored within it as well
        db.collection(TEACHING_SLOT_PRESETS_COLLECTION)
            .document(slotId)
            .delete()
            .await()

        Log.d(TAG, "Teaching slot preset successfully deleted: $slotId")
    } catch (e: Exception) {
        Log.e(TAG, "Error deleting teaching slot preset: $slotId", e)
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
