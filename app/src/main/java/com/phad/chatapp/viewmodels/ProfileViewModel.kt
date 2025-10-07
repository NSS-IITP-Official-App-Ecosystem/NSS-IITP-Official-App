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
        loadStatistics()
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
                            isStudent = userTypeFromDb.equals("Student", ignoreCase = true)
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
}


