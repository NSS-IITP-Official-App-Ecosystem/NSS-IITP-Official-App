package com.phad.chatapp.services

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.models.Group
import kotlinx.coroutines.tasks.await
import java.util.Date

class SubjectAssignmentService {
    private val TAG = "SubjectAssignmentService"
    private val db = FirebaseFirestore.getInstance()
    
    private suspend fun getAdminUsers(): List<String> {
        val adminUsers = mutableSetOf<String>()
        try {
            val admins1 = db.collection("users").whereEqualTo("userType", "admin").get().await()
            for (doc in admins1.documents) {
                adminUsers.add(doc.id)
            }
            val admins2 = db.collection("users").whereEqualTo("userType", "Admin").get().await()
            for (doc in admins2.documents) {
                adminUsers.add(doc.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching admin users from firestore", e)
        }
        if (adminUsers.isEmpty()) {
            return FALLBACK_ADMIN_USERS
        }
        return adminUsers.toList()
    }
    
    companion object {
        private val FALLBACK_ADMIN_USERS = listOf("2301MC51", "2301CS16")
    }
    
    /**
     * Sync all subject assignments to groups subcollection
     */
    suspend fun syncSubjectAssignmentsToGroups() {
        try {
            Log.d(TAG, "Starting sync of subject assignments to groups")
            val adminUsers = getAdminUsers()
            
            // Get initial data
            val subjectGroupsSnapshot = db.collection("groups").whereEqualTo("subject", true).get().await()
            val subjectGroups = subjectGroupsSnapshot.documents.mapNotNull { it.toObject(Group::class.java)?.apply { id = it.id } }
            val assignmentsSnapshot = db.collection("subjectAssignments").get().await()
            val assignmentsMap = assignmentsSnapshot.documents.associateBy({ it.id }, { it })

            // PHASE 1: Push pending changes from groups to subjectAssignments
            for (group in subjectGroups) {
                if (group.pendingAdd.isNotEmpty() || group.pendingRemove.isNotEmpty()) {
                    val parts = group.id.split(" & ")
                    if (parts.size >= 2) {
                        val parentDocId = parts[0]
                        val subjectCode = parts[1]
                        val parentDoc = assignmentsMap[parentDocId]
                        if (parentDoc != null) {
                            val assignments = (parentDoc.get("assignments") as? List<Map<String, Any>>)?.toMutableList() ?: mutableListOf()
                            
                            // Add new participants with details from Student collection
                            for (rollNo in group.pendingAdd) {
                                if (assignments.none { it["volunteerRollNo"] == rollNo && it["subjectCode"] == subjectCode }) {
                                    val (studentName, nssGroup) = getStudentDetails(rollNo)
                                    val newAssignment = mutableMapOf<String, Any>(
                                        "dayIndex" to (0..6).random(),
                                        "slotIndex" to (0..7).random(),
                                        "subjectCode" to subjectCode,
                                        "volunteerGroup" to nssGroup,
                                        "volunteerName" to studentName,
                                        "volunteerRollNo" to rollNo
                                    )
                                    assignments.add(newAssignment)
                                }
                            }
                            
                            // Remove participants
                            val toRemove = group.pendingRemove.toSet()
                            val filteredAssignments = assignments.filterNot {
                                it["subjectCode"] == subjectCode && it["volunteerRollNo"] in toRemove
                            }
                            
                            // Update Firestore
                            db.collection("subjectAssignments").document(parentDocId)
                                .update("assignments", filteredAssignments).await()
                            
                            // Clear pendingAdd/pendingRemove and set update=false
                            db.collection("groups").document(group.id).update(
                                mapOf(
                                    "pendingAdd" to emptyList<String>(),
                                    "pendingRemove" to emptyList<String>(),
                                    "update" to false
                                )
                            ).await()
                            Log.d(TAG, "Synced group ${group.id} to subjectAssignments (add: ${group.pendingAdd}, remove: ${group.pendingRemove})")
                        }
                    }
                }
            }
            
            // RE-FETCH assignments after updates for reverse sync
            Log.d(TAG, "Re-fetching subject assignments for reverse sync")
            val updatedAssignmentsSnapshot = db.collection("subjectAssignments").get().await()
            val updatedAssignmentsMap = updatedAssignmentsSnapshot.documents.associateBy({ it.id }, { it })

            // PHASE 2: Reverse sync - update all subject groups from subjectAssignments
            for (group in subjectGroups) {
                val parts = group.id.split(" & ")
                if (parts.size >= 2) {
                    val parentDocId = parts[0]
                    val subjectCode = parts[1]
                    val parentDoc = updatedAssignmentsMap[parentDocId] // Use updated map
                    if (parentDoc != null) {
                        val assignments = parentDoc.get("assignments") as? List<Map<String, Any>> ?: emptyList()
                        val subjectAssignments = assignments.filter { it["subjectCode"] == subjectCode }
                        val participants = subjectAssignments.mapNotNull { it["volunteerRollNo"] as? String }.toMutableList()
                        participants.addAll(adminUsers)
                        val uniqueParticipants = participants.distinct()
                        
                        // Only update if participants list has changed
                        if (group.participants.sorted() != uniqueParticipants.sorted()) {
                            val messagingPermissions = uniqueParticipants.associateWith { true }.toMutableMap()
                            db.collection("groups").document(group.id).update(
                                mapOf(
                                    "participants" to uniqueParticipants,
                                    "messagingPermissions" to messagingPermissions
                                )
                            ).await()
                            Log.d(TAG, "Reverse synced group ${group.id} from subjectAssignments")
                        }
                    }
                }
            }
            
            // Also create new subject groups for any new assignments
            for (assignmentDoc in updatedAssignmentsSnapshot.documents) { // Use updated snapshot
                val parentDocId = assignmentDoc.id
                val assignments = assignmentDoc.get("assignments") as? List<Map<String, Any>>
                if (assignments != null) {
                    val subjectGroupsInAssignment = assignments.groupBy { it["subjectCode"] as? String ?: "" }
                    for ((subjectCode, subjectAssignments) in subjectGroupsInAssignment) {
                        if (subjectCode.isNotEmpty()) {
                            val groupId = "$parentDocId & $subjectCode"
                            val groupDoc = db.collection("groups").document(groupId).get().await()
                            if (!groupDoc.exists()) {
                                createOrUpdateSubjectGroup(groupId, subjectCode, subjectAssignments, adminUsers)
                            }
                        }
                    }
                }
            }
            
            Log.d(TAG, "Successfully completed two-way sync for subject assignments and groups")
        } catch (e: Exception) {
            Log.e(TAG, "Error in two-way sync for subject assignments and groups", e)
        }
    }
    
    /**
     * Create or update a subject-based group
     */
    private suspend fun createOrUpdateSubjectGroup(
        groupId: String,
        subjectCode: String,
        assignments: List<Map<String, Any>>,
        adminUsers: List<String>
    ) {
        try {
            // Extract volunteer roll numbers from assignments
            val participants = assignments.mapNotNull { it["volunteerRollNo"] as? String }.toMutableList()
            
            // Add admin users
            participants.addAll(adminUsers)
            
            // Remove duplicates
            val uniqueParticipants = participants.distinct()
            
            // Create messaging permissions map
            val messagingPermissions = uniqueParticipants.associateWith { true }.toMutableMap()
            
            // Create group object
            val group = Group(
                id = groupId,
                name = groupId, // Use groupId as name
                description = "Subject group for $subjectCode",
                participants = uniqueParticipants,
                admins = adminUsers,
                createdBy = adminUsers.firstOrNull() ?: "",
                messagingPermissions = messagingPermissions,
                createdAt = Timestamp.now(),
                subject = true,
                update = false // Always false when created from subjectAssignments
            )
            
            // Save to groups subcollection
            db.collection("groups").document(groupId).set(group).await()
            
            Log.d(TAG, "Created/updated subject group: $groupId with ${uniqueParticipants.size} participants")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating/updating subject group: $groupId", e)
        }
    }
    
    /**
     * Get all subject-based groups
     */
    suspend fun getSubjectGroups(): List<Group> {
        return try {
            val groupsSnapshot = db.collection("groups").whereEqualTo("subject", true).get().await()
            val groups = mutableListOf<Group>()
            
            for (doc in groupsSnapshot.documents) {
                val group = doc.toObject(Group::class.java)
                if (group != null) {
                    group.id = doc.id
                    groups.add(group)
                }
            }
            
            groups
        } catch (e: Exception) {
            Log.e(TAG, "Error getting subject groups", e)
            emptyList()
        }
    }
    
    /**
     * Update participant in subject group and sync back to subjectAssignments
     */
    suspend fun updateSubjectGroupParticipant(
        groupId: String,
        oldRollNo: String?,
        newRollNo: String,
        action: String // "add" or "remove"
    ) {
        try {
            // Update the group
            val groupRef = db.collection("groups").document(groupId)
            val groupDoc = groupRef.get().await()
            if (groupDoc.exists()) {
                val group = groupDoc.toObject(Group::class.java)
                if (group != null) {
                    val updatedParticipants = group.participants.toMutableList()
                    val updatedPendingAdd = group.pendingAdd.toMutableList()
                    val updatedPendingRemove = group.pendingRemove.toMutableList()
                    when (action) {
                        "add" -> {
                            if (!updatedParticipants.contains(newRollNo)) {
                                updatedParticipants.add(newRollNo)
                            }
                            if (!updatedPendingAdd.contains(newRollNo)) {
                                updatedPendingAdd.add(newRollNo)
                            }
                        }
                        "remove" -> {
                            updatedParticipants.remove(oldRollNo)
                            if (oldRollNo != null && !updatedPendingRemove.contains(oldRollNo)) {
                                updatedPendingRemove.add(oldRollNo)
                            }
                        }
                    }
                    val updates = mutableMapOf<String, Any>(
                        "participants" to updatedParticipants,
                        "pendingAdd" to updatedPendingAdd,
                        "pendingRemove" to updatedPendingRemove,
                        "update" to true,
                        "messagingPermissions" to updatedParticipants.associateWith { true }
                    )
                    groupRef.update(updates).await()
                    // Sync back to subjectAssignments
                    syncGroupToSubjectAssignment(groupId, updatedParticipants)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating subject group participant", e)
        }
    }
    
    /**
     * Sync group changes back to subjectAssignments collection
     */
    private suspend fun syncGroupToSubjectAssignment(groupId: String, participants: List<String>) {
        try {
            // Extract parent document ID and subject code from group ID
            val parts = groupId.split(" ")
            if (parts.size >= 2) {
                val parentDocId = parts[0]
                val subjectCode = parts[1]
                
                // Get the parent document
                val parentDoc = db.collection("subjectAssignments").document(parentDocId).get().await()
                if (parentDoc.exists()) {
                    val assignments = parentDoc.get("assignments") as? List<Map<String, Any>> ?: emptyList()
                    
                    val adminUsers = getAdminUsers()
                    // Filter out admin users from participants
                    val nonAdminParticipants = participants.filter { it !in adminUsers }
                    
                    // Update assignments for this subject code
                    val updatedAssignments = assignments.map { assignment ->
                        if (assignment["subjectCode"] == subjectCode) {
                            // Find a participant for this subject (simple assignment)
                            val participant = nonAdminParticipants.firstOrNull()
                            assignment.toMutableMap().apply {
                                put("volunteerRollNo", participant ?: "")
                                put("volunteerName", "") // You might want to fetch this from users collection
                            }
                        } else {
                            assignment
                        }
                    }
                    
                    // Update the parent document
                    db.collection("subjectAssignments").document(parentDocId)
                        .update("assignments", updatedAssignments).await()
                    
                    Log.d(TAG, "Synced group $groupId back to subjectAssignments")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing group to subjectAssignments", e)
        }
    }

    // Add participant to group (for subject groups)
    suspend fun addParticipantToGroup(groupId: String, rollNo: String) {
        val groupRef = db.collection("groups").document(groupId)
        val groupDoc = groupRef.get().await()
        if (groupDoc.exists()) {
            val group = groupDoc.toObject(Group::class.java)
            if (group != null) {
                val updatedParticipants = group.participants.toMutableList()
                val updatedPendingAdd = group.pendingAdd.toMutableList()
                if (!updatedParticipants.contains(rollNo)) {
                    updatedParticipants.add(rollNo)
                }
                if (!updatedPendingAdd.contains(rollNo)) {
                    updatedPendingAdd.add(rollNo)
                }
                groupRef.update(
                    mapOf(
                        "participants" to updatedParticipants,
                        "pendingAdd" to updatedPendingAdd,
                        "update" to true
                    )
                ).await()
            }
        }
    }

    // Remove participant from group (for subject groups)
    suspend fun removeParticipantFromGroup(groupId: String, rollNo: String) {
        val groupRef = db.collection("groups").document(groupId)
        val groupDoc = groupRef.get().await()
        if (groupDoc.exists()) {
            val group = groupDoc.toObject(Group::class.java)
            if (group != null) {
                val updatedParticipants = group.participants.toMutableList()
                val updatedPendingRemove = group.pendingRemove.toMutableList()
                updatedParticipants.remove(rollNo)
                if (!updatedPendingRemove.contains(rollNo)) {
                    updatedPendingRemove.add(rollNo)
                }
                groupRef.update(
                    mapOf(
                        "participants" to updatedParticipants,
                        "pendingRemove" to updatedPendingRemove,
                        "update" to true
                    )
                ).await()
            }
        }
    }

    private suspend fun getStudentDetails(rollNo: String): Pair<String, String> {
        var studentName = ""
        var nssGroup = ""
        try {
            val studentDoc = db.collection("Student").document(rollNo).get().await()
            if (studentDoc.exists()) {
                studentName = studentDoc.getString("Name") ?: ""
                nssGroup = studentDoc.getString("NSS_gro") ?: ""
            } else {
                Log.w(TAG, "No document found in Student collection for rollNo: $rollNo")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching student details for $rollNo", e)
        }
        return Pair(studentName, nssGroup)
    }
} 