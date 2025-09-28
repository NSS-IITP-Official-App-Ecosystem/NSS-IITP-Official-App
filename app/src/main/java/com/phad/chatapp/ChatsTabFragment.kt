package com.phad.chatapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.phad.chatapp.adapters.AdminAdapter
import com.phad.chatapp.models.Admin
import com.phad.chatapp.repositories.FirestoreRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatsTabFragment : Fragment() {
    private val TAG = "ChatsTabFragment"
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyTextView: TextView
    private lateinit var progressBar: ProgressBar
    private val repository = FirestoreRepository()
    private lateinit var adminAdapter: AdminAdapter
    private var showOnlyWithMessages = false // Add flag to control filtering users with messages
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Removed setHasOptionsMenu(true) to fix duplicate menu issue
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chats_tab, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        recyclerView = view.findViewById(R.id.recycler_chats)
        emptyTextView = view.findViewById(R.id.text_no_chats)
        progressBar = view.findViewById(R.id.progress_bar)
        
        setupRecyclerView()
        loadAdminList()
    }
    
    private fun setupRecyclerView() {
        adminAdapter = AdminAdapter(mutableListOf()) { admin ->
            // Handle admin click
            openChatWithAdmin(admin)
        }
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adminAdapter
    }
    
    private fun openChatWithAdmin(admin: Admin) {
        // Get the current user
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "You need to be logged in", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Try to get roll number from session first
        val sessionManager = com.phad.chatapp.utils.SessionManager(requireContext())
        var currentUserRollNumber = sessionManager.fetchUserId()
        var currentUserName = currentUser.displayName?.split("|")?.get(0)?.trim() ?: "User"
        
        if (currentUserRollNumber.isEmpty()) {
            // Try to extract roll number from display name
            val displayName = currentUser.displayName
            if (!displayName.isNullOrEmpty() && displayName.contains("|")) {
                // Format is "UserType|RollNumber"
                val parts = displayName.split("|")
                if (parts.size >= 2) {
                    currentUserRollNumber = parts[1].trim()
                    currentUserName = parts[0].trim()
                    Log.d(TAG, "Extracted roll number from display name: $currentUserRollNumber")
                }
            }
            
            // If still empty, try email
            if (currentUserRollNumber.isEmpty() && !currentUser.email.isNullOrEmpty()) {
                // Assuming email format like "rollnumber@domain.com"
                val email = currentUser.email!!
                currentUserRollNumber = email.substring(0, email.indexOf('@'))
                Log.d(TAG, "Extracted potential roll number from email: $currentUserRollNumber")
            }
        }
        
        if (currentUserRollNumber.isEmpty()) {
            // As a last resort, query Firestore using UID
            FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("uid", currentUser.uid)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val userDoc = documents.documents[0]
                        currentUserRollNumber = userDoc.getString("rollNumber") ?: ""
                        currentUserName = userDoc.getString("name") ?: "User"
                        
                        if (currentUserRollNumber.isEmpty()) {
                            Toast.makeText(requireContext(), "Could not determine your user ID", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }
                        
                        // Now we have the roll number, start the chat
                        startChatActivity(currentUserRollNumber, currentUserName, admin)
                    } else {
                        Toast.makeText(requireContext(), "Could not retrieve your user information", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error fetching current user's roll number", e)
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // We have the roll number, start the chat directly
            startChatActivity(currentUserRollNumber, currentUserName, admin)
        }
    }
    
    private fun startChatActivity(currentUserRollNumber: String, currentUserName: String, admin: Admin) {
        Log.d(TAG, "Opening chat with admin. Current user roll: $currentUserRollNumber, Admin roll: ${admin.rollNumber}")
        
        // Start chat activity with the roll numbers
        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("currentUserRollNumber", currentUserRollNumber)
            putExtra("currentUserName", currentUserName)
            putExtra("otherUserRollNumber", admin.rollNumber)
            putExtra("otherUserName", admin.name)
        }
        startActivity(intent)
    }
    
    private fun loadAdminList() {
        showLoading()
        
        // First, try to get the user type from SessionManager for quick access
        val sessionManager = com.phad.chatapp.utils.SessionManager(requireContext())
        val sessionUserType = sessionManager.fetchUserType()
        
        // Get the current user
        val currentUser = FirebaseAuth.getInstance().currentUser
        
        if (currentUser == null) {
            handleLoadError(Exception("You are not logged in"))
            return
        }
        
        // Try to get roll number from display name or from session
        var currentUserRollNumber = sessionManager.fetchUserId()
        
        if (currentUserRollNumber.isEmpty()) {
            // Try to extract roll number from display name
            val displayName = currentUser.displayName
            if (!displayName.isNullOrEmpty() && displayName.contains("|")) {
                // Format is "UserType|RollNumber"
                val parts = displayName.split("|")
                if (parts.size >= 2) {
                    currentUserRollNumber = parts[1].trim()
                    Log.d(TAG, "Extracted roll number from display name: $currentUserRollNumber")
                }
            }
            
            // If still empty, try email
            if (currentUserRollNumber.isEmpty() && !currentUser.email.isNullOrEmpty()) {
                // Assuming email format like "rollnumber@domain.com"
                val email = currentUser.email!!
                currentUserRollNumber = email.substring(0, email.indexOf('@'))
                Log.d(TAG, "Extracted potential roll number from email: $currentUserRollNumber")
            }
        }
        
        if (currentUserRollNumber.isEmpty()) {
            Toast.makeText(requireContext(), "Could not determine your roll number", Toast.LENGTH_SHORT).show()
            showEmptyState("Could not retrieve your user information")
            return
        }
        
        Log.d(TAG, "Using roll number: $currentUserRollNumber")
        
        // If user type is already in session, use it directly
        if (!sessionUserType.isNullOrEmpty()) {
            Log.d(TAG, "Retrieved user type from SessionManager: $sessionUserType")
            
            // If we already know the user type, load the appropriate user list
            when (sessionUserType) {
                "Admin" -> loadAllUsersForAdminWithImportantFlags(currentUserRollNumber)
                else -> loadAdminUsersWithImportantFlags(currentUserRollNumber)
            }
            return
        }
        
        // Fetch the user's document from Firestore using roll number (not UID)
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("rollNumber", currentUserRollNumber)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val userDoc = documents.documents[0]
                    val userType = userDoc.getString("userType") ?: ""
                    val userRollNumber = userDoc.getString("rollNumber") ?: currentUserRollNumber
                    val userYear = userDoc.getLong("year")?.toInt() ?: 0
                    
                    if (userType.isNotEmpty()) {
                        // Save to session for future use
                        sessionManager.createLoginSession(userType, userRollNumber, userYear)
                        
                        // Proceed with loading based on user type
                        when (userType) {
                            "Admin" -> loadAllUsersForAdminWithImportantFlags(currentUserRollNumber)
                            else -> loadAdminUsersWithImportantFlags(currentUserRollNumber)
                        }
                    } else {
                        Log.e(TAG, "User document found but userType is empty for roll number: $currentUserRollNumber")
                        handleLoadError(Exception("Could not determine your user type"))
                    }
                } else {
                    Log.e(TAG, "No user document found for roll number: $currentUserRollNumber")
                    showEmptyState("Could not find your user information")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching user by roll number: $currentUserRollNumber", e)
                handleLoadError(e)
            }
    }
    
    /**
     * Load all users for Admin with enhanced data
     */
    private fun loadAllUsersForAdminWithImportantFlags(currentUserRoll: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Show toast for status
                activity?.runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "Admin mode: Showing ALL users",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                val userList = repository.getAllUsersForAdmin1WithTimestamps()
                
                if (!isAdded) return@launch
                
                // Now enhance the user list with important message status only
                val enhancedList = userList.map { admin ->
                    val hasImportant = checkForImportantMessages(currentUserRoll, admin.rollNumber)
                    
                    admin.copy(
                        unreadCount = 0, // Not using unread counts anymore
                        hasImportantMessages = hasImportant
                    )
                }
                
                // Sort the list: important messages first, then by name
                val sortedAdmins = enhancedList.sortedWith(
                    compareByDescending<Admin> { it.hasImportantMessages }
                    .thenBy { it.name }
                )
                
                if (sortedAdmins.isNotEmpty()) {
                    adminAdapter.updateData(sortedAdmins)
                    showContent()
                } else {
                    showEmptyState("No users found.\n\nPlease ensure users are added to the database.")
                }
            } catch (e: Exception) {
                handleLoadError(e)
            }
        }
    }
    
    /**
     * Load Admin users for students with enhanced data
     */
    private fun loadAdminUsersWithImportantFlags(currentUserRoll: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Show toast for status
                activity?.runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "Showing Admin users with importance flags",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                val userList = repository.getAdmin1UsersOnly()
                
                if (!isAdded) return@launch
                
                // Enhance with important flags only
                val enhancedList = userList.map { admin ->
                    val hasImportant = checkForImportantMessages(currentUserRoll, admin.rollNumber)
                    
                    admin.copy(
                        unreadCount = 0, // Not using unread counts anymore
                        hasImportantMessages = hasImportant
                    )
                }
                
                // Sort the list: important messages first, then by name
                val sortedAdmins = enhancedList.sortedWith(
                    compareByDescending<Admin> { it.hasImportantMessages }
                    .thenBy { it.name }
                )
                
                if (sortedAdmins.isNotEmpty()) {
                    adminAdapter.updateData(sortedAdmins)
                    showContent()
                } else {
                    showEmptyState("No Admin1 users found.\n\nPlease ensure Admin1 users are added to the database.")
                }
            } catch (e: Exception) {
                handleLoadError(e)
            }
        }
    }
    
    /**
     * Check if there are any important messages from a specific user
     */
    private suspend fun checkForImportantMessages(currentUserRoll: String, otherUserRoll: String): Boolean {
        return try {
            // Query for unread important messages
            val querySnapshot = FirebaseFirestore.getInstance()
                .collection("user_conversations")
                .document(currentUserRoll)
                .collection(otherUserRoll)
                .whereEqualTo("sender", otherUserRoll)
                .whereEqualTo("read", false)
                .whereEqualTo("isImportant", true)
                .limit(1) // We only need to know if there's at least one
                .get()
                .await()
                
            !querySnapshot.isEmpty
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for important messages: ${e.message}")
            false
        }
    }
    
    private fun showLoading() {
        recyclerView.visibility = View.GONE
        emptyTextView.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
    }
    
    private fun showContent() {
        recyclerView.visibility = View.VISIBLE
        emptyTextView.visibility = View.GONE
        progressBar.visibility = View.GONE
    }
    
    private fun showEmptyState(message: String) {
        recyclerView.visibility = View.GONE
        emptyTextView.visibility = View.VISIBLE
        emptyTextView.text = message
        progressBar.visibility = View.GONE
    }

    private fun forceLoadAsAdmin1() {
        showLoading()
        
        // Get current user's roll number
        val sessionManager = com.phad.chatapp.utils.SessionManager(requireContext())
        val currentUserRoll = sessionManager.fetchUserId()
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d(TAG, "Forcing Admin1 mode - fetching ALL users")
                
                // Force Admin1 mode toast
                activity?.runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "FORCED Admin1 mode: Showing ALL users",
                        Toast.LENGTH_LONG
                    ).show()
                }
                
                // Get all users
                val userList = repository.getAllUsersForAdmin1WithTimestamps()
                
                Log.d(TAG, "Retrieved ${userList.size} users for display in forced Admin1 mode")
                Log.d(TAG, "User types in list: ${userList.map { it.userType }.distinct()}")
                
                // Check if fragment is still attached before updating UI
                if (!isAdded) return@launch
                
                // Enhance with important flags only
                val enhancedList = userList.map { admin ->
                    val hasImportant = checkForImportantMessages(currentUserRoll, admin.rollNumber)
                    
                    admin.copy(
                        unreadCount = 0, // Not using unread counts anymore
                        hasImportantMessages = hasImportant
                    )
                }
                
                // Sort: important first, then by name
                val sortedAdmins = enhancedList.sortedWith(
                    compareByDescending<Admin> { it.hasImportantMessages }
                    .thenBy { it.name }
                )
                
                // Skip admins with no messages if needed
                val finalList = if (showOnlyWithMessages) {
                    sortedAdmins.filter { it.hasImportantMessages }
                } else {
                    sortedAdmins
                }
                
                if (finalList.isNotEmpty()) {
                    adminAdapter.updateData(finalList)
                    showContent()
                } else {
                    showEmptyState("No users found with messages.\n\nTry refreshing or changing filters.")
                }
            } catch (e: Exception) {
                handleLoadError(e)
            }
        }
    }

    private fun handleLoadError(e: Exception) {
        if (e is CancellationException) {
            Log.d(TAG, "Coroutine cancelled")
            return
        }
        
        if (!isAdded) return
                
                Log.e(TAG, "Error loading user data", e)
                
                val errorMessage = when (e) {
                    is FirebaseFirestoreException -> {
                        when (e.code) {
                            FirebaseFirestoreException.Code.PERMISSION_DENIED -> 
                                "Cannot access Firestore database.\nCheck your security rules and ensure you have permission to read the users collection."
                            FirebaseFirestoreException.Code.UNAVAILABLE -> 
                                "Cannot connect to Firestore.\nPlease check your internet connection and try again."
                            FirebaseFirestoreException.Code.UNAUTHENTICATED -> 
                                "Authentication required.\nPlease sign in again to access the chat."
                            FirebaseFirestoreException.Code.NOT_FOUND ->
                                "Users collection not found.\nPlease ensure the Firestore database is properly set up."
                            else -> "Database error: ${e.message}\nPlease try again later."
                        }
                    }
                    is FirebaseException -> "Firebase error: ${e.message}\nPlease try again later."
                    else -> "Unexpected error: ${e.message}\nPlease try again later."
                }
                
                showEmptyState(errorMessage)
                
                // Safely show toast only if context is available
                context?.let { ctx ->
            Toast.makeText(ctx, "Failed to load users: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Public method to force reload as Admin1 - can be called from parent activity/fragment
     */
    fun reloadAsAdmin1() {
        Toast.makeText(requireContext(), "Forcing Admin1 mode", Toast.LENGTH_SHORT).show()
        forceLoadAsAdmin1()
    }
    
    /**
     * Public method to reload the user list - can be called from parent activity/fragment
     */
    fun reloadUserList() {
        Toast.makeText(requireContext(), "Reloading users", Toast.LENGTH_SHORT).show()
        loadAdminList()
    }
} 