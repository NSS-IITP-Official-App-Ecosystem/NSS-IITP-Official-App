package com.phad.chatapp.features.calendar.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.phad.chatapp.R
import com.phad.chatapp.databinding.FragmentCalendarTabBinding
import com.phad.chatapp.features.calendar.models.CalendarEvent
import com.phad.chatapp.features.calendar.models.EventStatus
import com.phad.chatapp.features.calendar.models.EventType
import com.phad.chatapp.features.calendar.models.LeaveApplication
import com.phad.chatapp.features.calendar.models.UserRole
import com.phad.chatapp.features.calendar.repository.CalendarRepository
import com.phad.chatapp.features.calendar.utils.CalendarSessionManager
import com.phad.chatapp.features.calendar.viewmodel.CalendarViewModel
import com.phad.chatapp.features.calendar.viewmodel.CalendarViewModelFactory
import com.phad.chatapp.features.scheduling.models.SubjectAssignmentDetails
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import androidx.core.content.ContextCompat
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ConcatAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.app.AlertDialog
import android.widget.TextView
import android.widget.EditText as TextInputEditText

class CalendarTabFragment : Fragment() {
    private var _binding: FragmentCalendarTabBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: CalendarViewModel
    private lateinit var calendarAdapter: CalendarGridAdapter
    private lateinit var eventsAdapter: EventAdapter
    private lateinit var leavesAdapter: LeaveApplicationAdapter
    private lateinit var concatAdapter: ConcatAdapter
    private var tabType: String = TAB_TYPE_TEACHING
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private var availableLeaves: List<LeaveApplication> = emptyList()
    private var userRollNumber: String = ""
    private var userName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            tabType = it.getString(ARG_TAB_TYPE, TAB_TYPE_TEACHING)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Fetch user roll number and assignments FIRST so adapters can use them
        val sessionManager = CalendarSessionManager(requireContext())
        userRollNumber = sessionManager.fetchRollNumber()
        userName = sessionManager.fetchUserName()
        
        val repository = CalendarRepository()
        
        // Create a tab-specific ViewModel factory with the appropriate tab type
        val factory = CalendarViewModelFactory(repository, tabType)
        
        // Create a tab-specific ViewModel so each tab has its own state
        viewModel = ViewModelProvider(this, factory).get(CalendarViewModel::class.java)
        
        if (tabType == TAB_TYPE_TEACHING && userRollNumber.isNotEmpty()) {
            viewModel.fetchUserAssignments(userRollNumber)
        }
        
        setupWindowInsets()
        setupCalendarGrid()
        setupEventsRecyclerView()
        setupViews()
        observeViewModel()
        
        // Get user role from the parent Fragment
        (parentFragment as? CalendarFragment)?.let { calendarFragment ->
            calendarFragment.currentUserRole.observe(viewLifecycleOwner) { role ->
                viewModel.setUserRole(role)
            }
        }
    }

    private fun setupCalendarGrid() {
        calendarAdapter = CalendarGridAdapter(
            context = requireContext(), 
            events = emptyList(), 
            leaveApplications = emptyList(),
            teachingAssignments = viewModel.userAssignments.value ?: emptyList(),
            isTeachingCalendar = (tabType == TAB_TYPE_TEACHING),
            userRollNumber = userRollNumber,
            isAdmin = (viewModel.currentUserRole.value == UserRole.ADMIN1 || viewModel.currentUserRole.value == UserRole.ADMIN2)
        )
        calendarAdapter.onDateSelectedListener = { clickedDate ->
            Log.d("CalendarTabFragment", "Date clicked: ${dateFormat.format(clickedDate)}")
            
            // Update this tab's viewModel — this triggers the LiveData observer
            // which updates the event list and date header below the calendar
            viewModel.selectDate(clickedDate)
            
            // For non-teaching tabs, also propagate to parent to show date actions
            if (tabType != TAB_TYPE_TEACHING) {
                (parentFragment as? CalendarFragment)?.let { parent ->
                    parent.updateLastClickedDate(clickedDate)
                    parent.showDateActionDialog(clickedDate, tabType)
                }
            } else {
                // For teaching tab, just update the selected date in the parent
                (parentFragment as? CalendarFragment)?.updateLastClickedDate(clickedDate)
            }
        }
        
        binding.gridCalendar.adapter = calendarAdapter
        
        // Set month display
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        val displayCal = Calendar.getInstance()
        displayCal.set(Calendar.YEAR, calendarAdapter.getCurrentYear())
        displayCal.set(Calendar.MONTH, calendarAdapter.getCurrentMonth())
        binding.tvMonthName.text = monthFormat.format(displayCal.time)
        binding.tvYearNumber.text = calendarAdapter.getCurrentYear().toString()
        Log.d("CalendarTabFragment", "Initial month display: ${binding.tvMonthName.text} ${binding.tvYearNumber.text}")
        
        // Always default to today on first load
        val today = Calendar.getInstance().time
        // Highlight today's cell in the grid immediately (before LiveData observer fires)
        calendarAdapter.setSelectedDate(today)
        // Notify viewModel — triggers the observer that updates the date header and event list
        viewModel.selectDate(today)
        // Also sync the shared parent ViewModel
        (parentFragment as? CalendarFragment)?.let { parent ->
            parent.sharedViewModel.selectDate(today)
        }
        
        // Load all leave applications
        loadAllLeaveApplications()
    }
    
    fun updateCalendarMonth(year: Int, month: Int) {
        calendarAdapter.setMonth(year, month)
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        val displayCal = Calendar.getInstance()
        displayCal.set(Calendar.YEAR, year)
        displayCal.set(Calendar.MONTH, month)
        binding.tvMonthName.text = monthFormat.format(displayCal.time)
        binding.tvYearNumber.text = year.toString()
        
        // Update events for the new month
        viewModel.events.value?.let { events ->
            val adapter = CalendarGridAdapter(
                requireContext(), 
                events, 
                availableLeaves,
                viewModel.userAssignments.value ?: emptyList(),
                (tabType == TAB_TYPE_TEACHING),
                userRollNumber,
                isAdmin = (viewModel.currentUserRole.value == UserRole.ADMIN1 || viewModel.currentUserRole.value == UserRole.ADMIN2)
            )
            adapter.setMonth(year, month)
            adapter.onDateSelectedListener = calendarAdapter.onDateSelectedListener
            viewModel.selectedDate.value?.let { adapter.setSelectedDate(it) }
            calendarAdapter = adapter
            binding.gridCalendar.adapter = calendarAdapter
        }
    }
    
    private fun setupViews() {
        // Setup Month Navigation
        // Previous month button
        binding.btnPrevMonth.setOnClickListener {
            val calendar = Calendar.getInstance()
            // Get current month from the adapter
            val currentMonth = calendarAdapter.getCurrentMonth()
            val currentYear = calendarAdapter.getCurrentYear()
            calendar.set(currentYear, currentMonth, 1)
            calendar.add(Calendar.MONTH, -1)
            updateCalendarMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
        }
        
        // Next month button
        binding.btnNextMonth.setOnClickListener {
            val calendar = Calendar.getInstance()
            // Get current month from the adapter
            val currentMonth = calendarAdapter.getCurrentMonth()
            val currentYear = calendarAdapter.getCurrentYear()
            calendar.set(currentYear, currentMonth, 1)
            calendar.add(Calendar.MONTH, 1)
            updateCalendarMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
        }
    }

    private fun setupWindowInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.topBannerLayout) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            // Base padding below the status bar so the content has visual breathing room
            val basePaddingPx = (16 * resources.displayMetrics.density).toInt()
            
            v.setPadding(
                0, // stretch banner horizontally
                systemBars.top + basePaddingPx,
                0, // stretch banner horizontally
                v.paddingBottom
            )
            insets
        }
    }

    private fun setupEventsRecyclerView() {
        eventsAdapter = EventAdapter { event ->
            if (tabType == TAB_TYPE_TEACHING && viewModel.currentUserRole.value == UserRole.USER) {
                // Point 5: Clean click matrix for Scheduled Events
                if (event.status == EventStatus.SCHEDULED) {
                    showLeaveForEvent(event) // Only your own scheduled classes will appear here now due to Point 4 logic
                } else {
                    showTeachingEventDetailsDialog(event)
                }
            } else {
                showTeachingEventDetailsDialog(event)
            }
        }
        
        leavesAdapter = LeaveApplicationAdapter(userRollNumber) { leaveApplication ->
            if (tabType == TAB_TYPE_TEACHING && viewModel.currentUserRole.value == UserRole.USER) {
                val isMyLeave = leaveApplication.rollNumber == userRollNumber
                
                if (isMyLeave && (leaveApplication.status == EventStatus.PENDING || leaveApplication.status == EventStatus.APPROVED)) {
                    // Clicked my own pending or approved leave -> Offer to cancel it
                    showCancelLeaveDialog(leaveApplication)
                } else if (!isMyLeave && leaveApplication.status == EventStatus.APPROVED) {
                    // Clicked someone else's approved leave -> Offer to substitute
                    showAcceptClassDialogForLeave(leaveApplication)
                } else if (!isMyLeave && leaveApplication.status == EventStatus.ACCEPTED && leaveApplication.substitutedByRollNumber == userRollNumber) {
                    // Clicked someone else's leave that I accepted -> Go straight to chained leave
                    showChainedLeaveConfirmationDialog(leaveApplication)
                }
                // Intentionally do nothing for the sender's own ACCEPTED leave or REJECTED leaves
            } else {
                showLeaveDetailsDialog(leaveApplication)
            }
        }
        
        // Create a proper config for the ConcatAdapter
        val config = ConcatAdapter.Config.Builder()
            .setIsolateViewTypes(true)
            .setStableIdMode(ConcatAdapter.Config.StableIdMode.NO_STABLE_IDS)
            .build()
            
        // Use ConcatAdapter with the config to show both events and leaves in the same list
        concatAdapter = ConcatAdapter(config, eventsAdapter, leavesAdapter)
        
        binding.recyclerEvents.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerEvents.adapter = concatAdapter
        
        // Show no events message by default
        updateNoEventsVisibility(true)
    }
    

    private fun observeViewModel() {
        // Observe user role
        viewModel.currentUserRole.observe(viewLifecycleOwner) { role ->
            updateUIForRole(role)
        }
        
        // Observe selected date
        viewModel.selectedDate.observe(viewLifecycleOwner) { date ->
            calendarAdapter.setSelectedDate(date)
            updateEventsForDate(date)
            
            // The date is now elegantly handled by the top app bar structure
            // Update the header label simply to: Events on 12th March
            val cal = java.util.Calendar.getInstance()
            cal.time = date
            val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
            val suffix = when {
                day in 11..13 -> "th"
                day % 10 == 1 -> "st"
                day % 10 == 2 -> "nd"
                day % 10 == 3 -> "rd"
                else -> "th"
            }
            val displayMonth = java.text.SimpleDateFormat("MMMM", java.util.Locale.getDefault()).format(date)
            binding.tvSelectedDate.text = "Events on $day$suffix $displayMonth"
            
            Log.d("CalendarTabFragment", "Loading data for ${date.time}")
        }
        
        // Observe all events
        viewModel.events.observe(viewLifecycleOwner) { events ->
            // Update calendar with all events
            val adapter = CalendarGridAdapter(
                context = requireContext(), 
                events = events,
                leaveApplications = availableLeaves,
                teachingAssignments = viewModel.userAssignments.value ?: emptyList(),
                isTeachingCalendar = (tabType == TAB_TYPE_TEACHING),
                userRollNumber = userRollNumber,
                isAdmin = (viewModel.currentUserRole.value == UserRole.ADMIN1 || viewModel.currentUserRole.value == UserRole.ADMIN2)
            )
            // Keep the current month when updating
            adapter.setMonth(calendarAdapter.getCurrentYear(), calendarAdapter.getCurrentMonth())
            adapter.onDateSelectedListener = calendarAdapter.onDateSelectedListener
            viewModel.selectedDate.value?.let { adapter.setSelectedDate(it) }
            calendarAdapter = adapter
            binding.gridCalendar.adapter = calendarAdapter
            
            // Update current date's events
            viewModel.selectedDate.value?.let { updateEventsForDate(it) }
        }
        
        // Also observe leave applications for changes
        viewModel.leaveApplications.observe(viewLifecycleOwner) { leaveApplications ->
            // Store the leave applications
            availableLeaves = leaveApplications
            
            // Update the calendar grid with the new leave applications
            if (tabType == TAB_TYPE_TEACHING) {
                val adapter = CalendarGridAdapter(
                    context = requireContext(), 
                    events = viewModel.events.value ?: emptyList(),
                    leaveApplications = leaveApplications,
                    teachingAssignments = viewModel.userAssignments.value ?: emptyList(),
                    isTeachingCalendar = true,
                    userRollNumber = userRollNumber,
                    isAdmin = (viewModel.currentUserRole.value == UserRole.ADMIN1 || viewModel.currentUserRole.value == UserRole.ADMIN2)
                )
                // Keep the current month when updating
                adapter.setMonth(calendarAdapter.getCurrentYear(), calendarAdapter.getCurrentMonth())
                adapter.onDateSelectedListener = calendarAdapter.onDateSelectedListener
                viewModel.selectedDate.value?.let { adapter.setSelectedDate(it) }
                calendarAdapter = adapter
                binding.gridCalendar.adapter = calendarAdapter
                
                // Update current date's events and leaves
                viewModel.selectedDate.value?.let { updateEventsForDate(it) }
            }
        }
        
        // Observe user assignments
        if (tabType == TAB_TYPE_TEACHING) {
            viewModel.userAssignments.observe(viewLifecycleOwner) { assignments ->
                Log.d("CalendarTabFragment", "User assignments updated: ${assignments.size}")
                
                // Refresh the calendar adapter with the new assignments
                val adapter = CalendarGridAdapter(
                    context = requireContext(),
                    events = viewModel.events.value ?: emptyList(),
                    leaveApplications = availableLeaves,
                    teachingAssignments = assignments,
                    isTeachingCalendar = true,
                    userRollNumber = userRollNumber,
                    isAdmin = (viewModel.currentUserRole.value == UserRole.ADMIN1 || viewModel.currentUserRole.value == UserRole.ADMIN2)
                )
                adapter.setMonth(calendarAdapter.getCurrentYear(), calendarAdapter.getCurrentMonth())
                adapter.onDateSelectedListener = calendarAdapter.onDateSelectedListener
                viewModel.selectedDate.value?.let { adapter.setSelectedDate(it) }
                calendarAdapter = adapter
                binding.gridCalendar.adapter = calendarAdapter
                
                viewModel.selectedDate.value?.let { updateEventsForDate(it) }
            }
        }
    }
    
    fun updateEventsForDate(date: Date) {
        // Log the update process for debugging
        Log.d("CalendarTabFragment", "Updating events for date: ${dateFormat.format(date)}")
        
        // Get events for this date with fresh data from repository
        val eventsForDate = viewModel.getEventsForDay(date).toMutableList()
        
        // Get leaves for this date FIRST so we can filter virtual events
        val leavesForDate = availableLeaves.filter { isSameDay(it.date, date) }
        val pendingLeaves = viewModel.getLeaveApplicationsForDay(date)
        
        // Combine all leaves (approved and pending)
        var allLeaves = leavesForDate + pendingLeaves.filter { !leavesForDate.any { approved -> approved.id == it.id } }
        
        // Point 4: Filter irrelevant substitutions.
        val role = viewModel.currentUserRole.value
        val isAdmin = role == UserRole.ADMIN1 || role == UserRole.ADMIN2

        if (tabType == TAB_TYPE_TEACHING) {
            if (isAdmin) {
                // Admins see all classes available for substitution and all classes being substituted
                allLeaves = allLeaves.filter { leave ->
                    leave.status == EventStatus.APPROVED || leave.status == EventStatus.ACCEPTED
                }
            } else if (userRollNumber.isNotEmpty()) {
                val myLeavesThisDay = allLeaves.filter { it.rollNumber == userRollNumber }
                
                allLeaves = allLeaves.filter { leave ->
                    val isMyLeave = leave.rollNumber == userRollNumber
                    
                    val iAcceptedIt = leave.status == EventStatus.ACCEPTED && leave.substitutedByRollNumber == userRollNumber
                    var showIAcceptedIt = false
                    if (iAcceptedIt) {
                        val iTookLeaveForThis = myLeavesThisDay.any { myLeave -> 
                            myLeave.subject == leave.subject && myLeave.slot == leave.slot && (myLeave.status == EventStatus.PENDING || myLeave.status == EventStatus.APPROVED)
                        }
                        showIAcceptedIt = !iTookLeaveForThis
                    }
                    
                    val isUnaccepted = leave.status != EventStatus.ACCEPTED

                    // Show it if it's my leave, or I accepted it (and didn't apply for leave), or it hasn't been accepted yet
                    isMyLeave || showIAcceptedIt || isUnaccepted
                }
            }
        }
        
        Log.d("CalendarTabFragment", "Found ${allLeaves.size} relevant leaves for date")
        
        // Map user assignments to events if this is the teaching tab
        if (tabType == TAB_TYPE_TEACHING) {
            val assignments = viewModel.userAssignments.value ?: emptyList()
            if (assignments.isNotEmpty()) {
                val cal = Calendar.getInstance().apply { time = date }
                val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                val dayName = dayFormat.format(cal.time) // "Mon", "Tue", etc.
                
                Log.d("CalendarTabFragment", "⭐ CHECKING ASSIGNMENTS ⭐")
                Log.d("CalendarTabFragment", "Target Day: '$dayName'")
                
                val dayAssignments = assignments.filter { 
                    val dbDay = it.dayName.trim().lowercase()
                    val targetDay = dayName.trim().lowercase()
                    Log.d("CalendarTabFragment", "Comparing: DB day = '$dbDay' vs Selected = '$targetDay'")
                    
                    // Match if they are equal OR if one is a substring of the other (e.g. "fri" vs "friday")
                    dbDay == targetDay || dbDay.startsWith(targetDay) || targetDay.startsWith(dbDay)
                }
                
                Log.d("CalendarTabFragment", "Found ${dayAssignments.size} assignments out of ${assignments.size} total for $dayName")
                
                // Get leaves specifically for this user on this date
                val userLeavesOnDate = allLeaves.filter { it.rollNumber == userRollNumber }
                
                val virtualEvents = dayAssignments.mapNotNull { assignment ->
                    // Check if there is already a leave applied for this specific class
                    val hasLeaveForThisClass = userLeavesOnDate.any { leave -> 
                        leave.subject.equals(assignment.subjectName, ignoreCase = true) && 
                        leave.slot.equals(assignment.slotName, ignoreCase = true) 
                    }
                    
                    if (hasLeaveForThisClass) {
                        null // Skip generating the scheduled class card
                    } else {
                        CalendarEvent(
                            id = "assignment_${assignment.subjectCode}_${assignment.dayName}_${assignment.slotName}",
                            date = date,
                            title = assignment.subjectName,
                            description = "${assignment.schoolName} - ${assignment.classAndSection}",
                            eventType = EventType.TEACHING,
                            timeSlot = assignment.slotName,
                            status = EventStatus.SCHEDULED,
                            createdBy = "system",
                            timestamp = System.currentTimeMillis()
                        )
                    }
                }
                
                // Add virtual events at the top
                eventsForDate.addAll(0, virtualEvents)
            }
        }
        Log.d("CalendarTabFragment", "Found ${eventsForDate.size} total events for date")
        
        // Update adapters on the main thread to ensure proper synchronization
        activity?.runOnUiThread {
            // Update with the new data
            eventsAdapter.updateEvents(eventsForDate)
            leavesAdapter.updateLeaves(allLeaves)
            
            // Explicitly notify the ConcatAdapter that data has changed
            concatAdapter.notifyDataSetChanged()
            
            // Update visibility based on whether there are events or leaves
            updateNoEventsVisibility(eventsForDate.isEmpty() && allLeaves.isEmpty())
        }
    }
    
    private fun updateUIForRole(role: UserRole) {
        // Update leavesAdapter with admin status
        if (::leavesAdapter.isInitialized) {
            leavesAdapter.setAdminStatus(role == UserRole.ADMIN1 || role == UserRole.ADMIN2)
        }
        
        // The redundant action buttons have been removed from the layout
        // so we don't need to update them anymore based on role
        
        // We can update anything else that needs to change based on role here
        when (role) {
            UserRole.ADMIN1, UserRole.ADMIN2 -> {
                // Admin-specific UI updates if needed
            }
            UserRole.USER -> {
                // User-specific UI updates if needed
            }
        }
    }
    
    private fun updateNoEventsVisibility(showNoEvents: Boolean) {
        binding.noEventsText.visibility = if (showNoEvents) View.VISIBLE else View.GONE
        binding.recyclerEvents.visibility = if (showNoEvents) View.GONE else View.VISIBLE
    }
    
    private fun showTeachingEventDetailsDialog(event: CalendarEvent) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_teaching_event_details, null)
        
        // Setup dialog view
        dialogView.findViewById<android.widget.TextView>(R.id.tvEventTitle).text = event.title
        
        // Create a comprehensive description that includes roll number if accepted
        val description = StringBuilder(event.description)
        if (event.location.isNotBlank()) {
            description.append("\n\nLocation: ${event.location}")
        }
        description.append("\n\nTime: ${event.timeSlot}")
        description.append("\nStatus: ${event.status}")
        
        // Add roll number information if the class has been accepted
        if (event.acceptedByRollNumber.isNotEmpty()) {
            description.append("\n\nThis class has been accepted by:")
            description.append("\nRoll Number: ${event.acceptedByRollNumber}")
        }
        
        dialogView.findViewById<android.widget.TextView>(R.id.tvEventDescription).text = description.toString()
        
        val dialogBuilder = MaterialAlertDialogBuilder(requireContext(), R.style.CalendarDarkDialog)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            
        // Admin delete button removed as per requirement to only show relevant info

        
        val dialog = dialogBuilder.show()
    }
    
    private fun showAcceptClassDialog(event: CalendarEvent) {
        // Check if event has already been accepted by a roll number
        if (event.acceptedByRollNumber.isNotEmpty()) {
            Toast.makeText(requireContext(), "This class has already been accepted by roll number: ${event.acceptedByRollNumber}", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check if there are any available leaves for this date
        val availableLeavesForDate = viewModel.getLeaveApplicationsForDay(event.date)
            .filter { it.status == EventStatus.APPROVED }
        
        // Log for debugging
        Log.d("CalendarTabFragment", "Date: ${dateFormat.format(event.date)}")
        Log.d("CalendarTabFragment", "All leave applications: ${viewModel.getLeaveApplicationsForDay(event.date).size}")
        Log.d("CalendarTabFragment", "Available leaves: ${availableLeavesForDate.size}")
        
        if (availableLeavesForDate.isEmpty()) {
            Toast.makeText(requireContext(), "Cannot accept class: No available leaves for this date", Toast.LENGTH_SHORT).show()
            return
        }
        
        // First show the list of available leaves to select from
        val leaveOptions = availableLeavesForDate.map { leave -> 
            "Student: ${leave.userName}, Roll: ${leave.rollNumber}, Slot: ${leave.slot}, Subject: ${leave.subject}"
        }.toTypedArray()
        
        MaterialAlertDialogBuilder(requireContext(), R.style.CalendarDarkDialog)
            .setTitle("Select Leave to Substitute")
            .setItems(leaveOptions) { _, which ->
                val selectedLeave = availableLeavesForDate[which]
                
                // Create a virtual class event for this leave if needed
                val eventToUse = if (event.eventType == EventType.TEACHING) {
                    // If we already have a teaching event, use that
                    event
                } else {
                    // Otherwise create a virtual class event
                    CalendarEvent(
                        id = "virtual_" + selectedLeave.id,
                        date = selectedLeave.date,
                        title = "Class for ${selectedLeave.subject}",
                        description = "Substitution class for ${selectedLeave.userName}",
                        eventType = EventType.TEACHING,
                        timeSlot = selectedLeave.slot,
                        status = EventStatus.SCHEDULED,
                        createdBy = selectedLeave.userName,
                        timestamp = System.currentTimeMillis()
                    )
                }
                
                showClassAcceptanceDetailsDialog(eventToUse, selectedLeave)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showClassAcceptanceDetailsDialog(event: CalendarEvent, selectedLeave: LeaveApplication) {
        // Prevent accepting if the logged-in user hasn't set their roll number
        if (userRollNumber.isEmpty()) {
            Toast.makeText(requireContext(), "Your roll number is not configured properly. Cannot accept class.", Toast.LENGTH_SHORT).show()
            return
        }

        // Verify that the roll number is DIFFERENT from the selected leave's roll number
        if (userRollNumber == selectedLeave.rollNumber) {
            Toast.makeText(requireContext(), 
                "You cannot accept your own leave application.", 
                Toast.LENGTH_LONG).show()
            return
        }

        // Check for schedule conflicts
        val cal = Calendar.getInstance().apply { time = event.date }
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dayName = dayFormat.format(cal.time).lowercase()
        
        val assignments = viewModel.userAssignments.value ?: emptyList()
        val hasConflict = assignments.any { assignment ->
            val dbDay = assignment.dayName.trim().lowercase()
            val isSameDay = dbDay == dayName || dbDay.startsWith(dayName) || dayName.startsWith(dbDay)
            val isSameSlot = assignment.slotName.equals(selectedLeave.slot, ignoreCase = true)
            isSameDay && isSameSlot
        }

        // Create details text with the selected leave information
        val detailsText = java.lang.StringBuilder()
        if (hasConflict) {
            detailsText.append("⚠️ WARNING: You are already scheduled for a class during this time slot today. Please ensure you can manage both or have made arrangements.\n\n")
        }
        
        detailsText.append("Class Details:\n")
        detailsText.append("Subject: ${selectedLeave.subject}\n")
        detailsText.append("Time: ${selectedLeave.slot}\n")
        detailsText.append("School: ${selectedLeave.school}\n")
        detailsText.append("Student on Leave: ${selectedLeave.userName}\n")
        detailsText.append("Student Roll Number: ${selectedLeave.rollNumber}\n\n")
        
        detailsText.append("Are you sure you want to substitute for this class?")

        MaterialAlertDialogBuilder(requireContext(), R.style.CalendarDarkDialog)
            .setTitle("Confirm Substitution")
            .setMessage(detailsText.toString())
            .setPositiveButton("Accept") { _, _ ->
                // For virtual events, record acceptance in the leave application itself
                lifecycleScope.launch {
                    try {
                        if (event.id.startsWith("virtual_")) {
                            // This is a virtual event based on a leave, update the leave directly
                            viewModel.markLeaveAsSubstituted(selectedLeave.id, userRollNumber, userName)
                        } else {
                            // This is a real teaching event
                            viewModel.acceptClass(event.id, userRollNumber, userName)
                        }
                        
                        Toast.makeText(requireContext(), "Class accepted successfully", Toast.LENGTH_SHORT).show()
                        
                        // Refresh the events display immediately
                        updateEventsForDate(event.date)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Failed to accept class: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("CalendarTabFragment", "Error accepting class", e)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showGeneralEventDetailsDialog(event: CalendarEvent) {
        val bookedStatus = if (event.bookedBy.isNotEmpty()) {
            "Booked by: ${event.bookedByName}"
        } else {
            "Available"
        }
        
        val locationText = if (event.location.isNotBlank()) "\nLocation: ${event.location}" else ""
        val dialogBuilder = MaterialAlertDialogBuilder(requireContext(), R.style.CalendarDarkDialog)
            .setTitle(event.title)
            .setMessage("${event.description}$locationText\n\nTime: ${event.timeSlot}\nStatus: ${event.status}\n$bookedStatus")
            .setPositiveButton("Close", null)
        
        // For users, show Book Slot button if the event is not booked
        if (viewModel.currentUserRole.value == UserRole.USER && event.bookedBy.isEmpty()) {
            dialogBuilder.setNeutralButton("Book Slot") { _, _ ->
                showBookSlotConfirmationDialog(event)
            }
        } 
        // For admins, optionally the book slot button
        else if (viewModel.currentUserRole.value == UserRole.ADMIN1 || viewModel.currentUserRole.value == UserRole.ADMIN2) {
            // Admin delete button removed as per requirement
               
            // If the event isn't booked, also allow admins to book it (as negative button)
            if (event.bookedBy.isEmpty()) {
                dialogBuilder.setNegativeButton("Book Slot") { _, _ ->
                    showBookSlotConfirmationDialog(event)
                }
            }
        }
        
        dialogBuilder.show()
    }
    
    private fun showTeachingDayOptions(date: Date) {
        // Determine availability of actions
        val assignments = viewModel.userAssignments.value ?: emptyList()
        val cal = Calendar.getInstance().apply { time = date }
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dayName = dayFormat.format(cal.time).lowercase()
        
        val hasClass = assignments.any { assignment ->
            val dbDay = assignment.dayName.trim().lowercase()
            dbDay == dayName || dbDay.startsWith(dayName) || dayName.startsWith(dbDay)
        }
        
        val availableLeavesForDate = viewModel.getLeaveApplicationsForDay(date)
            .filter { it.status == EventStatus.APPROVED && it.rollNumber != userRollNumber} // exclude user's own leaves
            
        val hasSubstitutions = availableLeavesForDate.isNotEmpty()
        
        if (!hasClass && !hasSubstitutions) {
            // Nothing to do for this day
            return
        }
        
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        
        if (hasClass) {
            options.add("Apply for Leave")
            actions.add { showLeaveApplicationDialog(date) }
        }
        
        if (hasSubstitutions) {
            options.add("Accept Substitute Class")
            actions.add { showAcceptClassDialogForDate(date) }
        }
        
        MaterialAlertDialogBuilder(requireContext(), R.style.CalendarDarkDialog)
            .setTitle(dateFormat.format(date))
            .setItems(options.toTypedArray()) { _, which ->
                actions[which].invoke()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showAcceptClassDialogForDate(date: Date) {
        val availableLeavesForDate = viewModel.getLeaveApplicationsForDay(date)
            .filter { it.status == EventStatus.APPROVED && it.rollNumber != userRollNumber }
            
        if (availableLeavesForDate.isEmpty()) {
            Toast.makeText(requireContext(), "No classes available to substitute on this date.", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show the list of available leaves to select from
        val leaveOptions = availableLeavesForDate.map { leave -> 
            "Student: ${leave.userName}, Roll: ${leave.rollNumber}, Slot: ${leave.slot}, Subject: ${leave.subject}"
        }.toTypedArray()
        
        MaterialAlertDialogBuilder(requireContext(), R.style.CalendarDarkDialog)
            .setTitle("Select Class to Substitute")
            .setItems(leaveOptions) { _, which ->
                val selectedLeave = availableLeavesForDate[which]
                
                // Create a virtual class event for this leave
                val virtualEvent = CalendarEvent(
                    id = "virtual_" + selectedLeave.id,
                    date = selectedLeave.date,
                    title = "Class for ${selectedLeave.subject}",
                    description = "Substitution class for ${selectedLeave.userName}",
                    eventType = EventType.TEACHING,
                    timeSlot = selectedLeave.slot,
                    status = EventStatus.SCHEDULED,
                    createdBy = selectedLeave.userName,
                    timestamp = System.currentTimeMillis()
                )
                
                showClassAcceptanceDetailsDialog(virtualEvent, selectedLeave)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Called when user taps an "AVAILABLE SUBSTITUTION" card directly. */
    private fun showAcceptClassDialogForLeave(leave: LeaveApplication) {
        val virtualEvent = CalendarEvent(
            id = "virtual_" + leave.id,
            date = leave.date,
            title = "Class for ${leave.subject}",
            description = "Substitution class for ${leave.userName}",
            eventType = EventType.TEACHING,
            timeSlot = leave.slot,
            status = EventStatus.SCHEDULED,
            createdBy = leave.userName,
            timestamp = System.currentTimeMillis()
        )
        showClassAcceptanceDetailsDialog(virtualEvent, leave)
    }

    /**
     * Called when user taps a SCHEDULED class card.
     * Matches the tapped event back to its SubjectAssignmentDetails and
     * goes straight to the confirm dialog — no class picker needed.
     */
    private fun showLeaveForEvent(event: CalendarEvent) {
        val assignments = viewModel.userAssignments.value ?: emptyList()

        // Match by subject name AND slot (both are stored in the virtual event)
        val matches = assignments.filter { a ->
            a.subjectName.equals(event.title, ignoreCase = true) &&
            a.slotName.equals(event.timeSlot, ignoreCase = true)
        }

        when {
            matches.size == 1 -> {
                // Exactly one match — go straight to confirm
                showLeaveConfirmationDialog(event.date, matches[0])
            }
            matches.size > 1 -> {
                // Rare edge: multiple assignments for same subject+slot on different days
                // Fall back to picker limited to these matches
                val classOptions = matches.map { a ->
                    "${a.subjectName} — ${a.schoolName} ${a.classAndSection}\nSlot: ${a.slotName}"
                }.toTypedArray()
                MaterialAlertDialogBuilder(requireContext(), R.style.CalendarDarkDialog)
                    .setTitle("Select Class for Leave")
                    .setItems(classOptions) { _, which ->
                        showLeaveConfirmationDialog(event.date, matches[which])
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                // No match found — fall back to day-based search
                showLeaveApplicationDialog(event.date)
            }
        }
    }

    private fun showLeaveApplicationDialog(date: Date) {
        // Fallback used when no specific event is known (e.g. from old paths)
        val assignments = viewModel.userAssignments.value ?: emptyList()
        val cal = Calendar.getInstance().apply { time = date }
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dayName = dayFormat.format(cal.time).lowercase()
        
        val dayAssignments = assignments.filter { assignment ->
            val dbDay = assignment.dayName.trim().lowercase()
            dbDay == dayName || dbDay.startsWith(dayName) || dayName.startsWith(dbDay)
        }
        
        if (dayAssignments.isEmpty()) {
            Toast.makeText(requireContext(), "You don't have any classes scheduled for $dayName.", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (dayAssignments.size == 1) {
            // Only one class that day — skip picker, go straight to confirm
            showLeaveConfirmationDialog(date, dayAssignments[0])
            return
        }

        // Multiple classes that day — show picker
        val classOptions = dayAssignments.map { assignment ->
            "${assignment.subjectName} — ${assignment.schoolName} ${assignment.classAndSection}\nSlot: ${assignment.slotName}"
        }.toTypedArray()
        
        MaterialAlertDialogBuilder(requireContext(), R.style.CalendarDarkDialog)
            .setTitle("Select Class for Leave")
            .setItems(classOptions) { _, which ->
                showLeaveConfirmationDialog(date, dayAssignments[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showLeaveConfirmationDialog(date: Date, assignment: SubjectAssignmentDetails) {
        // Edge Case: Check for duplicate leave
        val existingLeaves = viewModel.leaveApplications.value ?: emptyList()
        val isDuplicate = existingLeaves.any { leave ->
            isSameDay(leave.date, date) && 
            leave.subject == assignment.subjectName && 
            leave.slot == assignment.slotName &&
            leave.rollNumber == userRollNumber
        }

        if (isDuplicate) {
            Toast.makeText(requireContext(), "You have already applied for leave for this class.", Toast.LENGTH_SHORT).show()
            return
        }

        // Edge Case: Check for past dates
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val applyTitle = if (date.before(today)) {
            "Confirm Leave Application (Past Date)"
        } else {
            "Confirm Leave Application"
        }

        val details = "Subject: ${assignment.subjectName}\n" +
                "School: ${assignment.schoolName} ${assignment.classAndSection}\n" +
                "Slot: ${assignment.slotName}\n" +
                "Date: ${dateFormat.format(date)}"
        
        val message = if (date.before(today)) {
            "Warning: You are applying for leave on a past date.\n\n$details"
        } else {
            details
        }
        
        MaterialAlertDialogBuilder(requireContext(), R.style.CalendarDarkDialog)
            .setTitle(applyTitle)
            .setMessage(message)
            .setPositiveButton("Apply for Leave") { _, _ ->
                submitSmartLeave(date, assignment)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun submitSmartLeave(date: Date, assignment: SubjectAssignmentDetails) {
        val userId = userRollNumber.ifEmpty { "current_user_id" }
        val displayName = userName.ifEmpty { "Current User" }
        val slot = assignment.slotName
        val subject = assignment.subjectName
        val school = "${assignment.schoolName} ${assignment.classAndSection}".trim()
        val rollNumber = userRollNumber
        
        // Create a temporary leave application for immediate UI update
        val newLeave = LeaveApplication(
            id = UUID.randomUUID().toString(),
            userId = userId,
            userName = displayName,
            rollNumber = rollNumber,
            date = date,
            slot = slot,
            subject = subject,
            school = school,
            status = EventStatus.APPROVED, // Auto-approve per requirements
            timestamp = System.currentTimeMillis()
        )
        
        val updatedLeaves = availableLeaves.toMutableList()
        updatedLeaves.add(newLeave)
        availableLeaves = updatedLeaves
        updateCalendarWithLeaves(availableLeaves)
        
        lifecycleScope.launch {
            val repository = (viewModel as CalendarViewModel).getCalendarRepository()
            val success = repository.applyForLeave(userId, displayName, rollNumber, date, slot, subject, school)
            
            if (success) {
                Toast.makeText(requireContext(), "Leave application submitted!", Toast.LENGTH_SHORT).show()
                (parentFragment as? CalendarFragment)?.notifyLeaveApplicationSubmitted()
            } else {
                Toast.makeText(requireContext(), "Failed to submit leave", Toast.LENGTH_SHORT).show()
                val filteredLeaves = availableLeaves.filter { it.id != newLeave.id }
                availableLeaves = filteredLeaves
                updateCalendarWithLeaves(availableLeaves)
            }
        }
    }
    
    // Fallback manual leave form (used when no schedule data is available)
    private fun showManualLeaveApplicationDialog(date: Date) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_leave_application, null)
        
        val etSlot = dialogView.findViewById<android.widget.EditText>(R.id.etSlot)
        val etSubject = dialogView.findViewById<android.widget.EditText>(R.id.etSubject)
        val etSchool = dialogView.findViewById<android.widget.EditText>(R.id.etSchool)
        val etRollNumber = dialogView.findViewById<android.widget.EditText>(R.id.etRollNumber)
        
        // Pre-fill roll number if available
        if (userRollNumber.isNotEmpty()) {
            etRollNumber.setText(userRollNumber)
        }
        
        MaterialAlertDialogBuilder(requireContext(), R.style.CalendarDarkDialog)
            .setTitle("Apply for Leave")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val slot = etSlot.text.toString().trim()
                val subject = etSubject.text.toString().trim()
                val school = etSchool.text.toString().trim()
                val rollNumber = etRollNumber.text.toString().trim()
                
                if (slot.isEmpty() || subject.isEmpty() || school.isEmpty() || rollNumber.isEmpty()) {
                    Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val userId = userRollNumber.ifEmpty { "current_user_id" }
                val displayName = userName.ifEmpty { "Current User" }
                
                val newLeave = LeaveApplication(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    userName = displayName,
                    rollNumber = rollNumber,
                    date = date,
                    slot = slot,
                    subject = subject,
                    school = school,
                    status = EventStatus.APPROVED, // Auto-approve per requirements
                    timestamp = System.currentTimeMillis()
                )
                
                val updatedLeaves = availableLeaves.toMutableList()
                updatedLeaves.add(newLeave)
                availableLeaves = updatedLeaves
                updateCalendarWithLeaves(availableLeaves)
                
                lifecycleScope.launch {
                    val repository = (viewModel as CalendarViewModel).getCalendarRepository()
                    val success = repository.applyForLeave(userId, displayName, rollNumber, date, slot, subject, school)
                    
                    if (success) {
                        Toast.makeText(requireContext(), "Leave application submitted!", Toast.LENGTH_SHORT).show()
                        (parentFragment as? CalendarFragment)?.notifyLeaveApplicationSubmitted()
                    } else {
                        Toast.makeText(requireContext(), "Failed to submit leave", Toast.LENGTH_SHORT).show()
                        val filteredLeaves = availableLeaves.filter { it.id != newLeave.id }
                        availableLeaves = filteredLeaves
                        updateCalendarWithLeaves(availableLeaves)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    // Helper method to update the calendar with new leave applications
    private fun updateCalendarWithLeaves(leaveApplications: List<LeaveApplication>) {
        activity?.runOnUiThread {
            // Update calendar grid to highlight days with leave applications
            val adapter = CalendarGridAdapter(
                context = requireContext(), 
                events = viewModel.events.value ?: emptyList(),
                leaveApplications = leaveApplications,
                teachingAssignments = viewModel.userAssignments.value ?: emptyList(),
                isTeachingCalendar = (tabType == TAB_TYPE_TEACHING),
                userRollNumber = userRollNumber,
                isAdmin = (viewModel.currentUserRole.value == UserRole.ADMIN1 || viewModel.currentUserRole.value == UserRole.ADMIN2)
            )
            
            // Keep the current month and selected date
            adapter.setMonth(calendarAdapter.getCurrentYear(), calendarAdapter.getCurrentMonth())
            adapter.onDateSelectedListener = calendarAdapter.onDateSelectedListener
            viewModel.selectedDate.value?.let { adapter.setSelectedDate(it) }
            
            calendarAdapter = adapter
            binding.gridCalendar.adapter = calendarAdapter
            
            // Also update the event list for the current day
            viewModel.selectedDate.value?.let { updateEventsForDate(it) }
        }
    }
    
    private fun showBookSlotConfirmationDialog(event: CalendarEvent) {
        // Check if date is in the past
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        if (event.date.before(today)) {
            Toast.makeText(requireContext(), "Cannot book slots for past dates", Toast.LENGTH_SHORT).show()
            return
        }
        
        MaterialAlertDialogBuilder(requireContext(), R.style.CalendarDarkDialog)
            .setTitle("Book Slot")
            .setMessage("Would you like to book this slot for ${event.title} on ${dateFormat.format(event.date)}?")
            .setPositiveButton("Yes") { _, _ ->
                // In a real app, get the actual user ID and name from auth
                val userId = "current_user_id"
                val userName = "Current User"
                
                lifecycleScope.launch {
                    val repository = (viewModel as CalendarViewModel).getCalendarRepository()
                    val success = repository.bookEventSlot(event.id, userId, userName)
                    
                    if (success) {
                        Toast.makeText(requireContext(), "Slot booked successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to book slot", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showCancelLeaveDialog(leaveApplication: LeaveApplication) {
        val title = "Cancel Leave Application"
        val message = if (leaveApplication.status == EventStatus.ACCEPTED && leaveApplication.substitutedByRollNumber.isNotEmpty()) {
            "This class has already been accepted by ${leaveApplication.substitutedByRollNumber}. Cancelling your leave will notify them and you will have to take the class. Are you sure you want to cancel?"
        } else {
            "Are you sure you want to cancel your leave application for ${leaveApplication.subject}?"
        }

        MaterialAlertDialogBuilder(requireContext(), R.style.CalendarDarkDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel Leave") { _, _ ->
                val success = viewModel.deleteLeaveApplication(leaveApplication.id)
                if (success) {
                    Toast.makeText(requireContext(), "Leave application cancelled", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to cancel leave", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Keep Leave", null)
            .show()
    }

    private fun showCancelSubstitutionDialog(leaveApplication: LeaveApplication) {
        val title = "Withdraw Substitution"
        val message = "Are you sure you want to withdraw from substituting for ${leaveApplication.userName} in ${subjectName(leaveApplication)} at ${leaveApplication.slot}?"

        MaterialAlertDialogBuilder(requireContext(), R.style.CalendarDarkDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Withdraw") { _, _ ->
                lifecycleScope.launch {
                    val success = (viewModel as CalendarViewModel).withdrawSubstitution(leaveApplication.id)
                    if (success) {
                        Toast.makeText(requireContext(), "Substitution withdrawn", Toast.LENGTH_SHORT).show()
                        // The LiveData observers in the ViewModel will automatically 
                        // update the UI when the Firestore data changes.
                    } else {
                        Toast.makeText(requireContext(), "Failed to withdraw", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Keep Class", null)
            .show()
    }

    private fun subjectName(leaveApplication: LeaveApplication): String {
        return leaveApplication.subject.ifEmpty { "the class" }
    }
    
    private fun showLeaveDetailsDialog(leaveApplication: LeaveApplication) {
        val messageBuilder = StringBuilder()
        messageBuilder.append("Student: ${leaveApplication.userName}\n")
        messageBuilder.append("Roll Number: ${leaveApplication.rollNumber}\n")
        messageBuilder.append("Subject: ${leaveApplication.subject}\n")
        messageBuilder.append("Time: ${leaveApplication.slot}\n")
        messageBuilder.append("School: ${leaveApplication.school}\n")
        messageBuilder.append("Status: ${leaveApplication.status.name}\n")
        
        if (leaveApplication.status == EventStatus.ACCEPTED) {
            val subName = leaveApplication.substitutedByName.takeIf { it.isNotEmpty() } ?: "Unknown"
            val subRoll = leaveApplication.substitutedByRollNumber.takeIf { it.isNotEmpty() } ?: "Unknown"
            messageBuilder.append("\nSubstituted By:\n$subName ($subRoll)")
        }
        
        val dialogBuilder = MaterialAlertDialogBuilder(requireContext(), R.style.CalendarDarkDialog)
            .setTitle("Leave Application Details")
            .setMessage(messageBuilder.toString())
            .setPositiveButton("Close", null)
            
        // Setup Accept Class functionality
        val isUser = viewModel.currentUserRole.value == UserRole.USER
        val isApproved = leaveApplication.status == EventStatus.APPROVED
        val notSelf = leaveApplication.rollNumber != userRollNumber
        
        if (isUser && isApproved && notSelf) {
            dialogBuilder.setNeutralButton("Accept Class") { _, _ ->
                // Create a virtual class event for this leave
                val virtualEvent = CalendarEvent(
                    id = "virtual_" + leaveApplication.id,
                    date = leaveApplication.date,
                    title = "Class for ${leaveApplication.subject}",
                    description = "Substitution class for ${leaveApplication.userName}",
                    eventType = EventType.TEACHING,
                    timeSlot = leaveApplication.slot,
                    status = EventStatus.SCHEDULED,
                    createdBy = leaveApplication.userName,
                    timestamp = System.currentTimeMillis()
                )
                showClassAcceptanceDetailsDialog(virtualEvent, leaveApplication)
            }
        }
        

        // Admin delete button removed as per requirement to only show relevant info

        
        dialogBuilder.show()
    }
    
    private fun showChainedLeaveConfirmationDialog(originalLeave: LeaveApplication) {
        val details = "Subject: ${originalLeave.subject}\n" +
                "School: ${originalLeave.school}\n" +
                "Slot: ${originalLeave.slot}\n" +
                "Date: ${dateFormat.format(originalLeave.date)}"

        MaterialAlertDialogBuilder(requireContext(), R.style.CalendarDarkDialog)
            .setTitle("Apply for Leave")
            .setMessage("You accepted this class for ${originalLeave.userName}, but now want to apply for leave. Are you sure?\n\n$details")
            .setPositiveButton("Apply for Leave") { _, _ ->
                submitChainedLeave(originalLeave)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitChainedLeave(originalLeave: LeaveApplication) {
        val userId = userRollNumber.ifEmpty { "current_user_id" }
        val displayName = userName.ifEmpty { "Current User" }
        val rollNum = userRollNumber
        
        // Create a temporary leave application for immediate UI update
        val newLeave = LeaveApplication(
            id = UUID.randomUUID().toString(),
            userId = userId,
            userName = displayName,
            rollNumber = rollNum,
            date = originalLeave.date,
            slot = originalLeave.slot,
            subject = originalLeave.subject,
            school = originalLeave.school,
            status = EventStatus.APPROVED, // Auto-approve per requirements
            timestamp = System.currentTimeMillis()
        )
        
        val updatedLeaves = availableLeaves.toMutableList()
        updatedLeaves.add(newLeave)
        availableLeaves = updatedLeaves
        updateEventsForDate(originalLeave.date)
        
        lifecycleScope.launch {
            val repository = (viewModel as CalendarViewModel).getCalendarRepository()
            val success = repository.applyForLeave(
                userId = userId, 
                userName = displayName, 
                rollNumber = rollNum, 
                date = originalLeave.date, 
                slot = originalLeave.slot, 
                subject = originalLeave.subject, 
                school = originalLeave.school
            )
            
            if (success) {
                Toast.makeText(requireContext(), "Leave application submitted!", Toast.LENGTH_SHORT).show()
                (parentFragment as? CalendarFragment)?.notifyLeaveApplicationSubmitted()
            } else {
                Toast.makeText(requireContext(), "Failed to submit leave", Toast.LENGTH_SHORT).show()
                val filteredLeaves = availableLeaves.filter { it.id != newLeave.id }
                availableLeaves = filteredLeaves
                updateEventsForDate(originalLeave.date)
            }
        }
    }
    
    // Helper enum for distinguishing between different types of entries
    private enum class EntryType {
        EVENT,
        LEAVE_APPLICATION
    }
    
    // Show confirmation dialog before deletion
    private fun showDeleteConfirmationDialog(itemName: String, itemId: String, itemType: EntryType) {
        MaterialAlertDialogBuilder(requireContext(), R.style.CalendarDarkDialog)
            .setTitle("Confirm Deletion")
            .setMessage("Are you sure you want to delete this $itemName? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                when (itemType) {
                    EntryType.EVENT -> deleteEvent(itemId)
                    EntryType.LEAVE_APPLICATION -> deleteLeaveApplication(itemId)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    // Delete an event from the database
    private fun deleteEvent(eventId: String) {
        lifecycleScope.launch {
            try {
                val success = viewModel.deleteEvent(eventId)
                if (success) {
                    Toast.makeText(requireContext(), "Event deleted successfully", Toast.LENGTH_SHORT).show()
                    // Refresh the events list
                    viewModel.selectedDate.value?.let { updateEventsForDate(it) }
                } else {
                    Toast.makeText(requireContext(), "Failed to delete event", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("CalendarTabFragment", "Error deleting event", e)
            }
        }
    }
    
    // Delete a leave application from the database
    private fun deleteLeaveApplication(leaveId: String) {
        lifecycleScope.launch {
            try {
                val success = viewModel.deleteLeaveApplication(leaveId)
                if (success) {
                    Toast.makeText(requireContext(), "Leave application deleted successfully", Toast.LENGTH_SHORT).show()
                    // Refresh the events list
                    viewModel.selectedDate.value?.let { updateEventsForDate(it) }
                } else {
                    Toast.makeText(requireContext(), "Failed to delete leave application", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("CalendarTabFragment", "Error deleting leave application", e)
            }
        }
    }
    
    fun loadAllLeaveApplications() {
        lifecycleScope.launch {
            try {
                Log.d("CalendarTabFragment", "Loading all leave applications...")
                val repository = (viewModel as CalendarViewModel).getCalendarRepository()
                
                // Get ALL leave applications, not just approved ones
                val allLeaveApplications = withContext(Dispatchers.IO) {
                    repository.getAllLeaveApplications()
                }
                
                Log.d("CalendarTabFragment", "Loaded ${allLeaveApplications.size} leave applications")
                
                // Count by status
                val pendingCount = allLeaveApplications.count { it.status == EventStatus.PENDING }
                val approvedCount = allLeaveApplications.count { it.status == EventStatus.APPROVED }
                val rejectedCount = allLeaveApplications.count { it.status == EventStatus.REJECTED }
                
                Log.d("CalendarTabFragment", "Leave applications by status: Pending=$pendingCount, Approved=$approvedCount, Rejected=$rejectedCount")
                
                availableLeaves = allLeaveApplications
                
                // Update calendar UI with the loaded leave applications
                updateCalendarWithLeaves(allLeaveApplications)
            } catch (e: Exception) {
                Log.e("CalendarTabFragment", "Error loading leave applications: ${e.message}")
            }
        }
    }
    
    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
               cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Add a method to get the currently selected date
    fun getSelectedDate(): Date? {
        return viewModel.selectedDate.value
    }

    // Add method to get the month header text
    fun getMonthHeaderText(): String? {
        return "${binding.tvMonthName.text} ${binding.tvYearNumber.text}"
    }

    // Add method to get the currently selected day
    fun getSelectedDay(): Int? {
        return viewModel.selectedDate.value?.let { date ->
            val cal = Calendar.getInstance()
            cal.time = date
            cal.get(Calendar.DAY_OF_MONTH)
        }
    }

    // Add this method to get the tab type
    fun getTabType(): String {
        return tabType
    }

    companion object {
        const val TAB_TYPE_TEACHING = "teaching"
        const val TAB_TYPE_EVENTS = "events"
        private const val ARG_TAB_TYPE = "tab_type"

        fun newInstance(tabType: String): CalendarTabFragment {
            return CalendarTabFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TAB_TYPE, tabType)
                }
            }
        }
    }
} 
