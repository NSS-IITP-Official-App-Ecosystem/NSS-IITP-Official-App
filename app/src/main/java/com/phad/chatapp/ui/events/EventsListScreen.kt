package com.phad.chatapp.ui.events

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.utils.PDFGenerator
import kotlinx.coroutines.tasks.await
import android.net.Uri
import androidx.core.content.FileProvider
import android.content.Intent
import kotlinx.coroutines.launch
import com.phad.chatapp.utils.SessionManager

data class EventDetail(
    val id: String,
    val name: String,
    val date: String,
    val hours: Double,
    val isMandatory: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsListScreen(
    semester: Int,
    rollNumber: String,
    onBackClick: () -> Unit
) {
    var events by remember { mutableStateOf<List<EventDetail>>(emptyList()) }
    var attendedCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var studentName by remember { mutableStateOf("") }
    var nssGroup by remember { mutableStateOf("") }
    var isGeneratingFile by remember { mutableStateOf(false) }
    var showFileMessage by remember { mutableStateOf<String?>(null) }
    var headerTotal by remember { mutableStateOf<Double?>(null) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(semester, rollNumber) {
        try {
            isLoading = true
            error = null
            
            val db = FirebaseFirestore.getInstance()
            // Seed from session as fallback
            val sessionManager = SessionManager(context)
            val sessionName = sessionManager.fetchUserName()
            if (!sessionName.isNullOrEmpty()) studentName = sessionName

            // Prefer unified users collection
            val userDoc = db.collection("users").document(rollNumber).get().await()
            if (userDoc.exists()) {
                studentName = userDoc.getString("name") ?: studentName
            } else {
                // Legacy fallback: Student collection (deprecated)
                val studentDoc = db.collection("Student").document(rollNumber).get().await()
                if (studentDoc.exists()) {
                    studentName = studentDoc.getString("Name") ?: studentName
                    nssGroup = studentDoc.getString("NSS_gro") ?: ""
                }
            }
            
            val eventsSnapshot = db.collection("NSS_Events_Attendence").get().await()
            
            val eventLog = mutableListOf<EventDetail>()
            var localAttendedCount = 0
            
            eventsSnapshot.documents.forEach { doc ->
                val event = doc.toObject(AttendanceEvent::class.java)
                if (event != null) {
                    val eventSemester = getSemesterFromDate(event.eventDate)
                    if (eventSemester == semester) {
                        val attended = event.attendees.any { it.rollNumber == rollNumber }
                        val eventName = extractEventNameFromId(event.id)
                        if (attended) {
                            localAttendedCount++
                            eventLog.add(
                                EventDetail(
                                    id = event.id,
                                    name = eventName,
                                    date = event.eventDate,
                                    hours = event.hours,
                                    isMandatory = event.isMandatory
                                )
                            )
                        } else if (event.isMandatory && event.negativeHours > 0.0) {
                            // Absent in a mandatory event: log negative hours
                            eventLog.add(
                                EventDetail(
                                    id = event.id,
                                    name = eventName,
                                    date = event.eventDate,
                                    hours = -event.negativeHours,
                                    isMandatory = true
                                )
                            )
                        }
                    }
                }
            }
            
            // Sort events by date (newest first)
            events = eventLog.sortedByDescending { parseDate(it.date) }
            attendedCount = localAttendedCount

            // Header total from users (source of truth)
            val totalsDoc = db.collection("users").document(rollNumber).get().await()
            headerTotal = if (totalsDoc.exists()) {
                val s1 = totalsDoc.getDouble("sem1Hours") ?: 0.0
                val s2 = totalsDoc.getDouble("sem2Hours") ?: 0.0
                when (semester) { 1 -> s1; 2 -> s2; else -> null }
            } else null
            isLoading = false
        } catch (e: Exception) {
            error = e.message
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Semester $semester Events",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (events.isNotEmpty() && !isGeneratingFile) {
                        IconButton(
                            onClick = {
                                isGeneratingFile = true
                                scope.launch {
                                    try {
                                        val generator = PDFGenerator(context)
                                        val rows = events.map { Triple(it.name, it.date, it.hours.toInt()) }
                                        val path = generator.generateStudentEventsList(studentName, rollNumber, semester, rows)
                                        if (path != null) {
                                            showFileMessage = "PDF saved to Downloads/NSS_Reports"
                                        } else {
                                            showFileMessage = "Failed to generate PDF report"
                                        }
                                    } catch (e: Exception) {
                                        showFileMessage = e.message ?: "Failed to generate PDF report"
                                    } finally {
                                        isGeneratingFile = false
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Download PDF",
                                tint = Color.White
                            )
                        }
                    }
                    if (isGeneratingFile) {
                        Box(
                            modifier = Modifier.padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xff0d0302),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xff0d0302)
    ) { paddingValues ->
        // Show file generation messages
        showFileMessage?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(3000)
                showFileMessage = null
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Error loading events",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error ?: "Unknown error",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (events.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "No events attended",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "You haven't attended any events in Semester $semester yet",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Header with total hours
                val totalHours = headerTotal ?: events.sumOf { it.hours }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Total Hours: $totalHours",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xffffffff)
                        )
                        Text(
                            text = "$attendedCount events attended",
                            fontSize = 14.sp,
                            color = Color(0xffffffff).copy(alpha = 0.7f)
                        )
                    }
                }

                // Events list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(events) { event ->
                        EventCard(event = event)
                    }
                }
            }
        }
        
        // Show PDF message as overlay
        showFileMessage?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.contains("PDF saved") || message.contains("saved")) Color(0xFF4CAF50) else Color(0xFFF44336)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = message,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun EventCard(event: EventDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (event.isMandatory) Color(0xFFFFFDE7) else Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = event.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xff0d0302)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.date,
                    fontSize = 14.sp,
                    color = Color(0xff0d0302).copy(alpha = 0.7f)
                )
            }
            
            // Hours badge
            val badgeColor = if (event.hours < 0) Color(0xFFF57C00) else Color(0xFF4CAF50)
            Box(
                modifier = Modifier
                    .background(
                        badgeColor,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${event.hours}h",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFFFFF)
                )
            }
        }
    }
}

// Intentionally empty: helper removed; PDF generation handled in composable click coroutine

private fun extractEventNameFromId(eventId: String): String {
    // Event ID format: "{day}_{month}_{event_name_with_underscores}"
    // Extract the event name part and replace underscores with spaces
    val parts = eventId.split("_")
    if (parts.size >= 3) {
        val eventNameParts = parts.drop(2) // Skip day and month
        return eventNameParts.joinToString(" ").replace("_", " ")
    }
    return eventId.replace("_", " ")
}

private fun getSemesterFromDate(eventDate: String): Int {
    try {
        val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH)
        val date = dateFormat.parse(eventDate)
        if (date != null) {
            val calendar = java.util.Calendar.getInstance()
            calendar.time = date
            
            val month = calendar.get(java.util.Calendar.MONTH) + 1
            val year = calendar.get(java.util.Calendar.YEAR)
            
            // Semester 1: July 2025 to December 2025
            if (year == 2025 && month in 7..12) {
                return 1
            }
            // Semester 2: January 2026 to May 2026
            else if (year == 2026 && month in 1..5) {
                return 2
            }
        }
    } catch (e: Exception) {
        // Handle parsing error
    }
    return 0 // Not in any semester
}

private fun parseDate(dateString: String): Long {
    return try {
        val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH)
        dateFormat.parse(dateString)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}
