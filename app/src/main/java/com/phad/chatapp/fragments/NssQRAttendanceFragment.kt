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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh

import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.selection.toggleable // Added import
import java.util.Date
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
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
import coil.compose.AsyncImage
import com.phad.chatapp.utils.PhotoAttendanceManager
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.utils.LocationPermissionHelper
import com.phad.chatapp.viewmodels.QRAttendanceViewModel
import com.phad.chatapp.viewmodels.QRAttendanceViewModelFactory
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import com.phad.chatapp.ui.components.GradientHeader
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
    private var pendingStartEvent: com.phad.chatapp.models.AttendanceEvent? = null
    private var showLocationDialog by mutableStateOf(false)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val granted = fine || coarse
        val event = pendingStartEvent
        
        if (granted && event != null) {
            // Permissions granted, now check if location is enabled
            if (LocationPermissionHelper.isLocationEnabled(requireContext())) {
                // All good, start attendance
                viewModel.startAttendanceSession(event)
                pendingStartEvent = null
            } else {
                // Show dialog to enable location
                showLocationDialog = true
            }
        } else if (!granted) {
            pendingStartEvent = null
            Toast.makeText(requireContext(), "Location permission is required to start attendance", Toast.LENGTH_LONG).show()
        }
    }
    
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
                
                // Show location dialog if needed
                if (showLocationDialog) {
                    LocationEnableDialog(
                        onEnableClick = {
                            LocationPermissionHelper.openLocationSettings(requireContext())
                            showLocationDialog = false
                        },
                        onDismiss = {
                            showLocationDialog = false
                            pendingStartEvent = null
                        }
                    )
                }
                
                QRAttendanceAdminScreen(
                    uiState = uiState,
                    onEventSelected = { event ->
                        val ctx = requireContext()
                        val fineGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val coarseGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        
                        if (fineGranted || coarseGranted) {
                            // Permissions granted, check if location is enabled
                            if (LocationPermissionHelper.isLocationEnabled(ctx)) {
                                // All good, start attendance
                                viewModel.startAttendanceSession(event)
                            } else {
                                // Show dialog to enable location
                                pendingStartEvent = event
                                showLocationDialog = true
                            }
                        } else {
                            // Request permissions first
                            pendingStartEvent = event
                            locationPermissionLauncher.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        }
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
                    onCreateEvent = { name, description, location, date, openingTime, closingTime, hours, isMandatory, negativeHours, wings, visibleOnlyToPresent, allowedAttendanceMode ->
                        viewModel.createAttendanceEvent(name, description, location, date, openingTime, closingTime, hours, isMandatory, negativeHours, wings, visibleOnlyToPresent, allowedAttendanceMode)
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
                    onUpdateEvent = { name, description, location, date, openingTime, closingTime, hours, isMandatory, negativeHours, wings, visibleOnlyToPresent, allowedAttendanceMode, eventId ->
                        viewModel.updateAttendanceEvent(name, description, location, date, openingTime, closingTime, hours, isMandatory, negativeHours, wings, visibleOnlyToPresent, allowedAttendanceMode, eventId)
                    },
                    onGeneratePDF = { event ->
                        viewModel.generateAttendancePDF(event)
                    },
                    onClearSuccessMessage = {
                        viewModel.clearSuccessMessage()
                    },
                    onDismissRollResults = {
                        viewModel.clearRollResults()
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
    
    override fun onResume() {
        super.onResume()
        // Automatically refresh events when returning to the screen
        viewModel.refreshAvailableEvents()
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
    onCreateEvent: (String, String, String, Date, Date, Date, Double, Boolean, Double, List<String>, Boolean, String) -> Unit,
    onShowCreateDialog: () -> Unit,
    onHideCreateDialog: () -> Unit,
    onClearCreateSuccess: () -> Unit,
    onCloseEvent: (AttendanceEvent) -> Unit,
    onNavigateBack: () -> Unit = {},
    onShowEditDialog: (AttendanceEvent) -> Unit, // New parameter
    onHideEditDialog: () -> Unit, // New parameter
    onUpdateEvent: (String, String, String, Date, Date, Date, Double, Boolean, Double, List<String>, Boolean, String, String) -> Unit, // New parameter (added eventId, allowedAttendanceMode)
    onGeneratePDF: (AttendanceEvent) -> Unit, // PDF generation callback
    onClearSuccessMessage: () -> Unit, // Clear success message callback
    onDismissRollResults: () -> Unit, // Dismiss roll results dialog
    onAddManualAttendance: (AttendanceEvent, String) -> Unit, // Manual attendance callback
    onMarkAbsent: (AttendanceEvent, String) -> Unit // Mark absent callback
) {
    // Scroll state for events list (hoisted to persist across navigation/dialogs)
    val eventsListState = rememberLazyListState()

    var selectedTab by remember { mutableStateOf(0) }
    var pendingPhotos by remember { mutableStateOf<List<PhotoAttendanceManager.PendingPhotoRecord>>(emptyList()) }
    var isLoadingPending by remember { mutableStateOf(false) }
    var pendingFetchError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            isLoadingPending = true
            pendingFetchError = null
            val result = PhotoAttendanceManager.getPendingPhotos()
            isLoadingPending = false
            result.fold(
                onSuccess = { pendingPhotos = it },
                onFailure = { pendingFetchError = it.message ?: "Failed to load pending photos" }
            )
        }
    }

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

    // Roll operation results dialog
    if (uiState.showRollResults && uiState.rollResults.isNotEmpty()) {
        RollOperationResultsDialog(
            title = uiState.rollOperationTitle ?: "Operation Results",
            results = uiState.rollResults,
            onDismiss = onDismissRollResults
        )
    }

    // Roll batch progress dialog
    if (uiState.showRollProgress) {
        RollOperationProgressDialog(
            title = uiState.rollProgressTitle ?: "Processing Rolls",
            processed = uiState.rollProgressProcessed,
            total = uiState.rollProgressTotal
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            // Show FAB only on event selection screen (not during active session) and for admin users
            if (!uiState.isSessionActive && uiState.isAdmin && selectedTab == 0) {
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
        val pullRefreshState = rememberPullToRefreshState()
        val refreshScope = rememberCoroutineScope()
        var isRefreshing by remember { mutableStateOf(false) }

        val onRefresh: () -> Unit = {
            isRefreshing = true
            refreshScope.launch {
                onLoadEvents()
                isRefreshing = false
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5))
                    // Removed parent padding(16.dp) to extend header
            ) {
                // Header with back button - Full Width
                GradientHeader(
                    title = "QR Attendance",
                    icon = Icons.Default.QrCode,
                    onBackClick = null,
                    isTitleCentered = true
                    // Removed refresh action
                )

                if (!uiState.isSessionActive) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.White,
                        contentColor = Color(0xFF2196F3)
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Events", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Pending Photos", fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp)) // Increased spacing below header

                // Content container with padding
                Column(
                     modifier = Modifier
                         .fillMaxWidth()
                         .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    // Spacer removed
    
                    if (selectedTab == 1 && !uiState.isSessionActive) {
                        PendingVerificationsScreen(
                            pendingRecords = pendingPhotos,
                            isLoading = isLoadingPending,
                            error = pendingFetchError,
                            eventList = uiState.availableEvents,
                            onVerifyClick = { record, isApprove ->
                                refreshScope.launch {
                                    isLoadingPending = true
                                    val res = PhotoAttendanceManager.verifyPhoto(
                                        logId = record.id,
                                        status = if (isApprove) "Approved" else "Rejected",
                                        adminRollNumber = uiState.adminId,
                                        adminName = uiState.adminName
                                    )
                                    isLoadingPending = false
                                    res.fold(
                                        onSuccess = {
                                            pendingPhotos = pendingPhotos.filter { it.id != record.id }
                                            Toast.makeText(context, "Attendance ${if (isApprove) "approved" else "rejected"} successfully!", Toast.LENGTH_SHORT).show()
                                        },
                                        onFailure = { err ->
                                            Toast.makeText(context, "Verification failed: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            }
                        )
                    } else {
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
                                onMarkAbsent = onMarkAbsent, // Pass mark absent callback
                                listState = eventsListState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSelectionScreen(
    events: List<AttendanceEvent>,
    onEventSelected: (AttendanceEvent) -> Unit,
    onRefresh: () -> Unit,
    onCloseEvent: ((AttendanceEvent) -> Unit)? = null,
    onShowEditDialog: (AttendanceEvent) -> Unit,
    onGeneratePDF: (AttendanceEvent) -> Unit,
    onAddManualAttendance: (AttendanceEvent, String) -> Unit,
    onMarkAbsent: (AttendanceEvent, String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState // Added list state
) {
    // State for Search and Filters
    var searchQuery by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var fromDate by remember { mutableStateOf<LocalDate?>(null) }
    var toDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedWing by remember { mutableStateOf<String?>(null) }
    var isMandatoryFilter by remember { mutableStateOf(false) }
    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }

    // Date Formatters
    val displayDateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH) }

    // Collect all available wings from events for the dropdown
    val availableWings = remember(events) {
        events.flatMap { it.wings }.distinct().sorted()
    }

    // Filter Logic
    val filteredEvents = remember(events, searchQuery, fromDate, toDate, selectedWing, isMandatoryFilter) {
        events.filter { event ->
            // 1. Search Query
            val matchesSearch = if (searchQuery.isBlank()) true else {
                event.getEventName().contains(searchQuery, ignoreCase = true)
            }

            // 2. Date Filter
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

            // 3. Wing Filter
            val matchesWing = selectedWing?.let { wing ->
                event.wings.contains(wing)
            } ?: true

            // 4. Mandatory Filter
            val matchesMandatory = if (isMandatoryFilter) event.isMandatory else true

            matchesSearch && matchesFromDate && matchesToDate && matchesWing && matchesMandatory
        }
    }

    val sortedEvents = remember(filteredEvents) {
        filteredEvents.sortedWith(compareByDescending<AttendanceEvent> {
            try {
                val dateFormatter3 = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
                LocalDate.parse(it.eventDate, dateFormatter3)
            } catch (e: Exception) {
                try {
                    val dateFormatter4 = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH)
                    LocalDate.parse(it.eventDate, dateFormatter4)
                } catch (e2: Exception) {
                    try {
                        val oldDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
                        LocalDate.parse(it.eventDate, oldDateFormatter)
                    } catch (e3: Exception) {
                        it.createdAt.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                }
            }
        }.thenByDescending {
            try {
                val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
                val timeParts = it.eventTime.split(" - ")
                if (timeParts.isNotEmpty()) {
                    LocalTime.parse(timeParts[0].trim(), timeFormatter)
                } else {
                    LocalTime.of(0, 0)
                }
            } catch (e: Exception) {
                LocalTime.of(0, 0)
            }
        })
    }

    // Date Picker Dialogs
    if (showFromDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = fromDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showFromDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        fromDate = java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showFromDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    fromDate = null
                    showFromDatePicker = false 
                }) { Text("Clear") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showToDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = toDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showToDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        toDate = java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showToDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    toDate = null
                    showToDatePicker = false 
                }) { Text("Clear") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Reduced spacer
        Spacer(modifier = Modifier.height(4.dp))

        // Search and Filter UI Section - Removed Card wrapper
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column {
                // Row 1: Search Bar and Toggle Filter

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Modern Search Bar Look
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search events...", color = Color.Gray, fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(25.dp), // Pill shape
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
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
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (showFilters) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(50.dp).clickable { 
                            showFilters = !showFilters
                            if (!showFilters) {
                                fromDate = null
                                toDate = null
                                selectedWing = null
                                isMandatoryFilter = false
                            }
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filters",
                                tint = if (showFilters) Color.White else Color.Gray
                            )
                        }
                    }
                }

                // Expanded Filter Section
                if (showFilters) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Date Range
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // From Date
                        OutlinedCard(
                            onClick = { showFromDatePicker = true },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DateRange, "From", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = fromDate?.format(displayDateFormatter) ?: "From Date",
                                    fontSize = 13.sp,
                                    color = if (fromDate != null) Color.Black else Color.Gray
                                )
                            }
                        }

                        // To Date
                        OutlinedCard(
                            onClick = { showToDatePicker = true },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DateRange, "To", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = toDate?.format(displayDateFormatter) ?: "To Date",
                                    fontSize = 13.sp,
                                    color = if (toDate != null) Color.Black else Color.Gray
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
                        // Wing Dropdown (Simplified as a Box with DropdownMenu)
                        var wingMenuExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedCard(
                                onClick = { wingMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
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

                        // Mandatory Checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { isMandatoryFilter = !isMandatoryFilter }
                        ) {
                            Checkbox(
                                checked = isMandatoryFilter,
                                onCheckedChange = { isMandatoryFilter = it }
                            )
                            Text("Mandatory", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (sortedEvents.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize()) {
                 // No events available message
                 Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Events Found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Try adjusting your search or filters.",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    bottom = 96.dp,
                    start = 4.dp, // Reduced horizontal padding
                    end = 4.dp,
                    top = 8.dp
                )
            ) {
                items(sortedEvents) { event ->
                    EventCard(
                        event = event,
                        onSelect = { onEventSelected(event) },
                        onCloseEvent = onCloseEvent,
                        onLongPress = onShowEditDialog,
                        onDownloadPDF = onGeneratePDF,
                        onAddManualAttendance = onAddManualAttendance,
                        onMarkAbsent = onMarkAbsent
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
    onUpdateEvent: (String, String, String, Date, Date, Date, Double, Boolean, Double, List<String>, Boolean, String, String) -> Unit,
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
    var selectedWings by remember { mutableStateOf(event.wings) }
    var visibleOnlyToPresent by remember { mutableStateOf(event.visibleOnlyToPresent) }
    var allowedAttendanceMode by remember { mutableStateOf(event.allowedAttendanceMode) }

    // Reset error state when dialog opens or event changes
    LaunchedEffect(event) {
        showError = false
        validationErrorMessage = ""
        eventName = event.getEventName()
        eventDescription = event.description
        eventLocation = event.location
        eventHours = com.phad.chatapp.utils.AttendanceEventUtils.formatHours(event.hours)
        selectedDate = event.getEventDateAsDate()
        val updatedTimePair = com.phad.chatapp.utils.AttendanceEventUtils.parseTimeRange(event.eventTime)
        openingTime = updatedTimePair?.first ?: com.phad.chatapp.utils.AttendanceEventUtils.createTimeFromHourMinute(Date(), 9, 0)
        closingTime = updatedTimePair?.second ?: com.phad.chatapp.utils.AttendanceEventUtils.createTimeFromHourMinute(Date(), 17, 0)
        isMandatory = event.isMandatory
        negativeHours = if (event.negativeHours > 0) com.phad.chatapp.utils.AttendanceEventUtils.formatHours(event.negativeHours) else ""
        selectedWings = event.wings
        visibleOnlyToPresent = event.visibleOnlyToPresent
        allowedAttendanceMode = event.allowedAttendanceMode
    }

    // Show error if there's an error message
    LaunchedEffect(errorMessage) {
        showError = !errorMessage.isNullOrEmpty()
    }

    // Custom Dialog with fixed header and footer, scrollable content
    Dialog(onDismissRequest = { 
        if (!isUpdating) {
            onDismiss()
        }
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp) // Limit max height to prevent overflow
            ) {
                // Fixed Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(20.dp, 16.dp)
                ) {
                    Text(
                        text = "Edit Event",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.White)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
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

                Spacer(modifier = Modifier.height(4.dp)) // Reduced to 4dp

                // Event Date Field with prominent styling
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            Log.d("DatePicker", "Date field clicked, opening date picker")
                            showDatePicker = true 
                        }
                ) {
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
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false, // Disable the text field itself
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
                }

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

                Spacer(modifier = Modifier.height(8.dp)) // Reduced from 16.dp

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

                Spacer(modifier = Modifier.height(4.dp)) // Further reduced to 4.dp

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

                // Increased spacing before "Visible Only" section
                Spacer(modifier = Modifier.height(24.dp))

                // Visible only to attendees
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = visibleOnlyToPresent,
                            onValueChange = { 
                                if (!isUpdating) {
                                    visibleOnlyToPresent = it
                                    if (it) isMandatory = false // Mutual exclusivity
                                }
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = visibleOnlyToPresent,
                        onCheckedChange = null, // Handled by toggleable
                        enabled = !isUpdating
                    )
                    Text(
                        text = "Visible only to Attendees",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }

                // Add spacer between "Visible only" and "Mandatory"
                Spacer(modifier = Modifier.height(8.dp)) // Reduced to 8dp

                // Mandatory Event checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = isMandatory,
                            onValueChange = { 
                                if (!isUpdating) {
                                    isMandatory = it
                                    if (it) visibleOnlyToPresent = false // Mutual exclusivity
                                }
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isMandatory,
                        onCheckedChange = null, // Handled by toggleable
                        enabled = !isUpdating
                    )
                    Text(
                        text = "Mandatory Event",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }

                if (isMandatory) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = negativeHours,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
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

                // Allowed Attendance Mode
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Allowed Attendance Method",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF333333)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf("BOTH" to "Both", "QR" to "QR Only", "GEO" to "Photo Only")
                    modes.forEach { (modeValue, modeLabel) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = !isUpdating) { allowedAttendanceMode = modeValue }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = allowedAttendanceMode == modeValue,
                                onClick = { allowedAttendanceMode = modeValue },
                                enabled = !isUpdating
                            )
                            Text(
                                text = modeLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Wing Selection
                // Increased spacing before "Select Wings"
                Spacer(modifier = Modifier.height(24.dp))
                Column {
                    Text(
                        "Select Wings",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val wings = listOf(
                        "Teaching and Technical Wing",
                        "Chetna Wing",
                        "Prayatna Wing",
                        "Rural Development Wing",
                        "Environmental Wing",
                        "Design and Curation Wing"
                    )
                    
                    // Select All Checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isUpdating) {
                                if (selectedWings.size == wings.size) {
                                    selectedWings = emptyList()
                                } else {
                                    selectedWings = wings
                                }
                            }
                            .height(30.dp)
                    ) {
                        Checkbox(
                            checked = selectedWings.size == wings.size,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    selectedWings = wings
                                } else {
                                    selectedWings = emptyList()
                                }
                            },
                            enabled = !isUpdating
                        )
                        Text(
                            text = "Select All",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    wings.forEach { wing ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isUpdating) {
                                    if (selectedWings.contains(wing)) {
                                        selectedWings = selectedWings - wing
                                    } else {
                                        selectedWings = selectedWings + wing
                                    }
                                }
                                .height(30.dp) // Explicit height to reduce spacing further
                        ) {
                            Checkbox(
                                checked = selectedWings.contains(wing),
                                onCheckedChange = { isChecked ->
                                    if (isChecked) selectedWings = selectedWings + wing
                                    else selectedWings = selectedWings - wing
                                },
                                enabled = !isUpdating
                            )
                            Text(
                                text = wing,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))



                Spacer(modifier = Modifier.height(16.dp))

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

                    // Add bottom padding to ensure content doesn't get cut off
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Fixed Footer with buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(20.dp, 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isUpdating
                    ) {
                        Text("Cancel")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
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
                                selectedWings.isEmpty() -> {
                                    showError = true
                                    validationErrorMessage = "Please select at least one wing"
                                }
                                // Allow edits regardless of whether opening time is in the past
                                else -> {
                                    showError = false
                                    validationErrorMessage = ""
                                    onUpdateEvent(trimmedName, eventDescription.trim(), eventLocation.trim(), selectedDate, openingTime, closingTime, hoursValue, isMandatory, negHoursValue, selectedWings, visibleOnlyToPresent, allowedAttendanceMode, event.id)
                                }
                            }
                        },
                        enabled = !isUpdating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) {
                        Text("Update Event")
                    }
                }
            }
        }
    }

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
                }
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
                        // Date
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Date",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = event.getFormattedEventDate(),
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
                                text = event.getFormattedTimeRange(),
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

                // Wings Information
                if (event.wings.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = "Wing",
                                tint = Color(0xFF673AB7),
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

                    if (event.visibleOnlyToPresent) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Note - This Event is visible to only Attendees",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Red,
                            textAlign = TextAlign.Center
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
fun RollOperationResultsDialog(
    title: String,
    results: List<com.phad.chatapp.viewmodels.RollOperationResult>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 0.dp, max = 360.dp) // make scrollable window bounded
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(results) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.rollNumber,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            val bg = if (item.success) Color(0x334CAF50) else Color(0x33F44336)
                            val fg = if (item.success) Color(0xFF2E7D32) else Color(0xFFB71C1C)
                            Surface(
                                color = bg,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (item.success) "Successful" else (item.errorMessage ?: "Unsuccessful"),
                                    color = fg,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Divider(color = Color(0x11000000))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun RollOperationProgressDialog(
    title: String,
    processed: Int,
    total: Int
) {
    val progress = if (total > 0) processed.toFloat() / total.toFloat() else 0f
    AlertDialog(
        onDismissRequest = { /* non-dismissible while processing */ },
        title = {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "$processed / $total",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
fun ActiveSessionScreen(
    uiState: com.phad.chatapp.viewmodels.AdminQRUiState,
    onEndSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
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
                                    "Hours: ${com.phad.chatapp.utils.AttendanceEventUtils.formatHours(event.hours)} / -${com.phad.chatapp.utils.AttendanceEventUtils.formatHours(event.negativeHours)}"
                                } else {
                                    "Hours: ${com.phad.chatapp.utils.AttendanceEventUtils.formatHours(event.hours)}"
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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

                Spacer(modifier = Modifier.height(0.dp))

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

                Spacer(modifier = Modifier.height(0.dp))

                // End Session Button - Centered inside card
                Button(
                    onClick = onEndSession,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .wrapContentWidth()
                        .widthIn(min = 200.dp)
                        .align(Alignment.CenterHorizontally) 
                ) {
                    Text(
                        text = "End Session",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // Add spacer at the bottom to allow scrolling past the floating nav bar
        Spacer(modifier = Modifier.height(90.dp))


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
    onCreateEvent: (String, String, String, Date, Date, Date, Double, Boolean, Double, List<String>, Boolean, String) -> Unit,
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

    var selectedWings by remember { mutableStateOf(emptyList<String>()) }
    var visibleOnlyToPresent by remember { mutableStateOf(false) }
    var allowedAttendanceMode by remember { mutableStateOf("BOTH") }

    // Reset error state when dialog opens
    LaunchedEffect(Unit) {
        showError = false
    }

    // Show error if there's an error message
    LaunchedEffect(errorMessage) {
        showError = !errorMessage.isNullOrEmpty()
    }

    // Custom Dialog with fixed header and footer, scrollable content
    Dialog(onDismissRequest = { 
        if (!isCreating) {
            onDismiss()
        }
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp) // Limit max height to prevent overflow
            ) {
                // Fixed Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(20.dp, 16.dp)
                ) {
                    Text(
                        text = "Create New Event",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.White)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
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

                    Spacer(modifier = Modifier.height(4.dp)) // Reduced to 4dp

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

                    Spacer(modifier = Modifier.height(8.dp)) // Reduced from 16.dp

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
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            },
                            placeholder = { Text("Select opening time") },
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showOpeningTimePicker = true },
                            enabled = false,
                            readOnly = true,
                            singleLine = true,
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
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            },
                            placeholder = { Text("Select closing time") },
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showClosingTimePicker = true },
                            enabled = false,
                            readOnly = true,
                            singleLine = true,
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

                    Spacer(modifier = Modifier.height(8.dp)) // Reduced from 16.dp

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

                    Spacer(modifier = Modifier.height(4.dp)) // Further reduced to 4.dp

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

                    // Increased spacing before "Visible Only" section
                    Spacer(modifier = Modifier.height(24.dp))

                                // Visible only to attendees - Renamed and moved above Mandatory
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .toggleable(
                                            value = visibleOnlyToPresent,
                                            onValueChange = { 
                                                visibleOnlyToPresent = it
                                                if (it) isMandatory = false // Mutual exclusivity
                                            }
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = visibleOnlyToPresent,
                                        onCheckedChange = null // Handled by toggleable
                                    )
                                    Text(
                                        text = "Visible only to Attendees",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }

                                // Add spacer between "Visible only" and "Mandatory"
                                Spacer(modifier = Modifier.height(8.dp)) // Reduced to 8dp

                                // Mandatory Event Checkbox
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .toggleable(
                                            value = isMandatory,
                                            onValueChange = { 
                                                isMandatory = it
                                                if (it) visibleOnlyToPresent = false // Mutual exclusivity
                                            }
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isMandatory,
                                        onCheckedChange = null // Handled by toggleable
                                    )
                                    Text(
                                        text = "Mandatory Event",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }

                    // Negative hours input shown only when mandatory
                    if (isMandatory) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = negativeHours,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
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

                    // Allowed Attendance Mode
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Allowed Attendance Method",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF333333)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf("BOTH" to "Both", "QR" to "QR Only", "GEO" to "Photo Only")
                        modes.forEach { (modeValue, modeLabel) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = !isCreating) { allowedAttendanceMode = modeValue }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = allowedAttendanceMode == modeValue,
                                    onClick = { allowedAttendanceMode = modeValue },
                                    enabled = !isCreating
                                )
                                Text(
                                    text = modeLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Wing Selection
                    // Increased spacing before "Select Wings"
                    Spacer(modifier = Modifier.height(24.dp))
                    Column {
                        Text(
                            "Select Wings",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val wings = listOf(
                            "Teaching and Technical Wing",
                            "Chetna Wing",
                            "Prayatna Wing",
                            "Rural Development Wing",
                            "Environmental Wing",
                            "Design and Curation Wing"
                        )
                        
                        // Select All Checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isCreating) {
                                    if (selectedWings.size == wings.size) {
                                        selectedWings = emptyList()
                                    } else {
                                        selectedWings = wings
                                    }
                                }
                                .height(30.dp)
                        ) {
                            Checkbox(
                                checked = selectedWings.size == wings.size,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        selectedWings = wings
                                    } else {
                                        selectedWings = emptyList()
                                    }
                                },
                                enabled = !isCreating
                            )
                            Text(
                                text = "Select All",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        wings.forEach { wing ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isCreating) {
                                        if (selectedWings.contains(wing)) {
                                            selectedWings = selectedWings - wing
                                        } else {
                                            selectedWings = selectedWings + wing
                                        }
                                    }
                                    .height(30.dp) // Explicit height to reduce spacing further
                            ) {
                                Checkbox(
                                    checked = selectedWings.contains(wing),
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) selectedWings = selectedWings + wing
                                        else selectedWings = selectedWings - wing
                                    },
                                    enabled = !isCreating
                                )
                                Text(
                                    text = wing,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
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

                    // Add bottom padding to ensure content doesn't get cut off
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Fixed Footer with buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(20.dp, 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isCreating
                    ) {
                        Text("Cancel")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
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
                                selectedWings.isEmpty() -> {
                                    showError = true
                                    validationErrorMessage = "Please select at least one wing"
                                }
                                // Allow creation even if opening time is in the past (no restriction)
                                else -> {
                                    showError = false
                                    validationErrorMessage = ""
                                    onCreateEvent(trimmedName, eventDescription.trim(), eventLocation.trim(), selectedDate, openingTime, closingTime, hoursValue, isMandatory, negHoursValue, selectedWings, visibleOnlyToPresent, allowedAttendanceMode)
                                }
                            }
                        },
                        enabled = !isCreating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) {
                        Text("Create Event")
                    }
                }
            }
        }
    }

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

@Composable
fun PendingVerificationsScreen(
    pendingRecords: List<PhotoAttendanceManager.PendingPhotoRecord>,
    isLoading: Boolean,
    error: String?,
    eventList: List<AttendanceEvent>,
    onVerifyClick: (PhotoAttendanceManager.PendingPhotoRecord, Boolean) -> Unit
) {
    val context = LocalContext.current
    
    // State to track which event is selected for viewing details
    var selectedEventId by remember { mutableStateOf<String?>(null) }
    
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF2196F3))
        }
    } else if (error != null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Error",
                    tint = Color.Red,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = error, color = Color.Red, textAlign = TextAlign.Center)
            }
        }
    } else if (pendingRecords.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = "No Pending",
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Pending Verifications",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Gray
                )
                Text(
                    text = "All geo-tagged photo submissions have been reviewed.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        // Group pending records by eventId
        val groupedRecords = remember(pendingRecords) {
            pendingRecords.groupBy { it.eventId }
        }
        
        if (selectedEventId == null) {
            // Render Menu of Events
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Select Event to Review Photos",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                
                items(groupedRecords.keys.toList()) { eventId ->
                    val eventRecords = groupedRecords[eventId] ?: emptyList()
                    val eventObj = eventList.find { it.id == eventId }
                    val eventName = eventObj?.getEventName() ?: eventId
                    val eventDate = eventObj?.getFormattedEventDate() ?: ""
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedEventId = eventId },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = eventName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF212121)
                                )
                                if (eventDate.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = eventDate,
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            
                            // Badge showing count of pending submissions
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "${eventRecords.size} Pending",
                                    color = Color(0xFF1976D2),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Render Pending Photos for the selected event
            val eventRecords = groupedRecords[selectedEventId] ?: emptyList()
            
            // Auto-navigate back to events list if all records for this event are reviewed/removed
            LaunchedEffect(eventRecords) {
                if (eventRecords.isEmpty()) {
                    selectedEventId = null
                }
            }
            
            val selectedEventObj = eventList.find { it.id == selectedEventId }
            val selectedEventName = selectedEventObj?.getEventName() ?: selectedEventId ?: ""
            
            Column(modifier = Modifier.fillMaxSize()) {
                // Detail Header with Back Button and Event Title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedEventId = null }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back to event list",
                            tint = Color(0xFF2196F3)
                        )
                    }
                    Text(
                        text = selectedEventName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF2196F3),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                
                HorizontalDivider(color = Color(0xFFF5F5F5))
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(eventRecords) { record ->
                        val formattedDate = remember(record.submittedAtMs) {
                            val date = Date(record.submittedAtMs)
                            val formatter = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            formatter.format(date)
                        }
                        
                        var resolvedAddress by remember(record.latitude, record.longitude) { mutableStateOf("Loading address...") }
                        LaunchedEffect(record.latitude, record.longitude) {
                            resolvedAddress = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val geocoder = android.location.Geocoder(context, Locale.getDefault())
                                    @Suppress("DEPRECATION")
                                    val addresses = geocoder.getFromLocation(record.latitude, record.longitude, 1)
                                    if (!addresses.isNullOrEmpty()) {
                                        val address = addresses[0]
                                        val fullAddress = address.getAddressLine(0)
                                        if (!fullAddress.isNullOrEmpty()) {
                                            val parts = fullAddress.split(",")
                                            parts.take(3).joinToString(",").trim()
                                        } else {
                                            "Unknown Location"
                                        }
                                    } else {
                                        "Unknown Location"
                                    }
                                } catch (e: Exception) {
                                    "Unknown Location"
                                }
                            }
                        }
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                // Header details
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = record.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color(0xFF212121)
                                        )
                                        Text(
                                            text = "Roll: ${record.rollNumber}",
                                            fontSize = 13.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Pending",
                                            color = Color(0xFFE65100),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = Color(0xFFF5F5F5))
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Submission and Location info
                                Text(
                                    text = "Submitted: $formattedDate",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "Location",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Column {
                                        Text(
                                            text = String.format("GPS: %.6f, %.6f", record.latitude, record.longitude),
                                            fontSize = 12.sp,
                                            color = Color.DarkGray
                                        )
                                        Text(
                                            text = resolvedAddress,
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Image display
                                if (record.photoUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = record.photoUrl,
                                        contentDescription = "Volunteer Geo-Tagged Photo",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                
                                // Approve / Reject Actions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Reject Button
                                    Button(
                                        onClick = { onVerifyClick(record, false) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Reject", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    // Approve Button
                                    Button(
                                        onClick = { onVerifyClick(record, true) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66BB6A)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Approve", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

