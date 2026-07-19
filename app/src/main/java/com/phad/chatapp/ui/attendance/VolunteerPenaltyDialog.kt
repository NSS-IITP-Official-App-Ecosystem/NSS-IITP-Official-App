package com.phad.chatapp.ui.attendance

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.phad.chatapp.viewmodels.AttendanceViewModel
import com.phad.chatapp.viewmodels.PenaltySelection
import com.phad.chatapp.viewmodels.VolunteerPenaltyState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerPenaltyDialog(
    eventId: String,
    eventName: String,
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var volunteers by remember { mutableStateOf<List<VolunteerPenaltyState>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isApplying by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(eventId) {
        isLoading = true
        loadError = null
        val result = viewModel.getVolunteersForPenaltyWithError(eventId)
        result.fold(
            onSuccess = { volunteers = it },
            onFailure = { loadError = it.message }
        )
        isLoading = false
    }

    val filteredVolunteers = remember(volunteers, searchQuery) {
        if (searchQuery.isBlank()) {
            volunteers
        } else {
            volunteers.filter {
                it.rollNumber.contains(searchQuery, ignoreCase = true) ||
                    it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isApplying) onDismiss() },
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.9f)
            .padding(16.dp),
        title = {
            Column {
                Text(
                    text = "Adjust Hours & Penalties",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFFE53935)
                )
                Text(
                    text = eventName,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFE53935))
                    }
                } else if (loadError != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error: $loadError",
                            color = Color(0xFFEF5350),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else if (volunteers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No eligible volunteers found for this event.",
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name or roll number...", color = Color.Gray) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.Gray
                            )
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF2E2E2E),
                            unfocusedContainerColor = Color(0xFF2E2E2E),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color(0xFFE53935),
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Color(0xFFE53935)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bulk Set (Filtered):",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            BulkSetButton("All (+)", Color(0xFF4CAF50)) {
                                volunteers = volunteers.setFilteredSelection(filteredVolunteers, PenaltySelection.POSITIVE)
                            }
                            BulkSetButton("All (0)", Color.White) {
                                volunteers = volunteers.setFilteredSelection(filteredVolunteers, PenaltySelection.ZERO)
                            }
                            BulkSetButton("All (-)", Color(0xFFE53935)) {
                                volunteers = volunteers.setFilteredSelection(filteredVolunteers, PenaltySelection.NEGATIVE)
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredVolunteers) { volunteer ->
                            VolunteerPenaltyRow(
                                volunteer = volunteer,
                                onSelectionChange = { selection ->
                                    volunteers = volunteers.map { vol ->
                                        if (vol.rollNumber == volunteer.rollNumber) {
                                            vol.copy(selection = selection)
                                        } else {
                                            vol
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { showConfirmDialog = true },
                enabled = !isLoading && !isApplying && volunteers.isNotEmpty()
            ) {
                Text("Apply Settings", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isApplying) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )

    if (showConfirmDialog) {
        val totalPositive = volunteers.count { it.selection == PenaltySelection.POSITIVE }
        val totalZero = volunteers.count { it.selection == PenaltySelection.ZERO }
        val totalNegative = volunteers.count { it.selection == PenaltySelection.NEGATIVE }

        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Text("Confirm Adjustments", fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
            },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to apply these adjustments? This action cannot be undone.",
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Final Breakdown:", fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
                    Text("- Positive (+ Hours): $totalPositive students", color = Color(0xFF81C784))
                    Text("- Zero (0 Hours): $totalZero students", color = Color.White)
                    Text("- Negative (- Hours): $totalNegative students", color = Color(0xFFEF5350))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        coroutineScope.launch {
                            isApplying = true
                            val positiveRolls = volunteers
                                .filter { it.selection == PenaltySelection.POSITIVE }
                                .map { it.rollNumber }
                            val negativeRolls = volunteers
                                .filter { it.selection == PenaltySelection.NEGATIVE }
                                .map { it.rollNumber }
                            val zeroRolls = volunteers
                                .filter { it.selection == PenaltySelection.ZERO }
                                .map { it.rollNumber }

                            val result = viewModel.applyAbsentPenalty(
                                eventId = eventId,
                                positiveRollNumbers = positiveRolls,
                                negativeRollNumbers = negativeRolls,
                                zeroRollNumbers = zeroRolls
                            )

                            isApplying = false
                            if (result.isSuccess) {
                                onSuccess(result.getOrNull() ?: "Penalty settings applied successfully!")
                            } else {
                                Toast.makeText(
                                    context,
                                    result.exceptionOrNull()?.message ?: "Error",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) {
                    Text("Confirm", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}

@Composable
private fun BulkSetButton(text: String, color: Color, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        modifier = Modifier
            .height(32.dp)
            .padding(horizontal = 4.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun VolunteerPenaltyRow(
    volunteer: VolunteerPenaltyState,
    onSelectionChange: (PenaltySelection) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2E))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = volunteer.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = volunteer.rollNumber,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (volunteer.isAbsent) Color(0xFFE53935).copy(alpha = 0.15f)
                            else Color(0xFF4CAF50).copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (volunteer.isAbsent) "Absent" else "Present",
                        color = if (volunteer.isAbsent) Color(0xFFEF5350) else Color(0xFF81C784),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hours Action:",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PenaltyRadioOption(
                        label = "+",
                        color = Color(0xFF4CAF50),
                        selected = volunteer.selection == PenaltySelection.POSITIVE,
                        onClick = { onSelectionChange(PenaltySelection.POSITIVE) }
                    )
                    PenaltyRadioOption(
                        label = "0",
                        color = Color.White,
                        selected = volunteer.selection == PenaltySelection.ZERO,
                        onClick = { onSelectionChange(PenaltySelection.ZERO) }
                    )
                    PenaltyRadioOption(
                        label = "-",
                        color = Color(0xFFE53935),
                        selected = volunteer.selection == PenaltySelection.NEGATIVE,
                        onClick = { onSelectionChange(PenaltySelection.NEGATIVE) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PenaltyRadioOption(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = color,
                unselectedColor = Color.Gray
            ),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private fun List<VolunteerPenaltyState>.setFilteredSelection(
    filteredVolunteers: List<VolunteerPenaltyState>,
    selection: PenaltySelection
): List<VolunteerPenaltyState> {
    val filteredRolls = filteredVolunteers.map { it.rollNumber }.toSet()
    return map { volunteer ->
        if (volunteer.rollNumber in filteredRolls) {
            volunteer.copy(selection = selection)
        } else {
            volunteer
        }
    }
}