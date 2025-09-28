package com.phad.chatapp.features.scheduling.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import com.phad.chatapp.features.scheduling.models.SubjectPreset
import com.phad.chatapp.features.scheduling.ui.components.StandardButton
import com.phad.chatapp.features.scheduling.ui.theme.DarkBackground
import com.phad.chatapp.features.scheduling.ui.theme.DarkSurface
import com.phad.chatapp.features.scheduling.ui.theme.ErrorRed
import com.phad.chatapp.features.scheduling.ui.theme.NeutralCardSurface
import com.phad.chatapp.features.scheduling.ui.theme.YellowAccent
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.features.scheduling.firebase.FirestoreCollection
import com.phad.chatapp.features.scheduling.firebase.FirestoreHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.util.Log
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items

@Composable
fun SubjectAssignmentScreen(navController: NavController) {
    var subjectPresets by remember { mutableStateOf<List<SubjectPreset>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<SubjectPreset?>(null) }
    var presetToEdit by remember { mutableStateOf<SubjectPreset?>(null) }
    var isDeleting by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Load subject presets from Firebase
    LaunchedEffect(Unit) {
        loadSubjectPresets { presets ->
            subjectPresets = presets
            isLoading = false
        }
    }

    // Function to delete a preset
    fun deletePreset(preset: SubjectPreset) {
        isDeleting = true

        coroutineScope.launch {
            try {
                val success = deleteSubjectPreset(preset.name)
                if (success) {
                    // Remove from local list
                    subjectPresets = subjectPresets.filter { it.name != preset.name }
                    snackbarHostState.showSnackbar("Preset '${preset.name}' deleted successfully")
                } else {
                    snackbarHostState.showSnackbar("Failed to delete preset")
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Error deleting preset: ${e.message}")
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
            // Header with back button and title
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
                    text = "Subject Preset",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )

                StandardButton(
                    onClick = { navController.navigate("subjectPresetSelection") }
                ) {
                    Text(
                        "Add",
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
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Create Preset button
                    StandardButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Create Preset",
                            color = Color.Black,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Existing Subject Presets
                    if (subjectPresets.isNotEmpty()) {
                        Text(
                            "Existing Subject Presets",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(1),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(subjectPresets) { preset ->
                                SubjectPresetCard(
                                    preset = preset,
                                    onClick = { 
                                        presetToEdit = preset
                                        showEditDialog = true
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

        // SnackbarHost for user feedback
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Create Subject Preset Dialog
    if (showCreateDialog) {
        CreateSubjectPresetDialog(
            onDismiss = { showCreateDialog = false },
            onPresetCreated = { newPreset ->
                subjectPresets = subjectPresets + newPreset
                showCreateDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Preset '${newPreset.name}' created successfully")
                }
            }
        )
    }

    // Edit Subject Preset Dialog
    if (showEditDialog && presetToEdit != null) {
        EditSubjectPresetDialog(
            preset = presetToEdit!!,
            onDismiss = { 
                showEditDialog = false
                presetToEdit = null
            },
            onPresetUpdated = { updatedPreset ->
                // Update the preset in the local list
                subjectPresets = subjectPresets.map { 
                    if (it.name == updatedPreset.name) updatedPreset else it 
                }
                showEditDialog = false
                presetToEdit = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Preset '${updatedPreset.name}' updated successfully")
                }
            }
        )
    }

    // Delete confirmation dialog
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
            containerColor = DarkSurface,
            titleContentColor = Color.White,
            textContentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            text = {
                Text(
                    "Are you sure you want to delete '${presetToDelete!!.name}'? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0B0B0)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deletePreset(presetToDelete!!)
                        showDeleteDialog = false
                        presetToDelete = null
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
                        presetToDelete = null
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
fun SubjectPresetCard(
    preset: SubjectPreset,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeutralCardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
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
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Secondary info line
            val subjectCount = preset.subjects.count { it.value > 0 }
            Text(
                text = "$subjectCount subject${if (subjectCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB0B0B0)
            )
            // Subject chips flow layout
            val nonZeroSubjects = preset.subjects.entries.filter { it.value > 0 }
            if (nonZeroSubjects.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                SubjectChipsFlowLayout(subjects = nonZeroSubjects)
            }
        }
    }
}

@Composable
fun SubjectChipsFlowLayout(subjects: List<Map.Entry<String, Int>>, modifier: Modifier = Modifier) {
    val maxChipsPerLine = 6
    val chunkedSubjects = subjects.chunked(maxChipsPerLine)
    Column(modifier = modifier) {
        chunkedSubjects.forEachIndexed { idx, lineSubjects ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                lineSubjects.forEach { (subject, count) ->
                    SubjectChip(subject = subject, count = count)
                }
            }
            if (idx < chunkedSubjects.size - 1) {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun SubjectChip(subject: String, count: Int) {
    val chipText = "$subject: $count"
    Box(
        modifier = Modifier
            .height(28.dp)
            .defaultMinSize(minWidth = 48.dp)
            .background(
                color = YellowAccent,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = chipText,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Visible
        )
    }
}

private suspend fun loadSubjectPresets(onResult: (List<SubjectPreset>) -> Unit) {
    try {
        // Use our FirestoreHelper to get the Firestore instance
        val db = FirestoreHelper.getFirestore()
        
        val snapshot = db.collection(FirestoreCollection.SUBJECT_PRESETS).get().await()
        val presets = snapshot.documents.mapNotNull { doc ->
            try {
                val data = doc.data
                if (data != null) {
                    SubjectPreset(
                        id = doc.id, // Document ID is the preset name
                        name = data["name"] as? String ?: doc.id,
                        subjects = (data["subjects"] as? Map<String, Any>)?.mapValues {
                            (it.value as? Number)?.toInt() ?: 0
                        } ?: emptyMap(),
                        createdAt = 0L // No longer stored in database
                    )
                } else null
            } catch (e: Exception) {
                Log.e("SubjectAssignmentScreen", "Error parsing preset document: ${doc.id}", e)
                null
            }
        }
        onResult(presets)
    } catch (e: Exception) {
        Log.e("SubjectAssignmentScreen", "Error loading subject presets", e)
        onResult(emptyList())
    }
}

private suspend fun deleteSubjectPreset(presetName: String): Boolean {
    return try {
        // Use our FirestoreHelper to get the Firestore instance
        val db = FirestoreHelper.getFirestore()
        
        // Check connectivity first
        if (!FirestoreHelper.isConnectedToFirestore()) {
            Log.e("SubjectAssignmentScreen", "Cannot connect to Firestore to delete preset")
            return false
        }
        
        // Use preset name as document ID
        db.collection(FirestoreCollection.SUBJECT_PRESETS)
            .document(presetName)
            .delete()
            .await()
        true
    } catch (e: Exception) {
        Log.e("SubjectAssignmentScreen", "Error deleting subject preset: $presetName", e)
        false
    }
}
