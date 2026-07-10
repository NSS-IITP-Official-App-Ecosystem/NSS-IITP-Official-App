package com.phad.chatapp.ui.help

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phad.chatapp.R
import com.phad.chatapp.models.Issue
import com.phad.chatapp.utils.HelpConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeIssueDialog(
    onDismiss: () -> Unit,
    onSubmit: (Issue, Uri?) -> Unit,
    userName: String,
    userRollNumber: String,
    userWings: List<String>,
    isSubmitting: Boolean
) {
    val context = LocalContext.current
    
    // Theme colors
    val backgroundColor = colorResource(id = R.color.ui_white)
    val surfaceColor = colorResource(id = R.color.ui_gray_light)
    val primaryColor = colorResource(id = R.color.ui_blue)
    val onSurfaceColor = colorResource(id = R.color.ui_dark)
    
    // State
    var category by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    
    var addressedTo by remember { mutableStateOf("") }
    var addressedToExpanded by remember { mutableStateOf(false) }
    
    val addressedToOptions = remember(userWings) { HelpConstants.getAddressedToOptions(userWings) }

    var subject by remember { mutableStateOf("") }
    var subjectFocused by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    val scrollState = rememberScrollState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // Check file size
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                val sizeIndex = cursor?.getColumnIndex(OpenableColumns.SIZE)
                cursor?.moveToFirst()
                val size = sizeIndex?.let { cursor.getLong(it) } ?: 0L
                cursor?.close()
                
                if (size > HelpConstants.MAX_ATTACHMENT_SIZE_BYTES) {
                    Toast.makeText(context, "Image must be less than 100KB", Toast.LENGTH_SHORT).show()
                } else {
                    selectedImageUri = uri
                }
            } catch (e: Exception) {
                // Fallback length check
                val length = context.contentResolver.openAssetFileDescriptor(uri, "r")?.length ?: 0L
                if (length > HelpConstants.MAX_ATTACHMENT_SIZE_BYTES) {
                    Toast.makeText(context, "Image must be less than 100KB", Toast.LENGTH_SHORT).show()
                } else {
                    selectedImageUri = uri
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = backgroundColor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "Compose Issue",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = onSurfaceColor,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Name (Read-only)
            OutlinedTextField(
                value = userName,
                onValueChange = {},
                enabled = false,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = onSurfaceColor,
                    disabledBorderColor = surfaceColor,
                    disabledLabelColor = colorResource(id = R.color.ui_gray_dark)
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Roll Number (Read-only)
            OutlinedTextField(
                value = userRollNumber,
                onValueChange = {},
                enabled = false,
                label = { Text("Roll Number") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = onSurfaceColor,
                    disabledBorderColor = surfaceColor,
                    disabledLabelColor = colorResource(id = R.color.ui_gray_dark)
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Category Dropdown
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    HelpConstants.ISSUE_CATEGORIES.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                category = option
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Addressed To Dropdown
            ExposedDropdownMenuBox(
                expanded = addressedToExpanded,
                onExpandedChange = { addressedToExpanded = !addressedToExpanded }
            ) {
                OutlinedTextField(
                    value = addressedTo,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Addressed To") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = addressedToExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors()
                )
                ExposedDropdownMenu(
                    expanded = addressedToExpanded,
                    onDismissRequest = { addressedToExpanded = false }
                ) {
                    addressedToOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                addressedTo = option
                                addressedToExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Subject
            OutlinedTextField(
                value = subject,
                onValueChange = { if (it.length <= 50) subject = it },
                label = { Text("Subject") },
                supportingText = { if (subjectFocused) Text("${subject.length}/50") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { subjectFocused = it.isFocused }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Attachment Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = surfaceColor, contentColor = primaryColor)
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Attach Photo (<100KB)")
                }
            }

            if (selectedImageUri != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.size(100.dp)) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Selected image",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                    IconButton(
                        onClick = { selectedImageUri = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = {
                        category = ""
                        addressedTo = ""
                        subject = ""
                        description = ""
                        selectedImageUri = null
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {
                        if (category.isEmpty() || addressedTo.isEmpty() || subject.isEmpty() || description.isEmpty()) {
                            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val issue = Issue(
                            name = userName,
                            rollNumber = userRollNumber,
                            category = category,
                            addressedTo = addressedTo,
                            subject = subject,
                            description = description
                        )
                        onSubmit(issue, selectedImageUri)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("Submit")
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
