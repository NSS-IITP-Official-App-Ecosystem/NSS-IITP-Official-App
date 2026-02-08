package com.phad.chatapp.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phad.chatapp.R
import com.phad.chatapp.models.Update

data class HomeUiState(
    val greeting: String = "Good Morning",
    val userName: String = "User...",
    val updates: List<Update> = emptyList(),
    val isAdmin: Boolean = false,
    val isNssInterface: Boolean = false,
    val isRefreshing: Boolean = false
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    state: HomeUiState,
    onChatbotClick: () -> Unit,
    onAddUpdateClick: () -> Unit,
    onUpdateClick: (Update) -> Unit,
    onEditPost: (Update) -> Unit = {},
    onDeletePost: (Update) -> Unit = {},
    onBatchDeleteClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onBottomNavVisibilityChanged: (Boolean) -> Unit = {}
) {
    val backgroundColor = Color(0xff0d0302)
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    val isSmallScreen = screenHeight < 700.dp
    val isMediumScreen = screenHeight < 800.dp
    
    val headerBottomPadding = when {
        isSmallScreen -> 16.dp
        isMediumScreen -> 24.dp
        else -> 32.dp
    }
    
    val contentPadding = when {
        isSmallScreen -> 16.dp
        isMediumScreen -> 20.dp
        else -> 24.dp
    }
    
    val greetingFontSize = when {
        isSmallScreen -> 26.sp
        isMediumScreen -> 29.sp
        else -> 32.sp
    }
    
    val userNameFontSize = when {
        isSmallScreen -> 20.sp
        isMediumScreen -> 22.sp
        else -> 24.sp
    }

    var selectedTab by remember { mutableStateOf(0) }
    var showDetailScreen by remember { mutableStateOf<Update?>(null) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    // Sync pager state with selected tab
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            selectedTab = page
        }
    }
    
    // Hide/show bottom navigation based on detail screen visibility
    LaunchedEffect(showDetailScreen) {
        onBottomNavVisibilityChanged(showDetailScreen == null)
    }

    // Handle back button when detail screen is shown
    BackHandler(enabled = showDetailScreen != null) {
        showDetailScreen = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (showDetailScreen != null) {
            // Show detail screen
            UpdateDetailScreen(
                update = showDetailScreen!!,
                onBackClick = { showDetailScreen = null }
            )
        } else {
            // Show main feed
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .padding(
                            start = contentPadding,
                            top = 4.dp,
                            end = contentPadding,
                            bottom = headerBottomPadding
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(
                            id = if (state.isNssInterface) R.drawable.nss_logo_main else R.drawable.app_logo_top_left
                        ),
                        contentDescription = if (state.isNssInterface) "NSS Logo" else "Teaching Wing Logo",
                        modifier = Modifier.size(60.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.isAdmin) {
                            // Admin: Plus button with menu
                            Box {
                                var showMenu by remember { mutableStateOf(false) }
                                
                                IconButton(
                                    onClick = { showMenu = !showMenu },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
//                                        .background(Color(0xFFFFC107))
                                ) {
                                    Icon(
                                        if (showMenu) Icons.Filled.Close else Icons.Filled.Add,
                                        contentDescription = "Menu",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                
                                MaterialTheme(
                                    shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
                                ) {
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false },
                                        modifier = Modifier
                                            .width(200.dp), // Check width
                                        offset = androidx.compose.ui.unit.DpOffset(x = (-16).dp, y = 8.dp)
                                    ) {
                                        DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    "Create Update",
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.Black
                                                ) 
                                            },
                                        onClick = {
                                            showMenu = false
                                            onAddUpdateClick()
                                        },
                                        leadingIcon = { 
                                            Icon(
                                                Icons.Filled.Add, 
                                                contentDescription = null,
                                                tint = Color(0xFF2196F3) // Blue tint
                                            ) 
                                        },
                                        colors = MenuDefaults.itemColors(
                                            textColor = Color.Black,
                                            leadingIconColor = Color(0xFF2196F3)
                                        )
                                    )
                                    
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = Color.LightGray.copy(alpha = 0.5f))

                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                "Batch Delete",
                                                fontWeight = FontWeight.Medium,
                                                color = Color.Black
                                            ) 
                                        },
                                        onClick = {
                                            showMenu = false
                                            onBatchDeleteClick()
                                        },
                                        leadingIcon = { 
                                            Icon(
                                                Icons.Filled.Delete, 
                                                contentDescription = null,
                                                tint = Color(0xFFF44336) // Red tint
                                            ) 
                                        },
                                        colors = MenuDefaults.itemColors(
                                            textColor = Color.Black,
                                            leadingIconColor = Color(0xFFF44336)
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                            // Non-admin: FAQ button
                            IconButton(onClick = onChatbotClick) {
                                Icon(
                                    painterResource(id = R.drawable.ic_faq),
                                    contentDescription = "FAQs",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isSmallScreen) 20.dp else 24.dp)
                                )
                            }
                        }
                    }
                }

                // Body
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                        .background(Color.White)
                ) {
                    // Greeting with Toggle Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = contentPadding, vertical = 16.dp)
                            .background(Color.White),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    style = SpanStyle(
                                        fontSize = greetingFontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                ) {
                                    append("${state.greeting}\n")
                                }
                                withStyle(
                                    style = SpanStyle(
                                        fontSize = userNameFontSize,
                                        color = Color.Black.copy(alpha = 0.7f)
                                    )
                                ) {
                                    append(state.userName)
                                }
                            },
                            lineHeight = when {
                                isSmallScreen -> 30.sp
                                isMediumScreen -> 33.sp
                                else -> 36.sp
                            },
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Compact Toggle Switch with Icons
                        val animatedOffset by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (selectedTab == 0) 0f else 1f,
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 300,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                            ),
                            label = "toggle_indicator"
                        )
                        
                        Box(
                            modifier = Modifier
                                .width(90.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFFFFF8E1))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    // Toggle between tabs
                                    selectedTab = if (selectedTab == 0) 1 else 0
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(selectedTab)
                                    }
                                }
                        ) {
                            // Animated indicator
                            Box(
                                modifier = Modifier
                                    .offset(x = 45.dp * animatedOffset)
                                    .width(45.dp)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xFFFFC107))
                            )
                            
                            // Toggle buttons with icons
                            Row(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Updates icon
                                Box(
                                    modifier = Modifier
                                        .width(45.dp)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_article),
                                        contentDescription = "Updates",
                                        tint = if (selectedTab == 0) Color.Black else Color(0xFF424242),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                
                                // Reels icon
                                Box(
                                    modifier = Modifier
                                        .width(45.dp)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_play_circle),
                                        contentDescription = "Reels",
                                        tint = if (selectedTab == 1) Color.Black else Color(0xFF424242),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Content with HorizontalPager for swipe navigation
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF5F5F5))
                    ) { page ->
                        when (page) {
                            0 -> UpdatesTab(
                                updates = state.updates.filter { it.postType == "text" },
                                isAdmin = state.isAdmin,
                                onEditClick = onEditPost,
                                onDeleteClick = onDeletePost,
                                onReadMoreClick = { showDetailScreen = it },
                                isRefreshing = state.isRefreshing,
                                onRefresh = onRefresh
                            )
                            1 -> ReelsTab(
                                updates = state.updates.filter { it.postType == "reel" },
                                isAdmin = state.isAdmin,
                                onEditClick = onEditPost,
                                onDeleteClick = onDeletePost,
                                isRefreshing = state.isRefreshing,
                                onRefresh = onRefresh
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdatesTab(
    updates: List<Update>,
    isAdmin: Boolean,
    onEditClick: (Update) -> Unit,
    onDeleteClick: (Update) -> Unit,
    onReadMoreClick: (Update) -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    if (updates.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No updates available", color = Color.Gray)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
        ) {
            items(updates) { update ->
                UpdateCard(
                    update = update,
                    isAdmin = isAdmin,
                    onEditClick = onEditClick,
                    onDeleteClick = onDeleteClick,
                    onReadMoreClick = onReadMoreClick
                )
            }
        }
    }
}

@Composable
fun ReelsTab(
    updates: List<Update>,
    isAdmin: Boolean,
    onEditClick: (Update) -> Unit,
    onDeleteClick: (Update) -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    if (updates.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No reels available", color = Color.Gray)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
        ) {
            items(updates) { update ->
                ReelCard(
                    update = update,
                    isAdmin = isAdmin,
                    onEditClick = onEditClick,
                    onDeleteClick = onDeleteClick
                )
            }
        }
    }
}
