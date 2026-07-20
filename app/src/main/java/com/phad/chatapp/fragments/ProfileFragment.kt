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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.phad.chatapp.repositories.ProfileRepository
import androidx.fragment.app.activityViewModels
import com.phad.chatapp.viewmodels.ProfileViewModel

class ProfileFragment : Fragment() {
    private val TAG = "ProfileFragment"
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
                        // Switch to NSS interface (user is currently in Teaching Wing)
                        sessionManager.setLastInterfaceChoice("NSS")
                        val intent = Intent(requireContext(), com.phad.chatapp.NssMainActivity::class.java)
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
                    onManageStudentsClick = {
                        val bundle = android.os.Bundle().apply {
                            putString("startDestination", "manageStudents")
                        }
                        findNavController().navigate(R.id.schedulingFragment, bundle)
                    },
                    onChangeSubjectsClick = {
                        // Navigate to scheduling fragment with arguments
                        val bundle = android.os.Bundle().apply {
                            putString("startDestination", "manageSubjects")
                        }
                        findNavController().navigate(R.id.schedulingFragment, bundle)
                    },
                    onSubjectPreferenceClick = {
                        val bundle = android.os.Bundle().apply {
                            putString("startDestination", "subjectPreference")
                        }
                        findNavController().navigate(R.id.schedulingFragment, bundle)
                    },
                    onUpdateClassesPerWeek = { newValue ->
                        if (newValue.isNotEmpty()) {
                            viewModel.updateClassesPerWeek(newValue)
                            // Also update local state to reflect change immediately
                            _uiState.update { it.copy(classesPerWeek = newValue) }
                        }
                    },
                    currentInterface = "Teaching Wing",
                    teachingWing = teachingWing
                )
            }
        }
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

                // Fetch all events to build dynamic columns
                val eventsSnapshot = db.collection("NSS_Events_Attendence").get().await()
                val events = eventsSnapshot.documents.mapNotNull { it.toObject(AttendanceEvent::class.java) }
                    .sortedBy { it.getEventDateAsDate().time }

                // Fetch all students (case-insensitive userType)
                val usersSnapshot = db.collection("users")
                    .whereIn("userType", listOf("Student", "student"))
                    .get()
                    .await()

                val students = usersSnapshot.documents.map { doc ->
                    // Map to User while preserving document ID as roll number if field empty
                    val user = doc.toObject(User::class.java) ?: User()
                    if (user.rollNumber.isEmpty()) user.apply { rollNumber = doc.id } else user
                }.sortedWith(compareBy({ it.name.lowercase() }, { it.rollNumber }))

                // Build per-student event hours based on users.eventsList and total hours from users.hours
                val perStudentEventHours: MutableMap<String, MutableMap<String, Double>> = mutableMapOf()
                val totalHoursPerStudent: MutableMap<String, Double> = mutableMapOf()

                val eventHoursById = events.associate { it.id to it.hours }
                val eventNegativeHoursById = events.associate { it.id to it.negativeHours }
                val mandatoryEventsById = events.associate { it.id to it.isMandatory }

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
                    events.forEach { event ->
                        if (event.isMandatory && event.id !in eventsList) {
                            perEvent[event.id] = -event.negativeHours
                        }
                    }
                }

                // Generate Excel via utility
                val excelPath = ExcelGenerator(requireContext()).generateAttendanceMatrixReport(
                    students = students,
                    events = events,
                    perStudentEventHours = perStudentEventHours,
                    totalHoursPerStudent = totalHoursPerStudent
                )

                if (excelPath != null) {
                    val file = java.io.File(excelPath)
                    Toast.makeText(requireContext(), "File saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to generate attendance report", Toast.LENGTH_LONG).show()
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied for file access", e)
                Toast.makeText(requireContext(), "Permission denied. Please check app permissions.", Toast.LENGTH_LONG).show()
            } catch (e: java.io.IOException) {
                Log.e(TAG, "File I/O error during export", e)
                Toast.makeText(requireContext(), "File system error. Please try again.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting attendance matrix", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
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

                // Fetch all students (case-insensitive userType)
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
                    events.forEach { event ->
                        if (event.isMandatory && event.id !in eventsList) {
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Log.d(TAG, "=== PROFILE FRAGMENT INITIALIZATION ===")
        
        // Initialize session manager
        sessionManager = SessionManager(requireContext())
        profileRepository = ProfileRepository(requireContext())
        viewModel.initialize()
        
        // Load user profile from session
        loadProfileFromSession()
        
        Log.d(TAG, "=== PROFILE FRAGMENT INITIALIZATION COMPLETE ===")
        
        // Sync ViewModel state to local state
        lifecycleScope.launch {
            viewModel.uiState.collect { vmState ->
                // Only update if data is actually loaded in VM (simple check)
                if (vmState.classesPerWeek != "0" || vmState.subjectPreferences.isNotEmpty() || vmState.assignedSlots.isNotEmpty()) {
                    Log.d(TAG, "Syncing ViewModel state to Fragment: classes=${vmState.classesPerWeek}, prefs=${vmState.subjectPreferences.size}, slots=${vmState.assignedSlots.size}")
                    _uiState.update { local ->
                        local.copy(
                            classesPerWeek = vmState.classesPerWeek,
                            subjectPreferences = vmState.subjectPreferences,
                            assignedSlots = vmState.assignedSlots,
                            // Sync other potentially enhanced fields if VM is source of truth
                            subjectPreference1 = vmState.subjectPreference1,
                            subjectPreference2 = vmState.subjectPreference2,
                            subjectPreference3 = vmState.subjectPreference3
                        )
                    }
                }
            }
        }
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
                    Log.d(TAG, "ProfileFragment: Starting to read statistics for rollNumber: $rollNumber")
                    
                    // Set refreshing state to true
                    _uiState.update { it.copy(isRefreshing = true) }
                    
                    // Read statistics directly from users collection
                    val (sem1Stats, sem2Stats, eventsStats) = AttendanceStatsCalculator.readStudentStatsFromUsers(rollNumber)
                    Log.d(TAG, "ProfileFragment: Received stats - SEM1=$sem1Stats, SEM2=$sem2Stats, Events=$eventsStats")
                    
                    _uiState.update { 
                        it.copy(
                            sem1Hours = sem1Stats,
                            sem2Hours = sem2Stats,
                            eventsAttended = eventsStats,
                            isRefreshing = false // Reset refreshing state
                        ) 
                    }
                    // Student stats are maintained by the server. We no longer write them from the client.
                    Log.d(TAG, "Refreshed student attendance stats: SEM1=$sem1Stats, SEM2=$sem2Stats, Events=$eventsStats")
                } catch (e: Exception) {
                    _uiState.update { 
                        it.copy(
                            sem1Hours = "0/0",
                            sem2Hours = "0/0",
                            eventsAttended = "0/0"
                        ) 
                    }
                    Log.e(TAG, "Error refreshing student attendance statistics", e)
                    _uiState.update { it.copy(isRefreshing = false) } // Ensure false on error
                }
                // Refresh subject preferences
                loadSubjectPreferences()
            }
        } else { // Admin or other
            lifecycleScope.launch {
                try {
                    // For admins, just show total events conducted from meta/statistics
                    val db = FirebaseFirestore.getInstance()
                    val meta = db.collection("meta").document("statistics").get().await()
                    val totalEvents = (meta.getLong("totalEvents") ?: 0L).toInt()

                    _uiState.update { it.copy(isRefreshing = true) }
                    
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
                            eventsAttended = "-/0"
                        ) 
                    }
                    Log.e(TAG, "Error refreshing admin event statistics", e)
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    private fun loadProfileFromSession() {
        Log.d(TAG, "=== LOADING PROFILE FROM SESSION ===")
        
        val attendanceStats = sessionManager.fetchAttendanceStats()
        val userType = sessionManager.fetchUserType()
        val rollNumber = sessionManager.fetchUserId()
        
        Log.d(TAG, "Session data - rollNumber: $rollNumber, userType: $userType, attendanceStats: $attendanceStats")

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
        
        Log.d(TAG, "Base profile created: $baseProfile")

        // Load enhanced profile data from unified users collection for both roles
        loadEnhancedUserProfile(baseProfile)
        
        // Load attendance statistics
        Log.d(TAG, "Calling loadStatistics with rollNumber: $rollNumber")
        loadStatistics(rollNumber)
        
        // Load Subject Preferences
        loadSubjectPreferences()
        
        Log.d(TAG, "=== PROFILE FROM SESSION LOADING COMPLETE ===")
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
        Log.d(TAG, "=== LOADING STATISTICS ===")
        Log.d(TAG, "loadStatistics called with rollNumber: $rollNumber")
        
        val userType = sessionManager.fetchUserType()
        Log.d(TAG, "User type: $userType")
        
        if (!userType.equals("Student", ignoreCase = true)) {
            Log.d(TAG, "User is not a student, setting admin stats")
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
            Log.e(TAG, "Cannot load statistics: Roll number is null")
            return
        }
        
        Log.d(TAG, "Starting coroutine to load statistics...")
        // Load new semester-based statistics
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Inside coroutine, calling readStudentStatsFromUsers...")
                val (sem1Stats, sem2Stats, eventsStats) = AttendanceStatsCalculator.readStudentStatsFromUsers(rollNumber)
                Log.d(TAG, "Received stats from readStudentStatsFromUsers: SEM1=$sem1Stats, SEM2=$sem2Stats, Events=$eventsStats")
                
                Log.d(TAG, "Updating UI state with new statistics...")
                Log.d(TAG, "Current UI state before update: ${_uiState.value}")
                _uiState.update { currentState ->
                    val newState = currentState.copy(
                        sem1Hours = sem1Stats,
                        sem2Hours = sem2Stats,
                        eventsAttended = eventsStats
                    )
                    Log.d(TAG, "New UI state after update: $newState")
                    newState
                }
                Log.d(TAG, "UI state updated successfully")
                Log.d(TAG, "Final UI state: ${_uiState.value}")
                Log.d(TAG, "Loaded new statistics: SEM1=$sem1Stats, SEM2=$sem2Stats, Events=$eventsStats")
            } catch (e: Exception) {
                Log.e(TAG, "Exception in loadStatistics coroutine", e)
                _uiState.update { 
                    it.copy(
                        sem1Hours = "0/0",
                        sem2Hours = "0/0",
                        eventsAttended = "0/0"
                    ) 
                }
                Log.e(TAG, "Error loading new statistics", e)
            }
        }
        Log.d(TAG, "=== LOADING STATISTICS COMPLETE ===")
    }
    private fun loadSubjectPreferences() {
        val rollNumber = sessionManager.fetchUserId()
        lifecycleScope.launch {
            try {
                // 1. Fetch User Preferences (IDs - which are Names now, but code is robust)
                val userDoc = FirebaseFirestore.getInstance()
                    .collection("ttwStudents")
                    .document(rollNumber)
                    .get()
                    .await()
                
                // Fetch classesPerWeek safely (can be String or Number)
                val rawClasses = userDoc.get("classesPerWeek")
                val classesCount = rawClasses?.toString() ?: "0"
                
                Log.d(TAG, "Loaded classesPerWeek in Fragment: $classesCount (type: ${rawClasses?.javaClass?.simpleName})")
                
                // Update local state
                _uiState.update { it.copy(classesPerWeek = classesCount) }

                val prefIds = userDoc.get("subjectPreferences") as? List<String> ?: emptyList()
                
                if (prefIds.isNotEmpty()) {
                    // Update directly with names (ID=Name)
                    _uiState.update { it.copy(subjectPreferences = prefIds) }
                } else {
                     _uiState.update { it.copy(subjectPreferences = emptyList()) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading subject preferences", e)
            }
        }
    }
} 