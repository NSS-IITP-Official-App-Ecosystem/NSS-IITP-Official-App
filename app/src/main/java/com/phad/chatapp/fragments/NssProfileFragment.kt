package com.phad.chatapp.fragments

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
import com.phad.chatapp.LoginActivity
import com.phad.chatapp.R
import com.phad.chatapp.EventsListActivity
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
                val state by viewModel.uiState.collectAsState()
                val teachingWing = sessionManager.getTeachingWing()
                ProfileScreen(
                    state = state,
                    onLogoutClick = { logout() },
                    onRefreshClick = {
                        viewModel.refreshStatistics()
                        Toast.makeText(requireContext(), "Refreshing...", Toast.LENGTH_SHORT).show()
                    },
                    onLibraryClick = {
                        findNavController().navigate(R.id.action_nssProfileFragment_to_nssLibraryItemListFragment)
                    },
                    onChatClick = {},
                    onScheduleClick = {},
                    onExportAttendanceClick = { exportAttendanceMatrix() },
                    onEventHistoryClick = { openEventHistory() },
                    onSwitchInterfaceClick = {
                        // Use session flag to determine eligibility
                        if (sessionManager.getTeachingWing()) {
                            // Directly switch to Teaching Wing interface (symmetric to Teaching Wing -> NSS switch)
                            sessionManager.setLastInterfaceChoice("TEACHING_WING")
                            val intent = Intent(requireContext(), com.phad.chatapp.MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                        }
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
                    // Read statistics directly from users collection
                    val (sem1Stats, sem2Stats, eventsStats) = AttendanceStatsCalculator.readStudentStatsFromUsers(rollNumber)
                    
                    _uiState.update { 
                        it.copy(
                            sem1Hours = sem1Stats,
                            sem2Hours = sem2Stats,
                            eventsAttended = eventsStats
                        ) 
                    }
                    
                    // Update student document with new stats
                    AttendanceStatsCalculator.updateStudentStats(rollNumber)
                    
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
                }
            }
        } else { // Admin or other
            lifecycleScope.launch {
                try {
                    // For admins, just show total events conducted from meta/statistics
                    val db = FirebaseFirestore.getInstance()
                    val meta = db.collection("meta").document("statistics").get().await()
                    val totalEvents = (meta.getLong("totalEvents") ?: 0L).toInt()
                    
                    _uiState.update { 
                        it.copy(
                            sem1Hours = "-/$totalEvents",
                            sem2Hours = "-/$totalEvents",
                            eventsAttended = "-/$totalEvents"
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

                    _uiState.value = baseProfile.copy(
                        name = name,
                        rollNumber = rollNumber,
                        userType = userTypeFromDb,
                        email = outlook,
                        collegeEmail = outlook,
                        instituteId = outlook,
                        phone = baseProfile.phone,
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
        lifecycleScope.launch {
            try {
                Log.d(TAG, "NSS Inside coroutine, calling readStudentStatsFromUsers...")
                val (sem1Stats, sem2Stats, eventsStats) = AttendanceStatsCalculator.readStudentStatsFromUsers(rollNumber)
                Log.d(TAG, "NSS Received stats from readStudentStatsFromUsers: SEM1=$sem1Stats, SEM2=$sem2Stats, Events=$eventsStats")
                
                Log.d(TAG, "NSS Updating UI state with new statistics...")
                Log.d(TAG, "NSS Current UI state before update: ${_uiState.value}")
                _uiState.update { currentState ->
                    val newState = currentState.copy(
                        sem1Hours = sem1Stats,
                        sem2Hours = sem2Stats,
                        eventsAttended = eventsStats
                    ) 
                    Log.d(TAG, "NSS New UI state after update: $newState")
                    newState
                }
                Log.d(TAG, "NSS UI state updated successfully")
                Log.d(TAG, "NSS Final UI state: ${_uiState.value}")
                Log.d(TAG, "NSS Loaded new statistics: SEM1=$sem1Stats, SEM2=$sem2Stats, Events=$eventsStats")
            } catch (e: Exception) {
                Log.e(TAG, "NSS Exception in loadStatistics coroutine", e)
                _uiState.update { 
                    it.copy(
                        sem1Hours = "0/0",
                        sem2Hours = "0/0",
                        eventsAttended = "0/0"
                    ) 
                }
                Log.e(TAG, "NSS Error loading new statistics", e)
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
                    eventsList.forEach { eventId ->
                        eventHoursById[eventId]?.let { hours -> perEvent[eventId] = hours }
                    }
                }

                val excelPath = ExcelGenerator(requireContext()).generateAttendanceMatrixReport(
                    students = students,
                    events = events,
                    perStudentEventHours = perStudentEventHours,
                    totalHoursPerStudent = totalHoursPerStudent
                )

                if (excelPath != null) {
                    val file = java.io.File(excelPath)
                    val uri: Uri = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.fileprovider",
                        file
                    )
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(requireContext(), "No Excel viewer found. File saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Failed to generate Excel file", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting attendance matrix", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}