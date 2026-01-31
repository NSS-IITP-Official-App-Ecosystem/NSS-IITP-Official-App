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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
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
    onDeleteClick: (Update) -> Unit = {}
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Determine if text is long enough to need expansion
    // This is an estimation. For precise calculation, we'd need TextLayoutResult.
    // For now, we assume > 200 chars or > 4 lines logic is handled by maxLines visually,
    // but the "Read more" button logic relies on us knowing.
    // A simpler approach: Always show truncated Text. If it overflows, show button.
    // Standard Compose way involves `onTextLayout`.
    var hasOverflow by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E)) // Dark background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                // Only enable scroll if expanded
                .then(if (isExpanded) Modifier.verticalScroll(scrollState) else Modifier)
        ) {
            // Header: Author
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                AsyncImage(
                    model = update.authorImageUrl ?: com.phad.chatapp.R.drawable.default_profile_image,
                    contentDescription = "Author",
                    modifier = Modifier.size(40.dp).clickable { /* Profile Click */ },
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = update.authorName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = java.text.SimpleDateFormat("MMM dd • hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(update.timestamp)),
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                
                // Admin Actions
                if (isAdmin) {
                   // ... (Ideally reuse admin menu logic, or simple icon here)
                   // Since ReelItem has it, we should probably extract it or duplicate short logic.
                   // I'll skip embedded menu for now to focus on content layout.
                   // Or pass a lambda to show controls. 
                }
            }

            // Title
            if (!update.title.isNullOrEmpty()) {
                Text(
                    text = update.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Body
            if (!update.content.isNullOrEmpty()) {
                Box(modifier = Modifier.animateContentSize()) {
                    if (isExpanded) {
                        Text(
                            text = update.content,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        )
                    } else {
                        Column {
                            Text(
                                text = update.content,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { result ->
                                    hasOverflow = result.hasVisualOverflow
                                }
                            )
                            if (hasOverflow || update.content.length > 300) { // Fallback check
                                TextButton(
                                    onClick = { isExpanded = true },
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text("Read more...", color = Color(0xFF42A5F5))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Inline Media (Image)
            if (!update.imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = update.imageUrl,
                    contentDescription = "Post Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp) // Fixed height in flow, or wrap content
                        .clickable { /* View Fullscreen */ },
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Attachments (Document / Link)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!update.documentUrl.isNullOrEmpty()) {
                    SuggestionChip(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.documentUrl))
                            context.startActivity(intent)
                        },
                        label = { Text(update.documentName ?: "Document", color = Color.White) },
                        icon = { Icon(Icons.Default.Description, null, tint = Color.Yellow) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color.DarkGray)
                    )
                }
                
                if (!update.externalLink.isNullOrEmpty()) {
                    SuggestionChip(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.externalLink))
                            context.startActivity(intent)
                        },
                        label = { Text("Visit Link", color = Color.White) },
                        icon = { Icon(Icons.Default.Link, null, tint = Color.Cyan) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color.DarkGray)
                    )
                }
            }
        }
    }
}
