package com.phad.chatapp.ui.help

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.InsertDriveFile
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

private val PaddingScreenHorizontal = 24.dp
private val PaddingScreenVertical = 8.dp
private val SpacingBtwFields = 12.dp
private val SpacingBtwGroups = 16.dp
private val SpacingBeforeButtons = 24.dp
private val SpacingBottomScreen = 32.dp
private val HeightDescriptionField = 120.dp
private val SizeImagePreview = 100.dp
private val SizeRemoveImageBtn = 24.dp
private val SizeRemoveImageIcon = 16.dp
private val SizeLoadingSpinner = 24.dp
private val CornerRadiusImage = 8.dp
private val CornerRadiusRemoveBtn = 12.dp
private val SpacingBtwIconAndText = 8.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeIssueDialog(
    onDismiss: () -> Unit,
    onSubmit: (Issue, Uri?, String?) -> Unit,
    userName: String,
    userRollNumber: String,
    userWings: List<String>,
    addressedToOptions: List<String>,
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

    var subject by remember { mutableStateOf("") }
    var subjectFocused by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    val scrollState = rememberScrollState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
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
                    Toast.makeText(context, "File must be less than 1MB", Toast.LENGTH_SHORT).show()
                } else {
                    selectedImageUri = uri
                }
            } catch (e: Exception) {
                // Fallback length check
                val length = context.contentResolver.openAssetFileDescriptor(uri, "r")?.length ?: 0L
                if (length > HelpConstants.MAX_ATTACHMENT_SIZE_BYTES) {
                    Toast.makeText(context, "File must be less than 1MB", Toast.LENGTH_SHORT).show()
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
                .padding(horizontal = PaddingScreenHorizontal, vertical = PaddingScreenVertical)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "Compose Issue",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = onSurfaceColor,
                modifier = Modifier.padding(bottom = SpacingBtwGroups)
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
            Spacer(modifier = Modifier.height(SpacingBtwFields))

            // Category Dropdown
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isSubmitting,
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
            Spacer(modifier = Modifier.height(SpacingBtwFields))

            // Addressed To Dropdown
            ExposedDropdownMenuBox(
                expanded = addressedToExpanded,
                onExpandedChange = { addressedToExpanded = !addressedToExpanded }
            ) {
                OutlinedTextField(
                    value = addressedTo,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isSubmitting,
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
            Spacer(modifier = Modifier.height(SpacingBtwFields))

            // Subject
            OutlinedTextField(
                value = subject,
                onValueChange = { if (it.length <= 50) subject = it },
                label = { Text("Subject") },
                enabled = !isSubmitting,
                supportingText = { if (subjectFocused) Text("${subject.length}/50") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { subjectFocused = it.isFocused }
            )
            Spacer(modifier = Modifier.height(SpacingBtwFields))

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HeightDescriptionField),
                maxLines = 5
            )
            Spacer(modifier = Modifier.height(SpacingBtwGroups))

            // Attachment Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        photoPickerLauncher.launch("*/*")
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = surfaceColor, contentColor = primaryColor)
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(SpacingBtwIconAndText))
                    Text("Attach File (Image/PDF)")
                }
            }

            if (selectedImageUri != null) {
                Spacer(modifier = Modifier.height(SpacingBtwIconAndText))
                val mimeType = context.contentResolver.getType(selectedImageUri!!) ?: ""
                
                Box(modifier = Modifier.wrapContentSize()) {
                    if (mimeType.startsWith("image/")) {
                        Box(modifier = Modifier.size(SizeImagePreview)) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Selected image",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(CornerRadiusImage))
                            )
                        }
                    } else {
                        // PDF or other document
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(surfaceColor, RoundedCornerShape(CornerRadiusImage))
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = if (mimeType.contains("pdf")) Icons.Default.PictureAsPdf else Icons.Default.InsertDriveFile,
                                contentDescription = "Document",
                                tint = primaryColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Document Attached", color = onSurfaceColor, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.width(24.dp)) // space for close button
                        }
                    }
                    
                    IconButton(
                        onClick = { selectedImageUri = null },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-8).dp)
                            .size(SizeRemoveImageBtn)
                            .background(colorResource(id = R.color.ui_dark).copy(alpha = 0.6f), RoundedCornerShape(CornerRadiusRemoveBtn))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = colorResource(id = R.color.ui_white), modifier = Modifier.size(SizeRemoveImageIcon))
                    }
                }
            }

            Spacer(modifier = Modifier.height(SpacingBeforeButtons))

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
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear")
                }
                Spacer(modifier = Modifier.width(SpacingBtwGroups))
                Button(
                    onClick = {
                        if (category.isEmpty() || addressedTo.isEmpty() || subject.isEmpty() || description.isEmpty()) {
                            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        var finalAddressedTo = addressedTo
                        if (addressedTo.contains("Wing SubCoord", ignoreCase = true) || addressedTo.contains("Wing Secretary", ignoreCase = true)) {
                            val userWing = userWings.firstOrNull()
                            if (userWing != null) {
                                val normalizedWing = if (userWing.endsWith("Wing")) userWing else "$userWing Wing"
                                val baseRole = if (addressedTo.contains("Secretary", ignoreCase = true)) "Wing Secretary" else "Wing SubCoord"
                                finalAddressedTo = "$baseRole - $normalizedWing"
                            } else {
                                Toast.makeText(context, "You must be in a Wing to address this role", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                        }

                        val issue = Issue(
                            name = userName,
                            rollNumber = userRollNumber,
                            category = category,
                            addressedTo = finalAddressedTo,
                            subject = subject,
                            description = description
                        )
                        val finalMimeType = selectedImageUri?.let { context.contentResolver.getType(it) }
                        onSubmit(issue, selectedImageUri, finalMimeType)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(SizeLoadingSpinner), color = colorResource(id = R.color.ui_white))
                    } else {
                        Text("Submit")
                    }
                }
            }
            Spacer(modifier = Modifier.height(SpacingBottomScreen))
        }
    }
}
