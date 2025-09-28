package com.phad.chatapp.features.calendar.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.phad.chatapp.features.calendar.models.CalendarEvent
import com.phad.chatapp.features.calendar.models.EventStatus
import com.phad.chatapp.features.calendar.models.EventType
import com.phad.chatapp.features.calendar.models.LeaveApplication
import com.phad.chatapp.features.calendar.models.UserRole
import com.phad.chatapp.features.calendar.repository.CalendarRepository
import com.phad.chatapp.features.calendar.ui.CalendarTabFragment
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Calendar

class CalendarViewModel(
    private val repository: CalendarRepository,
    val tabType: String = ""
) : ViewModel() {
    
    // Internal reference to all events
    private val allEvents: LiveData<List<CalendarEvent>> = repository.events
    
    // Filtered events for this specific tab
    private val _filteredEvents = MutableLiveData<List<CalendarEvent>>()
    val events: LiveData<List<CalendarEvent>> get() = _filteredEvents
    
    // Leave applications
    val leaveApplications: LiveData<List<LeaveApplication>> = repository.leaveApplications
    
    // User role state
    private val _currentUserRole = MutableLiveData<UserRole>()
    val currentUserRole: LiveData<UserRole> = _currentUserRole
    
    // Selected date
    private val _selectedDate = MutableLiveData<Date>()
    val selectedDate: LiveData<Date> = _selectedDate
    
    // Date range selection
    private val _isRangeSelectionMode = MutableLiveData<Boolean>()
    val isRangeSelectionMode: LiveData<Boolean> = _isRangeSelectionMode
    
    private val _startDate = MutableLiveData<Date?>()
    val startDate: LiveData<Date?> = _startDate
    
    private val _endDate = MutableLiveData<Date?>()
    val endDate: LiveData<Date?> = _endDate
    
    private val _dateRange = MutableLiveData<List<Date>>()
    val dateRange: LiveData<List<Date>> = _dateRange
    
    init {
        // Default role is USER
        _currentUserRole.value = UserRole.USER
        
        // Initialize with current date
        _selectedDate.value = Date()
        
        // Initialize range selection mode as disabled
        _isRangeSelectionMode.value = false
        _startDate.value = null
        _endDate.value = null
        _dateRange.value = emptyList()
        
        // Set up observer for repository events
        observeRepositoryEvents()
    }
    
    private fun observeRepositoryEvents() {
        // Observe the repository events and filter them based on tab type
        allEvents.observeForever { events ->
            if (tabType.isEmpty()) {
                // No filtering needed, use all events
                _filteredEvents.value = events
            } else {
                // Filter by event type based on tab
                val filteredList = events.filter { event ->
                    when (tabType) {
                        CalendarTabFragment.TAB_TYPE_TEACHING -> event.eventType == EventType.TEACHING
                        CalendarTabFragment.TAB_TYPE_EVENTS -> event.eventType == EventType.GENERAL_EVENT
                        else -> true
                    }
                }
                _filteredEvents.value = filteredList
            }
        }
    }
    
    // Set the user role (Admin1, Admin2, or User)
    fun setUserRole(role: UserRole) {
        _currentUserRole.value = role
        Log.d("CalendarViewModel", "User role set to: $role")
    }
    
    // Select a date
    fun selectDate(date: Date) {
        // Log the exact date being set
        android.util.Log.d("CalendarViewModel", "Setting EXACT date: ${date.toString()}")
        
        if (_isRangeSelectionMode.value == true) {
            handleRangeSelection(date)
        } else {
            // Create a clean date with only year, month, day parts to avoid any time issues
            val cal = Calendar.getInstance().apply { 
                time = date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            // Format the date for logging
            val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            android.util.Log.d("CalendarViewModel", "EXACT date selected: ${dateFormat.format(cal.time)}")
            
            // Set the exact date in the LiveData
            _selectedDate.value = cal.time
        }
    }
    
    // Toggle range selection mode
    fun toggleRangeSelectionMode() {
        val currentMode = _isRangeSelectionMode.value ?: false
        _isRangeSelectionMode.value = !currentMode
        
        // Reset range if disabling
        if (!currentMode == false) {
            clearDateRange()
        }
    }
    
    // Handle range selection logic
    private fun handleRangeSelection(date: Date) {
        when {
            _startDate.value == null -> {
                // First date in range
                _startDate.value = date
                _endDate.value = null
                _selectedDate.value = date
                _dateRange.value = listOf(date)
            }
            _endDate.value == null -> {
                // Second date in range
                val start = _startDate.value!!
                if (date.before(start)) {
                    _endDate.value = start
                    _startDate.value = date
                } else {
                    _endDate.value = date
                }
                // Generate date range
                updateDateRange()
                _selectedDate.value = date
            }
            else -> {
                // Reset and start new range
                _startDate.value = date
                _endDate.value = null
                _selectedDate.value = date
                _dateRange.value = listOf(date)
            }
        }
    }
    
    // Generate all dates in the range
    private fun updateDateRange() {
        val start = _startDate.value ?: return
        val end = _endDate.value ?: return
        
        val range = mutableListOf<Date>()
        val calendar = java.util.Calendar.getInstance()
        calendar.time = start
        
        // Add all dates from start to end (inclusive)
        while (!calendar.time.after(end)) {
            range.add(calendar.time)
            calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        
        _dateRange.value = range
    }
    
    // Clear date range
    fun clearDateRange() {
        _startDate.value = null
        _endDate.value = null
        _dateRange.value = emptyList()
    }
    
    // Get events for the selected date range
    fun getEventsForDateRange(): List<CalendarEvent> {
        val range = _dateRange.value ?: return emptyList()
        return _filteredEvents.value?.filter { event ->
            range.any { date ->
                isSameDay(date, event.date)
            }
        } ?: emptyList()
    }
    
    // Helper to compare dates ignoring time
    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { time = date1 }
        val cal2 = java.util.Calendar.getInstance().apply { time = date2 }
        
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.MONTH) == cal2.get(java.util.Calendar.MONTH) &&
               cal1.get(java.util.Calendar.DAY_OF_MONTH) == cal2.get(java.util.Calendar.DAY_OF_MONTH)
    }
    
    // Add a new event
    fun addEvent(title: String, description: String, date: Date, type: EventType) {
        viewModelScope.launch {
            repository.addEvent(title, description, "", date, type)
        }
    }
    
    // Update an existing event
    fun updateEvent(event: CalendarEvent): Boolean {
        try {
            viewModelScope.launch {
                repository.updateEvent(event)
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    // Delete an event
    fun deleteEvent(eventId: String): Boolean {
        return repository.deleteEvent(eventId)
    }
    
    // Get events for a specific day, filtered by tab type
    fun getEventsForDay(date: Date): List<CalendarEvent> {
        val eventsForDay = repository.getEventsForDay(date)
        
        // If no specific tab type, return all events
        if (tabType.isEmpty()) return eventsForDay
        
        // Otherwise, filter by event type
        return eventsForDay.filter { event ->
            when (tabType) {
                CalendarTabFragment.TAB_TYPE_TEACHING -> event.eventType == EventType.TEACHING
                CalendarTabFragment.TAB_TYPE_EVENTS -> event.eventType == EventType.GENERAL_EVENT
                else -> true
            }
        }
    }
    
    // Helper method to get events for a specific date and type
    fun getEventsByDateAndType(date: Date, eventType: String, callback: (List<CalendarEvent>) -> Unit) {
        viewModelScope.launch {
            val allEventsForDay = repository.getEventsForDay(date)
            val filteredEvents = allEventsForDay.filter {
                when (eventType) {
                    CalendarTabFragment.TAB_TYPE_TEACHING -> it.eventType == EventType.TEACHING
                    CalendarTabFragment.TAB_TYPE_EVENTS -> it.eventType == EventType.GENERAL_EVENT
                    else -> true
                }
            }
            callback(filteredEvents)
        }
    }
    
    // Update event status
    fun updateEventStatus(eventId: String, status: EventStatus): Boolean {
        return repository.updateEventStatus(eventId, status)
    }
    
    // Get leave applications for a specific day
    fun getLeaveApplicationsForDay(date: Date): List<LeaveApplication> {
        Log.d("CalendarViewModel", "Getting leave applications for date: ${date}")
        val leaves = repository.getLeaveApplicationsForDay(date)
        
        // Log all leave applications for this date
        Log.d("CalendarViewModel", "Found ${leaves.size} leave applications for date")
        val approvedCount = leaves.count { it.status == EventStatus.APPROVED }
        val pendingCount = leaves.count { it.status == EventStatus.PENDING }
        Log.d("CalendarViewModel", "Leave status breakdown - Approved: $approvedCount, Pending: $pendingCount")
        
        return leaves
    }
    
    // Apply for leave (Updated to include roll number)
    fun applyForLeave(
        date: Date,
        userId: String,
        userName: String,
        slot: String,
        subject: String,
        school: String,
        rollNumber: String
    ) {
        viewModelScope.launch {
            repository.applyForLeave(userId, userName, rollNumber, date, slot, subject, school)
        }
    }
    
    // Update leave application status
    fun updateLeaveStatus(leaveId: String, status: EventStatus) {
        viewModelScope.launch {
            try {
                repository.updateLeaveStatus(leaveId, status)
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Error updating leave status: ${e.message}")
            }
        }
    }
    
    // Book an event slot
    fun bookEventSlot(eventId: String, userId: String, userName: String) {
        viewModelScope.launch {
            repository.bookEventSlot(eventId, userId, userName)
        }
    }
    
    // Accept a class with roll number (New method)
    fun acceptClass(eventId: String, rollNumber: String) {
        viewModelScope.launch {
            repository.acceptClass(eventId, rollNumber)
        }
    }
    
    // Expose repository for direct access when needed
    fun getCalendarRepository(): CalendarRepository {
        return repository
    }
    
    // Force refresh the leave applications from the repository
    suspend fun refreshLeaveApplications() {
        try {
            // This method forces a refresh of leave applications data from the database
            val leaves = repository.getAllLeaveApplications()
            Log.d("CalendarViewModel", "Manually refreshed leave applications: ${leaves.size} total")
        } catch (e: Exception) {
            Log.e("CalendarViewModel", "Error refreshing leave applications: ${e.message}", e)
        }
    }
    
    // Mark a leave as substituted by a student with the given roll number
    suspend fun markLeaveAsSubstituted(leaveId: String, rollNumber: String) {
        try {
            repository.markLeaveAsSubstituted(leaveId, rollNumber)
            Log.d("CalendarViewModel", "Leave $leaveId marked as substituted by roll number $rollNumber")
        } catch (e: Exception) {
            Log.e("CalendarViewModel", "Error marking leave as substituted: ${e.message}", e)
            throw e
        }
    }
    
    // Delete a leave application
    fun deleteLeaveApplication(leaveId: String): Boolean {
        return try {
            val success = repository.deleteLeaveApplication(leaveId)
            Log.d("CalendarViewModel", "Delete leave application result: $success")
            success
        } catch (e: Exception) {
            Log.e("CalendarViewModel", "Error deleting leave application: ${e.message}", e)
            false
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Remove the observer to prevent memory leaks
        allEvents.observeForever { /* Do nothing */ }
    }
}

// Factory for creating the ViewModel with dependencies
class CalendarViewModelFactory(
    private val repository: CalendarRepository,
    private val tabType: String = ""
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CalendarViewModel(repository, tabType) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 