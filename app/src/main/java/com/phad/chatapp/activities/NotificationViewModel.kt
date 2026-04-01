package com.phad.chatapp.activities

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.phad.chatapp.models.NotificationItem
import com.phad.chatapp.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val db = FirebaseFirestore.getInstance()
    private val sessionManager = SessionManager(application)
    val isAdmin = sessionManager.fetchUserType().equals("Admin", ignoreCase = true)

    private val PREFS_NAME = "NotificationPrefs"
    private val KEY_READ_IDS = "read_notification_ids"
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getReadIds(): Set<String> {
        return prefs.getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
    }

    private fun saveReadId(id: String) {
        val currentIds = getReadIds().toMutableSet()
        currentIds.add(id)
        prefs.edit().putStringSet(KEY_READ_IDS, currentIds).apply()
    }
    
    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        fetchNotifications()
    }

    fun fetchNotifications() {
        val userType = sessionManager.fetchUserType()
        val userId = sessionManager.fetchUserId()

        // Include both NSS and TTW topics so history works regardless of which
        // interface the user is currently on.
        // Also include wing topics from the user's profile.
        val profile = sessionManager.getProfileFromSession()
        val wingTopics = profile.wings.map { wing ->
            "wing_" + wing.lowercase().replace(" ", "_").replace("&", "and")
        }.toSet()

        val userTopics = setOf(
            "all",
            "nss",
            "ttw",
            "nss_user",
            "ttw_user",
            "nss_$userType",       // e.g. "nss_Admin", "nss_Student"
            "ttw_$userType",       // e.g. "ttw_Admin", "ttw_Student"
            "nss_admin",           // lowercase variants
            "ttw_admin",
            "user_$userId"         // personal topic for direct messages
        ) + wingTopics

        Log.d("NotificationViewModel", "Fetching notifications for userId=$userId, userType=$userType, topics=$userTopics")

        _isLoading.value = true
        db.collection("app_notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, e ->
                _isLoading.value = false
                if (e != null) {
                    Log.w("NotificationViewModel", "Listen failed: ${e.message}", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val fiveDaysAgoMs = System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000L)
                    val readTopicIds = getReadIds()
                    // Pre-compute lowercase set once for efficient comparison
                    val userTopicsLower = userTopics.map { it.lowercase() }.toSet()

                    Log.d("NotificationViewModel", "Raw docs from Firestore: ${snapshot.size()}")
                    Log.d("NotificationViewModel", "User topics (lowercase): $userTopicsLower")

                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(NotificationItem::class.java)?.apply {
                            id = doc.id
                            isRead = readTopicIds.contains(doc.id)
                        }
                    }.filter { item ->
                        val timestampOk = item.timestamp == null ||
                            item.timestamp!!.toDate().time > fiveDaysAgoMs
                        // Match against targetTopics list OR legacy targetType string
                        val topicMatch = item.targetTopics.any { it.lowercase() in userTopicsLower }
                            || (item.targetType != null && item.targetType!!.lowercase() in userTopicsLower)
                        Log.d("NotificationViewModel", "Doc ${item.id}: targetTopics=${item.targetTopics} targetType=${item.targetType} timestampOk=$timestampOk topicMatch=$topicMatch")
                        timestampOk && topicMatch
                    }

                    Log.d("NotificationViewModel", "Filtered list size: ${list.size}")
                    _notifications.value = list
                }
            }
    }

    fun markAsRead(notificationId: String) {
        saveReadId(notificationId)
        val currentList = _notifications.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == notificationId }
        if (index != -1 && !currentList[index].isRead) {
            val updatedItem = currentList[index].copy(isRead = true).apply { id = notificationId }
            currentList[index] = updatedItem
            _notifications.value = currentList
        }
    }

    fun deleteNotification(notificationId: String) {
        if (!isAdmin) return
        db.collection("app_notifications").document(notificationId).delete()
            .addOnSuccessListener {
                Log.d("NotificationViewModel", "Successfully deleted notification: $notificationId")
            }
            .addOnFailureListener { e ->
                Log.e("NotificationViewModel", "Failed to delete notification", e)
            }
    }
}
