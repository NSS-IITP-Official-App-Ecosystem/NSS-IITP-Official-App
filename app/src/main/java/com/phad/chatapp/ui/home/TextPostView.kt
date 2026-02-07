package com.phad.chatapp.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
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
                // Show Documents from list (if available) or legacy single field (fallback)
                val docs = update.documentUrls ?: if (!update.documentUrl.isNullOrEmpty()) listOf(update.documentUrl) else emptyList()
                val docNames = update.documentNames // Should be list, fallback loop if legacy single
                
                docs.forEachIndexed { index, url ->
                    val name = docNames?.getOrNull(index) ?: update.documentName ?: "Document"
                    AttachmentBox(
                        text = name,
                        icon = Icons.Default.Description,
                        isLink = false,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    )
                }

                // Show Links from list or legacy
                val links = update.externalLinks ?: if (!update.externalLink.isNullOrEmpty()) listOf(update.externalLink) else emptyList()
                
                links.forEach { linkUrl ->
                    if (!linkUrl.isNullOrEmpty()) {
                        AttachmentBox(
                            text = linkUrl,
                            icon = Icons.Default.Link,
                            isLink = true,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl))
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }

            // Inline Media (Image) - Moved ABOVE Body
            if (update.imageUrls != null && update.imageUrls.isNotEmpty()) {
                 Spacer(modifier = Modifier.height(12.dp))
                 // Show only first image for feed view, or pager? 
                 // For simplified feed list, showing first image or pager is common. 
                 // Let's simple show first image for now as per `TextPostView` legacy behavior, 
                 // or column of images? User didn't specify multiple image view in feed, just "upload progress".
                 // BUT `TextPostView` is likely used in Feed. 
                 // If I just show first, it matches legacy.
                 // Ideally a Horizontal Pager.
                 // Sticking to legacy single image view logic for now to avoid scope creep, using first image.
                 AsyncImage(
                    model = update.imageUrls.first(),
                    contentDescription = "Post Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clickable { /* View Fullscreen */ },
                    contentScale = ContentScale.Crop
                )
            } else if (!update.imageUrl.isNullOrEmpty()) {
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

@Composable
fun AttachmentBox(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLink: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .then(if (isLink) Modifier.width(188.dp) else Modifier.wrapContentWidth()) // Fixed width for links (0.75x of 250dp), wrap for docs
            .height(40.dp) // 3. Same Height
            .border(1.dp, Color(0xFFE0E0E0), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) // 3. Style Border
            .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon at left
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF1E88E5), // Blue tint
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Text
        Text(
            text = text,
            color = Color.Black, // 3. Font Size same
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = isLink) // Fill if link to allow truncation at end of fixed width
        )
        
        // Download Icon for Docs (right side)
        if (!isLink) {
             Spacer(modifier = Modifier.width(8.dp))
             Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download",
                 tint = Color.Gray,
                 modifier = Modifier.size(16.dp)
             )
        }
    }
}
