package com.phad.chatapp.repositories

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.phad.chatapp.models.Message
import com.phad.chatapp.models.User

class MessageRepository {
    private val TAG = "MessageRepository"
    private val db = FirebaseFirestore.getInstance()
    private val userRepository = UserRepository()
    
    // Firestore path constants
    companion object {
        const val GROUPS_COLLECTION = "groups"
        const val MESSAGES_COLLECTION = "messages"
    }
    
    /**
     * Get messages for a specific group
     */
    fun getMessagesForGroup(groupId: String): Query {
        Log.d(TAG, "Getting messages for group: $groupId")
        return db.collection(GROUPS_COLLECTION)
            .document(groupId)
            .collection(MESSAGES_COLLECTION)
            .orderBy("timestamp", Query.Direction.ASCENDING)
    }
    
    /**
     * Send a new message to a group
     */
    fun sendMessage(message: Message): Task<Void> {
        Log.d(TAG, "Sending message: ${message.text}")
        
        val messageData = HashMap<String, Any>()
        messageData["text"] = message.text
        messageData["sender"] = message.sender
        messageData["timestamp"] = message.timestamp
        messageData["read"] = message.read
        
        return db.collection(GROUPS_COLLECTION)
            .document(message.id)
            .collection(MESSAGES_COLLECTION)
            .document()
            .set(messageData)
            .addOnSuccessListener { 
                Log.d(TAG, "Message successfully sent") 
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error sending message: ${e.message}", e)
            }
    }
    
    /**
     * Delete a specific message
     */
    fun deleteMessage(groupId: String, messageId: String): Task<Void> {
        Log.d(TAG, "Deleting message $messageId from group $groupId")
        return db.collection(GROUPS_COLLECTION)
            .document(groupId)
            .collection(MESSAGES_COLLECTION)
            .document(messageId)
            .delete()
    }
    
    /**
     * Get latest message for a group (for preview)
     */
    fun getLatestMessageForGroup(groupId: String): Task<QuerySnapshot> {
        Log.d(TAG, "Getting latest message for group: $groupId")
        return db.collection(GROUPS_COLLECTION)
            .document(groupId)
            .collection(MESSAGES_COLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
    }
    
    /**
     * Create a new message and send it to a group
     */
    fun createMessage(text: String, senderId: String, groupId: String): Task<Void> {
        val message = HashMap<String, Any>()
        message["text"] = text
        message["sender"] = senderId
        message["timestamp"] = Timestamp.now()
        message["read"] = false

        return db.collection(GROUPS_COLLECTION)
            .document(groupId)
            .collection(MESSAGES_COLLECTION)
            .document()
            .set(message)
            .addOnSuccessListener {
                Log.d(TAG, "Message created successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error creating message", e)
            }
    }

    fun markMessageAsRead(groupId: String, messageId: String): Task<Void> {
        return db.collection(GROUPS_COLLECTION)
            .document(groupId)
            .collection(MESSAGES_COLLECTION)
            .document(messageId)
            .update("read", true)
            .addOnSuccessListener {
                Log.d(TAG, "Message marked as read")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error marking message as read", e)
            }
    }
} 