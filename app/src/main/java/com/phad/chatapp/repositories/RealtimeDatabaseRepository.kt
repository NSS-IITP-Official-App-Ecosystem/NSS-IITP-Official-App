package com.phad.chatapp.repositories

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.phad.chatapp.models.Admin
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RealtimeDatabaseRepository {
    private val TAG = "RealtimeDatabaseRepo"
    private val database = FirebaseDatabase.getInstance()
    
    // References
    private val admin1Reference = database.getReference("Admin1")
    private val admin2Reference = database.getReference("Admin2")
    private val studentsReference = database.getReference("Students")
    
    init {
        // Persistence is enabled in MainActivity to ensure it's done before any DB operations
        Log.d(TAG, "RealtimeDatabaseRepository initialized")
    }
    
    // Fetch all second year admins
    suspend fun getAdmin1List(): List<Admin> {
        return try {
            Log.d(TAG, "Attempting to fetch Admin1 data from Realtime Database...")
            
            // Use suspendCancellableCoroutine to handle async Firebase calls in a coroutine
            suspendCancellableCoroutine { continuation ->
                val valueEventListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        Log.d(TAG, "Admin1 data received, children count: ${snapshot.childrenCount}")
                        val adminList = mutableListOf<Admin>()
                        
                        for (childSnapshot in snapshot.children) {
                            try {
                                val rollNumber = childSnapshot.key ?: ""
                                val name = childSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                                val description = childSnapshot.child("description").getValue(String::class.java) ?: ""
                                val year = childSnapshot.child("year").getValue(Int::class.java) ?: 0
                                val contactNumber = childSnapshot.child("contactNumber").getValue(String::class.java) ?: ""
                                val email = childSnapshot.child("email").getValue(String::class.java) ?: ""
                                
                                val admin = Admin(
                                    rollNumber = rollNumber,
                                    name = name,
                                    description = description,
                                    year = year,
                                    contactNumber = contactNumber,
                                    email = email
                                )
                                
                                Log.d(TAG, "Added admin: $admin")
                                adminList.add(admin)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing admin data", e)
                            }
                        }
                        
                        if (adminList.isEmpty()) {
                            Log.w(TAG, "No admins found in Realtime Database, returning hardcoded data")
                            continuation.resume(getHardcodedAdmins())
                        } else {
                            continuation.resume(adminList)
                        }
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Database error: ${error.message}")
                        // If there's an error, use hardcoded data
                        continuation.resume(getHardcodedAdmins())
                    }
                }
                
                admin1Reference.addListenerForSingleValueEvent(valueEventListener)
                
                continuation.invokeOnCancellation {
                    // Clean up if coroutine is cancelled
                    admin1Reference.removeEventListener(valueEventListener)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Admin1 list", e)
            getHardcodedAdmins()
        }
    }
    
    // Hardcoded admins for offline mode
    private fun getHardcodedAdmins(): List<Admin> {
        Log.d(TAG, "Returning hardcoded admin data for offline mode")
        return listOf(
            Admin(
                rollNumber = "2301MC51", 
                name = "Aditya Gupta (Offline)", 
                description = "Offline mode - Data from Realtime DB",
                year = 2,
                contactNumber = "9876543210",
                email = "admin1@example.com"
            ),
            Admin(
                rollNumber = "2302EE45", 
                name = "Aditya (Offline)", 
                description = "Computer Science Department Representative",
                year = 2,
                contactNumber = "9876543211",
                email = "admin2@example.com"
            )
        )
    }
    
    // Fetch admin by roll number
    suspend fun getAdminByRollNumber(rollNumber: String): Admin? {
        return try {
            suspendCancellableCoroutine { continuation ->
                admin1Reference.child(rollNumber).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            try {
                                val name = snapshot.child("name").getValue(String::class.java) ?: ""
                                val description = snapshot.child("description").getValue(String::class.java) ?: ""
                                val year = snapshot.child("year").getValue(Int::class.java) ?: 0
                                val contactNumber = snapshot.child("contactNumber").getValue(String::class.java) ?: ""
                                val email = snapshot.child("email").getValue(String::class.java) ?: ""
                                
                                val admin = Admin(
                                    rollNumber = rollNumber,
                                    name = name,
                                    description = description,
                                    year = year,
                                    contactNumber = contactNumber,
                                    email = email
                                )
                                continuation.resume(admin)
                            } catch (e: Exception) {
                                continuation.resume(null)
                            }
                        } else {
                            // Check Admin2 if not found in Admin1
                            admin2Reference.child(rollNumber).addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    if (snapshot.exists()) {
                                        try {
                                            val name = snapshot.child("name").getValue(String::class.java) ?: ""
                                            val description = snapshot.child("description").getValue(String::class.java) ?: ""
                                            val year = snapshot.child("year").getValue(Int::class.java) ?: 0
                                            val contactNumber = snapshot.child("contactNumber").getValue(String::class.java) ?: ""
                                            val email = snapshot.child("email").getValue(String::class.java) ?: ""
                                            
                                            val admin = Admin(
                                                rollNumber = rollNumber,
                                                name = name,
                                                description = description,
                                                year = year,
                                                contactNumber = contactNumber,
                                                email = email
                                            )
                                            continuation.resume(admin)
                                        } catch (e: Exception) {
                                            continuation.resume(null)
                                        }
                                    } else {
                                        continuation.resume(null)
                                    }
                                }
                                
                                override fun onCancelled(error: DatabaseError) {
                                    continuation.resume(null)
                                }
                            })
                        }
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        continuation.resume(null)
                    }
                })
            }
        } catch (e: Exception) {
            null
        }
    }
} 