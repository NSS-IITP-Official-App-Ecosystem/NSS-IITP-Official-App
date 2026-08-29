package com.phad.chatapp.ui.home

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.phad.chatapp.models.Update
import java.text.SimpleDateFormat
import java.util.*

/**
 * Determines if an Instagram URL is a reel or post
 * Reels use 9:16 aspect ratio, posts use 4:5
 */
private fun isInstagramReel(url: String): Boolean {
    return url.contains("/reel/")
}

@Composable
fun ReelCard(
    update: Update,
    isAdmin: Boolean = false,
    onEditClick: (Update) -> Unit = {},
    onDeleteClick: (Update) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                    
                    // Target Wings
                    if (!update.targetWings.isNullOrEmpty()) {
                        val wingsText = update.targetWings.joinToString(", ")
                        Text(
                            text = "For: $wingsText",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
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

            // Reel Content Area with conditional aspect ratio
            // Reels: 9:16, Posts: 4:5
            val aspectRatio = if (!update.instagramUrl.isNullOrEmpty() && !isInstagramReel(update.instagramUrl!!)) {
                5f / 8f // Instagram post ratio
            } else {
                9f / 16.5f // Instagram reel ratio
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                when {
                    !update.instagramUrl.isNullOrEmpty() -> {
                        InstagramEmbedPlayer(
                            instagramUrl = update.instagramUrl!!
                        )
                    }
                    update.isVideo && !update.mediaUrl.isNullOrEmpty() -> {
                        ExoVideoPlayer(
                            videoUrl = update.mediaUrl,
                            isVisible = true,
                            fitContent = true
                        )
                    }
                    else -> {
                        // Fallback for text or no media
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No media available",
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Instagram Embed Player for Cards
 * Uses Instagram's official blockquote + embed.js method for better compatibility
 */
@Composable
fun InstagramEmbedPlayer(
    instagramUrl: String
) {
    val context = LocalContext.current
    // Use original URL (not /embed) for blockquote method
    val cleanUrl = instagramUrl.trimEnd('/')
    var loadFailed by remember { mutableStateOf(false) }

    if (loadFailed) {
        // Fallback UI for unavailable reels
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Reel unavailable",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "This reel may be private or deleted",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(instagramUrl))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE1306C) // Instagram pink
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open in Instagram")
                }
            }
        }
    } else {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    // WebView Settings
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        
                        // Allow content access
                        allowContentAccess = true
                        allowFileAccess = true
                        
                        // Disable zoom controls
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                        
                        // Performance settings
                        cacheMode = WebSettings.LOAD_DEFAULT
                        
                        // Enable viewport
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    
                    // Disable WebView internal scrolling
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    
                    // Cookie management for Instagram engagement features
                    val cookieManager = android.webkit.CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                    }
                    
                    webChromeClient = WebChromeClient()
                    
                    // Handle load errors
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            if (errorCode != WebViewClient.ERROR_UNKNOWN) {
                                loadFailed = true
                            }
                        }
                    }
                    
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    // Use Instagram's blockquote embed method (more reliable than iframe)
                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                            <style>
                                * {
                                    margin: 0;
                                    padding: 0;
                                    box-sizing: border-box;
                                }
                                body { 
                                    margin: 0; 
                                    padding: 0; 
                                    background-color: black; 
                                    overflow: hidden;
                                    display: flex;
                                    align-items: center;
                                    justify-content: center;
                                    height: 100vh;
                                    width: 100vw;
                                }
                                .instagram-media {
                                    max-width: 100% !important;
                                    min-width: 100% !important;
                                    width: 100% !important;
                                }
                                .instagram-media-rendered {
                                    margin: 0 !important;
                                }
                            </style>
                        </head>
                        <body>
                            <blockquote class="instagram-media" 
                                data-instgrm-permalink="$cleanUrl" 
                                data-instgrm-version="14"
                                style="background:#000; border:0; margin:0; padding:0; width:100%;">
                            </blockquote>
                            <script async src="//www.instagram.com/embed.js"></script>
                        </body>
                        </html>
                    """.trimIndent()
                    
                    loadDataWithBaseURL("https://www.instagram.com", html, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
