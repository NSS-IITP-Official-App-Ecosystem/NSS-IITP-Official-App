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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileFragment : Fragment() {
    private val TAG = "ProfileFragment"
    private lateinit var sessionManager: SessionManager
    
    private val _uiState = MutableStateFlow(ProfileUiState())
    private val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val state by uiState.collectAsState()
                val teachingWing = sessionManager.getTeachingWing()
                ProfileScreen(
                    state = state,
                    onLogoutClick = { logout() },
                    onChatbotClick = {
                        val intent = Intent(requireContext(), com.phad.chatapp.features.home.faqs.ui.FaqActivity::class.java)
                        intent.putExtra("interface_type", "teaching_wing")
                        startActivity(intent)
                    },
                    onLibraryClick = {
                        findNavController().navigate(R.id.action_profileFragment_to_libraryItemListFragment)
                    },
                    onChatClick = {},
                    onScheduleClick = {},
                    onExportAttendanceClick = { exportAttendanceMatrix() },
                    onSwitchInterfaceClick = {
                        val currentInterface = "Teaching Wing"
                        if (teachingWing) {
                            if (currentInterface == "Teaching Wing") {
                                sessionManager.setLastInterfaceChoice("NSS")
                                val intent = Intent(requireContext(), com.phad.chatapp.NssMainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                            } else {
                                sessionManager.setLastInterfaceChoice("TEACHING_WING")
                                val intent = Intent(requireContext(), com.phad.chatapp.MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                            }
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
                    currentInterface = "Teaching Wing",
                    teachingWing = teachingWing
                )
            }
        }
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
                val perStudentEventHours: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
                val totalHoursPerStudent: MutableMap<String, Int> = mutableMapOf()

                val eventHoursById = events.associate { it.id to it.hours }

                usersSnapshot.documents.forEach { doc ->
                    val roll = doc.id
                    @Suppress("UNCHECKED_CAST")
                    val eventsList = doc.get("eventsList") as? List<String> ?: emptyList()
                    val totalHours = (doc.getLong("hours") ?: 0L).toInt()
                    totalHoursPerStudent[roll] = totalHours
                    val perEvent = perStudentEventHours.getOrPut(roll) { mutableMapOf() }
                    eventsList.forEach { eventId ->
                        eventHoursById[eventId]?.let { hours -> perEvent[eventId] = hours }
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Log.d(TAG, "=== PROFILE FRAGMENT INITIALIZATION ===")
        
        // Initialize session manager
        sessionManager = SessionManager(requireContext())
        
        // Load user profile from session
        loadProfileFromSession()
        
        Log.d(TAG, "=== PROFILE FRAGMENT INITIALIZATION COMPLETE ===")
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
                    // Read statistics directly from users collection
                    val (sem1Stats, sem2Stats, eventsStats) = AttendanceStatsCalculator.readStudentStatsFromUsers(rollNumber)
                    Log.d(TAG, "ProfileFragment: Received stats - SEM1=$sem1Stats, SEM2=$sem2Stats, Events=$eventsStats")
                    
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
                    // For admins, just show total events conducted
                    val db = FirebaseFirestore.getInstance()
                    val eventsSnapshot = db.collection("NSS_Events_Attendence").get().await()
                    val totalEvents = eventsSnapshot.size()
                    
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
        
        Log.d(TAG, "=== PROFILE FROM SESSION LOADING COMPLETE ===")
    }

    private fun loadEnhancedUserProfile(baseProfile: ProfileUiState) {
        val rollNumber = sessionManager.fetchUserId()
        val db = FirebaseFirestore.getInstance()

        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading user profile for rollNumber: $rollNumber")
                val userDoc = db.collection("users").document(rollNumber).get().await()

                if (userDoc.exists()) {
                    val name = userDoc.getString("name") ?: "Unknown"
                    val instituteOutlookId = userDoc.getString("instituteOutlookId") ?: "Not found"
                    val userTypeFromDb = userDoc.getString("userType") ?: baseProfile.userType
                    
                    Log.d(TAG, "Found user data: name='$name', instituteOutlookId='$instituteOutlookId', userType='$userTypeFromDb'")
                    
                    val enhancedProfile = baseProfile.copy(
                        // Basic information
                        name = name,
                        rollNumber = rollNumber,
                        userType = userTypeFromDb,
                        
                        // Contact information
                        email = instituteOutlookId,
                        collegeEmail = instituteOutlookId,
                        instituteId = instituteOutlookId, // Set Institute ID to the same value
                        
                        // Keep phone if already stored in session; no phone in users schema
                        phone = baseProfile.phone,
                        
                        // Set isStudent based on userType from Firestore
                        isStudent = userTypeFromDb.equals("Student", ignoreCase = true)
                    )

                    _uiState.value = enhancedProfile
                    Log.d(TAG, "Enhanced user profile loaded from users collection: name='$name', email='$instituteOutlookId'")

                } else {
                    Log.w(TAG, "User document not found in users collection for rollNumber: $rollNumber")
                    _uiState.value = baseProfile.copy(
                        name = "User not found",
                        collegeEmail = "Not found in users collection",
                        email = "Not found in users collection"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading enhanced user profile", e)
                _uiState.value = baseProfile.copy(
                    name = "Error loading profile",
                    collegeEmail = "Error: ${e.message}",
                    email = "Error: ${e.message}"
                )
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
} 