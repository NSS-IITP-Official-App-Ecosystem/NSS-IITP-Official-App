package com.phad.chatapp.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.repositories.ProfileRepository
import com.phad.chatapp.ui.profile.ProfileUiState
import com.phad.chatapp.utils.AttendanceStatsCalculator
import com.phad.chatapp.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "ProfileViewModel"
    private val repository = ProfileRepository(application.applicationContext)
    private val sessionManager = SessionManager(application.applicationContext)

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var loadedForRoll: String? = null

    fun initialize() {
        Log.d(TAG, "ProfileViewModel.initialize called")
        val rollNumber = sessionManager.fetchUserId()
        if (loadedForRoll == rollNumber) return
        loadedForRoll = rollNumber

        val userType = sessionManager.fetchUserType()
        val attendanceStats = sessionManager.fetchAttendanceStats()

        val base = ProfileUiState(
            name = sessionManager.fetchUserName(),
            rollNumber = rollNumber,
            collegeEmail = "loading...",
            email = "loading...",
            events = attendanceStats,
            userType = userType,
            isStudent = userType.equals("Student", ignoreCase = true)
        )
        _uiState.value = base

        loadEnhancedUserProfile()
        loadEnhancedUserProfile()
        loadStatistics()
        loadSubjectPreferences()
    }

    fun prefetch() {
        viewModelScope.launch {
            try {
                val roll = sessionManager.fetchUserId()
                repository.getUserDocMap(roll)
                FirebaseFirestore.getInstance().collection("meta").document("statistics").get().await()
            } catch (_: Exception) {}
        }
    }

    private fun loadEnhancedUserProfile() {
        val base = _uiState.value
        val rollNumber = base.rollNumber
        viewModelScope.launch {
            try {
                val map = repository.getUserDocMap(rollNumber)
                if (map != null) {
                    val name = (map["name"] as? String) ?: base.name
                    val outlook = (map["instituteOutlookId"] as? String) ?: base.collegeEmail
                    val userTypeFromDb = (map["userType"] as? String) ?: base.userType
                    _uiState.update {
                        it.copy(
                            name = name,
                            email = outlook,
                            collegeEmail = outlook,
                            instituteId = outlook,
                            userType = userTypeFromDb,
                            isStudent = userTypeFromDb.equals("Student", ignoreCase = true),
                            wings = (map["wings"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile", e)
            }
        }
    }

    private fun loadStatistics() {
        val rollNumber = sessionManager.fetchUserId()
        val isStudent = sessionManager.fetchUserType().equals("Student", ignoreCase = true)
        if (!isStudent) {
            _uiState.update { it.copy(sem1Hours = "-/-", sem2Hours = "-/-", eventsAttended = "-/-") }
            return
        }

        viewModelScope.launch {
            try {
                val (s1, s2, ev) = AttendanceStatsCalculator.readStudentStatsFromUsers(rollNumber)
                _uiState.update { it.copy(sem1Hours = s1, sem2Hours = s2, eventsAttended = ev) }
            } catch (e: Exception) {
                _uiState.update { it.copy(sem1Hours = "0/0", sem2Hours = "0/0", eventsAttended = "0/0") }
            }
        }
    }

    fun refreshStatistics() {
        loadStatistics()
        loadSubjectPreferences()
    }

    private fun loadSubjectPreferences() {
        val rollNumber = sessionManager.fetchUserId()
        Log.d(TAG, "Loading subject preferences and classes for $rollNumber")
        viewModelScope.launch {
            try {
                // 1. Fetch User Preferences (IDs) and Classes Per Week
                val userDoc = FirebaseFirestore.getInstance()
                    .collection("ttwStudents")
                    .document(rollNumber)
                    .get()
                    .await()
                
                if (userDoc.exists()) {
                    Log.d(TAG, "ttwStudents document exists for $rollNumber")
                    val prefIds = userDoc.get("subjectPreferences") as? List<String> ?: emptyList()
                    // Try to get as generic object first to avoid casting exceptions
                    val rawClasses = userDoc.get("classesPerWeek")
                    val classesCount = rawClasses?.toString() ?: "0"
                    
                    Log.d(TAG, "Loaded classesPerWeek: $classesCount (type: ${rawClasses?.javaClass?.simpleName})")

                    // Update classes count immediately
                    _uiState.update { it.copy(classesPerWeek = classesCount) }
                    
                    if (prefIds.isNotEmpty()) {
                        // 2. Fetch All Subjects (to map ID -> Name)
                        val subjectsSnapshot = FirebaseFirestore.getInstance()
                            .collection("TTW_Subjects")
                            .get()
                            .await()
                        
                        val subjectMap = subjectsSnapshot.documents.associate { 
                            it.id to (it.getString("name") ?: "") 
                        }
                        
                        // 3. Map IDs to Names
                        val prefNames = prefIds.mapNotNull { id -> subjectMap[id] }
                        _uiState.update { it.copy(subjectPreferences = prefNames) }
                    }
                } else {
                    Log.d(TAG, "ttwStudents document DOES NOT EXIST for $rollNumber")
                    _uiState.update { it.copy(classesPerWeek = "0", subjectPreferences = emptyList()) }
                }

                // 4. Fetch Assigned Slots from Generated Schedules
                fetchAssignedSlots(rollNumber)

            } catch (e: Exception) {
                Log.e(TAG, "Error loading subject preferences", e)
            }
        }
    }

    private suspend fun fetchAssignedSlots(rollNumber: String) {
        try {
            val schedulesSnapshot = FirebaseFirestore.getInstance()
                .collection("generatedSchedules")
                .get()
                .await()

            val mySlots = mutableListOf<String>()

            for (doc in schedulesSnapshot.documents) {
                val scheduleName = doc.getString("name") ?: doc.id
                val assignments = doc.get("optimizedAssignments") as? List<Map<String, Any>> 
                                ?: doc.get("assignments") as? List<Map<String, Any>> 
                                ?: emptyList()
                
                val referenceData = doc.get("referenceData") as? Map<String, Any> ?: emptyMap()
                val dayNames = (referenceData["dayNames"] as? List<String>) ?: emptyList()
                val timeSlotNames = (referenceData["timeSlotNames"] as? List<String>) ?: emptyList()

                // Find my assignments
                val myAssignments = assignments.filter { 
                    (it["volunteerRollNo"] as? String) == rollNumber 
                }

                for (assignment in myAssignments) {
                    val dayIndex = (assignment["dayIndex"] as? Number)?.toInt() ?: 0
                    val slotIndex = (assignment["slotIndex"] as? Number)?.toInt() ?: 0
                    val subject = (assignment["assignedSubject"] as? String) 
                                ?: (assignment["subjectCode"] as? String) 
                                ?: "Assigned"

                    val standardDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    val dayName = standardDays.getOrNull(dayIndex) ?: "Day ${dayIndex + 1}"
                    val timeName = timeSlotNames.getOrNull(slotIndex) ?: "Slot ${slotIndex + 1}"
                    
                    // Format: "Monday, 10:00 AM - Maths (School A)"
                    mySlots.add("$dayName, $timeName - $subject ($scheduleName)")
                }
            }
            
            Log.d(TAG, "Found ${mySlots.size} assigned slots for $rollNumber")
            _uiState.update { it.copy(assignedSlots = mySlots) }

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching assigned slots", e)
        }
    }

    fun updateClassesPerWeek(count: String) {
        val rollNumber = sessionManager.fetchUserId()
        val countInt = count.toIntOrNull() ?: 0
        Log.d(TAG, "Updating classesPerWeek to $countInt for $rollNumber")
        
        viewModelScope.launch {
            try {
                // Try update first
                FirebaseFirestore.getInstance()
                    .collection("ttwStudents")
                    .document(rollNumber)
                    .update("classesPerWeek", countInt) // Store as Number
                    .await()
                Log.d(TAG, "Update successful via update()")
                _uiState.update { it.copy(classesPerWeek = count) }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating classes per week: ${e.message}. Trying set()...")
                // If the document doesn't exist, set it (for new students)
                try {
                    val data = hashMapOf("classesPerWeek" to countInt)
                    FirebaseFirestore.getInstance()
                        .collection("ttwStudents")
                        .document(rollNumber)
                        .set(data, com.google.firebase.firestore.SetOptions.merge())
                        .await()
                    Log.d(TAG, "Update successful via set(merge)")
                    _uiState.update { it.copy(classesPerWeek = count) }
                } catch (e2: Exception) {
                    Log.e(TAG, "Error setting classes per week", e2)
                }
            }
        }
    }
}


