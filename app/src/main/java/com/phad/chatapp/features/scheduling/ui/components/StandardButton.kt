package com.phad.chatapp.features.scheduling.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.phad.chatapp.features.scheduling.ui.theme.NeutralGray
import com.phad.chatapp.features.scheduling.ui.theme.YellowAccent

// Standardized Button Design System
object StandardButtonDefaults {
    val Height = 44.dp
    val CornerRadius = 10.dp
    val Elevation = 3.dp
    val HorizontalPadding = 20.dp
    val VerticalPadding = 10.dp
    val MinimumWidth = 80.dp

    @Composable
    fun buttonColors() = ButtonDefaults.buttonColors(
        containerColor = YellowAccent,
        contentColor = Color.Black,
        disabledContainerColor = NeutralGray.copy(alpha = 0.5f),
        disabledContentColor = Color.Black.copy(alpha = 0.5f)
    )

    @Composable
    fun buttonElevation() = ButtonDefaults.buttonElevation(
        defaultElevation = Elevation,
        pressedElevation = (Elevation.value - 1).dp,
        disabledElevation = 0.dp
    )

    val buttonShape = RoundedCornerShape(CornerRadius)

    val contentPadding = PaddingValues(
        horizontal = HorizontalPadding,
        vertical = VerticalPadding
    )
}

@Composable
fun StandardButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = StandardButtonDefaults.Height)
            .widthIn(min = StandardButtonDefaults.MinimumWidth),
        enabled = enabled,
        colors = StandardButtonDefaults.buttonColors(),
        elevation = StandardButtonDefaults.buttonElevation(),
        shape = StandardButtonDefaults.buttonShape,
        contentPadding = StandardButtonDefaults.contentPadding,
        content = content
    )
}
