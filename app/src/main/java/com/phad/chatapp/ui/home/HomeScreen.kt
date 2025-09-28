package com.phad.chatapp.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phad.chatapp.R
import com.phad.chatapp.models.Update

data class HomeUiState(
    val greeting: String = "Good Morning",
    val userName: String = "User...",
    val updates: List<Update> = emptyList(),
    val isAdmin: Boolean = false,
    val isNssInterface: Boolean = false
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    state: HomeUiState,
    onChatbotClick: () -> Unit,
    onAddUpdateClick: () -> Unit,
    onUpdateClick: (Update) -> Unit
) {
    // Debug logging
    android.util.Log.d("HomeScreen", "HomeScreen - Received state: isAdmin=${state.isAdmin}, isNssInterface=${state.isNssInterface}, userName='${state.userName}'")
    
    val backgroundColor = Color(0xff0d0302)
    val surfaceColor = Color.White
    val onSurfaceColor = Color.Black
    val onSurfaceVariantColor = onSurfaceColor.copy(alpha = 0.6f)

    // Get screen height for responsive spacing
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp

    // Calculate responsive dimensions based on screen size
    val isSmallScreen = screenHeight < 700.dp
    val isMediumScreen = screenHeight < 800.dp

    // Responsive header bottom padding
    val headerBottomPadding = when {
        isSmallScreen -> 16.dp   // Small screens
        isMediumScreen -> 24.dp  // Medium screens
        else -> 32.dp            // Large screens
    }

    // Responsive content padding
    val contentPadding = when {
        isSmallScreen -> 16.dp
        isMediumScreen -> 20.dp
        else -> 24.dp
    }

    // Responsive font sizes
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

    val updatesFontSize = when {
        isSmallScreen -> 24.sp
        isMediumScreen -> 26.sp
        else -> 28.sp
    }

    // Responsive spacing
    val spacingBetweenSections = when {
        isSmallScreen -> 12.dp
        isMediumScreen -> 14.dp
        else -> 16.dp
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header - positioned just below status bar with minimal top padding
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(
                        start = contentPadding,
                        top = 4.dp, // Minimal top padding - system handles status bar
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
                    // Attendance icon removed from teaching wing interface
                    // if (!state.isNssInterface) {
                    //     IconButton(onClick = onTodoClick) {
                    //         Icon(
                    //             painterResource(id = R.drawable.mditickcircle),
                    //             contentDescription = "To-Do List",
                    //             tint = Color.White,
                    //             modifier = Modifier.size(if (isSmallScreen) 20.dp else 24.dp)
                    //         )
                    //     }
                    //     Spacer(Modifier.width(if (isSmallScreen) 4.dp else 8.dp))
                    // }
                    IconButton(onClick = onChatbotClick) {
                        Icon(
                            painterResource(id = R.drawable.ic_faq), // You'll need to add this icon
                            contentDescription = "FAQs",
                            tint = Color.White,
                            modifier = Modifier.size(if (isSmallScreen) 20.dp else 24.dp)
                        )
                    }
                }
            }

            // Body - with proper spacing from header
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(surfaceColor)
                    .verticalScroll(rememberScrollState())
            ) {
                // Greeting and Next Class
                Column(
                    modifier = Modifier.padding(all = contentPadding)
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                style = SpanStyle(
                                    fontSize = greetingFontSize,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("${state.greeting}\n")
                            }
                            withStyle(
                                style = SpanStyle(fontSize = userNameFontSize)
                            ) {
                                append(state.userName)
                            }
                        },
                        color = onSurfaceColor,
                        lineHeight = when {
                            isSmallScreen -> 30.sp
                            isMediumScreen -> 33.sp
                            else -> 36.sp
                        }
                    )
                    Spacer(Modifier.height(spacingBetweenSections))
                    // Next class info removed
                }

                // Updates Section
                Text(
                    text = "Updates",
                    color = onSurfaceColor.copy(alpha = 0.8f),
                    fontSize = updatesFontSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = contentPadding)
                )
                Spacer(Modifier.height(spacingBetweenSections))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = contentPadding),
                    horizontalArrangement = Arrangement.spacedBy(
                        if (isSmallScreen) 12.dp else 16.dp
                    )
                ) {
                    items(state.updates) { update ->
                        UpdateCard(
                            update = update,
                            onUpdateClick = onUpdateClick,
                            isSmallScreen = isSmallScreen
                        )
                    }
                }
                Spacer(
                    Modifier.height(
                        when {
                            isSmallScreen -> 60.dp
                            isMediumScreen -> 70.dp
                            else -> 80.dp
                        }
                    )
                ) // Spacer for content to clear FAB
            }
        }

        // Floating Action Button positioned absolutely
        if (state.isAdmin) {
            // Debug logging
            android.util.Log.d("HomeScreen", "HomeScreen - Showing FloatingActionButton because isAdmin=${state.isAdmin}")
            
            FloatingActionButton(
                onClick = onAddUpdateClick,
                containerColor = Color(0xffffcc00),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Update", tint = Color.Black)
            }
        } else {
            // Debug logging
            android.util.Log.d("HomeScreen", "HomeScreen - NOT showing FloatingActionButton because isAdmin=${state.isAdmin}")
        }
    }
}

@Composable
fun UpdateCard(
    update: Update,
    onUpdateClick: (Update) -> Unit,
    isSmallScreen: Boolean = false
) {
    val cardWidth = if (isSmallScreen) 260.dp else 280.dp
    val padding = if (isSmallScreen) 12.dp else 16.dp
    val spacing = if (isSmallScreen) 6.dp else 8.dp

    Card(
        modifier = Modifier
            .width(cardWidth)
            .clickable { onUpdateClick(update) },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(padding)) {
            // 1. Content - Add null safety check
            if (!update.content.isNullOrBlank()) {
                Text(
                    text = update.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    fontSize = if (isSmallScreen) 13.sp else 14.sp
                )
                Spacer(Modifier.height(spacing))
            } else {
                // Fallback for updates without content (should not happen with our filtering, but just in case)
                Text(
                    text = "No content available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 3,
                    fontSize = if (isSmallScreen) 13.sp else 14.sp
                )
                Spacer(Modifier.height(spacing))
            }

            // 2. Title - Add null safety check
            if (!update.title.isNullOrBlank()) {
                Text(
                    text = update.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    fontSize = if (isSmallScreen) 15.sp else 16.sp
                )
                Spacer(Modifier.height(spacing))
            } else {
                // Fallback for updates without titles (should not happen with our filtering, but just in case)
                Text(
                    text = "Untitled Update",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
                    fontSize = if (isSmallScreen) 15.sp else 16.sp
                )
                Spacer(Modifier.height(spacing))
            }

            // 3. Image - Add null safety check
            if (!update.imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = update.imageUrl,
                    contentDescription = update.title ?: "Update image", // Add null safety
                    placeholder = painterResource(id = R.drawable.rectangle9),
                    error = painterResource(id = R.drawable.rectangle9),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(spacing))
            }

            // 4. PDF - Add null safety check
            if (!update.documentName.isNullOrEmpty() && !update.documentUrl.isNullOrEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = "PDF",
                        modifier = Modifier.size(if (isSmallScreen) 18.dp else 20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(spacing))
                    Text(
                        text = update.documentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        fontSize = if (isSmallScreen) 11.sp else 12.sp
                    )
                }
                Spacer(Modifier.height(spacing))
            }

            // 5. External Link - Add null safety check
            if (!update.externalLink.isNullOrEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Link,
                        contentDescription = "Link",
                        modifier = Modifier.size(if (isSmallScreen) 18.dp else 20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(spacing))
                    Text(
                        text = update.externalLink,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        fontSize = if (isSmallScreen) 11.sp else 12.sp
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen(
        state = HomeUiState(
            greeting = "Good Morning",
            userName = "John Doe",
            updates = listOf(
                Update(
                    id = "1",
                    title = "Sample Update",
                    content = "This is a sample update content",
                    timestamp = System.currentTimeMillis()
                )
            ),
            isAdmin = true,
            isNssInterface = false
        ),
        onChatbotClick = {},
        onAddUpdateClick = {},
        onUpdateClick = {}
    )
}
