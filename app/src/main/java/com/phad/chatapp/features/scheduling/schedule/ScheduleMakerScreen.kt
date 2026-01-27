package com.phad.chatapp.features.scheduling.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.phad.chatapp.features.scheduling.ui.theme.*
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Assignment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleMakerScreen(navController: NavController) {

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                // Header Section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "Schedule Maker",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = "Teaching & Technical Wing",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Text(
                        text = "What would you like to do?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // Menu Buttons
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Menu buttons
                    StaggeredMenuButton(
                        icon = Icons.Default.Schedule,
                        title = "Create Teaching Slots",
                        description = "Define slots for teaching sessions",
                        onClick = { navController.navigate("teachingSlots") }
                    )

                    StaggeredMenuButton(
                        icon = Icons.Default.DateRange,
                        title = "Set Availability",
                        description = "Add volunteer's free time slots for scheduling",
                        onClick = {
                            navController.navigate("teachingSlotsOptionsScreen?destination=setAvailability")
                        }
                    )

                    StaggeredMenuButton(
                        icon = Icons.Default.Group,
                        title = "Manage Volunteers",
                        description = "Select volunteers for teaching sessions",
                        onClick = { navController.navigate("volunteerPresets") }
                    )



                    StaggeredMenuButton(
                        icon = Icons.Default.ViewList,
                        title = "Generate Schedule",
                        description = "Create complete teaching schedule",
                        onClick = { navController.navigate("scheduleGeneration") }
                    )

                    StaggeredMenuButton(
                        icon = Icons.Default.School,
                        title = "Assign Subject",
                        description = "Assign subjects to sessions",
                        onClick = { navController.navigate("subjectAssignment") }
                    )

                    // View Assignments button
                    OutlinedButton(
                        onClick = { navController.navigate("viewAssignments") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .padding(horizontal = 2.dp, vertical = 10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF4CAF50),
                            containerColor = Color.Transparent
                        ),
                        border = BorderStroke(
                            width = 3.dp,
                            color = Color(0xFF4CAF50)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = "View Assignments",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "View Assignments",
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF4CAF50),
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                // Add bottom padding to prevent overlap with navigation bar
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }

// Menu Button
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaggeredMenuButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = NeutralCardSurface // Using DarkBackground theme color (#121212)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 2.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Circular yellow icon background - restored original styling
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFD600)) // Yellow background
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Text column with title and description - updated for dark background
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White // White text for dark background
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0B0B0)
                )
            }
        }
    }
}

// Compact Assignment Button - smaller and positioned as secondary action
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaggeredCompactAssignmentButton(
    onClick: () -> Unit
) {
    // Compact button with reduced size and subtle styling
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = Color(0xFF1A1A1A)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color(0xFF4CAF50)
        ),
        elevation = CardDefaults.outlinedCardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            // Smaller icon with green accent
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50).copy(alpha = 0.15f))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Assignment,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Compact text
            Text(
                text = "View Assignments",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
fun ScheduleMakerScreenPreview() {
    SchedulingTheme {
        val navController = androidx.navigation.compose.rememberNavController()
        ScheduleMakerScreen(navController = navController)
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
fun StaggeredMenuButtonPreview() {
    SchedulingTheme {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = DarkBackground
        ) {
            StaggeredMenuButton(
                icon = Icons.Default.Schedule,
                title = "Create Teaching Slots",
                description = "Define slots for teaching sessions",
                onClick = { }
            )
        }
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
fun StaggeredCompactAssignmentButtonPreview() {
    SchedulingTheme {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = DarkBackground
        ) {
            StaggeredCompactAssignmentButton(
                onClick = { }
            )
        }
    }
}