package com.phad.chatapp.repositories

import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.phad.chatapp.models.User
import kotlin.Exception
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UserRepository {
    private val TAG = "UserRepository"
    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")
    
    fun getAllUsers(): Task<List<User>> {
        val taskCompletionSource = TaskCompletionSource<List<User>>()
        
        usersCollection.get()
            .addOnSuccessListener { result ->
                val allUsers = mutableListOf<User>()
                for (doc in result.documents) {
                    try {
                        val userType = doc.getString("userType") ?: ""
                        val user = User(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            email = doc.getString("email") ?: "",
                            rollNumber = doc.getString("rollNumber") ?: doc.id,
                            userType = userType,
                            description = doc.getString("description") ?: "",
                            contactNumber = doc.getString("contactNumber") ?: ""
                        ).apply {
                            // Safely set year value from any type
                            setYear(doc.get("year"))
                        }
                        allUsers.add(user)
                        Log.d(TAG, "Parsed User: Name=${user.name}, RollNumber=${user.rollNumber}, UserType=${user.userType}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing user document: ${doc.id}", e)
                    }
                }
                Log.d(TAG, "All users loaded from 'users' collection: ${allUsers.size}")
                taskCompletionSource.setResult(allUsers)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to get users from 'users' collection", exception)
                taskCompletionSource.setException(exception)
            }
        
        return taskCompletionSource.task
    }
    
    fun getUserById(userId: String): Task<User> {
        val taskCompletionSource = TaskCompletionSource<User>()
        Log.d(TAG, "Looking up user with ID: $userId in 'users' collection")
        
        // Directly query the 'users' collection
        usersCollection.document(userId).get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    try {
                        val userType = documentSnapshot.getString("userType") ?: "Student"
                        val user = createUserFromDocument(documentSnapshot, userType)
                        taskCompletionSource.setResult(user)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing user $userId from 'users' collection", e)
                        taskCompletionSource.setException(e)
                    }
                } else {
                    Log.w(TAG, "User $userId not found in 'users' collection")
                    // Return a default User instead of null
                    taskCompletionSource.setResult(User(id = userId, rollNumber = userId))
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error querying 'users' collection for $userId", e)
                taskCompletionSource.setException(e)
            }
        
        return taskCompletionSource.task
    }

    private fun createUserFromDocument(documentSnapshot: com.google.firebase.firestore.DocumentSnapshot, userType: String): User {
        try {
            // Extract user data safely
            val data = documentSnapshot.data ?: mapOf<String, Any>()
            
            return User(
                id = documentSnapshot.id,
                name = data["name"] as? String ?: "",
                email = data["email"] as? String ?: "",
                rollNumber = data["rollNumber"] as? String ?: documentSnapshot.id,
                userType = userType,
                description = data["description"] as? String ?: "",
                contactNumber = data["contact_number"] as? String ?: "",
                profileImageUrl = data["profile_image_url"] as? String
            ).apply {
                // Safely set year value from any type
                setYear(data["year"])
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating user from document: ${e.message}", e)
            // Return a default User as fallback
            return User(
                id = documentSnapshot.id,
            ) 
        }
    }

    /**
     * Get a user by roll number
     */
    suspend fun getUserByRollNumber(rollNumber: String): Result<User?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting user by roll number: $rollNumber")
            
            val querySnapshot = usersCollection
                .whereEqualTo("rollNumber", rollNumber)
                .limit(1)
                .get()
                .await()
            
            if (querySnapshot.isEmpty) {
                Log.d(TAG, "No user found with roll number: $rollNumber")
                return@withContext Result.success(null)
            }
            
            val user = querySnapshot.documents[0].toObject(User::class.java)
            Log.d(TAG, "Found user: $user")
            
            return@withContext Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user by roll number", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Update user's reference image URL
     */
    suspend fun updateReferenceImageUrl(userId: String, imageUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating reference image URL for user: $userId")
            
            usersCollection.document(userId)
                .update("referenceImageUrl", imageUrl)
                .await()
            
            Log.d(TAG, "Reference image URL updated successfully")
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating reference image URL", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Check if a user exists by roll number
     */
    suspend fun checkUserExists(rollNumber: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking if user exists with roll number: $rollNumber")
            
            val querySnapshot = usersCollection
                .whereEqualTo("rollNumber", rollNumber)
                .limit(1)
                .get()
                .await()
            
            val exists = !querySnapshot.isEmpty
            Log.d(TAG, "User exists: $exists")
            
            return@withContext Result.success(exists)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if user exists", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Get all users with admin role
     */
    suspend fun getAdminUsers(): Result<List<User>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting admin users")
            
            val querySnapshot = usersCollection
                .whereEqualTo("isAdmin", true)
                .get()
                .await()
            
            val adminUsers = querySnapshot.documents.mapNotNull { 
                it.toObject(User::class.java) 
            }
            
            Log.d(TAG, "Found ${adminUsers.size} admin users")
            return@withContext Result.success(adminUsers)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting admin users", e)
            return@withContext Result.failure(e)
        }
    }
} 