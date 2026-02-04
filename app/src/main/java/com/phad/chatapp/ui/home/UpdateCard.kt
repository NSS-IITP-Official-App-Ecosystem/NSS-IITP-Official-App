package com.phad.chatapp.ui.home

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun UpdateCard(
    update: Update,
    isAdmin: Boolean = false,
    onEditClick: (Update) -> Unit = {},
    onDeleteClick: (Update) -> Unit = {},
    onReadMoreClick: (Update) -> Unit = {}
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp), // Extra padding for arrow button
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row with Title/Timestamp and Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Title
                        if (!update.title.isNullOrEmpty()) {
                            Text(
                                text = update.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // Timestamp
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatDate(update.timestamp),
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    // Admin Menu
                    if (isAdmin) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Menu",
                                    tint = Color.Gray
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        showMenu = false
                                        onEditClick(update)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        showMenu = false
                                        onDeleteClick(update)
                                    }
                                )
                            }
                        }
                    }
                }

                // Attachments Section - Links + Documents in one horizontal row
                val allLinks = update.getAllLinks()
                val allDocuments = update.getAllDocuments()
                
                if (allLinks.isNotEmpty() || allDocuments.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        // Links first
                        items(allLinks) { link ->
                            SuggestionChip(
                                onClick = {
                                    try {
                                        val url = link.trim()
                                        if (url.isEmpty()) {
                                            android.widget.Toast.makeText(context, "Link is empty", android.widget.Toast.LENGTH_SHORT).show()
                                            return@SuggestionChip
                                        }
                                        
                                        val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                            "https://$url"
                                        } else {
                                            url
                                        }
                                        
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.util.Log.e("UpdateCard", "Failed to open link", e)
                                        android.widget.Toast.makeText(context, "Cannot open link: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text("Link", fontSize = 12.sp) },
                                icon = { Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp)) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Color(0xFFE0E0E0)
                                )
                            )
                        }
                        
                        // Documents after links
                        items(allDocuments) { (url, name) ->
                            DocumentChipWithMenu(
                                documentName = name,
                                documentUrl = url
                            )
                        }
                    }
                }

                // Images Section - Horizontal Scrollable
                val allImages = update.getAllImages()
                if (allImages.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(allImages) { imageUrl ->
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Post Image",
                                modifier = Modifier
                                    .width(250.dp)
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                // Text Content (after images)
                if (!update.content.isNullOrEmpty()) {
                    Text(
                        text = update.content,
                        color = Color.Black,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Arrow button at bottom right - always visible
            IconButton(
                onClick = { onReadMoreClick(update) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .background(Color(0xFFFFC107), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open full post",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
