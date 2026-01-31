package com.phad.chatapp.features.scheduling.models

data class TeachingSlotPreset(
    val id: String = "",
    val name: String = "",
    val slots: List<TeachingSlot> = emptyList(),
    val subjects: List<SubjectAllocation> = emptyList()
)

data class TimeSlotInfo(
    val classTime: String = "",       // e.g., "09:00-10:00"
    val freeGroupTime: String = ""    // e.g., "10:00-10:30"
)

data class SubjectAllocation(
    val subjectName: String = "",
    val classCount: Int = 0,
    val priority: Int = 0  // Priority based on position (1 = highest)
)

data class TeachingSlot(
    val id: String = "",
    val time: String = "",  // Kept for backward compatibility
    val timeSlotInfo: TimeSlotInfo = TimeSlotInfo(),
    val freeGroups: List<String> = emptyList()
) 