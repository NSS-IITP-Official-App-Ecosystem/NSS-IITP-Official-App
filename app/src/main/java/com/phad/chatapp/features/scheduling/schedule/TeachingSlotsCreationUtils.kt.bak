package com.phad.chatapp.features.scheduling.schedule

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.features.scheduling.firebase.FirebaseManager
import com.phad.chatapp.features.scheduling.firebase.FirebaseManager.FirestoreCollection
import com.phad.chatapp.features.scheduling.models.TeachingSlot
import com.phad.chatapp.features.scheduling.models.TeachingSlotPreset
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Utility functions for creating and managing teaching slot presets
 */
object TeachingSlotsCreationUtils {
    private const val TAG = "TeachingSlotsCreationUtils"
    private val db = FirebaseFirestore.getInstance()
    
    /**
     * Creates sample teaching slot presets if none exist
     */
    fun createSampleTeachingSlotPresets(db: FirebaseFirestore, onComplete: (Boolean) -> Unit) {
        // First check if any presets already exist
        db.collection(FirestoreCollection.TEACHING_SLOT_PRESETS)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    Log.d(TAG, "No teaching slot presets found, creating samples")
                    createSamples(db, onComplete)
                } else {
                    Log.d(TAG, "Teaching slot presets already exist (${querySnapshot.size()}), skipping sample creation")
                    onComplete(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking teaching slot presets", e)
                onComplete(false)
            }
    }
    
    private fun createSamples(db: FirebaseFirestore, onComplete: (Boolean) -> Unit) {
        // Create sample teaching slot presets
        val samplePresets = listOf(
            createWeekdayEveningPreset(),
            createWeekendMorningPreset()
        )
        
        var completedCount = 0
        var successCount = 0
        
        for (preset in samplePresets) {
            val presetData = hashMapOf(
                "name" to preset.name,
                "dayOfWeek" to preset.dayOfWeek?.toString(),
                "startTime" to preset.startTime?.toString(),
                "endTime" to preset.endTime?.toString(),
                "location" to preset.location,
                "capacity" to preset.capacity,
                "description" to preset.description
            )

            // Use preset name as document ID
            db.collection(FirestoreCollection.TEACHING_SLOT_PRESETS)
                .document(preset.name)
                .set(presetData)
                .addOnSuccessListener {
                    Log.d(TAG, "Created sample preset with name as ID: ${preset.name}")
                    successCount++
                    completedCount++

                    if (completedCount == samplePresets.size) {
                        onComplete(successCount > 0)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error creating sample preset", e)
                    completedCount++

                    if (completedCount == samplePresets.size) {
                        onComplete(successCount > 0)
                    }
                }
        }
    }
    
    private fun createWeekdayEveningPreset(): TeachingSlotPreset {
        return TeachingSlotPreset(
            name = "Weekday Evenings",
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(17, 0), // 5:00 PM
            endTime = LocalTime.of(18, 30),  // 6:30 PM
            location = "Main Campus",
            capacity = 20,
            description = "Evening teaching sessions on weekdays"
        )
    }
    
    private fun createWeekendMorningPreset(): TeachingSlotPreset {
        return TeachingSlotPreset(
            name = "Weekend Mornings",
            dayOfWeek = DayOfWeek.SATURDAY,
            startTime = LocalTime.of(10, 0), // 10:00 AM
            endTime = LocalTime.of(11, 30),  // 11:30 AM
            location = "Community Center",
            capacity = 15,
            description = "Morning teaching sessions on weekends"
        )
    }

    suspend fun saveTeachingSlot(teachingSlot: TeachingSlot): Boolean {
        return try {
            val documentRef = if (teachingSlot.id.isNullOrEmpty()) {
                db.collection(FirestoreCollection.TEACHING_SLOTS).document()
            } else {
                db.collection(FirestoreCollection.TEACHING_SLOTS).document(teachingSlot.id)
            }
            
            val slotWithId = if (teachingSlot.id.isNullOrEmpty()) {
                teachingSlot.copy(id = documentRef.id)
            } else {
                teachingSlot
            }
            
            documentRef.set(slotWithId).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getTeachingSlots(): List<TeachingSlot> {
        return try {
            val snapshot = db.collection(FirestoreCollection.TEACHING_SLOTS).get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(TeachingSlot::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun deleteTeachingSlot(slotId: String): Boolean {
        return try {
            db.collection(FirestoreCollection.TEACHING_SLOTS).document(slotId).delete().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
} 