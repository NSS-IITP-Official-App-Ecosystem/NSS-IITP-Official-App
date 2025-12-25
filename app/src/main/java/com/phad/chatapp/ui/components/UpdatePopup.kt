package com.phad.chatapp.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phad.chatapp.utils.InAppUpdateManager

@Composable
fun UpdateOverlay(
    updateStatus: InAppUpdateManager.UpdateStatus,
    onDismissRequest: () -> Unit
) {
    if (updateStatus == InAppUpdateManager.UpdateStatus.NONE) return

    // Scrim to block interaction
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consume clicks */ },
        contentAlignment = Alignment.Center
    ) {
        UpdateDialogCard(updateStatus, onDismissRequest)
    }
}

@Composable
fun UpdateDialogCard(
    updateStatus: InAppUpdateManager.UpdateStatus,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val isMandatory = updateStatus == InAppUpdateManager.UpdateStatus.MANDATORY

    // Colors: Red for Mandatory, Yellow (Amber) for Optional
    val mainColor = if (isMandatory) Color(0xFFD32F2F) else Color(0xFFFFC107) 
    // Text on button: White on Red, Black on Yellow for readability
    val btnTextColor = if (isMandatory) Color.White else Color(0xFF202124)
    
    // Emoji & Text Logic
    val emoji = if (isMandatory) "🚧" else "✨"
    val title = if (isMandatory) "Not Me, But You..." else "Service before self,\nbut Update before Service"
    val body = if (isMandatory) "Really needs to update this app to continue using it." else "A newer version of the app is available."
    val btnText = if (isMandatory) "Update Now" else "Update App"

    Card(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .wrapContentHeight(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon Circle
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = mainColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(100)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 40.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF202124),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Body
            Text(
                text = body,
                fontSize = 16.sp,
                color = Color(0xFF5F6368),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Update Button
            Button(
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")))
                    } catch (e: Exception) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = mainColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = btnText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = btnTextColor)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary Action
            if (isMandatory) {
                OutlinedButton(
                    onClick = {
                        (context as? android.app.Activity)?.finishAffinity()
                        android.os.Process.killProcess(android.os.Process.myPid())
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "Exit App", color = Color(0xFF5F6368), fontWeight = FontWeight.SemiBold)
                }
            } else {
                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "Continue without update", color = Color(0xFF5F6368), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
