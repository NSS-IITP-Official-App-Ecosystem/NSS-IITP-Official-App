package com.phad.chatapp.features.scheduling.models

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
