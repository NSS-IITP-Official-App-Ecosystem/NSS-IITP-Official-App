package com.phad.chatapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.phad.chatapp.adapters.MessageAdapter
import com.phad.chatapp.databinding.ActivityChatBinding
import com.phad.chatapp.models.Message
import com.phad.chatapp.utils.AttachmentHandler
import com.phad.chatapp.utils.DriveServiceHelper
import com.phad.chatapp.utils.FileTypeEnum
import com.phad.chatapp.utils.NotificationHelper
import com.phad.chatapp.utils.SessionManager
import java.util.Date
import java.util.regex.Pattern
import androidx.appcompat.widget.PopupMenu

class ChatActivity : AppCompatActivity() {
    private val TAG = "ChatActivity"
    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessageAdapter
    private lateinit var sessionManager: SessionManager
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var currentUserRollNumber: String
    private lateinit var otherUserRollNumber: String
    private lateinit var otherUserName: String
    private lateinit var conversationId: String
    private var otherUserToken: String = ""
    
    // Add fields to track user info loading status
    private var userInfoLoaded = false
    
    // Listener registrations for cleanup
    private var senderListenerRegistration: ListenerRegistration? = null
    private var receiverListenerRegistration: ListenerRegistration? = null
    
    // Add attachment handler as a property
    private lateinit var attachmentHandler: AttachmentHandler
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make the app draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Set status bar color to black
        window.statusBarColor = resources.getColor(android.R.color.black, theme)
        
        // Make status bar icons light for better visibility on dark background
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }
        
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize SessionManager
        sessionManager = SessionManager(this)
        
        // Initialize adapter early to avoid null reference
        adapter = MessageAdapter(sessionManager.fetchUserId())
        
        // Extract user info from intent
        val intent = intent
        otherUserName = intent.getStringExtra("otherUserName") ?: "Chat"
        otherUserRollNumber = intent.getStringExtra("otherUserRollNumber") ?: ""
        
        // Setup window insets
        setupWindowInsets()
        
        // Get data from intent
        currentUserRollNumber = intent.getStringExtra("currentUserRollNumber") ?: ""
        conversationId = generateConversationId(currentUserRollNumber, otherUserRollNumber)
        
        // Validate roll numbers
        if (currentUserRollNumber.isEmpty() || otherUserRollNumber.isEmpty()) {
            // If roll numbers aren't directly available, try to get them
            val currentUser = auth.currentUser
            if (currentUser != null) {
                // Try to extract roll number from display name
                val displayName = currentUser.displayName
                if (!displayName.isNullOrEmpty() && displayName.contains("|")) {
                    // Format is "UserType|RollNumber"
                    val parts = displayName.split("|")
                    if (parts.size >= 2) {
                        currentUserRollNumber = parts[1].trim()
                        Log.d(TAG, "Extracted roll number from display name: $currentUserRollNumber")
                        
                        // Only continue if we have the other user's roll number
                        if (otherUserRollNumber.isNotEmpty()) {
                            userInfoLoaded = true
                            continueSetup()
                        } else {
                            showToast("Could not determine other user's ID")
                            finish()
                        }
                        return
                    }
                }
                
                // If still empty, try email
                if (currentUserRollNumber.isEmpty() && !currentUser.email.isNullOrEmpty()) {
                    // Assuming email format like "rollnumber@domain.com"
                    val email = currentUser.email!!
                    currentUserRollNumber = email.substring(0, email.indexOf('@'))
                    Log.d(TAG, "Extracted potential roll number from email: $currentUserRollNumber")
                    
                    // Only continue if we have the other user's roll number
                    if (otherUserRollNumber.isNotEmpty()) {
                        userInfoLoaded = true
                        continueSetup()
                    } else {
                        showToast("Could not determine other user's ID")
                        finish()
                    }
                    return
                }
                
                // As last resort, check Firestore
                fetchUserRollNumber(currentUser.uid) { fetchedRollNumber ->
                    if (fetchedRollNumber.isNotEmpty()) {
                        currentUserRollNumber = fetchedRollNumber
                        Log.d(TAG, "Current user roll number fetched from Firestore: $currentUserRollNumber")
                        
                        // Only continue if we have the other user's roll number
                        if (otherUserRollNumber.isNotEmpty()) {
                            userInfoLoaded = true
                            continueSetup()
                        } else {
                            showToast("Could not determine other user's ID")
                            finish()
                        }
                    } else {
                        showToast("Could not determine your user ID")
                        finish()
                    }
                }
            } else {
                showToast("You are not logged in")
                finish()
                return
            }
        } else {
            // Roll numbers are already available
            userInfoLoaded = true
            Log.d(TAG, "Chat initialization with roll numbers - Current user: $currentUserRollNumber, Other user: $otherUserRollNumber ($otherUserName)")
        }
        
        // Setup toolbar with the user's name
        binding.textTitle.text = otherUserName
        
        // Setup back button
        binding.backButton.setOnClickListener {
            finish()
        }
        
        // Setup RecyclerView with proper layout
        setupRecyclerView()
        
        // Setup message input and send button
        setupMessageInput()
        
        // Initialize attachment handler
        attachmentHandler = AttachmentHandler(this)
        
        // Initialize Drive service
        attachmentHandler.initDriveService()
        
        attachmentHandler.setOnFileSelectedListener { fileUri, fileType ->
            // Show loading indicator
            showLoading(true)
            
            // Upload the file
            attachmentHandler.uploadFile(fileUri, fileType) { fileUrl ->
                // Hide loading indicator
                showLoading(false)
                
                if (fileUrl != null) {
                    // Insert the file URL into the message input or send directly
                    when (fileType) {
                        FileTypeEnum.IMAGE -> sendMediaMessage(fileUrl, "image")
                        FileTypeEnum.DOCUMENT -> sendMediaMessage(fileUrl, "document")
                    }
                }
            }
        }
        
        // If user info is already loaded, continue with setup
        if (userInfoLoaded) {
            continueSetup()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Mark messages as read when activity is resumed
        if (::currentUserRollNumber.isInitialized && ::otherUserRollNumber.isInitialized) {
            markMessagesAsRead()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up listeners to avoid memory leaks
        senderListenerRegistration?.remove()
        receiverListenerRegistration?.remove()
    }
    
    private fun sendMessage(text: String) {
        val userId = sessionManager.fetchUserId()
        if (userId.isEmpty()) {
            showToast("You must be logged in to send messages")
            return
        }
        
        // Check if the message contains @everyone mention
        val hasEveryoneMention = text.contains("@everyone", ignoreCase = true)
        
        // Initialize read status map
        val readStatusMap = mutableMapOf<String, Boolean>()
        readStatusMap[currentUserRollNumber] = true // Sender has read it
        readStatusMap[otherUserRollNumber] = false  // Receiver hasn't read it yet
        
        // Create the message object
        val message = Message(
            text = text,
            sender = userId,
            timestamp = Timestamp.now(),
            read = readStatusMap,
            mentionsEveryone = hasEveryoneMention,
            groupId = "",  // Not a group chat
            receiver = otherUserRollNumber
        )
        
        // Convert to HashMap for Firestore
        val messageMap = hashMapOf(
            "text" to message.text,
            "sender" to message.sender,
            "timestamp" to message.timestamp,
            "read" to message.read,
            "mentionsEveryone" to message.mentionsEveryone,
            "receiver" to message.receiver
        )
        
        // Generate a timestamp-based document ID instead of random ID
        // Format: {timestamp_milliseconds}
        val messageId = System.currentTimeMillis().toString()
        
        // Create a batch to ensure both users' collections are updated
        val batch = db.batch()
        
        // 1. Add message to current user's collection
        val currentUserMessageRef = db.collection("user_conversations")
            .document(currentUserRollNumber)
            .collection(otherUserRollNumber)
            .document(messageId)
        
        batch.set(currentUserMessageRef, messageMap)
        
        // 2. Add the same message to the other user's collection
        val otherUserMessageRef = db.collection("user_conversations")
            .document(otherUserRollNumber)
            .collection(currentUserRollNumber)
            .document(messageId)
        
        batch.set(otherUserMessageRef, messageMap)
        
        // Commit the batch
        batch.commit()
            .addOnSuccessListener {
                Log.d(TAG, "Message sent with ID: $messageId")
                binding.messageInput.setText("")
                
                // Send notification to recipient
                val notificationHelper = NotificationHelper(this)
                notificationHelper.sendMessageNotification(
                    "", // FCM token - will be fetched in the method
                    currentUserRollNumber, // sender roll number - will be used to fetch the actual name
                    text, // message 
                    otherUserRollNumber, // receiver
                    false, // not a group message
                    "", // no group ID
                    currentUserRollNumber // sender ID
                )
                
                // Update last message in conversation
                updateLastMessage(text)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error sending message", e)
                showToast("Failed to send message")
            }
    }
    
    /**
     * Update the last message in conversation metadata
     */
    private fun updateLastMessage(text: String) {
        // Create conversation metadata if it doesn't exist or update it
        val conversationData = hashMapOf(
            "lastMessage" to text,
            "lastMessageTime" to Timestamp.now(),
            "participants" to listOf(currentUserRollNumber, otherUserRollNumber),
            "sender" to currentUserRollNumber,
            "isImportant" to false
        )
        
        // Use the same conversationId format for consistency
        val metadataId = generateConversationId(currentUserRollNumber, otherUserRollNumber)
        
        db.collection("conversation_metadata")
            .document(metadataId)
            .set(conversationData)
            .addOnSuccessListener {
                Log.d(TAG, "Conversation metadata updated")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating conversation metadata", e)
            }
    }
    
    /**
     * Extract mentioned users from a message text
     * This method is kept for compatibility with group chats but not used in 1-1 messages
     */
    private fun extractMentionedUsers(text: String): List<String> {
        val mentions = mutableListOf<String>()
        val pattern = Pattern.compile("@([a-zA-Z0-9]+)\\b")
        val matcher = pattern.matcher(text)
        
        while (matcher.find()) {
            val mentionedUser = matcher.group(1)
            if (mentionedUser != null && mentionedUser != "everyone") {
                mentions.add(mentionedUser)
            }
        }
        
        return mentions
    }
    
    /**
     * Generate a consistent conversation ID from two user IDs
     */
    private fun generateConversationId(userId1: String, userId2: String): String {
        // Sort IDs to ensure consistency regardless of who initiates the conversation
        val sortedIds = listOf(userId1, userId2).sorted()
        return "${sortedIds[0]}_${sortedIds[1]}"
    }
    
    /**
     * Show a toast message
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun markMessagesAsRead() {
        Log.d(TAG, "Starting to mark messages as read from $otherUserRollNumber to $currentUserRollNumber")
        
        // Get all messages sent by the other user
        db.collection("user_conversations")
            .document(currentUserRollNumber)
            .collection(otherUserRollNumber)
            .whereEqualTo("sender", otherUserRollNumber)
            .get()
            .addOnSuccessListener { querySnapshot ->
                // Filter messages that the current user hasn't read yet
                val unreadDocs = querySnapshot.documents.filter { doc ->
                    val readMap = doc.get("read") as? Map<*, *>
                    val currentUserRead = readMap?.get(currentUserRollNumber) as? Boolean ?: false
                    !currentUserRead
                }
                
                val messageCount = unreadDocs.size
                if (messageCount == 0) {
                    Log.d(TAG, "No unread messages to mark as read")
                    return@addOnSuccessListener
                }
                
                Log.d(TAG, "Marking $messageCount messages as read for user $currentUserRollNumber")
                
                // Create a batch to update all documents at once
                val batch = db.batch()
                
                // For each unread message
                for (document in unreadDocs) {
                    // Get current read map
                    val readMap = (document.get("read") as? Map<*, *>)?.toMutableMap() ?: mutableMapOf<String, Boolean>()
                    
                    // Update current user's read status
                    @Suppress("UNCHECKED_CAST")
                    val updatedReadMap = (readMap as MutableMap<String, Boolean>).apply {
                        this[currentUserRollNumber] = true
                    }
                    
                    // Update in current user's collection
                    batch.update(document.reference, "read", updatedReadMap)
                    
                    // Also update in other user's collection (same message ID)
                    val otherUserRef = db.collection("user_conversations")
                        .document(otherUserRollNumber)
                        .collection(currentUserRollNumber)
                        .document(document.id)
                    
                    batch.update(otherUserRef, "read", updatedReadMap)
                }
                
                // Commit the batch
                batch.commit()
                    .addOnSuccessListener {
                        Log.d(TAG, "Messages marked as read successfully for user $currentUserRollNumber")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to mark messages as read", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching unread messages", e)
            }
    }
    
    private fun setupRecyclerView() {
        // Setup layout manager with proper configuration
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true  // Show newest messages at the bottom
            reverseLayout = false
        }
        
        binding.recyclerMessages.apply {
            this.layoutManager = layoutManager
            this.adapter = this@ChatActivity.adapter
            setHasFixedSize(true)
            
            // Add scroll listener to handle new messages
            addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
                if (bottom < oldBottom) {
                    post {
                        // Add null safety check
                        this.adapter?.let { adapter ->
                            if (adapter.itemCount > 0) {
                                smoothScrollToPosition(adapter.itemCount - 1)
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun setupMessageListeners() {
        Log.d(TAG, "Setting up message listeners for conversations between $currentUserRollNumber and $otherUserRollNumber")
        
        // Listen for messages in the current user's collection (all messages between currentUser and otherUser)
        senderListenerRegistration = db.collection("user_conversations")
            .document(currentUserRollNumber)
            .collection(otherUserRollNumber)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Listen failed for messages: ${e.message}", e)
                    return@addSnapshotListener
                }
                
                if (snapshot == null) {
                    Log.d(TAG, "No messages snapshot")
                    return@addSnapshotListener
                }
                
                Log.d(TAG, "Received ${snapshot.documents.size} messages from Firestore")
                
                // Process the messages
                if (!snapshot.isEmpty) {
                    processMessages(snapshot.documents)
                    
                    // Mark messages as read if they are from the other user and unread
                    val unreadMessages = snapshot.documents.filter { doc ->
                        val sender = doc.getString("sender")
                        
                        // Get read status map
                        @Suppress("UNCHECKED_CAST")
                        val readMap = doc.get("read") as? Map<String, Boolean>
                        
                        // Check if message is from other user and current user hasn't read it
                        val currentUserRead = readMap?.get(currentUserRollNumber) ?: false
                        sender == otherUserRollNumber && !currentUserRead
                    }
                    
                    if (unreadMessages.isNotEmpty()) {
                        markSpecificMessagesAsRead(unreadMessages)
                    }
                } else {
                    // No messages yet
                    if (::adapter.isInitialized) {
                        adapter.updateMessages(emptyList())
                    } else {
                        Log.w(TAG, "Cannot update messages: adapter not initialized")
                    }
                }
            }
    }
    
    private fun processMessages(documents: List<com.google.firebase.firestore.DocumentSnapshot>) {
        Log.d(TAG, "Processing ${documents.size} messages from snapshot")
                
        val messages = mutableListOf<Message>()
        
        documents.forEach { doc ->
            val data = doc.data
            if (data != null) {
                // Get the read status map
                @Suppress("UNCHECKED_CAST")
                val readMap = (data["read"] as? Map<String, Boolean>) ?: mapOf(
                    currentUserRollNumber to false,
                    otherUserRollNumber to false
                )
                
                // Extract media fields
                val mediaUrl = data["mediaUrl"] as? String ?: ""
                val mediaType = data["mediaType"] as? String ?: ""
                val contentType = data["contentType"] as? String ?: ""
                
                // For direct messages, we don't use taggedUsers field
                val message = Message(
                    id = doc.id,
                    text = data["text"] as? String ?: "",
                    sender = data["sender"] as? String ?: "",
                    timestamp = data["timestamp"] as? Timestamp ?: Timestamp(Date()),
                    read = readMap,
                    mentionsEveryone = data["mentionsEveryone"] as? Boolean ?: false,
                    taggedUsers = emptyList(), // Empty for direct messages
                    mediaUrl = mediaUrl,
                    mediaType = mediaType,
                    contentType = contentType,
                    receiver = data["receiver"] as? String ?: ""
                )
                messages.add(message)
                Log.d(TAG, "Message parsed: ID=${message.id}, Text=${message.text}, From=${message.sender}, ReadStatus=${message.read}, MediaType=${message.mediaType}")
            } else {
                Log.w(TAG, "Skipping message with null data, id: ${doc.id}")
            }
        }
                
        // Update the UI on the main thread
        runOnUiThread {
            // Check if activity is still active and adapter is initialized
            if (!isFinishing && !isDestroyed && ::adapter.isInitialized) {
                // Update messages and scroll to bottom
                adapter.updateMessages(messages)
                if (messages.isNotEmpty()) {
                    binding.recyclerMessages.post {
                        binding.recyclerMessages.adapter?.let { adapter ->
                            binding.recyclerMessages.smoothScrollToPosition(adapter.itemCount - 1)
                        }
                    }
                }
            } else {
                Log.w(TAG, "Skipping UI update because activity is finishing or adapter not initialized")
            }
        }
    }
    
    private fun setupWindowInsets() {
        // Set status bar color to transparent
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        
        // Make status bar icons dark or light based on theme
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
    }
    
    private fun setupMessageInput() {
        // Enable/disable send button based on message content
        binding.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.buttonSend.isEnabled = !s.isNullOrEmpty()
            }
        })
        
        // Set up send button click listener
        binding.buttonSend.setOnClickListener {
            val messageText = binding.messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
            }
        }
        
        // Set up attachment button
        binding.attachButton.setOnClickListener {
            showAttachmentOptions(it)
        }
    }
    
    private fun fetchUserRollNumber(uid: String, callback: (String) -> Unit) {
        Log.d(TAG, "Fetching roll number for user with UID: $uid")
        db.collection("users")
            .whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val document = documents.documents[0]
                    val rollNumber = document.getString("rollNumber") ?: ""
                    callback(rollNumber)
                } else {
                    Log.w(TAG, "No user document found for UID: $uid")
                    callback("")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching user by UID", e)
                callback("")
            }
    }
    
    private fun continueSetup() {
        // Setup message listeners
        setupMessageListeners()
        
        // Mark messages as read initially
        markMessagesAsRead()
        
        // Start listening for notifications for this user
        val notificationHelper = NotificationHelper(this)
        notificationHelper.startListeningForNotifications(currentUserRollNumber)
    }
    
    override fun onPause() {
        super.onPause()
        
        // Stop notification listener when activity is paused
        val notificationHelper = NotificationHelper(this)
        notificationHelper.stopListeningForNotifications()
    }
    
    private fun markSpecificMessagesAsRead(messages: List<com.google.firebase.firestore.DocumentSnapshot>) {
        if (messages.isEmpty()) return
        
        Log.d(TAG, "Marking ${messages.size} specific messages as read")
        
        // Create a batch to update all documents at once
        val batch = db.batch()
        
        // For each unread message
        for (document in messages) {
            // Get existing read map if it exists
            @Suppress("UNCHECKED_CAST")
            val existingReadMap = (document.get("read") as? Map<String, Boolean>) ?: mutableMapOf()
            
            // Create updated read map
            val updatedReadMap = existingReadMap.toMutableMap()
            updatedReadMap[currentUserRollNumber] = true // Mark as read by current user
            
            // Update in current user's collection
            batch.update(document.reference, "read", updatedReadMap)
            
            // Also update in other user's collection (same message ID)
            val otherUserRef = db.collection("user_conversations")
                .document(otherUserRollNumber)
                .collection(currentUserRollNumber)
                .document(document.id)
            
            batch.update(otherUserRef, "read", updatedReadMap)
        }
        
        // Commit the batch
        batch.commit()
            .addOnSuccessListener {
                Log.d(TAG, "Messages marked as read successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to mark messages as read", e)
            }
    }
    
    // Add a show loading method if it doesn't exist
    private fun showLoading(show: Boolean) {
        if (::binding.isInitialized) {
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        }
    }
    
    // Add this method to handle attachment button clicks
    private fun showAttachmentOptions(view: View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menu.add(Menu.NONE, 1, Menu.NONE, "Photo from Gallery")
        popupMenu.menu.add(Menu.NONE, 2, Menu.NONE, "Take Photo")
        popupMenu.menu.add(Menu.NONE, 3, Menu.NONE, "Document")
        popupMenu.menu.add(Menu.NONE, 4, Menu.NONE, "Audio")
        
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
        val userId = sessionManager.fetchUserId()
        if (userId.isEmpty()) {
            showToast("You must be logged in to send messages")
            return
        }
        
        // Initialize read status map
        val readStatusMap = mutableMapOf<String, Boolean>()
        readStatusMap[currentUserRollNumber] = true // Sender has read it
        readStatusMap[otherUserRollNumber] = false  // Receiver hasn't read it yet
        
        // Create the message object with media content
        val message = Message(
            text = fileUrl,
            sender = userId,
            timestamp = Timestamp.now(),
            read = readStatusMap,
            mentionsEveryone = false,
            groupId = "",  // Not a group chat
            receiver = otherUserRollNumber,
            contentType = fileType,  // Set the content type for UI rendering
            mediaUrl = fileUrl,      // Add mediaUrl field
            mediaType = fileType     // Add mediaType field
        )
        
        // Convert to HashMap for Firestore
        val messageMap = hashMapOf(
            "text" to message.text,
            "sender" to message.sender,
            "timestamp" to message.timestamp,
            "read" to message.read,
            "mentionsEveryone" to message.mentionsEveryone,
            "receiver" to message.receiver,
            "contentType" to message.contentType,
            "mediaUrl" to message.mediaUrl,    // Add mediaUrl field 
            "mediaType" to message.mediaType   // Add mediaType field
        )
        
        // Generate a unique message ID using the current time in milliseconds
        val currentTimeMillis = System.currentTimeMillis()
        val messageId = currentTimeMillis.toString()
        
        // Create a batch to ensure both users' collections are updated
        val batch = db.batch()
        
        // 1. Add message to current user's collection
        val currentUserMessageRef = db.collection("user_conversations")
            .document(currentUserRollNumber)
            .collection(otherUserRollNumber)
            .document(messageId)
        
        batch.set(currentUserMessageRef, messageMap)
        
        // 2. Add the same message to the other user's collection
        val otherUserMessageRef = db.collection("user_conversations")
            .document(otherUserRollNumber)
            .collection(currentUserRollNumber)
            .document(messageId)
        
        batch.set(otherUserMessageRef, messageMap)
        
        // Commit the batch
        batch.commit()
            .addOnSuccessListener {
                Log.d(TAG, "Media message sent with ID: $messageId")
                
                // Send notification to recipient
                val notificationHelper = NotificationHelper(this)
                val notificationText = "Sent a $fileType"
                notificationHelper.sendMessageNotification(
                    "", // FCM token - will be fetched in the method
                    currentUserRollNumber, // sender roll number - will be used to fetch the actual name
                    notificationText, // message 
                    otherUserRollNumber, // receiver
                    false, // not a group message
                    "", // no group ID
                    currentUserRollNumber // sender ID
                )
                
                // Update last message in conversation
                updateLastMessage("[${fileType.capitalize()}]")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error sending media message", e)
                showToast("Failed to send $fileType")
            }
    }
}