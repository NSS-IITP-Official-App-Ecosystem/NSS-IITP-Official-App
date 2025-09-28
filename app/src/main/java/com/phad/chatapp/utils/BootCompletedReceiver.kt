package com.phad.chatapp.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver that runs when the device boots to restore notification capabilities
 */
class BootCompletedReceiver : BroadcastReceiver() {
    private val TAG = "BootCompletedReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed - restoring notification capabilities")
            
            // Create an instance of ChatMessagingService to create notification channels
            try {
                val messagingService = ChatMessagingService()
                messagingService.createNotificationChannels()
                Log.d(TAG, "Successfully recreated notification channels")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to recreate notification channels: ${e.message}", e)
            }
            
            // Get the current user ID
            val sessionManager = SessionManager(context)
            val currentUserId = sessionManager.fetchUserId()
            
            // Only start listening for notifications if user is logged in
            if (currentUserId.isNotEmpty()) {
                Log.d(TAG, "Starting notification listener for user $currentUserId")
                val notificationHelper = NotificationHelper(context.applicationContext)
                notificationHelper.startListeningForNotifications(currentUserId)
            } else {
                Log.d(TAG, "User not logged in, skipping notification listener startup")
            }
        }
    }
} 