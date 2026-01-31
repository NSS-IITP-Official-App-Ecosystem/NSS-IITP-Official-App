package com.phad.chatapp.ui.home

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.phad.chatapp.R
import com.phad.chatapp.models.Update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReelItem(
    update: Update,
    isVisible: Boolean,
    isAdmin: Boolean = false,
    onUpdateClick: (Update) -> Unit,
    onEditClick: (Update) -> Unit = {},
    onDeleteClick: (Update) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onUpdateClick(update) }
    ) {
        if (update.postType == "text") {
            TextPostView(
                update = update,
                isAdmin = isAdmin,
                onEditClick = onEditClick,
                onDeleteClick = onDeleteClick
            )
        } else {
            // Content Layer for Reels
            when {
            !update.instagramUrl.isNullOrEmpty() -> {
                InstagramPlayer(instagramUrl = update.instagramUrl!!, isVisible = isVisible)
            }
            update.isVideo && !update.mediaUrl.isNullOrEmpty() -> {
                ExoVideoPlayer(videoUrl = update.mediaUrl, isVisible = isVisible)
            }
            !update.imageUrl.isNullOrEmpty() -> {
                AsyncImage(
                    model = update.imageUrl,
                    contentDescription = "Update Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                // Text-only background (gradient)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1E1E1E),
                                    Color(0xFF2D2D2D)
                                )
                            )
                        )
                )
            }
        }

        // Overlay Gradient for better text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        // Info Layer (Bottom)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 60.dp) // Space for bottom navigation if needed
        ) {
            // Author & Date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {


                AsyncImage(
                    model = update.authorImageUrl ?: R.drawable.default_profile_image,
                    contentDescription = "Author",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Gray),
                    contentScale = ContentScale.Crop,
                    error = rememberVectorPainter(Icons.Default.Person) // Use a safe vector icon
                )
                
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = update.authorName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = formatDate(update.timestamp),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
            
            // Title
            if (!update.title.isNullOrEmpty()) {
                Text(
                    text = update.title!!, // Safe given the check
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            
            // Content (Truncated or scrollable in popup)
            if (!update.content.isNullOrEmpty()) {
                Text(
                    text = update.content!!, // Safe given the check
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
            }

            // Attachments Indicators
            if (!update.documentUrl.isNullOrEmpty() || !update.externalLink.isNullOrEmpty()) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    if (!update.documentUrl.isNullOrEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Doc",
                                tint = Color.Yellow,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = " Document",
                                color = Color.Yellow,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (!update.externalLink.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.size(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Link",
                                tint = Color.Cyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = " Link",
                                color = Color.Cyan,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Admin Controls (Top Right)
        if (isAdmin) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .padding(top = 24.dp)
            ) {
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
    }
}


@Composable
fun ExoVideoPlayer(videoUrl: String, isVisible: Boolean) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    // Prepare player when URL changes
    LaunchedEffect(videoUrl) {
        val mediaItem = MediaItem.fromUri(videoUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    // Play/Pause based on visibility
    LaunchedEffect(isVisible) {
        if (isVisible) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            StyledPlayerView(context).apply {
                player = exoPlayer
                useController = false
                resizeMode = com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun InstagramPlayer(instagramUrl: String, isVisible: Boolean) {
    // Instagram embed implementation using official embed script
    // This enables native likes, comments, shares, and view tracking
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                
                // Instagram needs cookies for engagement features
                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                }
                
                webChromeClient = WebChromeClient()
                
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { webView ->
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <style>
                        body { 
                            margin: 0; 
                            padding: 0; 
                            background-color: black; 
                            overflow: hidden;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            height: 100vh;
                        }
                        .instagram-media {
                            max-width: 100% !important;
                            min-width: 326px !important;
                            width: calc(100% - 2px) !important;
                        }
                    </style>
                </head>
                <body>
                    <blockquote class="instagram-media" 
                        data-instgrm-permalink="$instagramUrl" 
                        data-instgrm-version="14"
                        style="background:#FFF; border:0; border-radius:3px; box-shadow:0 0 1px 0 rgba(0,0,0,0.5),0 1px 10px 0 rgba(0,0,0,0.15); margin: 1px; max-width:540px; min-width:326px; padding:0; width:99.375%; width:-webkit-calc(100% - 2px); width:calc(100% - 2px);">
                    </blockquote>
                    <script async src="//www.instagram.com/embed.js"></script>
                </body>
                </html>
            """.trimIndent()

            webView.loadDataWithBaseURL("https://www.instagram.com", html, "text/html", "UTF-8", null)
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
