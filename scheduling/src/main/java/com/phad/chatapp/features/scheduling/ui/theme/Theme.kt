package com.phad.chatapp.features.scheduling.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Use the color constants from Color.kt file
private val TTWColorScheme = darkColorScheme(
    primary = YellowAccent,
    onPrimary = DarkPrimary,
    secondary = YellowAccent,
    onSecondary = Color.Black,
    tertiary = TealAccent,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = SurfaceElevated,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = NeutralGray,
    error = ErrorRed,
    onError = Color.White
)

// Keeping these for compatibility but not using them anymore
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun TTWAppTheme(
    darkTheme: Boolean = true, // Always use dark theme based on TTW App screenshots
    content: @Composable () -> Unit
) {
    val colorScheme = TTWColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Used by the scheduling feature
@Composable
fun SchedulingTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    TTWAppTheme(darkTheme = darkTheme, content = content)
}

// Keep AppTheme for backward compatibility, but redirect to TTWAppTheme
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Ignored parameter
    content: @Composable () -> Unit
) {
    TTWAppTheme(darkTheme = darkTheme, content = content)
} 