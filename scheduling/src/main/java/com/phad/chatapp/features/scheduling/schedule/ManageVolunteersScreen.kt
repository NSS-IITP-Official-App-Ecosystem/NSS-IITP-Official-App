package com.phad.chatapp.features.scheduling.schedule

import android.util.Log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.itemsIndexed

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore

import kotlinx.coroutines.launch

import com.phad.chatapp.features.scheduling.firebase.FirebaseManager
import com.phad.chatapp.features.scheduling.models.VolunteerInfo
import com.phad.chatapp.features.scheduling.ui.theme.*
import com.phad.chatapp.features.scheduling.ui.components.StandardButton




// User data model based on Firebase structure
data class User(
    val uid: String = "",
    val name: String = "",
    val rollNumber: String = "",
    val email: String = "",
    val contactNumber: String = "",
    val userType: String = "",
    val group: Int = 0, // Will store the academic group number
    val isVolunteer: Boolean = false // Flag to indicate if user is selected as volunteer
)

// Sort options for the table
enum class SortField {
    NAME_ASC, NAME_DESC, GROUP_ASC, GROUP_DESC
}

private const val TAG = "ManageVolunteersScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageVolunteersScreen(
    navController: NavController,
    presetId: String? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()



    // Show initial logging to diagnose issues
    LaunchedEffect(Unit) {
        Log.d(TAG, "ManageVolunteersScreen being composed with presetId: $presetId")
    }

    // If no preset ID is provided, redirect to preset list
    if (presetId == null) {
        LaunchedEffect(Unit) {
            navController.navigate("volunteerPresets") {
                popUpTo("manageVolunteers") { inclusive = true }
            }
        }
        return
    }

    // State
    var presetName by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var filteredUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentSort by remember { mutableStateOf(SortField.NAME_ASC) }
    var searchQuery by remember { mutableStateOf("") }



    // Function to load preset details
    fun loadPresetDetails() {
        FirebaseManager.getInstance().getDocument(
            "volunteerPresets/$presetId",
            onSuccess = { document ->
                if (document.exists()) {
                    presetName = document.getString("name") ?: "Unnamed Preset"
                    Log.d(TAG, "Loaded preset: $presetName")
                } else {
                    Log.e(TAG, "Preset document does not exist")
                    errorMessage = "Preset not found"
                }
            },
            onFailure = { e ->
                Log.e(TAG, "Error loading preset: ${e.message}", e)
                errorMessage = "Error loading preset: ${e.message}"
            }
        )
    }

    // Function to apply filters and sorting
    fun applyFiltersAndSort() {
        var result = users

        // Apply search filter
        if (searchQuery.isNotEmpty()) {
            result = result.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.rollNumber.contains(searchQuery, ignoreCase = true) ||
                it.group.toString().contains(searchQuery, ignoreCase = true)
            }
        }

        // Apply sorting - maintain selected volunteers at top within the sort order
        result = when (currentSort) {
            SortField.NAME_ASC -> result.sortedWith(
                compareByDescending<User> { it.isVolunteer }
                    .thenBy { it.name }
            )
            SortField.NAME_DESC -> result.sortedWith(
                compareByDescending<User> { it.isVolunteer }
                    .thenByDescending { it.name }
            )
            SortField.GROUP_ASC -> result.sortedWith(
                compareByDescending<User> { it.isVolunteer }
                    .thenBy { it.group }
            )
            SortField.GROUP_DESC -> result.sortedWith(
                compareByDescending<User> { it.isVolunteer }
                    .thenByDescending { it.group }
            )
        }



        filteredUsers = result
    }

    // Function to load selected volunteers for this preset
    fun loadSelectedVolunteers(studentsList: List<User>): List<User> {
        val usersMap = studentsList.associateBy { it.uid }
        val selectedIds = mutableSetOf<String>()

        // Get the selected volunteers from the main preset document
        FirebaseManager.getInstance().getDocument(
            "volunteerPresets/$presetId",
            onSuccess = { document ->
                if (document.exists()) {
                    // Get volunteers array from the preset document
                    val volunteersData = document.get("volunteers") as? List<Map<String, Any>>
                    volunteersData?.forEach { volunteerMap ->
                        val uid = volunteerMap["rollNo"] as? String
                        if (uid != null) {
                            selectedIds.add(uid)
                        }
                    }
                }

                // Set isVolunteer flag for selected users
                users = studentsList.map { user ->
                    if (selectedIds.contains(user.uid)) {
                        user.copy(isVolunteer = true)
                    } else {
                        user
                    }
                }

                // Apply initial sorting and filtering
                applyFiltersAndSort()
            },
            onFailure = { e ->
                Log.e(TAG, "Error loading volunteers: ${e.message}", e)
                // Still return all users, just not marked as volunteers
                users = studentsList
                applyFiltersAndSort()
            }
        )

        // Return initial state with no volunteers selected
        return studentsList
    }

    // Helper function to try loading from different collections
    fun tryNextCollection(collections: List<String>, index: Int) {
        if (index >= collections.size) {
            Log.e(TAG, "Tried all collections, none worked")
            isLoading = false
            errorMessage = "Could not find student data in any expected collection. Try checking collections."
            return
        }

        val collectionName = collections[index]
        Log.d(TAG, "Trying to load users from collection: $collectionName")

        try {
            FirebaseManager.getInstance().getCollection(
                collectionName,
                onSuccess = { snapshot ->
                    Log.d(TAG, "Query for '$collectionName' collection completed. Document count: ${snapshot.size()}")

                    if (snapshot.isEmpty) {
                        Log.e(TAG, "The '$collectionName' collection is empty, trying next collection")
                        // Try the next collection
                        tryNextCollection(collections, index + 1)
                        return@getCollection
                    }

                    try {
                        val usersList = mutableListOf<User>()

                        // Track field names to handle different schemas
                        val fieldMappings = mutableMapOf<String, String>()

                        // Try to detect field names from the first document
                        if (snapshot.documents.isNotEmpty()) {
                            val doc = snapshot.documents[0]
                            val fields = doc.data?.keys ?: emptyList()
                            Log.d(TAG, "Fields in first document: $fields")

                            // Map fields with fuzzy matching
                            for (field in fields) {
                                val lowerField = field.toLowerCase()
                                when {
                                    lowerField.contains("name") -> fieldMappings["name"] = field
                                    lowerField.contains("roll") -> fieldMappings["rollNumber"] = field
                                    lowerField.contains("email") || lowerField.contains("gmail") -> fieldMappings["email"] = field
                                    lowerField.contains("contact") || lowerField.contains("phone") || lowerField.contains("mobile") -> fieldMappings["contactNumber"] = field
                                    lowerField.contains("group") || lowerField.contains("academic") -> fieldMappings["group"] = field
                                }
                            }

                            Log.d(TAG, "Detected field mappings: $fieldMappings")
                        }

                        for (document in snapshot.documents) {
                            val uid = document.id
                            Log.d(TAG, "Processing student document: $uid with data: ${document.data}")

                            // Use field mappings if available, or try common field names
                            val name = document.getString(fieldMappings["name"] ?: "Name")
                                ?: document.getString("name")
                                ?: "Unknown"

                            val rollNumber = document.getString(fieldMappings["rollNumber"] ?: "Roll_No_")
                                ?: document.getString("rollNumber")
                                ?: document.getString("roll_number")
                                ?: ""

                            val email = document.getString(fieldMappings["email"] ?: "Email")
                                ?: document.getString("email")
                                ?: document.getString("Gmail_ID")
                                ?: ""

                            val contactNumber = document.getString(fieldMappings["contactNumber"] ?: "Contact_Number")
                                ?: document.getString("contactNumber")
                                ?: document.getString("Mobile_no_")
                                ?: ""

                            val groupField = fieldMappings["group"] ?: "Academic_Grp_"
                            val groupStr = document.getString(groupField)
                                ?: document.getString("group")
                                ?: document.getString("academicGroup")
                                ?: "0"

                            val group = groupStr.toIntOrNull() ?: 0

                            usersList.add(
                                User(
                                    uid = uid,
                                    name = name,
                                    rollNumber = rollNumber,
                                    email = email,
                                    contactNumber = contactNumber,
                                    userType = "",  // Not in database schema
                                    group = group
                                )
                            )
                        }

                        if (usersList.isEmpty()) {
                            Log.e(TAG, "No users could be extracted from collection '$collectionName', trying next")
                            tryNextCollection(collections, index + 1)
                            return@getCollection
                        }

                        // Load selected volunteers for this preset
                        users = loadSelectedVolunteers(usersList)

                        isLoading = false

                        Log.d(TAG, "Successfully loaded ${usersList.size} students from collection '$collectionName'")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing students from '$collectionName': ${e.message}", e)
                        // Try the next collection
                        tryNextCollection(collections, index + 1)
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Firestore error when fetching '$collectionName' collection: ${e.message}", e)
                    // Try the next collection
                    tryNextCollection(collections, index + 1)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Firestore for collection '$collectionName': ${e.message}", e)
            // Try the next collection
            tryNextCollection(collections, index + 1)
        }
    }

    // Function to load users from Firebase
    fun loadUsers() {
        isLoading = true
        errorMessage = null

        Log.d(TAG, "Starting to load users from Firestore")

        // Try these collections in order - prioritize generateSchedule collection
        val collectionsToTry = listOf("generateSchedule", "students", "Student", "Users", "users")
        tryNextCollection(collectionsToTry, 0)
    }

    // Update user volunteer status
    fun toggleVolunteerStatus(user: User) {
        val updatedUsers = users.map {
            if (it.uid == user.uid) {
                it.copy(isVolunteer = !it.isVolunteer)
            } else {
                it
            }
        }
        users = updatedUsers


        applyFiltersAndSort()
    }

    // Calculate selection state for Select All/Deselect All button
    val selectedCount = filteredUsers.count { it.isVolunteer }
    val totalCount = filteredUsers.size
    val allSelected = selectedCount == totalCount && totalCount > 0
    val noneSelected = selectedCount == 0

    // Determine button text based on selection state
    val selectAllButtonText = if (allSelected) "Deselect All" else "Select All"

    // Handle Select All/Deselect All action
    fun handleSelectAllToggle() {
        val shouldSelectAll = !allSelected

        val updatedUsers = users.map { user ->
            // Only modify users that are currently visible in filtered list
            if (filteredUsers.any { it.uid == user.uid }) {
                user.copy(isVolunteer = shouldSelectAll)
            } else {
                user
            }
        }
        users = updatedUsers


        applyFiltersAndSort()
    }

    // Function to save the selected volunteers
    fun saveVolunteers() {
        isLoading = true

        // Get the selected volunteers
        val selectedVolunteers = users.filter { it.isVolunteer }

        // Create a batch write operation
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()

        try {
            // Create detailed volunteer info list for the preset
            val volunteerInfoList = selectedVolunteers.map { user ->
                VolunteerInfo(
                    rollNo = user.uid,
                    name = user.name,
                    group = user.group.toString()
                )
            }

            // Update the preset document with count and volunteer info
            val presetRef = db.collection("volunteerPresets").document(presetId)

            // Calculate group counts
            val groupCounts = selectedVolunteers
                .groupBy { it.group.toString() }
                .mapValues { it.value.size }

            val presetData = hashMapOf(
                "volunteerCount" to selectedVolunteers.size,
                "groupCounts" to groupCounts,
                "volunteers" to volunteerInfoList
            )

            // Update the preset document directly
            presetRef.update(presetData)
                .addOnSuccessListener {
                    isLoading = false
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Volunteers saved successfully")
                    }
                }
                .addOnFailureListener { e ->
                    isLoading = false
                    Log.e(TAG, "Error updating preset: ${e.message}", e)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Failed to save volunteers: ${e.message}")
                    }
                }
        } catch (e: Exception) {
            isLoading = false
            Log.e(TAG, "Error in volunteer save operation: ${e.message}", e)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Error saving volunteers: ${e.message}")
            }
        }
    }



    // Load users and preset details when screen is first displayed
    LaunchedEffect(presetId) {
        Log.d(TAG, "Initializing data load for preset: $presetId")
        loadPresetDetails()
        loadUsers()
    }

    // Effect to apply filters and sort when they change
    LaunchedEffect(searchQuery, currentSort) {
        applyFiltersAndSort()
    }

    // Add this function to check available collections in Firestore
    fun checkAvailableCollections() {
        isLoading = true
        errorMessage = null

        Log.d(TAG, "Checking available Firestore collections")

        try {
            // Get Firestore instance
            val db = FirebaseFirestore.getInstance()

            // Since there's no direct API to list collections, we'll try different possible collection names
            val possibleCollections = listOf("students", "student", "Student", "Students", "users", "Users")

            var collectionsChecked = 0
            val foundCollections = mutableListOf<String>()

            for (collectionName in possibleCollections) {
                db.collection(collectionName).limit(1).get()
                    .addOnSuccessListener { snapshot ->
                        collectionsChecked++

                        if (!snapshot.isEmpty) {
                            foundCollections.add("$collectionName (${snapshot.size()} documents)")
                            Log.d(TAG, "Found collection: $collectionName with ${snapshot.size()} documents")

                            // If at least one document exists, log its field names to check structure
                            if (snapshot.documents.isNotEmpty()) {
                                val doc = snapshot.documents[0]
                                Log.d(TAG, "Sample document fields from $collectionName: ${doc.data?.keys}")
                            }
                        }

                        // If we've checked all collections, update UI
                        if (collectionsChecked == possibleCollections.size) {
                            isLoading = false
                            if (foundCollections.isEmpty()) {
                                errorMessage = "No student collections found. Available collections to check: ${possibleCollections.joinToString()}"
                            } else {
                                errorMessage = "Found collections: ${foundCollections.joinToString()}. Please update the code to use the correct collection name."
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        collectionsChecked++
                        Log.e(TAG, "Error checking collection $collectionName: ${e.message}")

                        // If we've checked all collections, update UI
                        if (collectionsChecked == possibleCollections.size) {
                            isLoading = false
                            if (foundCollections.isEmpty()) {
                                errorMessage = "Couldn't find any student collections. Please check Firestore database structure."
                            } else {
                                errorMessage = "Found collections: ${foundCollections.joinToString()}. Please update the code to use the correct collection."
                            }
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Firestore for collection check: ${e.message}", e)
            errorMessage = "Error checking collections: ${e.message}"
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp) // UI.md standard horizontal margins
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp), // UI.md TopAppBar padding: 8dp top and bottom
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button with UI.md specifications
                IconButton(
                    onClick = { navController.navigateUp() },
                    modifier = Modifier
                        .padding(8.dp)
                        .size(48.dp) // UI.md accessibility standard
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp) // UI.md icon size
                    )
                }

                // Title with UI.md typography
                Text(
                    text = if (presetName.isNotEmpty()) "Choose Volunteers" else "Manage Volunteers",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )

                // Save button using UI.md specifications
                if (!isLoading && errorMessage == null) {
                    StandardButton(
                        onClick = { saveVolunteers() }
                    ) {
                        Text(
                            text = "Save",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Main content area with reduced spacing for tighter layout
            Column(
                modifier = Modifier.padding(top = 12.dp) // Reduced from 24dp to 12dp for tighter spacing
            ) {
                // Search bar with UI.md specifications
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search by name, roll number or group",
                            color = NeutralGray
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = YellowAccent
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp) // Reduced from 16dp to 8dp for tighter spacing
                        .height(56.dp), // UI.md accessibility height
                    shape = RoundedCornerShape(16.dp), // UI.md corner radius
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NeutralCardSurface,
                        unfocusedContainerColor = NeutralCardSurface,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = YellowAccent,
                        unfocusedBorderColor = NeutralGray.copy(alpha = 0.7f)
                    )
                )

                // Loading or error states with UI.md specifications
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = YellowAccent,
                                    strokeWidth = 4.dp,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Loading students...",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }

                    errorMessage != null -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = errorMessage ?: "Unknown error",
                                    color = ErrorRed,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                Button(
                                    onClick = { loadUsers() },
                                    modifier = Modifier
                                        .heightIn(min = 44.dp)
                                        .widthIn(min = 80.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = YellowAccent,
                                        contentColor = Color.Black
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 3.dp,
                                        pressedElevation = 2.dp
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Text("Retry", fontWeight = FontWeight.Medium)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = { checkAvailableCollections() },
                                    modifier = Modifier
                                        .heightIn(min = 44.dp)
                                        .widthIn(min = 80.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = YellowAccent,
                                        contentColor = Color.Black
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 3.dp,
                                        pressedElevation = 2.dp
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Text("Check Database Collections", fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    filteredUsers.isEmpty() && searchQuery.isEmpty() -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No users found",
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }

                    filteredUsers.isEmpty() && searchQuery.isNotEmpty() -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No users matching \"$searchQuery\"",
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }

                    else -> {
                        // Table header with UI.md specifications
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp) // UI.md minimum height
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Responsive Select All/Deselect All header with UI.md specifications
                                Box(
                                    modifier = Modifier
                                        .width(60.dp) // UI.md column width
                                        .clickable {
                                            if (filteredUsers.isNotEmpty()) {
                                                handleSelectAllToggle()
                                            }
                                        },
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = "Select",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = YellowAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Name header (sortable) with UI.md specifications
                                Box(
                                    modifier = Modifier
                                        .weight(2f) // UI.md flexible weight
                                        .clickable {
                                            currentSort = if (currentSort == SortField.NAME_ASC)
                                                SortField.NAME_DESC else SortField.NAME_ASC
                                        },
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Name",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = YellowAccent
                                        )

                                        if (currentSort == SortField.NAME_ASC || currentSort == SortField.NAME_DESC) {
                                            Icon(
                                                imageVector = if (currentSort == SortField.NAME_ASC)
                                                    Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                contentDescription = "Sort direction",
                                                modifier = Modifier.size(16.dp),
                                                tint = YellowAccent
                                            )
                                        }
                                    }
                                }

                                // Roll Number header with UI.md specifications
                                Text(
                                    text = "Roll No.",
                                    modifier = Modifier.width(120.dp), // UI.md fixed width
                                    style = MaterialTheme.typography.titleMedium,
                                    color = YellowAccent,
                                    fontWeight = FontWeight.Bold
                                )

                                // Group header (sortable) with UI.md specifications - reduced width
                                Box(
                                    modifier = Modifier
                                        .width(50.dp) // Reduced from 80dp to 50dp for compact display
                                        .clickable {
                                            currentSort = if (currentSort == SortField.GROUP_ASC)
                                                SortField.GROUP_DESC else SortField.GROUP_ASC
                                        },
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Gp", // Shortened from "Group" to "Gp"
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = YellowAccent
                                        )

                                        if (currentSort == SortField.GROUP_ASC || currentSort == SortField.GROUP_DESC) {
                                            Icon(
                                                imageVector = if (currentSort == SortField.GROUP_ASC)
                                                    Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                contentDescription = "Sort direction",
                                                modifier = Modifier.size(16.dp),
                                                tint = YellowAccent
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Student List
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(16.dp), // UI.md card spacing
                                contentPadding = PaddingValues(bottom = 100.dp) // UI.md bottom padding to prevent navigation bar overlap
                            ) {
                                itemsIndexed(filteredUsers) { index, user ->
                                    StudentSelectionRow(
                                        user = user,
                                        isSelected = user.isVolunteer,
                                        onToggle = { toggleVolunteerStatus(user) }
                                    )
                                }
                            }
                    }
                }
                } // Close main content Column with UI.md content spacing
            }

            // Snackbar host
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
    }
}

// StudentSelectionRow component
@Composable
fun StudentSelectionRow(
    user: User,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    // Selection state background color
    val backgroundColor = if (isSelected) {
        YellowAccent.copy(alpha = 0.1f)
    } else {
        NeutralCardSurface
    }

    Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() },
            shape = RoundedCornerShape(12.dp), // UI.md corner radius
            color = backgroundColor,
            shadowElevation = 0.dp, // No shadow/elevation
            border = null // Remove any border styling
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp) // UI.md accessibility height
                    .padding(horizontal = 16.dp, vertical = 12.dp), // UI.md padding
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox with proper touch target (UI.md specifications)
                Box(
                    modifier = Modifier
                        .size(48.dp) // UI.md accessibility touch target
                        .width(60.dp), // UI.md column width
                    contentAlignment = Alignment.Center
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggle() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = YellowAccent,
                            uncheckedColor = NeutralGray,
                            checkmarkColor = Color.Black // UI.md contrast
                        )
                    )
                }

                // Student Name with UI.md typography
                Text(
                    text = user.name,
                    modifier = Modifier.weight(2f), // UI.md flexible weight
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )

                // Roll Number with UI.md specifications
                Text(
                    text = user.rollNumber,
                    modifier = Modifier.width(120.dp), // UI.md fixed width
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0B0B0) // UI.md secondary text color
                )

                // Group with UI.md specifications - reduced width to match header
                Text(
                    text = user.group.toString(),
                    modifier = Modifier.width(50.dp), // Reduced from 80dp to 50dp to match header
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0B0B0) // UI.md secondary text color
                )
            }
        }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
fun ManageVolunteersScreenPreview() {
    MaterialTheme {
        val navController = androidx.navigation.compose.rememberNavController()
        ManageVolunteersScreen(navController = navController, presetId = "previewPresetId")
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
fun VolunteerRowPreview() {
    MaterialTheme {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Box(
                modifier = Modifier.width(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Checkbox(
                    checked = true,
                    onCheckedChange = { },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        checkmarkColor = Color.White
                    )
                )
            }

            // Name
            Text(
                text = "John Doe",
                modifier = Modifier.weight(1f),
                color = Color.White
            )

            // Roll Number
            Text(
                text = "CS21B001",
                modifier = Modifier.width(100.dp),
                color = Color.White
            )

            // Group
            Text(
                text = "5",
                modifier = Modifier.width(50.dp), // Updated to match new layout
                color = Color.White
            )
        }
    }
}