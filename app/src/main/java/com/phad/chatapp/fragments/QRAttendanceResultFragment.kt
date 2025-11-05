package com.phad.chatapp.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.phad.chatapp.R
import com.phad.chatapp.ui.components.DimmedHomeBackground

/**
 * Fragment to display QR attendance processing results (success or error)
 * Replaces the previous dialog-based approach with a dedicated screen
 */
class QRAttendanceResultFragment : Fragment() {
    
    private var resultMessage: String = ""
    private var resultType: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get arguments
        arguments?.let { bundle ->
            resultMessage = bundle.getString("result_message", "Unknown result")
            resultType = bundle.getString("result_type", "error")
        }
        
        // Handle back button press
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBack()
            }
        })
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                QRAttendanceResultScreenWithBackground(
                    message = resultMessage,
                    isSuccess = resultType == "success",
                    onDismiss = { navigateBack() }
                )
            }
        }
    }
    
    private fun navigateBack() {
        // Navigate back to the previous screen (usually the home screen)
        findNavController().popBackStack(R.id.nssHomeFragment, false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRAttendanceResultScreenWithBackground(
    message: String,
    isSuccess: Boolean,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Dimmed home background
        DimmedHomeBackground()

        // Result content overlay
        QRAttendanceResultContent(
            message = message,
            isSuccess = isSuccess,
            onDismiss = onDismiss
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRAttendanceResultContent(
    message: String,
    isSuccess: Boolean,
    onDismiss: () -> Unit
) {
    // Treat already-marked attendance as a neutral info state
    val isAlreadyMarked = message.contains("already", ignoreCase = true)

    val iconColor = when {
        isAlreadyMarked -> Color(0xFF2196F3)
        isSuccess -> Color(0xFF4CAF50)
        else -> Color(0xFFF44336)
    }
    val icon: ImageVector = when {
        isAlreadyMarked -> Icons.Default.Info
        isSuccess -> Icons.Default.CheckCircle
        else -> Icons.Default.Error
    }
    val title = when {
        isAlreadyMarked -> "Already Marked"
        isSuccess -> "Attendance Marked!"
        else -> "Error Occurred"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                Icon(
                    imageVector = icon,
                    contentDescription = if (isSuccess) "Success" else "Error",
                    tint = iconColor,
                    modifier = Modifier.size(64.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Title
                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Message
                Text(
                    text = message,
                    fontSize = 16.sp,
                    color = Color(0xFF424242),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Dismiss button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isAlreadyMarked -> Color(0xFF2196F3)
                            isSuccess -> Color(0xFF4CAF50)
                            else -> Color(0xFF2196F3)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "OK",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
                
                if (!isSuccess && !isAlreadyMarked) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Try Again button for errors
                    OutlinedButton(
                        onClick = {
                            // Navigate back to QR scan screen to try again
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Try Again",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2196F3)
                        )
                    }
                }
            }
        }
    }
}

// DimmedHomeBackground moved to shared component
