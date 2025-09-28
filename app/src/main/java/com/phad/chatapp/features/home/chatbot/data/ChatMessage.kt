package com.phad.chatapp.features.home.chatbot.data

import java.time.LocalDateTime

data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: LocalDateTime,
    val isGenerating: Boolean = false
) 