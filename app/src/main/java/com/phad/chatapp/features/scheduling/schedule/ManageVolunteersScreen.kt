package com.phad.chatapp.features.scheduling.schedule

import android.util.Log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.FlowRow

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore

import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
    val classCount: Int = 0, // Number of classes this volunteer can teach
    val subjectPreferences: List<String> = emptyList() // Subject priorities
)

// Sort options for the table
enum class SortField {
    NAME_ASC, NAME_DESC, GROUP_ASC, GROUP_DESC
}

private const val TAG = "ManageVolunteersScreen"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    
    // New Preset Creation State
    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }



    // Function to load preset details
    fun loadPresetDetails() {
        if (presetId == "new_preset") {
            isLoading = false
            presetName = "New Preset"
            return
        }

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

        // Define comparators
        val classCountComparator = compareByDescending<User> { it.classCount }
        
        val specificSortComparator = when (currentSort) {
            SortField.NAME_ASC -> compareBy<User> { it.name }
            SortField.NAME_DESC -> compareByDescending<User> { it.name }
            SortField.GROUP_ASC -> compareBy<User> { it.group }
            SortField.GROUP_DESC -> compareByDescending<User> { it.group }
        }

        // Apply search filter and specific sorting
        if (searchQuery.isNotEmpty()) {
            val query = searchQuery
            
            // 1. Group Search (Numeric)
            if (query.all { it.isDigit() }) {
                val queryInt = query.toIntOrNull()
                if (queryInt != null) {
                    result = result.filter { 
                        it.group == queryInt 
                    }
                }
                // Sort: Class Count -> Selected Sort
                result = result.sortedWith(classCountComparator.then(specificSortComparator))
            }
            // 2. Roll Number Search (Alphanumeric: letters + digits)
            else if (query.any { it.isDigit() } && query.any { it.isLetter() }) {
               result = result.filter {
                   it.rollNumber.contains(query, ignoreCase = true)
               }
               // Sort: Class Count -> Selected Sort
               result = result.sortedWith(classCountComparator.then(specificSortComparator))
            }
            // 3. Subject Search (All Caps Letters)
            else if (query.all { it.isUpperCase() || !it.isLetter() } && query.any { it.isLetter() }) {
                result = result.filter { user ->
                    user.subjectPreferences.any { it.contains(query, ignoreCase = true) }
                }
                // Sort: Subject Match Index (Priority) -> Class Count -> Selected Sort
                result = result.sortedWith(
                    compareBy<User> { user ->
                        val index = user.subjectPreferences.indexOfFirst { 
                            it.contains(query, ignoreCase = true) 
                        }
                        if (index != -1) index else Int.MAX_VALUE
                    }
                    .then(classCountComparator)
                    .then(specificSortComparator)
                )
            }
            // 4. Name Search (Default / Text)
            else {
                result = result.filter { user ->
                    // Match start of any name part (First name, surname, middle name, etc)
                    user.name.split(" ").any { it.startsWith(query, ignoreCase = true) }
                }
                
                // Sort: Name Part Match Index (First Name match > Surname match) -> Class Count -> Selected Sort
                result = result.sortedWith(
                    compareBy<User> { user ->
                        val index = user.name.split(" ").indexOfFirst { 
                            it.startsWith(query, ignoreCase = true) 
                        }
                        if (index != -1) index else Int.MAX_VALUE
                    }
                    .then(classCountComparator)
                    .then(specificSortComparator)
                )
            }
        } else {
            // No search: Standard sort
            result = result.sortedWith(
                classCountComparator.then(specificSortComparator)
            )
        }

        filteredUsers = result
    }

    // Function to load selected volunteers for this preset
    fun loadSelectedVolunteers(studentsList: List<User>): List<User> {
        val usersMap = studentsList.associateBy { it.uid }
        val volunteerCounts = mutableMapOf<String, Int>()

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
                            // Get classCount from map, default to 1 for backward compatibility
                            val count = when (val countValue = volunteerMap["classCount"]) {
                                is Long -> countValue.toInt()
                                is Int -> countValue
                                else -> 1 // Backward compatibility: existing volunteers get count of 1
                            }
                            volunteerCounts[uid] = count
                        }
                    }
                }

                // Set classCount for users based on loaded data
                users = studentsList.map { user ->
                    val count = volunteerCounts[user.uid] ?: 0
                    user.copy(classCount = count)
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

                            // Get subjects (try common field names)
                            val subjects = (document.get("subjectPreferences") as? List<*>)?.mapNotNull { it as? String }
                                ?: (document.get("subjects") as? List<*>)?.mapNotNull { it as? String }
                                ?: emptyList()

                            usersList.add(
                                User(
                                    uid = uid,
                                    name = name,
                                    rollNumber = rollNumber,
                                    email = email,
                                    contactNumber = contactNumber,
                                    userType = "",  // Not in database schema
                                    group = group,
                                    subjectPreferences = subjects
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

        // Try these collections in order - prefer new ttwStudents collection
        val collectionsToTry = listOf("ttwStudents", "generateSchedule", "users")
        tryNextCollection(collectionsToTry, 0)
    }

    // Increment class count for a user
    fun incrementClassCount(user: User) {
        val updatedUsers = users.map {
            if (it.uid == user.uid) {
                it.copy(classCount = it.classCount + 1)
            } else {
                it
            }
        }
        users = updatedUsers
        applyFiltersAndSort()
    }

    // Decrement class count for a user (minimum 0)
    fun decrementClassCount(user: User) {
        val updatedUsers = users.map {
            if (it.uid == user.uid) {
                it.copy(classCount = maxOf(0, it.classCount - 1))
            } else {
                it
            }
        }
        users = updatedUsers
        applyFiltersAndSort()
    }

    // Calculate selection state for increment all functionality
    val selectedCount = filteredUsers.count { it.classCount > 0 }
    val totalCount = filteredUsers.size

    // Handle increment all visible students by 1
    fun handleIncrementAll() {
        val updatedUsers = users.map { user ->
            // Only modify users that are currently visible in filtered list
            if (filteredUsers.any { it.uid == user.uid }) {
                user.copy(classCount = user.classCount + 1)
            } else {
                user
            }
        }
        users = updatedUsers
        applyFiltersAndSort()
    }

    // Function to save the selected volunteers
    fun saveVolunteers() {
        if (presetId == "new_preset") {
            newPresetName = ""
            showSaveDialog = true
            return
        }

        isLoading = true

        // Get volunteers with classCount > 0
        val selectedVolunteers = users.filter { it.classCount > 0 }

        // Create a batch write operation
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()

        try {
            // Create detailed volunteer info list for the preset, including classCount
            val volunteerInfoList = selectedVolunteers.map { user ->
                VolunteerInfo(
                    rollNo = user.uid,
                    name = user.name,
                    group = user.group.toString(),
                    classCount = user.classCount
                )
            }

            // Update the preset document with count and volunteer info
            val presetRef = db.collection("volunteerPresets").document(presetId)

            // Calculate total volunteer count as sum of all class counts
            val totalVolunteerCount = selectedVolunteers.sumOf { it.classCount }

            // Calculate group counts as sum of class counts per group
            val groupCounts = selectedVolunteers
                .groupBy { it.group.toString() }
                .mapValues { entry -> entry.value.sumOf { it.classCount } }

            val presetData = hashMapOf(
                "volunteerCount" to totalVolunteerCount,
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
                    navController.popBackStack() // Close screen after save
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

    // Function to create and save a new preset
    fun createAndSaveNewPreset(name: String) {
        if (name.isBlank()) {
             coroutineScope.launch {
                snackbarHostState.showSnackbar("Preset name cannot be empty")
            }
            return
        }
        
        isLoading = true

        // Get volunteers with classCount > 0
        val selectedVolunteers = users.filter { it.classCount > 0 }

        // Create a batch write operation
        val db = FirebaseFirestore.getInstance()
        
        try {
            // Create detailed volunteer info list
            val volunteerInfoList = selectedVolunteers.map { user ->
                VolunteerInfo(
                    rollNo = user.uid,
                    name = user.name,
                    group = user.group.toString(),
                    classCount = user.classCount
                )
            }

            // Calculate totals
            val totalVolunteerCount = selectedVolunteers.sumOf { it.classCount }
            val groupCounts = selectedVolunteers
                .groupBy { it.group.toString() }
                .mapValues { entry -> entry.value.sumOf { it.classCount } }

            val presetData = hashMapOf(
                "name" to name,
                "createdAt" to System.currentTimeMillis(),
                "volunteerCount" to totalVolunteerCount,
                "groupCounts" to groupCounts,
                "volunteers" to volunteerInfoList
            )

            // Save new document
            db.collection("volunteerPresets").document(name)
                .set(presetData)
                .addOnSuccessListener {
                    isLoading = false
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Preset created successfully")
                    }
                    navController.popBackStack() // Close screen
                }
                .addOnFailureListener { e ->
                    isLoading = false
                    Log.e(TAG, "Error creating preset: ${e.message}", e)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Failed to create preset: ${e.message}")
                    }
                }
        } catch (e: Exception) {
            isLoading = false
            Log.e(TAG, "Error preparing data: ${e.message}", e)
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

    // Function to sync class counts from database (Long press feature)
    fun syncClassesFromDatabase() {
        isLoading = true
        coroutineScope.launch {
            try {
                // Fetch all students from ttwStudents to get latest classesPerWeek
                val snapshot = FirebaseFirestore.getInstance()
                    .collection("ttwStudents")
                    .get()
                    .await()

                val dbCounts = mutableMapOf<String, Int>()
                
                for (doc in snapshot.documents) {
                    val rollNo = doc.id
                    val rawCount = doc.get("classesPerWeek")
                    // Handle String or Number safely
                    val count = when (rawCount) {
                        is String -> rawCount.toIntOrNull() ?: 0
                        is Number -> rawCount.toInt()
                        else -> 0
                    }
                    // Always add to map, even if 0, to support overwriting existing values with 0
                    dbCounts[rollNo] = count
                }

                // Update users list
                val updatedUsers = users.map { user ->
                    // If user exists in DB, use that value.
                    // If user is NOT in DB, we default to 0 to ensure the "sync" reflects the DB's state (which is empty/0 for them).
                    // This matches the "replace existing" requirement.
                    val newCount = dbCounts[user.uid] ?: 0
                    user.copy(classCount = newCount)
                }
                
                users = updatedUsers
                applyFiltersAndSort()
                
                snackbarHostState.showSnackbar("Synced class counts for ${dbCounts.size} students from database")

            } catch (e: Exception) {
                Log.e(TAG, "Error syncing from database: ${e.message}", e)
                snackbarHostState.showSnackbar("Failed to sync: ${e.message}")
            } finally {
                isLoading = false
            }
        }
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
                // Removed horizontal padding here to allow custom padding for header and content
        ) {
            // State for search visibility
            var isSearchActive by remember { mutableStateOf(false) }

            // Top bar with Toggleable Search
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp) // UI.md TopAppBar padding
                    .padding(start = 4.dp, end = 20.dp), // Less padding at start for Back button
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
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
                    // Search Bar Mode
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search volunteers...",
                                color = NeutralGray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .padding(end = 8.dp),
                        shape = RoundedCornerShape(25.dp), // Pill shape
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
                                 Icon(
                                     Icons.Default.Close,
                                     contentDescription = "Close Search",
                                     tint = NeutralGray
                                 )
                             }
                        }
                    )
                } else {
                    // Title Mode
                    Text(
                        text = "Manage Volunteers",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )

                    // Search Icon
                    // Search Icon
                    if (!isLoading) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp, end = 4.dp)
                                .size(40.dp) // Circular button size
                                .clip(CircleShape)
                                .background(YellowAccent)
                                .clickable { isSearchActive = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.Black, // Black icon on yellow background
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Main content area with reduced spacing for tighter layout
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp) // Restore standard padding for content
                    .padding(top = 6.dp) // Requested padding between header and table
            ) {
                // Old Search Bar removed


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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No Volunteers found",
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
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
                                // Responsive increment all header with UI.md specifications
                                Box(
                                    modifier = Modifier
                                        .width(60.dp) // Reduced width to give more space to name
                                        .combinedClickable(
                                            onClick = {
                                                if (filteredUsers.isNotEmpty()) {
                                                    handleIncrementAll()
                                                }
                                            },
                                            onLongClick = {
                                                syncClassesFromDatabase()
                                            }
                                        ),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = "Class",
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
                                    contentAlignment = Alignment.Center // Center-aligned
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
                                    text = "Roll",
                                    modifier = Modifier
                                        .width(100.dp), // Increased width to prevent wrapping
                                    style = MaterialTheme.typography.titleMedium,
                                    color = YellowAccent,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center // Centered
                                )

                                // Group header (sortable) with UI.md specifications - reduced width
                                Box(
                                    modifier = Modifier
                                        .width(45.dp) // Compact display
                                        .padding(end = 2.dp) // Shift right
                                        .clickable {
                                            currentSort = if (currentSort == SortField.GROUP_ASC)
                                                SortField.GROUP_DESC else SortField.GROUP_ASC
                                        },
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End, // Align content to end
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Gp", // Shortened from "Group" to "Gp"
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = YellowAccent,
                                            textAlign = TextAlign.End
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

                            // List with Custom Scrollbar
                            Box(
                                modifier = Modifier
                                    .weight(1f) // Fill remaining space
                                    .fillMaxWidth()
                            ) {
                                val listState = rememberLazyListState()

                                LazyColumn(
                                    state = listState,
                                    verticalArrangement = Arrangement.spacedBy(16.dp), // UI.md card spacing
                                    contentPadding = PaddingValues(bottom = 100.dp) // UI.md bottom padding
                                ) {
                                    itemsIndexed(
                                        items = filteredUsers,
                                        key = { _, user -> user.uid } // Stable key
                                    ) { index, user ->
                                        StudentSelectionRow(
                                            user = user,
                                            classCount = user.classCount,
                                            onIncrement = { incrementClassCount(user) },
                                            onDecrement = { decrementClassCount(user) }
                                        )
                                    }
                                }

                                // Custom Scrollbar Logic
                                val layoutInfo = listState.layoutInfo
                                val totalItems = layoutInfo.totalItemsCount
                                val visibleItemsSize = layoutInfo.visibleItemsInfo.size
                                
                                if (totalItems > 0 && visibleItemsSize < totalItems) {
                                    val viewportHeight = layoutInfo.viewportSize.height.toFloat()
                                    val firstVisibleItemIndex = listState.firstVisibleItemIndex
                                    val firstVisibleItemOffset = listState.firstVisibleItemScrollOffset
                                    
                                    // Estimated Scrollbar Parameters
                                    val estimatedItemHeight = if (visibleItemsSize > 0) 
                                        viewportHeight / visibleItemsSize else 0f
                                        
                                    // Calculate scroll percentage (0f to 1f)
                                    val totalContentHeightEstimate = totalItems * estimatedItemHeight
                                    val currentScrollOffset = (firstVisibleItemIndex * estimatedItemHeight) + firstVisibleItemOffset
                                    
                                    val scrollFraction = if (totalContentHeightEstimate > viewportHeight) 
                                        currentScrollOffset / (totalContentHeightEstimate - viewportHeight) 
                                        else 0f
                                    
                                    // Scrollbar Thumb
                                    val thumbHeight = (viewportHeight * (visibleItemsSize.toFloat() / totalItems.toFloat()))
                                        .coerceAtLeast(viewportHeight * 0.1f) // Minimum size
                                    
                                    val safeScrollFraction = scrollFraction.coerceIn(0f, 1f)
                                    val thumbOffset = safeScrollFraction * (viewportHeight - thumbHeight)

                                    Canvas(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(6.dp)
                                            .fillMaxHeight()
                                            .padding(end = 2.dp) // Padding from edge
                                    ) {
                                        drawRoundRect(
                                            color = YellowAccent.copy(alpha = 0.5f),
                                            topLeft = Offset(0f, thumbOffset),
                                            size = Size(4.dp.toPx(), thumbHeight),
                                            cornerRadius = CornerRadius(2.dp.toPx())
                                        )
                                    }
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

        // Floating Action Button for Save
        if (!isLoading && errorMessage == null) {
            FloatingActionButton(
                onClick = { saveVolunteers() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 48.dp),
                containerColor = Color(0xFF4CAF50), // Green color
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Save, // Save icon (was Check)
                    contentDescription = "Save",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    // Save/Name Dialog for New Preset
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = {
                Text(
                    text = "Name Your Preset",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            containerColor = NeutralCardSurface,
            titleContentColor = Color.White,
            textContentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YellowAccent,
                        unfocusedBorderColor = NeutralGray.copy(alpha = 0.7f),
                        focusedLabelColor = YellowAccent,
                        unfocusedLabelColor = NeutralGray,
                        cursorColor = YellowAccent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                StandardButton(
                    onClick = {
                        createAndSaveNewPreset(newPresetName)
                        showSaveDialog = false
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSaveDialog = false }
                ) {
                    Text("Cancel", color = YellowAccent)
                }
            }
        )
    }
}

// StudentSelectionRow component
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentSelectionRow(
    user: User,
    classCount: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    // Selection state background color - highlight if count > 0
    val backgroundColor = if (classCount > 0) {
        YellowAccent.copy(alpha = 0.1f)
    } else {
        NeutralCardSurface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // Determine which half was clicked
                    val clickX = offset.x
                    val width = size.width
                    if (clickX < width / 2) {
                        // Left half - decrement
                        if (classCount > 0) {
                            onDecrement()
                        }
                    } else {
                        // Right half - increment
                        onIncrement()
                    }
                }
            },
        shape = RoundedCornerShape(12.dp), // UI.md corner radius
        color = backgroundColor,
        shadowElevation = 0.dp, // No shadow/elevation
        border = null // Remove any border styling
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp) // UI.md padding
        ) {
            // Top Row: Class, Name, Roll, Group
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Class count display
                Box(
                    modifier = Modifier
                        .width(60.dp) // Match header width
                        .padding(start = 8.dp), // Move text left
                    contentAlignment = Alignment.CenterStart // Align left
                ) {
                    Text(
                        text = classCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (classCount > 0) YellowAccent else Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start // Align left
                    )
                }

                // Student Name with UI.md typography
                Text(
                    text = user.name,
                    modifier = Modifier.weight(2f), // UI.md flexible weight
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center // Center-aligned to match header
                )

                // Roll Number with UI.md specifications
                Text(
                    text = user.rollNumber,
                    modifier = Modifier
                        .width(100.dp), // Increased width to prevent wrapping
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0B0B0), // UI.md secondary text color
                    textAlign = TextAlign.Center // Centered
                )

                // Group with UI.md specifications - reduced width to match header
                Text(
                    text = user.group.toString(),
                    modifier = Modifier
                        .width(45.dp) // Reduced to match header
                        .padding(end = 8.dp), // Shift right to match header
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0B0B0), // UI.md secondary text color
                    textAlign = TextAlign.End // Align right
                )
            }

            // Bottom Row: Subject Preferences
            if (user.subjectPreferences.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(4.dp), 
                    maxItemsInEachRow = Int.MAX_VALUE
                ) {
                    user.subjectPreferences.forEach { subject ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .background(
                                    color = Color.White.copy(alpha = 0.1f), 
                                    shape = RoundedCornerShape(50) // Oval shape
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = subject.take(3),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
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