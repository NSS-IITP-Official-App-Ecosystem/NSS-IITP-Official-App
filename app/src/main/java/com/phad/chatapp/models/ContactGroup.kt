package com.phad.chatapp.models

import com.google.firebase.firestore.DocumentId

data class ContactGroup(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val icon: String = "phone", // Optional
    val filterByWing: Boolean = false,
    val order: Int = 0,
    val contacts: List<ContactPerson> = emptyList()
)

data class ContactPerson(
    val name: String = "",
    val role: String = "",
    val phone: String? = null,
    val email: String? = null,
    val rollNumber: String? = null
)
