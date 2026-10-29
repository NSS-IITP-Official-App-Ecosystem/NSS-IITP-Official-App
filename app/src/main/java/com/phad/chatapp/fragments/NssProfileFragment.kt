package com.phad.chatapp.fragments

import com.phad.chatapp.activities.EventsListActivity

import com.phad.chatapp.activities.LoginActivity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
// Imports removed
import com.phad.chatapp.R
// Import removed
import com.phad.chatapp.ui.profile.ProfileScreen
import com.phad.chatapp.ui.profile.ProfileUiState
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.utils.AttendanceStatsCalculator
import com.phad.chatapp.utils.ExcelGenerator
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.models.User
import androidx.core.content.FileProvider
import android.content.ActivityNotFoundException
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.phad.chatapp.repositories.ProfileRepository
import androidx.fragment.app.activityViewModels
import com.phad.chatapp.viewmodels.ProfileViewModel

class NssProfileFragment : Fragment() {
    private val TAG = "NssProfileFragment"
    private lateinit var sessionManager: SessionManager
    private lateinit var profileRepository: ProfileRepository
    private val viewModel: ProfileViewModel by activityViewModels()
    
    private val _uiState = MutableStateFlow(ProfileUiState())
    private val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val state by uiState.collectAsState()
                // Show switch button if user has Teaching Wing in their wings list  
                val teachingWing = state.wings.any { it.contains("Teaching", ignoreCase = true) }
                ProfileScreen(
                    state = state,
                    onLogoutClick = { logout() },
                    onRefreshClick = {
                        refreshAttendanceStats()
                    },
                    onChatClick = {},
                    onScheduleClick = {},
                    onExportAttendanceClick = { exportAttendanceMatrix() },
                    onSyncToGoogleSheetsClick = { syncAttendanceMatrixToGoogleSheets() },
                    onEventHistoryClick = { openEventHistory() },
                    onSwitchInterfaceClick = {
                        // Switch to Teaching Wing interface (user has Teaching Wing in their wings list)
                        sessionManager.setLastInterfaceChoice("TEACHING_WING")
                        val intent = Intent(requireContext(), com.phad.chatapp.MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    },
                    onSem1HoursClick = {
                        val rollNumber = sessionManager.fetchUserId()
                        val intent = Intent(requireContext(), EventsListActivity::class.java)
                        intent.putExtra("semester", 1)
                        intent.putExtra("rollNumber", rollNumber)
                        startActivity(intent)
                    },
                    onSem2HoursClick = {
                        val rollNumber = sessionManager.fetchUserId()
                        val intent = Intent(requireContext(), EventsListActivity::class.java)
                        intent.putExtra("semester", 2)
                        intent.putExtra("rollNumber", rollNumber)
                        startActivity(intent)
                    },
                    onFaqsClick = {
                        val intent = Intent(requireContext(), com.phad.chatapp.features.home.faqs.ui.FaqActivity::class.java)
                        startActivity(intent)
                    },
                    currentInterface = "NSS",
                    teachingWing = teachingWing
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Log.d(TAG, "=== NSS PROFILE FRAGMENT INITIALIZATION ===")
        
        // Initialize session manager
        sessionManager = SessionManager(requireContext())
        profileRepository = ProfileRepository(requireContext())
        viewModel.initialize()
        
        // Load user profile from session
        loadProfileFromSession()
        
        Log.d(TAG, "=== NSS PROFILE FRAGMENT INITIALIZATION COMPLETE ===")
    }
    
    private fun logout() {
        Log.d(TAG, "Logout button clicked")
        try {
            // Clear session data
                sessionManager.logoutUser()

            // Sign out from Firebase
            FirebaseAuth.getInstance().signOut()

            // Navigate to LoginActivity
            val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            requireActivity().finish()
            } catch (e: Exception) {
                Log.e(TAG, "Error during logout: ${e.message}", e)
                Toast.makeText(requireContext(), "Logout failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        
    // Call this after login, after marking attendance (students), or from chat tab refresh (admins)
    fun refreshAttendanceStats() {
        val userType = sessionManager.fetchUserType()
        val rollNumber = sessionManager.fetchUserId()
        
        if (userType.equals("Student", ignoreCase = true)) {
            lifecycleScope.launch {
                try {
                    // Set refreshing state to true
                    _uiState.update { it.copy(isRefreshing = true) }
                    // Student stats are maintained by the server. We no longer trigger a background recalculation that overwrites them.
                    _uiState.update { it.copy(isRefreshing = false) }
                    
                    Log.d(TAG, "Triggered student attendance stats recalculation")
                } catch (e: Exception) {
                    _uiState.update { it.copy(isRefreshing = false) }
                    Log.e(TAG, "Error refreshing student attendance statistics", e)
                }
            }
        } else { // Admin or other
            lifecycleScope.launch {
                try {
                    // Set refreshing state
                    _uiState.update { it.copy(isRefreshing = true) }

                    // For admins, just show total events conducted from meta/statistics
                    val db = FirebaseFirestore.getInstance()
                    val meta = db.collection("meta").document("statistics").get().await()
                    val totalEvents = (meta.getLong("totalEvents") ?: 0L).toInt()
                    
                    _uiState.update { 
                        it.copy(
                            sem1Hours = "-/$totalEvents",
                            sem2Hours = "-/$totalEvents",
                            eventsAttended = "-/$totalEvents",
                             isRefreshing = false // Reset refreshing state
                        ) 
                    }
                    
                    Log.d(TAG, "Refreshed admin event statistics: Total events=$totalEvents")
                } catch (e: Exception) {
                    _uiState.update { 
                        it.copy(
                            sem1Hours = "-/0",
                            sem2Hours = "-/0",
                            eventsAttended = "-/0",
                            isRefreshing = false // Reset refreshing state on error
                        ) 
                    }
                    Log.e(TAG, "Error refreshing admin event statistics", e)
                }
            }
        }
    }

    private fun loadProfileFromSession() {
        Log.d(TAG, "=== NSS LOADING PROFILE FROM SESSION ===")
        
        val attendanceStats = sessionManager.fetchAttendanceStats()
        val userType = sessionManager.fetchUserType()
        val rollNumber = sessionManager.fetchUserId()
        
        Log.d(TAG, "NSS Session data - rollNumber: $rollNumber, userType: $userType, attendanceStats: $attendanceStats")

        // Create base profile with session data
        val baseProfile = ProfileUiState(
            name = sessionManager.fetchUserName(),
            rollNumber = rollNumber,
            collegeEmail = "loading...",
            email = "loading...",
            events = attendanceStats,
            userType = userType,
            isStudent = userType.equals("Student", ignoreCase = true)
        )
        
        Log.d(TAG, "NSS Base profile created: $baseProfile")

        // Load enhanced profile data from unified users collection for both roles
        loadEnhancedUserProfile(baseProfile)
        
        // Load attendance statistics
        Log.d(TAG, "NSS Calling loadStatistics with rollNumber: $rollNumber")
        loadStatistics(rollNumber)
        
        // Auto-refresh stats on profile open
        refreshAttendanceStats()
        
        Log.d(TAG, "=== NSS PROFILE FROM SESSION LOADING COMPLETE ===")
    }

    private fun loadEnhancedUserProfile(baseProfile: ProfileUiState) {
        val rollNumber = sessionManager.fetchUserId()

        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading user profile (cache-first) for rollNumber: $rollNumber")
                val map = profileRepository.getUserDocMap(rollNumber)
                if (map != null) {
                    val name = (map["name"] as? String) ?: "Unknown"
                    val outlook = (map["instituteOutlookId"] as? String) ?: "Not found"
                    val userTypeFromDb = (map["userType"] as? String) ?: baseProfile.userType
                    
                    val rawWings = map["wings"]
                    val wingsList = if (rawWings is List<*>) rawWings.map { it.toString() } else emptyList()

                    _uiState.value = baseProfile.copy(
                        name = name,
                        rollNumber = rollNumber,
                        userType = userTypeFromDb,
                        email = outlook,
                        collegeEmail = outlook,
                        instituteId = outlook,
                        phone = baseProfile.phone,
                        wings = wingsList,
                        isStudent = userTypeFromDb.equals("Student", ignoreCase = true)
                    )
                } else {
                    Log.w(TAG, "User document not found for $rollNumber")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading enhanced user profile", e)
            }
        }
    }

    // Removed legacy admin/student enhancement; unified by loadEnhancedUserProfile()
    
    private fun loadStatistics(rollNumber: String?) {
        Log.d(TAG, "=== NSS LOADING STATISTICS ===")
        Log.d(TAG, "NSS loadStatistics called with rollNumber: $rollNumber")
        
        val userType = sessionManager.fetchUserType()
        Log.d(TAG, "NSS User type: $userType")
        
        if (!userType.equals("Student", ignoreCase = true)) {
            Log.d(TAG, "NSS User is not a student, setting admin stats")
            _uiState.update { 
                it.copy(
                    sem1Hours = "-/-",
                    sem2Hours = "-/-",
                    eventsAttended = "-/-"
                ) 
            }
            return
        }
        if (rollNumber == null) {
            Log.e(TAG, "NSS Cannot load statistics: Roll number is null")
            return
        }
        
        Log.d(TAG, "NSS Starting coroutine to load statistics...")
        // Load new semester-based statistics
        Log.d(TAG, "NSS Starting coroutine to load statistics...")
        // Load new semester-based statistics with Real-time Listener
        lifecycleScope.launch {
            try {
                Log.d(TAG, "NSS Setting up real-time listener for stats...")
                
                profileRepository.listenToUserUpdates(rollNumber).collect { data ->
                    if (data != null) {
                        // Extract stats from real-time update
                        val sem1 = (data["sem1Hours"] as? Number)?.toDouble() ?: 0.0
                        val sem2 = (data["sem2Hours"] as? Number)?.toDouble() ?: 0.0
                        val hours = (data["hours"] as? Number)?.toDouble() ?: (sem1 + sem2)
                        val events = (data["eventsAttended"] as? Number)?.toLong() ?: 0L
                        
                        // We still need the denominators (totals)
                        // Ideally these should also be real-time or cached, but for now we fetch valid totals
                        // To avoid async complexity inside collect, we'll use a helper or simple defaults
                        // Note: AttendanceEventUtils.formatHours handles formatting
                        
                        // For totals, we re-fetch briefly or use cached values if we had them?
                        // For simplicity and performance, effectively we just want to update the nominators
                        // The denominators usually don't change often.
                        
                        // Get properly formatted stats with correct denominators from the calculator
                        val (sem1Stats, sem2Stats, eventsStats) = AttendanceStatsCalculator.readStudentStatsFromUsers(rollNumber)
                        
                        _uiState.update { 
                            it.copy(
                                sem1Hours = sem1Stats,
                                sem2Hours = sem2Stats,
                                eventsAttended = eventsStats
                            ) 
                        }
                        
                        Log.d(TAG, "Real-time stats update: SEM1=$sem1Stats, SEM2=$sem2Stats, Events=$eventsStats")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "NSS Exception in loadStatistics flow", e)
            }
        }
        Log.d(TAG, "=== NSS LOADING STATISTICS COMPLETE ===")
    }

    private fun openEventHistory() {
        val userType = sessionManager.fetchUserType()
        if (!userType.equals("Admin", ignoreCase = true)) {
            Toast.makeText(requireContext(), "Only admins can access event history", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(requireContext(), com.phad.chatapp.features.events.EventHistoryActivity::class.java)
        startActivity(intent)
    }

    private fun exportAttendanceMatrix() {
        val userType = sessionManager.fetchUserType()
        if (!userType.equals("Admin", ignoreCase = true)) {
            Toast.makeText(requireContext(), "Only admins can export attendance", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "Preparing attendance sheet...", Toast.LENGTH_SHORT).show()
                val db = FirebaseFirestore.getInstance()
                val appContext = requireContext().applicationContext

                val excelPath = withContext(Dispatchers.IO) {
                    // Fetch all events to build dynamic columns
                    val eventsSnapshot = db.collection("NSS_Events_Attendence").get().await()
                    val events = eventsSnapshot.documents.mapNotNull { 
                        try { it.toObject(AttendanceEvent::class.java) } catch (e: Exception) { null }
                    }.sortedBy { it.getEventDateAsDate().time }

                    // Fetch all students (case-insensitive)
                    val usersSnapshot = db.collection("users")
                        .whereIn("userType", listOf("Student", "student"))
                        .get()
                        .await()

                    val students = usersSnapshot.documents.map { doc ->
                        val user = doc.toObject(User::class.java) ?: User()
                        if (user.rollNumber.isEmpty()) user.apply { rollNumber = doc.id } else user
                    }.sortedWith(compareBy({ it.name.lowercase() }, { it.rollNumber }))

                    val perStudentEventHours: MutableMap<String, MutableMap<String, Double>> = mutableMapOf()
                    val totalHoursPerStudent: MutableMap<String, Double> = mutableMapOf()

                    val eventHoursById = events.associate { it.id to it.hours }
                    
                    usersSnapshot.documents.forEach { doc ->
                        val roll = doc.id
                        @Suppress("UNCHECKED_CAST")
                        val eventsList = doc.get("eventsList") as? List<String> ?: emptyList()
                        val totalHours = (doc.getDouble("hours") ?: 0.0)
                        totalHoursPerStudent[roll] = totalHours
                        val perEvent = perStudentEventHours.getOrPut(roll) { mutableMapOf() }
                        
                        // Add hours for attended events
                        eventsList.forEach { eventId ->
                            eventHoursById[eventId]?.let { hours -> perEvent[eventId] = hours }
                        }
                        
                        // Add negative hours for mandatory events where student is absent
                        @Suppress("UNCHECKED_CAST")
                        val userWings = (doc.get("wings") as? List<String>) ?: emptyList<String>()
                        
                        events.forEach { event ->
                            val isOpenEvent = event.getDisplayWings() == "Open Event"
                            val isDncEvent = event.wings.contains("Design and Curation Wing")
                            val isRelevant = isOpenEvent || isDncEvent || userWings.any { it in event.wings }

                            if (isRelevant && !event.visibleOnlyToPresent && event.isMandatory &&
                                event.absentPenaltyApplied &&
                                event.id !in eventsList &&
                                roll !in event.exemptedRollNumbers &&
                                !event.zeroPenaltyRollNumbers.any { it.equals(roll, ignoreCase = true) } &&
                                !event.positivePenaltyRollNumbers.any { it.equals(roll, ignoreCase = true) }) {
                                perEvent[event.id] = -event.negativeHours
                            }
                        }
                    }

                    ExcelGenerator(appContext).generateAttendanceMatrixReport(
                        students = students,
                        events = events,
                        perStudentEventHours = perStudentEventHours,
                        totalHoursPerStudent = totalHoursPerStudent
                    )
                }

                context?.let { ctx ->
                    if (excelPath != null) {
                        val file = java.io.File(excelPath)
                        Toast.makeText(ctx, "File saved to Downloads/NSS_Reports", Toast.LENGTH_LONG).show()
                        try {
                            val uri = FileProvider.getUriForFile(
                                ctx,
                                "${ctx.packageName}.fileprovider",
                                file
                            )
                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            if (viewIntent.resolveActivity(ctx.packageManager) != null) {
                                ctx.startActivity(viewIntent)
                            } else {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ctx.startActivity(Intent.createChooser(shareIntent, "Open Attendance Matrix"))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error opening excel: ${e.message}", e)
                        }
                    } else {
                        Toast.makeText(ctx, "Failed to generate attendance report", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error exporting attendance matrix", t)
                context?.let { ctx ->
                    val errorMsg = when (t) {
                        is OutOfMemoryError -> "Out of memory while generating attendance matrix."
                        is SecurityException -> "Permission denied. Please check app permissions."
                        is java.io.IOException -> "Storage error: ${t.message}"
                        else -> "Error: ${t.message ?: t.javaClass.simpleName}"
                    }
                    Toast.makeText(ctx, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun syncAttendanceMatrixToGoogleSheets() {
        val userType = sessionManager.fetchUserType()
        if (!userType.equals("Admin", ignoreCase = true)) {
            Toast.makeText(requireContext(), "Only admins can sync attendance", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "Syncing to Google Sheets...", Toast.LENGTH_SHORT).show()
                val db = FirebaseFirestore.getInstance()

                // Fetch all events to build dynamic columns
                val eventsSnapshot = db.collection("NSS_Events_Attendence").get().await()
                val events = eventsSnapshot.documents.mapNotNull { it.toObject(AttendanceEvent::class.java) }
                    .sortedBy { it.getEventDateAsDate().time }

                // Fetch all students (case-insensitive)
                val usersSnapshot = db.collection("users")
                    .whereIn("userType", listOf("Student", "student"))
                    .get()
                    .await()

                val students = usersSnapshot.documents.map { doc ->
                    val user = doc.toObject(User::class.java) ?: User()
                    if (user.rollNumber.isEmpty()) user.apply { rollNumber = doc.id } else user
                }.sortedWith(compareBy({ it.name.lowercase() }, { it.rollNumber }))

                val perStudentEventHours: MutableMap<String, MutableMap<String, Double>> = mutableMapOf()
                val totalHoursPerStudent: MutableMap<String, Double> = mutableMapOf()

                val eventHoursById = events.associate { it.id to it.hours }
                
                usersSnapshot.documents.forEach { doc ->
                    val roll = doc.id
                    @Suppress("UNCHECKED_CAST")
                    val eventsList = doc.get("eventsList") as? List<String> ?: emptyList()
                    val totalHours = (doc.getDouble("hours") ?: 0.0)
                    totalHoursPerStudent[roll] = totalHours
                    val perEvent = perStudentEventHours.getOrPut(roll) { mutableMapOf() }
                    
                    // Add hours for attended events
                    eventsList.forEach { eventId ->
                        eventHoursById[eventId]?.let { hours -> perEvent[eventId] = hours }
                    }
                    
                    // Add negative hours for mandatory events where student is absent
                    val userWings = (doc.get("wings") as? List<String>) ?: emptyList<String>()
                    
                    events.forEach { event ->
                        val isOpenEvent = event.getDisplayWings() == "Open Event"
                        val isDncEvent = event.wings.contains("Design and Curation Wing")
                        val isRelevant = isOpenEvent || isDncEvent || userWings.any { it in event.wings }

                        if (isRelevant && !event.visibleOnlyToPresent && event.isMandatory &&
                            event.absentPenaltyApplied &&
                            event.id !in eventsList &&
                            roll !in event.exemptedRollNumbers &&
                            !event.zeroPenaltyRollNumbers.any { it.equals(roll, ignoreCase = true) } &&
                            !event.positivePenaltyRollNumbers.any { it.equals(roll, ignoreCase = true) }) {
                            perEvent[event.id] = -event.negativeHours
                        }
                    }
                }

                val result = com.phad.chatapp.utils.GoogleSheetsSync.sync(
                    context = requireContext(),
                    students = students,
                    events = events,
                    perStudentEventHours = perStudentEventHours,
                    totalHoursPerStudent = totalHoursPerStudent
                )

                result.fold(
                    onSuccess = {
                        Toast.makeText(requireContext(), "Google Sheet updated successfully!", Toast.LENGTH_LONG).show()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to sync to Google Sheets", error)
                        Toast.makeText(requireContext(), "Sync failed: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing to Google Sheets", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}