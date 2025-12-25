package com.phad.chatapp.utils

import com.phad.chatapp.activities.GroupChatActivity

import com.phad.chatapp.activities.ChatActivity

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
// Imports removed
import com.phad.chatapp.R
import com.google.firebase.firestore.FirebaseFirestore
import java.util.regex.Pattern

class ChatMessagingService : FirebaseMessagingService() {
    private val TAG = "ChatMessagingService"
    
    companion object {
        private const val CHANNEL_ID = "chat_notifications"
        private const val CHANNEL_NAME = "Chat Notifications"
        private const val CHANNEL_ID_MENTIONS = "mentions_notifications"
        private const val CHANNEL_NAME_MENTIONS = "Mentions Notifications"
        private const val NOTIFICATION_ID_BASE = 1000

        /**
         * Check if notification permissions are granted
         */
        fun areNotificationsEnabled(context: Context): Boolean {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: $token")
        
        // Store the token in Firestore for the current user
        val sessionManager = SessionManager(applicationContext)
        val userId = sessionManager.fetchUserId()
        
        if (userId.isNotEmpty()) {
            updateTokenInFirestore(userId, token)
        } else {
            Log.w(TAG, "Cannot save FCM token: User not logged in")
        }
    }
    
    private fun updateTokenInFirestore(userId: String, token: String) {
        // Update the user's FCM token in Firestore
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .update("fcmToken", token)
            .addOnSuccessListener {
                Log.d(TAG, "FCM token updated in Firestore for user $userId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update FCM token in Firestore", e)
            }
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Received FCM message: ${remoteMessage.data}")
        
        // Extract notification data
        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            Log.d(TAG, "Processing FCM message with data: $data")
            
            // Don't show notification for messages from the current user
            val sessionManager = SessionManager(applicationContext)
            val currentUserId = sessionManager.fetchUserId()
            val senderId = data["senderId"] ?: ""
            
            if (senderId == currentUserId) {
                Log.d(TAG, "Ignoring notification for message sent by current user")
                return
            }
            
            // Process the notification data
            showNotificationFromData(applicationContext, data)
        }
    }
    
    /**
     * Public method to show a notification from notification data.
     * Can be called directly from other components or from FCM.
     */
    fun showNotificationFromData(context: Context, data: Map<String, String>) {
        try {
            // Check if notification permission is granted
            if (!areNotificationsEnabled(context)) {
                Log.e(TAG, "Cannot show notification: Notifications are disabled by the user")
                return
            }
            
            val messageText = data["message"] ?: ""
            val senderName = data["senderName"] ?: "Someone"
            val senderId = data["senderId"] ?: ""
            val groupId = data["groupId"] ?: ""
            val groupName = data["groupName"] ?: "Group Chat"
            val isTagged = data["isTagged"]?.toBoolean() ?: false
            val isEveryone = data["isEveryone"]?.toBoolean() ?: false
            
            Log.d(TAG, "Preparing notification: isTagged=$isTagged, isEveryone=$isEveryone, from=$senderName, message=$messageText")
            
            // Don't show notification for messages from the current user if context allows session check
            if (context.applicationContext is android.app.Application) {
                val sessionManager = SessionManager(context)
                val currentUserId = sessionManager.fetchUserId()
                
                if (senderId == currentUserId) {
                    Log.d(TAG, "Ignoring notification for message sent by current user")
                    return
                }
            }
            
            if (groupId.isNotEmpty()) {
                showGroupMessageNotification(context, groupId, groupName, senderName, messageText, isTagged, isEveryone)
            } else {
                // This is a direct message
                val isImportant = data["isImportant"]?.toBoolean() ?: false
                showDirectMessageNotification(context, mapOf(
                    "message" to messageText,
                    "senderName" to senderName,
                    "senderId" to senderId,
                    "isImportant" to isImportant.toString()
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification: ${e.message}", e)
        }
    }
    
    /**
     * Show a notification for a direct message
     */
    fun showDirectMessageNotification(context: Context, data: Map<String, String>) {
        // Check if notification permission is granted
        if (!areNotificationsEnabled(context)) {
            Log.e(TAG, "Cannot show notification: Notifications are disabled by the user")
            return
        }
        
        val messageText = data["message"] ?: ""
        val senderName = data["senderName"] ?: "Someone"
        val senderId = data["senderId"] ?: ""
        
        // Check for special tags
        val isImportant = messageText.contains("@important", ignoreCase = true) || data["isImportant"]?.toBoolean() ?: false
        val containsEveryone = messageText.contains("@everyone", ignoreCase = true)
        val containsRollNumberMention = Pattern.compile("@([0-9A-Z]{8}|[0-9]{4}[A-Z]{2}[0-9]{2})\\b").matcher(messageText).find()
        
        val notificationType = when {
            isImportant -> "important"
            containsEveryone -> "everyone"
            containsRollNumberMention -> "mention"
            else -> "regular"
        }
        
        Log.d(TAG, "Showing direct message notification: type=$notificationType, from=$senderName (ID: $senderId)")
        
        // Don't show notification for messages from the current user if context allows session check
        if (context.applicationContext is android.app.Application) {
            val sessionManager = SessionManager(context)
            val currentUserId = sessionManager.fetchUserId()
            
            if (senderId == currentUserId) {
                Log.d(TAG, "Ignoring notification for message sent by current user")
                return
            }
        }
        
        try {
            // Get the current user's roll number
            val sessionManager = SessionManager(context)
            val currentUserRollNumber = sessionManager.fetchUserId()
            
            // Create intent to open the direct chat when notification is tapped
            val intent = Intent(context, ChatActivity::class.java).apply {
                putExtra("otherUserRollNumber", senderId)
                putExtra("otherUserName", senderName)
                putExtra("currentUserRollNumber", currentUserRollNumber)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context, 
                senderId.hashCode(), 
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            // Create a notification with appropriate styling based on the message type
            val notificationTitle = when {
                isImportant -> "‼️ Important message from $senderName"
                containsEveryone -> "👥 $senderName mentioned everyone"
                containsRollNumberMention -> "🔔 $senderName mentioned users"
                else -> "Message from $senderName"
            }
            
            // Format the message text based on type
            val notificationText = when {
                isImportant -> "‼️ " + messageText.replace("@important", "", ignoreCase = true).trim()
                else -> messageText
            }
            
            // Create a notification channel if needed
            createNotificationChannelsIfNeeded(context)
            
            // Choose appropriate channel based on message type
            val channelId = if (isImportant || containsEveryone || containsRollNumberMention) CHANNEL_ID_MENTIONS else CHANNEL_ID
            
            val notificationBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Use appropriate icon
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
                .setPriority(if (isImportant || containsEveryone || containsRollNumberMention) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            
            // Set appropriate color based on message type
            val notificationColor = when {
                isImportant -> Color.parseColor("#FF9900") // Neon Orange for important
                containsEveryone -> Color.parseColor("#FFC107") // Amber/Yellow for @everyone
                containsRollNumberMention -> Color.parseColor("#4CAF50") // Green for roll number mentions
                else -> Color.parseColor("#2196F3") // Blue for regular
            }
            
            notificationBuilder
                .setColorized(true)
                .setColor(notificationColor)
            
            // Use different notification ID for each sender to allow for multiple notifications
            val notificationId = NOTIFICATION_ID_BASE + senderId.hashCode()
            
            try {
                with(NotificationManagerCompat.from(context)) {
                    notify(notificationId, notificationBuilder.build())
                    Log.d(TAG, "Direct message notification displayed from $senderName (Type: $notificationType)")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to show notification: Permission not granted", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing notification: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing direct message notification: ${e.message}", e)
        }
    }
    
    private fun showGroupMessageNotification(
        context: Context,
        groupId: String,
        groupName: String,
        senderName: String,
        message: String,
        isTagged: Boolean,
        isEveryone: Boolean
    ) {
        try {
            // Check if this is an update notification (using the prefix we added in HomeFragment)
            val isUpdate = message.startsWith("[UPDATE:")
            
            // Extract update ID if this is an update notification
            var updateId = ""
            val updatedMessage: String
            
            if (isUpdate) {
                // Parse out the update ID and clean up the message
                val updatePattern = Pattern.compile("\\[UPDATE:([^\\]]+)\\]\\s*(.+)")
                val matcher = updatePattern.matcher(message)
                
                if (matcher.find() && matcher.groupCount() >= 2) {
                    updateId = matcher.group(1) ?: ""
                    updatedMessage = matcher.group(2) ?: message
                } else {
                    updatedMessage = message.replaceFirst(Regex("\\[UPDATE:[^\\]]+\\]\\s*"), "")
                }
            } else {
                updatedMessage = message
            }
            
            // Check if the message contains tags
            val isImportant = updatedMessage.contains("@important", ignoreCase = true)
            val containsEveryone = updatedMessage.contains("@everyone", ignoreCase = true)
            val containsRollNumberMention = Pattern.compile("@([0-9A-Z]{8}|[0-9]{4}[A-Z]{2}[0-9]{2})\\b").matcher(updatedMessage).find()
            
            // Determine the notification type based on tags
            val usesEveryone = containsEveryone || isEveryone
            val usesMention = containsRollNumberMention || isTagged
            
            Log.d(TAG, "Showing ${if (isUpdate) "update" else "group"} notification: group=$groupName, from=$senderName, tagged=$usesMention, everyone=$usesEveryone, important=$isImportant")
            
            // Create intent to open the appropriate activity when notification is tapped
            val intent = if (isUpdate && updateId.isNotEmpty()) {
                // For updates, we want to open the UpdateDetailActivity
                Intent(context, com.phad.chatapp.activities.UpdateDetailActivity::class.java).apply {
                    putExtra("UPDATE_ID", updateId)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            } else {
                // For regular group messages, open the GroupChatActivity
                Intent(context, GroupChatActivity::class.java).apply {
                    putExtra("GROUP_ID", groupId)
                    putExtra("GROUP_NAME", groupName)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context, 
                if (isUpdate) updateId.hashCode() else groupId.hashCode(), 
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            // Create a notification with appropriate styling based on the message type
            val notificationTitle = when {
                isUpdate -> "📢 New Update from $senderName"
                isImportant -> "‼️ Important message from $senderName in $groupName"
                usesEveryone -> "$senderName mentioned everyone in $groupName"
                usesMention -> "$senderName mentioned users in $groupName"
                else -> "$senderName in $groupName"
            }
            
            // Format the message text for important messages
            val notificationText = if (isImportant) {
                "‼️ " + updatedMessage.replace("@important", "", ignoreCase = true).trim()
            } else {
                updatedMessage
            }
            
            // Create notification channels if needed
            createNotificationChannelsIfNeeded(context)
            
            // Choose appropriate channel based on importance/mention type
            val channelId = if (isImportant || usesMention || usesEveryone) CHANNEL_ID_MENTIONS else CHANNEL_ID
            
            val notificationBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Use appropriate icon
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
                .setPriority(if (isUpdate || isImportant || usesMention || usesEveryone) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            
            // Set appropriate color based on message type
            val notificationColor = when {
                isUpdate -> Color.parseColor("#673AB7") // Purple for updates
                isImportant -> Color.parseColor("#FF9900") // Neon Orange for important
                usesEveryone -> Color.parseColor("#FFC107") // Amber/Yellow for @everyone
                usesMention -> Color.parseColor("#4CAF50") // Green for roll number mentions
                else -> Color.parseColor("#2196F3") // Blue for regular
            }
            
            notificationBuilder
                .setColorized(true)
                .setColor(notificationColor)
            
            // Use different notification ID for each group to allow for multiple notifications
            val notificationId = if (isUpdate) {
                NOTIFICATION_ID_BASE + updateId.hashCode()
            } else {
                NOTIFICATION_ID_BASE + groupId.hashCode()
            }
            
            try {
                with(NotificationManagerCompat.from(context)) {
                    notify(notificationId, notificationBuilder.build())
                    Log.d(TAG, "${if (isUpdate) "Update" else "Group"} notification displayed for ${
                        when {
                            isUpdate -> "update"
                            isImportant -> "important message"
                            usesEveryone -> "@everyone mention"
                            usesMention -> "user mention"
                            else -> "message"
                        }
                    } in group $groupName")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to show notification: Permission not granted", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing notification: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing notification: ${e.message}", e)
        }
    }
    
    /**
     * Create notification channels for the app
     * Public method that can be called from other components
     */
    fun createNotificationChannels() {
        createNotificationChannelsIfNeeded(applicationContext)
    }
    
    private fun createNotificationChannelsIfNeeded(context: Context) {
        // Create the notification channels for Android Oreo and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            try {
                // Check if regular channel exists already
                if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                    // Regular chat messages channel (default importance)
                    val regularChannel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Notifications for regular chat messages"
                        enableVibration(true)
                    }
                    notificationManager.createNotificationChannel(regularChannel)
                    Log.d(TAG, "Regular notification channel created")
                }
                
                // Check if mentions channel exists already
                if (notificationManager.getNotificationChannel(CHANNEL_ID_MENTIONS) == null) {
                    // Mentions channel (high importance)
                    val mentionsChannel = NotificationChannel(CHANNEL_ID_MENTIONS, CHANNEL_NAME_MENTIONS, NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Notifications for messages where you are mentioned"
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 250, 250, 250) // Custom vibration pattern
                        lightColor = Color.GREEN
                        enableLights(true)
                    }
                    notificationManager.createNotificationChannel(mentionsChannel)
                    Log.d(TAG, "Mentions notification channel created")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating notification channels: ${e.message}", e)
            }
        }
    }
} 