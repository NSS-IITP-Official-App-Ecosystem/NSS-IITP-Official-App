package com.phad.chatapp.utils

object HelpConstants {
    const val HELP_AMBULANCE_NUMBER = "108"

    val ISSUE_CATEGORIES = listOf(
        "General Inquiry",
        "Technical Issue",
        "Attendance Dispute",
        "Event Feedback",
        "Harassment / Disciplinary",
        "Other"
    )

    // Base addresses for the form
    val BASE_ADDRESSED_TO_OPTIONS = listOf(
        "NSS Admin",
        "Technical Team",
        "General Secretary"
    )

    fun getAddressedToOptions(userWings: List<String>): List<String> {
        val options = BASE_ADDRESSED_TO_OPTIONS.toMutableList()
        userWings.forEach { wing ->
            // Normalize "Design and Curation" missing "Wing" at end
            val normalizedWing = if (wing.endsWith("Wing")) wing else "$wing Wing"
            options.add("Wing Subcoord - $normalizedWing")
        }
        return options
    }
    
    // File size limit in bytes (100KB)
    const val MAX_ATTACHMENT_SIZE_BYTES = 100 * 1024L
    
    // Default network timeout for Issue Transactions
    const val NETWORK_TIMEOUT_MS = 15000L

}
