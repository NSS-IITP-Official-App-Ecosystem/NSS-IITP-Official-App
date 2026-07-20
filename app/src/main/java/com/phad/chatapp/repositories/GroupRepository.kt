package com.phad.chatapp.repositories

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.phad.chatapp.models.Group
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
            val groupsSnapshot = groupsCollection.whereArrayContains("participants", userId).get().await()
            val groups = mutableListOf<Group>()
            
            for (doc in groupsSnapshot.documents) {
                val group = doc.toObject(Group::class.java)
                if (group != null) {
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
            val groupsSnapshot = groupsCollection.whereArrayContains("participants", userId).get().await()
            val groups = mutableListOf<Group>()
            
            for (doc in groupsSnapshot.documents) {
                val group = doc.toObject(Group::class.java)
                // Regular groups don't have space in their ID
                if (group != null && !doc.id.contains(" ")) {
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
            val groupsSnapshot = groupsCollection.whereArrayContains("participants", userId).get().await()
            val groups = mutableListOf<Group>()
            
            for (doc in groupsSnapshot.documents) {
                val group = doc.toObject(Group::class.java)
                // Subject groups have space in their ID
                if (group != null && doc.id.contains(" ")) {
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
    
    suspend fun isGroupNameExists(groupName: String): Boolean {
        return try {
            val querySnapshot = groupsCollection.whereEqualTo("name", groupName).get().await()
            !querySnapshot.isEmpty
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if group name exists: $groupName", e)
            false
        }
    }

    suspend fun syncScheduleGroup(presetName: String, scheduledVolunteers: List<String>, adminId: String) {
        try {
            Log.d(TAG, "Syncing schedule group for preset: $presetName")
            val querySnapshot = groupsCollection.whereEqualTo("name", presetName).get().await()
            
            if (querySnapshot.isEmpty) {
                // Group doesn't exist, create it
                val finalParticipants = scheduledVolunteers.toMutableSet().apply {
                    add(adminId)
                }.toList()
                
                val newGroup = Group(
                    name = presetName,
                    description = "Auto-generated group for $presetName classes",
                    participants = finalParticipants,
                    admins = listOf(adminId),
                    createdBy = adminId
                )
                newGroup.initializeMessagingPermissions()
                
                // Create a deterministic document ID for class groups
                val docId = presetName.trim().replace("\\s+".toRegex(), "_").replace("[^a-zA-Z0-9_]".toRegex(), "")
                val newGroupRef = groupsCollection.document(docId)
                val groupWithId = newGroup.copy(id = docId, createdAt = Timestamp.now())
                
                newGroupRef.set(groupWithId).await()
                Log.d(TAG, "Successfully created new auto-group for $presetName")
            } else {
                // Group exists, update it based on Option B (Replace with exact schedule volunteers + all admins)
                val doc = querySnapshot.documents.first()
                val existingGroup = doc.toObject(Group::class.java)
                if (existingGroup != null) {
                    val existingAdmins = existingGroup.admins
                    val existingCreator = existingGroup.createdBy
                    
                    // Final participants: scheduled volunteers + existing admins + creator + current admin
                    val finalParticipants = scheduledVolunteers.toMutableSet().apply {
                        addAll(existingAdmins)
                        if (existingCreator.isNotEmpty()) {
                            add(existingCreator)
                        }
                        add(adminId) 
                    }.toList()
                    
                    val finalAdmins = existingAdmins.toMutableSet().apply {
                        add(adminId)
                    }.toList()
                    
                    doc.reference.update(
                        mapOf(
                            "participants" to finalParticipants,
                            "admins" to finalAdmins
                        )
                    ).await()
                    Log.d(TAG, "Successfully updated auto-group for $presetName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing schedule group for $presetName", e)
        }
    }

    suspend fun autoSyncScheduleGroups(scheduleData: Map<String, List<String>>) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val currentEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: ""
            val adminRollNo = if (currentEmail.contains("_")) {
                currentEmail.substringBefore("@").split("_").last().uppercase()
            } else {
                "SystemAdmin"
            }
            
            // Filter out invalid presets first
            val validScheduleData = scheduleData.filter { it.key != "Unknown" && it.value.isNotEmpty() }
            if (validScheduleData.isEmpty()) return@withContext
            
            Log.d(TAG, "Fetching targeted groups for batch auto-sync...")
            // 1. Efficient DB read: Use whereIn to fetch ONLY the groups we care about.
            // Firestore 'in' queries support up to 30 values per query, so we chunk them.
            val presetNames = validScheduleData.keys.toList()
            val existingGroupsByName = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
            
            val chunks = presetNames.chunked(30)
            val deferredSnapshots = chunks.map { chunk ->
                async {
                    groupsCollection.whereIn("name", chunk).get().await()
                }
            }
            
            // Wait for all chunked queries to complete concurrently
            for (deferred in deferredSnapshots) {
                val snapshot = deferred.await()
                for (doc in snapshot.documents) {
                    val groupName = doc.getString("name")
                    if (groupName != null) {
                        existingGroupsByName[groupName] = doc
                    }
                }
            }
            
            // 2. Prepare a single Batch write for all updates
            val batch = db.batch()
            var batchCount = 0
            
            for ((presetName, scheduledVolunteers) in validScheduleData) {
                val existingDoc = existingGroupsByName[presetName]
                
                if (existingDoc == null) {
                    // Group doesn't exist, prepare creation
                    val finalParticipants = scheduledVolunteers.toMutableSet().apply {
                        add(adminRollNo)
                    }.toList()
                    
                    val newGroup = Group(
                        name = presetName,
                        description = "Auto-generated group for $presetName classes",
                        participants = finalParticipants,
                        admins = listOf(adminRollNo),
                        createdBy = adminRollNo
                    )
                    newGroup.initializeMessagingPermissions()
                    
                    val docId = presetName.trim().replace("\\s+".toRegex(), "_").replace("[^a-zA-Z0-9_]".toRegex(), "")
                    val newGroupRef = groupsCollection.document(docId)
                    val groupWithId = newGroup.copy(id = docId, createdAt = Timestamp.now())
                    
                    batch.set(newGroupRef, groupWithId)
                    batchCount++
                } else {
                    // Group exists, prepare update
                    val existingGroup = existingDoc.toObject(Group::class.java)
                    if (existingGroup != null) {
                        val existingAdmins = existingGroup.admins
                        val existingCreator = existingGroup.createdBy
                        
                        val finalParticipants = scheduledVolunteers.toMutableSet().apply {
                            addAll(existingAdmins)
                            if (existingCreator.isNotEmpty()) add(existingCreator)
                            add(adminRollNo) 
                        }.toList()
                        
                        val finalAdmins = existingAdmins.toMutableSet().apply {
                            add(adminRollNo)
                        }.toList()
                        
                        batch.update(existingDoc.reference, mapOf(
                            "participants" to finalParticipants,
                            "admins" to finalAdmins,
                            "createdAt" to Timestamp.now()
                        ))
                        batchCount++
                    }
                }
                
                // Firestore batches have a limit of 500 operations. Safely chunk them.
                if (batchCount >= 450) {
                    batch.commit().await()
                    batchCount = 0
                }
            }
            
            // 3. Commit the final batch write (1 Network Call for Writes)
            if (batchCount > 0) {
                batch.commit().await()
                Log.d(TAG, "Successfully committed batch of $batchCount group syncs")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to batch auto-sync schedule chat groups", e)
        }
    }
} 