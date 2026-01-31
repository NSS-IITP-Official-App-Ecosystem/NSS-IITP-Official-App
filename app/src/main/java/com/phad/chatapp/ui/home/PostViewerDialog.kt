package com.phad.chatapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phad.chatapp.models.Update

@Composable
fun PostViewerDialog(
    updates: List<Update>,
    initialPage: Int = 0,
    isAdmin: Boolean = false,
    onDismiss: () -> Unit,
    onUpdateClick: (Update) -> Unit,
    onEditClick: (Update) -> Unit = {},
    onDeleteClick: (Update) -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // Full screen dialog
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent) // Transparent to show blur effect if possible, but Dialog implies new window.
                // Note: Real-time blur over underlying activity in Compose Dialog is tricky.
                // We simulated "blur" by using a semi-transparent dark background 
                // because native blur requires API 31+ or RenderEffect on the underlying view.
                // User asked for "background being blur". We can try Modifier.blur on the content BEHIND, 
                // but since this is a Dialog, we can't easily blur the Activity below from here.
                // Best approximation: Semi-transparent scrim.
                .background(Color.Black.copy(alpha = 0.85f)) 
        ) {
            // Rounded Container with Margins
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 48.dp, horizontal = 24.dp) // Margin from edges
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
            ) {
                ReelFeed(
                    updates = updates,
                    initialPage = initialPage,
                    isAdmin = isAdmin,
                    onUpdateClick = onUpdateClick,
                    onEditClick = onEditClick,
                    onDeleteClick = onDeleteClick
                )
            }

            // Back/Close Button (Top Left of the blurred area)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .padding(top = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
