package com.phad.chatapp.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DownloadUtils {
    private const val TAG = "DownloadUtils"

    fun downloadAttachment(context: Context, url: String, isPdf: Boolean, isImage: Boolean) {
        Toast.makeText(context, "Opening attachment...", Toast.LENGTH_SHORT).show()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Download the file locally to the app's document cache
                val localUri = withContext(Dispatchers.IO) {
                    FileStorageUtils.downloadDocument(context, url)
                }
                
                if (localUri != null) {
                    val mimeType = when {
                        isPdf -> "application/pdf"
                        isImage -> "image/jpeg"
                        else -> "*/*"
                    }
                    
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(localUri, mimeType)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error opening attachment with specific mime type: ${e.message}", e)
                        // Try generic mime type
                        val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(localUri, "*/*")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(genericIntent)
                    }
                } else {
                    // Fallback to browser
                    Toast.makeText(context, "Opening in browser instead...", Toast.LENGTH_SHORT).show()
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(browserIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling attachment: ${e.message}", e)
                Toast.makeText(context, "Could not open attachment", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
