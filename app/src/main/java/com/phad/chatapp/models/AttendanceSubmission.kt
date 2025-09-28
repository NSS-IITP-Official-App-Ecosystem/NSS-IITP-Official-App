package com.phad.chatapp.models

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class AttendanceSubmission(
    @DocumentId
    val id: String = "",
    @field:PropertyName("rollNumber")
    val rollNumber: String = "",
    @field:PropertyName("studentName")
    val studentName: String = "",
    @field:PropertyName("studentRollNumber")
    val studentRollNumber: String = "",
    @field:PropertyName("eventName")
    val eventName: String = "",
    @field:PropertyName("timestamp")
    val timestamp: @RawValue Timestamp = Timestamp.now(),
    @field:PropertyName("location")
    val location: @RawValue GeoPoint? = null,
    @field:PropertyName("imageUrl")
    val imageUrl: String = "",
    @field:PropertyName("approvalStatus")
    val approvalStatus: String = "Pending", // "Pending", "Approved", or "Rejected"
    @field:PropertyName("approvedBy")
    val approvedBy: String? = null, // Roll number of the admin who approved/rejected
    @field:PropertyName("approvalTimestamp")
    val approvalTimestamp: Timestamp? = null
) : Parcelable 