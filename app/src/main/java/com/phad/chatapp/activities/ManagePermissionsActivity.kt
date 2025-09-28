package com.phad.chatapp.activities

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.R
import com.phad.chatapp.adapters.PermissionsAdapter
import com.phad.chatapp.models.Group
import com.phad.chatapp.utils.SessionManager

class ManagePermissionsActivity : AppCompatActivity() {
    
    private val TAG = "ManagePermissionsActivity"
    
    private lateinit var db: FirebaseFirestore
    private lateinit var sessionManager: SessionManager
    private lateinit var permissionsAdapter: PermissionsAdapter
    
    private lateinit var backButton: ImageView
    private lateinit var titleText: TextView
    private lateinit var searchButton: ImageView
    private lateinit var searchContainer: CardView
    private lateinit var searchEditText: EditText
    private lateinit var clearSearchButton: ImageView
    private lateinit var participantsRecyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var saveButton: Button
    
    private var groupId: String = ""
    private var groupName: String = ""
    private var currentUserId: String = ""
    private var adminDeselectedSelf = false
    private var permissionsChanged = false
    private var isSearchActive = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_permissions)
        
        // Set status bar color to match the toolbar
        window.statusBarColor = resources.getColor(android.R.color.black, theme)
        
        // Make status bar icons light for better visibility on dark background
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }
        
        // Initialize Firestore and SessionManager
        db = FirebaseFirestore.getInstance()
        sessionManager = SessionManager(this)
        
        // Get current user
        currentUserId = sessionManager.fetchUserId()
        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "You must be logged in to manage permissions", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Check if user is Admin
        val userType = sessionManager.fetchUserType()
        if (userType != "Admin" && userType != "Admin1" && userType != "Admin2") {
            Toast.makeText(this, "Only Admin users can manage permissions", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Get group info from intent
        groupId = intent.getStringExtra("GROUP_ID") ?: ""
        groupName = intent.getStringExtra("GROUP_NAME") ?: ""
        
        if (groupId.isEmpty()) {
            Toast.makeText(this, "Group info not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Initialize views
        initViews()
        
        // Load group details
        loadGroupDetails()
    }
    
    private fun initViews() {
        backButton = findViewById(R.id.back_button)
        titleText = findViewById(R.id.title_text)
        searchButton = findViewById(R.id.search_button)
        searchContainer = findViewById(R.id.search_container)
        searchEditText = findViewById(R.id.search_edit_text)
        clearSearchButton = findViewById(R.id.clear_search_button)
        participantsRecyclerView = findViewById(R.id.participants_recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        saveButton = findViewById(R.id.save_button)
        
        // Set title with group name
        titleText.text = "Permissions: $groupName"
        
        // Set up search functionality
        setupSearch()
        
        // Set back button listener
        backButton.setOnClickListener {
            if (isSearchActive) {
                // If search is active, close search first
                toggleSearch(false)
            } else if (permissionsChanged) {
                showUnsavedChangesDialog()
            } else {
                finish()
            }
        }
        
        // Set save button listener
        saveButton.setOnClickListener {
            showSaveConfirmationDialog()
        }
    }
    
    private fun setupSearch() {
        // Search button click listener
        searchButton.setOnClickListener {
            toggleSearch(true)
        }
        
        // Clear search button click listener
        clearSearchButton.setOnClickListener {
            Log.d(TAG, "Clear search button clicked")
            // Close the entire search box instead of just clearing text
            toggleSearch(false)
        }
        
        // Set up search edit text
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Show clear button only when there's text
                clearSearchButton.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {
                try {
                    if (::permissionsAdapter.isInitialized && s != null) {
                        // Avoid UI freezes by delaying search if typing quickly
                        searchEditText.removeCallbacks(searchRunnable)
                        searchEditText.postDelayed(searchRunnable, 300)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in text changed listener: ${e.message}", e)
                    // Reset search on error
                    if (::permissionsAdapter.isInitialized) {
                        searchEditText.removeCallbacks(searchRunnable)
                        permissionsAdapter.filter("")
                    }
                }
            }
        })
        
        // Set up search action on keyboard
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                try {
                    if (::permissionsAdapter.isInitialized) {
                        val query = searchEditText.text?.toString() ?: ""
                        permissionsAdapter.filter(query)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in search action: ${e.message}", e)
                    // Reset filter on error
                    if (::permissionsAdapter.isInitialized) {
                        permissionsAdapter.filter("")
                    }
                }
                return@setOnEditorActionListener true
            }
            false
        }
    }
    
    // Runnable to perform the actual search after a short delay
    private val searchRunnable = Runnable {
        try {
            val query = searchEditText.text?.toString() ?: ""
            if (::permissionsAdapter.isInitialized) {
                permissionsAdapter.filter(query)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in search runnable: ${e.message}", e)
            // Reset filter on error
            if (::permissionsAdapter.isInitialized) {
                permissionsAdapter.filter("")
            }
        }
    }
    
    private fun toggleSearch(show: Boolean) {
        isSearchActive = show
        if (show) {
            searchContainer.visibility = View.VISIBLE
            searchEditText.requestFocus()
            // Initialize clear button visibility
            clearSearchButton.visibility = if (searchEditText.text?.isNotEmpty() == true) View.VISIBLE else View.GONE
            // Show keyboard
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        } else {
            searchContainer.visibility = View.GONE
            // Clear search
            searchEditText.setText("")
            if (::permissionsAdapter.isInitialized) {
                permissionsAdapter.filter("")
            }
            // Hide keyboard
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
        }
    }
    
    override fun onBackPressed() {
        if (isSearchActive) {
            // If search is active, close search first
            toggleSearch(false)
        } else if (permissionsChanged) {
            showUnsavedChangesDialog()
        } else {
            super.onBackPressed()
        }
    }
    
    private fun loadGroupDetails() {
        showLoading(true)
        
        db.collection("groups").document(groupId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val group = document.toObject(Group::class.java)?.apply {
                        id = document.id
                    }
                    
                    if (group != null) {
                        setupPermissionsList(group)
                        
                        // Ensure all participants have permissions set
                        group.initializeMessagingPermissions()
                    } else {
                        showError("Failed to load group data")
                    }
                } else {
                    showError("Group not found")
                }
                
                showLoading(false)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading group: ${e.message}", e)
                showError("Error: ${e.message}")
                showLoading(false)
            }
    }
    
    private fun setupPermissionsList(group: Group) {
        // Create adapter
        permissionsAdapter = PermissionsAdapter(
            group = group,
            currentUserId = currentUserId,
            onPermissionChanged = { userId, hasPermission ->
                // Mark that permissions have changed
                permissionsChanged = true
                
                // Check if the user is toggling their own permission
                if (userId == currentUserId && !hasPermission) {
                    // Admin toggled themselves off - special handling
                    adminDeselectedSelf = true
                    Toast.makeText(this, "Warning: You will not be able to edit permissions until you restore your own permission.", Toast.LENGTH_LONG).show()
                } else if (userId == currentUserId && hasPermission) {
                    // Admin restored their own permission
                    adminDeselectedSelf = false
                } else if (group.isUserAdmin(userId) && !hasPermission) {
                    // Another admin's permission was changed
                    Toast.makeText(this, "This admin's messaging permission has been disabled.", Toast.LENGTH_SHORT).show()
                }
            }
        )
        
        // Set adapter
        participantsRecyclerView.adapter = permissionsAdapter
        
        // Fetch user names for all participants
        for (userId in group.participants) {
            fetchUserName(userId)
        }
    }
    
    private fun fetchUserName(userId: String) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("name") ?: userId
                    permissionsAdapter.updateUserName(userId, name)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching user name: ${e.message}", e)
            }
    }
    
    private fun showSaveConfirmationDialog() {
        if (!::permissionsAdapter.isInitialized) {
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Save Changes")
            .setMessage("Are you sure you want to save these permission changes?")
            .setPositiveButton("Yes") { dialog, _ ->
                dialog.dismiss()
                savePermissions()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
    
    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes. Do you want to save them before leaving?")
            .setPositiveButton("Save") { dialog, _ ->
                dialog.dismiss()
                savePermissions()
            }
            .setNegativeButton("Discard") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setNeutralButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
    
    private fun savePermissions() {
        if (!::permissionsAdapter.isInitialized) {
            finish()
            return
        }
        
        showLoading(true)
        
        // Get updated permissions
        val permissions = permissionsAdapter.getPermissions()
        
        // Update Firestore
        db.collection("groups").document(groupId)
            .update("messagingPermissions", permissions)
            .addOnSuccessListener {
                Log.d(TAG, "Permissions updated successfully")
                Toast.makeText(this, "Permissions updated", Toast.LENGTH_SHORT).show()
                // Reset the changed flag
                permissionsChanged = false
                finish()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating permissions: ${e.message}", e)
                showError("Failed to update permissions: ${e.message}")
                showLoading(false)
            }
    }
    
    private fun showLoading(isLoading: Boolean) {
        loadingProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
        saveButton.isEnabled = !isLoading
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}