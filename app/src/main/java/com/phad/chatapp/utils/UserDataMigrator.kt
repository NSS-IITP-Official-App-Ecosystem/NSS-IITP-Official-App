package com.phad.chatapp.utils

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Utility for migrating user data from TWApp database to the main app database
 */
object UserDataMigrator {
    private const val TAG = "UserDataMigrator"
    
    /**
     * Migrate all users (students, subcoordinators, coordinators) from TWApp to main app
     */
    suspend fun migrateAllUsers(
        context: Context,
        lifecycleScope: CoroutineScope,
        onProgress: (String, Int) -> Unit,
        onComplete: (Int) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            var totalMigrated = 0
            
            // Step 1: Migrate students
            onProgress("Migrating students...", 0)
            Log.d(TAG, "Starting migration of students")
            
            try {
                val students = StudentDataViewer.getAllStudents()
                Log.d(TAG, "Retrieved ${students.size} students to migrate")
                
                // Process students in batches
                val studentCount = migrateUsersInBatches(
                    users = students,
                    userType = "Student",
                    onProgress = { current, total ->
                        onProgress("Migrating students: $current/$total", current)
                    }
                )
                
                totalMigrated += studentCount
                Log.d(TAG, "Successfully migrated $studentCount students")
                
                onProgress("Students migration complete. Starting subcoordinators...", totalMigrated)
            } catch (e: Exception) {
                Log.e(TAG, "Error migrating students", e)
                onProgress("Error migrating students: ${e.message}", totalMigrated)
            }
            
            // Step 2: Migrate subcoordinators (Admin1)
            try {
                val admins = Admin1DataViewer.getAllAdmins()
                Log.d(TAG, "Retrieved ${admins.size} subcoordinators to migrate")
                
                // Process subcoordinators in batches
                val admin1Count = migrateUsersInBatches(
                    users = admins,
                    userType = "Admin1",
                    onProgress = { current, total ->
                        onProgress("Migrating subcoordinators: $current/$total", totalMigrated + current)
                    }
                )
                
                totalMigrated += admin1Count
                Log.d(TAG, "Successfully migrated $admin1Count subcoordinators")
                
                onProgress("Subcoordinators migration complete. Starting coordinators...", totalMigrated)
            } catch (e: Exception) {
                Log.e(TAG, "Error migrating subcoordinators", e)
                onProgress("Error migrating subcoordinators: ${e.message}", totalMigrated)
            }
            
            // Step 3: Migrate coordinators (Admin2)
            try {
                val coordinators = Admin2DataViewer.getAllCoordinators()
                Log.d(TAG, "Retrieved ${coordinators.size} coordinators to migrate")
                
                // Process coordinators in batches
                val admin2Count = migrateUsersInBatches(
                    users = coordinators,
                    userType = "Admin2",
                    onProgress = { current, total ->
                        onProgress("Migrating coordinators: $current/$total", totalMigrated + current)
                    }
                )
                
                totalMigrated += admin2Count
                Log.d(TAG, "Successfully migrated $admin2Count coordinators")
                
                onProgress("Migration complete!", totalMigrated)
            } catch (e: Exception) {
                Log.e(TAG, "Error migrating coordinators", e)
                onProgress("Error migrating coordinators: ${e.message}", totalMigrated)
            }
            
            // All done!
            onComplete(totalMigrated)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during migration process", e)
            onError(e)
        }
    }
    
    /**
     * Migrate a batch of users to the main app's users collection
     */
    private suspend fun migrateUsersInBatches(
        users: List<Map<String, Any>>,
        userType: String,
        batchSize: Int = 10,
        onProgress: (Int, Int) -> Unit
    ): Int {
        if (users.isEmpty()) return 0
        
        var migratedCount = 0
        val mainDb = FirebaseFirestore.getInstance()
        
        // Process in batches to avoid overwhelming Firestore
        val batches = users.chunked(batchSize)
        
        for ((batchIndex, batch) in batches.withIndex()) {
            val deferreds = batch.mapIndexed { index, userData ->
                // Create a deferred that processes a single user
                withContext(Dispatchers.IO) {
                    async {
                        try {
                            val rollNumber = userData["rollNumber"] as? String ?: return@async false
                            
                            // Prepare user data for main app
                            val userDataForMainApp = prepareUserDataForMainDb(userData, userType)
                            
                            // Check if user already exists
                            val existingUser = mainDb.collection("users")
                                .whereEqualTo("rollNumber", rollNumber)
                                .get()
                                .await()
                                .documents
                                .firstOrNull()
                            
                            if (existingUser != null) {
                                // Update existing user
                                mainDb.collection("users")
                                    .document(existingUser.id)
                                    .update(userDataForMainApp)
                                    .await()
                            } else {
                                // Create new user
                                mainDb.collection("users")
                                    .document()
                                    .set(userDataForMainApp)
                                    .await()
                            }
                            
                            return@async true
                        } catch (e: Exception) {
                            Log.e(TAG, "Error migrating user", e)
                            return@async false
                        }
                    }
                }
            }
            
            // Wait for all operations in this batch to complete
            val results = deferreds.awaitAll()
            migratedCount += results.count { it }
            
            // Report progress
            onProgress(migratedCount, users.size)
        }
        
        return migratedCount
    }
    
    /**
     * Prepare user data for the main app's users collection
     */
    private fun prepareUserDataForMainDb(
        userData: Map<String, Any>,
        userType: String
    ): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        // Copy common fields
        result["rollNumber"] = userData["rollNumber"] as? String ?: ""
        result["name"] = userData["name"] as? String ?: ""
        result["userType"] = userType
        
        // Copy email if available
        userData["email"]?.let { result["email"] = it }
        
        // Copy phone if available
        userData["phone"]?.let { result["phone"] = it }
        
        // Set user status
        result["status"] = "Active"
        
        // Add timestamp for when this user was migrated
        result["migratedAt"] = com.google.firebase.Timestamp.now()
        
        return result
    }
}
