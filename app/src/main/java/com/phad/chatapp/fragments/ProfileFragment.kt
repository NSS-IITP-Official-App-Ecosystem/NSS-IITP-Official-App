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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize session manager
        sessionManager = SessionManager(requireContext())
        
        // Load user profile from session
        loadProfileFromSession()
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
        
        if (userType == "Student") {
            lifecycleScope.launch {
                try {
                    // Calculate new semester-based statistics
                    val (sem1Stats, sem2Stats, eventsStats) = AttendanceStatsCalculator.calculateStudentStats(rollNumber)
                    
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
        val profile = sessionManager.getProfileFromSession()
        val attendanceStats = sessionManager.fetchAttendanceStats()
        val userType = sessionManager.fetchUserType()

        // For Student users, load enhanced profile data from Firestore
        if (userType == "Student") {
            loadEnhancedStudentProfile(profile.copy(events = attendanceStats))
        } else {
            // For Admin users, load enhanced profile data from NSS_ADMINS collection
            loadEnhancedAdminProfile(profile.copy(events = attendanceStats))
        }
    }

    private fun loadEnhancedStudentProfile(baseProfile: ProfileUiState) {
        val rollNumber = sessionManager.fetchUserId()
        val db = FirebaseFirestore.getInstance()

        lifecycleScope.launch {
            try {
                val studentDoc = db.collection("Student").document(rollNumber).get().await()

                if (studentDoc.exists()) {
                    // Calculate new semester-based statistics
                    val (sem1Stats, sem2Stats, eventsStats) = AttendanceStatsCalculator.calculateStudentStats(rollNumber)
                    
                    val enhancedProfile = baseProfile.copy(
                        // Basic information
                        name = studentDoc.getString("Name") ?: baseProfile.name,
                        rollNumber = studentDoc.getString("Roll_No_") ?: baseProfile.rollNumber,

                        // Academic information
                        academicGroup = studentDoc.getString("Academic_Grp_") ?: "N/A",
                        courseCode = studentDoc.getString("Course_Code") ?: "N/A",
                        nssGroup = studentDoc.getString("NSS_gro") ?: "N/A",

                        // Contact information
                        gmailId = studentDoc.getString("Gmail_ID") ?: "N/A",
                        instituteId = studentDoc.getString("Institute_ID") ?: "N/A",
                        phone = studentDoc.getString("Mobile_no_") ?: baseProfile.phone,

                        // Subject preferences - removed from profile display
                        subjectPreference1 = "",
                        subjectPreference2 = "",
                        subjectPreference3 = "",

                        // Teaching Wing status
                        teachingWingStatus = if (studentDoc.getBoolean("Teaching_wing") == true) "Teaching Wing" else "Not Applicable",
                        
                        // New semester-based statistics
                        sem1Hours = sem1Stats,
                        sem2Hours = sem2Stats,
                        eventsAttended = eventsStats
                    )

                    _uiState.value = enhancedProfile
                    Log.d(TAG, "Enhanced student profile loaded successfully with new stats")

                } else {
                    Log.w(TAG, "Student document not found, using base profile")
                    _uiState.value = baseProfile
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading enhanced student profile", e)
                _uiState.value = baseProfile
            }
        }
    }

    private fun loadEnhancedAdminProfile(baseProfile: ProfileUiState) {
        val rollNumber = sessionManager.fetchUserId()
        val db = FirebaseFirestore.getInstance()

        lifecycleScope.launch {
            try {
                val adminDoc = db.collection("NSS_ADMINS").document(rollNumber).get().await()

                if (adminDoc.exists()) {
                    val imageUrl = adminDoc.getString("image_url") ?: ""
                    Log.d(TAG, "Admin document found. Image URL: '$imageUrl'")
                    Log.d(TAG, "Admin document data: ${adminDoc.data}")

                    val enhancedProfile = baseProfile.copy(
                        // Basic information
                        name = adminDoc.getString("Name") ?: baseProfile.name,
                        rollNumber = adminDoc.getString("Roll_Number") ?: baseProfile.rollNumber,

                        // Contact information
                        collegeEmail = adminDoc.getString("College_Email") ?: "N/A",
                        phone = adminDoc.getString("Contact_Number") ?: baseProfile.phone,
                        email = adminDoc.getString("Personal_Email") ?: baseProfile.email,

                        // Profile image
                        profileImageUrl = imageUrl,

                        // Mark as not student (Admin)
                        isStudent = false
                    )

                    _uiState.value = enhancedProfile
                    Log.d(TAG, "Enhanced admin profile loaded successfully")

                } else {
                    Log.w(TAG, "Admin document not found, using base profile")
                    _uiState.value = baseProfile.copy(isStudent = false)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading enhanced admin profile", e)
                _uiState.value = baseProfile.copy(isStudent = false)
            }
        }
    }
    
    private fun loadStatistics(rollNumber: String?) {
        val userType = sessionManager.fetchUserType()
        if (userType != "Student") {
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
        
        // Load new semester-based statistics
        lifecycleScope.launch {
            try {
                val (sem1Stats, sem2Stats, eventsStats) = AttendanceStatsCalculator.calculateStudentStats(rollNumber)
                _uiState.update { 
                    it.copy(
                        sem1Hours = sem1Stats,
                        sem2Hours = sem2Stats,
                        eventsAttended = eventsStats
                    ) 
                }
                Log.d(TAG, "Loaded new statistics: SEM1=$sem1Stats, SEM2=$sem2Stats, Events=$eventsStats")
            } catch (e: Exception) {
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
    }
} 