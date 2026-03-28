package com.phad.chatapp.models

import com.google.firebase.firestore.DocumentId
import java.io.Serializable

data class NotificationItem(
    @DocumentId
    var id: String = "",
    val title: String = "",
    val body: String = "",
    val timestamp: com.google.firebase.Timestamp? = null,
    val type: String = "general", // "general", "event", "update", "important", "mandatory"
    var isRead: Boolean = false,
    val relatedId: String? = null, // e.g., Update ID or Event ID
    val targetType: String? = null, // e.g., "ttw_user", "nss_user"
    val targetUser: String? = null // if it's a direct message to user
) : Serializable
