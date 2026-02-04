package com.phad.chatapp.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phad.chatapp.models.Update

@Composable
fun TextPostView(
    update: Update,
    isAdmin: Boolean = false,
    onEditClick: (Update) -> Unit = {},
    onDeleteClick: (Update) -> Unit = {},
    onDismiss: () -> Unit = {},
    isInFullView: Boolean = false,
    onReadMoreClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) } // State for Admin Menu
    val scrollState = rememberScrollState()

    // Determine if text is long enough to need expansion
    // This is an estimation. For precise calculation, we'd need TextLayoutResult.
    // For now, we assume > 200 chars or > 4 lines logic is handled by maxLines visually,
    // but the "Read more" button logic relies on us knowing.
    // A simpler approach: Always show truncated Text. If it overflows, show button.
    // Standard Compose way involves `onTextLayout`.
    // Standard Compose way involves `onTextLayout`.
    var hasOverflow by remember { mutableStateOf(false) }
    // If in Full View, always expanded. If not, local toggle?
    // User wants: In Feed (Not Full View), it's truncated. Clicking "Read more" opens Full View.
    // So "isExpanded" is really "isInFullView" OR "local expansion".
    // Actually, if isInFullView is true, we force full text.
    // If isInFullView is false, we constrain and show button.
    
    // We can use a local state for animation if we wanted to expand in place, but user wants to open new screen.
    // So "Read more" click -> onReadMoreClick().
    
    // Overriding isExpanded based on param
    val showFullContent = isInFullView || isExpanded


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White) // Restored White background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .fillMaxSize()
                // Only enable scroll if showing full content (Dialog Mode)
                .then(if (showFullContent) Modifier.verticalScroll(scrollState) else Modifier)
        ) {
            // Header: Top Rectangle with Cross Button, Title, and Time (EXACT REPLICA of ReelItem)
            Row(
                 modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121212)) // Darker Header
                    .padding(12.dp),
                 verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Title
                    if (!update.title.isNullOrEmpty()) {
                        Text(
                            text = update.title,
                            fontSize = 14.sp, 
                            fontWeight = FontWeight.Bold,
                            color = Color.White, // Header text white
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // Time Stamp
                    Text(
                        text = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(update.timestamp)),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
                
                // Admin Controls (Top Right)
                if (isAdmin) {
                    Box {
                        androidx.compose.material3.IconButton(
                            onClick = { showMenu = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = Color.White
                            )
                        }
                        
                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Edit Post") },
                                onClick = { 
                                    showMenu = false
                                    onEditClick(update)
                                }
                            )
                            androidx.compose.material3.DropdownMenuItem(
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

            Spacer(modifier = Modifier.height(16.dp))

            // Attachments (Document / Link) - Moved ABOVE Body
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!update.documentUrl.isNullOrEmpty()) {
                    SuggestionChip(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.documentUrl))
                            context.startActivity(intent)
                        },
                        label = { Text(update.documentName ?: "Document", color = Color.Black) },
                        icon = { Icon(Icons.Default.Description, null, tint = Color.Blue) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFFEEEEEE))
                    )
                }
                
                if (!update.externalLink.isNullOrEmpty()) {
                    SuggestionChip(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.externalLink))
                            context.startActivity(intent)
                        },
                        label = { Text("Visit Link", color = Color.Black) },
                        icon = { Icon(Icons.Default.Link, null, tint = Color.Blue) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFFEEEEEE))
                    )
                }
            }

            // Inline Media (Image) - Moved ABOVE Body
            if (!update.imageUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = update.imageUrl,
                    contentDescription = "Post Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp) // Fixed height in flow, or wrap content
                        .clickable { /* View Fullscreen */ },
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Body
            if (!update.content.isNullOrEmpty()) {
                Box(modifier = Modifier.animateContentSize().padding(horizontal = 16.dp)) {
                    if (showFullContent) {
                        Text(
                            text = update.content,
                            color = Color.Black, // Text Black
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        )
                    } else {
                        Column {
                            Text(
                                text = update.content,
                                color = Color.Black, // Text Black
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                maxLines = 12, // Reduced to 12
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { result ->
                                    hasOverflow = result.hasVisualOverflow
                                }
                            )
                            if (hasOverflow || update.content.length > 300) { // Fallback check
                                TextButton(
                                    onClick = { 
                                        if (isInFullView) {
                                            isExpanded = true // Should not happen if logic is correct
                                        } else {
                                            onReadMoreClick() 
                                        }
                                    },
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text("Read more...", color = Color(0xFF42A5F5))
                                }
                            }
                        }
                    }
                }
            }
            
            // Spacer at bottom
             Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
