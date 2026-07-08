package com.phad.chatapp.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.phad.chatapp.models.ContactGroup
import com.phad.chatapp.models.ContactPerson
import com.phad.chatapp.utils.Constants
import kotlinx.coroutines.tasks.await

class HelpRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val helpContactsCollection = firestore.collection("help_contacts")
    
    // In-memory cache
    private var cachedContactGroups: List<ContactGroup>? = null

    suspend fun getContactGroups(forceRefresh: Boolean = false): Result<List<ContactGroup>> {
        if (!forceRefresh && cachedContactGroups != null) {
            return Result.success(cachedContactGroups!!)
        }

        return try {
            // Fetch from Firestore
            // Try to use CACHE first if not forcing refresh, but normally Android SDK handles offline
            // We just use default get() which tries SERVER, falls back to CACHE.
            val snapshot = helpContactsCollection.get().await()
            
            if (snapshot.isEmpty) {
                cachedContactGroups = emptyList()
                Result.success(emptyList())
            } else {
                val groups = snapshot.documents.mapNotNull { it.toObject(ContactGroup::class.java) }
                    .sortedBy { it.order }
                cachedContactGroups = groups
                Result.success(groups)
            }
        } catch (e: Exception) {
            // On failure (e.g. offline with no cache), return cached if available, else failure
            if (cachedContactGroups != null) {
                Result.success(cachedContactGroups!!)
            } else {
                Result.failure(e)
            }
        }
    }

}
