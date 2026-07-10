package com.phad.chatapp.models

data class ContactGroup(
    val title: String = "",
    val icon: String = "phone", // Optional
    val filterByWing: Boolean = false,
    val order: Int = 0,
    val contacts: List<ContactPerson> = emptyList()
)

data class ContactPerson(
    val name: String = "",
    val role: String = "",
    val phone: String = ""
)
