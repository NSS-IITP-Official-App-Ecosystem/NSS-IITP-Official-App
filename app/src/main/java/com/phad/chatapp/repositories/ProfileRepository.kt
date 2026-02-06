package com.phad.chatapp.repositories

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.phad.chatapp.utils.SessionManager
import kotlinx.coroutines.tasks.await
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Repository to load profile-related data with cache-first strategy and TTL.
 */
class ProfileRepository(private val context: Context) {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val sessionManager = SessionManager(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("ProfileRepositoryPrefs", MODE_PRIVATE)

    companion object {
        private const val TAG = "ProfileRepository"
        private const val PROFILE_TTL_MS: Long = 15 * 60 * 1000 // 15 minutes
        private const val PREF_PROFILE_LAST_FETCHED_AT = "profile_last_fetched_at"
    }

    private fun isFresh(now: Long, last: Long): Boolean {
        return (now - last) <= PROFILE_TTL_MS
    }

    suspend fun getUserDocMap(rollNumber: String): Map<String, Any>? {
        val now = System.currentTimeMillis()
        val lastFetched = prefs.getLong(PREF_PROFILE_LAST_FETCHED_AT, 0L)

        // 1) Try cache first
        try {
            val cacheSnap = firestore
                .collection("users")
                .document(rollNumber)
                .get(Source.CACHE)
                .await()

            if (cacheSnap.exists()) {
                val data = cacheSnap.data
                if (data != null) {
                    Log.d(TAG, "Returning profile from cache for $rollNumber")
                    // Refresh in background if stale
                    if (!isFresh(now, lastFetched)) {
                        tryRefreshFromServer(rollNumber)
                    }
                    return data
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Cache miss or error: ${e.message}")
        }

        // 2) Cache miss or stale: read minimal fields from server
        val snap = firestore
            .collection("users")
            .document(rollNumber)
            .get(Source.SERVER)
            .await()

        if (!snap.exists()) return null

        prefs.edit().putLong(PREF_PROFILE_LAST_FETCHED_AT, now).apply()
        return snap.data
    }

    private fun selectFields() = listOf(
        "name",
        "userType",
        "instituteOutlookId",
        "eventsAttended",
        "sem1Hours",
        "sem2Hours",
        "hours"
    )

    private suspend fun tryRefreshFromServer(rollNumber: String) {
        try {
            val snap = firestore
                .collection("users")
                .document(rollNumber)
                .get(Source.SERVER)
                .await()
            if (snap.exists()) {
                prefs.edit().putLong(PREF_PROFILE_LAST_FETCHED_AT, System.currentTimeMillis()).apply()
            }
        } catch (_: Exception) {
        }
    }

    fun listenToUserUpdates(rollNumber: String): Flow<Map<String, Any>?> = callbackFlow {
        Log.d(TAG, "Starting real-time listener for user: $rollNumber")
        val registration = firestore.collection("users").document(rollNumber)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e)
                    close(e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    Log.d(TAG, "Real-time update received for user: $rollNumber")
                    trySend(snapshot.data)
                } else {
                    Log.d(TAG, "Current data: null")
                    trySend(null)
                }
            }
        
        awaitClose { 
            Log.d(TAG, "Removing real-time listener for user: $rollNumber")
            registration.remove() 
        }
    }
}


