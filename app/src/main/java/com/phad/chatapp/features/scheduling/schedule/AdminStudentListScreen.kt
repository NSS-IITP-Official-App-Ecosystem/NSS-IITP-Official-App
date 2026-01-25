package com.phad.chatapp.features.scheduling.schedule

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.features.scheduling.firebase.FirebaseManager
import com.phad.chatapp.features.scheduling.ui.theme.*
import kotlinx.coroutines.launch

// Student data model
data class Student(
    val id: String = "",
    val name: String = "",
    val rollNumber: String = "",
    val interviewScore: Int = 0,
    val academicGroup: String = ""
)

private const val TAG = "AdminStudentListScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStudentListScreen(
    navController: NavController
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // State
    var students by remember { mutableStateOf<List<Student>>(emptyList()) }
    var filteredStudents by remember { mutableStateOf<List<Student>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    
    // Edit Dialog State
    var showEditDialog by remember { mutableStateOf(false) }
    var studentToEdit by remember { mutableStateOf<Student?>(null) }
    var newScoreInput by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    // Load Students
    LaunchedEffect(Unit) {
        isLoading = true
        FirebaseManager.getInstance().getCollection(
            "ttwStudents",
            onSuccess = { snapshot ->
                val loadedStudents = snapshot.documents.mapNotNull { doc ->
                    try {
                        val scoreVal = doc.get("interviewScore")
                        val score = when(scoreVal) {
                            is Long -> scoreVal.toInt()
                            is String -> scoreVal.toIntOrNull() ?: 0
                            else -> 0
                        }
                        
                        Student(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            rollNumber = doc.getString("rollNumber") ?: doc.id,
                            interviewScore = score,
                            academicGroup = doc.getString("academicGroup") ?: ""
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing student doc ${doc.id}", e)
                        null
                    }
                }
                students = loadedStudents
                filteredStudents = loadedStudents
                isLoading = false
            },
            onFailure = { e ->
                Log.e(TAG, "Error loading students", e)
                isLoading = false
            }
        )
    }

    // Filter Logic
    LaunchedEffect(searchQuery, students) {
        if (searchQuery.isBlank()) {
            filteredStudents = students
        } else {
            val query = searchQuery.trim()
            filteredStudents = students.filter { student ->
                student.name.contains(query, ignoreCase = true) ||
                student.rollNumber.contains(query, ignoreCase = true)
            }
        }
    }

    // Update Score Function
    fun updateStudentScore(student: Student, newScore: Int) {
        isSaving = true
        val db = FirebaseFirestore.getInstance()
        db.collection("ttwStudents").document(student.id)
            .update("interviewScore", newScore)
            .addOnSuccessListener {
                isSaving = false
                showEditDialog = false
                
                // Update local list
                val updatedList = students.map { 
                    if (it.id == student.id) it.copy(interviewScore = newScore) else it 
                }
                students = updatedList
                
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Score updated successfully")
                }
            }
            .addOnFailureListener { e ->
                isSaving = false
                Log.e(TAG, "Error updating score", e)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Failed to update score: ${e.message}")
                }
            }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
             contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp)
                        .padding(start = 4.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { 
                             if (isSearchActive) {
                                isSearchActive = false
                                searchQuery = ""
                            } else {
                                navController.navigateUp() 
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Search by Name or Roll No...",
                                    color = NeutralGray,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .padding(end = 8.dp),
                            shape = RoundedCornerShape(25.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = NeutralCardSurface,
                                unfocusedContainerColor = NeutralCardSurface,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.Transparent, 
                                unfocusedBorderColor = Color.Transparent
                            ),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { 
                                    searchQuery = "" 
                                    isSearchActive = false 
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = NeutralGray)
                                }
                            }
                        )
                    } else {
                        Text(
                            text = "Manage Students",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        )

                        if (!isLoading) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp, end = 4.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(YellowAccent)
                                    .clickable { isSearchActive = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Content
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = YellowAccent)
                    }
                } else if (filteredStudents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                         Text(
                            text = if (searchQuery.isNotEmpty()) "No students found matching '$searchQuery'" else "No students found",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                    ) {
                        items(filteredStudents, key = { it.id }) { student ->
                            StudentCard(
                                student = student,
                                onClick = {
                                    studentToEdit = student
                                    newScoreInput = student.interviewScore.toString()
                                    showEditDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Score Dialog
    if (showEditDialog && studentToEdit != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = NeutralCardSurface,
            title = {
                Text(
                    text = "Edit Interview Score",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            },
            text = {
                Column {
                    Text(
                        text = "Student: ${studentToEdit!!.name}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Roll No: ${studentToEdit!!.rollNumber}",
                        color = Color(0xFFB0B0B0),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    OutlinedTextField(
                        value = newScoreInput,
                        onValueChange = { if (it.all { char -> char.isDigit() }) newScoreInput = it },
                        label = { Text("Interview Score") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                         colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YellowAccent,
                            unfocusedBorderColor = NeutralGray.copy(alpha = 0.7f),
                            focusedLabelColor = YellowAccent,
                            unfocusedLabelColor = NeutralGray,
                            cursorColor = YellowAccent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val score = newScoreInput.toIntOrNull()
                        if (score != null) {
                            updateStudentScore(studentToEdit!!, score)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YellowAccent),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
                    } else {
                        Text("Save", color = Color.Black)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = YellowAccent)
                }
            }
        )
    }
}

@Composable
fun StudentCard(
    student: Student,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NeutralCardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = student.rollNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0B0B0)
                )
                 if (student.academicGroup.isNotEmpty()) {
                    Text(
                        text = "Group: ${student.academicGroup}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF808080),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            
            Column(
                horizontalAlignment = Alignment.End
            ) {
                 Text(
                    text = "Score",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB0B0B0)
                )
                Text(
                    text = "${student.interviewScore}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = YellowAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
