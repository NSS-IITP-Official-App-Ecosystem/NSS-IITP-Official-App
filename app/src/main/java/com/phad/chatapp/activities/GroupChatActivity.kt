package com.phad.chatapp.activities

import android.app.Dialog
import com.phad.chatapp.R
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import com.phad.chatapp.adapters.MessageAdapter
import com.phad.chatapp.databinding.ActivityGroupChatBinding
import com.phad.chatapp.databinding.DialogGroupInfoBinding
import com.phad.chatapp.models.Group
import com.phad.chatapp.models.Message
import com.phad.chatapp.repositories.MessageRepository

import com.phad.chatapp.utils.SessionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlinx.coroutines.launch
import com.phad.chatapp.repositories.GroupRepository
import com.phad.chatapp.utils.Constants
import com.phad.chatapp.utils.AttachmentHandler
import com.phad.chatapp.utils.FileTypeEnum
import com.phad.chatapp.activities.ManagePermissionsActivity
import com.phad.chatapp.utils.NetworkConnectivityObserver
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class GroupChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGroupChatBinding
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageRepository: MessageRepository
    private lateinit var sessionManager: SessionManager
    private var groupId: String = ""
    private var groupName: String = ""
    private var currentGroup: Group? = null
    private var currentUserRole: String = ""
    
    private val senderNames = ConcurrentHashMap<String, String>()
    

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    // Add attachment handler property
    private lateinit var attachmentHandler: AttachmentHandler
    
    // For storing all messages to support search
    private val allMessages = mutableListOf<Message>()
    private var isSearchActive = false
    
    companion object {
        private const val TAG = "GroupChatActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set up status bar to work well with our UI
        setupStatusBar()
        
        binding = ActivityGroupChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize components
        messageRepository = MessageRepository()
        sessionManager = SessionManager(this)
        
        // Initialize Firebase instances
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        
        // Get group data from intent
        groupId = intent.getStringExtra("GROUP_ID") ?: ""
        groupName = intent.getStringExtra("GROUP_NAME") ?: "Group Chat"
        
        if (groupId.isEmpty()) {
            Toast.makeText(this, "Invalid group", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        Log.d(TAG, "Opened group chat: $groupName (ID: $groupId)")
        
        currentUserRole = sessionManager.fetchUserType()
        
        // Initialize attachment handler
        attachmentHandler = AttachmentHandler(this)
        
        // Initialize Drive service
        attachmentHandler.initDriveService()
        
        // Set up file selection listener
        attachmentHandler.setOnFileSelectedListener { fileUri, fileType ->
            // Show loading indicator
            binding.loadingProgress.visibility = View.VISIBLE
            
            // Upload the file
            attachmentHandler.uploadFile(fileUri, fileType) { fileUrl ->
                // Hide loading indicator
                binding.loadingProgress.visibility = View.GONE
                
                if (fileUrl != null) {
                    // Send the media message
                    when (fileType) {
                        FileTypeEnum.IMAGE -> sendMediaMessage(fileUrl, "image")
                        FileTypeEnum.DOCUMENT -> sendMediaMessage(fileUrl, "document")
                    }
                }
            }
        }
        
        // Observe network connectivity
        val networkObserver = NetworkConnectivityObserver(this)
        lifecycleScope.launch {
            networkObserver.networkStatus.collect { isConnected ->
                binding.bannerOffline.visibility = if (isConnected) View.GONE else View.VISIBLE
            }
        }
        
        // Check if user is allowed to access this group
        checkGroupAccess()
    }
    
    private fun setupStatusBar() {
        // Make status bar transparent and ensure it doesn't push content down
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Set status bar icons to light color for better visibility on dark background
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        
        // Set status bar color to black
        window.statusBarColor = resources.getColor(android.R.color.black, theme)
    }
    
    private fun checkGroupAccess() {
        val userId = sessionManager.fetchUserId()
        
        // Show loading spinner while checking
        binding.loadingProgress.visibility = View.VISIBLE
        
        db.collection("groups").document(groupId)
            .get()
            .addOnSuccessListener { document ->
                binding.loadingProgress.visibility = View.GONE
                
                if (document != null && document.exists()) {
                    currentGroup = document.toObject(Group::class.java)?.apply {
                        id = document.id
                    }
                    
                    // Check if user is a participant
                    if (currentGroup?.participants?.contains(userId) == true) {
                        // User is allowed to access the group
                        Log.d(TAG, "User $userId is a participant in group $groupId")
                        setupGroupChat()
                    } else {
                        // User is not a participant
                        Log.d(TAG, "User $userId is not a participant in group $groupId")
                        showAccessDeniedDialog()
                    }
                } else {
                    Log.d(TAG, "Group not found")
                    Toast.makeText(this, "Group not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                binding.loadingProgress.visibility = View.GONE
                Log.e(TAG, "Error checking group access: ${e.message}", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }
    
    private fun showAccessDeniedDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_access_denied)
        
        // Get admin info to display in message
        val adminRollNumber = currentGroup?.createdBy ?: "Unknown"
        
        // Update the dialog message to include creator info
        if (adminRollNumber.isNotEmpty() && adminRollNumber != "Unknown") {
            // Fetch creator name information from Firestore (if available)
            db.collection("users").document(adminRollNumber)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        // Extract creator name if available
                        val creatorName = document.getString("name") ?: "Unknown"
                        
                        // Update the dialog message with creator info
                        val messageView = dialog.findViewById<TextView>(R.id.dialog_message)
                        messageView.text = "To enter the group, contact the admin:\n\nName: $creatorName\nRoll Number: $adminRollNumber"
                        
                        if (!isFinishing) {
                            dialog.show()
                        }
                    } else {
                        // We have the roll number but no name, still show that
                        showPartialAccessDeniedDialog(dialog, adminRollNumber)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error fetching creator info: ${e.message}", e)
                    showPartialAccessDeniedDialog(dialog, adminRollNumber)
                }
        } else {
            showDefaultAccessDeniedDialog(dialog)
        }
        
        // Set up close button
        dialog.findViewById<Button>(R.id.close_button).setOnClickListener {
            dialog.dismiss()
            finish() // Close the activity after dismissing dialog
        }
        
        // Set dialog properties
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false) // User must click the button
    }
    
    private fun showPartialAccessDeniedDialog(dialog: Dialog, adminRollNumber: String) {
        // Use roll number only if name wasn't available
        val messageView = dialog.findViewById<TextView>(R.id.dialog_message)
        messageView.text = "To enter the group, contact the admin:\n\nRoll Number: $adminRollNumber"
        
        // Show dialog with partial info
        if (!isFinishing) {
            dialog.show()
        }
    }
    
    private fun showDefaultAccessDeniedDialog(dialog: Dialog) {
        // Use default message if we couldn't get creator info
        val messageView = dialog.findViewById<TextView>(R.id.dialog_message)
        messageView.text = "To enter the group, contact the admin with your roll number and name."
        
        // Show dialog with default message
        if (!isFinishing) {
            dialog.show()
        }
    }
    
    private fun setupGroupChat() {
        // Set up the title and toolbar elements
        binding.groupName.text = groupName
        
        // Check if this is the Announcements group and update UI if needed
        val isAnnouncementGroup = groupId == Constants.ANNOUNCEMENT_GROUP_ID
        
        // Get user type to check permissions
        val userType = sessionManager.fetchUserType()
        val isAdmin = userType.equals("Admin", ignoreCase = true)
        val userId = sessionManager.fetchUserId()
        
        // If this is the announcement group and user is not admin, disable message input
        if (isAnnouncementGroup && !isAdmin) {
            disableMessageInput("Only administrators can post announcements")
        } 
        // Check if user has permission to send messages based on messagingPermissions field
        else if (currentGroup != null && !currentGroup!!.canUserSendMessages(userId)) {
            disableMessageInput("You don't have permission to send messages in this group")
        }
        
        // Now that we know the user has access, set up the chat interface
        setupToolbar()
        setupRecyclerView()
        setupMessageInput()
        loadMessages()
    }
    
    private fun setupToolbar() {
        // Set title with group name
        binding.groupName.text = groupName
        
        // Set back button click listener
        binding.backButton.setOnClickListener {
            finish() // Just finish the activity to go back
        }
        
        // Set up search button
        binding.searchButton.setOnClickListener {
            toggleSearch(true)
        }
        
        // Set up menu button
        binding.moreOptions.setOnClickListener { view ->
            showPopupMenu(view)
        }
        
        // Set up search functionality
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
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Debounce to avoid too many refreshes
                binding.searchEditText.removeCallbacks(searchRunnable)
                binding.searchEditText.postDelayed(searchRunnable, 300)
                
                // Show clear button only when there's text
                binding.clearSearchButton.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
        
        // Handle search action from keyboard
        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch(binding.searchEditText.text.toString())
                return@setOnEditorActionListener true
            }
            false
        }
    }
    
    private val searchRunnable = Runnable {
        performSearch(binding.searchEditText.text.toString())
    }
    
    private fun toggleSearch(show: Boolean) {
        isSearchActive = show
        
        if (show) {
            binding.searchContainer.visibility = View.VISIBLE
            binding.searchEditText.requestFocus()
            // Show keyboard
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        } else {
            binding.searchContainer.visibility = View.GONE
            // Clear search
            binding.searchEditText.setText("")
            // Reset to show all messages
            resetSearchResults()
            // Hide keyboard
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
        }
    }
    
    private fun performSearch(query: String) {
        try {
            if (query.isBlank()) {
                resetSearchResults()
                return
            }
            
            val lowerCaseQuery = query.lowercase().trim()
            
            // Filter messages that contain the search query
            val filteredMessages = allMessages.filter { message ->
                message.text?.lowercase()?.contains(lowerCaseQuery) == true || 
                senderNames[message.sender]?.lowercase()?.contains(lowerCaseQuery) == true
            }
            
            // Update the adapter with filtered messages
            messageAdapter.updateMessages(filteredMessages)
            
            // Scroll to the first match
            if (filteredMessages.isNotEmpty()) {
                binding.messagesRecyclerView.scrollToPosition(0)
            }
            
            // Show a message if no results found
            if (filteredMessages.isEmpty() && query.isNotEmpty()) {
                Toast.makeText(this, "No messages found for '$query'", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching messages: ${e.message}", e)
            Toast.makeText(this, "Error searching messages", Toast.LENGTH_SHORT).show()
            resetSearchResults()
        }
    }
    
    private fun resetSearchResults() {
        // Restore original message list
        messageAdapter.updateMessages(allMessages)
    }
    
    private fun loadGroupDetails() {
        db.collection("groups").document(groupId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    currentGroup = document.toObject(Group::class.java)?.apply {
                        id = document.id
                    }
                    Log.d(TAG, "Group loaded: ${currentGroup?.name}, admins: ${currentGroup?.admins}")
                } else {
                    Log.d(TAG, "Group not found")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading group: ${e.message}", e)
            }
    }
    
    private fun showPopupMenu(view: View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.group_chat_menu, popupMenu.menu)
        
        // Get user type to check permissions
        val userType = sessionManager.fetchUserType()
        val isAdmin = userType.equals("Admin", ignoreCase = true)
        
        // Show/hide menu items based on user role
        popupMenu.menu.findItem(R.id.action_manage_participants)?.isVisible = isAdmin
        popupMenu.menu.findItem(R.id.action_manage_permissions)?.isVisible = isAdmin
        popupMenu.menu.findItem(R.id.action_edit_group_name)?.isVisible = isAdmin
        popupMenu.menu.findItem(R.id.action_edit_description)?.isVisible = isAdmin
        popupMenu.menu.findItem(R.id.action_view_group_info)?.isVisible = true  // Visible to all users
        
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_manage_participants -> {
                    startManageParticipants()
                    true
                }
                R.id.action_manage_permissions -> {
                    startManagePermissions()
                    true
                }
                R.id.action_edit_group_name -> {
                    showEditGroupNameDialog()
                    true
                }
                R.id.action_edit_description -> {
                    showEditDescriptionDialog()
                    true
                }
                R.id.action_view_group_info -> {
                    showGroupInfo()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }
    
    private fun startManageParticipants() {
        val intent = Intent(this, ManageParticipantsActivity::class.java).apply {
            putExtra("GROUP_ID", groupId)
            putExtra("GROUP_NAME", groupName)
        }
        startActivity(intent)
    }
    
    private fun startManagePermissions() {
        val intent = Intent(this, ManagePermissionsActivity::class.java).apply {
            putExtra("GROUP_ID", groupId)
            putExtra("GROUP_NAME", groupName)
        }
        startActivity(intent)
    }
    
    private fun showEditGroupNameDialog() {
        // Create and configure the dialog
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_edit_text)
        
        // Set dialog title and hint
        val titleView = dialog.findViewById<TextView>(R.id.dialog_title)
        titleView.text = "Edit Group Name"
        
        // Set the current group name in the edit text
        val editText = dialog.findViewById<EditText>(R.id.edit_text_field)
        editText.setText(currentGroup?.name)
        editText.hint = "Enter group name"
        
        // Set up buttons
        dialog.findViewById<Button>(R.id.cancel_button).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.findViewById<Button>(R.id.save_button).setOnClickListener {
            val newName = editText.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(this, "Group name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            updateGroupName(newName, dialog)
        }
        
        // Show dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    
    private fun showEditDescriptionDialog() {
        // Create and configure the dialog
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_edit_text)
        
        // Set dialog title and hint
        val titleView = dialog.findViewById<TextView>(R.id.dialog_title)
        titleView.text = "Edit Description"
        
        // Set the current description in the edit text
        val editText = dialog.findViewById<EditText>(R.id.edit_text_field)
        editText.setText(currentGroup?.description)
        editText.hint = "Enter group description"
        
        // Allow multiline input for description
        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        editText.maxLines = 5
        editText.setLines(3)
        
        // Set up buttons
        dialog.findViewById<Button>(R.id.cancel_button).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.findViewById<Button>(R.id.save_button).setOnClickListener {
            val newDescription = editText.text.toString().trim()
            if (newDescription.isEmpty()) {
                Toast.makeText(this, "Description cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            updateGroupDescription(newDescription, dialog)
        }
        
        // Show dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    
    private fun updateGroupName(newName: String, dialog: Dialog) {
        // Show progress
        val progressBar = dialog.findViewById<ProgressBar>(R.id.progress_bar) ?: ProgressBar(this)
        progressBar.visibility = View.VISIBLE
        
        // Update the group name in Firestore
        db.collection("groups").document(groupId)
            .update("name", newName)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                dialog.dismiss()
                
                // Update the UI
                binding.groupName.text = newName
                groupName = newName
                
                // Also update the local Group object
                currentGroup?.let { group ->
                    group.name = newName
                }
                
                Toast.makeText(this, "Group name updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e(TAG, "Error updating group name: ${e.message}", e)
                Toast.makeText(this, "Failed to update group name: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun updateGroupDescription(newDescription: String, dialog: Dialog) {
        // Show progress
        val progressBar = dialog.findViewById<ProgressBar>(R.id.progress_bar) ?: ProgressBar(this)
        progressBar.visibility = View.VISIBLE
        
        // Update the group description in Firestore
        db.collection("groups").document(groupId)
            .update("description", newDescription)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                dialog.dismiss()
                
                // Update the local Group object
                currentGroup?.let { group ->
                    group.description = newDescription
                }
                
                Toast.makeText(this, "Group description updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e(TAG, "Error updating group description: ${e.message}", e)
                Toast.makeText(this, "Failed to update description: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun showGroupInfo() {
        if (currentGroup == null) {
            Toast.makeText(this, "Group information not available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialog = Dialog(this)
        val dialogBinding = DialogGroupInfoBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        
        // Fill in group information
        dialogBinding.groupInfoTitle.text = currentGroup?.name
        dialogBinding.groupDescription.text = currentGroup?.description
        
        // Fetch creator name from users collection
        val creatorId = currentGroup?.createdBy ?: "Unknown"
        dialogBinding.groupCreator.text = creatorId // Default to ID while loading
        
        if (creatorId != "Unknown" && creatorId.isNotEmpty()) {
            db.collection("users").document(creatorId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val creatorName = document.getString("name")
                        if (!creatorName.isNullOrEmpty()) {
                            dialogBinding.groupCreator.text = creatorName
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error fetching creator name", e)
                }
        }
        
        // Format the date
        val createdDate = currentGroup?.createdAt?.toDate()
        val dateFormat = SimpleDateFormat("MMM d, yyyy, hh:mm a", Locale.getDefault())
        dialogBinding.groupCreatedDate.text = createdDate?.let { dateFormat.format(it) } ?: "Unknown"
        
        dialogBinding.groupParticipantsCount.text = "${currentGroup?.participants?.size ?: 0} participants"
        
        // Set up close button
        dialogBinding.closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Set dialog properties
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    
    private fun setupRecyclerView() {
        // Initialize the message adapter with the current user ID
        messageAdapter = MessageAdapter(sessionManager.fetchUserId()) { message ->
            val userId = sessionManager.fetchUserId()
            val isSender = message.sender == userId
            val isAdmin = currentGroup?.isUserAdmin(userId) == true
            val hasPermission = currentGroup?.canUserSendMessages(userId) == true
            
            // Allow deletion if the user is an admin, OR if they are the sender AND have permission
            if ((isSender && hasPermission) || isAdmin) {
                val alertMessage = if (isAdmin && !isSender) {
                    "Are you sure you want to delete this participant's message for everyone?"
                } else {
                    "Are you sure you want to delete this message for everyone?"
                }
                
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Delete Message")
                    .setMessage(alertMessage)
                    .setPositiveButton("Delete") { _, _ ->
                        messageRepository.deleteMessage(groupId, message.id)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else if (isSender && !hasPermission) {
                Toast.makeText(this, "You cannot delete messages while your permissions are revoked.", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Configure RecyclerView
        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@GroupChatActivity).apply {
                stackFromEnd = true // Messages appear from bottom
            }
            adapter = messageAdapter
        }
    }
    
    private fun setupMessageInput() {
        binding.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.sendButton.isEnabled = !s.isNullOrBlank()
            }
        })
        
        binding.sendButton.setOnClickListener {
            val messageText = binding.messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
            }
        }
        
        binding.attachButton.setOnClickListener {
            showAttachmentOptions(it)
        }
    }
    
    // Add method to show attachment options
    private fun showAttachmentOptions(view: View) {
        val popupMenu = PopupMenu(this, view)
        val menu = popupMenu.menu
        
        menu.add(Menu.NONE, 1, Menu.NONE, "Photo from Gallery")
        menu.add(Menu.NONE, 2, Menu.NONE, "Take Photo")
        menu.add(Menu.NONE, 3, Menu.NONE, "Document")
        
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> attachmentHandler.pickImageFromGallery()
                2 -> attachmentHandler.takePhoto()
                3 -> attachmentHandler.pickDocument()
            }
            true
        }
        
        popupMenu.show()
    }
    
    // Add method to send media messages
    private fun sendMediaMessage(fileUrl: String, fileType: String) {
        // Get current user ID
        val currentUserId = sessionManager.fetchUserId()
        if (currentUserId.isEmpty()) {
            showToast("You must be logged in to send messages")
            return
        }
        
        // Check if user has permission to send messages
        if (currentGroup != null && !currentGroup!!.canUserSendMessages(currentUserId)) {
            showToast("You don't have permission to send files in this group")
            return
        }
        
        // Check if this is the announcement group and if user has permission to post
        val isAnnouncementGroup = groupId == Constants.ANNOUNCEMENT_GROUP_ID
        val userType = sessionManager.fetchUserType()
        val isAdmin = userType.equals("Admin", ignoreCase = true)
        
        if (isAnnouncementGroup && !isAdmin) {
            showToast("Only administrators can post attachments to announcements")
            return
        }
        
        // Get group participants to initialize read status map
        val participants = currentGroup?.participants ?: listOf()
        val readStatusMap = mutableMapOf<String, Boolean>()
        
        // Initialize read status as false for all participants except the sender
        participants.forEach { participantId ->
            readStatusMap[participantId] = participantId == currentUserId // Only sender has read = true
        }
        
        // Create a display text based on media type
        val displayText = when (fileType) {
            "image" -> "[Image]"
            "document" -> "[Document]"
            else -> "[File]"
        }
        
        // Create message object with current timestamp
        val timestamp = Timestamp.now()
        val message = Message(
            text = fileUrl, // Store the file URL in the text field
            sender = currentUserId,
            timestamp = timestamp,
            read = readStatusMap,
            mentionsEveryone = false,
            taggedUsers = emptyList(),
            groupId = groupId,
            mediaUrl = fileUrl,
            mediaType = fileType,
            contentType = fileType // Use contentType for UI rendering
        )
        
        // Convert message to map for Firestore
        val messageMap = hashMapOf(
            "text" to message.text,
            "sender" to message.sender,
            "timestamp" to message.timestamp,
            "read" to message.read,
            "groupId" to message.groupId,
            "mentionsEveryone" to message.mentionsEveryone,
            "taggedUsers" to message.taggedUsers,
            "mediaUrl" to fileUrl,
            "mediaType" to fileType,
            "contentType" to fileType
        )
        
        // Generate a timestamp-based document ID instead of auto-generated ID
        // Format: {timestamp_seconds}{timestamp_nanoseconds}
        val messageId = timestamp.seconds.toString() + timestamp.nanoseconds.toString()
        // Clear input field immediately for responsive UI
        binding.messageInput.setText("")

        // Add the message to Firestore with timestamp-based ID
        db.collection("groups").document(groupId)
            .collection("messages")
            .document(messageId)  // Use timestamp-based ID
            .set(messageMap)      // Use set instead of add
            .addOnSuccessListener { 
                Log.d(TAG, "Media message added with ID: $messageId")
                
                // Send push notifications
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val senderName = sessionManager.fetchUserName().takeIf { it.isNotEmpty() } ?: "Someone"
                    val groupTitle = currentGroup?.name ?: groupName
                    val participants = currentGroup?.participants ?: listOf()
                    
                    // Notify everyone in the group for media messages
                    val targets = participants.filter { it != sessionManager.fetchUserId() }
                    
                    targets.forEach { targetId ->
                        com.phad.chatapp.utils.NotificationSender.sendNotification(
                            topic = "user_$targetId",
                            title = "$senderName in $groupTitle",
                            body = "Sent a $fileType",
                            data = mapOf(
                                "type" to "group_message",
                                "groupId" to groupId,
                                "senderId" to sessionManager.fetchUserId(),
                                "senderName" to senderName
                            )
                        )
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error adding media message", e)
                showToast("Failed to send $fileType")
            }
    }
    
    private fun loadMessages() {
        // Show loading progress
        binding.loadingProgress.visibility = View.VISIBLE
        
        // Listen for new messages
        messageRepository.getMessagesForGroup(groupId)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, e ->
            if (e != null) {
                    Log.e(TAG, "Listen failed.", e)
                    binding.loadingProgress.visibility = View.GONE
                return@addSnapshotListener
            }
            
                if (snapshot != null) {
            val messages = mutableListOf<Message>()
            
                    // Process the documents
                for (doc in snapshot.documents) {
                    try {
                            val message = doc.toObject(Message::class.java)
                            if (message != null) {
                                // Ensure message has an ID
                                message.id = doc.id
                                message.isPending = doc.metadata.hasPendingWrites()
                                messages.add(message)
                                
                                // If we don't have the sender's name, fetch it
                                if (!senderNames.containsKey(message.sender)) {
                                    fetchUserName(message.sender)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing message: ${e.message}")
                        }
                    }
                    
                    // Sort messages by timestamp (newest at the bottom)
                    messages.sortBy { it.timestamp }
                    
                    // Store all messages for search functionality
                    allMessages.clear()
                    allMessages.addAll(messages)
                    
                    // Update the adapter with all messages or search results
                    if (isSearchActive && binding.searchEditText.text.isNotEmpty()) {
                        performSearch(binding.searchEditText.text.toString())
            } else {
                        messageAdapter.updateMessages(messages)
                    }
                    
                    // Mark messages as read since they are being displayed
                    markMessagesAsRead()
                    
                    // Hide loading progress
                    binding.loadingProgress.visibility = View.GONE
                    
                    // Scroll to the bottom if there are messages
                    if (messages.isNotEmpty()) {
                        binding.messagesRecyclerView.scrollToPosition(messages.size - 1)
                    }
                }
        }
    }
    
    private fun sendMessage(text: String) {
        if (text.isBlank()) {
            return
        }
        
        // Get current user ID
        val currentUserId = sessionManager.fetchUserId()
        if (currentUserId.isEmpty()) {
            showToast("You must be logged in to send messages")
            return
        }
        
        // Check if user has permission to send messages
        if (currentGroup != null && !currentGroup!!.canUserSendMessages(currentUserId)) {
            showToast("You don't have permission to send messages in this group")
            return
        }
        
        // Check if this is the announcement group and if user has permission to post
        val isAnnouncementGroup = groupId == Constants.ANNOUNCEMENT_GROUP_ID
        val userType = sessionManager.fetchUserType()
        val isAdmin = userType.equals("Admin", ignoreCase = true)
        
        if (isAnnouncementGroup && !isAdmin) {
            showToast("Only administrators can post announcements")
            return
        }
        
        // Check if the message contains @everyone or @rollNumber mentions
        val hasEveryoneMention = text.contains("@everyone", ignoreCase = true)
        
        // Extract mentioned roll numbers using regex
        val mentionedUsers = mutableListOf<String>()
        val pattern = Pattern.compile("@([0-9A-Z]{8}|[0-9]{4}[A-Z]{2}[0-9]{2})\\b")
        val matcher = pattern.matcher(text)
        
        while (matcher.find()) {
            matcher.group(1)?.let { mentionedRollNumber ->
                mentionedUsers.add(mentionedRollNumber)
                Log.d(TAG, "Found mention of roll number: $mentionedRollNumber")
            }
        }
        
        // Get group participants to initialize read status map
        val participants = currentGroup?.participants ?: listOf()
        val readStatusMap = mutableMapOf<String, Boolean>()
        
        // Initialize read status as false for all participants except the sender
        participants.forEach { participantId ->
            readStatusMap[participantId] = participantId == currentUserId // Only sender has read = true
        }
        
        // Create message object with current timestamp
        val timestamp = Timestamp.now()
        val message = Message(
            text = text,
            sender = currentUserId,
            timestamp = timestamp,
            read = readStatusMap,
            mentionsEveryone = hasEveryoneMention,
            taggedUsers = mentionedUsers,
            groupId = groupId
        )

        // Convert message to map for Firestore
        val messageMap = hashMapOf(
            "text" to message.text,
            "sender" to message.sender,
            "timestamp" to message.timestamp,
            "read" to message.read,
            "groupId" to message.groupId,
            "mentionsEveryone" to message.mentionsEveryone,
            "taggedUsers" to message.taggedUsers
        )

        // Generate a timestamp-based document ID instead of auto-generated ID
        // Format: {timestamp_seconds}{timestamp_nanoseconds}
        val messageId = timestamp.seconds.toString() + timestamp.nanoseconds.toString()
        // Clear input field immediately for responsive UI
        binding.messageInput.setText("")

        // Add message to group messages collection with timestamp-based ID
        db.collection("groups").document(groupId)
            .collection("messages")
            .document(messageId)
            .set(messageMap)
            .addOnSuccessListener {
                Log.d(TAG, "Message sent successfully with ID: $messageId")
                
                // Send push notifications
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val senderName = sessionManager.fetchUserName().takeIf { it.isNotEmpty() } ?: "Someone"
                    val groupTitle = currentGroup?.name ?: groupName
                    
                    val targets = if (hasEveryoneMention) {
                        participants.filter { it != currentUserId }
                    } else {
                        mentionedUsers.filter { it != currentUserId }
                    }
                    
                    targets.forEach { targetId ->
                        com.phad.chatapp.utils.NotificationSender.sendNotification(
                            topic = "user_$targetId",
                            title = "$senderName in $groupTitle",
                            body = text,
                            data = mapOf(
                                "type" to "group_message",
                                "groupId" to groupId,
                                "senderId" to currentUserId,
                                "senderName" to senderName
                            )
                        )
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error sending message", e)
                Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
            }
    }
    

    
    /**
     * Fetch sender name if not already cached
     */
    private fun fetchUserName(rollNumber: String) {
        if (rollNumber.isEmpty() || senderNames.containsKey(rollNumber)) return
        
        db.collection("users").document(rollNumber).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    document.getString("name")?.let { name ->
                        senderNames[rollNumber] = name
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching sender name", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "GroupChatActivity resumed")
        // Mark messages as read when activity is resumed
        if (groupId.isNotEmpty()) {
            markMessagesAsRead()
        }
    }
    
    private fun markMessagesAsRead() {
        val currentUserId = sessionManager.fetchUserId()
        if (currentUserId.isEmpty() || groupId.isEmpty()) return
        
        db.collection("groups").document(groupId).collection("messages")
            .whereEqualTo("read.$currentUserId", false)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val unreadDocs = querySnapshot.documents
                
                if (unreadDocs.isEmpty()) return@addOnSuccessListener
                
                Log.d(TAG, "Marking ${unreadDocs.size} group messages as read")
                
                // Firestore batch limit is 500, but we probably won't hit that here
                // We could chunk it if needed
                val chunks = unreadDocs.chunked(400)
                for (chunk in chunks) {
                    val batch = db.batch()
                    for (doc in chunk) {
                        val readMap = (doc.get("read") as? Map<*, *>)?.toMutableMap() ?: mutableMapOf<String, Boolean>()
                        @Suppress("UNCHECKED_CAST")
                        val updatedReadMap = (readMap as MutableMap<String, Boolean>).apply {
                            this[currentUserId] = true
                        }
                        batch.update(doc.reference, "read", updatedReadMap)
                    }
                    batch.commit()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to mark group messages as read", e)
            }
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "GroupChatActivity paused")
    }

    /**
     * Helper method to show toast messages
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun disableMessageInput(message: String) {
        binding.messageInput.isEnabled = false
        binding.messageInput.hint = message
        binding.sendButton.isEnabled = false
        binding.sendButton.alpha = 0.5f
        binding.attachButton.isEnabled = false
        binding.attachButton.alpha = 0.5f
    }

    override fun onBackPressed() {
        // If search is active, close it instead of exiting
        if (isSearchActive) {
            toggleSearch(false)
        } else {
            super.onBackPressed()
        }
    }
} 
