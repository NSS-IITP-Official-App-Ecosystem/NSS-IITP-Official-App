package com.phad.chatapp.models

import com.google.firebase.firestore.DocumentId

data class User(
    @DocumentId
    var id: String = "",
    var name: String = "",
    var rollNumber: String = "",
    var instituteOutlookId: String = "",
    var uid: String = "",
    var unreadCount: Int = 0,
    var userType: String = "student",
    var wings: List<String> = emptyList(),
    // Legacy/optional fields retained for compatibility with existing UI and repos
    var email: String = "",                 // Prefer instituteOutlookId; kept for backward-compat
    var description: String = "",           // Optional user bio/notes
    var contactNumber: String = "",         // Optional contact
    var profileImageUrl: String? = null,     // Optional profile image URL
    var referenceImageUrl: String? = null,   // Optional face recognition reference image URL
    var year: String? = null                 // Deprecated; keep to avoid crashes during migration
)

/**
 * Setter kept for backward-compat with older code paths that attempted to set a dynamic year.
 * The new schema does not use year; we normalize the provided value to String and store in [year].
 */
fun User.setYear(value: Any?) {
    year = value?.toString()
}