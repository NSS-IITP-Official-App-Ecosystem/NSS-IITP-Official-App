package com.phad.chatapp.repositories

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.phad.chatapp.models.UnreadMessage
import com.phad.chatapp.utils.SessionManager
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

class UnreadMessageRepository(private val context: Context) {
    private val TAG = "UnreadMessageRepository"
    private val db = FirebaseFirestore.getInstance()
    private val sessionManager = SessionManager(context)
    private val userNameCache = ConcurrentHashMap<String, String>()
    
    /**
     * Fetch all unread messages for the current user
     */
    suspend fun fetchAllUnreadMessages(): Pair<List<UnreadMessage>, List<UnreadMessage>> {
        val currentUserId = sessionManager.fetchUserId()
        if (currentUserId.isEmpty()) {
            return Pair(emptyList(), emptyList())
        }
        
        // Get unread direct messages
        val directMessages = fetchUnreadDirectMessages(currentUserId)
        
        // Get unread group messages
        val groupMessages = fetchUnreadGroupMessages(currentUserId)
        
        return Pair(directMessages, groupMessages)
    }
    
    /**
     * Fetch unread direct messages from user_conversations collection
     */
    private suspend fun fetchUnreadDirectMessages(currentUserId: String): List<UnreadMessage> {
        val unreadMessages = mutableListOf<UnreadMessage>()
        
        try {
            // Get all chat partners by querying recent contacts or existing direct chat references
            val chatPartnerIds = getChatPartners(currentUserId)
                
            // For each chat partner
            for (chatPartnerId in chatPartnerIds) {
                try {
                    // Get the latest message
                    val latestMessageDocs = db.collection("user_conversations")
                        .document(currentUserId)
                        .collection(chatPartnerId)
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(1)
                        .get()
                        .await()
                    
                    if (!latestMessageDocs.isEmpty) {
                        val latestDoc = latestMessageDocs.documents[0]
                        
                        // Check if the message is unread and not sent by current user
                        val senderId = latestDoc.getString("sender") ?: ""
                        val messageText = latestDoc.getString("text") ?: ""
                        
                        // Extract read map
                        val readMap = latestDoc.get("read") as? Map<*, *>
                        val currentUserReadStatus = readMap?.get(currentUserId) as? Boolean ?: true
                        
                        // Only include if message is unread and sent by other user
                        if (!currentUserReadStatus && senderId != currentUserId) {
                            // Get sender name
                            val senderName = getUserName(senderId)
                            
                            unreadMessages.add(
                                UnreadMessage(
                                    id = latestDoc.id,
                                    senderName = senderName,
                                    messageText = messageText,
                                    timestamp = latestDoc.getTimestamp("timestamp") ?: com.google.firebase.Timestamp.now(),
                                    isGroupMessage = false,
                                    chatPartnerId = chatPartnerId,
                                    lastMessageSender = senderId
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching messages for chat partner $chatPartnerId", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching direct unread messages", e)
        }
        
        return unreadMessages
    }
    
    /**
     * Get a list of chat partner IDs for the current user by looking at recent chats
     */
    private suspend fun getChatPartners(userId: String): List<String> {
        val chatPartners = mutableListOf<String>()
        
        try {
            // Query conversation_metadata collection for the current user
            val metadataSnapshot = db.collection("conversation_metadata")
                .whereArrayContains("participants", userId)
                .get()
                .await()
                
            val partners = metadataSnapshot.documents
                .mapNotNull { doc ->
                    val participants = doc.get("participants") as? List<*>
                    participants?.firstOrNull { it != userId }?.toString()
                }
                
            if (partners.isNotEmpty()) {
                chatPartners.addAll(partners.distinct())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chat partners", e)
        }
        
        return chatPartners
    }
    
    /**
     * Fetch unread group messages from groups collection
     */
    private suspend fun fetchUnreadGroupMessages(currentUserId: String): List<UnreadMessage> {
        val unreadMessages = mutableListOf<UnreadMessage>()
        
        try {
            // First get all groups the user is a member of
            val groupsQuerySnapshot = db.collection("groups")
                .whereArrayContains("participants", currentUserId)
                .get()
                .await()
            
            // For each group
            for (groupDoc in groupsQuerySnapshot.documents) {
                val groupId = groupDoc.id
                val groupName = groupDoc.getString("name") ?: "Unknown Group"
                
                try {
                    // Get the latest message
                    val latestMessageDocs = db.collection("groups")
                        .document(groupId)
                        .collection("messages")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(1)
                        .get()
                        .await()
                    
                    if (!latestMessageDocs.isEmpty) {
                        val latestDoc = latestMessageDocs.documents[0]
                        
                        // Check if the message is unread
                        val senderId = latestDoc.getString("sender") ?: ""
                        val messageText = latestDoc.getString("text") ?: ""
                        
                        // Extract read map
                        val readMap = latestDoc.get("read") as? Map<*, *>
                        val currentUserReadStatus = readMap?.get(currentUserId) as? Boolean ?: true
                        
                        // Only include if message is unread and not sent by current user
                        if (!currentUserReadStatus && senderId != currentUserId) {
                            // Get sender name
                            val senderName = getUserName(senderId)
                            
                            unreadMessages.add(
                                UnreadMessage(
                                    id = latestDoc.id,
                                    senderName = groupName,
                                    messageText = "$senderName: $messageText",
                                    timestamp = latestDoc.getTimestamp("timestamp") ?: com.google.firebase.Timestamp.now(),
                                    isGroupMessage = true,
                                    groupId = groupId,
                                    lastMessageSender = senderId
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching messages for group $groupId", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching group unread messages", e)
        }
        
        return unreadMessages
    }
    
    /**
     * Get user name from cache or Firestore
     */
    private suspend fun getUserName(userId: String): String {
        // Check cache first
        if (userNameCache.containsKey(userId)) {
            return userNameCache[userId] ?: userId
        }
        
        // Fetch from Firestore
        try {
            val userDoc = db.collection("users").document(userId).get().await()
            val name = userDoc.getString("name")
            
            if (name != null) {
                userNameCache[userId] = name
                return name
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user name", e)
        }
        
        return userId
    }
} 