package com.phad.chatapp.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.R
import com.phad.chatapp.SessionManager
import com.phad.chatapp.databinding.ActivityCreateUpdateBinding
import com.phad.chatapp.models.Update
import java.util.*

class CreateUpdateActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreateUpdateBinding
    private val TAG = "CreateUpdateActivity"
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var sessionManager: SessionManager
    
    private var selectedImageUri: Uri? = null
    private var selectedDocumentUri: Uri? = null
    private var documentName: String? = null
    
    // Location data
    private var locationLatitude: Double? = null
    private var locationLongitude: Double? = null
    
    // Event date
    private var eventDate: Long? = null
    
    // Category options
    private val categoryOptions = listOf("General", "Academic", "Event", "Announcement", "Notice")
    
    // Register for image selection result
    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                binding.selectedImageLayout.visibility = View.VISIBLE
                binding.selectedImage.setImageURI(uri)
                binding.removeImageButton.visibility = View.VISIBLE
            }
        }
    }
    
    // Register for document selection result
    private val selectDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedDocumentUri = uri
                documentName = getDocumentName(uri)
                binding.selectedDocumentLayout.visibility = View.VISIBLE
                binding.documentNameText.text = documentName
                binding.removeDocumentButton.visibility = View.VISIBLE
            }
        }
    }
    
    // Register for map location selection result
    private val selectLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                // This would handle data returned from a map picker activity
                // For demo purposes, we'll just use placeholder values
                locationLatitude = data.getDoubleExtra("latitude", 0.0)
                locationLongitude = data.getDoubleExtra("longitude", 0.0)
                
                // Update location name if provided
                val locationName = data.getStringExtra("location_name")
                if (!locationName.isNullOrEmpty()) {
                    binding.locationNameEditText.setText(locationName)
                }
                
                // Update location address if provided
                val locationAddress = data.getStringExtra("location_address")
                if (!locationAddress.isNullOrEmpty()) {
                    binding.locationAddressEditText.setText(locationAddress)
                }
                
                Toast.makeText(this, "Location selected", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateUpdateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        sessionManager = SessionManager(this)
        
        setupToolbar()
        setupCategoryDropdown()
        setupButtons()
        setupEventDateVisibility()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }
    
    private fun setupCategoryDropdown() {
        val adapter = ArrayAdapter(this, R.layout.dropdown_item, categoryOptions)
        (binding.categoryDropdown as? AutoCompleteTextView)?.setAdapter(adapter)
        
        // Set default value
        binding.categoryDropdown.setText(categoryOptions[0], false)
        
        // Listen for category changes to show/hide event date selector
        binding.categoryDropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedCategory = categoryOptions[position]
            binding.eventDateContainer.visibility = 
                if (selectedCategory.equals("Event", ignoreCase = true)) View.VISIBLE else View.GONE
        }
    }
    
    private fun setupEventDateVisibility() {
        // Initially hide the event date container
        binding.eventDateContainer.visibility = View.GONE
    }
    
    private fun setupButtons() {
        // Add Image Button
        binding.addImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            selectImageLauncher.launch(intent)
        }
        
        // Add Document Button
        binding.addDocumentButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*" // All file types
            }
            selectDocumentLauncher.launch(intent)
        }
        
        // Event Date Button
        binding.eventDateButton.setOnClickListener {
            showDatePicker()
        }
        
        // Pick Location Button
        binding.pickLocationButton.setOnClickListener {
            // In a real app, you'd launch a map activity or use Places API
            Toast.makeText(this, "Map location picker would launch here", Toast.LENGTH_SHORT).show()
            
            // For demo purposes, simulating coordinates
            MaterialAlertDialogBuilder(this)
                .setTitle("Location Selection")
                .setMessage("In a complete implementation, this would launch a map to select a location. For now, we'll use placeholder coordinates.")
                .setPositiveButton("Use Demo Coordinates") { _, _ ->
                    locationLatitude = 40.7128
                    locationLongitude = -74.0060
                    Toast.makeText(this, "Location set to New York coordinates", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        // Remove Image Button
        binding.removeImageButton.setOnClickListener {
            selectedImageUri = null
            binding.selectedImageLayout.visibility = View.GONE
            binding.removeImageButton.visibility = View.GONE
        }
        
        // Remove Document Button
        binding.removeDocumentButton.setOnClickListener {
            selectedDocumentUri = null
            documentName = null
            binding.selectedDocumentLayout.visibility = View.GONE
            binding.removeDocumentButton.visibility = View.GONE
        }
        
        // Post Update Button
        binding.postUpdateButton.setOnClickListener {
            val content = binding.updateContentEditText.text.toString().trim()
            if (content.isEmpty() && selectedImageUri == null && selectedDocumentUri == null) {
                Toast.makeText(this, "Please add some content, an image, or a document", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            binding.progressBar.visibility = View.VISIBLE
            binding.postUpdateButton.isEnabled = false
            
            publishUpdate()
        }
    }
    
    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        
        // If we already have a selected date, use it
        eventDate?.let {
            calendar.timeInMillis = it
        }
        
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        
        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            calendar.set(selectedYear, selectedMonth, selectedDay)
            eventDate = calendar.timeInMillis
            
            // Update the button text to show selected date
            val dateFormat = java.text.SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault())
            val formattedDate = dateFormat.format(calendar.time)
            binding.eventDateButton.text = formattedDate
        }, year, month, day).show()
    }
    
    private fun publishUpdate() {
        val currentUser = auth.currentUser
        
        if (currentUser == null) {
            binding.progressBar.visibility = View.GONE
            binding.postUpdateButton.isEnabled = true
            Toast.makeText(this, "You must be logged in to post updates", Toast.LENGTH_SHORT).show()
            return
        }
        
        val authorId = currentUser.uid
        val authorName = sessionManager.getUserName() ?: "Admin"
        val timestamp = System.currentTimeMillis()
        
        // Get all input values
        val title = binding.updateTitleEditText.text.toString().trim().ifEmpty { "Untitled Update" }
        val content = binding.updateContentEditText.text.toString().trim()
        val category = binding.categoryDropdown.text.toString().lowercase()
        val isImportant = binding.importantCheckbox.isChecked
        val locationName = binding.locationNameEditText.text.toString().trim().takeIf { it.isNotEmpty() }
        val locationAddress = binding.locationAddressEditText.text.toString().trim().takeIf { it.isNotEmpty() }
        val externalLink = binding.externalLinkEditText.text.toString().trim().takeIf { it.isNotEmpty() }
        
        // Parse tags
        val tagsInput = binding.tagsEditText.text.toString().trim()
        val tags = if (tagsInput.isNotEmpty()) {
            tagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        
        // Create the update object with new simplified model
        val update = Update(
            id = UUID.randomUUID().toString(),
            authorId = authorId,
            authorName = authorName,
            authorImageUrl = currentUser.photoUrl?.toString(),
            title = title,
            content = content,
            externalLink = externalLink,
            documentName = documentName,
            documentUrl = selectedDocumentUri?.toString(), // In a real app, this would be a Drive URL
            imageName = if (selectedImageUri != null) "image_${System.currentTimeMillis()}.jpg" else null,
            imageUrl = selectedImageUri?.toString(), // In a real app, this would be a Drive URL
            mediaUrl = selectedImageUri?.toString(), // Keep for backward compatibility
            timestamp = timestamp,
            updateType = 1 // Default to Teaching Wing for CreateUpdateActivity
        )
        
        // Store the update in Firestore
        db.collection("updates")
            .add(update)
            .addOnSuccessListener { documentRef ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Update published successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.postUpdateButton.isEnabled = true
                Log.e(TAG, "Error posting update: ${e.message ?: "Unknown error"}")
                Toast.makeText(this, "Failed to publish update", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun getDocumentName(uri: Uri): String {
        var name = "document"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    name = it.getString(displayNameIndex)
                }
            }
        }
        return name
    }
} 