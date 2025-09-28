package com.phad.chatapp.models

import com.google.firebase.Timestamp

/**
 * Model class for unread messages to display in the unread messages popup
 */
data class UnreadMessage(
    val id: String = "",
    val senderName: String = "",
    val messageText: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val isGroupMessage: Boolean = false,
    val groupId: String = "",
    val chatPartnerId: String = "",
    val lastMessageSender: String = ""
) 