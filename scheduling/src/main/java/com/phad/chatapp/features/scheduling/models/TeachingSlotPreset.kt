package com.project.thephadproject.models

data class TeachingSlotPreset(
    val id: String = "",
    val name: String = "",
    val slots: List<TeachingSlot> = emptyList()
)

data class TeachingSlot(
    val id: String = "",
    val time: String = "",
    val freeGroups: List<String> = emptyList()
) 