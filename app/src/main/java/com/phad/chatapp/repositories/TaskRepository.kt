package com.phad.chatapp.repositories

import android.util.Log
import com.google.android.gms.tasks.Task as GoogleTask
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.phad.chatapp.models.Task
import java.util.Date

class TaskRepository {
    private val TAG = "TaskRepository"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TASKS_COLLECTION = "tasks"
    
    // Get all tasks for the current user
    fun getUserTasks(): GoogleTask<List<Task>> {
        val currentUserId = auth.currentUser?.uid ?: return Tasks.forCanceled()
        
        return db.collection(TASKS_COLLECTION)
            .whereEqualTo("userId", currentUserId)
            .orderBy("isCompleted", Query.Direction.ASCENDING)
            .orderBy("dueDate", Query.Direction.ASCENDING)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .continueWith { task ->
                if (task.isSuccessful) {
                    val tasks = mutableListOf<Task>()
                    for (document in task.result!!) {
                        try {
                            val task = document.toObject(Task::class.java)
                            tasks.add(task)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing task: ${e.message}")
                        }
                    }
                    tasks
                } else {
                    throw task.exception ?: Exception("Unknown error fetching tasks")
                }
            }
    }
    
    // Add a new task
    fun addTask(title: String, description: String, dueDate: Date?): GoogleTask<DocumentReference> {
        val currentUserId = auth.currentUser?.uid ?: return Tasks.forCanceled()
        
        val taskData = hashMapOf(
            "userId" to currentUserId,
            "title" to title,
            "description" to description,
            "dueDate" to dueDate,
            "isCompleted" to false,
            "createdAt" to Date(),
            "completedAt" to null
        )
        
        return db.collection(TASKS_COLLECTION).add(taskData)
    }
    
    // Update task completion status
    fun updateTaskCompletion(taskId: String, isCompleted: Boolean): GoogleTask<Void> {
        val updates = hashMapOf<String, Any?>(
            "isCompleted" to isCompleted,
            "completedAt" to if (isCompleted) Date() else null
        )
        
        return db.collection(TASKS_COLLECTION)
            .document(taskId)
            .update(updates as Map<String, Any>)
    }
    
    // Delete a task
    fun deleteTask(taskId: String): GoogleTask<Void> {
        return db.collection(TASKS_COLLECTION)
            .document(taskId)
            .delete()
    }
} 