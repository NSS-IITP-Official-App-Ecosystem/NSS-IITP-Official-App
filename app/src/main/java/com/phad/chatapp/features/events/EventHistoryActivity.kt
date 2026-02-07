package com.phad.chatapp.features.events

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Category
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.viewmodels.AttendanceViewModel
import com.phad.chatapp.viewmodels.AttendanceViewModelFactory
import com.phad.chatapp.utils.SessionManager // Added import
import com.phad.chatapp.ui.components.GradientHeader
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class EventHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                EventHistoryScreen(onBackClick = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventHistoryScreen(
    onBackClick: () -> Unit,
    viewModel: AttendanceViewModel = viewModel(factory = AttendanceViewModelFactory(LocalContext.current.applicationContext as android.app.Application))
) {
    var closedEvents by remember { mutableStateOf<List<AttendanceEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showMakeLiveDialog by remember { mutableStateOf<AttendanceEvent?>(null) }
    var lastRefreshTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // States for Refresh
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // Filter Stats
    var searchQuery by remember { mutableStateOf("") }
    var showFilterDialog by remember { mutableStateOf(false) }
    var fromDate by remember { mutableStateOf<LocalDate?>(null) }
    var toDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedWing by remember { mutableStateOf<String?>(null) }
    var mandatoryOnly by remember { mutableStateOf(false) }

    // Date Picker States
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    
    // User Info for Visibility Logic
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val isAdmin = remember { sessionManager.fetchUserType().equals("Admin", ignoreCase = true) }
    val currentUserRollNumber = remember { sessionManager.fetchUserId() }

    // Sync pull-to-refresh state
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            isRefreshing = true
            // Load events with force refresh
            try {
                // Determine if we should force refresh logic inside loadEvents
                // But here we'll just call the loading logic directly
                val currentTime = System.currentTimeMillis()
                closedEvents = viewModel.getClosedEvents()
                lastRefreshTime = currentTime
            } catch (e: Exception) {
                Log.e("EventHistory", "Error refreshing events", e)
            } finally {
                isRefreshing = false
                pullRefreshState.endRefresh()
            }
        }
    }

    // Load events function
    fun loadEvents(forceRefresh: Boolean = false) {
        coroutineScope.launch {
            try {
                isLoading = true
                val currentTime = System.currentTimeMillis()
                if (!forceRefresh && (currentTime - lastRefreshTime) < 300000) {
                     // Cache hit logic could go here, but for now we reload if simple call
                }
                closedEvents = viewModel.getClosedEvents()
                lastRefreshTime = currentTime
            } catch (e: Exception) {
                Log.e("EventHistoryScreen", "Error loading closed events", e)
            } finally {
                isLoading = false
            }
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        loadEvents(forceRefresh = true)
    }

    // Filter Logic
    val availableWings = remember(closedEvents) {
        closedEvents.flatMap { it.wings }.distinct().sorted()
    }

    val filteredEvents = remember(closedEvents, searchQuery, fromDate, toDate, selectedWing, mandatoryOnly) {
        closedEvents.filter { event ->
            // 1. Search
            val matchesSearch = if (searchQuery.isBlank()) true else {
                event.getEventName().contains(searchQuery, ignoreCase = true)
            }

            // 2. Date
            val eventDate = try {
                val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
                LocalDate.parse(event.eventDate, formatter)
            } catch (e: Exception) {
                 try {
                    val fallback = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
                    LocalDate.parse(event.eventDate, fallback)
                 } catch (e2: Exception) {
                     null
                 }
            }
            
            val matchesFromDate = fromDate?.let { start ->
                eventDate != null && !eventDate.isBefore(start)
            } ?: true
            
            val matchesToDate = toDate?.let { end ->
                eventDate != null && !eventDate.isAfter(end)
            } ?: true

            // 3. Wing
            val matchesWing = selectedWing?.let { wing ->
                event.wings.contains(wing)
            } ?: true

            // 4. Mandatory
            val matchesMandatory = if (mandatoryOnly) event.isMandatory else true

            // 5. Strict Visibility (Visible Only to Attendees)
            val matchesVisibility = if (event.visibleOnlyToPresent) {
                // If admin, they can see it. If student, MUST be an attendee.
                if (isAdmin) true else event.attendees.any { it.rollNumber == currentUserRollNumber }
            } else {
                true
            }

            matchesSearch && matchesFromDate && matchesToDate && matchesWing && matchesMandatory && matchesVisibility
        }
    }

    Scaffold(
        topBar = {
            GradientHeader(
                title = "Event History",
                icon = Icons.Default.History,
                onBackClick = onBackClick,
                isTitleCentered = true
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 2.dp)
            ) {
                // Inline Filter UI
                Box(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column {
                        // Row 1: Search Bar and Toggle Filter
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Search Bar
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f), // Removed fixed height to let it wrap text if needed, or stick to standard
                                placeholder = { Text("Search by event name", color = Color.Gray, fontSize = 14.sp) }, // Matched font size
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF2196F3),
                                    unfocusedBorderColor = Color.LightGray
                                ),
                                shape = RoundedCornerShape(25.dp),
                                leadingIcon = {
                                     Icon(
                                         imageVector = Icons.Default.Search, 
                                         contentDescription = "Search", 
                                         tint = Color.Gray,
                                         modifier = Modifier.size(20.dp)
                                     )
                                },
                                trailingIcon = {
                                     if(searchQuery.isNotEmpty()) {
                                         IconButton(onClick = { searchQuery = "" }) {
                                             Icon(
                                                 imageVector = Icons.Default.Close, 
                                                 contentDescription = "Clear", 
                                                 tint = Color.Gray, 
                                                 modifier = Modifier.size(20.dp)
                                             )
                                         }
                                     }
                                }
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            // Filter Toggle Button
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (showFilterDialog) MaterialTheme.colorScheme.primary else Color(0xFFE3F2FD)
                                ),
                                modifier = Modifier.size(56.dp),
                                onClick = {
                                    showFilterDialog = !showFilterDialog 
                                    if (!showFilterDialog) {
                                        fromDate = null
                                        toDate = null
                                        selectedWing = null
                                        mandatoryOnly = false
                                    }
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "Filters",
                                        tint = if (showFilterDialog) Color.White else Color(0xFF2196F3)
                                    )
                                }
                            }
                        }

                        // Expanded Filter Section
                        if (showFilterDialog) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Date Range
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
                                // From Date
                                OutlinedCard(
                                    onClick = { showFromPicker = true },
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp), // Increased padding
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.DateRange, "From", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = fromDate?.format(dateFormatter) ?: "From Date",
                                            fontSize = 13.sp,
                                            color = if (fromDate != null) Color.Black else Color.Gray,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // To Date
                                OutlinedCard(
                                    onClick = { showToPicker = true },
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.DateRange, "To", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = toDate?.format(dateFormatter) ?: "To Date",
                                            fontSize = 13.sp,
                                            color = if (toDate != null) Color.Black else Color.Gray,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Wing and Mandatory
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Wing Dropdown
                                var wingMenuExpanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedCard(
                                        onClick = { wingMenuExpanded = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = selectedWing ?: "All Wings",
                                                fontSize = 13.sp,
                                                color = if (selectedWing != null) Color.Black else Color.Gray,
                                                maxLines = 1
                                            )
                                            Icon(Icons.Default.ExpandMore, "Select Wing", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = wingMenuExpanded,
                                        onDismissRequest = { wingMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("All Wings") },
                                            onClick = { 
                                                selectedWing = null
                                                wingMenuExpanded = false
                                            }
                                        )
                                        availableWings.forEach { wing ->
                                            DropdownMenuItem(
                                                text = { Text(wing) },
                                                onClick = { 
                                                    selectedWing = wing
                                                    wingMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Mandatory Filter
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { mandatoryOnly = !mandatoryOnly }
                                ) {
                                    Checkbox(
                                        checked = mandatoryOnly,
                                        onCheckedChange = { mandatoryOnly = it }
                                    )
                                    Text("Mandatory", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filteredEvents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No events found", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredEvents) { event ->
                            ClosedEventCard(
                                event = event,
                                onMakeLiveClick = { showMakeLiveDialog = event }
                            )
                        }
                    }
                }
            }

            PullToRefreshContainer(
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Color.White,
                contentColor = Color(0xFF2196F3)
            )
        }
    }

    // Make Live Dialog
    showMakeLiveDialog?.let { event ->
        AlertDialog(
            onDismissRequest = { showMakeLiveDialog = null },
            title = { Text("Make Event Live") },
            text = { Text("Are you sure you want to make \"${event.description}\" live again?") },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch {
                        try {
                            viewModel.makeEventLive(event)
                            loadEvents(true)
                            showMakeLiveDialog = null
                        } catch (e: Exception) {
                            Log.e("EventHistory", "Error making event live", e)
                        }
                    }
                }) { Text("Make Live") }
            },
            dismissButton = {
                TextButton(onClick = { showMakeLiveDialog = null }) { Text("Cancel") }
            }
        )
    }

    // Date Picker Dialogs
    if (showFromPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = fromDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        fromDate = java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    fromDate = null
                    showFromPicker = false 
                }) { Text("Clear") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showToPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = toDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        toDate = java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    toDate = null
                    showToPicker = false 
                }) { Text("Clear") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}



@Composable
fun ClosedEventCard(
    event: AttendanceEvent,
    onMakeLiveClick: () -> Unit
) {
    // Extract event name from document ID (format: day_month_event_name)
    val eventName = if (event.id.isNotEmpty()) {
        val parts = event.id.split("_")
        if (parts.size >= 3) {
            // Join all parts after the first two (day and month) to get the event name
            parts.drop(2).joinToString("_").replace("_", " ")
        } else {
            event.description.ifEmpty { "Untitled Event" }
        }
    } else {
        event.description.ifEmpty { "Untitled Event" }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (event.isMandatory) Color(0xFFFFFDE7) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Event title - centered and bold
            Text(
                text = eventName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Event details with icons - compact layout
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Refactored Layout: 40% Left (Date/Hours), 60% Right (Time/Location)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    // Left Column (40%): Date + Hours
                    Column(
                        modifier = Modifier.weight(0.4f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Date (Full Date with Year)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Date",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = event.eventDate, // Using eventDate string directly as per existing code
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF333333)
                            )
                        }

                        // Hours
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Hours",
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (event.hours > 0) {
                                    if (event.isMandatory && event.negativeHours > 0) {
                                        "${com.phad.chatapp.utils.AttendanceEventUtils.formatHours(event.hours)} / -${com.phad.chatapp.utils.AttendanceEventUtils.formatHours(event.negativeHours)}"
                                    } else {
                                        com.phad.chatapp.utils.AttendanceEventUtils.formatHours(event.hours)
                                    }
                                } else "Not specified",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (event.hours > 0) Color(0xFF333333) else Color.Gray
                            )
                        }
                    }

                    // Right Column (60%): Time + Location
                    Column(
                        modifier = Modifier.weight(0.6f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Time
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Time",
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = event.eventTime, // Using eventTime string directly
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF333333)
                            )
                        }

                        // Location
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Location",
                                tint = Color(0xFFE91E63),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (event.location.isNotBlank()) event.location else "Not Specified",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (event.location.isNotBlank()) Color(0xFF333333) else Color.Gray
                            )
                        }
                    }
                }

                // Wings Information - Vertical List
                if (event.wings.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = "Wing",
                                tint = Color(0xFF673AB7),
                                modifier = Modifier.size(18.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                 Text(
                                    text = event.getDisplayWings(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF333333)
                                )
                            }
                        }
                    }
                }

                // Attendees information - centered and larger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = "Attendees",
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Attendees: ${event.getAttendeeCount()}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Single Make Live button with larger font
            Button(
                onClick = onMakeLiveClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Make Live",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Make Live",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
