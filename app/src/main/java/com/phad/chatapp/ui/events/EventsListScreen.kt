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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.geometry.Offset
import com.phad.chatapp.ui.components.GradientHeader

data class EventDetail(
    val id: String,
    val name: String,
    val date: String,
    val hours: Double,
    val isMandatory: Boolean,
    val wings: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsListScreen(
    semester: Int,
    rollNumber: String,
    onBackClick: () -> Unit
) {
    var events by remember { mutableStateOf<List<EventDetail>>(emptyList()) }
    var wingEvents by remember { mutableStateOf<List<EventDetail>>(emptyList()) }
    var openEvents by remember { mutableStateOf<List<EventDetail>>(emptyList()) }
    var attendedCount by remember { mutableStateOf(0) }
    var wingHours by remember { mutableStateOf(0.0) }
    var openEventHours by remember { mutableStateOf(0.0) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var studentName by remember { mutableStateOf("") }
    var nssGroup by remember { mutableStateOf("") }
    var isGeneratingFile by remember { mutableStateOf(false) }
    var showFileMessage by remember { mutableStateOf<String?>(null) }
    var headerTotal by remember { mutableStateOf<Double?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(semester, rollNumber, refreshKey) {
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
            var userWings: List<String> = emptyList()
            if (userDoc.exists()) {
                studentName = userDoc.getString("name") ?: studentName
                userWings = (userDoc.get("wings") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            } else {
                // Legacy fallback: Student collection (deprecated)
                val studentDoc = db.collection("Student").document(rollNumber).get().await()
                if (studentDoc.exists()) {
                    studentName = studentDoc.getString("Name") ?: studentName
                    nssGroup = studentDoc.getString("NSS_gro") ?: ""
                }
            }
            
            val eventsSnapshot = db.collection("NSS_Events_Attendence").get().await()
            
            val wingEventLog = mutableListOf<EventDetail>()
            val openEventLog = mutableListOf<EventDetail>()
            var localAttendedCount = 0
            
            eventsSnapshot.documents.forEach { doc ->
                val event = doc.toObject(AttendanceEvent::class.java)
                if (event != null) {
                    val eventSemester = getSemesterFromDate(event.eventDate)
                    if (eventSemester == semester) {
                        val attended = event.attendees.any { it.rollNumber == rollNumber }
                        val eventName = extractEventNameFromId(event.id)
                        
                        // Determine if this is a wing event or open event
                        val eventWings = event.wings
                        val isDnc = eventWings.contains("Design and Curation Wing")
                        val isUserWingEvent = eventWings.any { it in userWings }
                        
                        if (attended) {
                            localAttendedCount++
                            val eventDetail = EventDetail(
                                id = event.id,
                                name = eventName,
                                date = event.eventDate,
                                hours = event.hours,
                                isMandatory = event.isMandatory,
                                wings = event.wings
                            )
                            
                            // Categorize: Open events first, then user's wing events
                            val isOpenEvent = event.wings.containsAll(AttendanceEvent.ALL_WINGS)
                            
                            if (isOpenEvent) {
                                openEventLog.add(eventDetail)
                            } else if (isUserWingEvent) {
                                wingEventLog.add(eventDetail)
                            } else {
                                // Fallback for pure DNC or other cases
                                openEventLog.add(eventDetail)
                            }
                        } else if (!event.visibleOnlyToPresent && event.isMandatory && event.negativeHours > 0.0 && event.absentPenaltyApplied) {
                            // Absent in a mandatory event: check if relevant to user
                            if (isDnc || isUserWingEvent) {
                                val eventDetail = EventDetail(
                                    id = event.id,
                                    name = eventName,
                                    date = event.eventDate,
                                    hours = -event.negativeHours,
                                    isMandatory = true,
                                    wings = event.wings
                                )
                                
                                // Categorize negative hours
                                val isOpenEvent = event.wings.containsAll(AttendanceEvent.ALL_WINGS)
                                
                                if (isOpenEvent) {
                                    openEventLog.add(eventDetail)
                                } else if (isUserWingEvent) {
                                    wingEventLog.add(eventDetail)
                                } else {
                                    openEventLog.add(eventDetail)
                                }
                            }
                        }
                    }
                }
            }
            
            // Sort events by date (newest first)
            wingEvents = wingEventLog.sortedByDescending { parseDate(it.date) }
            openEvents = openEventLog.sortedByDescending { parseDate(it.date) }
            events = (wingEventLog + openEventLog).sortedByDescending { parseDate(it.date) }
            
            // Calculate wing hours and open event hours
            wingHours = wingEvents.sumOf { it.hours }
            openEventHours = openEvents.sumOf { it.hours }
            
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
        containerColor = Color(0xff0d0302)
    ) { paddingValues ->
        // Show file generation messages
        showFileMessage?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(3000)
                showFileMessage = null
            }
        }
        
        // Pull to refresh state
        val pullRefreshState = rememberPullToRefreshState()
        
        if (pullRefreshState.isRefreshing) {
            LaunchedEffect(true) {
                refreshKey++ // Trigger reload
                pullRefreshState.endRefresh()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                val semesterOrdinal = when (semester) {
                    1 -> "1st"
                    2 -> "2nd"
                    3 -> "3rd"
                    else -> "${semester}th"
                }
                
                // Header with Sem Summary
                GradientHeader(
                    title = "$semesterOrdinal Semester",
                    icon = Icons.Default.DateRange,
                    isTitleCentered = true,
                    onBackClick = onBackClick,
                    actions = {
                        if (events.isNotEmpty() && !isGeneratingFile) {
                            IconButton(
                                onClick = {
                                    isGeneratingFile = true
                                    scope.launch {
                                        try {
                                            val generator = PDFGenerator(context)

                                            val wingRows = wingEvents.map { 
                                                com.phad.chatapp.utils.StudentEventReportRow(
                                                    it.name, 
                                                    it.date, 
                                                    it.hours, 
                                                    if (it.wings.containsAll(AttendanceEvent.ALL_WINGS)) "Open Event" else it.wings.joinToString(", ")
                                                ) 
                                            }
                                            val openRows = openEvents.map { 
                                                com.phad.chatapp.utils.StudentEventReportRow(
                                                    it.name, 
                                                    it.date, 
                                                    it.hours, 
                                                    if (it.wings.containsAll(AttendanceEvent.ALL_WINGS)) "Open Event" else it.wings.joinToString(", ")
                                                ) 
                                            }
                                            
                                            val path = generator.generateStudentEventsList(
                                                studentName, 
                                                rollNumber, 
                                                semester, 
                                                wingRows, 
                                                openRows, 
                                                wingHours, 
                                                openEventHours, 
                                                attendedCount
                                            )
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
                        } else if (isGeneratingFile) {
                             Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                           Spacer(modifier = Modifier.size(48.dp))
                        }
                    }
                )

                // Events list with sections
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Wing Events Section
                    if (wingEvents.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Wing Events Attended",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Hours = $wingHours",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        items(wingEvents) { event ->
                            EventCard(event = event)
                        }
                    }
                    
                    // Spacer between sections
                    if (wingEvents.isNotEmpty() && openEvents.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    
                    // Open Events Section
                    if (openEvents.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Open Events Attended",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Hours = $openEventHours",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        items(openEvents) { event ->
                            EventCard(event = event)
                        }
                    }
                }
            }
        }
            PullToRefreshContainer(
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date on Left (DD MMM)
            Text(
                text = getFormattedDateDayMonth(event.date),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFA000),
                modifier = Modifier
                    .width(60.dp) // Fixed width for alignment
            )
            
            Spacer(modifier = Modifier.width(12.dp))

            // Name and Wings
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = event.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xff0d0302)
                )
                
                if (event.wings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (event.wings.containsAll(AttendanceEvent.ALL_WINGS)) "Open Event" else event.wings.joinToString("\n"),
                        fontSize = 12.sp,
                        color = Color(0xff0d0302).copy(alpha = 0.6f),
                        lineHeight = 16.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
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
                    text = "${com.phad.chatapp.utils.AttendanceEventUtils.formatHours(event.hours)}h",
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

/**
 * Determine semester from event date
 * Semester 1: July 1 - December 10 (any year)
 * Semester 2: December 11 - June 30 (any year)
 */
private fun getSemesterFromDate(eventDate: String): Int {
    try {
        val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH)
        val date = dateFormat.parse(eventDate)
        if (date != null) {
            val calendar = java.util.Calendar.getInstance()
            calendar.time = date
            
            val month = calendar.get(java.util.Calendar.MONTH) + 1
            val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
            
            // Semester 1: July 1 - December 10
            if (month in 7..11) {
                return 1
            } else if (month == 12 && day <= 10) {
                return 1
            }
            // Semester 2: December 11 - June 30
            else if (month == 12 && day >= 11) {
                return 2
            } else if (month in 1..6) {
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

private fun getFormattedDateDayMonth(dateString: String): String {
    return try {
        // Input format: "dd MMM yyyy"
        val inputFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH)
        val date = inputFormat.parse(dateString)
        if (date != null) {
            // Output format: "dd MMM"
            val outputFormat = java.text.SimpleDateFormat("dd MMM", java.util.Locale.ENGLISH)
            return outputFormat.format(date)
        }
        dateString.split(" ").take(2).joinToString(" ")
    } catch (e: Exception) {
        dateString.split(" ").take(2).joinToString(" ")
    }
}
