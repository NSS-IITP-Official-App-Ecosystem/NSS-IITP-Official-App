package com.phad.chatapp.repositories

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Source
import com.phad.chatapp.models.Admin
import com.phad.chatapp.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

class FirestoreRepository {
    private val TAG = "FirestoreRepository"
    private val firestore: FirebaseFirestore
    
    // Collection reference
    private val usersCollection: com.google.firebase.firestore.CollectionReference
    
    // Timeout for Firebase operations
    private val FIREBASE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(15)
    
    init {
        Log.d(TAG, "FirestoreRepository initializing...")
        
        try {
            // Log detailed Firebase info
            val firebaseApp = FirebaseApp.getInstance()
            Log.d(TAG, "Firebase app name: ${firebaseApp.name}")
            Log.d(TAG, "Firebase options: ${firebaseApp.options.applicationId}, storageBucket: ${firebaseApp.options.storageBucket}")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Firebase app info", e)
        }
        
        // Initialize Firestore with persistence enabled
        firestore = try {
            val db = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            db.firestoreSettings = settings
            Log.d(TAG, "Firestore initialized successfully with persistence")
            db
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firestore", e)
            FirebaseFirestore.getInstance() // Fallback to default
        }
        
        // Initialize collection reference
        usersCollection = firestore.collection("users")
        Log.d(TAG, "users collection path: ${usersCollection.path}")
    }
    
    // Method to fetch users with userType "admin" from the users collection (unified)
    suspend fun getAdminUsersOnly(): List<Admin> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Fetching users with userType = admin from users collection")
            
            // Query users collection directly for admin users
            val querySnapshot = usersCollection
                .whereEqualTo("userType", "admin")
                .get(Source.SERVER) // Force server fetch to ensure fresh data
                .await()
            
            Log.d(TAG, "Total admin users found: ${querySnapshot.size()}")
            
            // Convert to Admin objects
            querySnapshot.documents.mapNotNull { document ->
                try {
                    val userData = document.data
                    if (userData != null) {
                        Admin(
                            rollNumber = document.id,
                            name = (userData["name"] as? String) ?: "Unknown",
                            description = (userData["description"] as? String) ?: "",
                            year = when (val yearValue = userData["year"]) {
                                is Number -> yearValue.toInt()
                                is String -> yearValue.toIntOrNull() ?: 0
                                else -> 0
                            },
                            contactNumber = (userData["contactNumber"] as? String) ?: "",
                            email = (userData["email"] as? String) ?: "",
                            userType = (userData["userType"] as? String) ?: "Unknown"
                        ).also {
                            Log.d(TAG, "Added admin user: ${it.name} with roll number: ${it.rollNumber}")
                        }
                    } else {
                        Log.w(TAG, "Document ${document.id} has no data")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing user document ${document.id}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Admin1 users from users collection", e)
            throw e // Propagate error to handle it in the UI layer
        }
    }
    
    // Method to fetch all users for admin, sorted by conversation timestamp
    suspend fun getAllUsersForAdminWithTimestamps(): List<Admin> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Fetching all users for admin with timestamps")
            
            // Get all users except the current user
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            
            Log.d(TAG, "Current user ID for exclusion: $currentUserId")
            
            // Query needs to be fixed - we're not properly excluding the current user
            // and we need to ensure we get users of all types
            val querySnapshot = usersCollection
                .get(Source.SERVER) // Get all users from the server
                .await()
            
            Log.d(TAG, "Total users found in database: ${querySnapshot.size()}")
            
            // Filter out the current user after fetching
            val filteredDocs = if (currentUserId != null) {
                querySnapshot.documents.filter { it.id != currentUserId }
            } else {
                querySnapshot.documents
            }
            
            Log.d(TAG, "After filtering current user, total users: ${filteredDocs.size}")
            
            // Convert to Admin objects
            val userList = filteredDocs.mapNotNull { document ->
                try {
                    val userData = document.data
                    if (userData != null) {
                        // Log full user data for debugging
                        Log.d(TAG, "User data for ${document.id}: $userData")
                        
                        Admin(
                            rollNumber = document.id,
                            name = (userData["name"] as? String) ?: "Unknown",
                            description = (userData["description"] as? String) ?: "",
                            year = when (val yearValue = userData["year"]) {
                                is Number -> yearValue.toInt()
                                is String -> yearValue.toIntOrNull() ?: 0
                                else -> 0
                            },
                            contactNumber = (userData["contactNumber"] as? String) ?: "",
                            email = (userData["email"] as? String) ?: "",
                            userType = (userData["userType"] as? String) ?: "Unknown"
                        ).also {
                            Log.d(TAG, "Added user: ${it.name} with roll number: ${it.rollNumber}, type: ${it.userType}")
                        }
                    } else {
                        Log.w(TAG, "Document ${document.id} has no data")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing user document ${document.id}", e)
                    null
                }
            }
            
            Log.d(TAG, "Processed ${userList.size} users of these types: ${userList.map { it.userType }.distinct()}")
            
            // Pre-fetch timestamps for all users to avoid calling suspend functions inside sorting
            val userTimestamps = mutableMapOf<String, Long>()
            
            // Fetch timestamps from conversations and user_conversations
            for (user in userList) {
                try {
                    val otherUserRollNumber = user.rollNumber
                    // Sort to ensure same conversation ID regardless of order
                    val sortedUsers = listOf(currentUserId ?: "", otherUserRollNumber).sorted()
                    val conversationId = "conv_${sortedUsers[0]}_${sortedUsers[1]}"
                    
                    Log.d(TAG, "Looking for timestamps for conversation ID: $conversationId")
                    
                    // First check conversations collection for lastMessageTime
                    val conversationDoc = firestore.collection("conversations")
                        .document(conversationId)
                        .get()
                        .await()
                    
                    if (conversationDoc.exists() && conversationDoc.contains("lastMessageTime")) {
                        val timestamp = (conversationDoc.get("lastMessageTime") as? com.google.firebase.Timestamp)?.seconds ?: 0L
                        userTimestamps[user.rollNumber] = timestamp
                        Log.d(TAG, "Found lastMessageTime for ${user.name}: $timestamp in conversations")
                        continue
                    }
                    
                    // If not found, check user_conversations
                    val userConvDoc = firestore.collection("user_conversations")
                        .document(currentUserId ?: "")
                        .collection(conversationId)
                        .document(conversationId)
                        .get()
                        .await()
                    
                    if (userConvDoc.exists() && userConvDoc.contains("lastAccessed")) {
                        val timestamp = (userConvDoc.get("lastAccessed") as? com.google.firebase.Timestamp)?.seconds ?: 0L
                        userTimestamps[user.rollNumber] = timestamp
                        Log.d(TAG, "Found lastAccessed for ${user.name}: $timestamp in user_conversations")
                    } else {
                        Log.d(TAG, "No timestamp found for ${user.name}, using 0")
                        userTimestamps[user.rollNumber] = 0L
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting timestamp for ${user.rollNumber}", e)
                    userTimestamps[user.rollNumber] = 0L
                }
            }
            
            // Sort users by timestamp (desc) then by name (desc)
            val sortedUsers = userList.sortedWith(
                compareByDescending<Admin> { userTimestamps[it.rollNumber] ?: 0L }
                    .thenByDescending { it.name }
            )
            
            Log.d(TAG, "Sorted users by timestamp and name (desc):")
            sortedUsers.forEachIndexed { index, admin ->
                val timestamp = userTimestamps[admin.rollNumber] ?: 0L
                Log.d(TAG, "$index: ${admin.name} (${admin.userType}), timestamp: $timestamp")
            }
            
            sortedUsers
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching all users for admin", e)
            throw e
        }
    }
    
    // Method to fetch only admin users for admin users (kept for compatibility)
    suspend fun getAdminUsersForAdmins(): List<Admin> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Fetching admin users from users collection")
            
            // Query users collection directly for admin users
            val querySnapshot = usersCollection
                .whereEqualTo("userType", "admin")
                .get(Source.SERVER) // Force server fetch to ensure fresh data
                .await()
            
            Log.d(TAG, "Total admin users found: ${querySnapshot.size()}")
            
            // Convert to Admin objects
            val userList = querySnapshot.documents.mapNotNull { document ->
                try {
                    val userData = document.data
                    if (userData != null) {
                        Admin(
                            rollNumber = document.id,
                            name = (userData["name"] as? String) ?: "Unknown",
                            description = (userData["description"] as? String) ?: "",
                            year = when (val yearValue = userData["year"]) {
                                is Number -> yearValue.toInt()
                                is String -> yearValue.toIntOrNull() ?: 0
                                else -> 0
                            },
                            contactNumber = (userData["contactNumber"] as? String) ?: "",
                            email = (userData["email"] as? String) ?: "",
                            userType = (userData["userType"] as? String) ?: "Unknown"
                        ).also {
                            Log.d(TAG, "Added Admin1 user for Admin2: ${it.name} with roll number: ${it.rollNumber}")
                        }
                    } else {
                        Log.w(TAG, "Document ${document.id} has no data")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing user document ${document.id}", e)
                    null
                }
            }
            
            // Get current user ID
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            
            // Pre-fetch timestamps for sorting
            val userTimestamps = mutableMapOf<String, Long>()
            
            // Fetch timestamps from conversations and user_conversations
            for (user in userList) {
                try {
                    val otherUserRollNumber = user.rollNumber
                    // Sort to ensure same conversation ID regardless of order
                    val sortedUsers = listOf(currentUserId ?: "", otherUserRollNumber).sorted()
                    val conversationId = "conv_${sortedUsers[0]}_${sortedUsers[1]}"
                    
                    // First check conversations collection for lastMessageTime
                    val conversationDoc = firestore.collection("conversations")
                        .document(conversationId)
                        .get()
                        .await()
                    
                    if (conversationDoc.exists() && conversationDoc.contains("lastMessageTime")) {
                        val timestamp = (conversationDoc.get("lastMessageTime") as? com.google.firebase.Timestamp)?.seconds ?: 0L
                        userTimestamps[user.rollNumber] = timestamp
                        continue
                    }
                    
                    // If not found, check user_conversations
                    val userConvDoc = firestore.collection("user_conversations")
                        .document(currentUserId ?: "")
                        .collection(conversationId)
                        .document(conversationId)
                        .get()
                        .await()
                    
                    if (userConvDoc.exists() && userConvDoc.contains("lastAccessed")) {
                        val timestamp = (userConvDoc.get("lastAccessed") as? com.google.firebase.Timestamp)?.seconds ?: 0L
                        userTimestamps[user.rollNumber] = timestamp
                    } else {
                        userTimestamps[user.rollNumber] = 0L
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting timestamp for ${user.rollNumber}", e)
                    userTimestamps[user.rollNumber] = 0L
                }
            }
            
            // Sort users by timestamp (desc) then by name (desc)
            userList.sortedWith(
                compareByDescending<Admin> { userTimestamps[it.rollNumber] ?: 0L }
                    .thenByDescending { it.name }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Admin1 users for Admin2", e)
            throw e // Propagate error to handle it in the UI layer
        }
    }
    
    // Helper method to generate consistent conversation ID between two users
    private fun generateConversationId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "$userId1-$userId2" else "$userId2-$userId1"
    }
    
    // Method to get user data by ID
    suspend fun getUserData(userId: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Getting user data for ID: $userId")
            val userDoc = usersCollection.document(userId).get().await()
            if (userDoc.exists()) {
                val userData = userDoc.data
                Log.d(TAG, "Found user data: $userData")
                
                // Log the userType specifically as this is often what we're looking for
                val userType = userData?.get("userType") as? String
                Log.d(TAG, "User type for $userId: $userType")
                
                userData
            } else {
                Log.w(TAG, "No user found with ID: $userId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user data for ID: $userId", e)
            null
        }
    }
} 