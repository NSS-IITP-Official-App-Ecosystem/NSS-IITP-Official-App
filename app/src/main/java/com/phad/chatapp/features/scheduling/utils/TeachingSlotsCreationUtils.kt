package com.phad.chatapp.features.scheduling.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.features.scheduling.firebase.FirestoreCollection

object TeachingSlotsCreationUtils {
    private const val TAG = "TeachingSlotsCreationUtils"

    /**
     * Creates sample teaching slot presets for demonstration purposes
     * @param firestore The FirebaseFirestore instance
     * @param callback Callback function that returns true if samples were created successfully
     */
    fun createSampleTeachingSlotPresets(firestore: FirebaseFirestore, callback: (Boolean) -> Unit) {
        // Check if samples already exist
        firestore.collection(FirestoreCollection.TEACHING_SLOT_PRESETS)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    Log.d(TAG, "Samples already exist, not creating new ones")
                    callback(false)
                    return@addOnSuccessListener
                }

                // Create samples using preset names as document IDs
                val batch = firestore.batch()

                // Sample 1: Weekday Mornings
                val preset1Name = "Weekday Mornings"
                val preset1 = firestore.collection(FirestoreCollection.TEACHING_SLOT_PRESETS).document(preset1Name)
                val preset1Data = hashMapOf(
                    "presetName" to preset1Name,
                    "columnNames" to listOf("8:00", "9:00", "10:00", "11:00"),
                    "schedule" to listOf(
                        hashMapOf(
                            "day" to "Mon",
                            "slots" to listOf(true, true, true, true)
                        ),
                        hashMapOf(
                            "day" to "Tue",
                            "slots" to listOf(true, true, true, true)
                        ),
                        hashMapOf(
                            "day" to "Wed",
                            "slots" to listOf(true, true, true, true)
                        ),
                        hashMapOf(
                            "day" to "Thu",
                            "slots" to listOf(true, true, true, true)
                        ),
                        hashMapOf(
                            "day" to "Fri",
                            "slots" to listOf(true, true, true, true)
                        )
                    )
                )
                batch.set(preset1, preset1Data)

                // Sample 2: Weekend Schedule
                val preset2Name = "Weekend Schedule"
                val preset2 = firestore.collection(FirestoreCollection.TEACHING_SLOT_PRESETS).document(preset2Name)
                val preset2Data = hashMapOf(
                    "presetName" to preset2Name,
                    "columnNames" to listOf("10:00", "12:00", "2:00", "4:00"),
                    "schedule" to listOf(
                        hashMapOf(
                            "day" to "Sat",
                            "slots" to listOf(true, true, true, true)
                        ),
                        hashMapOf(
                            "day" to "Sun",
                            "slots" to listOf(true, true, true, true)
                        )
                    )
                )
                batch.set(preset2, preset2Data)

                // Commit the batch
                batch.commit()
                    .addOnSuccessListener {
                        Log.d(TAG, "Sample teaching slot presets created successfully")
                        callback(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error creating sample teaching slot presets", e)
                        callback(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking for existing presets", e)
                callback(false)
            }
    }
} 