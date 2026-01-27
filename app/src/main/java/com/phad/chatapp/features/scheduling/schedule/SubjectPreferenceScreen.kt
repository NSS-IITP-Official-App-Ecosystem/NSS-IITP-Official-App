package com.phad.chatapp.features.scheduling.schedule

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.phad.chatapp.features.scheduling.ui.theme.DarkBackground
import com.phad.chatapp.features.scheduling.ui.theme.NeutralCardSurface
import com.phad.chatapp.utils.SessionManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectPreferenceScreen(
    navController: NavController,
    onBackClick: () -> Unit
) {
    var allSubjects by remember { mutableStateOf<List<Subject>>(emptyList()) }
    var preferenceIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()

    // Derived Lists
    val preferenceSubjects = remember(allSubjects, preferenceIds) {
        preferenceIds.mapNotNull { id -> allSubjects.find { it.id == id } }
    }
    val availableSubjects = remember(allSubjects, preferenceIds) {
        allSubjects.filter { !preferenceIds.contains(it.id) }.sortedBy { it.name }
    }

    // Fetch Data
    LaunchedEffect(Unit) {
        try {
             val userId = sessionManager.fetchUserId()
             if (userId.isNullOrEmpty()) throw Exception("User not logged in or invalid roll number")
            
            // 1. Fetch User Preferences from ttwStudents
            val userDoc = db.collection("ttwStudents").document(userId).get().await()
            val prefs = userDoc.get("subjectPreferences") as? List<String> ?: emptyList()
            preferenceIds = prefs
            
            // 2. Fetch All Subjects from TTW_Subjects
            val subjectsSnapshot = db.collection("TTW_Subjects").get().await()
            val subjects = subjectsSnapshot.documents.mapNotNull { doc ->
                Subject(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    createdAt = doc.getTimestamp("createdAt")
                )
            }
            allSubjects = subjects
            isLoading = false
        } catch (e: Exception) {
            errorMessage = "Failed to load data: ${e.message}"
            isLoading = false
        }
    }

    // Save Preference Function
    fun savePreferences(newIds: List<String>) {
        preferenceIds = newIds // Optimistic update
        scope.launch {
            try {
                isSaving = true
                val userId = sessionManager.fetchUserId()
                if (userId.isNullOrEmpty()) return@launch
                
                db.collection("ttwStudents").document(userId)
                    .set(mapOf("subjectPreferences" to newIds), SetOptions.merge())
                    .await()
                
                isSaving = false
            } catch (e: Exception) {
                Log.e("SubjectPreference", "Error saving preferences", e)
                errorMessage = "Failed to save changes"
                isSaving = false
            }
        }
    }

    fun moveUp(index: Int) {
        if (index > 0) {
            val newList = preferenceIds.toMutableList()
            val item = newList.removeAt(index)
            newList.add(index - 1, item)
            savePreferences(newList)
        }
    }

    fun moveDown(index: Int) {
        if (index < preferenceIds.size - 1) {
            val newList = preferenceIds.toMutableList()
            val item = newList.removeAt(index)
            newList.add(index + 1, item)
            savePreferences(newList)
        }
    }

    fun removeSubject(id: String) {
        val newList = preferenceIds.toMutableList()
        newList.remove(id)
        savePreferences(newList)
    }

    fun addSubject(id: String) {
        val newList = preferenceIds.toMutableList()
        newList.add(id)
        savePreferences(newList) // Adds to end by default
    }

    // UI Structure
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        // Hide bottom nav hook
        val context = androidx.compose.ui.platform.LocalContext.current
        val bottomNavId = remember {
            context.resources.getIdentifier("bottom_nav_container", "id", context.packageName)
        }
        DisposableEffect(Unit) {
            val activity = context as? android.app.Activity
            val bottomNav = activity?.findViewById<android.view.View>(bottomNavId)
            bottomNav?.visibility = android.view.View.GONE
            onDispose { bottomNav?.visibility = android.view.View.VISIBLE }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Header
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
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Text(
                    text = "Subject Preference",
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = Color.Red, modifier = Modifier.padding(16.dp))
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFFFD600))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Section 1: Your Preferences
                        item {
                            Text(
                                text = "Your Preferences",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFFFD600),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        if (preferenceSubjects.isEmpty()) {
                            item {
                                Text(
                                    text = "No subjects added to preference yet.",
                                    color = Color.Gray,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            }
                        }

                        itemsIndexed(preferenceSubjects, key = { _, item -> "pref_${item.id}" }) { index, subject ->
                            PreferenceItem(
                                subject = subject,
                                index = index + 1,
                                isFirst = index == 0,
                                isLast = index == preferenceSubjects.size - 1,
                                onUp = { moveUp(index) },
                                onDown = { moveDown(index) },
                                onRemove = { removeSubject(subject.id) }
                            )
                        }

                        // Section 2: Available Subjects
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Available Subjects",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        if (availableSubjects.isEmpty()) {
                            item {
                                Text(
                                    text = "All existing subjects are in your list.",
                                    color = Color.Gray
                                )
                            }
                        }

                        items(availableSubjects, key = { "avail_${it.id}" }) { subject ->
                            AvailableSubjectItem(
                                subject = subject,
                                onAdd = { addSubject(subject.id) }
                            )
                        }

                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun PreferenceItem(
    subject: Subject,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NeutralCardSurface)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
        ) {
            // Rank
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFD600)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = index.toString(),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Text(
                text = subject.name,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp).weight(1f)
            )

            // Actions
            if (!isFirst) {
                IconButton(onClick = onUp, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowUpward, "Move Up", tint = Color.LightGray)
                }
            } else {
                Spacer(modifier = Modifier.size(32.dp))
            }

            if (!isLast) {
                IconButton(onClick = onDown, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowDownward, "Move Down", tint = Color.LightGray)
                }
            } else {
                Spacer(modifier = Modifier.size(32.dp))
            }
            
            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Remove", tint = Color.Red)
            }
        }
    }
}

@Composable
fun AvailableSubjectItem(
    subject: Subject,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.5f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth()
        ) {
            Text(
                text = subject.name,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Add, "Add to Preference", tint = Color(0xFF4CAF50)) // Green
            }
        }
    }
}
