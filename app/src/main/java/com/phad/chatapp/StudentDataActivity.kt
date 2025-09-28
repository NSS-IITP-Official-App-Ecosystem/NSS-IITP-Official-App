package com.phad.chatapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.phad.chatapp.adapters.Admin1Adapter
import com.phad.chatapp.adapters.Admin2Adapter
import com.phad.chatapp.adapters.StudentAdapter
import com.phad.chatapp.utils.Admin1DataViewer
import com.phad.chatapp.utils.Admin2DataViewer
import com.phad.chatapp.utils.MultiDatabaseHelper
import com.phad.chatapp.utils.StudentDataViewer
import kotlinx.coroutines.launch

class StudentDataActivity : AppCompatActivity() {
    private lateinit var tabLayout: TabLayout
    private lateinit var spinner: Spinner
    private lateinit var editTextQuery: EditText
    private lateinit var buttonSearch: Button
    private lateinit var textViewStatus: TextView
    private lateinit var recyclerView: RecyclerView
    
    private val TAG = "StudentDataActivity"
    
    // Current data type (student or admin)
    private var currentTab = 0 // 0 = Student, 1 = Admin1, 2 = Admin2
    
    // List of search criteria options for students
    private val studentSearchCriteria = listOf(
        "All Students",
        "By Roll Number",
        "Selected Students",
        "By Subject (Any Preference)",
        "By Subject (1st Preference)",
        "By NSS Group"
    )
    
    // List of search criteria options for subcoordinator admins
    private val subcoordinatorSearchCriteria = listOf(
        "All Subcoordinators",
        "By Roll Number",
        "By Committee",
        "By Designation"
    )
    
    // List of search criteria options for coordinator admins
    private val coordinatorSearchCriteria = listOf(
        "All Coordinators",
        "By Roll Number"
    )
    
    // List of search criteria options for combined admins
    private val combinedAdminSearchCriteria = listOf(
        "All Admins",
        "By Roll Number",
        "By Designation"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_data)
        
        // Initialize UI components
        tabLayout = findViewById(R.id.tab_layout)
        spinner = findViewById(R.id.spinner_criteria)
        editTextQuery = findViewById(R.id.edit_text_query)
        buttonSearch = findViewById(R.id.button_search)
        textViewStatus = findViewById(R.id.text_view_status)
        recyclerView = findViewById(R.id.recycler_view)
        
        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // Set up tabs
        setupTabs()
        
        // Set up spinner for student data (default tab)
        setupSpinner(studentSearchCriteria)
        
        // Handle spinner selection changes
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updateQueryFieldVisibility(position)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
        
        // Set up search button
        buttonSearch.setOnClickListener {
            when (currentTab) {
                0 -> performStudentSearch(spinner.selectedItemPosition)
                1 -> performSubcoordinatorSearch(spinner.selectedItemPosition)
                2 -> performCoordinatorSearch(spinner.selectedItemPosition)
                3 -> performCombinedAdminSearch(spinner.selectedItemPosition)
            }
        }
        
        // Initialize the secondary Firebase app
        try {
            MultiDatabaseHelper.initializeSecondaryFirebase(this)
            
            // Test the connection by fetching all data types
            lifecycleScope.launch {
                try {
                    val students = StudentDataViewer.getAllStudents()
                    val subcoordinators = Admin1DataViewer.getAllAdmins()
                    val coordinators = Admin2DataViewer.getAllCoordinators()
                    
                    Log.d(TAG, "Successfully connected to TWApp. Found ${students.size} students, " +
                        "${subcoordinators.size} subcoordinators, ${coordinators.size} coordinators")
                    
                    Toast.makeText(this@StudentDataActivity, 
                        "Connected to TWApp database (${students.size} students, " +
                        "${subcoordinators.size} subcoordinators, ${coordinators.size} coordinators)", 
                        Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Error connecting to TWApp database", e)
                    Toast.makeText(this@StudentDataActivity, 
                        "Error connecting to TWApp database: ${e.message}", 
                        Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize secondary Firebase app", e)
            Toast.makeText(this, 
                "Failed to initialize TWApp connection: ${e.message}", 
                Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupTabs() {
        // Add tabs
        tabLayout.apply {
            addTab(newTab().setText("Students"))
            addTab(newTab().setText("Subcoordinators"))
            addTab(newTab().setText("Coordinators"))
            addTab(newTab().setText("All Admins"))
        }
        
        // Set tab selection listener
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                
                // Update the search criteria spinner based on the selected tab
                when (currentTab) {
                    0 -> setupSpinner(studentSearchCriteria)
                    1 -> setupSpinner(subcoordinatorSearchCriteria)
                    2 -> setupSpinner(coordinatorSearchCriteria)
                    3 -> setupSpinner(combinedAdminSearchCriteria)
                }
                
                // Clear previous results
                textViewStatus.text = "Select search criteria"
                recyclerView.adapter = null
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }
    
    private fun setupSpinner(items: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }
    
    private fun updateQueryFieldVisibility(position: Int) {
        when (currentTab) {
            0 -> {
                // Student tab
                when (position) {
                    0 -> { // All Students
                        editTextQuery.visibility = View.GONE
                    }
                    2 -> { // Selected Students
                        editTextQuery.visibility = View.GONE
                    }
                    else -> {
                        editTextQuery.visibility = View.VISIBLE
                        
                        // Set hint based on selected criteria
                        editTextQuery.hint = when (position) {
                            1 -> "Enter Roll Number" // By Roll Number
                            3, 4 -> "Enter Subject" // By Subject
                            5 -> "Enter NSS Group" // By NSS Group
                            else -> "Enter Query"
                        }
                    }
                }
            }
            1, 2, 3 -> {
                // Admin tabs
                when (position) {
                    0 -> { // All Admins/Subcoordinators/Coordinators
                        editTextQuery.visibility = View.GONE
                    }
                    else -> {
                        editTextQuery.visibility = View.VISIBLE
                        
                        // Set hint based on selected criteria
                        editTextQuery.hint = when (position) {
                            1 -> "Enter Roll Number" // By Roll Number
                            2 -> if (currentTab == 3) "Enter Designation" else "Enter Committee" // By Committee/Designation
                            3 -> "Enter Designation" // By Designation
                            else -> "Enter Query"
                        }
                    }
                }
            }
        }
    }
    
    private fun performStudentSearch(criteriaPosition: Int) {
        // Show loading status
        textViewStatus.text = "Loading..."
        textViewStatus.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val students = when (criteriaPosition) {
                    0 -> { // All Students
                        StudentDataViewer.getAllStudents()
                    }
                    1 -> { // By Roll Number
                        val rollNumber = editTextQuery.text.toString().trim()
                        if (rollNumber.isEmpty()) {
                            throw IllegalArgumentException("Please enter a roll number")
                        }
                        val student = StudentDataViewer.getStudentByRollNumber(rollNumber)
                        if (student != null) listOf(student) else emptyList()
                    }
                    2 -> { // Selected Students
                        StudentDataViewer.getStudentsByCriteria("selected", "Yes")
                    }
                    3 -> { // By Subject (Any Preference)
                        val subject = editTextQuery.text.toString().trim()
                        if (subject.isEmpty()) {
                            throw IllegalArgumentException("Please enter a subject")
                        }
                        StudentDataViewer.getStudentsBySubjectPreference(subject)
                    }
                    4 -> { // By Subject (1st Preference)
                        val subject = editTextQuery.text.toString().trim()
                        if (subject.isEmpty()) {
                            throw IllegalArgumentException("Please enter a subject")
                        }
                        StudentDataViewer.getStudentsBySpecificPreference(subject, 1)
                    }
                    5 -> { // By NSS Group
                        val nssGroup = editTextQuery.text.toString().trim()
                        if (nssGroup.isEmpty()) {
                            throw IllegalArgumentException("Please enter an NSS group")
                        }
                        StudentDataViewer.getStudentsByCriteria("nss_group", nssGroup)
                    }
                    else -> emptyList()
                }
                
                // Update UI with results
                if (students.isEmpty()) {
                    textViewStatus.text = "No students found"
                } else {
                    textViewStatus.text = "${students.size} students found"
                    
                    // Set up adapter with student data
                    val studentAdapter = StudentAdapter(students)
                    recyclerView.adapter = studentAdapter
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing student search", e)
                textViewStatus.text = "Error: ${e.message}"
            }
        }
    }
    
    private fun performSubcoordinatorSearch(criteriaPosition: Int) {
        // Show loading status
        textViewStatus.text = "Loading..."
        textViewStatus.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val admins = when (criteriaPosition) {
                    0 -> { // All Subcoordinators
                        Admin1DataViewer.getAllAdmins()
                    }
                    1 -> { // By Roll Number
                        val rollNumber = editTextQuery.text.toString().trim()
                        if (rollNumber.isEmpty()) {
                            throw IllegalArgumentException("Please enter a roll number")
                        }
                        val admin = Admin1DataViewer.getAdminByRollNumber(rollNumber)
                        if (admin != null) listOf(admin) else emptyList()
                    }
                    2 -> { // By Committee
                        val committee = editTextQuery.text.toString().trim()
                        if (committee.isEmpty()) {
                            throw IllegalArgumentException("Please enter a committee name")
                        }
                        Admin1DataViewer.getAdminsByCommittee(committee)
                    }
                    3 -> { // By Designation
                        val designation = editTextQuery.text.toString().trim()
                        if (designation.isEmpty()) {
                            throw IllegalArgumentException("Please enter a designation")
                        }
                        Admin1DataViewer.getAdminsByDesignation(designation)
                    }
                    else -> emptyList()
                }
                
                // Update UI with results
                if (admins.isEmpty()) {
                    textViewStatus.text = "No subcoordinators found"
                } else {
                    textViewStatus.text = "${admins.size} subcoordinators found"
                    
                    // Set up adapter with admin data
                    val adminAdapter = Admin1Adapter(admins)
                    recyclerView.adapter = adminAdapter
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing subcoordinator search", e)
                textViewStatus.text = "Error: ${e.message}"
            }
        }
    }
    
    private fun performCoordinatorSearch(criteriaPosition: Int) {
        // Show loading status
        textViewStatus.text = "Loading..."
        textViewStatus.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val coordinators = when (criteriaPosition) {
                    0 -> { // All Coordinators
                        Admin2DataViewer.getAllCoordinators()
                    }
                    1 -> { // By Roll Number
                        val rollNumber = editTextQuery.text.toString().trim()
                        if (rollNumber.isEmpty()) {
                            throw IllegalArgumentException("Please enter a roll number")
                        }
                        val coordinator = Admin2DataViewer.getCoordinatorByRollNumber(rollNumber)
                        if (coordinator != null) listOf(coordinator) else emptyList()
                    }
                    else -> emptyList()
                }
                
                // Update UI with results
                if (coordinators.isEmpty()) {
                    textViewStatus.text = "No coordinators found"
                } else {
                    textViewStatus.text = "${coordinators.size} coordinators found"
                    
                    // Set up adapter with coordinator data using Admin2Adapter
                    val adminAdapter = Admin2Adapter(coordinators)
                    recyclerView.adapter = adminAdapter
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing coordinator search", e)
                textViewStatus.text = "Error: ${e.message}"
            }
        }
    }
    
    private fun performCombinedAdminSearch(criteriaPosition: Int) {
        // Show loading status
        textViewStatus.text = "Loading..."
        textViewStatus.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val admins = when (criteriaPosition) {
                    0 -> { // All Admins
                        Admin2DataViewer.getAllAdmins() // This method combines both admin collections
                    }
                    1 -> { // By Roll Number
                        val rollNumber = editTextQuery.text.toString().trim()
                        if (rollNumber.isEmpty()) {
                            throw IllegalArgumentException("Please enter a roll number")
                        }
                        
                        // Try to find in either collection
                        val admin1 = Admin1DataViewer.getAdminByRollNumber(rollNumber)
                        val admin2 = Admin2DataViewer.getCoordinatorByRollNumber(rollNumber)
                        
                        val results = mutableListOf<Map<String, Any>>()
                        if (admin1 != null) results.add(admin1)
                        if (admin2 != null) results.add(admin2)
                        
                        results
                    }
                    2 -> { // By Designation
                        val designation = editTextQuery.text.toString().trim()
                        if (designation.isEmpty()) {
                            throw IllegalArgumentException("Please enter a designation")
                        }
                        
                        if (designation.equals("Coordinator", ignoreCase = true)) {
                            Admin2DataViewer.getAllCoordinators()
                        } else {
                            Admin1DataViewer.getAdminsByDesignation(designation)
                        }
                    }
                    else -> emptyList()
                }
                
                // Update UI with results
                if (admins.isEmpty()) {
                    textViewStatus.text = "No admins found"
                } else {
                    textViewStatus.text = "${admins.size} admins found"
                    
                    // Determine which adapter to use based on data source
                    val isCoordinatorOnly = criteriaPosition == 2 && 
                                         editTextQuery.text.toString().equals("Coordinator", ignoreCase = true)
                    
                    // Use Admin2Adapter for coordinator-only results, otherwise use Admin1Adapter
                    val adminAdapter = if (isCoordinatorOnly) {
                        Admin2Adapter(admins)
                    } else {
                        Admin1Adapter(admins)
                    }
                    recyclerView.adapter = adminAdapter
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing combined admin search", e)
                textViewStatus.text = "Error: ${e.message}"
            }
        }
    }
} 