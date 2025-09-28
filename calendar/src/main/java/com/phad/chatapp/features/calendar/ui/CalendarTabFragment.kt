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
import com.phad.chatapp.features.calendar.R
import com.phad.chatapp.features.calendar.databinding.FragmentCalendarTabBinding
import com.phad.chatapp.features.calendar.models.CalendarEvent
import com.phad.chatapp.features.calendar.models.EventStatus
import com.phad.chatapp.features.calendar.models.EventType
import com.phad.chatapp.features.calendar.models.LeaveApplication
import com.phad.chatapp.features.calendar.models.UserRole
import com.phad.chatapp.features.calendar.repository.CalendarRepository
import com.phad.chatapp.features.calendar.viewmodel.CalendarViewModel
import com.phad.chatapp.features.calendar.viewmodel.CalendarViewModelFactory
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
    private var approvedLeaves: List<LeaveApplication> = emptyList()

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
        
        val repository = CalendarRepository()
        
        // Create a tab-specific ViewModel factory with the appropriate tab type
        val factory = CalendarViewModelFactory(repository, tabType)
        
        // Create a tab-specific ViewModel so each tab has its own state
        viewModel = ViewModelProvider(this, factory).get(CalendarViewModel::class.java)
        
        setupCalendarGrid()
        setupEventsRecyclerView()
        setupMonthNavigation()
        setupRangeSelectionToggle()
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
            isTeachingCalendar = (tabType == TAB_TYPE_TEACHING)
        )
        calendarAdapter.onDateSelectedListener = { clickedDate ->
            // CRITICAL: Use this exact date that was clicked
            Log.d("CalendarTabFragment", "⭐ EXACT DATE RECEIVED FROM GRID: ${dateFormat.format(clickedDate)}")
            
            // First update this tab's viewModel
            viewModel.selectDate(clickedDate)
            
            // Update the shared parent ViewModel and lastClickedDate
            (parentFragment as? CalendarFragment)?.let { parent ->
                // This is the EXACT date clicked by the user - use it directly
                parent.updateLastClickedDate(clickedDate)
                
                // Show action dialog immediately without delay to ensure the exact date is used
                parent.showDateActionDialog(clickedDate, tabType)
            }
        }
        
        binding.gridCalendar.adapter = calendarAdapter
        
        // Set month display
        binding.tvMonthYear.text = calendarAdapter.getMonthAndYear()
        Log.d("CalendarTabFragment", "Initial month display: ${binding.tvMonthYear.text}")
        
        // Force initial date selection if needed
        if (viewModel.selectedDate.value == null) {
            val today = Calendar.getInstance().time
            viewModel.selectDate(today)
            
            // Also update the shared parent ViewModel
            (parentFragment as? CalendarFragment)?.let { parent ->
                parent.sharedViewModel.selectDate(today)
                Log.d("CalendarTabFragment", "Initial date set in parent ViewModel: ${dateFormat.format(today)}")
            }
        }
        
        // Load all leave applications
        loadAllLeaveApplications()
    }
    
    fun updateCalendarMonth(year: Int, month: Int) {
        calendarAdapter.setMonth(year, month)
        binding.tvMonthYear.text = calendarAdapter.getMonthAndYear()
        
        // Update events for the new month
        viewModel.events.value?.let { events ->
            val adapter = CalendarGridAdapter(requireContext(), events)
            adapter.setMonth(year, month)
            adapter.onDateSelectedListener = calendarAdapter.onDateSelectedListener
            calendarAdapter = adapter
            binding.gridCalendar.adapter = calendarAdapter
        }
    }
    
    private fun setupMonthNavigation() {
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
    
    private fun setupEventsRecyclerView() {
        eventsAdapter = EventAdapter { event ->
            if (tabType == TAB_TYPE_TEACHING) {
                showTeachingEventDetailsDialog(event)
            } else {
                showGeneralEventDetailsDialog(event)
            }
        }
        
        leavesAdapter = LeaveApplicationAdapter { leaveApplication ->
            showLeaveDetailsDialog(leaveApplication)
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
    
    private fun setupRangeSelectionToggle() {
        // Add a toggle button for range selection mode
        val toggleButton = view?.findViewById<MaterialButton>(R.id.btnToggleRange)
            ?: MaterialButton(requireContext()).also { btn ->
                btn.id = View.generateViewId()
                btn.text = "Toggle Range"
                btn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.accent))
                
                // Add the button to the layout
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(8, 8, 8, 8)
                binding.calendarActions.addView(btn, params)
            }
        
        toggleButton.setOnClickListener {
            viewModel.toggleRangeSelectionMode()
        }
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
            
            // Update the selected date display
            binding.tvSelectedDate.text = "Events for ${dateFormat.format(date)}"
        }
        
        // Observe all events
        viewModel.events.observe(viewLifecycleOwner) { events ->
            // Update calendar with all events
            val adapter = CalendarGridAdapter(
                context = requireContext(), 
                events = events,
                leaveApplications = approvedLeaves,
                isTeachingCalendar = (tabType == TAB_TYPE_TEACHING)
            )
            // Keep the current month when updating
            adapter.setMonth(calendarAdapter.getCurrentYear(), calendarAdapter.getCurrentMonth())
            adapter.onDateSelectedListener = calendarAdapter.onDateSelectedListener
            calendarAdapter = adapter
            binding.gridCalendar.adapter = calendarAdapter
            
            // Update current date's events
            viewModel.selectedDate.value?.let { updateEventsForDate(it) }
        }
        
        // Also observe leave applications for changes
        viewModel.leaveApplications.observe(viewLifecycleOwner) { leaveApplications ->
            // Store the leave applications
            approvedLeaves = leaveApplications
            
            // Update the calendar grid with the new leave applications
            if (tabType == TAB_TYPE_TEACHING) {
                val adapter = CalendarGridAdapter(
                    context = requireContext(), 
                    events = viewModel.events.value ?: emptyList(),
                    leaveApplications = leaveApplications,
                    isTeachingCalendar = true
                )
                // Keep the current month when updating
                adapter.setMonth(calendarAdapter.getCurrentYear(), calendarAdapter.getCurrentMonth())
                adapter.onDateSelectedListener = calendarAdapter.onDateSelectedListener
                calendarAdapter = adapter
                binding.gridCalendar.adapter = calendarAdapter
                
                // Update current date's events and leaves
                viewModel.selectedDate.value?.let { updateEventsForDate(it) }
            }
        }
        
        // Observe range selection mode
        viewModel.isRangeSelectionMode.observe(viewLifecycleOwner) { isRangeMode ->
            calendarAdapter.setRangeSelectionMode(isRangeMode)
            updateUIForRangeMode(isRangeMode)
        }
        
        // Observe date range
        viewModel.dateRange.observe(viewLifecycleOwner) { range ->
            calendarAdapter.setSelectedDateRange(
                range, 
                viewModel.startDate.value, 
                viewModel.endDate.value
            )
            
            if (range.isNotEmpty()) {
                updateEventsForDateRange(range)
            }
        }
    }
    
    fun updateEventsForDate(date: Date) {
        // Log the update process for debugging
        Log.d("CalendarTabFragment", "Updating events for date: ${dateFormat.format(date)}")
        
        // Get events for this date with fresh data from repository
        val eventsForDate = viewModel.getEventsForDay(date)
        Log.d("CalendarTabFragment", "Found ${eventsForDate.size} events for date")
        
        // Get leaves for this date
        val leavesForDate = approvedLeaves.filter { isSameDay(it.date, date) }
        
        // Also show any pending leaves from repository
        val pendingLeaves = viewModel.getLeaveApplicationsForDay(date)
        
        // Combine all leaves (approved and pending)
        val allLeaves = leavesForDate + pendingLeaves.filter { !leavesForDate.any { approved -> approved.id == it.id } }
        Log.d("CalendarTabFragment", "Found ${allLeaves.size} leaves for date")
        
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
        
        val leaveButton = dialogView.findViewById<MaterialButton>(R.id.btnApplyForLeave)
        val acceptClassButton = dialogView.findViewById<MaterialButton>(R.id.btnAcceptClass)
        
        val dialogBuilder = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Close", null)
            
        // Add delete button for admins
        if (viewModel.currentUserRole.value == UserRole.ADMIN1 || viewModel.currentUserRole.value == UserRole.ADMIN2) {
            dialogBuilder.setNeutralButton("Delete") { _, _ ->
                showDeleteConfirmationDialog(
                    itemName = "Event",
                    itemId = event.id,
                    itemType = EntryType.EVENT
                )
            }
        }
        
        val dialog = dialogBuilder.show()
        
        // Setup buttons if user role is USER
        if (viewModel.currentUserRole.value == UserRole.USER) {
            // Setup apply for leave button - always visible
            leaveButton.visibility = View.VISIBLE
            leaveButton.setOnClickListener {
                dialog.dismiss()
                showLeaveApplicationDialog(event.date)
            }
            
            // Only show accept class button if the class hasn't been accepted yet
            // and if there are approved leaves
            val hasApprovedLeaves = approvedLeaves.any { 
                isSameDay(it.date, event.date) && it.status == EventStatus.APPROVED 
            }
            
            if (event.acceptedByRollNumber.isEmpty() && hasApprovedLeaves) {
                acceptClassButton.visibility = View.VISIBLE
                acceptClassButton.setOnClickListener {
                    dialog.dismiss()
                    showAcceptClassDialog(event)
                }
            } else {
                acceptClassButton.visibility = View.GONE
            }
        } else {
            // Hide buttons for ADMIN
            leaveButton.visibility = View.GONE
            acceptClassButton.visibility = View.GONE
        }
    }
    
    private fun showAcceptClassDialog(event: CalendarEvent) {
        // Check if event has already been accepted by a roll number
        if (event.acceptedByRollNumber.isNotEmpty()) {
            Toast.makeText(requireContext(), "This class has already been accepted by roll number: ${event.acceptedByRollNumber}", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check if there are any approved leaves for this date
        val approvedLeaves = viewModel.getLeaveApplicationsForDay(event.date)
            .filter { it.status == EventStatus.APPROVED }
        
        // Log for debugging
        Log.d("CalendarTabFragment", "Date: ${dateFormat.format(event.date)}")
        Log.d("CalendarTabFragment", "All leave applications: ${viewModel.getLeaveApplicationsForDay(event.date).size}")
        Log.d("CalendarTabFragment", "Approved leaves: ${approvedLeaves.size}")
        
        if (approvedLeaves.isEmpty()) {
            Toast.makeText(requireContext(), "Cannot accept class: No approved leaves for this date", Toast.LENGTH_SHORT).show()
            return
        }
        
        // First show the list of approved leaves to select from
        val leaveOptions = approvedLeaves.map { leave -> 
            "Student: ${leave.userName}, Roll: ${leave.rollNumber}, Slot: ${leave.slot}, Subject: ${leave.subject}"
        }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle("Select Leave to Substitute")
            .setItems(leaveOptions) { _, which ->
                val selectedLeave = approvedLeaves[which]
                
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
        // Show the dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_accept_class, null)
        val etRollNumber = dialogView.findViewById<TextInputEditText>(R.id.etRollNumber)
        val tvClassDetails = dialogView.findViewById<TextView>(R.id.tvClassDetails)
        
        // Create details text with the selected leave information
        val detailsText = StringBuilder()
        detailsText.append("Subject: ${selectedLeave.subject}\n")
        detailsText.append("Time: ${selectedLeave.slot}\n")
        detailsText.append("Student on Leave: ${selectedLeave.userName}\n\n")
        
        detailsText.append("SELECTED LEAVE:\n")
        detailsText.append("Student: ${selectedLeave.userName}\n")
        detailsText.append("Roll Number: ${selectedLeave.rollNumber}\n")
        detailsText.append("Subject: ${selectedLeave.subject}\n")
        detailsText.append("Slot: ${selectedLeave.slot}\n\n")
        
        detailsText.append("Please enter your roll number to confirm\n")
        detailsText.append("(Must be different from the leave applicant's roll number)")
        
        tvClassDetails.text = detailsText.toString()
        
        // Don't pre-fill with the leave applicant's roll number anymore
        etRollNumber.setText("")
        
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Accept Class")
            .setView(dialogView)
            .setPositiveButton("Accept") { _, _ ->
                val rollNumber = etRollNumber.text.toString().trim()
                
                if (rollNumber.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter your roll number", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Verify that the roll number is DIFFERENT from the selected leave's roll number
                if (rollNumber == selectedLeave.rollNumber) {
                    Toast.makeText(requireContext(), 
                        "You cannot accept your own leave application. Roll number must be different from the leave applicant.", 
                        Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                
                // For virtual events, record acceptance in the leave application itself
                lifecycleScope.launch {
                    try {
                        if (event.id.startsWith("virtual_")) {
                            // This is a virtual event based on a leave, update the leave directly
                            viewModel.markLeaveAsSubstituted(selectedLeave.id, rollNumber)
                        } else {
                            // This is a real teaching event
                            viewModel.acceptClass(event.id, rollNumber)
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
            .create()
        
        dialog.show()
    }
    
    private fun showGeneralEventDetailsDialog(event: CalendarEvent) {
        val bookedStatus = if (event.bookedBy.isNotEmpty()) {
            "Booked by: ${event.bookedByName}"
        } else {
            "Available"
        }
        
        val locationText = if (event.location.isNotBlank()) "\nLocation: ${event.location}" else ""
        val dialogBuilder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(event.title)
            .setMessage("${event.description}$locationText\n\nTime: ${event.timeSlot}\nStatus: ${event.status}\n$bookedStatus")
            .setPositiveButton("Close", null)
        
        // For users, show Book Slot button if the event is not booked
        if (viewModel.currentUserRole.value == UserRole.USER && event.bookedBy.isEmpty()) {
            dialogBuilder.setNeutralButton("Book Slot") { _, _ ->
                showBookSlotConfirmationDialog(event)
            }
        } 
        // For admins, show delete button and optionally the book slot button
        else if (viewModel.currentUserRole.value == UserRole.ADMIN1 || viewModel.currentUserRole.value == UserRole.ADMIN2) {
            // Delete button as neutral button
            dialogBuilder.setNeutralButton("Delete") { _, _ ->
                showDeleteConfirmationDialog(
                    itemName = "Event",
                    itemId = event.id,
                    itemType = EntryType.EVENT
                )
            }
            
            // If the event isn't booked, also allow admins to book it (as negative button)
            if (event.bookedBy.isEmpty()) {
                dialogBuilder.setNegativeButton("Book Slot") { _, _ ->
                    showBookSlotConfirmationDialog(event)
                }
            }
        }
        
        dialogBuilder.show()
    }
    
    private fun showLeaveApplicationDialog(date: Date) {
        // Check if date is in the past
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        if (date.before(today)) {
            Toast.makeText(requireContext(), "Cannot apply for leave on past dates", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_leave_application, null)
        
        // Find views in dialog
        val etSlot = dialogView.findViewById<android.widget.EditText>(R.id.etSlot)
        val etSubject = dialogView.findViewById<android.widget.EditText>(R.id.etSubject)
        val etSchool = dialogView.findViewById<android.widget.EditText>(R.id.etSchool)
        val etRollNumber = dialogView.findViewById<android.widget.EditText>(R.id.etRollNumber)
        
        MaterialAlertDialogBuilder(requireContext())
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
                
                // In a real app, get the actual user ID and name from auth
                val userId = "current_user_id"
                val userName = "Current User"
                
                // Create a temporary leave application object for immediate UI update
                val newLeave = LeaveApplication(
                    id = UUID.randomUUID().toString(), // Temporary ID
                    userId = userId,
                    userName = userName,
                    rollNumber = rollNumber,
                    date = date,
                    slot = slot,
                    subject = subject,
                    school = school,
                    status = EventStatus.PENDING,
                    timestamp = System.currentTimeMillis()
                )
                
                // Add to the local list immediately for UI update
                val updatedLeaves = approvedLeaves.toMutableList()
                updatedLeaves.add(newLeave)
                approvedLeaves = updatedLeaves
                
                // Update calendar UI immediately
                Log.d("CalendarTabFragment", "Updating calendar with new leave application")
                updateCalendarWithLeaves(approvedLeaves)
                
                // Submit leave application to Firestore
                lifecycleScope.launch {
                    val repository = (viewModel as CalendarViewModel).getCalendarRepository()
                    val success = repository.applyForLeave(userId, userName, rollNumber, date, slot, subject, school)
                    
                    if (success) {
                        Toast.makeText(requireContext(), "Leave application submitted successfully", Toast.LENGTH_SHORT).show()
                        
                        // Notify parent fragment to update all calendar tabs
                        (parentFragment as? CalendarFragment)?.notifyLeaveApplicationSubmitted()
                    } else {
                        Toast.makeText(requireContext(), "Failed to submit leave application", Toast.LENGTH_SHORT).show()
                        
                        // Remove the temporary leave if submission failed
                        val filteredLeaves = approvedLeaves.filter { it.id != newLeave.id }
                        approvedLeaves = filteredLeaves
                        updateCalendarWithLeaves(approvedLeaves)
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
                isTeachingCalendar = (tabType == TAB_TYPE_TEACHING)
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
        
        MaterialAlertDialogBuilder(requireContext())
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

    private fun updateUIForRangeMode(isRangeMode: Boolean) {
        // Update UI elements for range selection mode
        val toggleButton = view?.findViewById<MaterialButton>(R.id.btnToggleRange)
        toggleButton?.text = if (isRangeMode) "Exit Range Mode" else "Select Range"
        
        // Update title text
        if (isRangeMode) {
            binding.tvSelectedDate.text = "Select a date range"
            
            val startDate = viewModel.startDate.value
            val endDate = viewModel.endDate.value
            
            if (startDate != null && endDate != null) {
                binding.tvSelectedDate.text = "Range: ${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}"
            } else if (startDate != null) {
                binding.tvSelectedDate.text = "Range start: ${dateFormat.format(startDate)}"
            }
        }
    }
    
    private fun updateEventsForDateRange(dateRange: List<Date>) {
        if (dateRange.isEmpty()) return
        
        val eventsInRange = viewModel.getEventsForDateRange()
        
        // Update adapter on the main thread
        activity?.runOnUiThread {
            eventsAdapter.updateEvents(eventsInRange)
            leavesAdapter.updateLeaves(emptyList()) // Clear leaves in range mode
            
            // Notify the ConcatAdapter
            concatAdapter.notifyDataSetChanged()
            
            updateNoEventsVisibility(eventsInRange.isEmpty())
        }
        
        // Update the selected date range display
        val startDate = viewModel.startDate.value
        val endDate = viewModel.endDate.value
        
        if (startDate != null && endDate != null) {
            binding.tvSelectedDate.text = "Events from ${dateFormat.format(startDate)} to ${dateFormat.format(endDate)}"
        }
    }

    private fun showLeaveDetailsDialog(leaveApplication: LeaveApplication) {
        val statusText = when (leaveApplication.status) {
            EventStatus.ACCEPTED -> "ACCEPTED/SUBSTITUTED"
            EventStatus.APPROVED -> "APPROVED"
            EventStatus.REJECTED -> "REJECTED"
            else -> "PENDING"
        }
        
        val messageBuilder = StringBuilder()
        messageBuilder.append("Student: ${leaveApplication.userName}\n")
        messageBuilder.append("Roll Number: ${leaveApplication.rollNumber}\n")
        messageBuilder.append("Subject: ${leaveApplication.subject}\n")
        messageBuilder.append("Time: ${leaveApplication.slot}\n")
        messageBuilder.append("School: ${leaveApplication.school}\n")
        messageBuilder.append("Status: $statusText\n")
        
        // Add substitution information if this leave has been accepted by someone
        if (leaveApplication.status == EventStatus.ACCEPTED && leaveApplication.substitutedByRollNumber.isNotEmpty()) {
            messageBuilder.append("\n")
            messageBuilder.append("----------------------------------------\n")
            messageBuilder.append("SUBSTITUTION INFORMATION:\n")
            messageBuilder.append("----------------------------------------\n")
            messageBuilder.append("Original Student: ${leaveApplication.userName}\n")
            messageBuilder.append("Original Roll Number: ${leaveApplication.rollNumber}\n")
            messageBuilder.append("Substituted By Roll Number: ${leaveApplication.substitutedByRollNumber}\n")
            messageBuilder.append("----------------------------------------")
        }
        
        val dialogBuilder = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Leave Application Details")
            .setMessage(messageBuilder.toString())
            .setPositiveButton("Close", null)
        
        // Add delete button for admins
        if (viewModel.currentUserRole.value == UserRole.ADMIN1 || viewModel.currentUserRole.value == UserRole.ADMIN2) {
            dialogBuilder.setNeutralButton("Delete") { _, _ ->
                showDeleteConfirmationDialog(
                    itemName = "Leave Application",
                    itemId = leaveApplication.id,
                    itemType = EntryType.LEAVE_APPLICATION
                )
            }
        }
        
        dialogBuilder.show()
    }
    
    // Helper enum for distinguishing between different types of entries
    private enum class EntryType {
        EVENT,
        LEAVE_APPLICATION
    }
    
    // Show confirmation dialog before deletion
    private fun showDeleteConfirmationDialog(itemName: String, itemId: String, itemType: EntryType) {
        MaterialAlertDialogBuilder(requireContext())
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
                
                approvedLeaves = allLeaveApplications
                
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
        return binding.tvMonthYear.text?.toString()
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