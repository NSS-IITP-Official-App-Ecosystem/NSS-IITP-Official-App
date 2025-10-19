package com.phad.chatapp.features.home.faqs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.phad.chatapp.features.home.faqs.data.*
import com.phad.chatapp.features.home.faqs.utils.AdminAccessControl
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.utils.LinkDetector
import android.content.Intent
import androidx.compose.runtime.remember
import java.util.regex.Pattern

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(
    onNavigateBack: () -> Unit,
    viewModel: FaqViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = FaqViewModel.Factory(
            repository = FaqRepository()
        )
    )
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val adminInfo = remember { AdminAccessControl.getAdminInfo(sessionManager) }
    val uiState by viewModel.uiState.collectAsState()
    
    // Conversation history for QnA window
    var conversationHistory by remember { mutableStateOf<List<FaqQuestion>>(emptyList()) }
    var lastSelectedQuestionId by remember { mutableStateOf<String?>(null) }
    
    // Expanded sections state for indented format
    var expandedSections by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedSubSections by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<FaqSearchResult>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        TopBar(
            onBack = onNavigateBack,
            onRefresh = { viewModel.refresh() },
            onEdit = {
                        if (adminInfo.isAdmin) {
                                    val intent = Intent(context, AdminFaqActivity::class.java).apply {
                                        putExtra("user_type", adminInfo.userType)
                                    }
                                    context.startActivity(intent)
                                }
            },
            showEdit = adminInfo.isAdmin
        )
        
        // Search Bar
        SearchBar(
            value = searchQuery,
            onValueChange = { query ->
                searchQuery = query
                searchResults = performSearch(query, uiState)
                // Auto-expand sections that contain search results
                if (query.isNotEmpty()) {
                    expandedSections = searchResults.map { it.question.sectionId }.toSet()
                } else {
                    expandedSections = emptySet()
                    expandedSubSections = emptySet()
                }
            },
            placeholder = "Search FAQs..."
        )
        
        // Main Content - Split into Answer Panel and Question Tree
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Answer Panel (40% of screen)
                ConversationPanel(
                    conversationHistory = conversationHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.4f)
                )
                
                // Divider
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
                
                // Question Tree (60% of screen) - now with expandable format
                ExpandableQuestionTree(
                    viewModel = viewModel,
                    uiState = uiState,
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    onQuestionSelect = { question ->
                        // Only add if it's not the same question as the last one
                        if (lastSelectedQuestionId != question.id) {
                            conversationHistory = conversationHistory + question
                            lastSelectedQuestionId = question.id
                        }
                    },
                    expandedSections = expandedSections,
                    expandedSubSections = expandedSubSections,
                    onSectionToggle = { sectionId ->
                        expandedSections = if (expandedSections.contains(sectionId)) {
                            expandedSections - sectionId
                        } else {
                            expandedSections + sectionId
                        }
                    },
                    onSubSectionToggle = { subSectionId ->
                        expandedSubSections = if (expandedSubSections.contains(subSectionId)) {
                            expandedSubSections - subSectionId
                        } else {
                            expandedSubSections + subSectionId
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f)
                )
            }
        }
    }
}

@Composable
fun TopBar(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    showEdit: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .height(80.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "FAQs",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                if (showEdit) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Search FAQs..."
) {
    Box(
                modifier = Modifier
                    .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { 
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                ) 
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            singleLine = true
        )
    }
}

@Composable
fun ConversationPanel(
    conversationHistory: List<FaqQuestion>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // Auto-scroll to the latest question when conversation history changes
    LaunchedEffect(conversationHistory.size) {
        if (conversationHistory.isNotEmpty()) {
            listState.animateScrollToItem(conversationHistory.size - 1)
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (conversationHistory.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Select a question to view the answer",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(conversationHistory) { index: Int, question: FaqQuestion ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Question Section
                        Text(
//                            text = "Question ${index + 1}",
                            text = "Question",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = question.question,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        
                        // Answer Section
                        Text(
                            text = "Answer",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            when (question.answerType) {
                                com.phad.chatapp.features.home.faqs.data.AnswerType.TEXT -> {
                                    val raw = question.answer as? String ?: ""
                                    val lines = raw.split('\n')
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        lines.forEach { line ->
                                            val level = Regex("""^\\\\t(\d+)""").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                                            val content = if (level > 0) line.replaceFirst(Regex("""^\\\\t\d+\s*"""), "") else line
                                            val padding = (level.coerceAtLeast(0)).coerceAtMost(20) * 16
                                            Box(modifier = Modifier.fillMaxWidth().padding(start = padding.dp)) {
                                                LinkDetector.ClickableTextWithLinks(
                                                    text = content,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableQuestionTree(
    viewModel: FaqViewModel,
    uiState: FaqUiState,
    searchQuery: String,
    searchResults: List<FaqSearchResult>,
    onQuestionSelect: (FaqQuestion) -> Unit,
    expandedSections: Set<String>,
    expandedSubSections: Set<String>,
    onSectionToggle: (String) -> Unit,
    onSubSectionToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Error: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Show sections based on search results or all sections
                val sectionsToShow = if (searchQuery.isNotEmpty()) {
                    searchResults.map { it.question.sectionId }.distinct().mapNotNull { sectionId ->
                        uiState.sections[sectionId]
                    }
                } else {
                    uiState.sections.values.toList()
                }
                
                items(sectionsToShow) { section ->
                    val sectionQuestions = if (searchQuery.isNotEmpty()) {
                        searchResults.filter { it.question.sectionId == section.id }.map { it.question }
                    } else {
                        uiState.questions[section.id] ?: emptyList()
                    }
                    
                    val sectionSubSections = if (searchQuery.isNotEmpty()) {
                        sectionQuestions.mapNotNull { question ->
                            question.subSectionId?.let { subId ->
                                uiState.subSections[section.id]?.find { it.id == subId }
                            }
                        }.distinctBy { it.id }
                    } else {
                        uiState.subSections[section.id] ?: emptyList()
                    }
                    
                    ExpandableSectionItem(
                        section = section,
                        subSections = sectionSubSections,
                        questions = sectionQuestions,
                        isExpanded = expandedSections.contains(section.id),
                        expandedSubSections = expandedSubSections,
                        onSectionToggle = onSectionToggle,
                        onSubSectionToggle = onSubSectionToggle,
                        onQuestionSelect = onQuestionSelect,
                        searchQuery = searchQuery,
                        searchResults = searchResults
                    )
                }
            }
        }
    }
}

@Composable
fun ExpandableSectionItem(
    section: FaqSection,
    subSections: List<FaqSubSection>,
    questions: List<FaqQuestion>,
    isExpanded: Boolean,
    expandedSubSections: Set<String>,
    onSectionToggle: (String) -> Unit,
    onSubSectionToggle: (String) -> Unit,
    onQuestionSelect: (FaqQuestion) -> Unit,
    searchQuery: String = "",
    searchResults: List<FaqSearchResult> = emptyList()
) {
    Column {
        // Section header
        Card(
            onClick = { onSectionToggle(section.id) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // Expanded content
        if (isExpanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp)
            ) {
                // Show direct questions (without subsection) - filtered by search if applicable
                val directQuestions = questions.filter { it.subSectionId == null }
                directQuestions.forEach { question ->
                    QuestionItem(
                        question = question,
                        isSelected = false,
                        onClick = { onQuestionSelect(question) },
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                
                // Show subsections - filtered by search if applicable
                subSections.forEach { subSection ->
                    val subSectionQuestions = questions.filter { it.subSectionId == subSection.id }
                    if (subSectionQuestions.isNotEmpty()) {
                        ExpandableSubSectionItem(
                            subSection = subSection,
                            questions = subSectionQuestions,
                            isExpanded = expandedSubSections.contains(subSection.id),
                            onSubSectionToggle = onSubSectionToggle,
                            onQuestionSelect = onQuestionSelect
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableSubSectionItem(
    subSection: FaqSubSection,
    questions: List<FaqQuestion>,
    isExpanded: Boolean,
    onSubSectionToggle: (String) -> Unit,
    onQuestionSelect: (FaqQuestion) -> Unit
) {
    Column {
        // Subsection header
        Card(
            onClick = { onSubSectionToggle(subSection.id) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (isExpanded) Icons.Default.FolderOpen else Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = subSection.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // Expanded questions
        if (isExpanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp)
            ) {
                questions.forEach { question ->
                    QuestionItem(
                        question = question,
                        isSelected = false,
                        onClick = { onQuestionSelect(question) },
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SectionItem(
    section: FaqSection,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = section.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SubSectionItem(
    subSection: FaqSubSection,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = subSection.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun QuestionItem(
    question: FaqQuestion,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.HelpOutline,
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                },
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = question.question,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

// Search function to match text across all FAQs
private fun performSearch(query: String, uiState: FaqUiState): List<FaqSearchResult> {
    if (query.isEmpty()) return emptyList()
    
    val results = mutableListOf<FaqSearchResult>()
    val lowerQuery = query.lowercase()
    
    uiState.questions.forEach { (sectionId, questions) ->
        val section = uiState.sections[sectionId]
        if (section != null) {
            questions.forEach { question ->
                // Search in question text
                val questionMatches = question.question.lowercase().contains(lowerQuery)
                
                // Search in answer content
                val answerText = question.answer as? String ?: ""
                val answerMatches = answerText.lowercase().contains(lowerQuery)
                
                // If either question or answer contains the search term
                if (questionMatches || answerMatches) {
                    val subSection = question.subSectionId?.let { subId ->
                        uiState.subSections[sectionId]?.find { it.id == subId }
                    }
                    
                    results.add(
                        FaqSearchResult(
                            question = question,
                            sectionTitle = section.title,
                            subSectionTitle = subSection?.title
                        )
                    )
                }
            }
        }
    }
    
    return results
}

