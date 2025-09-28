package com.phad.chatapp.features.scheduling.schedule

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.features.scheduling.firebase.FirestoreCollection
import com.phad.chatapp.features.scheduling.firebase.FirestoreHelper
import com.phad.chatapp.features.scheduling.models.AssignmentEntry
import com.phad.chatapp.features.scheduling.models.RawSubjectAssignment
import com.phad.chatapp.features.scheduling.models.SubjectAssignmentDetails
import com.phad.chatapp.features.scheduling.models.SubjectConstants
import com.phad.chatapp.features.scheduling.ui.theme.DarkBackground
import com.phad.chatapp.features.scheduling.ui.theme.NeutralCardSurface
import com.phad.chatapp.features.scheduling.ui.theme.YellowAccent
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.auth.ktx.auth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
// PDF generation imports
import android.graphics.pdf.PdfDocument
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewAssignmentsScreen(navController: NavController) {
    var isLoading by remember { mutableStateOf(true) }
    var assignments by remember { mutableStateOf<List<SubjectAssignmentDetails>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var selectedFilters by remember { mutableStateOf(emptySet<String>()) }
    var selectedSchool by remember { mutableStateOf<String?>("AM") }
    var selectedSections by remember { mutableStateOf(setOf<String>()) }
    var showSchoolDropdown by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // Highlighted cells for search
    var highlightedCells by remember { mutableStateOf<Set<Triple<String, String, String>>>(emptySet()) }
    
    // Pre-calculate sections based on school to avoid delay
    val classSections = remember(assignments, selectedSchool) {
        assignments
            .filter { selectedSchool == null || it.schoolName == selectedSchool }
            .map { it.classAndSection }
            .distinct()
            .sorted()
    }
    
    // Get all schools for school selector - moved up for earlier calculation
    val schools = remember(assignments) {
        assignments.map { it.schoolName }.distinct().sorted()
    }
    
    // Load assignments from Firestore
    LaunchedEffect(Unit) {
        try {
            isLoading = true
            val loadedAssignments = loadAssignments()
            assignments = loadedAssignments
            isLoading = false
            
            if (loadedAssignments.isEmpty()) {
                snackbarHostState.showSnackbar("No assignments found")
            } else {
                snackbarHostState.showSnackbar("Loaded ${loadedAssignments.size} assignments")
                // Set default selected school to first school in list
                val schoolsList = loadedAssignments.map { it.schoolName }.distinct()
                if (schoolsList.isNotEmpty()) {
                    selectedSchool = schoolsList.first()
                    
                    // Select all sections for the selected school
                    selectedSections = loadedAssignments
                        .filter { it.schoolName == selectedSchool }
                        .map { it.classAndSection }
                        .toSet()
                }
            }
        } catch (e: Exception) {
            Log.e("ViewAssignmentsScreen", "Error loading assignments", e)
            snackbarHostState.showSnackbar("Error loading assignments: ${e.message}")
            isLoading = false
        }
    }
    
    // Filter assignments based on search query, filters, selected school and sections
    val filteredAssignments = remember(assignments, selectedFilters, selectedSchool, selectedSections, searchQuery) {
        assignments.filter { assignment ->
            val matchesFilters = selectedFilters.isEmpty() || 
                selectedFilters.contains(assignment.subjectCode) ||
                selectedFilters.contains(assignment.schoolName)
            
            val matchesSchool = selectedSchool == null || assignment.schoolName == selectedSchool
            
            // Only show sections that are selected
            val matchesSections = selectedSections.contains(assignment.classAndSection)
            
            matchesFilters && matchesSchool && matchesSections
        }
    }
    
    // Create display names for sections based on school-section combinations
    val sectionDisplayNames = remember(assignments, selectedSchool) {
        if (selectedSchool == null) {
            assignments
                .groupBy { "${it.schoolName} ${it.classAndSection}" }
                .keys
                .sorted()
                .toList()
        } else {
            assignments
                .filter { it.schoolName == selectedSchool }
                .map { "${it.schoolName} ${it.classAndSection}" }
                .distinct()
                .sorted()
                .toList()
        }
    }
    
    // When school changes, select all sections for that school
    LaunchedEffect(selectedSchool, assignments) {
        if (selectedSchool != null && assignments.isNotEmpty()) {
            // Select all sections for the new school
            selectedSections = assignments
                .filter { it.schoolName == selectedSchool }
                .map { it.classAndSection }
                .toSet()
        }
    }
    
    // Compute highlighted cells for search
    LaunchedEffect(searchQuery, assignments) {
        if (searchActive && searchQuery.isNotBlank()) {
            val lowerQuery = searchQuery.trim().lowercase()
            val cells = assignments.filter { assignment ->
                val nameParts = assignment.volunteerName.lowercase().split(" ")
                val nameMatches = (nameParts.firstOrNull()?.startsWith(lowerQuery) == true) ||
                        (nameParts.size > 1 && nameParts.last().startsWith(lowerQuery))

                val subjectMatches = assignment.subjectName.lowercase().startsWith(lowerQuery) ||
                        assignment.subjectCode.lowercase().startsWith(lowerQuery)

                val rollNoMatches = assignment.volunteerRollNo.lowercase().contains(lowerQuery)

                nameMatches || subjectMatches || rollNoMatches
            }.map { Triple("${it.schoolName} - ${it.classAndSection}", it.dayName, it.slotName) }.toSet()
            highlightedCells = cells
        } else {
            highlightedCells = emptySet()
        }
    }
    
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    if (searchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search assignments...", color = Color.Gray) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search",
                                    tint = YellowAccent
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = {
                                    searchActive = false
                                    searchQuery = ""
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Close Search",
                                        tint = YellowAccent
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .padding(start = 4.dp, end = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Black,
                                unfocusedContainerColor = Color.Black,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = YellowAccent,
                                unfocusedBorderColor = YellowAccent
                            )
                        )
                    } else {
                        Text(
                            text = "View Assignments",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .weight(1f)
                        )
                        if (!isLoading) {
                            // Replace IconButton with a Button that looks like the save button
                            Button(
                                onClick = { searchActive = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = YellowAccent,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = "Search",
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
                
                // Row for section selectors - only show when not loading
                if (!isLoading) {
                    // Replace the separate school dropdown and section row with a single row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // School dropdown on the left - styled to match screenshot
                        if (schools.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .clickable { showSchoolDropdown = true }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "School: ${selectedSchool ?: "All"}",
                                    color = YellowAccent,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select School",
                                    tint = YellowAccent
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showSchoolDropdown,
                                onDismissRequest = { showSchoolDropdown = false },
                                modifier = Modifier
                                    .background(Color(0xFF2A2A2A))
                            ) {
                                schools.forEach { school ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                text = school,
                                                color = if (school == selectedSchool) YellowAccent else Color.White,
                                                fontWeight = if (school == selectedSchool) FontWeight.Bold else FontWeight.Normal
                                            ) 
                                        },
                                        onClick = {
                                            selectedSchool = school
                                            showSchoolDropdown = false
                                        },
                                        leadingIcon = {
                                            if (school == selectedSchool) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = YellowAccent
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        
                        // Section buttons in a horizontally scrollable row
                        if (selectedSchool != null && classSections.isNotEmpty()) {
                            // Sort sections alphanumerically
                            val sortedSections = classSections.sortedWith(compareBy<String> { section ->
                                // Extract numeric part for proper numeric sorting
                                val numericPrefix = section.takeWhile { char -> char.isDigit() }
                                numericPrefix.toIntOrNull() ?: 0
                            }.thenBy { section ->
                                // Extract alphabetic suffix
                                section.dropWhile { char -> char.isDigit() }
                            })
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 8.dp),
                                    horizontalArrangement = Arrangement.End,
                                    reverseLayout = true,
                                    contentPadding = PaddingValues(end = 8.dp)
                                ) {
                                    items(sortedSections) { section ->
                                        val isSelected = selectedSections.contains(section)
                                        
                                        Button(
                                            onClick = {
                                                // Toggle section selection
                                                selectedSections = if (isSelected) {
                                                    selectedSections - section
                                                } else {
                                                    selectedSections + section
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) YellowAccent else Color(0xFF2A2A2A),
                                                contentColor = if (isSelected) Color.Black else Color.White
                                            ),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .height(32.dp)
                                                .padding(horizontal = 4.dp)
                                        ) {
                                            Text(
                                                text = section,
                                                fontWeight = FontWeight.Medium,
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
        },
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = YellowAccent
                )
            } else if (filteredAssignments.isEmpty()) {
                Text(
                    text = if (searchQuery.isNotEmpty() || selectedFilters.isNotEmpty()) 
                        "No assignments match your search" 
                    else 
                        "No assignments found",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
                ) {
                    // Group assignments by section
                    val groupedAssignments = filteredAssignments
                        .groupBy { "${it.schoolName} - ${it.classAndSection}" }
                        
                    // Create a custom comparator for alphanumeric sorting
                    val alphanumericComparator = Comparator<String> { a, b ->
                        // Split into parts (e.g., "AM - 10N" -> ["AM", "10N"])
                        val aParts = a.split(" - ", limit = 2)
                        val bParts = b.split(" - ", limit = 2)
                        
                        // Compare school names first
                        val schoolA = aParts[0]
                        val schoolB = bParts[0]
                        val schoolCompare = schoolA.compareTo(schoolB)
                        
                        if (schoolCompare != 0) return@Comparator schoolCompare
                        
                        // Then compare section parts
                        val sectionA = if (aParts.size > 1) aParts[1] else ""
                        val sectionB = if (bParts.size > 1) bParts[1] else ""
                        
                        // Extract numeric prefix from section
                        val numA = sectionA.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                        val numB = sectionB.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                        
                        val numCompare = numA.compareTo(numB)
                        if (numCompare != 0) return@Comparator numCompare
                        
                        // Finally compare alphabetic suffix
                        val suffixA = sectionA.dropWhile { it.isDigit() }
                        val suffixB = sectionB.dropWhile { it.isDigit() }
                        suffixA.compareTo(suffixB)
                    }
                    
                    // Sort the entries using the custom comparator
                    val sortedEntries = groupedAssignments.entries
                        .sortedWith(compareBy<Map.Entry<String, List<SubjectAssignmentDetails>>, String>(alphanumericComparator) { it.key })
                    
                    items(sortedEntries) { (displayName, sectionAssignments) ->
                        Text(
                            text = displayName,
                            color = YellowAccent,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        val sectionDayNames = sectionAssignments
                            .map { it.dayName }
                            .distinct()
                            .sorted()
                        
                        val sectionSlotNames = sectionAssignments
                            .map { it.slotName }
                            .distinct()
                            .sorted()
                        
                        AssignmentGrid(
                            assignments = sectionAssignments,
                            dayNames = sectionDayNames,
                            slotNames = sectionSlotNames,
                            modifier = Modifier.fillMaxWidth(),
                            highlightedCells = highlightedCells,
                            searchActive = searchActive,
                            displayName = displayName
                        )
                    }
                }
            }
            
            // Export to PDF button
            if (!isLoading && filteredAssignments.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            if (!isExporting) {
                                isExporting = true
                                coroutineScope.launch {
                                    try {
                                        val result = generateAndSharePDF(context, filteredAssignments, selectedSchool)
                                        isExporting = false
                                        snackbarHostState.showSnackbar(result)
                                    } catch (e: Exception) {
                                        isExporting = false
                                        snackbarHostState.showSnackbar("Export failed: ${e.message}")
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowAccent,
                            contentColor = Color.Black
                        ),
                        enabled = !isExporting,
                        modifier = Modifier.padding(vertical = 16.dp)
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isExporting) "Exporting..." else "Export to PDF",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
    
    // Filter dialog (kept for reference but won't show as showFilterDialog is never set to true)
    if (showFilterDialog) {
        FilterDialog(
            currentFilters = selectedFilters,
            onDismiss = { showFilterDialog = false },
            onApplyFilters = { filters ->
                selectedFilters = filters
                showFilterDialog = false
            },
            assignments = assignments
        )
    }
}

@Composable
fun AssignmentGrid(
    assignments: List<SubjectAssignmentDetails>,
    dayNames: List<String>,
    slotNames: List<String>,
    modifier: Modifier = Modifier,
    highlightedCells: Set<Triple<String, String, String>> = emptySet(),
    searchActive: Boolean = false,
    displayName: String
) {
    // Fixed day order
    val dayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val sortedDayNames = dayOrder.filter { dayNames.contains(it) }

    // Shared scroll state for synchronized scrolling
    val horizontalScrollState = rememberScrollState()

    // Table border and shadow
    Surface(
        modifier = modifier
            .padding(vertical = 8.dp)
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp)),
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF181818), RoundedCornerShape(12.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header row with time slots
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .background(Color(0xFF232323), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Empty cell for day column - fixed width
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Day/Time",
                        color = YellowAccent,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                }
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .horizontalScroll(horizontalScrollState),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    slotNames.forEach { slotName ->
                        Box(
                            modifier = Modifier
                                .width(90.dp)
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = slotName,
                                color = YellowAccent,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            // Day rows with assignments
            sortedDayNames.forEachIndexed { rowIdx, dayName ->
                val rowBg = if (rowIdx % 2 == 0) Color(0xFF181818) else Color(0xFF222222)
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .background(rowBg)
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Day name cell
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                    Row(
                        modifier = Modifier
                            .wrapContentWidth()
                            .horizontalScroll(horizontalScrollState),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        slotNames.forEach { slotName ->
                            val slotAssignments = assignments.filter {
                                it.dayName == dayName && it.slotName == slotName
                            }
                            val isHighlighted = highlightedCells.contains(Triple(displayName, dayName, slotName))
                            AssignmentCell(
                                assignments = slotAssignments,
                                modifier = Modifier
                                    .width(90.dp)
                                    .height(64.dp)
                                    .padding(horizontal = 2.dp)
                                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            searchActive && !isHighlighted -> Color(0xFF3A3200)
                                            else -> Color.Transparent
                                        }
                                    ),
                                searchActive = searchActive,
                                isHighlighted = isHighlighted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AssignmentCell(
    assignments: List<SubjectAssignmentDetails>,
    modifier: Modifier = Modifier,
    searchActive: Boolean = false,
    isHighlighted: Boolean = false
) {
    var showDetailDialog by remember { mutableStateOf(false) }
    var selectedAssignment by remember { mutableStateOf<SubjectAssignmentDetails?>(null) }

    Card(
        modifier = modifier
            .clickable(enabled = assignments.isNotEmpty()) {
                if (assignments.isNotEmpty()) {
                    selectedAssignment = assignments.first()
                    showDetailDialog = true
                }
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                searchActive && !isHighlighted -> Color(0xFF3A3200)
                assignments.isEmpty() -> Color(0xFF232323)
                else -> YellowAccent
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (assignments.isNotEmpty()) 2.dp else 0.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (assignments.isEmpty()) {
                Text(
                    text = "—",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            } else if (assignments.size == 1) {
                val assignment = assignments.first()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val firstName = assignment.volunteerName.split(" ").firstOrNull() ?: assignment.volunteerName
                    Text(
                        text = firstName,
                        color = Color.Black,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Extract last 4 digits of roll number
                    val last4Digits = if (assignment.volunteerRollNo.length > 4) {
                        assignment.volunteerRollNo.takeLast(4)
                    } else {
                        assignment.volunteerRollNo
                    }
                    
                    // Show last 4 digits of roll number + subject code in oval box
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(
                                when {
                                    searchActive && !isHighlighted -> Color(0xFF3A3200).copy(alpha = 0.8f)
                                    else -> YellowAccent.copy(alpha = 0.6f)
                                },
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$last4Digits (${assignment.subjectCode})",
                            color = when {
                                searchActive && !isHighlighted -> Color(0xFFAA9955)
                                else -> Color.Black
                            },
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    items(assignments) { assignment ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color(0xFF2E7D32),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(2.dp)
                        ) {
                            val firstName = assignment.volunteerName.split(" ").firstOrNull() ?: assignment.volunteerName
                            Text(
                                text = firstName,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
    if (showDetailDialog && selectedAssignment != null) {
        AssignmentDetailDialog(
            assignment = selectedAssignment!!,
            onDismiss = { showDetailDialog = false }
        )
    }
}

@Composable
fun AssignmentDetailDialog(
    assignment: SubjectAssignmentDetails,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF222222), RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Volunteer info card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFF2E7D32), // Green background for card
                        RoundedCornerShape(8.dp)
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Volunteer name
                    Text(
                        text = assignment.volunteerName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    // Roll number
                    Text(
                        text = assignment.volunteerRollNo,
                        color = Color.LightGray,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Subject name pill
                    Box(
                        modifier = Modifier
                            .background(YellowAccent, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = assignment.subjectName,
                            color = Color.Black,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Slot details
            Text(
                text = "Slot Details: ${assignment.schoolName} ${assignment.classAndSection}, ${assignment.dayName}, ${assignment.slotName}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Subject preferences (all 3)
            Text(
                text = "Subject Preferences",
                color = YellowAccent,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subject preference pills (showing current subject as first preference and dummy others)
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Current subject is first preference, then add 2 dummy preferences
                val subjectCode = assignment.subjectCode
                val dummySubjects = listOf(
                    SubjectConstants.MATHEMATICS,
                    SubjectConstants.ENGLISH,
                    SubjectConstants.SCIENCE
                ).filter { it != subjectCode }.take(2)
                
                val allPreferences = listOf(subjectCode) + dummySubjects
                
                allPreferences.forEachIndexed { index, code ->
                    val prefNumber = index + 1
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .background(
                                if (index == 0) YellowAccent else Color(0xFF3A3A3A),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$prefNumber. $code",
                            color = if (index == 0) Color.Black else YellowAccent,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Available Groups section
            Text(
                text = "Available Groups for this Slot",
                color = YellowAccent,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Group pills
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Show current group and some adjacent groups
                val groupNum = try {
                    assignment.volunteerGroup.toInt()
                } catch (e: Exception) {
                    0
                }
                
                val groupsToShow = listOf(
                    (groupNum - 1).coerceAtLeast(1),
                    groupNum,
                    groupNum + 1
                ).distinct()
                
                groupsToShow.forEach { group ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .background(
                                if (group.toString() == assignment.volunteerGroup) YellowAccent 
                                else Color(0xFF3A3A3A),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = group.toString(),
                            color = if (group.toString() == assignment.volunteerGroup) Color.Black else YellowAccent,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Close button
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = YellowAccent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Close")
            }
        }
    }
}

// Function to get a background color for subject cards - using provided color palette
private fun getSubjectCardColor(subjectCode: String): Color {
    return Color(0xFFFCF55F) // Darker shade of yellow (#FCF55F)
}

// Function to get a text color for subject names - always white for better contrast
private fun getSubjectTextColor(subjectCode: String): Color {
    return Color.White // Using white text for all subjects for better readability
}

@Composable
fun FilterDialog(
    currentFilters: Set<String>,
    onDismiss: () -> Unit,
    onApplyFilters: (Set<String>) -> Unit,
    assignments: List<SubjectAssignmentDetails>
) {
    var selectedFilters by remember { mutableStateOf(currentFilters) }
    
    // Extract unique subject codes and schools from assignments
    val subjects = remember(assignments) {
        assignments.map { it.subjectCode to it.subjectName }.toSet().toList()
    }
    
    val schools = remember(assignments) {
        assignments.map { it.schoolName }.toSet().sorted()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Filter Assignments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Subjects",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = YellowAccent
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subjects.forEach { (code, name) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFilters = if (selectedFilters.contains(code)) {
                                        selectedFilters - code
                                    } else {
                                        selectedFilters + code
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = selectedFilters.contains(code),
                                onCheckedChange = { checked ->
                                    selectedFilters = if (checked) {
                                        selectedFilters + code
                                    } else {
                                        selectedFilters - code
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = YellowAccent,
                                    uncheckedColor = Color.White.copy(alpha = 0.7f)
                                )
                            )
                            
                            Text(
                                text = "$name ($code)",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
                
                HorizontalDivider(color = Color(0xFF444444), thickness = 1.dp)
                
                Text(
                    text = "Schools",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = YellowAccent
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    schools.forEach { school ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFilters = if (selectedFilters.contains(school)) {
                                        selectedFilters - school
                                    } else {
                                        selectedFilters + school
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = selectedFilters.contains(school),
                                onCheckedChange = { checked ->
                                    selectedFilters = if (checked) {
                                        selectedFilters + school
                                    } else {
                                        selectedFilters - school
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = YellowAccent,
                                    uncheckedColor = Color.White.copy(alpha = 0.7f)
                                )
                            )
                            
                            Text(
                                text = school,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApplyFilters(selectedFilters) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = YellowAccent,
                    contentColor = Color.Black
                )
            ) {
                Text("Apply Filters")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = YellowAccent
                )
            ) {
                Text("Cancel")
            }
        },
        containerColor = Color(0xFF1E1E1E),
        textContentColor = Color.White
    )
}

// Function to generate PDF and share it
private suspend fun generateAndSharePDF(
    context: Context,
    assignments: List<SubjectAssignmentDetails>,
    selectedSchool: String?
): String = withContext(Dispatchers.IO) {
    try {
        // Group assignments by school and section
        val groupedAssignments = assignments.groupBy { "${it.schoolName} - ${it.classAndSection}" }
        
        // Create a custom comparator for alphanumeric sorting
        val alphanumericComparator = Comparator<String> { a, b ->
            // Split into parts (e.g., "AM - 10N" -> ["AM", "10N"])
            val aParts = a.split(" - ", limit = 2)
            val bParts = b.split(" - ", limit = 2)
            
            // Compare school names first
            val schoolA = aParts[0]
            val schoolB = bParts[0]
            val schoolCompare = schoolA.compareTo(schoolB)
            
            if (schoolCompare != 0) return@Comparator schoolCompare
            
            // Then compare section parts
            val sectionA = if (aParts.size > 1) aParts[1] else ""
            val sectionB = if (bParts.size > 1) bParts[1] else ""
            
            // Extract numeric prefix from section
            val numA = sectionA.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            val numB = sectionB.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            
            val numCompare = numA.compareTo(numB)
            if (numCompare != 0) return@Comparator numCompare
            
            // Finally compare alphabetic suffix
            val suffixA = sectionA.dropWhile { it.isDigit() }
            val suffixB = sectionB.dropWhile { it.isDigit() }
            suffixA.compareTo(suffixB)
        }
        
        // Sort the entries using the custom comparator
        val sortedEntries = groupedAssignments.entries
            .sortedWith(compareBy<Map.Entry<String, List<SubjectAssignmentDetails>>, String>(alphanumericComparator) { it.key })
        
        // Create PDF document
        val pdfDocument = PdfDocument()
        // Configure different paints for different elements
        val borderPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.5f  // Slightly thicker border
        }
        
        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 18f
            color = android.graphics.Color.BLACK
        }
        
        val headerPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
            color = android.graphics.Color.BLACK
        }
        
        val textPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 12f
            color = android.graphics.Color.BLACK
        }
        
        val headerCellPaint = Paint().apply {
            color = android.graphics.Color.rgb(240, 240, 240) // Light gray background for headers
            style = Paint.Style.FILL
        }
        
        // Special paint for day names
        val dayNamePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 16f
            color = android.graphics.Color.BLACK
        }
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val pdfFileName = "schedule_${selectedSchool ?: "All"}_$timestamp.pdf"
        
        var pageNumber = 1
        // For each section, create a page in the PDF
        for ((displayName, sectionAssignments) in sortedEntries) {
            // Extract unique day and slot names for this section
            val unsortedDayNames = sectionAssignments.map { it.dayName }.distinct()
            val slotNames = sectionAssignments.map { it.slotName }.distinct().sorted()
            
            // Define standard day order
            val standardDayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            
            // Sort days according to standard order
            val dayNames = unsortedDayNames.sortedWith { day1, day2 ->
                val index1 = standardDayOrder.indexOf(day1)
                val index2 = standardDayOrder.indexOf(day2)
                
                when {
                    index1 >= 0 && index2 >= 0 -> index1.compareTo(index2)
                    index1 >= 0 -> -1
                    index2 >= 0 -> 1
                    else -> day1.compareTo(day2)
                }
            }
            
            // Log day names for debugging
            Log.d("PDF_EXPORT", "Day names for $displayName (in order): ${dayNames.joinToString(", ")}")
            Log.d("PDF_EXPORT", "Slot names for $displayName: ${slotNames.joinToString(", ")}")
            
            // Create PDF page
            val pageInfo = PdfDocument.PageInfo.Builder(842, 595, pageNumber++).create() // A4 landscape
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            // Draw page title only - removed timestamp as requested
            canvas.drawText("Schedule: $displayName", 50f, 50f, titlePaint)
            
            // Draw table headers - increased cell width and height to fit full names, roll numbers, and full subject names
            val startX = 50f
            val startY = 80f  // Moved up since we removed the timestamp line
            val cellWidth = 140f
            val cellHeight = 85f  // Increased height to accommodate full subject names
            val headerStartY = startY + 30f
            
            // First, draw a background for the header row
            val headerRowRect = android.graphics.RectF(
                startX, 
                startY, 
                startX + cellWidth * (slotNames.size + 1), 
                startY + cellHeight
            )
            canvas.drawRect(headerRowRect, headerCellPaint)
            
            // Draw day column header with proper centering
            val dayTimeText = "Day/Time"
            val dayTimeX = startX + cellWidth/2 - dayNamePaint.measureText(dayTimeText)/2
            val textHeight = dayNamePaint.descent() - dayNamePaint.ascent()
            val textOffset = textHeight / 2 - dayNamePaint.descent()
            val dayTimeY = startY + cellHeight/2 + textOffset
            canvas.drawText(dayTimeText, dayTimeX, dayTimeY, dayNamePaint)
            
            // Draw time slot headers - make them more prominent using the same paint as day names
            slotNames.forEachIndexed { index, slotName ->
                val cellX = startX + (index + 1) * cellWidth
                val x = cellX + cellWidth/2 - dayNamePaint.measureText(slotName)/2 // Center text
                
                // Vertical centering calculation for header text
                val textHeight = dayNamePaint.descent() - dayNamePaint.ascent()
                val textOffset = textHeight / 2 - dayNamePaint.descent()
                val slotNameY = startY + cellHeight/2 + textOffset
                
                // Draw slot name with same prominence as day names
                canvas.drawText(slotName, x, slotNameY, dayNamePaint)
            }
            
            // Draw day rows
            dayNames.forEachIndexed { dayIndex, dayName ->
                val rowY = startY + (dayIndex + 1) * cellHeight
                
                // Draw assignments for each time slot
                slotNames.forEachIndexed { slotIndex, slotName ->
                    val cellX = startX + (slotIndex + 1) * cellWidth
                    
                    val assignment = sectionAssignments.find { 
                        it.dayName == dayName && it.slotName == slotName 
                    }
                    
                    if (assignment != null) {
                        // Use full name and full roll number
                        val fullName = assignment.volunteerName
                        val fullRollNumber = assignment.volunteerRollNo
                        
                        // Text wrapping for long names
                        val maxWidth = cellWidth - 25f
                        
                        // Draw volunteer name (full name) - handle text wrapping
                        val nameLines = wrapText(fullName, textPaint, maxWidth)
                        val nameY = rowY + 15f
                        // Modify the text paint to make names stand out more
                        val namePaint = Paint(textPaint)
                        namePaint.isFakeBoldText = true
                        
                        for ((index, line) in nameLines.withIndex()) {
                            canvas.drawText(line, cellX + 10f, nameY + (index * 13f), namePaint)
                        }
                        
                        // Draw roll number (full roll number)
                        canvas.drawText(fullRollNumber, cellX + 10f, rowY + 40f, textPaint)
                        
                        // Draw full subject name instead of subject code
                        if (assignment.subjectCode.isNotBlank()) {
                            // Use the full subject name (e.g., "Physics" instead of "PY")
                            val fullSubjectName = assignment.subjectName
                            
                            // For longer subject names, we might need to use a smaller font
                            val subjectPaint = Paint(headerPaint)
                            subjectPaint.color = android.graphics.Color.rgb(139, 69, 19)  // Brown color for subjects
                            subjectPaint.isFakeBoldText = true  // Make it bold
                            
                            // Measure the text width to see if we need to reduce font size
                            val subjectWidth = subjectPaint.measureText(fullSubjectName)
                            if (subjectWidth > cellWidth - 20f) {
                                // Reduce font size for long subject names
                                subjectPaint.textSize = 11f
                            } else {
                                // Use slightly larger font for subjects that fit
                                subjectPaint.textSize = 12f
                            }
                            
                            // Draw the full subject name lower in the cell to take advantage of increased height
                            canvas.drawText(fullSubjectName, cellX + 10f, rowY + 65f, subjectPaint)
                        }
                    }
                }
                
                // ======= DAY COLUMN DRAWING - MOVED TO AFTER ASSIGNMENT CELLS =======
                // First draw day column background 
                val dayColumnBackgroundPaint = Paint()
                dayColumnBackgroundPaint.color = android.graphics.Color.rgb(230, 230, 230) // Slightly darker gray
                dayColumnBackgroundPaint.style = Paint.Style.FILL
                
                val dayColumnRect = android.graphics.RectF(
                    startX,
                    rowY,
                    startX + cellWidth,
                    rowY + cellHeight
                )
                canvas.drawRect(dayColumnRect, dayColumnBackgroundPaint)
                
                // Then draw day name on top of background
                val dayNameX = startX + cellWidth/2 - dayNamePaint.measureText(dayName)/2
                val textHeight = dayNamePaint.descent() - dayNamePaint.ascent()
                val textOffset = textHeight / 2 - dayNamePaint.descent()
                val dayNameY = rowY + cellHeight/2 + textOffset
                
                // Log day drawing for debugging
                Log.d("PDF_EXPORT", "Drawing day '$dayName' at position x=$dayNameX, y=$dayNameY")
                
                // Draw the day name with bold, large font
                dayNamePaint.textSize = 20f  // Increased font size even more
                dayNamePaint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                dayNamePaint.color = android.graphics.Color.BLACK
                
                // Draw the actual day name
                canvas.drawText(dayName, dayNameX, dayNameY, dayNamePaint)
            }
            
                         // Draw outer table border
            canvas.drawRect(
                startX, startY,
                startX + cellWidth * (slotNames.size + 1), startY + cellHeight * (dayNames.size + 1),
                borderPaint
            )
            
            // Draw all vertical grid lines
            for (i in 0..slotNames.size) {
                val lineX = startX + i * cellWidth
                canvas.drawLine(
                    lineX, startY,
                    lineX, startY + cellHeight * (dayNames.size + 1),
                    borderPaint
                )
            }
            
            // Draw all horizontal grid lines
            for (i in 0..dayNames.size) {
                val lineY = startY + i * cellHeight
                canvas.drawLine(
                    startX, lineY,
                    startX + cellWidth * (slotNames.size + 1), lineY,
                    borderPaint
                )
            }
            
            pdfDocument.finishPage(page)
        }
        
        // Save the PDF to the cache directory under a shared_images folder
        // as defined in file_paths.xml in the app module
        val cacheDir = context.cacheDir
        val imagesDir = File(cacheDir, "images")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
        val pdfFile = File(imagesDir, pdfFileName)
        
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        
        // Share the PDF file
        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(
                context,
                context.applicationContext.packageName + ".fileprovider",
                pdfFile
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // If no PDF viewer app is available, offer to share the file
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
            }
        }
        
        return@withContext "PDF generated and opened"
    } catch (e: Exception) {
        Log.e("PDF_EXPORT", "Error generating PDF", e)
        throw e
    }
}

// Helper function for text wrapping in PDFs
private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var currentLine = StringBuilder()

    for (word in words) {
        val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
        val testLineWidth = paint.measureText(testLine)

        if (testLineWidth <= maxWidth) {
            currentLine.append(if (currentLine.isEmpty()) word else " $word")
        } else {
            // If the current line already has text, finish it and start a new line
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            } else {
                // If the single word is too long, need to cut it
                lines.add(word)
            }
        }
    }

    // Add the last line if there's anything
    if (currentLine.isNotEmpty()) {
        lines.add(currentLine.toString())
    }

    // Limit to maximum 2 lines to avoid overflow
    return if (lines.size > 2) {
        val shortenedSecondLine = lines[1] + "..."
        listOf(lines[0], shortenedSecondLine)
    } else {
        lines
    }
}

private suspend fun loadAssignments(): List<SubjectAssignmentDetails> {
    val db = FirestoreHelper.getFirestore()
    val result = mutableListOf<SubjectAssignmentDetails>()
    
    try {
        // Get all documents from the subjectAssignments collection
        val snapshot = db.collection(FirestoreCollection.SUBJECT_ASSIGNMENTS).get().await()
        Log.d("ViewAssignmentsScreen", "Found ${snapshot.documents.size} documents in subjectAssignments collection")
        
        // Process each document (each represents a schedule)
        for (document in snapshot.documents) {
            val data = document.data ?: continue
            
            // Parse raw assignment data
            val scheduleName = data["scheduleName"] as? String ?: document.id
            Log.d("ViewAssignmentsScreen", "Processing schedule: $scheduleName with document ID: ${document.id}")
            
            val assignments = (data["assignments"] as? List<Map<String, Any>>)?.map { assignment ->
                AssignmentEntry(
                    dayIndex = (assignment["dayIndex"] as? Number)?.toInt() ?: 0,
                    slotIndex = (assignment["slotIndex"] as? Number)?.toInt() ?: 0,
                    volunteerName = assignment["volunteerName"] as? String ?: "",
                    volunteerRollNo = assignment["volunteerRollNo"] as? String ?: "",
                    volunteerGroup = assignment["volunteerGroup"] as? String ?: "",
                    subjectCode = assignment["subjectCode"] as? String ?: ""
                )
            } ?: emptyList()
            
            Log.d("ViewAssignmentsScreen", "Found ${assignments.size} assignments for $scheduleName")
            
            // Get schedule reference data to map day/slot indices to names
            val scheduleDoc = db.collection(FirestoreCollection.GENERATED_SCHEDULES)
                .whereEqualTo("name", scheduleName)
                .get()
                .await()
                .documents
                .firstOrNull()
            
            if (scheduleDoc != null) {
                val referenceData = scheduleDoc.get("referenceData") as? Map<String, Any>
                val dayNames = (referenceData?.get("dayNames") as? List<String>) ?: emptyList()
                val slotNames = (referenceData?.get("timeSlotNames") as? List<String>) ?: emptyList()
                
                Log.d("ViewAssignmentsScreen", "Schedule data found: days=${dayNames.size}, slots=${slotNames.size}")
                
                // Process each assignment
                for (assignment in assignments) {
                    // Skip entries without subject code
                    if (assignment.subjectCode.isBlank()) continue
                    
                    // Extract school name and class+section from schedule name
                    // Handle different possible formats:
                    // "AM 10N" -> school="AM", classAndSection="10N"
                    // "AM-10N" -> school="AM", classAndSection="10N"
                    // "AM" -> school="AM", classAndSection=""
                    var schoolName = ""
                    var classAndSection = ""
                    
                    if (scheduleName.contains(" ")) {
                        // Format with space: "AM 10N"
                        val parts = scheduleName.split(" ", limit = 2)
                        schoolName = parts[0].trim()
                        classAndSection = if (parts.size > 1) parts[1].trim() else ""
                    } else if (scheduleName.contains("-")) {
                        // Format with dash: "AM-10N"
                        val parts = scheduleName.split("-", limit = 2)
                        schoolName = parts[0].trim()
                        classAndSection = if (parts.size > 1) parts[1].trim() else ""
                    } else {
                        // No separator, assume it's just the school name
                        schoolName = scheduleName.trim()
                    }
                    
                    Log.d("ViewAssignmentsScreen", "Parsed school=$schoolName, section=$classAndSection from $scheduleName")
                    
                    // Get day and slot names
                    val dayName = if (assignment.dayIndex < dayNames.size) dayNames[assignment.dayIndex] else "Day ${assignment.dayIndex}"
                    val slotName = if (assignment.slotIndex < slotNames.size) slotNames[assignment.slotIndex] else "Slot ${assignment.slotIndex}"
                    
                    // Get full subject name
                    val subjectName = SubjectConstants.SUBJECT_NAMES[assignment.subjectCode] ?: assignment.subjectCode
                    
                    // Create assignment details
                    val details = SubjectAssignmentDetails(
                        volunteerName = assignment.volunteerName,
                        volunteerRollNo = assignment.volunteerRollNo,
                        volunteerGroup = assignment.volunteerGroup,
                        subjectCode = assignment.subjectCode,
                        subjectName = subjectName,
                        dayName = dayName,
                        slotName = slotName,
                        schoolName = schoolName,
                        classAndSection = classAndSection
                    )
                    
                    result.add(details)
                    Log.d("ViewAssignmentsScreen", "Added assignment: ${details.volunteerName}, ${details.subjectName}, ${details.dayName}, ${details.schoolName} ${details.classAndSection}")
                }
            } else {
                Log.w("ViewAssignmentsScreen", "Could not find schedule document for $scheduleName")
                
                // Even if we can't find the schedule document, try to create assignments with default values
                for (assignment in assignments) {
                    // Skip entries without subject code
                    if (assignment.subjectCode.isBlank()) continue
                    
                    // Parse schedule name same as above
                    var schoolName = ""
                    var classAndSection = ""
                    
                    if (scheduleName.contains(" ")) {
                        val parts = scheduleName.split(" ", limit = 2)
                        schoolName = parts[0].trim()
                        classAndSection = if (parts.size > 1) parts[1].trim() else ""
                    } else if (scheduleName.contains("-")) {
                        val parts = scheduleName.split("-", limit = 2)
                        schoolName = parts[0].trim()
                        classAndSection = if (parts.size > 1) parts[1].trim() else ""
                    } else {
                        schoolName = scheduleName.trim()
                    }
                    
                    // Use default day/slot names based on indices
                    val dayName = "Day ${assignment.dayIndex + 1}"
                    val slotName = "Slot ${assignment.slotIndex + 1}"
                    
                    val subjectName = SubjectConstants.SUBJECT_NAMES[assignment.subjectCode] ?: assignment.subjectCode
                    
                    val details = SubjectAssignmentDetails(
                        volunteerName = assignment.volunteerName,
                        volunteerRollNo = assignment.volunteerRollNo,
                        volunteerGroup = assignment.volunteerGroup,
                        subjectCode = assignment.subjectCode,
                        subjectName = subjectName,
                        dayName = dayName,
                        slotName = slotName,
                        schoolName = schoolName,
                        classAndSection = classAndSection
                    )
                    
                    result.add(details)
                    Log.d("ViewAssignmentsScreen", "Added assignment with default values: ${details.volunteerName}, ${details.subjectName}, ${details.dayName}, ${details.schoolName} ${details.classAndSection}")
                }
            }
        }
        
        Log.d("ViewAssignmentsScreen", "Total assignments loaded: ${result.size}")
        // Define standard day order for sortinggit a
        val standardDayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        
        return result.sortedWith(compareBy<SubjectAssignmentDetails> { assignment -> 
            assignment.schoolName 
        }.thenBy { assignment -> 
            // Extract numeric part from classAndSection for proper numeric sorting
            val numericPrefix = assignment.classAndSection.takeWhile { char -> char.isDigit() }
            numericPrefix.toIntOrNull() ?: 0
        }.thenBy { assignment ->
            // Extract alphabetic suffix from classAndSection
            assignment.classAndSection.dropWhile { char -> char.isDigit() }
        }.thenBy { assignment -> 
            // Sort by standard day order instead of alphabetically
            val dayIndex = standardDayOrder.indexOf(assignment.dayName)
            if (dayIndex >= 0) dayIndex else Int.MAX_VALUE
        }.thenBy { assignment -> 
            // If day not in standard list, sort alphabetically
            if (standardDayOrder.contains(assignment.dayName)) "" else assignment.dayName
        }.thenBy { assignment -> 
            assignment.slotName 
        })
    } catch (e: Exception) {
        Log.e("ViewAssignmentsScreen", "Error loading assignments", e)
        throw e
    }
} 