package com.phad.chatapp.features.scheduling.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phad.chatapp.features.scheduling.models.SubjectPreset
import com.phad.chatapp.features.scheduling.models.SubjectConstants
import com.phad.chatapp.features.scheduling.ui.components.StandardButton
import com.phad.chatapp.features.scheduling.ui.components.SubjectCountRow
import com.phad.chatapp.features.scheduling.ui.theme.DarkBackground
import com.phad.chatapp.features.scheduling.ui.theme.NeutralCardSurface
import com.phad.chatapp.features.scheduling.ui.theme.YellowAccent
import com.phad.chatapp.features.scheduling.firebase.FirestoreCollection
import com.phad.chatapp.features.scheduling.firebase.FirestoreHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.ui.platform.LocalContext
import android.util.Log

@Composable
fun EditSubjectPresetDialog(
    preset: SubjectPreset,
    onDismiss: () -> Unit,
    onPresetUpdated: (SubjectPreset) -> Unit
) {
    var presetName by remember { mutableStateOf(preset.name) }
    var subjectCounts by remember { mutableStateOf<Map<String, Int>>(
        SubjectConstants.ALL_SUBJECTS.associateWith { subjectCode ->
            preset.subjects[subjectCode] ?: 0
        }
    ) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Function to check network connectivity
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NeutralCardSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Subject Preset",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Preset Name Input (disabled since we don't allow changing the name)
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { /* Disabled - name cannot be changed */ },
                    label = { Text("Preset Name", color = Color(0xFFB0B0B0)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF666666),
                        unfocusedBorderColor = Color(0xFF666666),
                        disabledTextColor = Color.White,
                        disabledBorderColor = Color(0xFF666666),
                        disabledLabelColor = Color(0xFFB0B0B0)
                    ),
                    enabled = false
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Error message
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                // Subjects List
                Text(
                    text = "Subject Classes",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(SubjectConstants.ALL_SUBJECTS) { subjectCode ->
                        SubjectCountRow(
                            subjectCode = subjectCode,
                            subjectName = SubjectConstants.SUBJECT_NAMES[subjectCode] ?: subjectCode,
                            count = subjectCounts[subjectCode] ?: 0,
                            onCountChange = { newCount ->
                                subjectCounts = subjectCounts + (subjectCode to newCount)
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text("Cancel")
                    }
                    
                    StandardButton(
                        onClick = {
                            val totalClasses = subjectCounts.values.sum()
                            if (totalClasses == 0) {
                                errorMessage = "Please add at least one subject class"
                                return@StandardButton
                            }
                            
                            // Check network connectivity before attempting to save
                            if (!isNetworkAvailable()) {
                                errorMessage = "No internet connection. Please check your network settings and try again."
                                return@StandardButton
                            }
                            
                            isLoading = true
                            errorMessage = ""
                            
                            coroutineScope.launch {
                                try {
                                    val updatedPreset = preset.copy(
                                        subjects = subjectCounts.filterValues { it > 0 }
                                    )

                                    val savedPreset = updateSubjectPreset(updatedPreset)
                                    onPresetUpdated(savedPreset)
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Failed to update preset"
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "Save Changes",
                                color = Color.Black,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun updateSubjectPreset(preset: SubjectPreset): SubjectPreset {
    // Use our FirestoreHelper to get the Firestore instance
    val db = FirestoreHelper.getFirestore()
    
    try {
        // Use preset name as document ID for better organization
        val docRef = db.collection(FirestoreCollection.SUBJECT_PRESETS).document(preset.name)

        // Check if document exists
        val existingDoc = docRef.get().await()
        if (!existingDoc.exists()) {
            throw Exception("Preset '${preset.name}' not found")
        }

        // Create preset data without storing the id or createdAt fields in the document
        val presetData = mapOf(
            "name" to preset.name,
            "subjects" to preset.subjects
        )

        // First check if we're connected to Firestore
        if (!FirestoreHelper.isConnectedToFirestore()) {
            throw Exception("Cannot connect to the server. Please check your internet connection and try again.")
        }
            
        // Update the document
        docRef.set(presetData).await()
        Log.d("EditSubjectPresetDialog", "Updated preset: ${preset.name}")

        // Return preset with document ID for local state management
        return preset
    } catch (e: Exception) {
        Log.e("EditSubjectPresetDialog", "Error updating preset: ${e.message}")
        throw e
    }
} 