package com.phad.chatapp.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phad.chatapp.models.Update
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
                    .background(Color(0xFFE6D8EF))
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

            // Attachments (Links and Documents)
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
                            label = { Text("Open Link") },
                            icon = { Icon(Icons.Default.Link, null) },
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
                            label = { Text(update.documentName ?: "Document") },
                            icon = { Icon(Icons.Default.Description, null) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFFE0E0E0)
                            )
                        )
                    }
                }
            }

            // Image
            if (!update.imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = update.imageUrl,
                    contentDescription = "Post Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
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

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("EEEE, MMM dd, yyyy • hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
