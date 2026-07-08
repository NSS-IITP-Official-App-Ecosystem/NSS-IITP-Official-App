package com.phad.chatapp.models

data class Issue(
    val id: String = "",
    val name: String = "",
    val rollNumber: String = "",
    val category: String = "",
    val addressedTo: String = "",
    val subject: String = "",
    val description: String = "",
    val photoUrl: String? = null,
    val photoPublicId: String? = null,
    val status: IssueStatus = IssueStatus.OPEN,
    val resolvedByRollNumber: String? = null,
    val resolvedByName: String? = null,
    val resolveComment: String? = null,
    val resolveTimestamp: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class IssueStatus {
    OPEN,
    CLOSED
}
