package com.phad.chatapp.features.scheduling.models

/**
 * Data model for subject assignments displayed in the view assignments screen
 */
data class SubjectAssignmentDetails(
    val volunteerName: String = "",
    val volunteerRollNo: String = "",
    val volunteerGroup: String = "",
    val subjectCode: String = "",
    val subjectName: String = "",  // Full name like "Mathematics" instead of "MA"
    val dayName: String = "",      // Day name like "Monday" instead of day index
    val slotName: String = "",     // Slot name like "9:00 AM" instead of slot index
    val schoolName: String = "",   // School name extracted from schedule name (e.g., "AM" from "AM 12N")
    val classAndSection: String = "" // Class and section extracted from schedule name (e.g., "12N" from "AM 12N")
)

/**
 * Data model for raw subject assignment data from Firestore
 */
data class RawSubjectAssignment(
    val scheduleName: String = "",
    val assignments: List<AssignmentEntry> = emptyList()
)

/**
 * Data model for individual assignment entries
 */
data class AssignmentEntry(
    val dayIndex: Int = 0,
    val slotIndex: Int = 0,
    val volunteerName: String = "",
    val volunteerRollNo: String = "",
    val volunteerGroup: String = "",
    val subjectCode: String = ""
) 