package com.phad.chatapp.models

import java.io.Serializable
import java.util.Date
import java.util.concurrent.TimeUnit

data class Update(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorImageUrl: String? = null,
    val title: String? = null,
    val content: String? = null,
    val externalLink: String? = null,
    val documentName: String? = null,
    val documentUrl: String? = null,
    val imageName: String? = null,
    val imageUrl: String? = null,
    val mediaUrl: String? = null, // Keep for backward compatibility
    val isVideo: Boolean = false, // New field to identify video updates
    val instagramUrl: String? = null, // Store Instagram Reel/Post URL for native engagement
    val postType: String = "text", // "reel" or "text" - determines post display mode
    val hasExternalLink: Boolean = false, // Flag for posts containing clickable links
    val viewCount: Long = 0, // Track post views for analytics
    val likeCount: Long = 0, // Track post likes for engagement
    val timestamp: Long = 0,
    val updateType: Int = 1 // 1=Teaching Wing, 2=NSS only, 3=Both
) : Serializable {
    // No-argument constructor for Firestore
    constructor() : this(
        id = "",
        authorId = "",
        authorName = "",
        authorImageUrl = null,
        title = null,
        content = null,
        externalLink = null,
        documentName = null,
        documentUrl = null,
        imageName = null,
        imageUrl = null,
        mediaUrl = null,
        isVideo = false, // New field for video updates
        instagramUrl = null, // New field for Instagram URL
        postType = "text",
        hasExternalLink = false,
        viewCount = 0,
        likeCount = 0,
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