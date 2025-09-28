package com.phad.chatapp

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.adapters.ParticipantAdapter
import com.phad.chatapp.databinding.ActivityManageParticipantsBinding
import com.phad.chatapp.models.Group
import com.phad.chatapp.models.User
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.services.SubjectAssignmentService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ManageParticipantsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityManageParticipantsBinding
    private lateinit var participantAdapter: ParticipantAdapter
    private lateinit var sessionManager: SessionManager
    
    private var groupId: String = ""
    private var groupName: String = ""
    private var allUsers = mutableListOf<User>()
    private var selectedParticipants = mutableSetOf<String>()
    private var lockedParticipants = mutableSetOf<String>()
    private var currentGroup: Group? = null
    private var originalParticipants = listOf<String>()
    private var currentUserId: String = ""
    
    companion object {
        private const val TAG = "ManageParticipants"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityManageParticipantsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up the status bar to be transparent
        setupStatusBar()
        
        // Get group data from intent
        groupId = intent.getStringExtra("GROUP_ID") ?: ""
        groupName = intent.getStringExtra("GROUP_NAME") ?: "Manage Group"
        
        if (groupId.isEmpty()) {
            Toast.makeText(this, "Invalid group", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Initialize utilities
        sessionManager = SessionManager(this)
        currentUserId = sessionManager.fetchUserId()
        
        setupToolbar()
        setupRecyclerView()
        loadGroupDetails()
    }
    
    private fun setupStatusBar() {
        // Set status bar to transparent so the toolbar appears to extend beneath it
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        
        // Make status bar icons dark or light depending on your needs
        val decorView = window.decorView
        val flags = decorView.systemUiVisibility
        decorView.systemUiVisibility = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }
    
    private fun setupToolbar() {
        binding.toolbarTitle.text = "Manage $groupName"
        
        // Back button
        binding.backButton.setOnClickListener {
            checkForChangesAndExit()
        }
        
        // Save button
        binding.saveButton.setOnClickListener {
            confirmAndSaveChanges()
        }

        // Set up search button
        binding.searchButton.setOnClickListener {
            toggleSearch(true)
        }
    }
    
    private fun toggleSearch(show: Boolean) {
        if (show) {
            binding.searchContainer.visibility = View.VISIBLE
            binding.searchEditText.requestFocus()
            // Initialize the clear button visibility based on current text
            binding.clearSearchButton.visibility = if (binding.searchEditText.text.isNotEmpty()) View.VISIBLE else View.GONE
            // Show keyboard
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        } else {
            binding.searchContainer.visibility = View.GONE
            // Clear search
            binding.searchEditText.setText("")
            // Reset to show all users
            participantAdapter.updateParticipants(allUsers)
            // Hide keyboard
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
        }
    }
    
    private fun setupRecyclerView() {
        // Set up participants adapter with locked participants and selection callback
        participantAdapter = ParticipantAdapter(
            selectedParticipants = selectedParticipants,
            lockedParticipants = lockedParticipants
        ) { count ->
            updateParticipantCount(count)
        }
        
        binding.allUsersRecycler.apply {
            layoutManager = LinearLayoutManager(this@ManageParticipantsActivity)
            adapter = participantAdapter
        }
        
        // Setup search functionality with the new search layout
        setupSearch()
    }
    
    private fun setupSearch() {
        // Set up close button for search
        binding.clearSearchButton.setOnClickListener { view ->
            Log.d(TAG, "Clear search button clicked")
            // Close the entire search box instead of just clearing text
            toggleSearch(false)
        }
        
        // Setup text change listener
        binding.searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                try {
                    filterUsers(s?.toString() ?: "")
                    
                    // Show clear button only when there's text
                    binding.clearSearchButton.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
                } catch (e: Exception) {
                    Log.e(TAG, "Error filtering users: ${e.message}", e)
                }
            }
            
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        // Handle search action
        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchEditText.text.toString()
                filterUsers(query)
                
                // Hide keyboard after search
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
                
                return@setOnEditorActionListener true
            }
            false
        }
    }
    
    private fun loadGroupDetails() {
        binding.progressBar.visibility = View.VISIBLE
        
        FirebaseFirestore.getInstance()
            .collection("groups")
            .document(groupId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    currentGroup = document.toObject(Group::class.java)?.apply {
                        id = document.id
                    }
                    
                    // Save original participants to check for changes later
                    originalParticipants = currentGroup?.participants ?: emptyList()
                    
                    // Initialize the selected participants with current participants
                    selectedParticipants.addAll(originalParticipants)
                    
                    // Add the creator and current user to locked participants 
                    // (cannot be removed from group)
                    currentGroup?.createdBy?.let { lockedParticipants.add(it) }
                    lockedParticipants.add(currentUserId)
                    
                    // Load all users
                    loadUsers()
                } else {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Group not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Log.e(TAG, "Error loading group: ${e.message}", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }
    
    private fun loadUsers() {
        // Load all users from Firestore
        FirebaseFirestore.getInstance()
            .collection("users")
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE
                
                if (!documents.isEmpty) {
                    allUsers.clear()
                    
                    // Convert documents to User objects with error handling
                    for (doc in documents) {
                        try {
                            // Get values from Firestore document
                            val userData = doc.data
                            
                            // Create User object with safe handling
                            val user = User(
                                id = doc.id,
                                name = userData["name"] as? String ?: "",
                                email = userData["email"] as? String ?: "",
                                rollNumber = userData["roll_number"] as? String ?: doc.id,
                                userType = userData["user_type"] as? String ?: "Student",
                                description = userData["description"] as? String ?: "",
                                contactNumber = userData["contact_number"] as? String ?: "",
                                profileImageUrl = userData["profile_image_url"] as? String
                            ).apply {
                                // Safely set year value
                                setYear(userData["year"])
                            }
                            
                            allUsers.add(user)
                            Log.d(TAG, "Loaded user: ${user.name} (${user.rollNumber})")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing user document ${doc.id}", e)
                        }
                    }
                    
                    // Sort users: participants first, then alphabetically
                    sortAndUpdateUsersList()
                    
                } else {
                    Log.d(TAG, "No users found")
                    binding.allUsersLabel.text = "No users found"
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Log.e(TAG, "Error loading users: ${e.message}", e)
                Toast.makeText(this, "Failed to load users: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun sortAndUpdateUsersList() {
        // Sort users: participants first, then alphabetically
        allUsers.sortWith(compareBy<User> { !selectedParticipants.contains(it.rollNumber) }
            .thenBy { it.name.lowercase() })
        
        // Update adapter with sorted users
        participantAdapter.updateParticipants(allUsers)
        updateParticipantCount(selectedParticipants.size)
    }
    
    private fun updateParticipantCount(count: Int) {
        binding.allUsersLabel.text = "All Users (${count} selected)"
    }
    
    private fun filterUsers(query: String) {
        try {
        if (query.isEmpty()) {
            // If query is empty, show all users
            participantAdapter.updateParticipants(allUsers)
            return
        }
        
        // Filter users by name or roll number
        val filteredUsers = allUsers.filter {
            it.name.contains(query, ignoreCase = true) || 
            it.rollNumber.contains(query, ignoreCase = true)
        }
        
        participantAdapter.updateParticipants(filteredUsers)
        } catch (e: Exception) {
            Log.e(TAG, "Error filtering users: ${e.message}", e)
            // If there's an error, show all users
            participantAdapter.updateParticipants(allUsers)
        }
    }
    
    private fun confirmAndSaveChanges() {
        val updatedParticipants = participantAdapter.getSelectedParticipants()
        
        // Check if there are any changes
        if (updatedParticipants.containsAll(originalParticipants) && 
            originalParticipants.containsAll(updatedParticipants)) {
            Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Show confirmation dialog
        AlertDialog.Builder(this)
            .setTitle("Confirm Changes")
            .setMessage("Are you sure you want to update the group participants?")
            .setPositiveButton("Update") { _, _ ->
                saveParticipantsChanges(updatedParticipants)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun saveParticipantsChanges(updatedParticipantsList: List<String>) {
        binding.progressBar.visibility = View.VISIBLE

        // Make sure required participants are included
        val finalParticipants = updatedParticipantsList.toMutableList()
        for (lockedId in lockedParticipants) {
            if (!finalParticipants.contains(lockedId)) {
                finalParticipants.add(lockedId)
            }
        }

        Log.d(TAG, "Saving participants: ${finalParticipants.joinToString()}")

        // Use SubjectAssignmentService for subject groups
        if (currentGroup?.subject == true) {
            val service = com.phad.chatapp.services.SubjectAssignmentService()
            val original = originalParticipants.toSet()
            val updated = finalParticipants.toSet()
            val toAdd = updated - original
            val toRemove = original - updated
            val groupId = currentGroup?.id ?: return
            lifecycleScope.launch {
                try {
                    for (rollNo in toAdd) {
                        service.addParticipantToGroup(groupId, rollNo)
                    }
                    for (rollNo in toRemove) {
                        service.removeParticipantFromGroup(groupId, rollNo)
                    }
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ManageParticipantsActivity, "Participants updated successfully", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: Exception) {
                    binding.progressBar.visibility = View.GONE
                    Log.e(TAG, "Error updating participants: ${e.message}", e)
                    Toast.makeText(this@ManageParticipantsActivity, "Failed to update participants: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // Regular group: update participants directly
            FirebaseFirestore.getInstance()
                .collection("groups")
                .document(groupId)
                .update("participants", finalParticipants)
                .addOnSuccessListener {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Participants updated successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    Log.e(TAG, "Error updating participants: ${e.message}", e)
                    Toast.makeText(this, "Failed to update participants: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
    
    private fun checkForChangesAndExit() {
        val updatedParticipants = participantAdapter.getSelectedParticipants()
        
        // Check if there are any changes
        if (updatedParticipants.containsAll(originalParticipants) && 
            originalParticipants.containsAll(updatedParticipants)) {
            finish()
            return
        }
        
        // Show confirmation dialog
        AlertDialog.Builder(this)
            .setTitle("Discard Changes")
            .setMessage("You have unsaved changes. Do you want to discard them?")
            .setPositiveButton("Discard") { _, _ -> finish() }
            .setNegativeButton("Keep Editing", null)
            .show()
    }
    
    override fun onBackPressed() {
        // Check if search is active
        if (binding.searchContainer.visibility == View.VISIBLE) {
            // Close search first
            toggleSearch(false)
        } else {
            // Check for changes before exiting
            checkForChangesAndExit()
        }
    }
} 