package com.phad.chatapp.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAE5EE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                                contentDescription = "Options",
                                tint = Color.Gray
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Post") },
                                onClick = {
                                    showMenu = false
                                    onEditClick(update)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Post") },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick(update)
                                }
                            )
                        }
                    }
                }
            }

            // Text Content (before image)
            if (!update.content.isNullOrEmpty()) {
                Text(
                    text = update.content,
                    color = Color.Black,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = if (update.imageUrl.isNullOrEmpty()) 10 else 5,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        hasOverflow = result.hasVisualOverflow
                    }
                )
            }

            // Image (full width, rounded)
            if (!update.imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = update.imageUrl,
                    contentDescription = "Post Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // Attachments (Links and Documents) - at bottom
            if (!update.externalLink.isNullOrEmpty() || !update.documentUrl.isNullOrEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!update.externalLink.isNullOrEmpty()) {
                        SuggestionChip(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.externalLink))
                                context.startActivity(intent)
                            },
                            label = { Text("Link", fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp)) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFFE0E0E0)
                            )
                        )
                    }

                    if (!update.documentUrl.isNullOrEmpty()) {
                        SuggestionChip(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.documentUrl))
                                context.startActivity(intent)
                            },
                            label = { Text("Document", fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp)) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFFE0E0E0)
                            )
                        )
                    }
                }
            }

            // Read more button
            if (hasOverflow || (!update.content.isNullOrEmpty() && update.content.length > 300)) {
                TextButton(
                    onClick = { onReadMoreClick(update) },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(
                        "Read more...",
                        color = Color(0xFF816DB2),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
