package com.phad.chatapp.models

data class ChatPreview(
    val id: String,
    val name: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int = 0,
    val avatarUrl: String? = null
) 