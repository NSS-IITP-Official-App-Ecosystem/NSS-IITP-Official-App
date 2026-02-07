package com.phad.chatapp.ui.home

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.core.content.FileProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phad.chatapp.models.Update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun UpdateDetailScreen(
    update: Update,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header with circular back button only
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular back button
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFF8E1))
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.Black
                )
            }
        }

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Timestamp
            Text(
                text = formatDate(update.timestamp),
                color = Color.Gray,
                fontSize = 13.sp
            )

            // Title
            if (!update.title.isNullOrEmpty()) {
                Text(
                    text = update.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            // Links Section
            val allLinks = update.getAllLinks()
            if (allLinks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    allLinks.forEach { link ->
                        AttachmentBox(
                            text = link,
                            icon = Icons.Default.Link,
                            isLink = true,
                            onClick = {
                                try {
                                    val url = link.trim()
                                    if (url.isEmpty()) return@AttachmentBox
                                    
                                    val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                        "https://$url"
                                    } else {
                                        url
                                    }
                                    
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Cannot open link", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // Documents Section
            val allDocuments = update.getAllDocuments()
            if (allDocuments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    allDocuments.forEach { (url, name) ->
                        DocumentChipWithMenu(
                            documentName = name,
                            documentUrl = url
                        )
                    }
                }
            }

            // Images Section - Horizontal Scrollable Row
            val allImages = update.getAllImages()
            if (allImages.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allImages) { imageUrl ->
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Post Image",
                            modifier = Modifier
                                .width(300.dp)
                                .heightIn(max = 400.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            // Full Content
            if (!update.content.isNullOrEmpty()) {
                Text(
                    text = update.content,
                    color = Color.Black,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
            }
            
            // Bottom spacing
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DocumentChipWithMenu(
    documentName: String,
    documentUrl: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Helper function to open document (Preview logic)
    fun openDocument() {
        scope.launch {
            try {
                android.widget.Toast.makeText(context, "Opening $documentName...", android.widget.Toast.LENGTH_SHORT).show()
                
                withContext(Dispatchers.IO) {
                    val cacheDir = context.cacheDir
                    val file = File(cacheDir, documentName)
                    
                    // Download file to cache
                    URL(documentUrl).openStream().use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        // Open file with appropriate app
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                "No PDF viewer app found",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DocumentChip", "Failed to open document", e)
                android.widget.Toast.makeText(
                    context,
                    "Failed to open document: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Helper function to download to external storage
    fun downloadDocument() {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(documentUrl))
                .setTitle(documentName)
                .setDescription("Downloading document")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, documentName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            downloadManager.enqueue(request)
            android.widget.Toast.makeText(context, "Downloading $documentName...", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("DocumentChip", "Failed to download", e)
            android.widget.Toast.makeText(
                context,
                "Failed to download: ${e.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Matched style with AttachmentBox (Links)
    Row(
        modifier = Modifier
            .wrapContentWidth()
            .height(40.dp)
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
            .background(Color.White, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                openDocument() // Just open on chip click
            }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Document Icon (Left)
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = Color(0xFF1E88E5), // Blue tint
            modifier = Modifier.size(20.dp)
        )

        Text(
            text = documentName,
            color = Color.Black,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp) // Limit width
        )
        
        Spacer(modifier = Modifier.width(4.dp))

        // Download Icon (Right)
        IconButton(
            onClick = {
                downloadDocument() // Download to storage
                openDocument()     // AND Open immediately
            },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download",
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("EEEE, MMM dd, yyyy • hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
