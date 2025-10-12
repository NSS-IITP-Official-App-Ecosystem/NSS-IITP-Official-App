package com.phad.chatapp.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh

import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Download
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import java.util.Date
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.phad.chatapp.R
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.viewmodels.QRAttendanceViewModel
import com.phad.chatapp.viewmodels.QRAttendanceViewModelFactory
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.filled.Edit

/**
 * Fragment for Admin QR Attendance - Take Attendance functionality
 * Only accessible to Admin users in NSS interface
 */
class NssQRAttendanceFragment : Fragment() {
    private val TAG = "NssQRAttendanceFragment"
    
    private lateinit var viewModel: QRAttendanceViewModel
    private lateinit var sessionManager: SessionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(requireContext())
        
        // Check if user is admin
        val userType = sessionManager.fetchUserType()
        Log.d(TAG, "User type from SessionManager: '$userType'")

        // Check for Admin per new schema (case-insensitive)
        if (!userType.equals("Admin", ignoreCase = true)) {
            Log.w(TAG, "Non-admin user trying to access QR attendance: '$userType'")
            Toast.makeText(requireContext(), "Access denied. Admin privileges required.", Toast.LENGTH_LONG).show()
            // Navigate back or close fragment
            parentFragmentManager.popBackStack()
            return
        }

        Log.d(TAG, "Admin access granted for user type: '$userType'")
        
        // Initialize ViewModel
        val factory = QRAttendanceViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[QRAttendanceViewModel::class.java]
        
        Log.d(TAG, "NssQRAttendanceFragment created for admin: ${sessionManager.fetchUserName()}")
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val uiState by viewModel.adminUiState.collectAsState()
                
                QRAttendanceAdminScreen(
                    uiState = uiState,
                    onEventSelected = { event ->
                        viewModel.startAttendanceSession(event)
                    },
                    onEndSession = {
                        viewModel.endAttendanceSession()
                    },
                    onLoadEvents = {
                        viewModel.refreshAvailableEvents()
                    },
                    onClearError = {
                        viewModel.clearError()
                    },
                    onCreateEvent = { name, description, location, date, openingTime, closingTime, hours, isMandatory, negativeHours ->
                        viewModel.createAttendanceEvent(name, description, location, date, openingTime, closingTime, hours, isMandatory, negativeHours)
                    },
                    onShowCreateDialog = {
                        viewModel.showCreateEventDialog()
                    },
                    onHideCreateDialog = {
                        viewModel.hideCreateEventDialog()
                    },
                    onClearCreateSuccess = {
                        viewModel.clearCreateEventSuccess()
                    },
                    onCloseEvent = { event ->
                        viewModel.closeEvent(event)
                    },
                    onNavigateBack = {
                        findNavController().navigateUp()
                    },
                    onShowEditDialog = { event -> // Pass the event to the ViewModel
                        viewModel.showEditEventDialog(event)
                    },
                    onHideEditDialog = {
                        viewModel.hideEditEventDialog()
                    },
                    onUpdateEvent = { name, description, location, date, openingTime, closingTime, hours, isMandatory, negativeHours, eventId ->
                        viewModel.updateAttendanceEvent(name, description, location, date, openingTime, closingTime, hours, isMandatory, negativeHours, eventId)
                    },
                    onGeneratePDF = { event ->
                        viewModel.generateAttendancePDF(event)
                    },
                    onClearSuccessMessage = {
                        viewModel.clearSuccessMessage()
                    },
                    onAddManualAttendance = { event, rollNumbers ->
                        viewModel.addManualAttendance(event.id, rollNumbers)
                    },
                    onMarkAbsent = { event, rollNumbers ->
                        viewModel.markStudentsAbsent(event.id, rollNumbers)
                    }
                )
            }
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Force refresh user info to ensure proper admin status
        viewModel.refreshUserInfo()

        // Load available events when fragment is created
        viewModel.loadAvailableEvents()

        // Observe error messages and UI state changes
        lifecycleScope.launch {
            viewModel.adminUiState.collect { state ->
                Log.d(TAG, "UI State changed: isAdmin=${state.isAdmin}, isSessionActive=${state.isSessionActive}, adminId='${state.adminId}', adminName='${state.adminName}'")

                state.errorMessage?.let { error ->
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // End any active session when leaving the fragment
        lifecycleScope.launch {
            val currentState = viewModel.adminUiState.value
            if (currentState.isSessionActive) {
                viewModel.endAttendanceSession()
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRAttendanceAdminScreen(
    uiState: com.phad.chatapp.viewmodels.AdminQRUiState,
    onEventSelected: (AttendanceEvent) -> Unit,
    onEndSession: () -> Unit,
    onLoadEvents: () -> Unit,
    onClearError: () -> Unit,
    onCreateEvent: (String, String, String, Date, Date, Date, Double, Boolean, Double) -> Unit,
    onShowCreateDialog: () -> Unit,
    onHideCreateDialog: () -> Unit,
    onClearCreateSuccess: () -> Unit,
    onCloseEvent: (AttendanceEvent) -> Unit,
    onNavigateBack: () -> Unit = {},
    onShowEditDialog: (AttendanceEvent) -> Unit, // New parameter
    onHideEditDialog: () -> Unit, // New parameter
    onUpdateEvent: (String, String, String, Date, Date, Date, Double, Boolean, Double, String) -> Unit, // New parameter (added eventId)
    onGeneratePDF: (AttendanceEvent) -> Unit, // PDF generation callback
    onClearSuccessMessage: () -> Unit, // Clear success message callback
    onAddManualAttendance: (AttendanceEvent, String) -> Unit, // Manual attendance callback
    onMarkAbsent: (AttendanceEvent, String) -> Unit // Mark absent callback
) {
    // Handle success message
    val context = LocalContext.current
    LaunchedEffect(uiState.createEventSuccess) {
        if (uiState.createEventSuccess) {
            // Show success toast
            Toast.makeText(
                context,
                "Event created successfully",
                Toast.LENGTH_SHORT
            ).show()
            onClearCreateSuccess()
        }
    }
    // Handle edit success message
    LaunchedEffect(uiState.editEventSuccess) {
        if (uiState.editEventSuccess) {
            Toast.makeText(
                context,
                "Event updated successfully",
                Toast.LENGTH_SHORT
            ).show()
            // You might want to clear this success state in ViewModel
            // For now, it will be cleared when dialog is hidden
        }
    }
    
    // Handle PDF generation success message
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            Toast.makeText(
                context,
                message,
                Toast.LENGTH_LONG
            ).show()
            onClearSuccessMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            // Show FAB only on event selection screen (not during active session) and for admin users
            if (!uiState.isSessionActive && uiState.isAdmin) {
                FloatingActionButton(
                    onClick = onShowCreateDialog,
                    containerColor = Color(0xFF2196F3),
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create New Event"
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Header with back button
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back button on the left
                    if (!uiState.isLoading) {
                        IconButton(
                            onClick = {
                                if (uiState.isSessionActive) {
                                    onEndSession() // End session if active
                                } else {
                                    onNavigateBack() // Navigate back to previous screen
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        // Empty space to maintain layout balance when loading
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    // Center content with QR icon and title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "QR Attendance",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "QR Attendance",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Refresh button on the right (only on event list screen). Hidden during active QR session.
                    if (!uiState.isSessionActive) {
                        IconButton(onClick = onLoadEvents) { // Use onLoadEvents from QRAttendanceAdminScreen
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Events",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoading) {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (!uiState.isSessionActive) {
                // Event selection screen
                EventSelectionScreen(
                    events = uiState.availableEvents,
                    onEventSelected = onEventSelected,
                    onRefresh = onLoadEvents,
                    onCloseEvent = onCloseEvent,
                    onShowEditDialog = onShowEditDialog, // Pass the new lambda here
                    onGeneratePDF = onGeneratePDF, // Pass PDF generation callback
                    onAddManualAttendance = onAddManualAttendance, // Pass manual attendance callback
                    onMarkAbsent = onMarkAbsent // Pass mark absent callback
                )
            } else {
                // Active session screen
                ActiveSessionScreen(
                    uiState = uiState,
                    onEndSession = onEndSession
                )
            }
        }
    }

    // Create Event Dialog
    if (uiState.showCreateEventDialog) {
        CreateEventDialog(
            isCreating = uiState.isCreatingEvent,
            onCreateEvent = onCreateEvent,
            onDismiss = onHideCreateDialog,
            errorMessage = uiState.errorMessage
        )
    }

    // Edit Event Dialog
    if (uiState.showEditEventDialog && uiState.editingEvent != null) {
        EditEventDialog(
            event = uiState.editingEvent!!,
            isUpdating = uiState.isUpdatingEvent,
            onUpdateEvent = onUpdateEvent,
            onDismiss = onHideEditDialog,
            errorMessage = uiState.errorMessage
        )
    }
}

@Composable
fun EventSelectionScreen(
    events: List<AttendanceEvent>,
    onEventSelected: (AttendanceEvent) -> Unit,
    onRefresh: () -> Unit,
    onCloseEvent: ((AttendanceEvent) -> Unit)? = null,
    onShowEditDialog: (AttendanceEvent) -> Unit, // New parameter
    onGeneratePDF: (AttendanceEvent) -> Unit, // PDF generation callback
    onAddManualAttendance: (AttendanceEvent, String) -> Unit, // Manual attendance callback
    onMarkAbsent: (AttendanceEvent, String) -> Unit // Mark absent callback
) {
    val sortedEvents = remember(events) {
        events.sortedWith(compareBy<AttendanceEvent> {
            // Parse eventDate - handle multiple date formats for robust sorting
            try {
                // Try new format first (DD MMM YYYY) with 3-letter month abbreviation
                val dateFormatter3 = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
                LocalDate.parse(it.eventDate, dateFormatter3)
            } catch (e: Exception) {
                try {
                    // Try with 4-letter month abbreviation (e.g., "Sept")
                    val dateFormatter4 = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH)
                    LocalDate.parse(it.eventDate, dateFormatter4)
                } catch (e2: Exception) {
                    try {
                        // Try old format (YYYY-MM-DD) for backward compatibility
                        val oldDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
                        LocalDate.parse(it.eventDate, oldDateFormatter)
                    } catch (e3: Exception) {
                        try {
                            // Fallback: try with default locale for new format
                            val dateFormatterDefault = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
                            LocalDate.parse(it.eventDate, dateFormatterDefault)
                        } catch (e4: Exception) {
                            try {
                                // Last fallback: try with default locale for old format
                                val oldDateFormatterDefault = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
                                LocalDate.parse(it.eventDate, oldDateFormatterDefault)
                            } catch (e5: Exception) {
                                // If all parsing fails, use creation date as fallback
                                it.createdAt.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                            }
                        }
                    }
                }
            }
        }.thenBy {
            // Parse eventTime (HH:MM AM/PM - HH:MM AM/PM) - sort by start time
            try {
                val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
                val timeParts = it.eventTime.split(" - ")
                if (timeParts.isNotEmpty()) {
                    LocalTime.parse(timeParts[0].trim(), timeFormatter) // Sort by start time
                } else {
                    LocalTime.of(0, 0) // Default time if parsing fails
                }
            } catch (e: Exception) {
                try {
                    // Try with 24-hour format as fallback
                    val timeFormatter24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
                    val timeParts = it.eventTime.split(" - ")
                    if (timeParts.isNotEmpty()) {
                        LocalTime.parse(timeParts[0].trim(), timeFormatter24)
                    } else {
                        LocalTime.of(0, 0)
                    }
                } catch (e2: Exception) {
                    LocalTime.of(0, 0) // Default time if all parsing fails
                }
            }
        })
    }

    Column {
        // Removed Header with refresh button
        
        Spacer(modifier = Modifier.height(8.dp)) // Keep this spacer for consistent spacing

        if (sortedEvents.isEmpty()) { // Use sortedEvents here
            // No events available - with proper bottom padding for floating navigation bar
            Column {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Active Events",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "There are no live events available for attendance. Please check back later or create a new event.",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                    }
                }
                // Add bottom spacer for floating navigation bar and FAB
                Spacer(modifier = Modifier.height(96.dp)) // 56dp nav height + 20dp margin + 20dp FAB space
            }
        } else {
            // Events list with proper bottom padding for floating navigation bar
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    bottom = 96.dp // 56dp nav height + 20dp margin + 20dp FAB space
                )
            ) {
                items(sortedEvents) { event -> // Use sortedEvents here
                    EventCard(
                        event = event,
                        onSelect = { onEventSelected(event) },
                        onCloseEvent = onCloseEvent,
                        onLongPress = onShowEditDialog, // Pass the new lambda here
                        onDownloadPDF = onGeneratePDF, // Pass PDF generation callback
                        onAddManualAttendance = onAddManualAttendance, // Pass manual attendance callback
                        onMarkAbsent = onMarkAbsent // Pass mark absent callback
                    )
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEventDialog(
    event: AttendanceEvent,
    isUpdating: Boolean,
    onUpdateEvent: (String, String, String, Date, Date, Date, Double, Boolean, Double, String) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String?
) {
    var eventName by remember { mutableStateOf(event.getEventName()) }
    var eventDescription by remember { mutableStateOf(event.description) }
    var eventLocation by remember { mutableStateOf(event.location) }
    var eventHours by remember { mutableStateOf(event.hours.toString()) }
    var selectedDate by remember { mutableStateOf(event.getEventDateAsDate()) }
    val initialTimePair = remember(event) {
        com.phad.chatapp.utils.AttendanceEventUtils.parseTimeRange(event.eventTime)
    }
    var openingTime by remember {
        mutableStateOf(initialTimePair?.first ?: com.phad.chatapp.utils.AttendanceEventUtils.createTimeFromHourMinute(Date(), 9, 0))
    }
    var closingTime by remember {
        mutableStateOf(initialTimePair?.second ?: com.phad.chatapp.utils.AttendanceEventUtils.createTimeFromHourMinute(Date(), 17, 0))
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showOpeningTimePicker by remember { mutableStateOf(false) }
    var showClosingTimePicker by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var validationErrorMessage by remember { mutableStateOf("") }
    var isMandatory by remember { mutableStateOf(event.isMandatory) }
    var negativeHours by remember { mutableStateOf(if (event.negativeHours > 0) event.negativeHours.toString() else "") }

    // Reset error state when dialog opens or event changes
    LaunchedEffect(event) {
        showError = false
        validationErrorMessage = ""
        eventName = event.getEventName()
        eventDescription = event.description
        eventLocation = event.location
        eventHours = event.hours.toString()
        selectedDate = event.getEventDateAsDate()
        val updatedTimePair = com.phad.chatapp.utils.AttendanceEventUtils.parseTimeRange(event.eventTime)
        openingTime = updatedTimePair?.first ?: com.phad.chatapp.utils.AttendanceEventUtils.createTimeFromHourMinute(Date(), 9, 0)
        closingTime = updatedTimePair?.second ?: com.phad.chatapp.utils.AttendanceEventUtils.createTimeFromHourMinute(Date(), 17, 0)
        isMandatory = event.isMandatory
        negativeHours = if (event.negativeHours > 0) event.negativeHours.toString() else ""
    }

    // Show error if there's an error message
    LaunchedEffect(errorMessage) {
        showError = !errorMessage.isNullOrEmpty()
    }

    AlertDialog(
        onDismissRequest = {
            if (!isUpdating) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = "Edit Event",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                // Event Name Field
                OutlinedTextField(
                    value = eventName,
                    onValueChange = {
                        eventName = it
                        showError = false
                    },
                    label = { Text("Event Name *") },
                    placeholder = { Text("Enter event name") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUpdating,
                    isError = showError && eventName.trim().isEmpty(),
                    supportingText = {
                        if (showError && eventName.trim().isEmpty()) {
                            Text(
                                text = "Event name is required",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Event Date Field with prominent styling
                OutlinedTextField(
                    value = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(selectedDate),
                    onValueChange = { },
                    label = {
                        Text(
                            "Event Date",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    },
                    placeholder = { Text("Select event date") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    enabled = false,
                    readOnly = true,
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color(0xFF333333)
                    ),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select Date",
                            tint = Color(0xFF4CAF50) // Green color for date
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Time Pickers Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Opening Time Field with prominent styling
                    OutlinedTextField(
                        value = com.phad.chatapp.utils.AttendanceEventUtils.formatTimeForPicker(openingTime),
                        onValueChange = { },
                        label = {
                            Text(
                                "Opening Time",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        },
                        placeholder = { Text("Select opening time") },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showOpeningTimePicker = true },
                        enabled = false,
                        readOnly = true,
                        textStyle = TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color(0xFF333333)
                        ),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Select Opening Time",
                                tint = Color(0xFF2196F3) // Blue color for time
                            )
                        }
                    )

                    // Closing Time Field with prominent styling
                    OutlinedTextField(
                        value = com.phad.chatapp.utils.AttendanceEventUtils.formatTimeForPicker(closingTime),
                        onValueChange = { },
                        label = {
                            Text(
                                "Closing Time",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        },
                        placeholder = { Text("Select closing time") },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showClosingTimePicker = true },
                        enabled = false,
                        readOnly = true,
                        textStyle = TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color(0xFF333333)
                        ),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Select Closing Time",
                                tint = Color(0xFF2196F3) // Blue color for time
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Hours Field with prominent styling
                OutlinedTextField(
                    value = eventHours,
                    onValueChange = { newValue ->
                        // Allow decimal input and ensure non-negative
                        if (newValue.isEmpty() || isValidDecimalInput(newValue)) {
                            eventHours = newValue
                            showError = false
                        }
                    },
                    label = {
                        Text(
                            "Hours",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    },
                    placeholder = { Text("Enter volunteer hours (e.g., 2.5)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUpdating,
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color(0xFF333333)
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Hours",
                            tint = Color(0xFFFFC107) // Golden color for hours
                        )
                    },
                    isError = showError && eventHours.trim().isEmpty(),
                    supportingText = {
                        if (showError && eventHours.trim().isEmpty()) {
                            Text(
                                text = "Hours value is required",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Location Field with prominent styling
                OutlinedTextField(
                    value = eventLocation,
                    onValueChange = {
                        eventLocation = it
                        showError = false
                    },
                    label = {
                        Text(
                            "Location (Optional)",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    },
                    placeholder = { Text("Enter event location") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUpdating,
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color(0xFF333333)
                    ),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = Color(0xFFE91E63) // Pink color for location
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Mandatory Event checkbox
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isMandatory,
                        onCheckedChange = { isMandatory = it },
                        enabled = !isUpdating
                    )
                    Text("Mandatory event", modifier = Modifier.padding(start = 8.dp))
                }

                if (isMandatory) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = negativeHours,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                negativeHours = newValue
                                showError = false
                            }
                        },
                        label = { Text("Negative Hours *") },
                        placeholder = { Text("Hours to deduct for absentees") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isUpdating,
                        textStyle = TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color(0xFF333333)
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = showError && negativeHours.trim().isEmpty(),
                        supportingText = {
                            if (showError && negativeHours.trim().isEmpty()) {
                                Text(
                                    text = "Negative hours are required for mandatory events",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }

                // Event Description Field
                OutlinedTextField(
                    value = eventDescription,
                    onValueChange = { eventDescription = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("Enter event description") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUpdating,
                    minLines = 2,
                    maxLines = 4
                )

                // Error message
                if (showError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (validationErrorMessage.isNotEmpty()) validationErrorMessage else (errorMessage ?: "Unknown error"),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                }

                // Loading indicator
                if (isUpdating) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Updating event...",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = eventName.trim()
                    val trimmedHours = eventHours.trim()
                    val hoursValue = trimmedHours.toDoubleOrNull() ?: -1.0
                    val negHoursValue = negativeHours.trim().toDoubleOrNull() ?: 0.0

                    when {
                        trimmedName.isEmpty() -> {
                            showError = true
                            validationErrorMessage = "Event name is required"
                        }
                        trimmedHours.isEmpty() || hoursValue < 0.0 -> {
                            showError = true
                            validationErrorMessage = "Please enter valid hours (0 or greater)"
                        }
                        isMandatory && negativeHours.trim().isEmpty() -> {
                            showError = true
                            validationErrorMessage = "Negative hours are required for mandatory events"
                        }
                        !com.phad.chatapp.utils.AttendanceEventUtils.validateEventTimes(openingTime, closingTime) -> {
                            showError = true
                            validationErrorMessage = "Closing time must be after opening time"
                        }
                        // Allow edits regardless of whether opening time is in the past
                        else -> {
                            showError = false
                            validationErrorMessage = ""
                            onUpdateEvent(trimmedName, eventDescription.trim(), eventLocation.trim(), selectedDate, openingTime, closingTime, hoursValue, isMandatory, negHoursValue, event.id)
                        }
                    }
                },
                enabled = !isUpdating,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Text("Update Event")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUpdating
            ) {
                Text("Cancel")
            }
        }
    )

    // Date Picker Dialog
    if (showDatePicker) {
        val context = LocalContext.current
        LaunchedEffect(showDatePicker) {
            val calendar = java.util.Calendar.getInstance()
            calendar.time = selectedDate

            val datePickerDialog = android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val newCalendar = java.util.Calendar.getInstance()
                    newCalendar.set(year, month, dayOfMonth)
                    selectedDate = newCalendar.time

                    // Time objects don't need to be updated when date changes
                    // since we now store date and time separately

                    showDatePicker = false
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            )

            datePickerDialog.setOnDismissListener {
                showDatePicker = false
            }

            datePickerDialog.show()
        }
    }

    // Opening Time Picker Dialog
    if (showOpeningTimePicker) {
        val context = LocalContext.current
        LaunchedEffect(showOpeningTimePicker) {
            val timePickerDialog = android.app.TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    openingTime = com.phad.chatapp.utils.AttendanceEventUtils.createTimeFromHourMinute(
                        Date(), hourOfDay, minute
                    )
                    showOpeningTimePicker = false
                },
                com.phad.chatapp.utils.AttendanceEventUtils.getHourFromDate(openingTime),
                com.phad.chatapp.utils.AttendanceEventUtils.getMinuteFromDate(openingTime),
                false // Use 12-hour format
            )

            timePickerDialog.setOnDismissListener {
                showOpeningTimePicker = false
            }

            timePickerDialog.show()
        }
    }

    // Closing Time Picker Dialog
    if (showClosingTimePicker) {
        val context = LocalContext.current
        LaunchedEffect(showClosingTimePicker) {
            val timePickerDialog = android.app.TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    closingTime = com.phad.chatapp.utils.AttendanceEventUtils.createTimeFromHourMinute(
                        Date(), hourOfDay, minute
                    )
                    showClosingTimePicker = false
                },
                com.phad.chatapp.utils.AttendanceEventUtils.getHourFromDate(closingTime),
                com.phad.chatapp.utils.AttendanceEventUtils.getMinuteFromDate(closingTime),
                false // Use 12-hour format
            )

            timePickerDialog.setOnDismissListener {
                showClosingTimePicker = false
            }

            timePickerDialog.show()
        }
    }
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventCard(
    event: AttendanceEvent,
    onSelect: () -> Unit,
    onCloseEvent: ((AttendanceEvent) -> Unit)? = null,
    onLongPress: ((AttendanceEvent) -> Unit)? = null, // New parameter
    onDownloadPDF: ((AttendanceEvent) -> Unit)? = null, // PDF download callback
    onAddManualAttendance: ((AttendanceEvent, String) -> Unit)? = null, // Manual attendance callback
    onMarkAbsent: ((AttendanceEvent, String) -> Unit)? = null // Mark absent callback
) {
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var showManualRollDialog by remember { mutableStateOf(false) }
    var isMarkingAbsent by remember { mutableStateOf(false) }
    val maxDescriptionLength = 100
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable( // Use combinedClickable
                onClick = onSelect,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress?.invoke(event)
                },
                indication = null, // No visual indication for long press
                interactionSource = remember { MutableInteractionSource() }
            ),
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

            // Event description with expand/collapse functionality
            if (event.description.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Description",
                        tint = Color(0xFF666666),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val displayText = if (event.description.length > maxDescriptionLength && !isDescriptionExpanded) {
                            "${event.description.take(maxDescriptionLength)}..."
                        } else {
                            event.description
                        }

                        Text(
                            text = displayText,
                            fontSize = 14.sp,
                            color = Color(0xFF666666),
                            lineHeight = 20.sp
                        )

                        if (event.description.length > maxDescriptionLength) {
                            TextButton(
                                onClick = { isDescriptionExpanded = !isDescriptionExpanded },
                                modifier = Modifier.padding(0.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isDescriptionExpanded) "Show less" else "Show more",
                                        fontSize = 12.sp,
                                        color = Color(0xFF2196F3)
                                    )
                                    Icon(
                                        imageVector = if (isDescriptionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = Color(0xFF2196F3),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Event details with improved visual hierarchy
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Date and time information in a 2-column layout, left-aligned
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start // Changed to Start
                ) {
                    // Date section
                    Column(modifier = Modifier.weight(1f)) { // Added weight
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
                    }

                    // Time section
                    Column(modifier = Modifier.weight(1f)) { // Added weight
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
                    }
                }

                // Location and Hours on the second line in a 2-column layout, left-aligned
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start // Changed to Start
                ) {
                    // Location section
                    Column(modifier = Modifier.weight(1f)) { // Added weight
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
                                text = if (event.location.isNotBlank()) event.location else "Not Specified",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (event.location.isNotBlank()) Color(0xFF333333) else Color.Gray
                            )
                        }
                    }

                    // Hours section
                    Column(modifier = Modifier.weight(1f)) { // Added weight
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
                                        "Hours: ${event.hours} / -${event.negativeHours}"
                                    } else {
                                        "Hours: ${event.hours}"
                                    }
                                } else "Not Specified",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (event.hours > 0) Color(0xFF333333) else Color.Gray
                            )
                        }
                    }
                }

                // Attendees on the third line, bigger font, centrally aligned
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = "Attendees",
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(24.dp) // Bigger icon
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Attendees: ${event.totalMarked}", // Use totalMarked for count
                            fontSize = 18.sp, // Bigger font
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action buttons with improved styling
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // First row: Start Attendance and Close Event buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    Button(
                        onClick = onSelect,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Start Attendance",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Show close button only if event is live and callback is provided
                    if (event.getEventStatus() == AttendanceEvent.STATUS_LIVE && onCloseEvent != null) {
                        Button(
                            onClick = { onCloseEvent(event) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(48.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Close Event",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                // Second row: Add Roll No and Attendance Report buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    // Add/Delete Roll No button
                    if (onAddManualAttendance != null) {
                        Box(
                            modifier = Modifier
                                .height(48.dp)
                                .background(
                                    color = Color(0xFF9C27B0),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        // Handle regular tap for adding attendance
                                        showManualRollDialog = true
                                        isMarkingAbsent = false
                                    },
                                    onLongClick = {
                                        // Show manual roll number entry dialog for marking absent
                                        showManualRollDialog = true
                                        isMarkingAbsent = true
                                    }
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Add/Delete Roll No",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }

                    // Attendance Log button (only show if there are attendees and callback is provided)
                    if (event.totalMarked > 0 && onDownloadPDF != null) {
                        Button(
                            onClick = { onDownloadPDF(event) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(48.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Attendance log",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    // Manual Roll Number Entry Dialog
    if (showManualRollDialog) {
        ManualRollNumberDialog(
            onDismiss = { 
                showManualRollDialog = false
                isMarkingAbsent = false
            },
            onAddAttendance = { rollNumbers ->
                if (isMarkingAbsent) {
                    // Call absent marking function
                    onMarkAbsent?.invoke(event, rollNumbers)
                } else {
                    // Call add attendance function
                    onAddManualAttendance?.invoke(event, rollNumbers)
                }
                showManualRollDialog = false
                isMarkingAbsent = false
            },
            isMarkingAbsent = isMarkingAbsent
        )
    }
}

@Composable
fun ActiveSessionScreen(
    uiState: com.phad.chatapp.viewmodels.AdminQRUiState,
    onEndSession: () -> Unit
) {
    Column(
        modifier = Modifier.padding(bottom = 76.dp) // 56dp nav height + 20dp margin
    ) {
        // Enhanced session info header with better visual hierarchy
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50)),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Event name with improved typography
                Text(
                    text = uiState.selectedEvent?.getEventName() ?: "Unknown Event",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Event details with combined date-time and attendees
                uiState.selectedEvent?.let { event ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Date and time with left-right alignment (prominent styling)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Date section on the left
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Date",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = event.getFormattedEventDate(),
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Time section on the right
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = "Time",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = event.getFormattedTimeRange(),
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Hours and Attendees row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hours section
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Hours",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (event.isMandatory && event.negativeHours > 0) {
                                    "Hours: ${event.hours} / -${event.negativeHours}"
                                } else {
                                    "Hours: ${event.hours}"
                                },
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Attendees section with enhanced badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = "Attendees",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Attendees:",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "${uiState.attendeeCount}",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }


            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // QR Code display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.selectedEvent?.isMandatory == true) Color(0xFFFFFDE7) else Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Descriptive text at the top
                Text(
                    text = "Scan QR to mark your Attendance",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = Color.Black,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // QR Code Image - enhanced size for projection visibility, perfectly centered horizontally
                uiState.currentQRCode?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code for Attendance",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(400.dp)
                            .wrapContentWidth(Alignment.CenterHorizontally)
                    )
                } ?: run {
                    // Loading placeholder - enhanced size matching QR code, perfectly centered horizontally
                    Box(
                        modifier = Modifier
                            .size(400.dp)
                            .wrapContentWidth(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}



/**
 * Helper function to validate decimal input for hours
 */
private fun isValidDecimalInput(input: String): Boolean {
    if (input.isEmpty()) return true
    
    // Allow only digits, one decimal point, and ensure non-negative
    val decimalPattern = Regex("^\\d*\\.?\\d*$")
    if (!decimalPattern.matches(input)) return false
    
    val doubleValue = input.toDoubleOrNull()
    return doubleValue != null && doubleValue >= 0.0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventDialog(
    isCreating: Boolean,
    onCreateEvent: (String, String, String, Date, Date, Date, Double, Boolean, Double) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String?,
    initialDate: Date? = null
) {
    var eventName by remember { mutableStateOf("") }
    var eventDescription by remember { mutableStateOf("") }
    var eventLocation by remember { mutableStateOf("") }
    var eventHours by remember { mutableStateOf("0") }
    var selectedDate by remember { mutableStateOf(initialDate ?: Date()) }
    var openingTime by remember {
        mutableStateOf(com.phad.chatapp.utils.AttendanceEventUtils.createTimeFromHourMinute(Date(), 9, 0))
    }
    var closingTime by remember {
        mutableStateOf(com.phad.chatapp.utils.AttendanceEventUtils.createTimeFromHourMinute(Date(), 17, 0))
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showOpeningTimePicker by remember { mutableStateOf(false) }
    var showClosingTimePicker by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var validationErrorMessage by remember { mutableStateOf("") }
    var isMandatory by remember { mutableStateOf(false) }
    var negativeHours by remember { mutableStateOf("") }

    // Reset error state when dialog opens
    LaunchedEffect(Unit) {
        showError = false
    }

    // Show error if there's an error message
    LaunchedEffect(errorMessage) {
        showError = !errorMessage.isNullOrEmpty()
    }

    AlertDialog(
        onDismissRequest = {
            if (!isCreating) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = "Create New Event",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                // Event Name Field
                OutlinedTextField(
                    value = eventName,
                    onValueChange = {
                        eventName = it
                        showError = false
                    },
                    label = { Text("Event Name *") },
                    placeholder = { Text("Enter event name") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCreating,
                    isError = showError && eventName.trim().isEmpty(),
                    supportingText = {
                        if (showError && eventName.trim().isEmpty()) {
                            Text(
                                text = "Event name is required",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Event Date Field with prominent styling
                OutlinedTextField(
                    value = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(selectedDate),
                    onValueChange = { },
                    label = {
                        Text(
                            "Event Date",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    },
                    placeholder = { Text("Select event date") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    enabled = false,
                    readOnly = true,
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color(0xFF333333)
                    ),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select Date",
                            tint = Color(0xFF4CAF50) // Green color for date
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Time Pickers Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Opening Time Field with prominent styling
                    OutlinedTextField(
                        value = com.phad.chatapp.utils.AttendanceEventUtils.formatTimeForPicker(openingTime),
                        onValueChange = { },
                        label = {
                            Text(
                                "Opening Time",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        },
                        placeholder = { Text("Select opening time") },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showOpeningTimePicker = true },
                        enabled = false,
                        readOnly = true,
                        textStyle = TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color(0xFF333333)
                        ),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Select Opening Time",
                                tint = Color(0xFF2196F3) // Blue color for time
                            )
                        }
                    )

                    // Closing Time Field with prominent styling
                    OutlinedTextField(
                        value = com.phad.chatapp.utils.AttendanceEventUtils.formatTimeForPicker(closingTime),
                        onValueChange = { },
                        label = {
                            Text(
                                "Closing Time",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        },
                        placeholder = { Text("Select closing time") },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showClosingTimePicker = true },
                        enabled = false,
                        readOnly = true,
                        textStyle = TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color(0xFF333333)
                        ),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Select Closing Time",
                                tint = Color(0xFF2196F3) // Blue color for time
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Hours Field with prominent styling
                OutlinedTextField(
                    value = eventHours,
                    onValueChange = { newValue ->
                        // Allow decimal input and ensure non-negative
                        if (newValue.isEmpty() || isValidDecimalInput(newValue)) {
                            eventHours = newValue
                            showError = false
                        }
                    },
                    label = {
                        Text(
                            "Hours",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    },
                    placeholder = { Text("Enter volunteer hours (e.g., 2.5)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCreating,
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color(0xFF333333)
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Hours",
                            tint = Color(0xFFFFC107) // Golden color for hours
                        )
                    },
                    isError = showError && eventHours.trim().isEmpty(),
                    supportingText = {
                        if (showError && eventHours.trim().isEmpty()) {
                            Text(
                                text = "Hours value is required",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Location Field with prominent styling
                OutlinedTextField(
                    value = eventLocation,
                    onValueChange = {
                        eventLocation = it
                        showError = false
                    },
                    label = {
                        Text(
                            "Location (Optional)",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    },
                    placeholder = { Text("Enter event location") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCreating,
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color(0xFF333333)
                    ),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = Color(0xFFE91E63) // Pink color for location
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Mandatory Event checkbox
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isMandatory,
                        onCheckedChange = { isMandatory = it },
                        enabled = !isCreating
                    )
                    Text("Mandatory event", modifier = Modifier.padding(start = 8.dp))
                }

                // Negative hours input shown only when mandatory
                if (isMandatory) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = negativeHours,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                negativeHours = newValue
                                showError = false
                            }
                        },
                        label = { Text("Negative Hours *") },
                        placeholder = { Text("Hours to deduct for absentees") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCreating,
                        textStyle = TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color(0xFF333333)
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = showError && negativeHours.trim().isEmpty(),
                        supportingText = {
                            if (showError && negativeHours.trim().isEmpty()) {
                                Text(
                                    text = "Negative hours are required for mandatory events",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }

                // Event Description Field
                OutlinedTextField(
                    value = eventDescription,
                    onValueChange = { eventDescription = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("Enter event description") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCreating,
                    minLines = 2,
                    maxLines = 4
                )

                // Error message
                if (showError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (validationErrorMessage.isNotEmpty()) validationErrorMessage else (errorMessage ?: "Unknown error"),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                }

                // Loading indicator
                if (isCreating) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Creating event...",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = eventName.trim()
                    val trimmedHours = eventHours.trim()
                    val hoursValue = trimmedHours.toDoubleOrNull() ?: -1.0
                    val negHoursValue = negativeHours.trim().toDoubleOrNull() ?: 0.0

                    when {
                        trimmedName.isEmpty() -> {
                            showError = true
                            validationErrorMessage = "Event name is required"
                        }
                        trimmedHours.isEmpty() || hoursValue < 0.0 -> {
                            showError = true
                            validationErrorMessage = "Please enter valid hours (0 or greater)"
                        }
                        isMandatory && negativeHours.trim().isEmpty() -> {
                            showError = true
                            validationErrorMessage = "Negative hours are required for mandatory events"
                        }
                        !com.phad.chatapp.utils.AttendanceEventUtils.validateEventTimes(openingTime, closingTime) -> {
                            showError = true
                            validationErrorMessage = "Closing time must be after opening time"
                        }
                        // Allow creation even if opening time is in the past (no restriction)
                        else -> {
                            showError = false
                            validationErrorMessage = ""
                            onCreateEvent(trimmedName, eventDescription.trim(), eventLocation.trim(), selectedDate, openingTime, closingTime, hoursValue, isMandatory, negHoursValue)
                        }
                    }
                },
                enabled = !isCreating,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Text("Create Event")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating
            ) {
                Text("Cancel")
            }
        }
    )

    // Date Picker Dialog
    if (showDatePicker) {
        val context = LocalContext.current
        LaunchedEffect(showDatePicker) {
            val calendar = java.util.Calendar.getInstance()
            calendar.time = selectedDate

            val datePickerDialog = android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val newCalendar = java.util.Calendar.getInstance()
                    newCalendar.set(year, month, dayOfMonth)
                    selectedDate = newCalendar.time

                    // Time objects don't need to be updated when date changes
                    // since we now store date and time separately

                    showDatePicker = false
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            )

            datePickerDialog.setOnDismissListener {
                showDatePicker = false
            }

            datePickerDialog.show()
        }
    }

    // Opening Time Picker Dialog
    if (showOpeningTimePicker) {
        val context = LocalContext.current
        LaunchedEffect(showOpeningTimePicker) {
            val timePickerDialog = android.app.TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    openingTime = com.phad.chatapp.utils.AttendanceEventUtils.createTimeFromHourMinute(
                        Date(), hourOfDay, minute
                    )
                    showOpeningTimePicker = false
                },
                com.phad.chatapp.utils.AttendanceEventUtils.getHourFromDate(openingTime),
                com.phad.chatapp.utils.AttendanceEventUtils.getMinuteFromDate(openingTime),
                false // Use 12-hour format
            )

            timePickerDialog.setOnDismissListener {
                showOpeningTimePicker = false
            }

            timePickerDialog.show()
        }
    }

    // Closing Time Picker Dialog
    if (showClosingTimePicker) {
        val context = LocalContext.current
        LaunchedEffect(showClosingTimePicker) {
            val timePickerDialog = android.app.TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    closingTime = com.phad.chatapp.utils.AttendanceEventUtils.createTimeFromHourMinute(
                        Date(), hourOfDay, minute
                    )
                    showClosingTimePicker = false
                },
                com.phad.chatapp.utils.AttendanceEventUtils.getHourFromDate(closingTime),
                com.phad.chatapp.utils.AttendanceEventUtils.getMinuteFromDate(closingTime),
                false // Use 12-hour format
            )

            timePickerDialog.setOnDismissListener {
                showClosingTimePicker = false
            }

            timePickerDialog.show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualRollNumberDialog(
    onDismiss: () -> Unit,
    onAddAttendance: (String) -> Unit,
    isMarkingAbsent: Boolean = false
) {
    var rollNumbersText by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isMarkingAbsent) "Mark Students Absent" else "Add Manual Attendance",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = if (isMarkingAbsent) 
                        "Enter roll numbers to mark as absent (separated by comma, line, space, or comma-space):"
                    else 
                        "Enter roll numbers separated by comma, line, space, or comma-space:",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = rollNumbersText,
                    onValueChange = { 
                        rollNumbersText = it
                        showError = false
                    },
                    label = { Text("Roll Numbers") },
                    // Removed placeholder examples per request to keep input empty
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    isError = showError,
                    supportingText = {
                        if (showError) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedText = rollNumbersText.trim()
                    if (trimmedText.isBlank()) {
                        showError = true
                        errorMessage = "Please enter at least one roll number"
                    } else {
                        onAddAttendance(trimmedText)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMarkingAbsent) Color(0xFFE91E63) else Color(0xFF9C27B0)
                )
            ) {
                Text(if (isMarkingAbsent) "Mark Absent" else "Add Attendance")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
