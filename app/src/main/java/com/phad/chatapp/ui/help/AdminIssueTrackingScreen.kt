package com.phad.chatapp.ui.help

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.phad.chatapp.R
import com.phad.chatapp.models.Issue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminIssueTrackingScreen(
    viewModel: AdminIssueViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedIssue by remember { mutableStateOf<Issue?>(null) }
    val context = LocalContext.current

    LaunchedEffect(uiState.resolveSuccess, uiState.resolveError) {
        if (uiState.resolveSuccess) {
            Toast.makeText(context, "Issue resolved successfully", Toast.LENGTH_SHORT).show()
            viewModel.resetResolveState()
            selectedIssue = null
        }
        if (uiState.resolveError != null) {
            Toast.makeText(context, uiState.resolveError, Toast.LENGTH_SHORT).show()
            viewModel.resetResolveState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resolve Issues", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorResource(id = R.color.issue_admin_primary),
                    titleContentColor = colorResource(id = R.color.ui_white),
                    navigationIconContentColor = colorResource(id = R.color.ui_white)
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.issues.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No open issues at the moment.", fontSize = 16.sp, color = colorResource(id = R.color.ui_gray))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(uiState.issues) { issue ->
                    AdminIssueCard(issue = issue) {
                        selectedIssue = issue
                    }
                }
            }
        }

        if (selectedIssue != null) {
            ResolveIssueDialog(
                issue = selectedIssue!!,
                isResolving = uiState.isResolving,
                onDismiss = { selectedIssue = null },
                onSubmit = { comment ->
                    viewModel.resolveIssue(selectedIssue!!.id, comment)
                }
            )
        }
    }
}

@Composable
fun AdminIssueCard(issue: Issue, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = issue.category,
                    fontSize = 12.sp,
                    color = colorResource(id = R.color.ui_white),
                    modifier = Modifier
                        .background(colorResource(id = R.color.issue_admin_primary), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                
                val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(issue.timestamp))
                Text(text = dateStr, fontSize = 12.sp, color = colorResource(id = R.color.ui_gray))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = issue.subject, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colorResource(id = R.color.ui_dark))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "From: ${issue.name} (${issue.rollNumber})", fontSize = 12.sp, color = colorResource(id = R.color.ui_gray_dark))
        }
    }
}

@Composable
fun ResolveIssueDialog(
    issue: Issue,
    isResolving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var comment by remember { mutableStateOf("") }
    val context = LocalContext.current

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
                Text("Resolve Issue", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colorResource(id = R.color.ui_dark))
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(text = "From: ${issue.name} (${issue.rollNumber})", fontSize = 14.sp, color = colorResource(id = R.color.ui_gray_dark), fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(text = "Subject", fontSize = 12.sp, color = colorResource(id = R.color.ui_gray))
                Text(text = issue.subject, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorResource(id = R.color.ui_dark))
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(text = "Description", fontSize = 12.sp, color = colorResource(id = R.color.ui_gray))
                Text(text = issue.description, fontSize = 14.sp, color = colorResource(id = R.color.ui_dark))
                Spacer(modifier = Modifier.height(16.dp))

                if (issue.photoUrl != null) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(issue.photoUrl))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.ui_gray_dark))
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "View Photo", modifier = Modifier.size(16.dp), tint = colorResource(id = R.color.ui_white))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Attached Photo", color = colorResource(id = R.color.ui_white))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Resolution Comment (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = colorResource(id = R.color.ui_gray_dark))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSubmit(comment) },
                        enabled = !isResolving,
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.issue_success))
                    ) {
                        if (isResolving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colorResource(id = R.color.ui_white))
                        } else {
                            Text("Mark as Resolved", color = colorResource(id = R.color.ui_white))
                        }
                    }
                }
            }
        }
    }
}
