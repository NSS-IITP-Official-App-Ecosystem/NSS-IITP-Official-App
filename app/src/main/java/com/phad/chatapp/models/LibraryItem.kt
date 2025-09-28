package com.phad.chatapp.models

data class LibraryItem(
    val id: String, // Firestore document ID
    val title: String,
    val path: String, // Firestore path to this item's document
    val googleDriveLink: String? = null, // Google Drive link for files
    val mimeType: String? = null, // MIME type for files
    val isSection: Boolean = false // Indicates if the item is a section/folder (true) or a file (false)
) 