package com.phad.chatapp.activities

import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phad.chatapp.models.NotificationItem

class NotificationHistoryActivity : ComponentActivity() {

    private val viewModel: NotificationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val notifications by viewModel.notifications.collectAsState()
            val isLoading by viewModel.isLoading.collectAsState()

            MaterialTheme {
                NotificationHistoryScreen(
                    notifications = notifications,
                    isLoading = isLoading,
                    isAdmin = viewModel.isAdmin,
                    onBackClick = { finish() },
                    onNotificationClick = { viewModel.markAsRead(it.id) },
                    onDeleteClick = { viewModel.deleteNotification(it) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    notifications: List<NotificationItem>,
    isLoading: Boolean,
    isAdmin: Boolean,
    onBackClick: () -> Unit,
    onNotificationClick: (NotificationItem) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Notification") },
            text = { Text("Are you sure you want to delete this notification globally? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { 
                    onDeleteClick(showDeleteDialog!!)
                    showDeleteDialog = null
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xff0d0302),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFF5F5F5)
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (notifications.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = "Empty",
                        modifier = Modifier.size(64.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No new notifications",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notifications) { notification ->
                        NotificationCard(
                            notification = notification,
                            isAdmin = isAdmin,
                            onClick = onNotificationClick,
                            onDeleteClick = { showDeleteDialog = it }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCard(
    notification: NotificationItem,
    isAdmin: Boolean,
    onClick: (NotificationItem) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    val backgroundColor = if (notification.isRead) Color.White else Color(0xFFF0F8FF)
    
    val timestampMs = notification.timestamp?.toDate()?.time ?: System.currentTimeMillis()
    val timeAgo = DateUtils.getRelativeTimeSpanString(timestampMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

    Card(
        onClick = { onClick(notification) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Unread indicator dot
            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp, end = 8.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2196F3))
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notification.body,
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = timeAgo,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            
            if (isAdmin) {
                IconButton(onClick = { onDeleteClick(notification.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red)
                }
            }
        }
    }
}
