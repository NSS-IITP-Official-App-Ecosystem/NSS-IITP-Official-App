package com.project.thephadproject.models

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val rollNumber: String = "",
    val contactNumber: String = "",
    val userType: String = "",
    val group: String = "",
    val isVolunteer: Boolean = false
) 