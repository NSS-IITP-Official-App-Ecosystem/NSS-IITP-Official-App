package com.phad.chatapp.features.scheduling.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.Timestamp
import com.phad.chatapp.features.scheduling.ui.theme.*
import kotlinx.coroutines.tasks.await

data class Subject(
    val id: String = "",
    val name: String = "",
    val createdAt: Timestamp? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSubjectsScreen(
    navController: NavController,
    onBackClick: () -> Unit
) {
    var subjects by remember { mutableStateOf<List<Subject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var subjectToDelete by remember { mutableStateOf<Subject?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val bottomNavId = remember { 
        context.resources.getIdentifier("bottom_nav_container", "id", context.packageName) 
    }

    // Hide bottom navigation when this screen is active
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val bottomNav = activity?.findViewById<android.view.View>(bottomNavId)
        bottomNav?.visibility = android.view.View.GONE
        
        onDispose {
            bottomNav?.visibility = android.view.View.VISIBLE
        }
    }

    val db = FirebaseFirestore.getInstance()
    val subjectsCollection = "TTW_Subjects"

    // Load subjects from Firebase
    LaunchedEffect(Unit) {
        try {
            val snapshot = db.collection(subjectsCollection)
                .get()
                .await()
            
            subjects = snapshot.documents.mapNotNull { doc ->
                Subject(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    createdAt = doc.getTimestamp("createdAt")
                )
            }.sortedBy { it.name }
            
            isLoading = false
        } catch (e: Exception) {
            errorMessage = "Failed to load subjects: ${e.message}"
            isLoading = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp)
                        .padding(start = 4.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Text(
                        text = "Add or Delete Subjects",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                }

                Text(
                    text = "Manage subjects for teaching sessions",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp, start = 20.dp, end = 20.dp)
                )

                // Error message
                errorMessage?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFD32F2F).copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = error,
                            color = Color(0xFFD32F2F),
                            modifier = Modifier.padding(16.dp),
                            fontSize = 14.sp
                        )
                    }
                }

                // Content
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFFFD600)
                            )
                        }
                    }
                    subjects.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "No subjects added yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFB0B0B0)
                                )
                                Text(
                                    text = "Tap + to add a subject",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF808080)
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(subjects) { subject ->
                                SubjectItem(
                                    subject = subject,
                                    onDelete = {
                                        subjectToDelete = subject
                                        showDeleteDialog = true
                                    }
                                )
                            }
                            // Bottom padding to prevent overlap with FAB
                            item {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                    }
                }
            }

            // Floating Action Button
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFFFFD600),
                contentColor = Color.Black,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Subject"
                )
            }
        }
    }

    // Add Subject Dialog
    if (showAddDialog) {
        AddSubjectDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { subjectName ->
                val name = subjectName.trim()
                
                // Check if subject already exists
                if (subjects.any { it.name.equals(name, ignoreCase = true) }) {
                     errorMessage = "Subject '$name' already exists!"
                     showAddDialog = false
                     return@AddSubjectDialog
                }

                // Add subject to Firebase with Name as Doc ID
                val newSubject = hashMapOf(
                    "name" to name,
                    "createdAt" to Timestamp.now()
                )
                
                // Use .document(name).set() instead of .add()
                db.collection(subjectsCollection)
                    .document(name)
                    .set(newSubject)
                    .addOnSuccessListener { 
                        // Refresh the list
                        subjects = subjects + Subject(
                            id = name,
                            name = name,
                            createdAt = Timestamp.now()
                        )
                        subjects = subjects.sortedBy { it.name }
                        
                        // Sync: Add to all students
                        // Here ID is the Name
                        updateAllStudentsOnAdd(db, name)
                        
                        showAddDialog = false
                    }
                    .addOnFailureListener { e ->
                        errorMessage = "Failed to add subject: ${e.message}"
                        showAddDialog = false
                    }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog && subjectToDelete != null) {
        DeleteConfirmationDialog(
            subjectName = subjectToDelete!!.name,
            onDismiss = {
                showDeleteDialog = false
                subjectToDelete = null
            },
            onConfirm = {
                // Delete from Firebase
                db.collection(subjectsCollection)
                    .document(subjectToDelete!!.id)
                    .delete()
                    .addOnSuccessListener {
                        // Sync: Remove from all students
                        val subjectId = subjectToDelete!!.id
                        updateAllStudentsOnDelete(db, subjectId)

                        // Remove from list
                        subjects = subjects.filter { it.id != subjectToDelete!!.id }
                        showDeleteDialog = false
                        subjectToDelete = null
                    }
                    .addOnFailureListener { e ->
                        errorMessage = "Failed to delete subject: ${e.message}"
                        showDeleteDialog = false
                        subjectToDelete = null
                    }
            }
        )
    }
}

@Composable
fun SubjectItem(
    subject: Subject,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = NeutralCardSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Yellow circular icon background
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFD600))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = subject.name.firstOrNull()?.uppercase() ?: "?",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            // Subject name
            Text(
                text = subject.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            )

            // Delete button
            IconButton(
                onClick = onDelete
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Subject",
                    tint = Color(0xFFD32F2F)
                )
            }
        }
    }
}

@Composable
fun AddSubjectDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var subjectName by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add New Subject",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter the subject name:",
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = subjectName,
                    onValueChange = {
                        subjectName = it
                        showError = false
                    },
                    label = { Text("Subject Name") },
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("Subject name cannot be empty") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (subjectName.trim().isEmpty()) {
                        showError = true
                    } else {
                        onAdd(subjectName.trim())
                    }
                }
            ) {
                Text(
                    text = "Add",
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(
    subjectName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Subject",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text("Are you sure you want to delete \"$subjectName\"?")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text(
                    text = "Delete",
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
// Helper functions for Bulk Updates
fun updateAllStudentsOnAdd(db: FirebaseFirestore, subjectId: String) {
    db.collection("ttwStudents").get().addOnSuccessListener { snapshot ->
        if (snapshot.isEmpty) return@addOnSuccessListener
        
        // Firestore batch limit is 500. We chunk it.
        val chunks = snapshot.documents.chunked(450)
        chunks.forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { doc ->
                batch.update(doc.reference, "subjectPreferences", FieldValue.arrayUnion(subjectId))
            }
            batch.commit().addOnFailureListener { e -> 
                android.util.Log.e("ManageSubjects", "Failed to sync add for chunk: ${e.message}")
            }
        }
    }
}

fun updateAllStudentsOnDelete(db: FirebaseFirestore, subjectId: String) {
    db.collection("ttwStudents").get().addOnSuccessListener { snapshot ->
        if (snapshot.isEmpty) return@addOnSuccessListener
        
        val chunks = snapshot.documents.chunked(450)
        chunks.forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { doc ->
                batch.update(doc.reference, "subjectPreferences", FieldValue.arrayRemove(subjectId))
            }
            batch.commit().addOnFailureListener { e ->
                 android.util.Log.e("ManageSubjects", "Failed to sync delete for chunk: ${e.message}")
            }
        }
    }
}
