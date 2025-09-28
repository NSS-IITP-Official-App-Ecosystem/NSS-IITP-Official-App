package com.phad.chatapp.models

import com.google.firebase.firestore.DocumentId
import java.util.Date

data class Task(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val dueDate: Date? = null,
    val isCompleted: Boolean = false,
    val createdAt: Date = Date(),
    val completedAt: Date? = null
) 