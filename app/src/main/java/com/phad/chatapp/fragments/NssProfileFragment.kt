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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class NssProfileFragment : Fragment() {
    private val TAG = "NssProfileFragment"
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
                        intent.putExtra("interface_type", "nss")
                        startActivity(intent)
                    },
                    onLibraryClick = {
                        findNavController().navigate(R.id.action_nssProfileFragment_to_nssLibraryItemListFragment)
                    },
                    onChatClick = {},
                    onScheduleClick = {},
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
                        
                        // Set isStudent based on userType
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
}