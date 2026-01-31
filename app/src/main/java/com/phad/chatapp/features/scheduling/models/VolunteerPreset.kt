package com.project.thephadproject.models

import com.google.firebase.Timestamp

data class VolunteerPreset(
    val id: String = "",
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val volunteerIds: List<String> = emptyList(),
    val volunteerCount: Int = 0,
    val groupCounts: Map<String, Int> = emptyMap(), // Map of group number to count of volunteers
    val volunteers: List<VolunteerInfo> = emptyList() // Adding back detailed volunteer information
)

// Detailed information about a volunteer
data class VolunteerInfo(
    val rollNo: String = "",
    val name: String = "",
    val group: String = "",
    val classCount: Int = 0  // Number of classes this volunteer can teach
)

// Detailed information about a volunteer assignment
data class VolunteerAssignment(
    val id: String = "",
    val teachingSlotPresetId: String = "",
    val teachingSlotPresetName: String = "",
    val volunteerPresetId: String = "",
    val day: String = "",
    val slotIndex: Int = 0,
    // Detailed volunteer info
    val volunteerId: String = "",
    val volunteerName: String = "",
    val volunteerRollNo: String = "",
    val volunteerGroup: String = "",
    val assignedAt: Timestamp? = null
)