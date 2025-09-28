package com.phad.chatapp.models

data class Admin(
    val rollNumber: String,
    val name: String,
    val description: String = "",
    val year: Int = 0,
    val contactNumber: String = "",
    val email: String = "",
    val userType: String = "Student",
    val unreadCount: Int = 0,
    val hasImportantMessages: Boolean = false
) 