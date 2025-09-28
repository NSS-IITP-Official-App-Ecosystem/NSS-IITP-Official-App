package com.phad.chatapp.features.scheduling.firebase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Manages Firestore operations for the scheduling module.
 * Uses the existing Firebase instance from the main app.
 */
object FirestoreManager {
    // Get the existing Firestore instance that was initialized by the main app
    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }
    
    // Constants for collection names
    const val TEACHING_SLOTS_COLLECTION = "teaching_slots"
    const val SCHEDULES_COLLECTION = "schedules"
    const val VOLUNTEER_PRESETS_COLLECTION = "volunteer_presets"
    
    /**
     * Example function to get teaching slots from Firestore.
     * This demonstrates how to use the existing Firebase instance.
     */
    suspend fun getTeachingSlots(): List<String> {
        return try {
            val snapshot = firestore.collection(TEACHING_SLOTS_COLLECTION).get().await()
            snapshot.documents.mapNotNull { it.id }
        } catch (e: Exception) {
            emptyList()
        }
    }
} 