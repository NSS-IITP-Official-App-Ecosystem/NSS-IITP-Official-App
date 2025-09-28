package com.phad.chatapp.features.home.faqs.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.phad.chatapp.features.home.faqs.data.*

@Composable
fun AdminFaqPencilIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Admin FAQ Management",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFaqRootSectionCard(
    section: FaqSection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap to manage this section",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AdminOperationDialog(
    node: FaqNode,
    onOperationSelected: (AdminOperation) -> Unit,
    onDismiss: () -> Unit
) {
    val operations = AdminOperations.getOperationsForNode(node.type)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Manage: ${node.title}",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            LazyColumn {
                items(operations) { operation ->
                    TextButton(
                        onClick = {
                            onOperationSelected(operation)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = AdminOperations.getOperationDisplayName(operation),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AdminFormDialog(
    dialogState: AdminDialogState,
    onFormDataChange: (AdminFormData) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!dialogState.isVisible) return
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = dialogState.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Scrollable form content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (dialogState.operation) {
                        AdminOperation.EDIT_SECTION_NAME -> {
                            SectionNameForm(
                                formData = dialogState.formData,
                                onFormDataChange = onFormDataChange
                            )
                        }
                        AdminOperation.ADD_SUBSECTION -> {
                            SubSectionForm(
                                formData = dialogState.formData,
                                onFormDataChange = onFormDataChange
                            )
                        }
                        AdminOperation.ADD_QUESTION, AdminOperation.EDIT_QUESTION -> {
                            QuestionForm(
                                formData = dialogState.formData,
                                onFormDataChange = onFormDataChange
                            )
                        }
                        else -> {
                            Text("Unsupported operation")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onConfirm) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun SectionNameForm(
    formData: AdminFormData,
    onFormDataChange: (AdminFormData) -> Unit
) {
    OutlinedTextField(
        value = formData.title,
        onValueChange = { onFormDataChange(formData.copy(title = it)) },
        label = { Text("Section Name") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
fun SubSectionForm(
    formData: AdminFormData,
    onFormDataChange: (AdminFormData) -> Unit
) {
    Column {
        OutlinedTextField(
            value = formData.title,
            onValueChange = { onFormDataChange(formData.copy(title = it)) },
            label = { Text("Sub-section Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Description field removed per requirements
    }
}

@Composable
fun QuestionForm(
    formData: AdminFormData,
    onFormDataChange: (AdminFormData) -> Unit
) {
    Column {
        OutlinedTextField(
            value = formData.question,
            onValueChange = { onFormDataChange(formData.copy(question = it)) },
            label = { Text("Question") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Answer Type",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(AnswerType.TEXT, AnswerType.BULLET_POINTS).forEach { answerType ->
                FilterChip(
                    selected = formData.answerType == answerType,
                    onClick = { onFormDataChange(formData.copy(answerType = answerType)) },
                    label = { Text(text = if (answerType == AnswerType.TEXT) "Text" else "Bullet Points") }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        when (formData.answerType) {
            AnswerType.TEXT -> {
                OutlinedTextField(
                    value = formData.textAnswer,
                    onValueChange = { onFormDataChange(formData.copy(textAnswer = it)) },
                    label = { Text("Answer") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
                
                // Link syntax helper text
                Text(
                    text = "💡 Tip: Use \\link(url) for clickable links (e.g., \\link(https://example.com))",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            AnswerType.BULLET_POINTS -> {
                BulletPointsForm(
                    bulletPoints = formData.bulletPoints,
                    onBulletPointsChange = { 
                        onFormDataChange(formData.copy(bulletPoints = it)) 
                    }
                )
            }
        }
    }
}

@Composable
fun BulletPointsForm(
    bulletPoints: List<String>,
    onBulletPointsChange: (List<String>) -> Unit
) {
    var newPoint by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var bulletPointToDelete by remember { mutableStateOf(-1) }
    
    Column {
        Text(
            text = "Bullet Points",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Link syntax helper text for bullet points
        Text(
            text = "💡 Tip: Use \\link(url) for clickable links in bullet points",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        bulletPoints.forEachIndexed { index, point ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top  // Align to top since text fields can have different heights
            ) {
                OutlinedTextField(
                    value = point,
                    onValueChange = { newValue ->
                        val updatedPoints = bulletPoints.toMutableList()
                        updatedPoints[index] = newValue
                        onBulletPointsChange(updatedPoints)
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = false,  // Allow multiple lines
                    maxLines = 5,  // Limit to 5 lines to prevent excessive height
                    placeholder = { Text("Bullet point ${index + 1}") }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(
                    onClick = {
                        bulletPointToDelete = index
                        showDeleteDialog = true
                    }
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove bullet point",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top  // Align to top for consistency
        ) {
            OutlinedTextField(
                value = newPoint,
                onValueChange = { newPoint = it },
                modifier = Modifier.weight(1f),
                singleLine = false,  // Allow multiple lines
                maxLines = 3,  // Limit to 3 lines for new point input
                placeholder = { Text("Add new bullet point") }
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Button(
                onClick = {
                    if (newPoint.isNotBlank()) {
                        onBulletPointsChange(bulletPoints + newPoint)
                        newPoint = ""
                    }
                },
                enabled = newPoint.isNotBlank()
            ) {
                Text("Add")
            }
        }
    }
    
    // Confirmation dialog for deleting bullet points
    if (showDeleteDialog && bulletPointToDelete >= 0) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteDialog = false
                bulletPointToDelete = -1
            },
            title = { 
                Text(
                    text = "Delete Bullet Point",
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Text("Are you sure you want to delete this bullet point? This action cannot be undone.") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updatedPoints = bulletPoints.toMutableList()
                        updatedPoints.removeAt(bulletPointToDelete)
                        onBulletPointsChange(updatedPoints)
                        showDeleteDialog = false
                        bulletPointToDelete = -1
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showDeleteDialog = false
                        bulletPointToDelete = -1
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AdminSectionContentView(
    sectionContent: SectionContent,
    currentNode: FaqNode?,
    onSubSectionClick: (FaqSubSection) -> Unit,
    onQuestionClick: (FaqQuestion) -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBackClick,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text("← Back to Sections")
            }
        }

        // Title (section or subsection)
        if (currentNode?.type == FaqNodeType.SUBSECTION) {
            Text(
                text = currentNode.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else {
            Text(
                text = sectionContent.section.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Subsections (only at section level)
            if (currentNode?.type != FaqNodeType.SUBSECTION && sectionContent.subSections.isNotEmpty()) {
                item {
                    Text(
                        text = "Sub-sections",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(sectionContent.subSections) { subSection ->
                    AdminContentCard(
                        title = subSection.title,
                        subtitle = subSection.description ?: "Sub-section",
                        onClick = { onSubSectionClick(subSection) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Questions
            if (sectionContent.questions.isNotEmpty()) {
                item {
                    Text(
                        text = "Questions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                val filtered = if (currentNode?.type == FaqNodeType.SUBSECTION) {
                    sectionContent.questions.filter { it.subSectionId == currentNode.subSectionId }
                } else {
                    sectionContent.questions.filter { it.subSectionId == null }
                }

                items(filtered) { question ->
                    AdminContentCard(
                        title = question.question,
                        subtitle = "Question",
                        onClick = { onQuestionClick(question) }
                    )
                }
            }

            // Empty state
            if (sectionContent.subSections.isEmpty() && sectionContent.questions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "This section is empty. Use the admin tools to add content.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminContentCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    node: FaqNode,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete ${node.type.name.lowercase().replace('_', ' ')}?",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete \"${node.title}\"? This action cannot be undone." +
                        if (node.type != FaqNodeType.QUESTION) " All sub-items will also be deleted." else "",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
