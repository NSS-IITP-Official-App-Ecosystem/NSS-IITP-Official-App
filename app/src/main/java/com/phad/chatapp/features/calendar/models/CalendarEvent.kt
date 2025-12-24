package com.phad.chatapp.features.calendar.models

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Model class for calendar events
 */
@Parcelize
data class CalendarEvent(
    // The id field without DocumentId annotation since the document already has this field
    val id: String = "",
    
    val date: Date = Date(),
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val eventType: EventType = EventType.TEACHING,
    val timeSlot: String = "",
    val status: EventStatus = EventStatus.SCHEDULED,
    val createdBy: String = "",
    val bookedBy: String = "",
    val bookedByName: String = "",
    val acceptedByRollNumber: String = "",
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable 
