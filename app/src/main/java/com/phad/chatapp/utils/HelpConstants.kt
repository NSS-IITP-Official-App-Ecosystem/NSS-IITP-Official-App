package com.phad.chatapp.utils

object HelpConstants {
    val ISSUE_CATEGORIES = listOf(
        "General Inquiry",
        "Technical Issue",
        "Attendance Dispute",
        "Event Feedback",
        "Harassment / Disciplinary",
        "Other"
    )

    // File size limit in bytes (1MB)
    const val MAX_ATTACHMENT_SIZE_BYTES = 1024 * 1024L
    
    // Default network timeout for Issue Transactions
    const val NETWORK_TIMEOUT_MS = 15000L

}
