package com.phad.chatapp.features.scheduling.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phad.chatapp.features.scheduling.ui.theme.DarkBackground
import com.phad.chatapp.features.scheduling.ui.theme.YellowAccent

@Composable
fun SubjectCountRow(
    subjectCode: String,
    subjectName: String,
    count: Int,
    onCountChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Subject Name
            Text(
                text = subjectName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            
            // Counter controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Decrease button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .shadow(4.dp, CircleShape)
                        .background(
                            if (count > 0) YellowAccent else Color.Gray,
                            CircleShape
                        )
                        .clickable(enabled = count > 0) {
                            if (count > 0) {
                                onCountChange(count - 1)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "-",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                }
                
                // Count display
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(24.dp)
                )
                
                // Increase button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .shadow(4.dp, CircleShape)
                        .background(YellowAccent, CircleShape)
                        .clickable {
                            onCountChange(count + 1)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                }
            }
        }
    }
} 