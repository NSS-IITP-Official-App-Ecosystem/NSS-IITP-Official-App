package com.phad.chatapp.features.events

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.viewmodels.AttendanceViewModel
import com.phad.chatapp.viewmodels.AttendanceViewModelFactory
import kotlinx.coroutines.launch

class EventHistoryActivity : ComponentActivity() {
    private val TAG = "EventHistoryActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                EventHistoryScreen(
                    onBackClick = { finish() }
                )
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
    var filteredEvents by remember { mutableStateOf<List<AttendanceEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showMakeLiveDialog by remember { mutableStateOf<AttendanceEvent?>(null) }
    var lastRefreshTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var searchQuery by remember { mutableStateOf("") }
    var showFilterDialog by remember { mutableStateOf(false) }
    var fromDateMillis by remember { mutableStateOf<Long?>(null) }
    var toDateMillis by remember { mutableStateOf<Long?>(null) }
    var mandatoryOnly by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Load events function with caching optimization
    fun loadEvents(forceRefresh: Boolean = false) {
        coroutineScope.launch {
            try {
                val currentTime = System.currentTimeMillis()
                
                // Check if we need to refresh (5 minutes cache or force refresh)
                if (!forceRefresh && (currentTime - lastRefreshTime) < 300000) { // 5 minutes
                    Log.d("EventHistoryScreen", "Using cached data")
                    return@launch
                }
                
                Log.d("EventHistoryScreen", "=== Loading closed events ===")
                if (forceRefresh) {
                    isRefreshing = true
                } else {
                    isLoading = true
                }
                
                // First test database connection
                val connectionTest = viewModel.testDatabaseConnection()
                Log.d("EventHistoryScreen", "Connection test result: $connectionTest")
                
                closedEvents = viewModel.getClosedEvents()
                lastRefreshTime = currentTime
                Log.d("EventHistoryScreen", "Received ${closedEvents.size} closed events")
                closedEvents.forEach { event ->
                    Log.d("EventHistoryScreen", "Event: ${event.id} - ${event.description} - isLive: ${event.isLive}")
                }
                Log.d("EventHistoryScreen", "Loading completed")
            } catch (e: Exception) {
                Log.e("EventHistoryScreen", "Error loading closed events", e)
                Log.e("EventHistoryScreen", "Exception details: ${e.message}")
                e.printStackTrace()
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    // Apply filters and search
    fun applyFilters() {
        val queryLower = searchQuery.trim().lowercase()
        filteredEvents = closedEvents.filter { event ->
            val name = event.getEventName().lowercase()
            val matchesQuery = if (queryLower.isEmpty()) true else name.contains(queryLower)

            val eventDate = event.getEventDateAsDate()
            val inFrom = fromDateMillis?.let { eventDate.time >= it } ?: true
            val inTo = toDateMillis?.let { eventDate.time <= it } ?: true
            val dateOk = inFrom && inTo

            val mandatoryOk = if (mandatoryOnly) event.isMandatory else true

            matchesQuery && dateOk && mandatoryOk
        }
    }

    // Load events on first launch and auto-refresh on screen open
    LaunchedEffect(Unit) {
        loadEvents(forceRefresh = true) // Force refresh on screen open
    }

    // Re-apply filters when source changes
    LaunchedEffect(closedEvents) { applyFilters() }
    LaunchedEffect(searchQuery, fromDateMillis, toDateMillis, mandatoryOnly) { applyFilters() }

    Scaffold(
        topBar = {
            // Header copied exactly from QR Attendance screen with spacing above
            Column {
                // Add spacing above the header
                Spacer(modifier = Modifier.height(80.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                    // Back button on the left
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Center content with Event icon and title
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = "Event History",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Event History",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Refresh button on the right
                    IconButton(
                        onClick = { loadEvents(forceRefresh = true) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Events",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Search and Filter row just below header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(text = "Search by event name") }
                )
                IconButton(onClick = { showFilterDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Filter",
                        tint = Color(0xFF2196F3)
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredEvents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No events found",
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Debug: Check logs for details",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = Color.Gray.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                Text(
                    text = "Closed Events (${filteredEvents.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
    }

    // Make Live Confirmation Dialog
    showMakeLiveDialog?.let { event ->
        AlertDialog(
            onDismissRequest = { showMakeLiveDialog = null },
            title = { Text("Make Event Live") },
            text = { 
                Text("Are you sure you want to make \"${event.description}\" live again? This will allow students to mark attendance for this event.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                viewModel.makeEventLive(event)
                                // Auto-refresh the list after making event live
                                loadEvents(forceRefresh = true)
                                showMakeLiveDialog = null
                            } catch (e: Exception) {
                                Log.e("EventHistoryScreen", "Error making event live", e)
                                showMakeLiveDialog = null
                            }
                        }
                    }
                ) {
                    Text("Make Live")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMakeLiveDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    // Filter dialog
    FilterDialog(
        show = showFilterDialog,
        onDismiss = { showFilterDialog = false },
        fromDateMillis = fromDateMillis,
        toDateMillis = toDateMillis,
        mandatoryOnly = mandatoryOnly,
        onFromDateChange = { fromDateMillis = it },
        onToDateChange = { toDateMillis = it },
        onMandatoryChange = { mandatoryOnly = it },
        onApply = { /* applyFilters will react via state */ }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    fromDateMillis: Long?,
    toDateMillis: Long?,
    mandatoryOnly: Boolean,
    onFromDateChange: (Long?) -> Unit,
    onToDateChange: (Long?) -> Unit,
    onMandatoryChange: (Boolean) -> Unit,
    onApply: () -> Unit
) {
    if (!show) return

    // States for date pickers
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    val fromState = rememberDatePickerState(initialSelectedDateMillis = fromDateMillis)
    val toState = rememberDatePickerState(initialSelectedDateMillis = toDateMillis)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Events") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // From date selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "From Date: " + (fromDateMillis?.let { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH).format(java.util.Date(it)) } ?: "Not set"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showFromPicker = true }) { Text("Pick") }
                        if (fromDateMillis != null) {
                            TextButton(onClick = { onFromDateChange(null) }) { Text("Clear") }
                        }
                    }
                }

                // To date selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "To Date: " + (toDateMillis?.let { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH).format(java.util.Date(it)) } ?: "Not set"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showToPicker = true }) { Text("Pick") }
                        if (toDateMillis != null) {
                            TextButton(onClick = { onToDateChange(null) }) { Text("Clear") }
                        }
                    }
                }

                // Mandatory toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(checked = mandatoryOnly, onCheckedChange = { onMandatoryChange(it) })
                    Text(text = "Mandatory only")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply()
                onDismiss()
            }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showFromPicker) {
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onFromDateChange(fromState.selectedDateMillis)
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = fromState)
        }
    }

    if (showToPicker) {
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onToDateChange(toState.selectedDateMillis)
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = toState)
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
                // Date and Time in one row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Date information
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Date",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = event.eventDate,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF333333)
                        )
                    }

                    // Time information
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Time",
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = event.eventTime,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF333333)
                        )
                    }
                }

                // Location and Hours in one row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Location information
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (event.location.isNotBlank()) event.location else "Not Specified",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (event.location.isNotBlank()) Color(0xFF333333) else Color.Gray
                        )
                    }

                    // Hours information
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Hours",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (event.hours > 0) "${event.hours}" else "Not Specified",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (event.hours > 0) Color(0xFF333333) else Color.Gray
                        )
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
