package com.phad.chatapp.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.phad.chatapp.models.Update

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReelFeed(
    updates: List<Update>,
    initialPage: Int = 0,
    isAdmin: Boolean = false,
    onUpdateClick: (Update) -> Unit,
    onEditClick: (Update) -> Unit = {},
    onDeleteClick: (Update) -> Unit = {},
    onDismiss: () -> Unit = {},
    isInFullView: Boolean = false // New param
) {
    if (updates.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No updates available", color = Color.White, fontSize = 18.sp)
        }
    } else {
        val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { updates.size })

        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            // Determine visibility for autoplay
            val isVisible = (pagerState.currentPage == page)
            
            ReelItem(
                update = updates[page],
                isVisible = isVisible,
                isAdmin = isAdmin,
                onUpdateClick = onUpdateClick,
                onEditClick = onEditClick,
                onDeleteClick = onDeleteClick,
                onDismiss = onDismiss,
                isInFullView = isInFullView
            )
        }
    }
}
