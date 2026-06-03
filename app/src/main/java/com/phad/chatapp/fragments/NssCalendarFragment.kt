package com.phad.chatapp.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.repositories.AttendanceQRRepository
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.viewmodels.QRAttendanceViewModel
import com.phad.chatapp.viewmodels.QRAttendanceViewModelFactory
import kotlinx.coroutines.launch
import com.phad.chatapp.ui.components.GradientHeader

/**
 * NSS Calendar screen (app module) replacing the black screen on calendar tab.
 * - Shows Upcoming (live/future) events
 * - Shows Past events (history)
 * - Allows Admins to create new future events (reuses CreateEventDialog from QR Attendance)
 * - Uses the same visual language as the QR Attendance admin screen
 */
class NssCalendarFragment : Fragment() {
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: QRAttendanceViewModel
    private val repository = AttendanceQRRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(requireContext())
        val factory = QRAttendanceViewModelFactory(requireActivity().application)
        viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[QRAttendanceViewModel::class.java]
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val adminUi by viewModel.adminUiState.collectAsState()
                var allEvents by remember { mutableStateOf<List<AttendanceEvent>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                var currentMonth by remember { mutableStateOf(YearMonth.now()) }
                var selectedDate by remember { mutableStateOf(LocalDate.now()) }
                var showDayEventsDialog by remember { mutableStateOf(false) }
                var dayEvents by remember { mutableStateOf<List<AttendanceEvent>>(emptyList()) }

                var dayTitle by remember { mutableStateOf("") }
                var userWings by remember { mutableStateOf<List<String>>(emptyList()) }
                val currentUserRollNumber = sessionManager.fetchUserId()
                val isAdmin = sessionManager.fetchUserType().equals("Admin", ignoreCase = true)


                // Load events on first composition and when dialog dismiss triggers refresh
                LaunchedEffect(Unit, adminUi.createEventSuccess) {
                    isLoading = true
                    errorMessage = null
                    
                    // Fetch user wings if not admin
                    if (!isAdmin && currentUserRollNumber.isNotBlank()) {
                         val wingsResult = repository.getUserWings(currentUserRollNumber)
                         userWings = wingsResult.getOrNull() ?: emptyList()
                    }

                    // After successful creation, force refresh; otherwise use cache
                    val all = repository.getAllEvents(forceRefresh = adminUi.createEventSuccess)
                    allEvents = all.getOrNull().orEmpty()
                    errorMessage = all.exceptionOrNull()?.message
                    isLoading = false
                }

                // Filter events based on user wings
                val filteredEvents = remember(allEvents, userWings, isAdmin) {
                    if (isAdmin) {
                        allEvents
                    } else {
                        allEvents.filter { event ->
                            // 4. If visibleOnlyToPresent is true, ONLY show if user is present
                            
                            val isPresent = event.hasStudentAttended(currentUserRollNumber)
                            
                            if (event.visibleOnlyToPresent) {
                                isPresent
                            } else {
                                val hasMatchingWing = event.wings.any { it in userWings }
                                val isDNCEvent = event.wings.contains("Design and Curation Wing")
                                
                                hasMatchingWing || isDNCEvent || isPresent
                            }
                        }
                    }
                }

                Scaffold(
                ) { padding ->
                    // Pull to refresh state
                    val pullRefreshState = rememberPullToRefreshState()
                    val refreshScope = rememberCoroutineScope()
                    var isRefreshing by remember { mutableStateOf(false) }

                    val onRefresh: () -> Unit = {
                        isRefreshing = true
                        refreshScope.launch {
                            isLoading = true
                            val all = repository.getAllEvents(forceRefresh = true)
                            allEvents = all.getOrNull().orEmpty()
                            errorMessage = all.exceptionOrNull()?.message
                            isLoading = false
                            isRefreshing = false
                        }
                    }

                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        state = pullRefreshState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = padding.calculateBottomPadding())
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFFF8F9FA),
                                            Color(0xFFE9ECEF)
                                        )
                                    )
                                )
                                // Removed padding(16.dp) to extend header width
                                .verticalScroll(rememberScrollState())
                        ) {
                        // Header Design - Title only
                        GradientHeader(
                            title = "Event Calendar",
                            icon = Icons.Default.CalendarMonth,
                            onBackClick = null,
                            isTitleCentered = true
                            // Removed refresh action
                        )

                        // Add padding for content below header since we removed parent padding
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Spacer(Modifier.height(16.dp))

                        // Month navigation row - without card wrapper
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { currentMonth = currentMonth.minusMonths(1) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowLeft,
                                    contentDescription = "Previous Month",
                                    tint = Color(0xFF666666),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                                    color = Color(0xFF333333),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = currentMonth.year.toString(),
                                    color = Color(0xFF999999),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )
                            }

                            IconButton(
                                onClick = { currentMonth = currentMonth.plusMonths(1) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = "Next Month",
                                    tint = Color(0xFF666666),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        if (isLoading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            errorMessage?.let { msg ->
                                if (msg.isNotBlank()) {
                                    Text(
                                        text = msg,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                // Calendar grid
                                CalendarGrid(
                                    month = currentMonth,
                                    events = filteredEvents,
                                    onDayClick = { date, eventsOnDay ->
                                        selectedDate = date
                                        if (eventsOnDay.isNotEmpty()) {
                                            // Show dialog with events for the selected day
                                            // Filter eventsOnDay based on strict visibility rules before showing dialog
                                            val filteredDayEvents = eventsOnDay.filter { event ->
                                                // Strict visibility check: "Visible only to Attendees" overrides all other logic
                                                if (event.visibleOnlyToPresent) {
                                                    // If not admin AND not attended AND visibility restricted -> SKIP
                                                    !(!isAdmin && !event.hasStudentAttended(currentUserRollNumber))
                                                } else {
                                                    // Standard Wing-based filtering for events NOT restricted strictly to attendees
                                                    if (!isAdmin) {
                                                        // Use captured userWings from onCreateView scope
                                                        val isRelevant = event.wings.isEmpty() || // Open logic (if no wings specified)
                                                                        event.wings.containsAll(AttendanceEvent.ALL_WINGS) || // "Open Event"
                                                                        event.wings.any { it in userWings } || // User's wing
                                                                        event.wings.contains("Design and Curation Wing") || // DNC events visible to all
                                                                        event.hasStudentAttended(currentUserRollNumber) // Actually attended

                                                        isRelevant
                                                    } else {
                                                        true // Admins see all events
                                                    }
                                                }
                                            }

                                            if (filteredDayEvents.isNotEmpty()) {
                                                dayEvents = filteredDayEvents
                                                dayTitle = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${date.year}"
                                                showDayEventsDialog = true
                                            }
                                        }
                                        // Empty days are now non-interactive - no action taken
                                    }
                                )

                                val now = YearMonth.now()
                                when {
                                    currentMonth.isAfter(now) -> {
                                        val upcomingEvent = findNextUpcomingEvent(filteredEvents, currentMonth)
                                        if (upcomingEvent != null) {
                                            UpcomingEventCard(event = upcomingEvent)
                                        } else {
                                            EmptyStateCard("No upcoming events for this month.")
                                        }
                                    }
                                    currentMonth.isBefore(now) -> {
                                        EventCountCard(events = filteredEvents, month = currentMonth, isAdmin = isAdmin, currentUserRollNumber = sessionManager.fetchUserId())
                                    }
                                    else -> {
                                        val upcomingEvent = findNextUpcomingEvent(filteredEvents, currentMonth)
                                        if (upcomingEvent != null) {
                                            UpcomingEventCard(event = upcomingEvent)
                                        }
                                    }
                                }
                            }

                            if (showDayEventsDialog) {
                                DayEventsDialog(
                                    title = dayTitle,
                                    events = dayEvents,
                                    isAdmin = isAdmin,
                                    onDismiss = { showDayEventsDialog = false },
                                    onCreateEvent = {
                                        showDayEventsDialog = false
                                        viewModel.showCreateEventDialog()
                                    }
                                )
                            }

                            Spacer(Modifier.height(80.dp)) // For floating nav space
                        }

                        // Day-of-week header and grid composable definitions below

                        } // End content padding column
                    } // End main column
                    
                    } // End Box
                } // End Scaffold

                // Reuse existing dialog from QR Attendance for creating new events
                if (adminUi.showCreateEventDialog) {
                    val preselected = java.util.Date.from(selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
                    CreateEventDialog(
                        isCreating = adminUi.isCreatingEvent,
                        onCreateEvent = { name, desc, location, date, open, close, hours, isMandatory, negativeHours, wings, visibleOnlyToPresent, allowedAttendanceMode ->
                            viewModel.createAttendanceEvent(name, desc, location, date, open, close, hours, isMandatory, negativeHours, wings, visibleOnlyToPresent, allowedAttendanceMode)
                        },
                        onDismiss = { viewModel.hideCreateEventDialog() },
                        errorMessage = adminUi.errorMessage,
                        initialDate = preselected
                    )
                }

                // Show success toast
                LaunchedEffect(adminUi.createEventSuccess) {
                    if (adminUi.createEventSuccess) {
                        Toast.makeText(requireContext(), "Event created successfully", Toast.LENGTH_SHORT).show()
                        viewModel.clearCreateEventSuccess()
                    }
                }
            }
        }
    }
}

@Composable
private fun DayEventsDialog(
    title: String,
    events: List<AttendanceEvent>,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onCreateEvent: () -> Unit
) {
    // Get current user's roll number for attendance status checking
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val currentUserRollNumber = remember { sessionManager.fetchUserId() }

    // Sort events by starting time
    val sortedEvents = remember(events) {
        events.sortedBy { event ->
            // Parse the start time from the event time string
            val timeRange = event.getTimeRangeString()
            val timePair = com.phad.chatapp.utils.AttendanceEventUtils.parseTimeRange(timeRange)
            timePair?.first?.time ?: Long.MAX_VALUE // Put events with invalid time at the end
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF1A1A1A)
            )
        },
        text = {
            if (sortedEvents.isEmpty()) {
                Text(
                    text = "No events on this day",
                    color = Color(0xFF666666),
                    fontSize = 14.sp
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .widthIn(min = 320.dp, max = 480.dp) // Expand dialog width for better content spacing
                ) {
                    items(sortedEvents) { event ->
                        EventDetailsCard(
                            event = event,
                            isAdmin = isAdmin,
                            currentUserRollNumber = currentUserRollNumber
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isAdmin && sortedEvents.isEmpty()) {
                TextButton(
                    onClick = onCreateEvent,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text(
                        text = "Create Event",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF666666)
                )
            ) {
                Text(
                    text = "Close",
                    fontWeight = FontWeight.Medium
                )
            }
        },
        modifier = Modifier.widthIn(min = 340.dp, max = 500.dp) // Set overall dialog width constraints
    )
}

@Composable
private fun EventDetailsCard(
    event: AttendanceEvent,
    isAdmin: Boolean,
    currentUserRollNumber: String
) {
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    val maxDescriptionLength = 100

    // Check attendance status for non-admin users
    val attendanceStatus = if (!isAdmin && currentUserRollNumber.isNotBlank()) {
        if (event.hasStudentAttended(currentUserRollNumber)) "Present" else "Absent"
    } else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (event.isMandatory) Color(0xFFFFFDE7) else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Event name with improved typography
            Text(
                text = event.getEventName(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = Color(0xFF1A1A1A)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Description with expand/collapse functionality
            if (event.description.isNotBlank()) {
                val displayText = if (event.description.length > maxDescriptionLength && !isDescriptionExpanded) {
                    "${event.description.take(maxDescriptionLength)}..."
                } else {
                    event.description
                }

                Text(
                    text = displayText,
                    fontSize = 14.sp,
                    color = Color(0xFF555555),
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (event.description.length > maxDescriptionLength) {
                                isDescriptionExpanded = !isDescriptionExpanded
                            }
                        }
                )

                if (event.description.length > maxDescriptionLength) {
                    Text(
                        text = if (isDescriptionExpanded) "Show less" else "Show more",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Event details with improved visual hierarchy - vertical layout for better readability
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date information on its own line
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Date",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = event.getFormattedEventDate(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF333333)
                    )
                }

                // Time information on its own line
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Time",
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = event.getFormattedTimeRange(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF333333)
                    )
                }

                // Location information (always show icon, with placeholder if blank)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = Color(0xFFE91E63),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (event.location.isNotBlank()) event.location else "Not specified",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (event.location.isNotBlank()) Color(0xFF333333) else Color.Gray
                    )
                }

                // Hours information (always show icon, with placeholder if 0)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Hours",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (event.hours > 0) {
                            if (event.isMandatory && event.negativeHours > 0) {
                                "${com.phad.chatapp.utils.AttendanceEventUtils.formatHours(event.hours)} / -${com.phad.chatapp.utils.AttendanceEventUtils.formatHours(event.negativeHours)}"
                            } else {
                                "${com.phad.chatapp.utils.AttendanceEventUtils.formatHours(event.hours)}"
                            }
                        } else "Not specified",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (event.hours > 0) Color(0xFF333333) else Color.Gray
                    )
                }



                // Wings information
                if (event.wings.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Category, // Using Category icon for Wings
                            contentDescription = "Wings",
                            tint = Color(0xFF673AB7), // Deep Purple
                            modifier = Modifier.size(18.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = event.getDisplayWings(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF333333)
                        )
                    }
                }

                // Attendees count (for admin) or attendance status (for non-admin) on its own line
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isFutureEvent = event.getEventDateAsDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().isAfter(LocalDate.now())

                    if (isAdmin) {
                        // Show attendees count for admin
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = "Attendees",
                            tint = Color(0xFF9C27B0),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Attendees",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF333333)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0xFFFF5722),
                                    shape = CircleShape
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = event.getAttendeeCount().toString(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (!isFutureEvent) { // Only show attendance status for past or current events
                        // Show attendance status for non-admin users
                        attendanceStatus?.let { status ->
                            Icon(
                                imageVector = if (status == "Present") Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = "Attendance Status",
                                tint = if (status == "Present") Color(0xFF4CAF50) else Color(0xFFF44336),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = status,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (status == "Present") Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF333333),
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun EmptyState(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            color = Color(0xFF5D4037)
        )
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    events: List<AttendanceEvent>,
    onDayClick: (LocalDate, List<AttendanceEvent>) -> Unit
) {
    val firstOfMonth = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()
    // Convert DayOfWeek to Sunday-first index (Sunday=0, Monday=1, etc.)
    val firstDayOfWeekIndex = when (firstOfMonth.dayOfWeek) {
        java.time.DayOfWeek.SUNDAY -> 0
        java.time.DayOfWeek.MONDAY -> 1
        java.time.DayOfWeek.TUESDAY -> 2
        java.time.DayOfWeek.WEDNESDAY -> 3
        java.time.DayOfWeek.THURSDAY -> 4
        java.time.DayOfWeek.FRIDAY -> 5
        java.time.DayOfWeek.SATURDAY -> 6
    }

    // Group events by LocalDate derived from AttendanceEvent.eventDate
    val eventsByDate = remember(events) {
        events.groupBy { ev ->
            val date = ev.getEventDateAsDate()
            date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        // Enhanced Days of week header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            val dayNames = listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                dayNames.forEach { dayName ->
                    Text(
                        text = dayName,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3),
                        fontSize = 14.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        val totalCells = firstDayOfWeekIndex + daysInMonth
        val rows = (totalCells + 6) / 7
        val gridHeight = rows * 60 // Reduced multiplier

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight.dp) // Set the calculated height
                .padding(horizontal = 4.dp)
        ) {
            // Leading blanks
            items(firstDayOfWeekIndex) {
                Box(Modifier.aspectRatio(1.0f))
            }
            // Days
            items(daysInMonth) { dayOffset ->
                val day = dayOffset + 1
                val date = month.atDay(day)
                val dayEvents = eventsByDate[date].orEmpty()
                DayCell(date, dayEvents, onClick = { onDayClick(date, dayEvents) })
            }
            // Trailing blanks to complete rows
            val trailing = rows * 7 - totalCells
            if (trailing > 0) {
                items(trailing) {
                    Box(Modifier.aspectRatio(1.0f))
                }
            }
        }
    }
}

/**
 * Determines the text color for a calendar day based on background color and current day status
 * Current day (today) always gets royal blue text color regardless of background color
 */
private fun getDayTextColor(
    backgroundColor: Color,
    isToday: Boolean
): Color {
    val RoyalBlue = Color(0xFF4169E1) // Define Royal Blue color
    return when {
        // For current day, ALWAYS use royal blue text regardless of background color
        isToday -> RoyalBlue

        // For colored backgrounds (green, red, orange), use black text for better contrast
        backgroundColor == Color(0xFF4CAF50) || // Green
        backgroundColor == Color(0xFFF44336) || // Red
        backgroundColor == Color(0xFFFFCC00) -> Color.Black // Orange

        // For white/transparent backgrounds, use dark gray text
        else -> Color(0xFF212121)
    }
}

/**
 * Determines the background color for a calendar day based on user role and attendance status
 * Current day (today) will get role-based background colors when events are present,
 * with blue text color applied separately to indicate it's the current day
 */
@Composable
private fun getDayBackgroundColor(
    date: LocalDate,
    events: List<AttendanceEvent>,
    isAdmin: Boolean,
    currentUserRollNumber: String,
    isToday: Boolean,
    isFutureDate: Boolean,
    hasEvents: Boolean
): Color {
    return when {
        // No events on this day (including current day with no events)
        !hasEvents -> Color.White

        // Admin logic (applies to all days including current day)
        isAdmin -> {
            val adminEventStatus = getAdminEventStatusForDay(date, events, isFutureDate, currentUserRollNumber)
            when (adminEventStatus) {
                AdminEventStatus.ALL_EVENTS_CREATED -> Color(0xFF4CAF50) // Green - all events created by current user
                AdminEventStatus.MISSING_EVENTS -> Color(0xFFFFCC00) // Orange - events created by others or mixed ownership
                AdminEventStatus.NO_EVENTS_NEEDED -> Color.White // Default white for days with no events
            }
        }

        // Regular user logic
        else -> {
            if (isFutureDate) {
                // Orange background for future dates with upcoming events
                Color(0xFFFFCC00) // Orange
            } else {
                // For past/current dates, check attendance status
                val userAttendanceStatus = getUserAttendanceStatusForDay(events, currentUserRollNumber)
                when (userAttendanceStatus) {
                    AttendanceStatus.PRESENT_ALL -> Color(0xFF4CAF50) // Green - present for all events
                    AttendanceStatus.MISSING_SOME -> Color(0xFFF44336) // Red - missing from some events
                    AttendanceStatus.NO_ATTENDANCE_DATA -> Color(0xFFE8F5E8) // Light green - default for events
                }
            }
        }
    }
}

/**
 * Enum to represent user attendance status for a day
 */
private enum class AttendanceStatus {
    PRESENT_ALL,      // User is present for all events on this day
    MISSING_SOME,     // User is missing from one or more events on this day
    NO_ATTENDANCE_DATA // No attendance data available
}

/**
 * Enum to represent admin event ownership status for a day
 */
private enum class AdminEventStatus {
    ALL_EVENTS_CREATED, // All events on this day were created by the current user
    MISSING_EVENTS,     // Some events on this day were created by other users
    NO_EVENTS_NEEDED    // No events are needed for this day
}

/**
 * Determines admin event creation status for a given day based on event ownership
 */
private fun getAdminEventStatusForDay(
    date: LocalDate,
    events: List<AttendanceEvent>,
    isFutureDate: Boolean,
    currentUserRollNumber: String
): AdminEventStatus {
    if (events.isEmpty()) {
        return if (isFutureDate) {
            // For future dates with no events, admin might need to create events
            // This is a simplified logic - in a real scenario, you might check against
            // a schedule or requirements for what events should exist
            AdminEventStatus.MISSING_EVENTS
        } else {
            // For past dates with no events, assume no events were needed
            AdminEventStatus.NO_EVENTS_NEEDED
        }
    }

    // Debug logging to understand the data
    Log.d("CalendarDebug", "=== Checking events for date: $date ===")
    Log.d("CalendarDebug", "Current user roll number: '$currentUserRollNumber'")
    Log.d("CalendarDebug", "Number of events: ${events.size}")

    events.forEachIndexed { index, event ->
        Log.d("CalendarDebug", "Event $index:")
        Log.d("CalendarDebug", "  - ID: ${event.id}")
        Log.d("CalendarDebug", "  - Description: ${event.description}")
        Log.d("CalendarDebug", "  - CreatedBy: '${event.createdBy}' (length: ${event.createdBy.length})")
        Log.d("CalendarDebug", "  - CreatorName: '${event.creatorName}'")
        Log.d("CalendarDebug", "  - Current user: '$currentUserRollNumber' (length: ${currentUserRollNumber.length})")
        Log.d("CalendarDebug", "  - Exact match: ${event.createdBy == currentUserRollNumber}")
        Log.d("CalendarDebug", "  - Case-insensitive match: ${event.createdBy.equals(currentUserRollNumber, ignoreCase = true)}")
        Log.d("CalendarDebug", "  - Trimmed match: ${event.createdBy.trim() == currentUserRollNumber.trim()}")
    }

    // Check if all events on this day were created by the current user
    // Use robust comparison that handles whitespace and case sensitivity
    val allEventsCreatedByCurrentUser = if (currentUserRollNumber.isEmpty()) {
        false // If we don't have a current user roll number, assume not all events are created by current user
    } else {
        events.all { event ->
            val eventCreator = event.createdBy.trim()
            val currentUser = currentUserRollNumber.trim()
            eventCreator.equals(currentUser, ignoreCase = true)
        }
    }

    Log.d("CalendarDebug", "All events created by current user: $allEventsCreatedByCurrentUser")

    // TEMPORARY WORKAROUND: If we can't determine the current user, show green for all events
    // This helps identify if the issue is with user identification vs event comparison
    val finalStatus = if (currentUserRollNumber.isEmpty()) {
        Log.w("CalendarDebug", "TEMPORARY WORKAROUND: No current user found, defaulting to GREEN for debugging")
        AdminEventStatus.ALL_EVENTS_CREATED // Show green when we can't identify current user
    } else if (allEventsCreatedByCurrentUser) {
        AdminEventStatus.ALL_EVENTS_CREATED // Green - all events created by current user
    } else {
        AdminEventStatus.MISSING_EVENTS // Orange - some events created by others
    }

    Log.d("CalendarDebug", "Returning status: ${when(finalStatus) {
        AdminEventStatus.ALL_EVENTS_CREATED -> "ALL_EVENTS_CREATED (Green)"
        AdminEventStatus.MISSING_EVENTS -> "MISSING_EVENTS (Orange)"
        AdminEventStatus.NO_EVENTS_NEEDED -> "NO_EVENTS_NEEDED (White)"
    }}")
    Log.d("CalendarDebug", "=== End debug for date: $date ===")

    return finalStatus
}

/**
 * Determines user attendance status for all events on a given day
 */
private fun getUserAttendanceStatusForDay(
    events: List<AttendanceEvent>,
    currentUserRollNumber: String
): AttendanceStatus {
    if (events.isEmpty() || currentUserRollNumber.isBlank()) {
        return AttendanceStatus.NO_ATTENDANCE_DATA
    }

    val attendedEvents = events.count { event ->
        event.hasStudentAttended(currentUserRollNumber)
    }

    return when {
        attendedEvents == events.size -> AttendanceStatus.PRESENT_ALL
        attendedEvents < events.size -> AttendanceStatus.MISSING_SOME
        else -> AttendanceStatus.NO_ATTENDANCE_DATA
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    events: List<AttendanceEvent>,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val isAdmin = sessionManager.fetchUserType().equals("Admin", ignoreCase = true)

    // Try multiple methods to get the current user's roll number
    val currentUserRollNumber = sessionManager.fetchUserId().takeIf { it.isNotEmpty() }
        ?: sessionManager.fetchRollNumber()?.takeIf { it.isNotEmpty() }
        ?: run {
            // Fallback: try to get from Firebase Auth display name
            try {
                val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val displayName = firebaseUser?.displayName
                if (!displayName.isNullOrEmpty() && displayName.contains("|")) {
                    // Format is "UserType|RollNumber"
                    val parts = displayName.split("|")
                    if (parts.size >= 2) {
                        parts[1].trim()
                    } else ""
                } else ""
            } catch (e: Exception) {
                Log.w("CalendarDebug", "Failed to get roll number from Firebase Auth: ${e.message}")
                ""
            }
        }



    val hasEvents = events.isNotEmpty()
    val isToday = date == LocalDate.now()
    val isFutureDate = date.isAfter(LocalDate.now())

    // Determine background color based on user role and attendance status
    val backgroundColor = getDayBackgroundColor(
        date = date,
        events = events,
        isAdmin = isAdmin,
        currentUserRollNumber = currentUserRollNumber,
        isToday = isToday,
        isFutureDate = isFutureDate,
        hasEvents = hasEvents
    )

    Card(
        modifier = Modifier
            .aspectRatio(1.0f) // Square aspect ratio for better grid alignment
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp) // Minimum size for better touch targets
            .height(70.dp) // Set a fixed height
            .then(
                if (hasEvents) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier // Empty days are non-clickable
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp // Consistent elevation for all days
        ),
        shape = RoundedCornerShape(8.dp),
        border = when {
            // Keep event border for days with events (excluding today)
            hasEvents && !isToday -> BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f))
            else -> null
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Blue star in top-right if any mandatory event exists for this day
            val hasMandatory = events.any { it.isMandatory }
            if (hasMandatory) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .size(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Mandatory Event",
                        tint = Color(0xFF1E88E5), // Blue
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    fontWeight = FontWeight.SemiBold, // Consistent font weight for all days
                    color = getDayTextColor(
                        backgroundColor = backgroundColor,
                        isToday = isToday
                    ),
                    fontSize = 14.sp, // Reduced from 20.sp to fit two-digit dates on smaller screens
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Visible
                )
            }
        }
    }
}

private fun findNextUpcomingEvent(events: List<AttendanceEvent>, month: YearMonth): AttendanceEvent? {
    Log.d("NssCalendarFragment", "Finding next upcoming event in ${month}. Total events: ${events.size}")
    val now = LocalDate.now()
    val currentTime = LocalTime.now()
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    val upcomingEvents = events.filter { event ->
        val eventDate = event.getEventDateAsDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val eventMonth = YearMonth.from(eventDate)

        if (eventMonth.equals(month)) {
            if (eventDate.isAfter(now)) {
                true
            } else if (eventDate.isEqual(now)) {
                try {
                    val timeParts = event.eventTime.split(" - ")
                    if (timeParts.size == 2) {
                        val endTimeString = timeParts[1]
                        val endTime = LocalTime.parse(endTimeString, timeFormatter)
                        endTime.isAfter(currentTime)
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }
            } else {
                false
            }
        } else {
            false
        }
    }

    Log.d("NssCalendarFragment", "Found ${upcomingEvents.size} upcoming events in ${month}.")
    
    // Sort events by date first, then by start time for events on the same date
    val nextEvent = upcomingEvents.minByOrNull { event ->
        val eventDate = event.getEventDateAsDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val timeRange = event.getTimeRangeString()
        val timePair = com.phad.chatapp.utils.AttendanceEventUtils.parseTimeRange(timeRange)
        val startTime = timePair?.first?.time ?: Long.MAX_VALUE
        
        // Create a comparable value: date as epoch day + start time in milliseconds
        eventDate.toEpochDay() * 86400000L + (startTime % 86400000L)
    }
    
    if (nextEvent != null) {
        Log.d("NssCalendarFragment", "Next upcoming event in ${month}: ${nextEvent.getEventName()}")
    } else {
        Log.d("NssCalendarFragment", "No upcoming events found in ${month}.")
    }
    return nextEvent
}

@Composable
private fun UpcomingEventCard(event: AttendanceEvent) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Upcoming Event",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2196F3),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = if (event.isMandatory) Color(0xFFFFFDE7) else Color(0xFFFFFFFF))
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Event name with improved typography
                Text(
                    text = event.getEventName(),
                    fontSize = 24.sp, // Increased font size
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = Color(0xFF1A1A1A)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Description
                if (event.description.isNotBlank()) {
                    Text(
                        text = event.description,
                        fontSize = 16.sp, // Increased font size
                        color = Color(0xFF555555),
                        lineHeight = 22.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Event details in a 2-column grid
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Column 1: Date and Hours (40%)
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.weight(0.4f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Date",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = event.getFormattedEventDate(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF333333)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Hours",
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (event.hours > 0) {
                                    if (event.isMandatory && event.negativeHours > 0) {
                                        "${com.phad.chatapp.utils.AttendanceEventUtils.formatHours(event.hours)} / -${com.phad.chatapp.utils.AttendanceEventUtils.formatHours(event.negativeHours)}"
                                    } else {
                                        com.phad.chatapp.utils.AttendanceEventUtils.formatHours(event.hours)
                                    }
                                } else "Not specified",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (event.hours > 0) Color(0xFF333333) else Color.Gray
                            )
                        }
                    }

                    // Column 2: Time and Location (60%)
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.weight(0.6f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Time",
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = event.getFormattedTimeRange(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF333333)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Location",
                                tint = Color(0xFFE91E63),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (event.location.isNotBlank()) event.location else "Not specified",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (event.location.isNotBlank()) Color(0xFF333333) else Color.Gray
                            )
                        }
                    }
                }

                // Wings Information - Vertical List
                if (event.wings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp)) 
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically, 
                        horizontalArrangement = Arrangement.Start
                    ) {
                        // Wings Section
                        Row(
                            verticalAlignment = Alignment.CenterVertically 
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = "Wing",
                                tint = Color(0xFF673AB7), 
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = event.getDisplayWings(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF333333)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)) // Light orange background
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFD84315) // Dark orange text
            )
        }
    }
}

@Composable
private fun EventCountCard(events: List<AttendanceEvent>, month: YearMonth, isAdmin: Boolean, currentUserRollNumber: String) {
    val eventsInMonth = events.filter { event ->
        val eventDate = event.getEventDateAsDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        YearMonth.from(eventDate) == month
    }.sortedBy { it.getEventDateAsDate() } // Sort events by date

    if (eventsInMonth.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Events in ${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2196F3),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    eventsInMonth.forEach { event ->
                        val attended = event.hasStudentAttended(currentUserRollNumber)
                        val backgroundColor = if (!isAdmin) {
                            when {
                                attended -> Color(0xFFE8F5E9) // Light Green
                                event.isMandatory && !attended -> Color(0xFFFFEBEE) // Light Red
                                else -> Color.Transparent
                            }
                        } else Color.Transparent

                        Surface(
                            color = backgroundColor,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp), // Add padding inside the colored row
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Column 1: Date (20%) - No Year
                                val fullDate = event.getFormattedEventDate()
                                val dateNoYear = if (fullDate.split(" ").size >= 3) {
                                    fullDate.substringBeforeLast(" ")
                                } else {
                                    fullDate
                                }

                                Text(
                                    text = dateNoYear,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFA000),
                                    modifier = Modifier.weight(0.25f)
                                )

                                // Column 2: Name (40%)
                                Text(
                                    text = event.getEventName(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Black,
                                    modifier = Modifier.weight(0.4f)
                                )

                                // Column 3: Wing (35%)
                                Column(
                                    modifier = Modifier.weight(0.35f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    if (event.wings.isNotEmpty()) {
                                        if (event.wings.containsAll(com.phad.chatapp.models.AttendanceEvent.ALL_WINGS)) {
                                            Text(
                                                text = "Open Event",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = Color.Gray,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        } else {
                                            event.wings.forEach { wing ->
                                                Text(
                                                    text = wing,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = Color.Gray,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.End
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "No Wing",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = Color.Gray,
                                            textAlign = TextAlign.End
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Events in ${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2196F3),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No events found for this month.",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }
            }
        }
    }
}
