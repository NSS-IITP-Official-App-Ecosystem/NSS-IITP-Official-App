package com.phad.chatapp.features.home.faqs.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phad.chatapp.features.home.faqs.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFaqScreen(
    onNavigateBack: () -> Unit,
    userType: String,
    viewModel: AdminFaqViewModel = viewModel(
        factory = AdminFaqViewModel.Factory(
            repository = FaqRepository(),
            userType = userType
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()

    // Handle phone's back button
    BackHandler(enabled = true) {
        if (uiState.navigationStack.isNotEmpty()) {
            // If we're in a section/subsection, go back to previous level
            viewModel.navigateBack()
        } else {
            // If we're at the main FAQ management screen, exit
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "FAQ Management",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            // Top-left back button always goes to main FAQ screen
                            onNavigateBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    ErrorMessage(
                        error = uiState.error!!,
                        onRetry = { /* Retry logic if needed */ },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    if (uiState.currentSectionContent != null) {
                        AdminSectionContentView(
                            sectionContent = uiState.currentSectionContent!!,
                            currentNode = uiState.navigationStack.lastOrNull(),
                            currentTab = uiState.currentTab,
                            onSubSectionClick = { subSection: FaqSubSection ->
                                viewModel.openSubSection(subSection)
                            },
                            onQuestionClick = { question: FaqQuestion ->
                                viewModel.openQuestion(question)
                            },
                            onTabSwitch = { tab ->
                                viewModel.switchTab(tab)
                            },
                            onEditSectionName = { node ->
                                viewModel.editSectionName(node)
                            },
                            onDeleteSection = { node ->
                                viewModel.deleteSection(node)
                            },
                            onAddSubSection = { node ->
                                viewModel.addSubSection(node)
                            },
                            onAddQuestion = { node ->
                                viewModel.addQuestion(node)
                            }
                        )
                    } else {
                        AdminFaqContent(
                            uiState = uiState,
                            onSectionClick = { section ->
                                viewModel.openRootSection(section)
                            },
                            onEditSectionName = { section ->
                                val node = FaqNode(
                                    id = section.id,
                                    title = section.title,
                                    type = FaqNodeType.ROOT_SECTION,
                                    section = section
                                )
                                viewModel.editSectionName(node)
                            }
                        )
                    }
                }
            }

            // Operation selection dialog
            if (uiState.showOperationDialog && uiState.selectedNode != null) {
                AdminOperationDialog(
                    node = uiState.selectedNode!!,
                    onOperationSelected = { operation ->
                        viewModel.selectOperation(operation)
                    },
                    onDismiss = {
                        viewModel.hideOperationDialog()
                    }
                )
            }

            // Form dialog for add/edit operations
            AdminFormDialog(
                dialogState = dialogState,
                onFormDataChange = { formData ->
                    viewModel.updateFormData(formData)
                },
                onConfirm = {
                    viewModel.executeOperation()
                },
                onDismiss = {
                    viewModel.hideDialog()
                },
                onDelete = if (dialogState.operation == AdminOperation.EDIT_QUESTION) {
                    {
                        val node = dialogState.node
                        if (node != null) {
                            viewModel.hideDialog()
                            viewModel.deleteSection(node)
                        }
                    }
                } else null
            )

            // Delete confirmation dialog
            if (uiState.showDeleteConfirmation && uiState.selectedNode != null) {
                DeleteConfirmationDialog(
                    node = uiState.selectedNode!!,
                    onConfirm = {
                        viewModel.executeDelete()
                    },
                    onDismiss = {
                        viewModel.hideDeleteConfirmation()
                    }
                )
            }
        }
    }
}

@Composable
fun AdminFaqContent(
    uiState: AdminFaqUiState,
    onSectionClick: (FaqSection) -> Unit,
    onEditSectionName: (FaqSection) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Simplified header (removed user type box per requirements)
        Text(
            text = "Click on any section below to manage its content",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Root sections title
        Text(
            text = "Root Sections",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Root sections list
        if (uiState.rootSections.isEmpty()) {
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
                        text = "No sections available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.rootSections) { section ->
                    AdminFaqRootSectionCard(
                        section = section,
                        onClick = { onSectionClick(section) },
                        onEditClick = { onEditSectionName(section) }
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorMessage(
    error: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Retry")
                }
            }
        }
    }
}
