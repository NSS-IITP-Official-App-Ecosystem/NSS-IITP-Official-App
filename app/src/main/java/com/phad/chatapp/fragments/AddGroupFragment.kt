package com.phad.chatapp.fragments

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.adapters.ParticipantAdapter
import com.phad.chatapp.models.Group
import com.phad.chatapp.models.User
import com.phad.chatapp.repositories.GroupRepository
import com.phad.chatapp.repositories.UserRepository
import com.phad.chatapp.utils.SessionManager
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import androidx.navigation.fragment.findNavController
import com.phad.chatapp.R
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.widget.ImageView

class AddGroupFragment : Fragment() {
    private val TAG = "AddGroupFragment"
    
    private val userRepository = UserRepository()
    private val groupRepository = GroupRepository()
    private lateinit var sessionManager: SessionManager
    private val db = FirebaseFirestore.getInstance()
    
    private lateinit var participantAdapter: ParticipantAdapter
    private lateinit var recyclerView: RecyclerView
    private var selectedParticipantCount = 0
    private var currentUserId = ""
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "AddGroupFragment.onCreateView started")
        
        try {
            val view = inflater.inflate(R.layout.fragment_add_group, container, false)
            
            // Set up status bar insets
            setupEdgeToEdge(view)
            
            // Initialize session
            sessionManager = SessionManager(requireContext())
            val userData = sessionManager.getUserDetails()
            currentUserId = userData[SessionManager.KEY_USER_ROLL_NUMBER] as String? ?: ""
            val userType = userData[SessionManager.KEY_USER_TYPE] as String? ?: ""
            
            Log.d(TAG, "Current user: ID='$currentUserId', Type='$userType'")
            
            // Set up back button navigation
            val backButton: View = view.findViewById(R.id.back_button)
            backButton.setOnClickListener {
                findNavController().navigateUp()
            }
            
            // Set up the course ID input (Group ID)
            val courseIdInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.course_id_input)
            
            // Set up the group name input
            val groupNameInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.group_name_input)
            
            // Set up the group description input
            val groupDescriptionInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.group_description_input)
            
            // Set up selected participants title
            val selectedParticipantsTitle = view.findViewById<TextView>(R.id.selected_participants_title)
            
            // Set up search components
            val searchContainer = view.findViewById<LinearLayout>(R.id.search_participants_container)
            val searchInput = view.findViewById<EditText>(R.id.search_participants_input)
            val clearSearchButton = view.findViewById<ImageView>(R.id.clear_search_button)
            
            // Set up action buttons
            val selectAllButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.select_all_button)
            val clearSelectionButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.clear_selection_button)
            
            // Set up recycler view for participants
            recyclerView = view.findViewById(R.id.participants_recycler_view)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            
            // Initialize adapter with the current user pre-selected and as a locked participant
            participantAdapter = ParticipantAdapter(
                selectedParticipants = mutableSetOf(currentUserId),
                lockedParticipants = setOf(currentUserId),
                onSelectionChanged = { count ->
                    updateSelectedParticipantsCount(count)
                }
            )
            recyclerView.adapter = participantAdapter
            recyclerView.isNestedScrollingEnabled = false
            recyclerView.visibility = View.VISIBLE
            
            // Load participants immediately
            lifecycleScope.launch {
                loadAllParticipants()
            }
            
            // Set up search functionality
            searchInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val query = s?.toString() ?: ""
                    filterParticipants(query)
                    clearSearchButton.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                }
            })
            
            // Set up clear search button
            clearSearchButton.setOnClickListener {
                searchInput.setText("")
                filterParticipants("")
                clearSearchButton.visibility = View.GONE
            }
            
            // Set up Select All button
            selectAllButton.setOnClickListener {
                selectAllParticipants()
            }
            
            // Set up Clear Selection button
            clearSelectionButton.setOnClickListener {
                clearAllSelections()
            }
            
            // Set up create button
            val createGroupButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.create_group_button)
            createGroupButton.setOnClickListener {
                val courseId = courseIdInput.text.toString().trim()
                if (courseId.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter a group ID", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                val groupName = groupNameInput.text.toString().trim()
                if (groupName.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter a group name", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                val groupDescription = groupDescriptionInput.text.toString().trim()
                
                // Get selected participants - make sure the current user is included
                val selectedParticipants = participantAdapter.getSelectedParticipants()
                
                if (selectedParticipants.isEmpty()) {
                    Toast.makeText(requireContext(), "Please select at least one participant", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // Check if group ID exists before creating group
                checkCourseIdAndCreateGroup(courseId, groupName, groupDescription, selectedParticipants)
            }
            
            // Initialize the participants count immediately
            updateSelectedParticipantsCount(1) // Start with 1 for the current user
            
            return view
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreateView", e)
            Toast.makeText(requireContext(), "Error initializing view: ${e.message}", Toast.LENGTH_LONG).show()
            throw e
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Load participants immediately when the view is created
        lifecycleScope.launch {
            loadAllParticipants()
        }
    }
    
    private fun setupEdgeToEdge(view: View) {
        try {
            // Set up the status bar spacer based on system window insets
            val statusBarSpacer = view.findViewById<View>(R.id.status_bar_spacer)
            
            // Get the toolbar container
            val toolbarContainer = view.findViewById<View>(R.id.toolbar_container)
            
            // Ensure toolbar is visible and properly styled with the black color
            toolbarContainer.apply {
                visibility = View.VISIBLE
                setBackgroundColor(android.graphics.Color.parseColor("#0D0302"))
            }
            
            // Ensure back button is visible and white
            view.findViewById<View>(R.id.back_button).apply {
                visibility = View.VISIBLE
            }
            
            // Style title
            view.findViewById<TextView>(R.id.toolbar_title).apply {
                setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            }
            
            // Apply window insets for status bar
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
                val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                
                // Adjust the height of the status bar spacer
                statusBarSpacer.layoutParams.height = statusBarHeight
                statusBarSpacer.requestLayout()
                
                Log.d(TAG, "Applied status bar insets: height=$statusBarHeight")
                insets
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up edge-to-edge display", e)
        }
    }
    
    private fun updateSelectedParticipantsCount(count: Int) {
        selectedParticipantCount = count
        val selectedParticipantsTitle = view?.findViewById<TextView>(R.id.selected_participants_title)
        selectedParticipantsTitle?.text = "$count selected"
        
        // Update button states based on selection
        val selectAllButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.select_all_button)
        val clearSelectionButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.clear_selection_button)
        
        // Enable/disable buttons based on selection state
        selectAllButton?.isEnabled = count < participantAdapter.getOriginalParticipantCount()
        clearSelectionButton?.isEnabled = count > 1 // More than just the current user
        
        // Update button text based on state
        if (count == participantAdapter.getOriginalParticipantCount()) {
            selectAllButton?.text = "All Selected"
        } else {
            selectAllButton?.text = "Select All"
        }
        
        if (count <= 1) {
            clearSelectionButton?.text = "Clear"
        } else {
            clearSelectionButton?.text = "Clear ($count)"
        }
    }
    
    private suspend fun loadAllParticipants() {
        try {
            Log.d(TAG, "Attempting to load all participants...")
            val allUsers = userRepository.getAllUsers().await()
            Log.d(TAG, "Fetched ${allUsers.size} users from repository.")
            
            // Update the adapter with all fetched users. The adapter's constructor already handles
            // initial selection and locking of the current user.
            participantAdapter.updateParticipants(allUsers)
            // Request a layout pass to ensure RecyclerView re-draws its items
            recyclerView.requestLayout()
            Log.d(TAG, "Participant adapter updated. Item count: ${participantAdapter.itemCount}")
            
            Log.d(TAG, "Loaded ${participantAdapter.itemCount} participants. Current user: $currentUserId")
            updateSelectedParticipantsCount(participantAdapter.getSelectedCount())
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading all participants", e)
            Toast.makeText(requireContext(), "Failed to load participants: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun filterParticipants(query: String) {
        participantAdapter.filterParticipants(query)
        updateSelectedParticipantsCount(participantAdapter.getSelectedCount())
    }

    private fun selectAllParticipants() {
        participantAdapter.selectAllParticipants()
        updateSelectedParticipantsCount(participantAdapter.getSelectedCount())
    }

    private fun clearAllSelections() {
        participantAdapter.clearAllSelections()
        updateSelectedParticipantsCount(participantAdapter.getSelectedCount())
    }
    
    private fun checkCourseIdAndCreateGroup(courseId: String, groupName: String, groupDescription: String, participants: List<String>) {
        db.collection("groups").document(courseId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Course ID already exists, show error dialog
                    AlertDialog.Builder(requireContext())
                        .setTitle("Group Already Exists")
                        .setMessage("A group with ID '$courseId' already exists. Please use a different ID.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    // Course ID is unique, proceed with group creation
                    createGroup(courseId, groupName, groupDescription, participants)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking group ID", e)
                Toast.makeText(
                    requireContext(),
                    "Error checking group ID: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
    
    private fun createGroup(courseId: String, groupName: String, groupDescription: String, participants: List<String>) {
        try {
            // Create and style a confirmation dialog
            val dialogBuilder = AlertDialog.Builder(requireContext())
            dialogBuilder.setTitle("Create Group")
                .setMessage("Are you sure you want to create group '$groupName' with ${participants.size} participants?")
                .setCancelable(false)
                .setPositiveButton("Yes") { _, _ ->
                    // Initialize messaging permissions - all participants can message by default
                    val messagingPermissions = mutableMapOf<String, Boolean>()
                    participants.forEach { userId ->
                        messagingPermissions[userId] = true
                    }
                    
                    // Create group object with the current user as an admin
                    val group = Group(
                        id = courseId,  // Use courseId as the document ID
                        name = groupName,
                        description = groupDescription,
                        participants = participants,
                        admins = listOf(currentUserId),  // Current user is admin
                        createdBy = currentUserId,
                        messagingPermissions = messagingPermissions,
                        createdAt = Timestamp.now()
                    )
                    
                    // Show loading toast
                    Toast.makeText(
                        requireContext(),
                        "Creating group...",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Save to Firestore with specific document ID
                    db.collection("groups").document(courseId)
                        .set(group)
                        .addOnSuccessListener {
                            Toast.makeText(
                                requireContext(),
                                "Group '$groupName' created successfully",
                                Toast.LENGTH_LONG
                            ).show()
                            
                            // Go back to previous screen
                            try {
                                findNavController().navigateUp()
                            } catch (e: Exception) {
                                requireActivity().onBackPressed()
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                requireContext(),
                                "Failed to create group: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
                .setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                }
            
            // Create and show the dialog
            val alertDialog = dialogBuilder.create()
            alertDialog.show()
            
            // Style the dialog buttons to match the app's color scheme
            val positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton?.apply {
                setTextColor(resources.getColor(android.R.color.white))
                setBackgroundColor(resources.getColor(android.R.color.holo_blue_dark))
            }
            
            val negativeButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            negativeButton?.apply {
                setTextColor(resources.getColor(android.R.color.darker_gray))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating group", e)
            Toast.makeText(requireContext(), "Error creating group: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
} 