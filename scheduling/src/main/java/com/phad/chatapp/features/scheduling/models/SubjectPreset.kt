package com.phad.chatapp.features.scheduling.models

/**
 * Data model for subject presets
 * Contains the number of classes for each subject
 */
data class SubjectPreset(
    val id: String = "",
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val subjects: Map<String, Int> = emptyMap() // Subject code to number of classes
)

/**
 * Constants for available subjects
 */
object SubjectConstants {
    const val MATHEMATICS = "MA"
    const val SCIENCE = "SC"
    const val ENGLISH = "EN"
    const val HINDI = "HN"
    const val SANSKRIT = "SA"
    const val SOCIAL_STUDIES = "SS"
    const val BIOLOGY = "BI"
    const val CHEMISTRY = "CH"
    const val PHYSICS = "PY"
    const val COMPUTER = "CM"
    
    val ALL_SUBJECTS = listOf(
        MATHEMATICS,
        SCIENCE,
        ENGLISH,
        HINDI,
        SANSKRIT,
        SOCIAL_STUDIES,
        BIOLOGY,
        CHEMISTRY,
        PHYSICS,
        COMPUTER
    )
    
    val SUBJECT_NAMES = mapOf(
        MATHEMATICS to "Mathematics",
        SCIENCE to "Science",
        ENGLISH to "English",
        HINDI to "Hindi",
        SANSKRIT to "Sanskrit",
        SOCIAL_STUDIES to "Social Studies",
        BIOLOGY to "Biology",
        CHEMISTRY to "Chemistry",
        PHYSICS to "Physics",
        COMPUTER to "Computer"
    )
}

/**
 * Data model for schedule and subject preset pairing
 */
data class ScheduleSubjectPairing(
    val schedulePresetId: String = "",
    val schedulePresetName: String = "",
    val subjectPresetId: String = "",
    val subjectPresetName: String = ""
)
