package com.phad.chatapp.models

import java.io.Serializable
import java.util.Date
import java.util.concurrent.TimeUnit

data class Update(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorImageUrl: String? = null,
    val title: String = "", // Changed from String? to String since title is now required
    val content: String = "",
    val externalLink: String? = null,
    val documentName: String? = null,
    val documentUrl: String? = null,
    val imageName: String? = null,
    val imageUrl: String? = null,
    val mediaUrl: String? = null, // Keep for backward compatibility
    val timestamp: Long = 0,
    val updateType: Int = 1 // 1=Teaching Wing, 2=NSS only, 3=Both
) : Serializable {
    // No-argument constructor for Firestore
    constructor() : this(
        id = "",
        authorId = "",
        authorName = "",
        authorImageUrl = null,
        title = "", // Changed from null to empty string
        content = "",
        externalLink = null,
        documentName = null,
        documentUrl = null,
        imageName = null,
        imageUrl = null,
        mediaUrl = null,
        timestamp = 0,
        updateType = 1
    )
    
    fun getTimeAgo(): String {
        val now = Date().time
        val diff = now - timestamp
        
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} min ago"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)} hr ago"
            diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)} days ago"
            else -> {
                val date = Date(timestamp)
                val formatter = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                formatter.format(date)
            }
        }
    }
    
    
    fun getUpdateTypeDisplay(): String {
        return when (updateType) {
            1 -> "Teaching Wing"
            2 -> "NSS"
            3 -> "Teaching Wing + NSS"
            else -> "Unknown"
        }
    }
} 