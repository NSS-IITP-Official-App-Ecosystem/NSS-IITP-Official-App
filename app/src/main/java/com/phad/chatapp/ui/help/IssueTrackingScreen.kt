package com.phad.chatapp.ui.help

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.clickable
import coil.compose.AsyncImage
import com.phad.chatapp.R
import com.phad.chatapp.models.Issue
import com.phad.chatapp.models.IssueStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun IssueTrackingScreen(
    viewModel: IssueViewModel,
    userName: String,
    userRollNumber: String,
    userWings: List<String>,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Light mode colors
    val backgroundColor = colorResource(id = R.color.ui_white)
    val surfaceColor = colorResource(id = R.color.ui_gray_light)
    val primaryColor = colorResource(id = R.color.ui_blue)
    val onSurfaceColor = colorResource(id = R.color.ui_dark)
    val secondaryTextColor = colorResource(id = R.color.ui_gray_dark)

    var showComposeDialog by remember { mutableStateOf(false) }
    var selectedIssue by remember { mutableStateOf<Issue?>(null) }
    var issueToClose by remember { mutableStateOf<Issue?>(null) }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.submitSuccess) {
        if (uiState.submitSuccess) {
            showComposeDialog = false
            Toast.makeText(context, "Issue submitted successfully", Toast.LENGTH_SHORT).show()
            viewModel.resetSubmitState()
            // Switch to Open Issues tab
            pagerState.animateScrollToPage(0)
        }
    }

    LaunchedEffect(uiState.closeSuccess) {
        if (uiState.closeSuccess) {
            issueToClose = null
            Toast.makeText(context, "Issue closed successfully", Toast.LENGTH_SHORT).show()
            viewModel.resetSubmitState()
            // Switch to Closed Issues tab
            pagerState.animateScrollToPage(1)
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.resetSubmitState()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Issue Tracking", fontWeight = FontWeight.Bold, color = onSurfaceColor) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = onSurfaceColor)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
                )
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = backgroundColor,
                    contentColor = primaryColor,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = primaryColor
                        )
                    }
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text("Open Issues", color = if (pagerState.currentPage == 0) primaryColor else secondaryTextColor) }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text("Closed Issues", color = if (pagerState.currentPage == 1) primaryColor else secondaryTextColor) }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showComposeDialog = true },
                containerColor = primaryColor,
                contentColor = Color.White,
                modifier = Modifier.padding(bottom = 72.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Compose Issue")
            }
        },
        containerColor = backgroundColor
    ) { paddingValues ->

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primaryColor)
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) { page ->
                val issues = if (page == 0) uiState.openIssues else uiState.closedIssues
                
                if (issues.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No issues found", color = secondaryTextColor, fontSize = 16.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(issues) { issue ->
                            IssueItem(
                                issue = issue,
                                surfaceColor = surfaceColor,
                                onSurfaceColor = onSurfaceColor,
                                secondaryTextColor = secondaryTextColor,
                                onClick = { selectedIssue = it },
                                onCloseIssue = { issueToClose = it }
                            )
                        }
                    }
                }
            }
        }

        if (showComposeDialog) {
            ComposeIssueDialog(
                onDismiss = { showComposeDialog = false },
                onSubmit = { issue, uri ->
                    viewModel.submitIssue(issue, uri)
                },
                userName = userName,
                userRollNumber = userRollNumber,
                userWings = userWings,
                isSubmitting = uiState.isSubmitting
            )
        }

        if (issueToClose != null) {
            CloseIssueDialog(
                onDismiss = { issueToClose = null },
                onSubmit = { comment ->
                    viewModel.closeIssue(issueToClose!!.id, comment)
                },
                isClosing = uiState.isClosing
            )
        }

        if (selectedIssue != null) {
            StudentIssueDetailDialog(
                issue = selectedIssue!!,
                onDismiss = { selectedIssue = null }
            )
        }

        if (uiState.isSubmitting || uiState.isClosing) {
            Dialog(
                onDismissRequest = { },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color.White, shape = RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = primaryColor)
                }
            }
        }
    }
}

@Composable
fun IssueItem(
    issue: Issue,
    surfaceColor: Color,
    onSurfaceColor: Color,
    secondaryTextColor: Color,
    onClick: (Issue) -> Unit,
    onCloseIssue: (Issue) -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val dateString = dateFormat.format(Date(issue.timestamp))
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(issue) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = issue.category,
                    color = colorResource(id = R.color.ui_blue),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = dateString,
                    color = secondaryTextColor,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = issue.subject,
                color = onSurfaceColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (issue.status == IssueStatus.CLOSED && issue.resolveComment != null) "Comment: ${issue.resolveComment}" else issue.description,
                color = secondaryTextColor,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "To: ${issue.addressedTo}",
                    color = secondaryTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                if (issue.photoUrl != null) {
                    Icon(
                        Icons.Default.Attachment,
                        contentDescription = "Has Attachment",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(16.dp).padding(end = 8.dp)
                    )
                }
                if (issue.status == IssueStatus.OPEN) {
                    OutlinedButton(
                        onClick = { onCloseIssue(issue) },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text("Close", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StudentIssueDetailDialog(
    issue: Issue,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val dateString = dateFormat.format(Date(issue.timestamp))

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(issue.category, fontSize = 12.sp, color = colorResource(id = R.color.ui_blue), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(issue.subject, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(dateString, fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Description", fontSize = 12.sp, color = Color.Gray)
                Text(issue.description, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Addressed To: ${issue.addressedTo}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                
                if (issue.status == IssueStatus.CLOSED) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color.LightGray, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Resolution Details", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val closedByName = if (issue.resolvedByRollNumber == issue.rollNumber) "Self" else (issue.resolvedByName ?: "An Admin")
                    Text("Closed by: $closedByName", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("Comment", fontSize = 12.sp, color = Color.Gray)
                    Text(issue.resolveComment ?: "No comment provided.", fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = colorResource(id = R.color.ui_blue))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloseIssueDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    isClosing: Boolean = false
) {
    var comment by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text("Close Issue", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = comment,
                    onValueChange = { 
                        comment = it
                        showError = false
                    },
                    label = { Text("Reason for closing") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    isError = showError,
                    supportingText = { if (showError) Text("Please provide a reason") }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (comment.isNotBlank()) {
                                onSubmit(comment)
                            } else {
                                showError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.ui_blue))
                    ) {
                        Text("Submit")
                    }
                }
            }
        }
    }
}
