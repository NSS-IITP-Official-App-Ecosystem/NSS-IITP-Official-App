package com.phad.chatapp.features.scheduling.firebase

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.google.firebase.firestore.ktx.toObject
import com.phad.chatapp.features.scheduling.models.TeachingSlot
import com.phad.chatapp.features.scheduling.models.TeachingSlotPreset
import com.phad.chatapp.features.scheduling.models.VolunteerInfo
import com.phad.chatapp.features.scheduling.models.VolunteerDetails
import com.project.thephadproject.models.VolunteerPreset
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper class to manage Firebase operations
 */
class FirebaseManager private constructor() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val TAG = "FirebaseManager"

    companion object {
        @Volatile
        private var instance: FirebaseManager? = null

        fun getInstance(): FirebaseManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseManager().also { instance = it }
            }
        }
    }

    // Collection constants
    object FirestoreCollection {
        const val TEACHING_SLOTS = "teaching_slots"
        const val TEACHING_SLOT_PRESETS = "teaching_slot_presets"
        const val VOLUNTEER_PRESETS = "volunteer_presets"
        const val VOLUNTEERS = "volunteers"
        const val SCHEDULES = "schedules"
        const val GENERATE_SCHEDULE = "generateSchedule"
    }

    /**
     * Gets a collection from Firestore
     */
    fun getCollection(
        path: String,
        onSuccess: (QuerySnapshot) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            firestore.collection(path)
                .get()
                .addOnSuccessListener { snapshot ->
                    onSuccess(snapshot)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error getting collection $path", exception)
                    onFailure(exception)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting collection $path", e)
            onFailure(e)
        }
    }

    /**
     * Gets a document from Firestore
     */
    fun getDocument(
        path: String,
        onSuccess: (DocumentSnapshot) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            firestore.document(path)
                .get()
                .addOnSuccessListener { document ->
                    onSuccess(document)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error getting document $path", exception)
                    onFailure(exception)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting document $path", e)
            onFailure(e)
        }
    }

    /**
     * Adds a document to a collection
     */
    fun addDocument(
        collection: String,
        data: Map<String, Any>,
        onSuccess: (DocumentReference) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            firestore.collection(collection)
                .add(data)
                .addOnSuccessListener { documentRef ->
                    onSuccess(documentRef)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error adding document to $collection", exception)
                    onFailure(exception)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception adding document to $collection", e)
            onFailure(e)
        }
    }

    /**
     * Updates a document in Firestore
     */
    fun updateDocument(
        path: String,
        data: Map<String, Any>,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            firestore.document(path)
                .update(data)
                .addOnSuccessListener {
                    onSuccess()
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error updating document $path", exception)
                    onFailure(exception)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception updating document $path", e)
            onFailure(e)
        }
    }

    /**
     * Gets a collection reference for the specified path.
     */
    fun getCollectionReference(path: String) = firestore.collection(path)

    /**
     * Gets a document reference for the specified path.
     */
    fun getDocumentReference(path: String) = firestore.document(path)

    /**
     * Sets data for a document with a specific ID.
     */
    fun setDocument(
        path: String,
        data: Map<String, Any>,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        firestore.document(path)
            .set(data)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error setting document at $path: ${e.message}", e)
                onFailure(e)
            }
    }

    /**
     * Deletes a document.
     */
    fun deleteDocument(
        path: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        firestore.document(path)
            .delete()
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error deleting document at $path: ${e.message}", e)
                onFailure(e)
            }
    }

    /**
     * Observes a document as a Flow for reactive updates.
     */
    fun observeDocumentAsFlow(path: String): Flow<DocumentSnapshot> = callbackFlow {
        var listenerRegistration: ListenerRegistration? = null
        
        try {
            listenerRegistration = firestore.document(path)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error observing document at $path: ${error.message}", error)
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null) {
                        trySend(snapshot)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up document listener at $path: ${e.message}", e)
        }
        
        awaitClose {
            Log.d(TAG, "Cancelling document listener at $path")
            listenerRegistration?.remove()
        }
    }

    /**
     * Observes a collection as a Flow for reactive updates.
     */
    fun observeCollectionAsFlow(path: String): Flow<QuerySnapshot> = callbackFlow {
        var listenerRegistration: ListenerRegistration? = null
        
        try {
            listenerRegistration = firestore.collection(path)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error observing collection at $path: ${error.message}", error)
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null) {
                        trySend(snapshot)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up collection listener at $path: ${e.message}", e)
        }
        
        awaitClose {
            Log.d(TAG, "Cancelling collection listener at $path")
            listenerRegistration?.remove()
        }
    }

    /**
     * Executes a batch write operation with multiple updates.
     */
    fun executeBatch(
        operations: (WriteBatch) -> Unit,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val batch = firestore.batch()
        operations(batch)
        
        batch.commit()
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error executing batch operation: ${e.message}", e)
                onFailure(e)
            }
    }

    suspend fun getTeachingSlots(): List<TeachingSlot> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection(FirestoreCollection.TEACHING_SLOTS).get().await()
            return@withContext snapshot.documents.mapNotNull { it.toObject<TeachingSlot>() }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    suspend fun saveTeachingSlot(teachingSlot: TeachingSlot): Boolean = withContext(Dispatchers.IO) {
        try {
            val documentRef = if (teachingSlot.id.isNullOrEmpty()) {
                firestore.collection(FirestoreCollection.TEACHING_SLOTS).document()
            } else {
                firestore.collection(FirestoreCollection.TEACHING_SLOTS).document(teachingSlot.id)
            }
            
            val slotWithId = if (teachingSlot.id.isNullOrEmpty()) {
                teachingSlot.copy(id = documentRef.id)
            } else {
                teachingSlot
            }
            
            documentRef.set(slotWithId).await()
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun getTeachingSlotPresets(): List<TeachingSlotPreset> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection(FirestoreCollection.TEACHING_SLOT_PRESETS).get().await()
            return@withContext snapshot.documents.mapNotNull { it.toObject<TeachingSlotPreset>() }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    suspend fun saveTeachingSlotPreset(preset: TeachingSlotPreset): Boolean = withContext(Dispatchers.IO) {
        try {
            // Use preset name as document ID, fallback to auto-generated ID if name is empty
            val documentId = if (preset.name.isNotEmpty()) {
                preset.name
            } else if (!preset.id.isNullOrEmpty()) {
                preset.id
            } else {
                firestore.collection(FirestoreCollection.TEACHING_SLOT_PRESETS).document().id
            }

            val documentRef = firestore.collection(FirestoreCollection.TEACHING_SLOT_PRESETS).document(documentId)

            val presetWithId = preset.copy(id = documentId)

            documentRef.set(presetWithId).await()
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun getVolunteers(): List<VolunteerInfo> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection(FirestoreCollection.VOLUNTEERS).get().await()
            return@withContext snapshot.documents.mapNotNull { it.toObject<VolunteerInfo>() }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    suspend fun saveVolunteer(volunteer: VolunteerInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            val documentRef = if (volunteer.rollNo.isNullOrEmpty()) {
                firestore.collection(FirestoreCollection.VOLUNTEERS).document()
            } else {
                firestore.collection(FirestoreCollection.VOLUNTEERS).document(volunteer.rollNo)
            }

            val volunteerWithRollNo = if (volunteer.rollNo.isNullOrEmpty()) {
                volunteer.copy(rollNo = documentRef.id)
            } else {
                volunteer
            }

            documentRef.set(volunteerWithRollNo).await()
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    /**
     * Get volunteers from generateSchedule collection with enhanced data
     */
    suspend fun getVolunteersFromGenerateSchedule(): List<VolunteerDetails> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection(FirestoreCollection.GENERATE_SCHEDULE).get().await()
            return@withContext snapshot.documents.mapNotNull { document ->
                val data = document.data ?: return@mapNotNull null
                try {
                    VolunteerDetails(
                        name = data["Name"] as? String ?: data["name"] as? String ?: "",
                        rollNo = data["Roll_No"] as? String ?: data["roll_no"] as? String ?: data["rollNumber"] as? String ?: document.id,
                        group = data["Academic_Grp"]?.toString() ?: data["academic_grp"]?.toString() ?: data["group"]?.toString() ?: "",
                        interviewScore = (data["Interview_Score"] as? Number)?.toInt() ?: (data["interview_score"] as? Number)?.toInt() ?: 0,
                        subjectPreference1 = data["Subj_Preference_1"] as? String ?: data["subjectPreference1"] as? String ?: "",
                        subjectPreference2 = data["Sub_Preference2"] as? String ?: data["subjectPreference2"] as? String ?: "",
                        subjectPreference3 = data["Sub_Preference_3"] as? String ?: data["subjectPreference3"] as? String ?: ""
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing volunteer data for document ${document.id}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching volunteers from generateSchedule collection", e)
            return@withContext emptyList()
        }
    }

    /**
     * Get a specific volunteer from generateSchedule collection by roll number
     */
    suspend fun getVolunteerFromGenerateSchedule(rollNo: String): VolunteerDetails? = withContext(Dispatchers.IO) {
        try {
            // First try direct document lookup
            val directDoc = firestore.collection(FirestoreCollection.GENERATE_SCHEDULE).document(rollNo).get().await()
            if (directDoc.exists()) {
                val data = directDoc.data ?: return@withContext null
                return@withContext VolunteerDetails(
                    name = data["Name"] as? String ?: data["name"] as? String ?: "",
                    rollNo = data["Roll_No"] as? String ?: data["roll_no"] as? String ?: data["rollNumber"] as? String ?: rollNo,
                    group = data["Academic_Grp"]?.toString() ?: data["academic_grp"]?.toString() ?: data["group"]?.toString() ?: "",
                    interviewScore = (data["Interview_Score"] as? Number)?.toInt() ?: (data["interview_score"] as? Number)?.toInt() ?: 0,
                    subjectPreference1 = data["Subj_Preference_1"] as? String ?: data["subjectPreference1"] as? String ?: "",
                    subjectPreference2 = data["Sub_Preference2"] as? String ?: data["subjectPreference2"] as? String ?: "",
                    subjectPreference3 = data["Sub_Preference_3"] as? String ?: data["subjectPreference3"] as? String ?: ""
                )
            }

            // Try querying by roll number field
            val query = firestore.collection(FirestoreCollection.GENERATE_SCHEDULE)
                .whereEqualTo("Roll_No", rollNo)
                .limit(1)
                .get()
                .await()

            if (query.documents.isNotEmpty()) {
                val doc = query.documents.first()
                val data = doc.data ?: return@withContext null
                return@withContext VolunteerDetails(
                    name = data["Name"] as? String ?: data["name"] as? String ?: "",
                    rollNo = data["Roll_No"] as? String ?: data["roll_no"] as? String ?: data["rollNumber"] as? String ?: rollNo,
                    group = data["Academic_Grp"]?.toString() ?: data["academic_grp"]?.toString() ?: data["group"]?.toString() ?: "",
                    interviewScore = (data["Interview_Score"] as? Number)?.toInt() ?: (data["interview_score"] as? Number)?.toInt() ?: 0,
                    subjectPreference1 = data["Subj_Preference_1"] as? String ?: data["subjectPreference1"] as? String ?: "",
                    subjectPreference2 = data["Sub_Preference2"] as? String ?: data["subjectPreference2"] as? String ?: "",
                    subjectPreference3 = data["Sub_Preference_3"] as? String ?: data["subjectPreference3"] as? String ?: ""
                )
            }

            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching volunteer $rollNo from generateSchedule collection", e)
            return@withContext null
        }
    }

    suspend fun deleteVolunteer(volunteerId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection(FirestoreCollection.VOLUNTEERS).document(volunteerId).delete().await()
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun getVolunteerPresets(): List<VolunteerPreset> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection(FirestoreCollection.VOLUNTEER_PRESETS).get().await()
            return@withContext snapshot.documents.mapNotNull { it.toObject<VolunteerPreset>() }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    suspend fun saveVolunteerPreset(preset: VolunteerPreset): Boolean = withContext(Dispatchers.IO) {
        try {
            val documentRef = if (preset.id.isNullOrEmpty()) {
                firestore.collection(FirestoreCollection.VOLUNTEER_PRESETS).document()
            } else {
                firestore.collection(FirestoreCollection.VOLUNTEER_PRESETS).document(preset.id)
            }

            // Create preset data without id and createdAt fields as per user preference
            val presetData = mapOf(
                "name" to preset.name,
                "volunteerCount" to preset.volunteerCount,
                "groupCounts" to preset.groupCounts,
                "volunteers" to preset.volunteers
            )

            documentRef.set(presetData).await()
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun deleteVolunteerPreset(presetId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection(FirestoreCollection.VOLUNTEER_PRESETS).document(presetId).delete().await()
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
} 