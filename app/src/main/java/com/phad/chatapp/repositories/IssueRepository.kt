package com.phad.chatapp.repositories

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.phad.chatapp.models.Issue
import com.phad.chatapp.models.IssueStatus
import com.phad.chatapp.utils.CloudinaryHelper
import com.phad.chatapp.utils.HelpConstants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID

class IssueRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val cloudinaryHelper: CloudinaryHelper
) {
    private val issuesCollection = firestore.collection("issues")

    suspend fun submitIssue(issue: Issue, imageUri: Uri?): Result<Unit> {
        return try {
            var photoUrl: String? = null
            var photoPublicId: String? = null

            if (imageUri != null) {
                // Upload to Cloudinary
                val uploadResult = cloudinaryHelper.uploadImage(imageUri, folder = "issues")
                photoUrl = uploadResult.url
                photoPublicId = uploadResult.publicId
            }

            val issueId = UUID.randomUUID().toString()
            val issueToSave = issue.copy(
                id = issueId,
                photoUrl = photoUrl,
                photoPublicId = photoPublicId
            )

            issuesCollection.document(issueId).set(issueToSave).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun closeIssue(
        issueId: String,
        closerRollNumber: String,
        closerName: String,
        comment: String
    ): Result<Unit> {
        return try {
            withTimeout(HelpConstants.NETWORK_TIMEOUT_MS) { // 15 seconds timeout for poor network
                firestore.runTransaction { transaction ->
                    val docRef = issuesCollection.document(issueId)
                    val snapshot = transaction.get(docRef)
                    
                    if (snapshot.getString("status") == IssueStatus.CLOSED.name) {
                        throw Exception("This issue has already been closed.")
                    }
                    
                    transaction.update(
                        docRef, 
                        "status", IssueStatus.CLOSED.name,
                        "resolvedByRollNumber", closerRollNumber,
                        "resolvedByName", closerName,
                        "resolveComment", comment.ifBlank { null },
                        "resolveTimestamp", System.currentTimeMillis()
                    )
                }.await()
            }
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("Network timeout: Please check your internet connection and try again."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveIssue(
        issueId: String,
        adminRollNumber: String,
        adminName: String,
        comment: String
    ): Result<Unit> {
        return try {
            withTimeout(HelpConstants.NETWORK_TIMEOUT_MS) { // 15 seconds timeout
                firestore.runTransaction { transaction ->
                    val docRef = issuesCollection.document(issueId)
                    val snapshot = transaction.get(docRef)

                    if (snapshot.getString("status") == IssueStatus.CLOSED.name) {
                        throw Exception("This issue has already been resolved.")
                    }

                    transaction.update(
                        docRef,
                        "status", IssueStatus.CLOSED.name,
                        "resolvedByRollNumber", adminRollNumber,
                        "resolvedByName", adminName,
                        "resolveComment", comment.ifBlank { null },
                        "resolveTimestamp", System.currentTimeMillis()
                    )
                }.await()
            }
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("Network timeout: Please check your internet connection and try again."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getIssuesForUser(rollNumber: String): Flow<List<Issue>> = callbackFlow {
        val subscription = issuesCollection
            .whereEqualTo("rollNumber", rollNumber)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val issues = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Issue::class.java)
                    }.sortedByDescending { it.timestamp }
                    trySend(issues).isSuccess
                }
            }

        awaitClose { subscription.remove() }
    }

    fun getAdminOpenIssues(adminUserType: String, adminWings: List<String>): Flow<List<Issue>> = callbackFlow {
        val targetAddresses = mutableListOf<String>()
        if (adminUserType == "admin" || adminUserType == "super_admin" || adminUserType == "Admin" || adminUserType == "Super Admin") {
            targetAddresses.add("NSS Admin")
            targetAddresses.add("Technical Team")
            targetAddresses.add("General Secretary")
        }
        
        adminWings.forEach { wing ->
            val normalizedWing = if (wing.endsWith("Wing")) wing else "$wing Wing"
            targetAddresses.add("Wing Subcoord - $normalizedWing")
        }

        if (targetAddresses.isEmpty()) {
            trySend(emptyList()).isSuccess
            awaitClose { }
            return@callbackFlow
        }

        // We only fetch OPEN issues from DB to avoid downloading thousands of closed issues.
        // We filter by targetAddresses locally, preventing ANY composite index requirements!
        val subscription = issuesCollection
            .whereEqualTo("status", IssueStatus.OPEN.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val issues = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Issue::class.java)
                    }.filter { it.addressedTo in targetAddresses }
                     .sortedByDescending { it.timestamp }
                     
                    trySend(issues).isSuccess
                }
            }
        awaitClose { subscription.remove() }
    }
}
