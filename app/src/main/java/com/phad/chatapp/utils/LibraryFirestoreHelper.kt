package com.phad.chatapp.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import com.phad.chatapp.models.LibraryItem
import com.google.firebase.storage.FirebaseStorage
import android.net.Uri
import com.google.firebase.firestore.CollectionReference

/**
 * Helper object for managing the Library data in Firestore.
 */
object LibraryFirestoreHelper {

    private const val TAG = "LibraryFirestoreHelper"
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    /**
     * Initializes the basic library schema in Firestore if it doesn't exist.
     * This should be called once, preferably by an admin user.
     */
    suspend fun initializeLibrarySchema() {
        try {
            Log.d(TAG, "Initializing library schema...")

            // 1. Top-Level Collections
            val sections = mapOf(
                "CoreTeaching" to "Core Teaching",
                "TutorialTechnicalTeaching" to "Tutorial and Technical Teaching",
                "Events" to "Events",
                "FoundationAcademy" to "Foundation Academy"
            )

            var order = 0
            for ((id, title) in sections) {
                val sectionRef = db.collection("librarySections").document(id)
                val sectionDoc = sectionRef.get().await()

                if (!sectionDoc.exists()) {
                    order++
                    sectionRef.set(mapOf("title" to title, "order" to order)).await()
                    Log.d(TAG, "Created library section: $title")
                } else {
                    Log.d(TAG, "Library section already exists: $title")
                }
            }

            // 2. CoreTeaching -> subCategories
            val coreTeachingSubCategories = mapOf(
                "CourseTeaching" to "Course Teaching",
                "Lab" to "Lab"
            )

            order = 0
            for ((id, title) in coreTeachingSubCategories) {
                val subCategoryRef = db.collection("librarySections").document("CoreTeaching").collection("subCategories").document(id)
                val subCategoryDoc = subCategoryRef.get().await()

                if (!subCategoryDoc.exists()) {
                    order++
                    subCategoryRef.set(mapOf("title" to title, "order" to order)).await()
                    Log.d(TAG, "Created Core Teaching subcategory: $title")
                } else {
                    Log.d(TAG, "Core Teaching subcategory already exists: $title")
                }
            }

            // 3. CoreTeaching -> CourseTeaching -> levels
            val courseTeachingLevels = mapOf(
                "Theory" to "Theory",
                "Lab" to "Lab"
            )

            order = 0
            for ((id, title) in courseTeachingLevels) {
                val levelRef = db.collection("librarySections").document("CoreTeaching").collection("subCategories").document("CourseTeaching").collection("levels").document(id)
                val levelDoc = levelRef.get().await()

                if (!levelDoc.exists()) {
                    order++
                    levelRef.set(mapOf("title" to title, "order" to order)).await()
                    Log.d(TAG, "Created Course Teaching level: $title")
                } else {
                    Log.d(TAG, "Course Teaching level already exists: $title")
                }
            }

            // 4. CoreTeaching -> CourseTeaching -> Theory -> grades and subjects (Initial setup for Class 8 English as an example)
            val theoryGrades = (8..12).map { "Class$it" to "Class $it" }

            for ((id, title) in theoryGrades) {
                val gradeRef = db.collection("librarySections").document("CoreTeaching").collection("subCategories").document("CourseTeaching").collection("levels").document("Theory").collection("grades").document(id)
                val gradeDoc = gradeRef.get().await()

                if (!gradeDoc.exists()) {
                    // Extract class number for ordering
                    val classNumber = id.replace("Class", "").toIntOrNull() ?: 0
                    gradeRef.set(mapOf("title" to title, "order" to classNumber)).await()
                    Log.d(TAG, "Created Theory grade: $title")

                    // Add initial subjects for this grade (example: English)
                    val initialSubjects = mapOf(
                        "English" to "English",
                        "Math" to "Math",
                        "Science" to "Science",
                        "Sanskrit" to "Sanskrit"
                    )

                    var subjectOrder = 0
                    for ((subjectId, subjectTitle) in initialSubjects) {
                        subjectOrder++
                        val subjectRef = gradeRef.collection("subjects").document(subjectId)
                        subjectRef.set(mapOf("title" to subjectTitle, "order" to subjectOrder)).await()
                        Log.d(TAG, "Created subject $subjectTitle for $title")
                    }

                } else {
                    Log.d(TAG, "Theory grade already exists: $title")
                }
            }

            // 5. CoreTeaching -> CourseTeaching -> Theory -> Class8 -> English -> resourceTypes (Example)
            val resourceTypes = mapOf(
                "CourseDocument" to "Course document",
                "NcertBookHindi" to "Ncert Hindi",
                "NcertBookEnglish" to "Ncert English",
                "PYQs" to "PYQs",
                "ShortNotes" to "Short Notes",
                "PracticeQuestions" to "Practice Qs"
            )

            // Example path for Class 8 English
            val exampleResourcePath = "librarySections/CoreTeaching/subCategories/CourseTeaching/levels/Theory/grades/Class8/subjects/English/resources"

            order = 0
            for ((id, title) in resourceTypes) {
                val resourceTypeRef = db.collection(exampleResourcePath).document(id)
                val resourceTypeDoc = resourceTypeRef.get().await()

                if (!resourceTypeDoc.exists()) {
                    order++
                    resourceTypeRef.set(mapOf("title" to title, "order" to order)).await()
                    Log.d(TAG, "Created resource type: $title")
                } else {
                    Log.d(TAG, "Resource type already exists: $title")
                }
            }

            // Add similar initializations for other branches (Lab, Tutorial/Technical, Events, Foundation Academy)
            // This is a basic structure, full schema initialization would be more extensive

            Log.d(TAG, "Library schema initialization complete.")

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing library schema", e)
        }
    }

    /**
     * Adds a file document to the specified library path.
     *
     * @param filePath The Firestore path to the 'files' subcollection
     *                 (e.g., "librarySections/CoreTeaching/subCategories/CourseTeaching/levels/Theory/grades/Class8/subjects/English/resources/CourseDocument/files")
     * @param filename The original filename.
     * @param googleDriveLink The web-viewable Google Drive link.
     * @param uploadedByRoll The roll number of the uploader.
     * @param mimeType The MIME type of the file.
     */
    suspend fun addFileDocument(
        filePath: String,
        filename: String,
        googleDriveLink: String,
        uploadedByRoll: String,
        mimeType: String
    ) {
        try {
            val timestamp = System.currentTimeMillis()
            // Construct the document ID
            val fileDocId = "${filename.substringBeforeLast(".", filename)}_${timestamp}_${uploadedByRoll}"

            val fileData = hashMapOf(
                "filename" to filename,
                "googleDriveLink" to googleDriveLink,
                "uploadedByRoll" to uploadedByRoll,
                "uploadedAt" to com.google.firebase.Timestamp.now(),
                "mimeType" to mimeType,
                "isSection" to false // Explicitly mark as not a section
            )

            db.collection(filePath).document(fileDocId).set(fileData).await()
            Log.d(TAG, "Added file document to Firestore: $filePath/$fileDocId")

        } catch (e: Exception) {
            Log.e(TAG, "Error adding file document to Firestore", e)
            throw e
        }
    }

    /**
     * Fetches the top-level library sections.
     *
     * @return A list of pairs where the first element is the document ID (key) and the second is a map of the document data.
     */
    suspend fun getLibrarySections(): List<Pair<String, Map<String, Any>>> {
        return try {
            Log.d("LibraryFirestoreHelper", "Attempting to fetch library sections from 'librarySections'")
            val snapshot = db.collection("librarySections").get().await()
            Log.d("LibraryFirestoreHelper", "Fetched ${snapshot.documents.size} documents from 'librarySections'")

            snapshot.documents.mapNotNull { doc ->
                val title = doc.data?.get("title") as? String
                // Safely access and cast the data map
                val dataMap = doc.data as? Map<String, Any>
                if (title != null && dataMap != null) {
                    Log.d("LibraryFirestoreHelper", "Fetched section document: id=${doc.id}, data=${doc.data}")
                    doc.id to dataMap // Use the safely cast map
                } else {
                    Log.w("LibraryFirestoreHelper", "Section document missing 'title' or data is null: id=${doc.id}, data=${doc.data}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("LibraryFirestoreHelper", "Error fetching library sections: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Fetches library items from a specified collection path, ordered by the 'order' field.
     * Includes file-specific data for documents in 'files' subcollections.
     *
     * @param collectionPath The Firestore path to the collection.
     * @return A list of LibraryItem objects.
     */
    suspend fun getLibraryItems(collectionPath: String): List<LibraryItem> {
        return try {
            val collectionRef = db.collection(collectionPath)

            // Apply orderBy("order") only if it's not a files collection
            val query = if (collectionPath.endsWith("/files")) {
                collectionRef
            } else {
                collectionRef.orderBy("order")
            }

            val snapshot = query.get().await()

            snapshot.documents.mapNotNull { doc ->
                val id = doc.id
                val path = "$collectionPath/${id}"

                // Retrieve fields, including the new isSection field
                val googleDriveLink = doc.data?.get("googleDriveLink") as? String
                val mimeType = doc.data?.get("mimeType") as? String
                val filename = doc.data?.get("filename") as? String
                val isSection = doc.data?.get("isSection") as? Boolean ?: false // Get isSection, default to false

                // Determine the title based on isSection and filename/title fields
                val title = if (isSection) {
                    doc.data?.get("title") as? String // Use 'title' for sections/folders
                } else {
                     filename ?: doc.data?.get("title") as? String // Use 'filename' or 'title' for files
                }

                // Only create LibraryItem if a title is found
                if (title != null) {
                    LibraryItem(
                        id = id,
                        title = title,
                        path = path,
                        googleDriveLink = googleDriveLink, // Will be null for sections/folders
                        mimeType = mimeType, // Will be null for sections/folders
                        isSection = isSection // Populate the new field
                    )
                } else {
                    Log.w(TAG, "Document missing title/filename or file indicators: ${doc.id} in $collectionPath")
                    null // Skip documents that cannot be mapped to a LibraryItem
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching library items from $collectionPath: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Adds a new top-level library section.
     *
     * @param title The title of the new section.
     * @return The ID of the newly created document, or null if an error occurred.
     */
    suspend fun addLibrarySection(title: String): String? {
        return try {
            Log.d(TAG, "Attempting to add new library section with title: $title")

            // Determine the next order number
            val snapshot = db.collection("librarySections")
                .orderBy("order", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            val nextOrder = if (snapshot.documents.isNotEmpty()) {
                (snapshot.documents[0].data?.get("order") as? Number)?.toInt() ?: 0
            } else {
                0
            } + 1

            val newSectionData = hashMapOf(
                "title" to title,
                "order" to nextOrder
            )

            val docRef = db.collection("librarySections").add(newSectionData).await()
            Log.d(TAG, "Added new library section: ${docRef.id}")
            docRef.id
        } catch (e: Exception) {
            Log.e(TAG, "Error adding library section", e)
            null
        }
    }

    /**
     * Adds a new library item to a specified collection.
     *
     * @param collectionPath The Firestore path to the collection where the item should be added.
     * @param title The title of the new item.
     * @param documentId An optional custom document ID. If null, Firestore will auto-generate one.
     * @return The ID of the newly created document, or null if an error occurred.
     */
    suspend fun addLibraryItem(collectionPath: String, title: String, documentId: String? = null): String? {
        return try {
            Log.d(TAG, "Attempting to add new library item to $collectionPath with title: $title")

            val collectionRef = db.collection(collectionPath)

            // Determine the next order number
            val snapshot = collectionRef
                .orderBy("order", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            val nextOrder = if (snapshot.documents.isNotEmpty()) {
                (snapshot.documents[0].data?.get("order") as? Number)?.toInt() ?: 0
            } else {
                0
            } + 1

            val newItemData = hashMapOf(
                "title" to title,
                "order" to nextOrder,
                "isSection" to true // Explicitly mark as a section/folder
            )

            val docRef = if (documentId != null) {
                collectionRef.document(documentId).set(newItemData).await()
                collectionRef.document(documentId)
            } else {
                collectionRef.add(newItemData).await()
            }

            Log.d(TAG, "Added new library item to $collectionPath: ${docRef.id}")
            docRef.id
        } catch (e: Exception) {
            Log.e(TAG, "Error adding library item to $collectionPath", e)
            null
        }
    }

    /**
     * Uploads a file to Firebase Storage.
     *
     * @param fileUri The URI of the file to upload.
     * @param storagePath The desired path in Firebase Storage (e.g., "library/files/...").
     * @return The download URL of the uploaded file, or null if an error occurred.
     */
    suspend fun uploadFileToStorage(fileUri: Uri, storagePath: String): String? {
        return try {
            Log.d(TAG, "Attempting to upload file to storage: $storagePath")
            val storageRef = storage.reference.child(storagePath)
            val uploadTask = storageRef.putFile(fileUri)
            uploadTask.await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.d(TAG, "File uploaded successfully. Download URL: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file to storage", e)
            null
        }
    }

    /**
     * Deletes a library item (document or collection) at the specified Firestore path.
     *
     * @param itemPath The full Firestore path to the item (document or collection).
     * @return True if deletion was successful, false otherwise.
     */
    suspend fun deleteLibraryItem(itemPath: String): Boolean {
        return try {
            val itemRef = db.document(itemPath)
            // Note: Deleting a document does NOT automatically delete its subcollections.
            // To delete a collection and its documents recursively, you typically need
            // to implement a server-side solution (e.g., Cloud Functions) or iterate
            // and delete documents in batches for smaller collections.
            // For simplicity here, this function primarily handles document deletion.
            // Deleting a collection path directly via client SDK might not be intended
            // or efficient for large collections.

            // Attempt to delete as a document first
            itemRef.delete().await()
            Log.d(TAG, "Deleted document at path: $itemPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting item at path: $itemPath", e)
            false
        }
    }

    /**
     * Renames a library item by updating its title in Firestore.
     *
     * @param itemPath The full Firestore path to the item (document).
     * @param newTitle The new title for the item.
     * @return True if renaming was successful, false otherwise.
     */
    suspend fun renameLibraryItem(itemPath: String, newTitle: String): Boolean {
        return try {
            val itemRef = db.document(itemPath)

            // Fetch the item first to determine if it's a section or a file
            val documentSnapshot = itemRef.get().await()
            if (!documentSnapshot.exists()) {
                Log.e(TAG, "Error renaming item at path: $itemPath. Document not found.")
                return false
            }

            val isSection = documentSnapshot.data?.get("isSection") as? Boolean ?: false

            val updateField = if (isSection) {
                "title"
            } else {
                "filename"
            }

            itemRef.update(updateField, newTitle).await()
            Log.d(TAG, "Renamed item at path: $itemPath to $newTitle (updated field: $updateField)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming item at path: $itemPath", e)
            false
        }
    }

    // You would add more functions here for fetching data from different levels of the schema
    // For example:
    // suspend fun getLibrarySections(): List<LibrarySection> { ... }
    // suspend fun getSubCategories(sectionId: String): List<SubCategory> { ... }
    // etc.

} 