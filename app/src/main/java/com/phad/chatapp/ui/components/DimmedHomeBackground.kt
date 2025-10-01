package com.phad.chatapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.phad.chatapp.ui.home.HomeScreen
import com.phad.chatapp.ui.home.HomeUiState
import com.phad.chatapp.utils.SessionManager
import java.util.*

/**
 * Reusable component that displays the home screen with a dimmed overlay effect.
 * Used as background for QR attendance processing and result screens.
 * 
 * @param dimOpacity The opacity of the dark overlay (default: 0.65f for 65% dimming)
 */
@Composable
fun DimmedHomeBackground(
    dimOpacity: Float = 0.65f
) {
    // Get the session manager to fetch user data
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    
    // Create a simplified home state for background display
    val homeState = remember {
        HomeUiState(
            greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
                in 0..11 -> "Good Morning"
                in 12..16 -> "Good Afternoon"
                else -> "Good Evening"
            },
            userName = sessionManager.fetchUserName().ifEmpty { "User" },
            updates = emptyList(), // Empty for background display to improve performance
                            isAdmin = sessionManager.fetchUserType().equals("Admin", ignoreCase = true),
            isNssInterface = sessionManager.getLastInterfaceChoice() == "NSS"
        )
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Home screen background (disabled interactions)
        HomeScreen(
            state = homeState,
            onChatbotClick = { /* Disabled in background */ },
            onAddUpdateClick = { /* Disabled in background */ },
            onUpdateClick = { /* Disabled in background */ }
        )
        
        // Dark overlay for dimming effect
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dimOpacity))
        )
    }
}
