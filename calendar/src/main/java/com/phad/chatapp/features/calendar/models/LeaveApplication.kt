package com.phad.chatapp.features.calendar.models

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Model class for leave applications
 */
@Parcelize
data class LeaveApplication(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val rollNumber: String = "",
    val date: Date = Date(),
    val slot: String = "",
    val subject: String = "",
    val school: String = "",
    val status: EventStatus = EventStatus.PENDING,
    val timestamp: Long = 0,
    val substitutedByRollNumber: String = ""
) : Parcelable 