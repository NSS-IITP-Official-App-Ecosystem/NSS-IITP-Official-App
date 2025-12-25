package com.phad.chatapp.utils

import android.content.Context
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import com.phad.chatapp.models.Admin

/**
 * Utility class for Firebase Realtime Database setup and operations
 */
class RealtimeDatabaseSetup {
    private val TAG = "RealtimeDatabaseSetup"
    
    companion object {
        @Volatile
        private var instance: RealtimeDatabaseSetup? = null
        
        fun getInstance(): RealtimeDatabaseSetup {
            return instance ?: synchronized(this) {
                instance ?: RealtimeDatabaseSetup().also { instance = it }
            }
        }
        
        // Get database reference
        fun getDatabase(): FirebaseDatabase {
            return FirebaseDatabase.getInstance()
        }
        
        // Get a specific reference in the database
        fun getDatabaseReference(path: String): DatabaseReference {
            return getDatabase().getReference(path)
        }
        
        // Initialize the database with persistence
        fun initialize(context: Context) {
            try {
                // Enable offline persistence
                FirebaseDatabase.getInstance().setPersistenceEnabled(true)
                Log.d("RealtimeDatabaseSetup", "Firebase Realtime Database initialized with persistence")
            } catch (e: Exception) {
                Log.e("RealtimeDatabaseSetup", "Error initializing Firebase Realtime Database", e)
            }
        }
        
        // Get reference to users
        fun getUsersReference(): DatabaseReference {
            return getDatabaseReference("users")
        }
        
        // Get reference to chats
        fun getChatsReference(): DatabaseReference {
            return getDatabaseReference("chats")
        }
        
        // Get reference to groups
        fun getGroupsReference(): DatabaseReference {
            return getDatabaseReference("groups")
        }
        
        // Get reference to a specific user
        fun getUserReference(userId: String): DatabaseReference {
            return getUsersReference().child(userId)
        }
        
        // Get reference to a specific chat
        fun getChatReference(chatId: String): DatabaseReference {
            return getChatsReference().child(chatId)
        }
        
        // Get reference to a specific group
        fun getGroupReference(groupId: String): DatabaseReference {
            return getGroupsReference().child(groupId)
        }
    }
    
    private val database = FirebaseDatabase.getInstance()
    
    /**
     * Call this method to populate Realtime Database with sample data
     */
    fun setupSampleData() {
        Log.d(TAG, "Setting up sample data in Realtime Database")
        setupAdmin1Data()
        setupAdmin2Data()
        setupStudentsData()
    }
    
    private fun setupAdmin1Data() {
        val admin1Reference = database.getReference("Admin1")
        Log.d(TAG, "Setting up Admin1 data")
        
        // Add first admin
        val admin1 = mapOf(
            "name" to "Aditya Gupta",
            "description" to "Hola",
            "year" to 2,
            "contactNumber" to "9876543210",
            "email" to "admin1@example.com"
        )
        
        admin1Reference.child("2301MC51").setValue(admin1)
            .addOnSuccessListener { 
                Log.d(TAG, "Admin1 data added successfully: 2301MC51") 
            }
            .addOnFailureListener { e -> 
                Log.e(TAG, "Error adding Admin1 data: 2301MC51", e) 
            }
        
        // Add second admin
        val admin2 = mapOf(
            "name" to "Aditya",
            "description" to "Computer Science Department Representative",
            "year" to 2,
            "contactNumber" to "9876543211",
            "email" to "admin2@example.com"
        )
        
        admin1Reference.child("2302EE45").setValue(admin2)
            .addOnSuccessListener { 
                Log.d(TAG, "Admin1 data added successfully: 2302EE45") 
            }
            .addOnFailureListener { e -> 
                Log.e(TAG, "Error adding Admin1 data: 2302EE45", e) 
            }
    }
    
    private fun setupAdmin2Data() {
        val admin2Reference = database.getReference("Admin2")
        Log.d(TAG, "Setting up Admin2 data")
        
        // Add first admin
        val admin1 = mapOf(
            "name" to "Third Year Representative",
            "description" to "Student Council Member",
            "year" to 3,
            "contactNumber" to "9876543220",
            "email" to "admin3@example.com"
        )
        
        admin2Reference.child("2201ME32").setValue(admin1)
            .addOnSuccessListener { 
                Log.d(TAG, "Admin2 data added successfully: 2201ME32") 
            }
            .addOnFailureListener { e -> 
                Log.e(TAG, "Error adding Admin2 data: 2201ME32", e) 
            }
        
        // Add second admin
        val admin2 = mapOf(
            "name" to "CS Department Head",
            "description" to "Computer Science Department Head for Third Year",
            "year" to 3,
            "contactNumber" to "9876543221",
            "email" to "admin4@example.com"
        )
        
        admin2Reference.child("2201CS78").setValue(admin2)
            .addOnSuccessListener { 
                Log.d(TAG, "Admin2 data added successfully: 2201CS78") 
            }
            .addOnFailureListener { e -> 
                Log.e(TAG, "Error adding Admin2 data: 2201CS78", e) 
            }
    }
    
    private fun setupStudentsData() {
        val studentsReference = database.getReference("Students")
        Log.d(TAG, "Setting up Students data")
        
        // Add first student
        val student1 = mapOf(
            "name" to "John Doe",
            "branch" to "Electrical Engineering",
            "year" to 2,
            "contactNumber" to "9876543230",
            "email" to "student1@example.com"
        )
        
        studentsReference.child("2301EE23").setValue(student1)
            .addOnSuccessListener { 
                Log.d(TAG, "Student data added successfully: 2301EE23") 
            }
            .addOnFailureListener { e -> 
                Log.e(TAG, "Error adding Student data: 2301EE23", e) 
            }
        
        // Add second student
        val student2 = mapOf(
            "name" to "Jane Smith",
            "branch" to "Computer Science",
            "year" to 3,
            "contactNumber" to "9876543231",
            "email" to "student2@example.com"
        )
        
        studentsReference.child("2201CS45").setValue(student2)
            .addOnSuccessListener { 
                Log.d(TAG, "Student data added successfully: 2201CS45") 
            }
            .addOnFailureListener { e -> 
                Log.e(TAG, "Error adding Student data: 2201CS45", e) 
            }
    }
} 