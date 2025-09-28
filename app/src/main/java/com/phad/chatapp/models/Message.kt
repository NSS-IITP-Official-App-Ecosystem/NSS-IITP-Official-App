package com.phad.chatapp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Message(
    @DocumentId
    var id: String = "",
    val text: String = "",
    val sender: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val read: Map<String, Boolean> = emptyMap(),
    
    // Media fields
    val mediaUrl: String = "",
    val mediaType: String = "", // "image", "audio", "document"
    var mentionsEveryone: Boolean = false,
    var taggedUsers: List<String> = emptyList(),
    var groupId: String = "",
    var receiver: String = "",
    var contentType: String = ""  // "image", "audio", "document" for Drive files
) 