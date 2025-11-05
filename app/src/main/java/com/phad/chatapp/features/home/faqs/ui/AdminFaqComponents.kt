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
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
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
            IconButton(
                onClick = onEditClick,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit section name",
                    tint = MaterialTheme.colorScheme.primary
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
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    if (!dialogState.isVisible) return
    
    val isSimpleDialog = dialogState.operation == AdminOperation.EDIT_SECTION_NAME || 
                        dialogState.operation == AdminOperation.ADD_SUBSECTION
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isSimpleDialog) 0.85f else 0.98f)
                .fillMaxHeight(if (isSimpleDialog) 0.4f else 0.9f)
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header with title and delete button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dialogState.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Delete button (only for edit question)
                    if (dialogState.operation == AdminOperation.EDIT_QUESTION && onDelete != null) {
                        IconButton(
                            onClick = onDelete
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete question",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Form content
                if (isSimpleDialog) {
                    // Simple form without scrolling
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
                        else -> {
                            Text("Unsupported operation")
                        }
                    }
                } else {
                    // Complex form with scrolling
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (dialogState.operation) {
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
        
        // Helper text for links and tabs - moved above answer field
        Text(
            text = "💡 Tips: Use \\link(url) for links.\nUse \\\\tN to insert indentation for bullet points.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = formData.textAnswer,
            onValueChange = { onFormDataChange(formData.copy(textAnswer = it)) },
            label = { Text("Answer") },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            maxLines = 15
        )
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
    currentTab: String,
    onSubSectionClick: (FaqSubSection) -> Unit,
    onQuestionClick: (FaqQuestion) -> Unit,
    onTabSwitch: (String) -> Unit,
    onEditSectionName: (FaqNode) -> Unit,
    onDeleteSection: (FaqNode) -> Unit,
    onAddSubSection: (FaqNode) -> Unit,
    onAddQuestion: (FaqNode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Title with edit and delete buttons
        val displayTitle = if (currentNode?.type == FaqNodeType.SUBSECTION) {
            currentNode.title
        } else {
            sectionContent.section.title
        }
        
        val currentNodeForActions = currentNode ?: FaqNode(
            id = sectionContent.section.id,
            title = sectionContent.section.title,
            type = FaqNodeType.ROOT_SECTION,
            section = sectionContent.section
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            
            // Edit button
            IconButton(
                onClick = { onEditSectionName(currentNodeForActions) }
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit section name",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // Delete button (only for subsections, not root sections)
            if (currentNode?.type == FaqNodeType.SUBSECTION) {
                IconButton(
                    onClick = { onDeleteSection(currentNodeForActions) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete section",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Scrollable content area
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Content based on current tab
            when (currentTab) {
                "subsections" -> {
                    if (currentNode?.type != FaqNodeType.SUBSECTION) {
                        // Show subsections (only top-level ones)
                        val topLevelSubSections = sectionContent.subSections.filter { 
                            it.parentSubSectionId == null 
                        }
                        
                        items(topLevelSubSections) { subSection ->
                            AdminContentCardWithActions(
                                title = subSection.title,
                                subtitle = subSection.description ?: "Sub-section",
                                onClick = { onSubSectionClick(subSection) },
                                onEdit = { 
                                    val node = FaqNode(
                                        id = subSection.id,
                                        title = subSection.title,
                                        type = FaqNodeType.SUBSECTION,
                                        sectionId = subSection.sectionId,
                                        subSectionId = subSection.id,
                                        subSection = subSection
                                    )
                                    onEditSectionName(node)
                                },
                                onDelete = {
                                    val node = FaqNode(
                                        id = subSection.id,
                                        title = subSection.title,
                                        type = FaqNodeType.SUBSECTION,
                                        sectionId = subSection.sectionId,
                                        subSectionId = subSection.id,
                                        subSection = subSection
                                    )
                                    onDeleteSection(node)
                                }
                            )
                        }
                        
                        // Empty state if no subsections
                        if (topLevelSubSections.isEmpty()) {
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
                                            text = "No sub-sections yet. Click the button below to add one.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Subsection view - show nested subsections
                        val nestedSubSections = sectionContent.subSections.filter { 
                            it.parentSubSectionId == currentNode.id 
                        }
                        
                        items(nestedSubSections) { subSection ->
                            AdminContentCardWithActions(
                                title = subSection.title,
                                subtitle = subSection.description ?: "Sub-section",
                                onClick = { onSubSectionClick(subSection) },
                                onEdit = { 
                                    val node = FaqNode(
                                        id = subSection.id,
                                        title = subSection.title,
                                        type = FaqNodeType.SUBSECTION,
                                        sectionId = subSection.sectionId,
                                        subSectionId = subSection.id,
                                        subSection = subSection
                                    )
                                    onEditSectionName(node)
                                },
                                onDelete = {
                                    val node = FaqNode(
                                        id = subSection.id,
                                        title = subSection.title,
                                        type = FaqNodeType.SUBSECTION,
                                        sectionId = subSection.sectionId,
                                        subSectionId = subSection.id,
                                        subSection = subSection
                                    )
                                    onDeleteSection(node)
                                }
                            )
                        }
                        
                        // Empty state if no nested subsections
                        if (nestedSubSections.isEmpty()) {
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
                                            text = "No sub-sections yet. Click the button below to add one.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                "questions" -> {
                    // Show questions
                    val filteredQuestions = if (currentNode?.type == FaqNodeType.SUBSECTION) {
                        sectionContent.questions.filter { it.subSectionId == currentNode.id }
                    } else {
                        sectionContent.questions.filter { it.subSectionId == null || it.subSectionId == "None" }
                    }
                    
                    items(filteredQuestions) { question ->
                        AdminQuestionCardWithDeleteOnly(
                            title = question.question,
                            subtitle = "Question",
                            onClick = { onQuestionClick(question) },
                            onDelete = {
                                val node = FaqNode(
                                    id = question.id,
                                    title = question.question,
                                    type = FaqNodeType.QUESTION,
                                    sectionId = question.sectionId,
                                    subSectionId = question.subSectionId,
                                    question = question
                                )
                                onDeleteSection(node)
                            }
                        )
                    }
                    
                    // Empty state if no questions
                    if (filteredQuestions.isEmpty()) {
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
                                        text = "No questions yet. Click the button below to add one.",
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
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Add button - positioned just before toggle buttons
        when (currentTab) {
            "subsections" -> {
                if (currentNode?.type != FaqNodeType.SUBSECTION) {
                    // Show add subsection button for root sections
                    Card(
                        onClick = { onAddSubSection(currentNodeForActions) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+ Add Sub-section",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    // Only show add subsection button if it's NOT the teaching_technical subsection
                    if (currentNode.id != "teaching_technical") {
                        Card(
                            onClick = { onAddSubSection(currentNodeForActions) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+ Add Sub-section",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
            "questions" -> {
                // Show add question button
                Card(
                    onClick = { onAddQuestion(currentNodeForActions) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ Add Question",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        
        // Tab buttons - fixed at bottom with improved UI
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Sub-sections tab with left rounded corners
            Button(
                onClick = { onTabSwitch("subsections") },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentTab == "subsections") 
                        MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(
                    topStart = 8.dp,
                    bottomStart = 8.dp,
                    topEnd = 0.dp,
                    bottomEnd = 0.dp
                )
            ) {
                Text("Sub-sections")
            }
            
            // Questions tab with right rounded corners
            Button(
                onClick = { onTabSwitch("questions") },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentTab == "questions") 
                        MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(
                    topStart = 0.dp,
                    bottomStart = 0.dp,
                    topEnd = 8.dp,
                    bottomEnd = 8.dp
                )
            ) {
                Text("Questions")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminContentCardWithActions(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
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
            
            // Edit button
            IconButton(
                onClick = onEdit,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun AdminQuestionCardWithDeleteOnly(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
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
            
            // Delete button only (no edit button for questions)
            IconButton(
                onClick = onDelete,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
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
