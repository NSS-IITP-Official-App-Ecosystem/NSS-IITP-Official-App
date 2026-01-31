package com.phad.chatapp.features.scheduling.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phad.chatapp.features.scheduling.schedule.Volunteer

/**
 * Dialog for manually selecting a volunteer when automatic assignment fails
 */
@Composable
fun VolunteerSelectionDialog(
    volunteers: List<Volunteer>,
    subject: String,
    onVolunteerSelected: (Volunteer) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Volunteer for $subject",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = "No volunteers have $subject as their first preference. Please manually select a volunteer:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(volunteers) { volunteer ->
                        VolunteerCard(
                            volunteer = volunteer,
                            onClick = { onVolunteerSelected(volunteer) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun VolunteerCard(
    volunteer: Volunteer,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = volunteer.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Roll: ${volunteer.rollNo}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = "Group: ${volunteer.group}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = "Interview Score: ${volunteer.interviewScore}/100",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "Classes Remaining: ${volunteer.classCount}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Subject Preferences:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            
            if (volunteer.subjectPreferences.isEmpty()) {
                Text(
                    text = "None",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                volunteer.subjectPreferences.forEach { subject ->
                    Text(
                        text = subject,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
