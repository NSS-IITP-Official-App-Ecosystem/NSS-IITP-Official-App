package com.phad.chatapp.utils

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import com.google.firebase.Timestamp
import java.util.UUID
import android.widget.Toast

/**
 * Helper class for sending notifications through Firebase
 */
class NotificationHelper(private val context: Context) {
    private val TAG = "NotificationHelper"
    private var notificationsListener: ListenerRegistration? = null
    private var lastNotificationId: String? = null
    private val notificationProcessingTimes = HashMap<String, Long>()
    
    companion object {
        // Pattern for detecting @everyone mentions
        private val EVERYONE_PATTERN = Pattern.compile("@everyone\\b", Pattern.CASE_INSENSITIVE)
        
        // Pattern for detecting @user mentions - matches @followed by letters/numbers
        private val USER_MENTION_PATTERN = Pattern.compile("@([a-zA-Z0-9]+)\\b")
        
        // Debounce time to prevent duplicate notifications (milliseconds)
        private const val NOTIFICATION_DEBOUNCE_TIME = 3000L
    }
    
    /**
     * Send a notification to all members of a group using Firebase Firestore
     * This stores notification data in a Firestore collection
     */
    suspend fun sendGroupMessageNotification(
        groupId: String,
        groupName: String,
        message: String,
        senderRollNumber: String,
        senderName: String
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Preparing to send notification for message: '$message' in group: $groupName")
            
            // Check if this message mentions @everyone
            val isEveryoneMention = EVERYONE_PATTERN.matcher(message).find()
            
            // Extract individual user mentions (@username)
            val mentionMatcher = USER_MENTION_PATTERN.matcher(message)
            val mentionedUsers = mutableListOf<String>()
            
            while (mentionMatcher.find()) {
                val mentionedUser = mentionMatcher.group(1)
                if (mentionedUser != null && mentionedUser != "everyone") {
                    mentionedUsers.add(mentionedUser)
                    Log.d(TAG, "Found mention for user: $mentionedUser")
                }
            }
            
            // Get all group participants
            val group = FirebaseFirestore.getInstance()
                .collection("groups")
                .document(groupId)
                .get()
                .await()
            
            val participants = group.get("participants") as? List<String> ?: emptyList()
            
            // Skip if there are no participants
            if (participants.isEmpty()) {
                Log.d(TAG, "No participants in group $groupId to notify")
                return@withContext
            }
            
            Log.d(TAG, "Found ${participants.size} participants, mentions: ${mentionedUsers.size}, @everyone: $isEveryoneMention")
            
            // Create a notification data document in Firestore
            val notificationId = UUID.randomUUID().toString()
            lastNotificationId = notificationId
            
            val notificationData = hashMapOf(
                "type" to "GROUP_MESSAGE",
                "groupId" to groupId,
                "groupName" to groupName,
                "message" to message,
                "senderRollNumber" to senderRollNumber,
                "senderName" to senderName,
                "mentionsEveryone" to isEveryoneMention,
                "mentionedUsers" to mentionedUsers,
                "participants" to participants.filter { it != senderRollNumber },
                "timestamp" to Timestamp.now(),
                "processed" to false,
                "notificationId" to notificationId
            )
            
            // Store notification in Firestore
            FirebaseFirestore.getInstance()
                .collection("notifications")
                .document(notificationId)
                .set(notificationData)
                .await()
            
            Log.d(TAG, "Notification data saved to Firestore with ID: $notificationId")
            
            // Also immediately process this notification for any users that might be active on this device
            withContext(Dispatchers.Main) {
                processNotificationLocally(notificationData)
                
                // Also directly show notification for current device if enabled
                val messagingService = ChatMessagingService()
                val sessionManager = SessionManager(context)
                val currentUserId = sessionManager.fetchUserId()
                
                // Only show if it's not from the current user
                if (senderRollNumber != currentUserId && participants.contains(currentUserId)) {
                    val isCurrentUserMentioned = mentionedUsers.contains(currentUserId)
                    
                    val data = mapOf(
                        "message" to message,
                        "senderName" to senderName,
                        "senderId" to senderRollNumber,
                        "groupId" to groupId,
                        "groupName" to groupName,
                        "isTagged" to isCurrentUserMentioned.toString(),
                        "isEveryone" to isEveryoneMention.toString()
                    )
                    
                    if (ChatMessagingService.areNotificationsEnabled(context)) {
                        messagingService.showNotificationFromData(context, data)
                        Log.d(TAG, "Directly showed notification on current device")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending group notification", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error sending notification: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Process a notification immediately for active users
     */
    private fun processNotificationLocally(notification: Map<String, Any>) {
        val currentUserRollNumber = SessionManager(context).fetchUserId()
        if (currentUserRollNumber.isEmpty()) return
        
        // Skip processing if this is from the current user
        val senderRollNumber = notification["senderRollNumber"] as? String ?: ""
        if (senderRollNumber == currentUserRollNumber) return
        
        // Check if this is for the current user
        val type = notification["type"] as? String ?: ""
        
        if (type == "IMPORTANT_MESSAGE") {
            // This is a direct chat important message
            val receiverRollNumber = notification["receiverRollNumber"] as? String ?: ""
            
            // Only process if the current user is the intended recipient
            if (receiverRollNumber != currentUserRollNumber) return
            
            // Get notification details
            val notificationId = notification["notificationId"] as? String ?: UUID.randomUUID().toString()
            
            // Debounce to prevent duplicate notifications
            val now = System.currentTimeMillis()
            val lastProcessed = notificationProcessingTimes[notificationId] ?: 0L
            if (now - lastProcessed < NOTIFICATION_DEBOUNCE_TIME) {
                Log.d(TAG, "Skipping duplicate notification processing: $notificationId")
                return
            }
            
            // Update processing time
            notificationProcessingTimes[notificationId] = now
            
            // Create notification data
            val data = mapOf(
                "message" to (notification["message"] as? String ?: ""),
                "senderName" to (notification["senderName"] as? String ?: "Someone"),
                "senderId" to senderRollNumber,
                "isImportant" to "true"
            )
            
            // Show the notification
            val messagingService = ChatMessagingService()
            messagingService.showDirectMessageNotification(context, data)
            
        } else if (type == "GROUP_MESSAGE") {
            // Group message handling (existing code)
            val participants = notification["participants"] as? List<String> ?: emptyList()
            if (!participants.contains(currentUserRollNumber)) return
            
            // Get notification ID and check for duplicates
            val notificationId = notification["notificationId"] as? String ?: UUID.randomUUID().toString()
            
            // Debounce to prevent duplicate notifications
            val now = System.currentTimeMillis()
            val lastProcessed = notificationProcessingTimes[notificationId] ?: 0L
            if (now - lastProcessed < NOTIFICATION_DEBOUNCE_TIME) {
                Log.d(TAG, "Skipping duplicate notification processing: $notificationId")
                return
            }
            
            // Update processing time
            notificationProcessingTimes[notificationId] = now
            
            // Determine notification type
            val mentionsEveryone = notification["mentionsEveryone"] as? Boolean ?: false
            val mentionedUsers = notification["mentionedUsers"] as? List<String> ?: emptyList()
            val isCurrentUserMentioned = mentionedUsers.contains(currentUserRollNumber)
            
            // Create notification data
            val data = mapOf(
                "message" to (notification["message"] as? String ?: ""),
                "senderName" to (notification["senderName"] as? String ?: "Someone"),
                "senderId" to senderRollNumber,
                "groupId" to (notification["groupId"] as? String ?: ""),
                "groupName" to (notification["groupName"] as? String ?: "Group Chat"),
                "isTagged" to isCurrentUserMentioned.toString(),
                "isEveryone" to mentionsEveryone.toString()
            )
            
            // Check if notifications are enabled
            if (!ChatMessagingService.areNotificationsEnabled(context)) {
                Log.w(TAG, "Cannot show notification - notifications are disabled by user")
                return
            }
            
            // Show the notification
            val messagingService = ChatMessagingService()
            messagingService.showNotificationFromData(context, data)
            Log.d(TAG, "Showing notification for group message with isTagged=${isCurrentUserMentioned}, isEveryone=${mentionsEveryone}")
        } else if (type == "UPDATE_NOTIFICATION") {
            // Process update notification for all users except sender
            val recipients = notification["recipients"] as? List<String> ?: emptyList()
            
            // Check if this user is a recipient
            if (recipients.contains(currentUserRollNumber)) {
                val updateId = notification["updateId"] as? String ?: ""
                val updateTitle = notification["title"] as? String ?: "New Update"
                val message = notification["message"] as? String ?: ""
                val senderName = notification["senderName"] as? String ?: ""
                
                Log.d(TAG, "Processing update notification for user $currentUserRollNumber: '$message'")
                
                // Show notification using ChatMessagingService
                val messagingService = ChatMessagingService()
                
                if (ChatMessagingService.areNotificationsEnabled(context)) {
                    val data = mapOf(
                        "message" to message,
                        "senderName" to senderName,
                        "senderId" to senderRollNumber,
                        "groupId" to "updates", // Use "updates" as a virtual group
                        "groupName" to "Updates"
                    )
                    
                    messagingService.showNotificationFromData(context, data)
                    Log.d(TAG, "Showed update notification on current device")
                }
            }
            
            // Mark notification as processed
            val notificationId = notification["notificationId"] as? String ?: return
            
            try {
                FirebaseFirestore.getInstance()
                    .collection("notifications")
                    .document(notificationId)
                    .update("processed", true)
                    .addOnSuccessListener {
                        Log.d(TAG, "Marked update notification as processed: $notificationId")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to mark update notification as processed: ${e.message}", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating notification status: ${e.message}", e)
            }
        } else if (type == "ATTENDANCE_REJECTED") {
            // Process attendance rejection notification
            val receiverRollNumber = notification["receiverRollNumber"] as? String ?: ""
            
            // Only process if current user is the receiver
            if (receiverRollNumber == currentUserRollNumber) {
                val message = notification["message"] as? String ?: ""
                val senderRollNumber = notification["senderRollNumber"] as? String ?: ""
                val eventName = notification["eventName"] as? String ?: ""
                
                Log.d(TAG, "Processing attendance rejection notification for user $currentUserRollNumber: '$message'")
                
                // Show notification using ChatMessagingService
                val messagingService = ChatMessagingService()
                
                if (ChatMessagingService.areNotificationsEnabled(context)) {
                    val data = mapOf(
                        "message" to message,
                        "senderName" to "Attendance System",
                        "senderId" to senderRollNumber,
                        "groupId" to "attendance",
                        "groupName" to "Attendance"
                    )
                    
                    messagingService.showNotificationFromData(context, data)
                    Log.d(TAG, "Showed attendance rejection notification on current device")
                }
            }
            
            // Mark notification as processed
            val notificationId = notification["notificationId"] as? String ?: return
            
            try {
            FirebaseFirestore.getInstance()
                .collection("notifications")
                .document(notificationId)
                .update("processed", true)
                .addOnSuccessListener {
                        Log.d(TAG, "Marked attendance rejection notification as processed: $notificationId")
                }
                .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to mark attendance rejection notification as processed: ${e.message}", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification status: ${e.message}", e)
            }
        } else {
            // Skip unknown notification types
            return
        }
    }
    
    /**
     * Start listening for notifications for a specific user
     */
    fun startListeningForNotifications(userRollNumber: String) {
        // Don't start if we're already listening
        if (notificationsListener != null) {
            Log.d(TAG, "Already listening for notifications")
            return
        }
        
        Log.d(TAG, "Starting to listen for notifications for user: $userRollNumber")
        
        val db = FirebaseFirestore.getInstance()
        val currentUserRollNumber = userRollNumber
        
        // Listen for unprocessed notifications
        notificationsListener = db.collection("notifications")
            .whereEqualTo("processed", false)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error listening for notifications", e)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    for (document in snapshot.documents) {
                        try {
                            Log.d(TAG, "Processing notification document: ${document.id}")
                            
                            val notification = document.data ?: continue
                            val type = notification["type"] as? String ?: ""
                            
                            // Skip if this is a notification we just generated
                            val notificationId = document.id
                            if (notificationId == lastNotificationId) {
                                Log.d(TAG, "Skipping notification we just created: $notificationId")
                                continue
                            }
                            
                            // Extract sender info
                            val senderRollNumber = notification["senderRollNumber"] as? String ?: ""
                            
                            // Skip notifications from the current user
                            if (senderRollNumber == currentUserRollNumber) {
                                db.collection("notifications").document(document.id)
                                    .update("processed", true)
                                Log.d(TAG, "Skipping notification from current user")
                                continue
                            }
                            
                            // Debounce to prevent duplicate notifications
                            val now = System.currentTimeMillis()
                            val lastProcessed = notificationProcessingTimes[notificationId] ?: 0L
                            if (now - lastProcessed < NOTIFICATION_DEBOUNCE_TIME) {
                                Log.d(TAG, "Debouncing notification: $notificationId")
                                continue
                            }
                            
                            // Update processing time
                            notificationProcessingTimes[notificationId] = now
                            
                            // Process notification based on type
                            if (type == "GROUP_MESSAGE") {
                                val participants = notification["participants"] as? List<String> ?: emptyList()
                                
                                // Skip if the current user is not a participant
                                if (!participants.contains(currentUserRollNumber)) {
                                    continue
                                }
                                
                                // Get additional info for group message
                                val groupId = notification["groupId"] as? String ?: ""
                                val groupName = notification["groupName"] as? String ?: "Group Chat"
                                
                                // Check if this user was specifically mentioned
                                val mentionsEveryone = notification["mentionsEveryone"] as? Boolean ?: false
                                val mentionedUsers = notification["mentionedUsers"] as? List<String> ?: emptyList()
                                val isCurrentUserMentioned = mentionedUsers.contains(currentUserRollNumber)
                                
                                // Get common notification data
                                val message = notification["message"] as? String ?: ""
                                val senderName = notification["senderName"] as? String ?: ""
                                
                                // Show the notification
                                val messagingService = ChatMessagingService()
                                
                                val data = mapOf(
                                    "message" to message,
                                    "senderName" to senderName,
                                    "senderId" to senderRollNumber,
                                    "groupId" to groupId,
                                    "groupName" to groupName,
                                    "isTagged" to isCurrentUserMentioned.toString(),
                                    "isEveryone" to mentionsEveryone.toString()
                                )
                                
                                messagingService.showNotificationFromData(context, data)
                                
                                // Mark notification as processed
                                db.collection("notifications").document(document.id)
                                    .update("processed", true)
                                    .addOnSuccessListener {
                                        Log.d(TAG, "Successfully marked notification as processed: $notificationId")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "Failed to mark notification as processed: ${e.message}", e)
                                    }
                            } else if (type == "DIRECT_MESSAGE") {
                                // Process direct message notification
                                val receiverRollNumber = notification["receiverRollNumber"] as? String ?: ""
                                
                                // Only process if current user is the receiver
                                if (receiverRollNumber == currentUserRollNumber) {
                                    // Get common notification data
                                    val message = notification["message"] as? String ?: ""
                                    val senderName = notification["senderName"] as? String ?: ""
                                    
                                    // Show the notification
                                    val messagingService = ChatMessagingService()
                                    
                                    val data = mapOf(
                                        "message" to message,
                                        "senderName" to senderName,
                                        "senderId" to senderRollNumber
                                    )
                                    
                                    messagingService.showDirectMessageNotification(context, data)
                                    
                                    // Mark notification as processed
                                    db.collection("notifications").document(document.id)
                                        .update("processed", true)
                                        .addOnSuccessListener {
                                            Log.d(TAG, "Successfully marked notification as processed: $notificationId")
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e(TAG, "Failed to mark notification as processed: ${e.message}", e)
                                        }
                                }
                            } else if (type == "UPDATE_NOTIFICATION") {
                                // Process update notification for all users except sender
                                val recipients = notification["recipients"] as? List<String> ?: emptyList()
                                
                                // Check if this user is a recipient
                                if (recipients.contains(currentUserRollNumber)) {
                                    val updateId = notification["updateId"] as? String ?: ""
                                    val updateTitle = notification["title"] as? String ?: "New Update"
                                    val message = notification["message"] as? String ?: ""
                                    val senderName = notification["senderName"] as? String ?: ""
                                    
                                    Log.d(TAG, "Processing update notification for user $currentUserRollNumber: '$message'")
                                    
                                    // Show notification using ChatMessagingService
                                    val messagingService = ChatMessagingService()
                                    
                                    if (ChatMessagingService.areNotificationsEnabled(context)) {
                                        val data = mapOf(
                                            "message" to message,
                                            "senderName" to senderName,
                                            "senderId" to senderRollNumber,
                                            "groupId" to "updates", // Use "updates" as a virtual group
                                            "groupName" to "Updates"
                                        )
                                        
                                        messagingService.showNotificationFromData(context, data)
                                        Log.d(TAG, "Showed update notification on current device")
                                    }
                                }
                                
                                // Mark notification as processed
                                db.collection("notifications").document(document.id)
                                    .update("processed", true)
                                    .addOnSuccessListener {
                                        Log.d(TAG, "Marked update notification as processed: ${document.id}")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "Failed to mark update notification as processed: ${e.message}", e)
                                    }
                            } else if (type == "ATTENDANCE_REJECTED") {
                                // Process attendance rejection notification
                                val receiverRollNumber = notification["receiverRollNumber"] as? String ?: ""
                                
                                // Only process if current user is the receiver
                                if (receiverRollNumber == currentUserRollNumber) {
                                    val message = notification["message"] as? String ?: ""
                                    val senderRollNumber = notification["senderRollNumber"] as? String ?: ""
                                    val eventName = notification["eventName"] as? String ?: ""
                                    
                                    Log.d(TAG, "Processing attendance rejection notification for user $currentUserRollNumber: '$message'")
                                    
                                    // Show notification using ChatMessagingService
                                    val messagingService = ChatMessagingService()
                                    
                                    if (ChatMessagingService.areNotificationsEnabled(context)) {
                                        val data = mapOf(
                                            "message" to message,
                                            "senderName" to "Attendance System",
                                            "senderId" to senderRollNumber,
                                            "groupId" to "attendance",
                                            "groupName" to "Attendance"
                                        )
                                        
                                        messagingService.showNotificationFromData(context, data)
                                        Log.d(TAG, "Showed attendance rejection notification on current device")
                                    }
                                }
                                
                                // Mark notification as processed
                                db.collection("notifications").document(document.id)
                                    .update("processed", true)
                                    .addOnSuccessListener {
                                        Log.d(TAG, "Marked attendance rejection notification as processed: ${document.id}")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "Failed to mark attendance rejection notification as processed: ${e.message}", e)
                                    }
                            } else {
                                // Skip unknown notification types
                                continue
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing notification", e)
                        }
                    }
                }
            }
            
        Log.d(TAG, "Notification listener started successfully")
    }
    
    /**
     * Stop listening for notifications
     */
    fun stopListeningForNotifications() {
        if (notificationsListener != null) {
            Log.d(TAG, "Stopping notification listener")
            notificationsListener?.remove()
            notificationsListener = null
        }
    }
    
    /**
     * Helper method to add color highlighting to messages with mentions
     * Should be called before displaying messages in the UI
     */
    fun highlightMentionsInMessage(message: String, currentUserRoll: String): CharSequence {
        // This is a simplified version - in a real app, you'd use a SpannableString
        // to apply formatting. This demonstrates the logic of detection.
        
        val highlightedMessage = message
            .replace("@everyone", "⚠️ @everyone")
            .replace("@$currentUserRoll", "⚠️ @$currentUserRoll")
        
        return highlightedMessage
    }
    
    /**
     * Send a notification for a direct message to a user
     */
    fun sendMessageNotification(
        userToken: String,
        senderRollNumber: String,
        message: String,
        receiverRollNumber: String,
        isGroupMessage: Boolean,
        groupId: String,
        senderIdParam: String
    ) {
        try {
            Log.d(TAG, "Preparing to send direct message notification from $senderRollNumber to $receiverRollNumber")
            
            // First fetch the sender's real name from Firestore
            FirebaseFirestore.getInstance().collection("users").document(senderRollNumber)
                .get()
                .addOnSuccessListener { document ->
                    val senderName = if (document != null && document.exists()) {
                        document.getString("name") ?: senderRollNumber
                    } else {
                        senderRollNumber
                    }
                    
                    Log.d(TAG, "Sending direct message notification with sender name: $senderName")
                    
                    // Create a notification entry in Firestore
                    val notificationId = UUID.randomUUID().toString()
                    lastNotificationId = notificationId
                    
                    val notificationData = hashMapOf(
                        "type" to "DIRECT_MESSAGE",
                        "message" to message,
                        "senderName" to senderName,
                        "senderRollNumber" to senderRollNumber,
                        "receiverRollNumber" to receiverRollNumber,
                        "timestamp" to Timestamp.now(),
                        "processed" to false,
                        "notificationId" to notificationId
                    )
                    
                    // Add group info if this is a group message
                    if (isGroupMessage && groupId.isNotEmpty()) {
                        notificationData["groupId"] = groupId
                        notificationData["type"] = "GROUP_MESSAGE"
                    }
                    
                    // Store notification in Firestore
                    FirebaseFirestore.getInstance()
                        .collection("notifications")
                        .document(notificationId)
                        .set(notificationData)
                        .addOnSuccessListener {
                            Log.d(TAG, "Direct message notification data saved with ID: $notificationId")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error saving direct message notification data", e)
                        }
                    
                    // Also directly show notification for current device if recipient is active here
                    val sessionManager = SessionManager(context)
                    val currentUserId = sessionManager.fetchUserId()
                    
                    // If recipient is on this device and it's not from the current user
                    if (receiverRollNumber == currentUserId && senderRollNumber != currentUserId) {
                        val messagingService = ChatMessagingService()
                        
                        if (ChatMessagingService.areNotificationsEnabled(context)) {
                            // Prepare notification data
                            val data = mapOf(
                                "message" to message,
                                "senderName" to senderName,
                                "senderId" to senderRollNumber
                            )
                            
                            // Show the notification
                            messagingService.showDirectMessageNotification(context, data)
                            Log.d(TAG, "Directly showed notification on current device")
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error fetching sender name", e)
                    // Fallback to using roll number if there's an error
                    sendNotificationWithFallbackName(senderRollNumber, message, receiverRollNumber, isGroupMessage, groupId)
                }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending direct message notification", e)
        }
    }
    
    /**
     * Fallback method to send notification with roll number as name
     */
    private fun sendNotificationWithFallbackName(
        senderRollNumber: String,
        message: String,
        receiverRollNumber: String,
        isGroupMessage: Boolean,
        groupId: String
    ) {
        try {
            // Create a notification entry in Firestore
            val notificationId = UUID.randomUUID().toString()
            lastNotificationId = notificationId
            
            val notificationData = hashMapOf(
                "type" to "DIRECT_MESSAGE",
                "message" to message,
                "senderName" to senderRollNumber, // Using roll number as fallback
                "senderRollNumber" to senderRollNumber,
                "receiverRollNumber" to receiverRollNumber,
                "timestamp" to Timestamp.now(),
                "processed" to false,
                "notificationId" to notificationId
            )
            
            // Add group info if this is a group message
            if (isGroupMessage && groupId.isNotEmpty()) {
                notificationData["groupId"] = groupId
                notificationData["type"] = "GROUP_MESSAGE"
            }
            
            // Store notification in Firestore
            FirebaseFirestore.getInstance()
                .collection("notifications")
                .document(notificationId)
                .set(notificationData)
                .addOnSuccessListener {
                    Log.d(TAG, "Direct message notification data saved with ID: $notificationId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error saving direct message notification data", e)
                }
            
            // Also try to directly show notification
            val sessionManager = SessionManager(context)
            val currentUserId = sessionManager.fetchUserId()
            
            if (receiverRollNumber == currentUserId && senderRollNumber != currentUserId) {
                val messagingService = ChatMessagingService()
                
                if (ChatMessagingService.areNotificationsEnabled(context)) {
                    val data = mapOf(
                        "message" to message,
                        "senderName" to senderRollNumber,
                        "senderId" to senderRollNumber
                    )
                    
                    messagingService.showDirectMessageNotification(context, data)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in fallback notification", e)
        }
    }

    /**
     * Send a notification for a group message
     */
    fun sendGroupMessageNotification(
        groupId: String,
        groupName: String,
        message: String,
        senderRollNumber: String,
        senderName: String? = null
    ) {
        try {
            Log.d(TAG, "Preparing group notification: groupId=$groupId, from=$senderRollNumber")
            
            // If sender name is provided, use it directly; otherwise fetch from Firestore
            if (senderName != null && senderName.isNotEmpty()) {
                continueWithGroupNotification(groupId, groupName, message, senderRollNumber, senderName)
            } else {
                // Fetch sender name from Firestore
                FirebaseFirestore.getInstance().collection("users").document(senderRollNumber)
                    .get()
                    .addOnSuccessListener { document ->
                        val realSenderName = if (document != null && document.exists()) {
                            document.getString("name") ?: senderRollNumber
                        } else {
                            senderRollNumber
                        }
                        
                        continueWithGroupNotification(groupId, groupName, message, senderRollNumber, realSenderName)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error fetching sender name for group message", e)
                        // Use roll number as fallback
                        continueWithGroupNotification(groupId, groupName, message, senderRollNumber, senderRollNumber)
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending group notification", e)
        }
    }
    
    /**
     * Continue sending group notification with sender name
     */
    private fun continueWithGroupNotification(
        groupId: String,
        groupName: String,
        message: String,
        senderRollNumber: String,
        senderName: String
    ) {
        try {
            Log.d(TAG, "Sending group notification with sender name: $senderName")
            
            // Get the group information to determine participants and mentions
            val db = FirebaseFirestore.getInstance()
            db.collection("groups").document(groupId)
                .get()
                .addOnSuccessListener { groupDocument ->
                    if (groupDocument == null || !groupDocument.exists()) {
                        Log.e(TAG, "Group not found: $groupId")
                        return@addOnSuccessListener
                    }
                    
                    // Get participants list
                    val participants = groupDocument.get("participants") as? List<String> ?: emptyList()
                    
                    if (participants.isEmpty()) {
                        Log.w(TAG, "No participants in group: $groupId")
                        return@addOnSuccessListener
                    }
                    
                    // Extract mention information from message
                    val mentionsEveryone = message.contains("@everyone", ignoreCase = true)
                    val mentionedUsers = extractMentionedUsers(message)
                    
                    // Create notification record in Firestore
                    val notificationId = UUID.randomUUID().toString()
                    lastNotificationId = notificationId
                    
                    val notificationData = hashMapOf(
                        "type" to "GROUP_MESSAGE",
                        "groupId" to groupId,
                        "groupName" to groupName,
                        "message" to message,
                        "senderName" to senderName,
                        "senderRollNumber" to senderRollNumber,
                        "participants" to participants,
                        "mentionsEveryone" to mentionsEveryone,
                        "mentionedUsers" to mentionedUsers,
                        "timestamp" to Timestamp.now(),
                        "processed" to false,
                        "notificationId" to notificationId
                    )
                    
                    // Store the notification in Firestore for processing by client apps
                    db.collection("notifications")
                        .document(notificationId)
                        .set(notificationData)
                        .addOnSuccessListener {
                            Log.d(TAG, "Group notification saved to Firestore with ID: $notificationId")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error saving group notification to Firestore", e)
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error fetching group data for notification", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in continueWithGroupNotification", e)
        }
    }
    
    /**
     * Extract mentioned users from a message text (roll numbers only)
     */
    private fun extractMentionedUsers(text: String): List<String> {
        val mentions = mutableListOf<String>()
        // Pattern for roll numbers in the format of @2301MC51 or similar formats
        val pattern = Pattern.compile("@([0-9A-Z]{8}|[0-9]{4}[A-Z]{2}[0-9]{2})\\b")
        val matcher = pattern.matcher(text)
        
        while (matcher.find()) {
            val mentionedRollNumber = matcher.group(1)
            if (mentionedRollNumber != null) {
                mentions.add(mentionedRollNumber)
                Log.d(TAG, "Found mention of roll number: $mentionedRollNumber")
            }
        }
        
        return mentions
    }

    /**
     * Send a notification to all users about a new update
     * This is specifically for updates, not regular group messages
     */
    suspend fun sendUpdateNotification(
        updateId: String,
        updateTitle: String,
        updateMessage: String,
        senderRollNumber: String,
        senderName: String,
        allUserIds: List<String>
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting update notification process for ${allUserIds.size} users: '$updateMessage'")
            
            // Skip if there are no users to notify
            if (allUserIds.isEmpty()) {
                Log.d(TAG, "No users to notify about update")
                return@withContext
            }
            
            // Create a notification data document in Firestore
            val notificationId = UUID.randomUUID().toString()
            lastNotificationId = notificationId
            
            // Construct the message with the update ID for proper routing
            val fullMessage = "[UPDATE:$updateId] $updateMessage"
            
            val notificationData = hashMapOf(
                "type" to "UPDATE_NOTIFICATION", // Special type for updates
                "updateId" to updateId,
                "title" to updateTitle,
                "message" to fullMessage,
                "senderRollNumber" to senderRollNumber,
                "senderName" to senderName,
                // Include all users except the sender as recipients
                "recipients" to allUserIds.filter { it != senderRollNumber },
                "timestamp" to Timestamp.now(),
                "processed" to false,
                "notificationId" to notificationId
            )
            
            // Store notification in Firestore
            try {
                FirebaseFirestore.getInstance()
                    .collection("notifications")
                    .document(notificationId)
                    .set(notificationData)
                    .await()
                    
                Log.d(TAG, "Update notification saved to Firestore with ID: $notificationId for ${allUserIds.size} users")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving notification to Firestore: ${e.message}", e)
                // Continue anyway to try direct notification
            }
            
            // Also immediately process this notification for current device
            withContext(Dispatchers.Main) {
                try {
                    val messagingService = ChatMessagingService()
                    val sessionManager = SessionManager(context)
                    val currentUserId = sessionManager.fetchUserId()
                    
                    // Only show if it's not from the current user
                    if (senderRollNumber != currentUserId) {
                        Log.d(TAG, "Showing update notification directly on device for user: $currentUserId")
                        
                        // Prepare the notification data with the format ChatMessagingService expects
                        val data = mapOf(
                            "message" to fullMessage,
                            "senderName" to senderName,
                            "senderId" to senderRollNumber,
                            "groupId" to "updates", // Use "updates" as a virtual group
                            "groupName" to "Updates"
                        )
                        
                        if (ChatMessagingService.areNotificationsEnabled(context)) {
                            try {
                                messagingService.showNotificationFromData(context, data)
                                Log.d(TAG, "Successfully showed update notification on current device")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error showing notification: ${e.message}", e)
                            }
                        } else {
                            Log.d(TAG, "Notifications are disabled by user settings")
                        }
                    } else {
                        Log.d(TAG, "Not showing notification since it's from the current user")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in Main dispatcher showing notification: ${e.message}", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending update notification: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error sending update notification: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Send a notification for attendance rejection
     */
    suspend fun sendNotification(
        message: String,
        senderRollNumber: String,
        receiverRollNumber: String,
        type: String,
        eventName: String
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Preparing to send attendance notification from $senderRollNumber to $receiverRollNumber")
            
            // Create a notification entry in Firestore
            val notificationId = UUID.randomUUID().toString()
            lastNotificationId = notificationId
            
            val notificationData = hashMapOf(
                "type" to type,
                "message" to message,
                "senderRollNumber" to senderRollNumber,
                "receiverRollNumber" to receiverRollNumber,
                "eventName" to eventName,
                "timestamp" to Timestamp.now(),
                "processed" to false,
                "notificationId" to notificationId
            )
            
            // Store notification in Firestore
            FirebaseFirestore.getInstance()
                .collection("notifications")
                .document(notificationId)
                .set(notificationData)
                .await()
            
            Log.d(TAG, "Attendance notification data saved with ID: $notificationId")
            
            // Also directly show notification for current device if recipient is active here
            val sessionManager = SessionManager(context)
            val currentUserId = sessionManager.fetchUserId()
            
            // If recipient is on this device and it's not from the current user
            if (receiverRollNumber == currentUserId && senderRollNumber != currentUserId) {
                val messagingService = ChatMessagingService()
                
                if (ChatMessagingService.areNotificationsEnabled(context)) {
                    // Prepare notification data
                    val data = mapOf(
                        "message" to message,
                        "senderName" to "Attendance System",
                        "senderId" to senderRollNumber
                    )
                    
                    // Show the notification
                    messagingService.showDirectMessageNotification(context, data)
                    Log.d(TAG, "Directly showed notification on current device")
                } else {
                    Log.d(TAG, "Notifications are disabled by user settings")
                }
            } else {
                Log.d(TAG, "Not showing notification - recipient not on this device or sender is current user")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending attendance notification", e)
        }
    }
} 