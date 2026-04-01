package com.phad.chatapp.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import java.io.Serializable

data class NotificationItem(
    @DocumentId
    var id: String = "",
    var title: String = "",
    var body: String = "",
    var timestamp: com.google.firebase.Timestamp? = null,
    var type: String = "general",
    // @PropertyName needed because Firestore uses Java reflection which treats
    // 'isXxx' boolean fields differently (looks for setter 'setRead' not 'setIsRead')
    @get:PropertyName("isRead")
    @set:PropertyName("isRead")
    var isRead: Boolean = false,
    var relatedId: String? = null,
    var targetType: String? = null,
    var targetTopics: List<String> = emptyList(),
    var targetUser: String? = null,
    var creatorId: String? = null,
    var targetRole: String? = null,
    var targetWing: String? = null
) : Serializable
