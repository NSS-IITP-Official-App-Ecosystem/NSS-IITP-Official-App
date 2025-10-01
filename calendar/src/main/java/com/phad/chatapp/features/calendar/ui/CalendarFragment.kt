package com.phad.chatapp.features.calendar.ui

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import com.phad.chatapp.features.calendar.R
import com.phad.chatapp.features.calendar.databinding.FragmentCalendarBinding
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
import com.google.firebase.auth.FirebaseAuth
import com.phad.chatapp.features.calendar.utils.CalendarSessionManager
import android.widget.LinearLayout

class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    
    // Main ViewModel for shared data (like user role)
    lateinit var sharedViewModel: CalendarViewModel
    
    // Calendar's session manager for user information
    private lateinit var sessionManager: CalendarSessionManager
    
    // LiveData for sharing user role with tab fragments
    private val _currentUserRole = MutableLiveData<UserRole>()
    val currentUserRole: LiveData<UserRole> get() = _currentUserRole
    
    private val simpleDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    
    // Track the exact date that was last clicked
    private var lastClickedDate: Date? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize SessionManager
        sessionManager = CalendarSessionManager(requireContext())
        
        val repository = CalendarRepository()
        val factory = CalendarViewModelFactory(repository)
        
        // Initialize the shared ViewModel (no tab type filtering)
        sharedViewModel = ViewModelProvider(this, factory).get(CalendarViewModel::class.java)
        
        setupViewPager()
        getUserRoleFromLogin()
        
        // Hide the FAB as we're using direct date clicks instead
        binding.fabAddEvent.visibility = View.GONE
        
        observeViewModel()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = CalendarPagerAdapter(this)

        // Set up TabLayout with ViewPager
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Teaching"
                else -> "Events"
            }
        }.attach()
    }

    // Define the adapter as an inner class
    private inner class CalendarPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2
        
        override fun createFragment(position: Int): Fragment {
            return CalendarTabFragment.newInstance(
                when (position) {
                    0 -> CalendarTabFragment.TAB_TYPE_TEACHING
                    else -> CalendarTabFragment.TAB_TYPE_EVENTS
                }
            )
        }
    }

    private fun getUserRoleFromLogin() {
        try {
            // Get user type from SessionManager
            val userType = sessionManager.fetchUserType()
            Log.d("CalendarFragment", "Fetched user type from SessionManager: $userType")

            // Set user role based on user type - include sanitization for null/empty values
            val role = if (userType.equals("Admin", ignoreCase = true)) {
                UserRole.ADMIN1
            } else {
                UserRole.USER
            }
            
            _currentUserRole.value = role
            sharedViewModel.setUserRole(role)
            
            // For debugging
            Log.d("CalendarFragment", "User role set to: $role (Type: $userType)")
        } catch (e: Exception) {
            // Default to USER if there's any error
            Log.e("CalendarFragment", "Error determining user role: ${e.message}")
            _currentUserRole.value = UserRole.USER
            sharedViewModel.setUserRole(UserRole.USER)
        }
    }

    private fun observeViewModel() {
        sharedViewModel.currentUserRole.observe(viewLifecycleOwner) { role ->
            _currentUserRole.value = role
            // Remove FAB-specific setup since we're not using it anymore
            // Just keep the role for date click actions
        }
        
        // Observe selected date
        sharedViewModel.selectedDate.observe(viewLifecycleOwner) { date ->
            // Log the date selection for debugging
            Log.d("CalendarFragment", "Selected date updated: ${simpleDateFormat.format(date)}")
        }
        
        // Observe date range selection mode
        sharedViewModel.isRangeSelectionMode.observe(viewLifecycleOwner) { isRangeMode ->
            // No longer need to update FAB state
        }
        
        // Observe date range
        sharedViewModel.dateRange.observe(viewLifecycleOwner) { range ->
            // Range selection functionality is preserved but not tied to FAB
        }
    }

    fun showDateActionDialog(date: Date, tabType: String) {
        Log.d("CalendarFragment", "Opening action dialog for date: ${simpleDateFormat.format(date)} with tabType: $tabType")
        
        // Check if date is in the past
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        val isDateInPast = date.before(today)
        
        // Show different options based on user role
        when (_currentUserRole.value) {
            UserRole.ADMIN1, UserRole.ADMIN2 -> {
                if (isDateInPast) {
                    // If date is in the past, only allow viewing events for admins
                    showPastDateOptionsDialog(date, tabType)
                } else {
                    showAdminOptionsDialog(date, tabType)
                }
            }
            UserRole.USER -> {
                if (isDateInPast) {
                    // For past dates, only allow viewing events
                    Toast.makeText(requireContext(), "Cannot create or modify events for past dates", Toast.LENGTH_SHORT).show()
                    showEventsForDate(date, tabType)
                } else if (tabType == CalendarTabFragment.TAB_TYPE_TEACHING) {
                    // User clicked on teaching tab - show options dialog for teaching
                    showTeachingOptionsForUser(date)
                } else {
                    // User clicked on events tab - show events for this date
                    showEventOptionsForUser(date, tabType)
                }
            }
            else -> { /* Do nothing */ }
        }
    }
    
    private fun showPastDateOptionsDialog(date: Date, tabType: String) {
        val dateStr = simpleDateFormat.format(date)
        
        // For past dates, admins can only view events or leave applications
        val options = if (tabType == CalendarTabFragment.TAB_TYPE_TEACHING) {
            arrayOf("View Events", "View Leave Applications")
        } else {
            arrayOf("View Events")
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Date Actions: $dateStr (Past Date)")
            .setMessage("Past dates are view-only. Events cannot be added or modified.")
            .setItems(options) { _, which ->
                if (tabType == CalendarTabFragment.TAB_TYPE_TEACHING) {
                    when (which) {
                        0 -> showEventsForDate(date, tabType)
                        1 -> showLeaveApplicationsDialog(date)
                    }
                } else {
                when (which) {
                        0 -> showEventsForDate(date, tabType)
                    }
                }
            }
            .show()
    }
    
    private fun showAdminOptionsDialog(date: Date, tabType: String) {
        val dateStr = simpleDateFormat.format(date)
        
        // Options based on tab type
        val options = if (tabType == CalendarTabFragment.TAB_TYPE_TEACHING) {
            arrayOf("Add Teaching", "View Events", "View Leave Applications", "Delete Event")
        } else {
            arrayOf("Add General Event", "View Events", "Delete Event")
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Date Actions: $dateStr")
            .setItems(options) { _, which ->
                if (tabType == CalendarTabFragment.TAB_TYPE_TEACHING) {
                    when (which) {
                        0 -> showAddEventDialog(date, tabType)
                        1 -> showEventsForDate(date, tabType)
                        2 -> showLeaveApplicationsDialog(date)
                        3 -> showDeleteEventDialog(date, tabType)
                    }
                } else {
                when (which) {
                        0 -> showAddEventDialog(date, tabType)
                        1 -> showEventsForDate(date, tabType)
                        2 -> showDeleteEventDialog(date, tabType)
                    }
                }
            }
            .show()
    }

    private fun showEventOptionsForUser(date: Date, tabType: String) {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Event Options for ${simpleDateFormat.format(date)}")
            .setItems(arrayOf("View Events", "Book Available Slot", "Accept Class")) { _, which ->
                when (which) {
                    0 -> showEventsForDate(date, tabType)
                    1 -> showBookSlotDialog(date)
                    2 -> showAcceptClassDialog(date)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }

    private fun showAcceptClassDialog(date: Date) {
        // Check if date is in the past
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        if (date.before(today)) {
            Toast.makeText(requireContext(), "Cannot accept classes for past dates", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Force refresh leave applications data
        lifecycleScope.launch {
            try {
                // Explicitly get the latest leave applications from the repository
                sharedViewModel.refreshLeaveApplications()
                
                // Get all approved leaves for this date with the refreshed data
                val approvedLeaves = sharedViewModel.getLeaveApplicationsForDay(date)
                    .filter { it.status == EventStatus.APPROVED }
                
                // Log for debugging
                Log.d("CalendarFragment", "Date: ${simpleDateFormat.format(date)}")
                Log.d("CalendarFragment", "All leave applications: ${sharedViewModel.getLeaveApplicationsForDay(date).size}")
                Log.d("CalendarFragment", "Approved leaves: ${approvedLeaves.size}")
                
                if (approvedLeaves.isEmpty()) {
                    Toast.makeText(requireContext(), "Cannot accept class: No approved leaves for this date", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Display UI on main thread
                activity?.runOnUiThread {
                    // First show the list of approved leaves to select from
                    val leaveOptions = approvedLeaves.map { leave -> 
                        "Student: ${leave.userName}, Roll: ${leave.rollNumber}, Slot: ${leave.slot}, Subject: ${leave.subject}"
                    }.toTypedArray()
                    
                    AlertDialog.Builder(requireContext())
                        .setTitle("Select Leave to Substitute")
                        .setItems(leaveOptions) { _, which ->
                            val selectedLeave = approvedLeaves[which]
                            
                            // Create a virtual class event for this leave - we don't need actual teaching events
                            val virtualClassEvent = CalendarEvent(
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
                            
                            // Show the acceptance dialog using this virtual event
                            showClassAcceptanceDetailsDialog(virtualClassEvent, selectedLeave)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            } catch (e: Exception) {
                Log.e("CalendarFragment", "Error loading leave applications: ${e.message}", e)
                Toast.makeText(requireContext(), "Error loading leave applications", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showClassAcceptanceDetailsDialog(event: CalendarEvent, selectedLeave: LeaveApplication) {
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
                            sharedViewModel.markLeaveAsSubstituted(selectedLeave.id, rollNumber)
                        } else {
                            // This is a regular teaching event
                            sharedViewModel.acceptClass(event.id, rollNumber)
                        }
                        Toast.makeText(requireContext(), "Class accepted successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Failed to accept class: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("CalendarFragment", "Error accepting class", e)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }

    private fun showEventsForDate(date: Date, tabType: String) {
        val dateStr = simpleDateFormat.format(date)
        
        // Get events for this date and tab type
        val eventsForDay = sharedViewModel.getEventsForDay(date)
            .filter { event ->
                if (tabType == CalendarTabFragment.TAB_TYPE_TEACHING) {
                    event.eventType == EventType.TEACHING
                } else {
                    event.eventType == EventType.GENERAL_EVENT
                }
            }
        
        if (eventsForDay.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("No Events")
                .setMessage("There are no events on $dateStr")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        // Create a custom dialog with a RecyclerView to display events
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_event_list, null)
        
        // If the layout doesn't exist yet, create it
        if (dialogView == null) {
            // Fallback to simple text list
            val eventDetails = eventsForDay.joinToString("\n\n") { event ->
                "${event.title}\nTime: ${event.timeSlot}\n${event.description}"
            }
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Events for $dateStr")
                .setMessage(eventDetails)
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerEvents)
        if (recyclerView == null) {
            // Fallback if RecyclerView not found
            val eventDetails = eventsForDay.joinToString("\n\n") { event ->
                "${event.title}\nTime: ${event.timeSlot}\n${event.description}"
            }
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Events for $dateStr")
                .setMessage(eventDetails)
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        // Create a simple adapter for events with options to view details and delete
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_event_with_actions, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }
            
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val event = eventsForDay[position]
                val view = holder.itemView
                
                // Set event details
                view.findViewById<TextView>(R.id.tvEventTitle).text = event.title
                view.findViewById<TextView>(R.id.tvEventTime).text = "Time: ${event.timeSlot}"
                view.findViewById<TextView>(R.id.tvEventDescription).text = event.description
                
                // Set status if applicable
                val statusText = when {
                    event.eventType == EventType.TEACHING && event.acceptedByRollNumber.isNotEmpty() ->
                        "ACCEPTED by: ${event.acceptedByRollNumber}"
                    event.bookedBy.isNotEmpty() ->
                        "BOOKED by: ${event.bookedByName}"
                    else ->
                        event.status.toString()
                }
                view.findViewById<TextView>(R.id.tvEventStatus).text = statusText
                
                // Set view details button
                val btnViewDetails = view.findViewById<View>(R.id.btnViewDetails)
                btnViewDetails.setOnClickListener {
                    // Show detailed view of the event
                    showEventDetailsDialog(event)
                }
                
                // Set delete button - only visible for admins
                val btnDelete = view.findViewById<View>(R.id.btnDelete)
                if (sharedViewModel.currentUserRole.value == UserRole.ADMIN1 || sharedViewModel.currentUserRole.value == UserRole.ADMIN2) {
                    btnDelete.visibility = View.VISIBLE
                    btnDelete.setOnClickListener {
                        showDeleteConfirmationDialog(
                            itemName = "Event",
                            itemId = event.id,
                            itemType = EntryType.EVENT
                        )
                    }
                } else {
                    btnDelete.visibility = View.GONE
                }
            }
            
            override fun getItemCount(): Int = eventsForDay.size
        }
        
        recyclerView.adapter = adapter
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Events for $dateStr")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    // Show detailed view of an event
    private fun showEventDetailsDialog(event: CalendarEvent) {
        val detailsBuilder = StringBuilder()
        detailsBuilder.append("Title: ${event.title}\n")
        detailsBuilder.append("Time: ${event.timeSlot}\n")
        if (event.location.isNotBlank()) {
            detailsBuilder.append("Location: ${event.location}\n")
        }
        detailsBuilder.append("Description: ${event.description}\n")
        detailsBuilder.append("Status: ${event.status}\n")
        
        // Add booking information if applicable
        if (event.bookedBy.isNotEmpty()) {
            detailsBuilder.append("\nBooked by: ${event.bookedByName}")
        }
        
        // Add roll number information if a class has been accepted
        if (event.eventType == EventType.TEACHING && event.acceptedByRollNumber.isNotEmpty()) {
            detailsBuilder.append("\n\nThis class has been accepted by:")
            detailsBuilder.append("\nRoll Number: ${event.acceptedByRollNumber}")
        }
        
        val dialogBuilder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(event.title)
            .setMessage(detailsBuilder.toString())
            .setPositiveButton("Close", null)
        
        // Add delete button for admins
        if (sharedViewModel.currentUserRole.value == UserRole.ADMIN1 || sharedViewModel.currentUserRole.value == UserRole.ADMIN2) {
            dialogBuilder.setNeutralButton("Delete") { _, _ ->
                showDeleteConfirmationDialog(
                    itemName = "Event",
                    itemId = event.id,
                    itemType = EntryType.EVENT
                )
            }
        }
        
        dialogBuilder.show()
    }

    private fun showBookSlotDialog(date: Date) {
        val dateStr = simpleDateFormat.format(date)
        
        // Check if date is in the past
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        if (date.before(today)) {
            Toast.makeText(requireContext(), "Cannot book slots for past dates", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get general events for this day
        val generalEvents = sharedViewModel.getEventsForDay(date)
            .filter { it.eventType == EventType.GENERAL_EVENT && it.bookedBy.isEmpty() }
        
        if (generalEvents.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("No Available Events")
                .setMessage("There are no available events on $dateStr to book.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        // Show list of available events
        val eventTitles = generalEvents.map { "${it.title} (${it.timeSlot})" }.toTypedArray()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Book Slot on $dateStr")
            .setItems(eventTitles) { _, which ->
                val event = generalEvents[which]
                
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Confirm Booking")
                    .setMessage("Would you like to book a slot for ${event.title} at ${event.timeSlot}?")
                    .setPositiveButton("Yes") { _, _ ->
                        // In a real app, get the actual user ID and name from auth
                        val userId = "current_user_id"
                        val userName = "Current User"
                        
                        lifecycleScope.launch {
                            val repository = sharedViewModel.getCalendarRepository()
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
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddEventDialog(date: Date, tabType: String) {
        val dateStr = simpleDateFormat.format(date)
        
        // Check if date is in the past
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        if (date.before(today)) {
            Toast.makeText(requireContext(), "Cannot create events for past dates", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Set event type based on the current tab
        val eventType = if (tabType == CalendarTabFragment.TAB_TYPE_TEACHING) 
            EventType.TEACHING else EventType.GENERAL_EVENT
        
        // Dynamically create the dialog view and its components
        val linearLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val etTitle = android.widget.EditText(requireContext()).apply {
            hint = "Event Title"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        val etDescription = android.widget.EditText(requireContext()).apply {
            hint = "Description"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            maxLines = 4
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        val etTimeSlot = android.widget.EditText(requireContext()).apply {
            hint = "Time Slot (e.g., 9:00 AM - 11:00 AM)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        linearLayout.addView(etTitle)
        linearLayout.addView(etDescription)
        linearLayout.addView(etTimeSlot)

        // Set default values
        val defaultTitle = if (eventType == EventType.TEACHING) 
            "Teaching - Subject" else "General Event"
        val defaultDesc = if (eventType == EventType.TEACHING)
            "Teaching session" else "General meeting or event"
        val defaultTime = "9:00 AM - 11:00 AM"
        
        etTitle.setText(defaultTitle)
        etDescription.setText(defaultDesc)
        etTimeSlot.setText(defaultTime)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create ${if (eventType == EventType.TEACHING) "Teaching" else "General"} Event")
            .setView(linearLayout)
            .setPositiveButton("Create") { _, _ ->
                val title = etTitle.text.toString().trim()
                val description = etDescription.text.toString().trim()
                val timeSlot = etTimeSlot.text.toString().trim()
                
                if (title.isEmpty() || description.isEmpty() || timeSlot.isEmpty()) {
                    Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Create the event using the repository
                lifecycleScope.launch {
                    val repository = sharedViewModel.getCalendarRepository()
                    try {
                        repository.addEvent(title, description, "", date, eventType)
                showEventCreatedConfirmation()
                    } catch (e: Exception) {
                        Toast.makeText(
                            requireContext(),
                            "Failed to create event: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showDeleteEventDialog(date: Date, tabType: String) {
        // Get events for the selected date filtered by tab type
        val eventsForDay = sharedViewModel.getEventsForDay(date)
        
        // Filter events by type based on current tab
        val events = eventsForDay.filter { event ->
            if (tabType == CalendarTabFragment.TAB_TYPE_TEACHING) {
                event.eventType == EventType.TEACHING
            } else {
                event.eventType == EventType.GENERAL_EVENT
            }
        }
        
        if (events.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("No Events")
                .setMessage("There are no events on this date to delete.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        val eventTitles = events.map { it.title }.toTypedArray()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Event")
            .setItems(eventTitles) { _, which ->
                val eventId = events[which].id
                // Implement event deletion in your repository
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Confirm Deletion")
                    .setMessage("Are you sure you want to delete this event?")
                    .setPositiveButton("Yes") { _, _ ->
                        if (sharedViewModel.deleteEvent(eventId)) {
                            showEventDeletedConfirmation()
                        } else {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Error")
                                .setMessage("Failed to delete the event. Please try again.")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showLeaveApplicationsDialog(date: Date) {
        // Get leave applications for the selected date
        val applications = sharedViewModel.getLeaveApplicationsForDay(date)
        
        if (applications.isEmpty()) {
        MaterialAlertDialogBuilder(requireContext())
                .setTitle("No Leave Applications")
                .setMessage("There are no leave applications for this date.")
                    .setPositiveButton("OK", null)
                    .show()
            return
        }
        
        // Create a custom dialog with a RecyclerView to display applications
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_leave_applications, null)
        
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerLeaveApplications)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        // Create a simple adapter for leave applications
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_leave_application, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }
            
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val application = applications[position]
                val view = holder.itemView
                
                // Set basic info
                val statusText = when (application.status) {
                    EventStatus.ACCEPTED -> "ACCEPTED/SUBSTITUTED"
                    EventStatus.APPROVED -> "APPROVED"
                    EventStatus.REJECTED -> "REJECTED"
                    else -> "PENDING"
                }
                
                view.findViewById<TextView>(R.id.tvLeaveName).text = application.userName
                view.findViewById<TextView>(R.id.tvLeaveSubject).text = application.subject
                view.findViewById<TextView>(R.id.tvLeaveStatus).text = statusText
                
                // Set the roll number text
                val rollNumberDetails = if (application.status == EventStatus.ACCEPTED && application.substitutedByRollNumber.isNotEmpty()) {
                    "Roll: ${application.rollNumber} (Substituted by: ${application.substitutedByRollNumber})"
                } else {
                    "Roll: ${application.rollNumber}"
                }
                view.findViewById<TextView>(R.id.tvLeaveRollNumber).text = rollNumberDetails
                
                // Set buttons
                val btnApprove = view.findViewById<View>(R.id.btnApprove)
                val btnReject = view.findViewById<View>(R.id.btnReject)
                
                // Handle click on the leave item to show detailed information
                view.setOnClickListener {
                    showLeaveDetailsDialog(application)
                }
                
                // Only show approve/reject buttons for pending applications
                // and only for admin users
                if (application.status == EventStatus.PENDING && 
                    (sharedViewModel.currentUserRole.value == UserRole.ADMIN1 || sharedViewModel.currentUserRole.value == UserRole.ADMIN2)) {
                    btnApprove.visibility = View.VISIBLE
                    btnReject.visibility = View.VISIBLE
                    
                    btnApprove.setOnClickListener {
                        updateLeaveStatus(application, EventStatus.APPROVED)
                    }
                    
                    btnReject.setOnClickListener {
                        updateLeaveStatus(application, EventStatus.REJECTED)
                    }
                } else {
                    btnApprove.visibility = View.GONE
                    btnReject.visibility = View.GONE
                }
            }
            
            override fun getItemCount(): Int = applications.size
        }
        
        recyclerView.adapter = adapter
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Leave Applications")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }
    
    private fun updateLeaveStatus(application: LeaveApplication, status: EventStatus) {
        lifecycleScope.launch {
            try {
                val repository = sharedViewModel.getCalendarRepository()
                val success = repository.updateLeaveStatus(application.id, status)
                
                if (success) {
                    val statusText = if (status == EventStatus.APPROVED) "approved" else "rejected"
                    Toast.makeText(
                        requireContext(),
                        "Leave application $statusText successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Failed to update leave application status",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun showUserBookSlotDialog(date: Date) {
        val dateStr = simpleDateFormat.format(date)
        
        // Get general events for this day
        val generalEvents = sharedViewModel.getEventsForDay(date)
            .filter { it.eventType == EventType.GENERAL_EVENT && it.bookedBy.isEmpty() }
        
        if (generalEvents.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("No Available Events")
                .setMessage("There are no available events on $dateStr to book.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        // Show list of available events
        val eventTitles = generalEvents.map { "${it.title} (${it.timeSlot})" }.toTypedArray()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Book Slot on $dateStr")
            .setItems(eventTitles) { _, which ->
                val event = generalEvents[which]
                
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Confirm Booking")
                    .setMessage("Would you like to book a slot for ${event.title} at ${event.timeSlot}?")
                    .setPositiveButton("Yes") { _, _ ->
                        // In a real app, get the actual user ID and name from auth
                        val userId = "current_user_id"
                        val userName = "Current User"
                        
                        lifecycleScope.launch {
                            val repository = sharedViewModel.getCalendarRepository()
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
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showEventCreatedConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Success")
            .setMessage("Event has been created successfully!")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showEventDeletedConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Success")
            .setMessage("Event has been deleted successfully!")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showDateRangeOptionsDialog(dateRange: List<Date>) {
        if (dateRange.isEmpty()) return
        
        val startDate = dateRange.first()
        val endDate = dateRange.last()
        val startDateStr = simpleDateFormat.format(startDate)
        val endDateStr = simpleDateFormat.format(endDate)
        
        // Different options based on user role
        when (_currentUserRole.value) {
            UserRole.ADMIN1, UserRole.ADMIN2 -> showAdminRangeOptionsDialog(dateRange, startDateStr, endDateStr)
            UserRole.USER -> showUserRangeOptionsDialog(dateRange, startDateStr, endDateStr)
            else -> { /* Do nothing */ }
        }
    }
    
    private fun showAdminRangeOptionsDialog(dateRange: List<Date>, startDateStr: String, endDateStr: String) {
        // Admin gets options for batch actions on the date range
        val options = arrayOf(
            "Create Recurring Events", 
            "View All Events in Range",
            "Manage Leave Applications in Range",
            "Exit Range Selection Mode",
            "Cancel"
        )
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Admin Options for $startDateStr to $endDateStr")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showCreateRecurringEventsDialog(dateRange)
                    1 -> showAllEventsInRangeDialog(dateRange)
                    2 -> showLeaveApplicationsInRangeDialog(dateRange)
                    3 -> sharedViewModel.toggleRangeSelectionMode() // Exit range mode
                    // 4 is Cancel, do nothing
                }
            }
            .show()
    }
    
    private fun showUserRangeOptionsDialog(dateRange: List<Date>, startDateStr: String, endDateStr: String) {
        // Get the current tab position to determine tab type
        val currentTabPosition = binding.viewPager.currentItem
        val tabType = if (currentTabPosition == 0) 
            CalendarTabFragment.TAB_TYPE_TEACHING
        else 
            CalendarTabFragment.TAB_TYPE_EVENTS
            
        // Different options based on tab type
        val actionLabel = if (tabType == CalendarTabFragment.TAB_TYPE_TEACHING) 
            "View Teaching Schedule" else "View Available Events"
            
        val options = arrayOf(
            actionLabel,
            "Exit Range Selection Mode",
            "Cancel"
        )
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Options for $startDateStr to $endDateStr")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (tabType == CalendarTabFragment.TAB_TYPE_TEACHING) {
                            showTeachingScheduleInRangeDialog(dateRange)
                        } else {
                            showAvailableEventsInRangeDialog(dateRange)
                        }
                    }
                    1 -> sharedViewModel.toggleRangeSelectionMode() // Exit range mode
                    // 2 is Cancel, do nothing
                }
            }
            .show()
    }
    
    private fun showCreateRecurringEventsDialog(dateRange: List<Date>) {
        // Implementation for creating recurring events across the date range
        Toast.makeText(requireContext(), "Creating recurring events for ${dateRange.size} days", Toast.LENGTH_SHORT).show()
        
        // For demo purposes, just show a dialog explaining what would happen
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create Recurring Events")
            .setMessage("This would allow creating events that repeat on all ${dateRange.size} selected days from ${simpleDateFormat.format(dateRange.first())} to ${simpleDateFormat.format(dateRange.last())}.")
            .setPositiveButton("OK") { _, _ ->
                // This would lead to a more detailed form for creating the recurring events
                sharedViewModel.toggleRangeSelectionMode() // Exit range mode after action
            }
            .show()
    }
    
    private fun showAllEventsInRangeDialog(dateRange: List<Date>) {
        // Get all events in the date range
        val eventsInRange = sharedViewModel.getEventsForDateRange()
        
        if (eventsInRange.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("No Events")
                .setMessage("There are no events in the selected date range.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        // Create a simple list of events
        val eventDetails = eventsInRange.joinToString("\n\n") { event ->
            "${simpleDateFormat.format(event.date)}: ${event.title} - ${event.description}"
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Events from ${simpleDateFormat.format(dateRange.first())} to ${simpleDateFormat.format(dateRange.last())}")
            .setMessage(eventDetails)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showLeaveApplicationsInRangeDialog(dateRange: List<Date>) {
        // Implementation for viewing leave applications across the date range
        // This would be similar to showLeaveApplicationsDialog but for multiple dates
        Toast.makeText(requireContext(), "Showing leave applications for the date range", Toast.LENGTH_SHORT).show()
        
        // For demo purposes, just show a confirmation dialog
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Leave Applications")
            .setMessage("This would show all leave applications between ${simpleDateFormat.format(dateRange.first())} and ${simpleDateFormat.format(dateRange.last())}.")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showTeachingScheduleInRangeDialog(dateRange: List<Date>) {
        // Implementation for viewing teaching schedule across the date range
        Toast.makeText(requireContext(), "Showing teaching schedule for the date range", Toast.LENGTH_SHORT).show()
        
        // For demo purposes, just show a confirmation dialog
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Teaching Schedule")
            .setMessage("This would show your teaching schedule between ${simpleDateFormat.format(dateRange.first())} and ${simpleDateFormat.format(dateRange.last())}.")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showAvailableEventsInRangeDialog(dateRange: List<Date>) {
        // Implementation for viewing available events across the date range
        Toast.makeText(requireContext(), "Showing available events for the date range", Toast.LENGTH_SHORT).show()
        
        // For demo purposes, just show a confirmation dialog
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Available Events")
            .setMessage("This would show all available events between ${simpleDateFormat.format(dateRange.first())} and ${simpleDateFormat.format(dateRange.last())}.")
            .setPositiveButton("OK", null)
            .show()
    }

    // Completely rewrite the getCurrentSelectedDate method with a more reliable approach
    private fun getCurrentSelectedDate(): Date {
        // If we have a direct touch/click event date, use that first
        if (lastClickedDate != null) {
            val clickedDate = lastClickedDate!!
            Log.d("CalendarFragment", "Using directly clicked date: ${simpleDateFormat.format(clickedDate)}")
            return clickedDate
        }
        
        // Otherwise use existing logic
        try {
            // Get the date from ViewModel as fallback
            val date = sharedViewModel.selectedDate.value ?: Calendar.getInstance().time
            return date
        } catch (e: Exception) {
            Log.e("CalendarFragment", "Error getting current date: ${e.message}", e)
            // Default to current date as fallback
            return Calendar.getInstance().time
        }
    }

    // Update the method to ensure the exact date is stored
    fun updateLastClickedDate(date: Date) {
        // Store the exact date clicked - create a clean version to be sure
        val cal = Calendar.getInstance().apply { 
            time = date
            // Keep only date parts, zero out time parts
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        // Set the exact clicked date
        lastClickedDate = cal.time
        
        // Always update the ViewModel as well
        sharedViewModel.selectDate(cal.time)
        
        // Get the day, month, and year for logging
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        
        // Log for debugging - use expanded format for clarity
        Log.d("CalendarFragment", "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d("CalendarFragment", "┃ EXACT DATE SET: $day/$month/$year")
        Log.d("CalendarFragment", "┃ Formatted: ${simpleDateFormat.format(lastClickedDate!!)}")
        Log.d("CalendarFragment", "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = CalendarFragment()
    }

    private fun showApplyForLeaveDialog(date: Date) {
        val dateStr = simpleDateFormat.format(date)
        
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
        
        // Inflate dialog for leave application
        val dialogView = layoutInflater.inflate(R.layout.dialog_leave_application, null)
        val subjectEditText = dialogView.findViewById<android.widget.EditText>(R.id.etSubject)
        val timeSlotEditText = dialogView.findViewById<android.widget.EditText>(R.id.etSlot)
        val schoolEditText = dialogView.findViewById<android.widget.EditText>(R.id.etSchool)
        val rollNumberEditText = dialogView.findViewById<android.widget.EditText>(R.id.etRollNumber)
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Apply for Leave on $dateStr")
            .setView(dialogView)
            .setPositiveButton("Apply", null)
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
        
        // Override the positive button click to validate
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val subject = subjectEditText.text.toString().trim()
            val timeSlot = timeSlotEditText.text.toString().trim()
            val school = schoolEditText.text.toString().trim()
            val rollNumber = rollNumberEditText.text.toString().trim()
            
            if (subject.isEmpty() || timeSlot.isEmpty() || school.isEmpty() || rollNumber.isEmpty()) {
                Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Get current user
            val currentUser = FirebaseAuth.getInstance().currentUser
            val userId = currentUser?.uid ?: ""
            val userName = currentUser?.displayName ?: "Unknown"
            
            // Apply for leave
            sharedViewModel.applyForLeave(
                date = date,
                userId = userId,
                userName = userName,
                slot = timeSlot,
                subject = subject,
                school = school,
                rollNumber = rollNumber
            )
            
            Toast.makeText(requireContext(), "Leave application submitted", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }

    /**
     * Notifies all tab fragments that a leave application has been submitted
     * This ensures that the color change is reflected in all teaching calendar instances
     */
    fun notifyLeaveApplicationSubmitted() {
        Log.d("CalendarFragment", "Broadcasting leave application update to all tabs")
        
        // Get all fragments in the ViewPager
        val fragmentManager = childFragmentManager
        fragmentManager.fragments.forEach { fragment ->
            if (fragment is CalendarTabFragment) {
                // Refresh leave applications for all tab fragments
                Log.d("CalendarFragment", "Refreshing leaves for tab: ${fragment.getTabType()}")
                fragment.loadAllLeaveApplications()
            }
        }
    }

    private fun showTeachingOptionsForUser(date: Date) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Teaching Calendar: ${simpleDateFormat.format(date)}")
            .setItems(arrayOf("Apply for Leave", "Accept Class", "View Events")) { _, which ->
                when (which) {
                    0 -> showApplyForLeaveDialog(date)
                    1 -> showAcceptClassDialog(date)
                    2 -> showEventsForDate(date, CalendarTabFragment.TAB_TYPE_TEACHING)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Add this method to display leave details
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
        if (sharedViewModel.currentUserRole.value == UserRole.ADMIN1 || sharedViewModel.currentUserRole.value == UserRole.ADMIN2) {
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
                val success = sharedViewModel.deleteEvent(eventId)
                if (success) {
                    Toast.makeText(requireContext(), "Event deleted successfully", Toast.LENGTH_SHORT).show()
                    // Refresh UI
                    sharedViewModel.selectedDate.value?.let { date ->
                        // Notify all tabs to refresh
                        notifyDateSelected(date)
                    }
                } else {
                    Toast.makeText(requireContext(), "Failed to delete event", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("CalendarFragment", "Error deleting event", e)
            }
        }
    }
    
    // Delete a leave application from the database
    private fun deleteLeaveApplication(leaveId: String) {
        lifecycleScope.launch {
            try {
                val success = sharedViewModel.deleteLeaveApplication(leaveId)
                if (success) {
                    Toast.makeText(requireContext(), "Leave application deleted successfully", Toast.LENGTH_SHORT).show()
                    // Refresh UI
                    sharedViewModel.selectedDate.value?.let { date ->
                        // Notify all tabs to refresh
                        notifyDateSelected(date)
                    }
                } else {
                    Toast.makeText(requireContext(), "Failed to delete leave application", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("CalendarFragment", "Error deleting leave application", e)
            }
        }
    }
    
    // Method to notify all tabs that a date has been selected
    private fun notifyDateSelected(date: Date) {
        for (fragment in childFragmentManager.fragments) {
            if (fragment is CalendarTabFragment) {
                fragment.updateEventsForDate(date)
            }
        }
    }
} 