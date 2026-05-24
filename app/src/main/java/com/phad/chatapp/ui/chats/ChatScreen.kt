package com.phad.chatapp.ui.chats

import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phad.chatapp.R
import com.phad.chatapp.models.Group
import com.phad.chatapp.models.User

data class ChatUiState(
    val recentUsers: List<User> = emptyList(),
    val communities: List<Group> = emptyList(),
    val isLoading: Boolean = true,
    val isUserAdmin: Boolean = false,
    val unreadDirectChatIds: Set<String> = emptySet(),
    val unreadGroupChatIds: Set<String> = emptySet()
)

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    state: ChatUiState,
    onCommunityClick: (Group) -> Unit,
    onUserClick: (User) -> Unit,
    onSearchClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onCreateGroupClick: () -> Unit,
    onRemoveGroupClick: () -> Unit,
    onUnreadMessagesClick: () -> Unit,
    onSyncSubjectGroupsClick: () -> Unit,
    currentUserRollNumber: String
) {
    val backgroundColor = Color(0xff0d0302)
    val surfaceColor = Color.White
    val isAdmin = currentUserRollNumber == "2301MC51" || currentUserRollNumber == "2301CS16"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Messages",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = Color.White
                    )
                }
                SettingsMenu(
                    isUserAdmin = state.isUserAdmin,
                    onRefreshClick = onRefreshClick,
                    onCreateGroupClick = onCreateGroupClick,
                    onRemoveGroupClick = onRemoveGroupClick,
                    onUnreadMessagesClick = onUnreadMessagesClick,
                    onSyncSubjectGroupsClick = onSyncSubjectGroupsClick
                )
            }
        }

        // Body
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(surfaceColor)
        ) {
            // Recent Users
            if (state.recentUsers.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Chats",
                        color = Color.Black.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.recentUsers) { user ->
                            UserAvatar(
                                user = user,
                                hasUnread = state.unreadDirectChatIds.contains(user.id),
                                onClick = { onUserClick(user) }
                            )
                        }
                    }
                }
            }

            // Loading indicator or Communities list
            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                if (state.communities.isNotEmpty()) {
                    item {
                        Text(
                            text = "Communities",
                            color = Color.Black.copy(alpha = 0.6f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp)
                        )
                    }
                    items(state.communities) { community ->
                        CommunityRow(
                            community = community,
                            hasUnread = state.unreadGroupChatIds.contains(community.id),
                            onClick = { onCommunityClick(community) }
                        )
                    }
                } else if (!state.recentUsers.isNotEmpty()) { // Only show if both are empty
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No chats or communities found.",
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Button for admin subject sync
        if (isAdmin) {
            Box(modifier = Modifier.fillMaxSize()) {
                FloatingActionButton(
                    onClick = onSyncSubjectGroupsClick,
                    containerColor = Color(0xffffcc00),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Sync Subject Groups", tint = Color.Black)
                }
            }
        }
    }
}

@Composable
private fun SettingsMenu(
    isUserAdmin: Boolean,
    onRefreshClick: () -> Unit,
    onCreateGroupClick: () -> Unit,
    onRemoveGroupClick: () -> Unit,
    onUnreadMessagesClick: () -> Unit,
    onSyncSubjectGroupsClick: () -> Unit
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { isMenuExpanded = true }) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = Color.White
            )
        }
        DropdownMenu(
            expanded = isMenuExpanded,
            onDismissRequest = { isMenuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Refresh") },
                onClick = {
                    onRefreshClick()
                    isMenuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Unread Messages") },
                onClick = {
                    onUnreadMessagesClick()
                    isMenuExpanded = false
                }
            )
            if (isUserAdmin) {
                DropdownMenuItem(
                    text = { Text("Create Group") },
                    onClick = {
                        onCreateGroupClick()
                        isMenuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Remove Group") },
                    onClick = {
                        onRemoveGroupClick()
                        isMenuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Sync Subject Groups") },
                    onClick = {
                        onSyncSubjectGroupsClick()
                        isMenuExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun UserAvatar(user: User, hasUnread: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box {
            AsyncImage(
                model = user.profileImageUrl,
                contentDescription = user.name,
                placeholder = painterResource(id = R.drawable.default_profile_image),
                error = painterResource(id = R.drawable.default_profile_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(2.dp, Color(0xffffcc00)), CircleShape)
            )
            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                        .border(BorderStroke(2.dp, Color.White), CircleShape)
                        .align(Alignment.TopEnd)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = user.name ?: "User",
            color = Color.Black,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CommunityRow(community: Group, hasUnread: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Image(
                painter = painterResource(id = R.drawable.ic_group),
                contentDescription = community.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
            )
            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                        .border(BorderStroke(2.dp, Color.White), CircleShape)
                        .align(Alignment.TopEnd)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = community.name,
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = community.description,
                color = Color.Gray,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatScreenPreview() {
    ChatScreen(
        state = ChatUiState(
            recentUsers = listOf(
                User(name = "Aditya"),
                User(name = "Pranav"),
                User(name = "Aman")
            ),
            communities = listOf(
                Group(name = "Announcements"),
                Group(name = "TUT-12")
            ),
            isLoading = false,
            isUserAdmin = true
        ),
        onCommunityClick = {},
        onUserClick = {},
        onSearchClick = {},
        onRefreshClick = {},
        onCreateGroupClick = {},
        onRemoveGroupClick = {},
        onUnreadMessagesClick = {},
        onSyncSubjectGroupsClick = {},
        currentUserRollNumber = "2301MC51"
    )
}