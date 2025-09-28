package com.phad.chatapp.repositories

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.phad.chatapp.models.Group
import kotlinx.coroutines.tasks.await
import java.util.Date

class GroupRepository {
    private val TAG = "GroupRepository"
    private val db = FirebaseFirestore.getInstance()
    private val groupsCollection = db.collection("groups")
    
    fun getCommunityGroups(): Task<QuerySnapshot> {
        Log.d(TAG, "Getting community groups")
        return groupsCollection.get()
    }
    
    /**
     * Get all groups (both regular and subject-based) for a user
     */
    suspend fun getAllGroupsForUser(userId: String): List<Group> {
        return try {
            val groupsSnapshot = groupsCollection.get().await()
            val groups = mutableListOf<Group>()
            
            for (doc in groupsSnapshot.documents) {
                val group = doc.toObject(Group::class.java)
                if (group != null && group.participants.contains(userId)) {
                    group.id = doc.id
                    groups.add(group)
                }
            }
            
            Log.d(TAG, "Found ${groups.size} groups for user $userId")
            groups
        } catch (e: Exception) {
            Log.e(TAG, "Error getting groups for user $userId", e)
            emptyList()
        }
    }
    
    /**
     * Get regular groups (non-subject-based) for a user
     */
    suspend fun getRegularGroupsForUser(userId: String): List<Group> {
        return try {
            val groupsSnapshot = groupsCollection.get().await()
            val groups = mutableListOf<Group>()
            
            for (doc in groupsSnapshot.documents) {
                val group = doc.toObject(Group::class.java)
                // Regular groups don't have space in their ID
                if (group != null && group.participants.contains(userId) && !doc.id.contains(" ")) {
                    group.id = doc.id
                    groups.add(group)
                }
            }
            
            Log.d(TAG, "Found ${groups.size} regular groups for user $userId")
            groups
        } catch (e: Exception) {
            Log.e(TAG, "Error getting regular groups for user $userId", e)
            emptyList()
        }
    }
    
    /**
     * Get subject-based groups for a user
     */
    suspend fun getSubjectGroupsForUser(userId: String): List<Group> {
        return try {
            val groupsSnapshot = groupsCollection.get().await()
            val groups = mutableListOf<Group>()
            
            for (doc in groupsSnapshot.documents) {
                val group = doc.toObject(Group::class.java)
                // Subject groups have space in their ID
                if (group != null && group.participants.contains(userId) && doc.id.contains(" ")) {
                    group.id = doc.id
                    groups.add(group)
                }
            }
            
            Log.d(TAG, "Found ${groups.size} subject groups for user $userId")
            groups
        } catch (e: Exception) {
            Log.e(TAG, "Error getting subject groups for user $userId", e)
            emptyList()
        }
    }
    
    /**
     * Check if a group is subject-based
     */
    fun isSubjectGroup(groupId: String): Boolean {
        return groupId.contains(" ")
    }
    
    fun createGroup(group: Group): Task<Void> {
        Log.d(TAG, "Creating new group: ${group.name}")
        
        // Create a document ID based on the group name
        // Replace spaces with underscores and remove special characters
        val baseDocId = group.name.trim()
            .replace("\\s+".toRegex(), "_")
            .replace("[^a-zA-Z0-9_]".toRegex(), "")
            .take(30) // Limit length to avoid very long IDs
        
        // Add a timestamp suffix to ensure uniqueness
        val timestamp = System.currentTimeMillis()
        val docId = "${baseDocId}_$timestamp"
        
        val newGroupRef = groupsCollection.document(docId)
        val groupWithId = group.copy(id = docId)
        
        // Set the timestamp for creation time if not already set
        if (groupWithId.createdAt == null) {
            groupWithId.createdAt = Timestamp.now()
        }
        
        Log.d(TAG, "Setting document ID to: $docId")
        return newGroupRef.set(groupWithId)
    }
    
    fun deleteGroup(groupId: String): Task<Void> {
        Log.d(TAG, "Deleting group: $groupId")
        return groupsCollection.document(groupId).delete()
    }
    
    fun getGroupById(groupId: String): Task<com.google.firebase.firestore.DocumentSnapshot> {
        Log.d(TAG, "Getting group by ID: $groupId")
        return groupsCollection.document(groupId).get()
    }
    
    fun getGroupsByParticipant(userId: String): Task<QuerySnapshot> {
        Log.d(TAG, "Getting groups for participant: $userId")
        return groupsCollection.whereArrayContains("participants", userId).get()
    }
} 