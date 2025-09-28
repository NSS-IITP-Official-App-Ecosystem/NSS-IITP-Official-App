package com.phad.chatapp.features.home.faqs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(
    onNavigateBack: () -> Unit,
    interfaceType: String,
    viewModel: FaqViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = FaqViewModel.Factory(FaqRepository(), interfaceType)
    )
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val adminInfo = remember { AdminAccessControl.getAdminInfo(sessionManager) }
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when new questions are added
    LaunchedEffect(uiState.history.size) {
        if (uiState.history.isNotEmpty()) {
            listState.animateScrollToItem(uiState.history.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.getCurrentTitle()) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Show admin pencil icon only for admins
                    if (adminInfo.isAdmin) {
                        AdminFaqPencilIcon(
                            onClick = {
                                val intent = Intent(context, AdminFaqActivity::class.java).apply {
                                    putExtra("user_type", adminInfo.userType)
                                    putExtra("is_teaching_wing", adminInfo.isTeachingWing)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
//                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                // Search Bar
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    onSearch = { viewModel.updateSearchQuery(it) },
                    active = false,
                    onActiveChange = {},
                    placeholder = { Text("Search a keyword...") },
                    leadingIcon = { Icon(Icons.Default.Search, "Search") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Search suggestions would go here if needed
                }

                // Upper section - History (60% of screen)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f)
                        .padding(horizontal = 16.dp),
                    state = listState
                ) {
                    items(uiState.history) { question ->
                        QuestionAnswerCard(question = question)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Lower section - Navigation (40% of screen)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.4f)
                        .background(Color(0xFFF5F5F5))
                        .drawBehind {
                            val strokeWidth = 2.dp.toPx()
                            drawLine(
                                color = Color(0xFFE0E0E0),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = strokeWidth
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Navigation buttons (if not at root and not in search mode)
                    if (uiState.navigationStack.isNotEmpty() && !uiState.isSearchMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { viewModel.navigateBack() },
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Back")
                            }
                            
                            Button(
                                onClick = { viewModel.navigateToRoot() },
                                modifier = Modifier.weight(1f).padding(start = 8.dp)
                            ) {
                                Icon(Icons.Default.Home, "Main Menu")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Main Menu")
                            }
                        }
                    }

                    // Search mode back button
                    if (uiState.isSearchMode) {
                        Button(
                            onClick = { viewModel.navigateToRoot() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Home, "Main Menu")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back to Main Menu")
                        }
                    }

                    // Navigation buttons list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(viewModel.getCurrentItems()) { item ->
                            NavigationButton(
                                item = item,
                                onClick = { viewModel.navigateTo(item) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuestionAnswerCard(question: FaqQuestion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = question.question,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            when (question.answerType) {
                AnswerType.TEXT -> {
                    LinkDetector.ClickableTextWithLinks(
                        text = question.answer as? String ?: "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                AnswerType.BULLET_POINTS -> {
                    @Suppress("UNCHECKED_CAST")
                    val points = question.answer as? List<String> ?: emptyList()
                    points.forEach { point ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text("• ", style = MaterialTheme.typography.bodyMedium)
                            LinkDetector.ClickableTextWithLinks(
                                text = point,
                                style = MaterialTheme.typography.bodyMedium
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
fun NavigationButton(
    item: FaqNavigationItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = when (item) {
                    is FaqNavigationItem.Section -> item.section.title
                    is FaqNavigationItem.SubSection -> item.subSection.title
                    is FaqNavigationItem.Question -> item.question.question
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
} 