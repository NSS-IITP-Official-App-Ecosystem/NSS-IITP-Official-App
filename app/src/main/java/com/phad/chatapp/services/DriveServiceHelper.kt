package com.phad.chatapp.services

import android.net.Uri
import android.util.Log

/**
 * Helper class for handling Google Drive operations and URL conversions
 */
object DriveServiceHelper {
    private const val TAG = "DriveServiceHelper"
    
    /**
     * Converts a Google Drive sharing URL to a direct media URL for display purposes
     * @param driveUrl The original Google Drive URL
     * @return A direct URL that can be used to display the media
     */
    fun getDirectMediaUrl(driveUrl: String): String {
        Log.d(TAG, "Converting drive URL: $driveUrl")
        
        try {
            // Extract file ID from Drive URL
            val fileId = extractFileId(driveUrl)
            if (fileId.isNotEmpty()) {
                // Create direct download link
                return "https://drive.google.com/uc?export=view&id=$fileId"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting Drive URL: ${e.message}")
        }
        
        // Return original URL if conversion fails
        return driveUrl
    }
    
    /**
     * Converts a Google Drive document URL to a direct URL for viewing
     * @param documentUrl The original Google Drive document URL
     * @return A direct URL that can be used to view the document
     */
    fun getDirectDocumentUrl(documentUrl: String): String {
        Log.d(TAG, "Converting document URL: $documentUrl")
        
        try {
            // Extract file ID from Drive URL
            val fileId = extractFileId(documentUrl)
            if (fileId.isNotEmpty()) {
                // Create direct view link
                return "https://drive.google.com/file/d/$fileId/view"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting document URL: ${e.message}")
        }
        
        // Return original URL if conversion fails
        return documentUrl
    }
    
    /**
     * Extracts the file ID from a Google Drive URL
     * @param driveUrl The Google Drive URL
     * @return The extracted file ID
     */
    private fun extractFileId(driveUrl: String): String {
        val uri = Uri.parse(driveUrl)
        
        // Try different URL formats
        when {
            // Format: https://drive.google.com/file/d/{fileId}/view
            driveUrl.contains("/file/d/") -> {
                val parts = driveUrl.split("/file/d/")
                if (parts.size > 1) {
                    val fileIdParts = parts[1].split("/")
                    if (fileIdParts.isNotEmpty()) {
                        return fileIdParts[0]
                    }
                }
            }
            
            // Format: https://drive.google.com/open?id={fileId}
            uri.getQueryParameter("id") != null -> {
                return uri.getQueryParameter("id") ?: ""
            }
            
            // Format: https://docs.google.com/document/d/{fileId}/edit
            driveUrl.contains("/document/d/") || 
            driveUrl.contains("/spreadsheets/d/") ||
            driveUrl.contains("/presentation/d/") -> {
                val regex = ".*/d/([a-zA-Z0-9-_]+).*".toRegex()
                val matchResult = regex.find(driveUrl)
                if (matchResult != null && matchResult.groupValues.size > 1) {
                    return matchResult.groupValues[1]
                }
            }
        }
        
        return ""
    }
} 