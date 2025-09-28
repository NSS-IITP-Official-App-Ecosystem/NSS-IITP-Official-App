package com.phad.chatapp.features.home.chatbot.data

data class QuestionNode(
    val id: String = "",
    val text: String = "",
    val answer: String? = null,
    val children: Map<String, QuestionNode> = emptyMap(),
    val isLeaf: Boolean = false
) {
    companion object {
        const val SUPPORT_EMAIL = "abc123@gmail.com"
    }
} 