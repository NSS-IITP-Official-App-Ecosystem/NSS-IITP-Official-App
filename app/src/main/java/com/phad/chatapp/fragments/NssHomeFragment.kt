package com.phad.chatapp.fragments

import com.phad.chatapp.activities.ChatActivity

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

import com.phad.chatapp.MainActivity
import com.phad.chatapp.R
import com.phad.chatapp.utils.DriveServiceHelper
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.adapters.UpdateCardAdapter
import com.phad.chatapp.models.Update
import java.io.FileNotFoundException
import java.util.Calendar
import java.util.UUID
import com.google.firebase.firestore.FieldValue
import com.phad.chatapp.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.phad.chatapp.ui.home.HomeScreen
import com.phad.chatapp.ui.home.HomeUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import android.content.SharedPreferences

class NssHomeFragment : Fragment() {
    private val TAG = "NssHomeFragment"
    
    private lateinit var sessionManager: SessionManager
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var driveServiceHelper: DriveServiceHelper
    private lateinit var sharedPreferences: SharedPreferences
    
    // Caching system for updates
    private data class CachedUpdate(
        val updates: List<Update>,
        val timestamp: Long
    )
    
    private var updateCache: CachedUpdate? = null
    private val CACHE_TTL = 5 * 60 * 1000L // 5 minutes
    private val CACHE_KEY_LAST_REFRESH = "last_nss_update_refresh"
    
    // Create update dialog
    private var createUpdateDialog: Dialog? = null
    private var selectedImageUri: Uri? = null
    private var selectedDocumentUri: Uri? = null
    
    // Image picker launcher
    private val imagePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                showSelectedImage(uri)
            }
        }
    }
    
    // Document picker launcher
    private val documentPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedDocumentUri = uri
                showSelectedDocument(uri)
            }
        }
    }
    
    private val _uiState = MutableStateFlow(HomeUiState(isNssInterface = true))
    private val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply { 
            setContent {
                val state by uiState.collectAsState()
                HomeScreen(
                    state = state,
                    onChatbotClick = {
                        val intent = Intent(requireContext(), com.phad.chatapp.features.home.faqs.ui.FaqActivity::class.java)
                        startActivity(intent)
                    },
                    onAddUpdateClick = { showUpdateOptionsDialog() },
                    onUpdateClick = { update ->
                        val intent = Intent(requireContext(), com.phad.chatapp.activities.UpdateDetailActivity::class.java)
                        intent.putExtra(com.phad.chatapp.activities.UpdateDetailActivity.EXTRA_UPDATE, update)
                        startActivity(intent)
                    }
                )
            }
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        sessionManager = SessionManager(requireContext())
        driveServiceHelper = DriveServiceHelper.getInstance(requireContext())
        sharedPreferences = requireContext().getSharedPreferences("nss_update_cache", android.content.Context.MODE_PRIVATE)
        
        loadGreetingAndNextClass()
        loadUpdatesIfNeeded()
    }
    
    private fun loadGreetingAndNextClass() {
        val greeting = getGreetingBasedOnTime()
        val userType = sessionManager.fetchUserType()
        val rollNumber = sessionManager.fetchUserId()
        
        // Load fresh user data from users collection
        loadUserDataFromFirestore(greeting, userType, rollNumber)
    }
    
    private fun loadUserDataFromFirestore(greeting: String, userType: String, rollNumber: String) {
        val db = FirebaseFirestore.getInstance()
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading user data for rollNumber: $rollNumber")
                
                if (rollNumber.isEmpty()) {
                    Log.w(TAG, "Roll number is empty, skipping Firestore user data fetch")
                    // Fallback to defaults
                    _uiState.update {
                        it.copy(
                            greeting = greeting,
                            userName = "User",
                            isAdmin = userType.equals("Admin", ignoreCase = true),
                            isNssInterface = true
                        )
                    }
                    return@launch
                }

                val userDoc = db.collection("users").document(rollNumber).get().await()
                
                val userName = if (userDoc.exists()) {
                    val name = userDoc.getString("name") ?: "User"
                    Log.d(TAG, "Found user name: '$name'")
                    name
                } else {
                    Log.w(TAG, "User document not found, using fallback")
                    "User"
                }
                
                _uiState.update {
                    it.copy(
                        greeting = greeting,
                        userName = userName,
                        isAdmin = userType.equals("Admin", ignoreCase = true),
                        isNssInterface = true
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user data from Firestore", e)
                // Fallback to session data
                val userName = sessionManager.fetchUserName().ifEmpty { "User" }
                _uiState.update {
                    it.copy(
                        greeting = greeting,
                        userName = userName,
                        isAdmin = userType.equals("Admin", ignoreCase = true),
                        isNssInterface = true
                    )
                }
            }
        }
    }
    
    private fun getGreetingBasedOnTime(): String {
        val calendar = Calendar.getInstance()
        return when (calendar.get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }
    
    private fun loadUpdatesIfNeeded() {
        if (shouldRefreshUpdates()) {
            Log.d(TAG, "NssHomeFragment - Cache expired or empty, loading fresh updates...")
            loadUpdates()
        } else {
            Log.d(TAG, "NssHomeFragment - Using cached updates")
            updateCache?.let { cached ->
                _uiState.update { it.copy(updates = cached.updates) }
            }
        }
    }
    
    private fun shouldRefreshUpdates(): Boolean {
        val lastRefresh = sharedPreferences.getLong(CACHE_KEY_LAST_REFRESH, 0)
        val now = System.currentTimeMillis()
        val isExpired = (now - lastRefresh) > CACHE_TTL
        val isCacheEmpty = updateCache == null
        
        Log.d(TAG, "NssHomeFragment - Cache check: lastRefresh=$lastRefresh, now=$now, expired=$isExpired, empty=$isCacheEmpty")
        return isExpired || isCacheEmpty
    }
    
    private fun loadUpdates() {
        Log.d(TAG, "NssHomeFragment - Starting to load updates...")
        db.collection("nss_updates")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20) // Increased for Reel load
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "NssHomeFragment - Successfully loaded ${documents.size()} documents from Firestore")
                
                val updates = documents.mapNotNull { doc ->
                    try {
                        val id = doc.getString("id") ?: doc.id
                        val title = doc.getString("title") // Nullable
                        val content = doc.getString("content") // Nullable
                        val authorId = doc.getString("authorId") ?: ""
                        val authorName = doc.getString("authorName") ?: "Unknown"
                        val authorImageUrl = doc.getString("authorImageUrl")
                        val externalLink = doc.getString("externalLink")
                        val documentName = doc.getString("documentName")
                        val documentUrl = doc.getString("documentUrl")
                        val imageName = doc.getString("imageName")
                        
                        // Handle potentially mixed types for mediaUrl
                        val mediaUrl = doc.getString("mediaUrl")
                        val imageUrl = doc.getString("imageUrl")
                        
                        // Robust timestamp handling
                        val timestampObj = doc.get("timestamp")
                        val timestamp: Long = when (timestampObj) {
                            is Long -> timestampObj
                            is String -> timestampObj.toLongOrNull() ?: 0L
                            else -> 0L
                        }
                        
                        // Robust updateType handling
                        val updateTypeObj = doc.get("updateType")
                        val updateType: Int = when (updateTypeObj) {
                            is Long -> updateTypeObj.toInt()
                            is Int -> updateTypeObj
                            is String -> updateTypeObj.toIntOrNull() ?: 1
                            else -> 1
                        }
                        
                        val isVideo = doc.getBoolean("isVideo") ?: false
                        val instagramUrl = doc.getString("instagramUrl")

                        var update = Update(
                            id = id,
                            authorId = authorId,
                            authorName = authorName,
                            authorImageUrl = authorImageUrl,
                            title = title,
                            content = content,
                            externalLink = externalLink,
                            documentName = documentName,
                            documentUrl = documentUrl,
                            imageName = imageName,
                            imageUrl = imageUrl,
                            mediaUrl = mediaUrl,
                            isVideo = isVideo,
                            instagramUrl = instagramUrl,
                            timestamp = timestamp,
                            updateType = updateType
                        )
                        
                        // Process Drive URL
                        val finalImageUrl = update.mediaUrl ?: update.imageUrl
                        if (!finalImageUrl.isNullOrEmpty()) {
                            update = update.copy(imageUrl = driveServiceHelper.processGoogleDriveUrl(finalImageUrl))
                        }
                        
                        update
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing individual update document ${doc.id}", e)
                        null
                    }
                }.filter { update ->
                    // Filter out truly invalid updates (e.g. no title AND no content AND no media)
                    // But we allow empty titles now, so let's just check if it's not effectively empty
                    val hasContent = !update.content.isNullOrEmpty() || 
                                     !update.title.isNullOrEmpty() || 
                                     !update.mediaUrl.isNullOrEmpty() || 
                                     !update.instagramUrl.isNullOrEmpty()
                    hasContent
                }
                
                Log.d(TAG, "NssHomeFragment - After manual parsing, ${updates.size} valid updates remaining")
                
                // Update cache
                updateCache = CachedUpdate(updates, System.currentTimeMillis())
                sharedPreferences.edit().putLong(CACHE_KEY_LAST_REFRESH, System.currentTimeMillis()).apply()
                
                _uiState.update { it.copy(updates = updates) }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading updates", e)
                Toast.makeText(context, "Failed to load updates.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToQRAttendance() {
        val userType = sessionManager.fetchUserType()
        val currentInterface = sessionManager.getLastInterfaceChoice() ?: "NSS"

        Log.d(TAG, "QR Attendance navigation - UserType: '$userType', Interface: '$currentInterface'")

        // Allow admins regardless of interface
        if (userType.equals("Admin", ignoreCase = true)) {
            Log.d(TAG, "Admin user accessing QR attendance management")
            findNavController().navigate(R.id.nssQRAttendanceFragment)
        } else if (userType.equals("Student", ignoreCase = true)) {
            Log.d(TAG, "Student user accessing QR scan")
            findNavController().navigate(R.id.nssQRScanFragment)
        } else {
            Log.w(TAG, "Access denied - UserType: '$userType', Interface: '$currentInterface'")
            Toast.makeText(requireContext(), "Access denied. Only NSS users can access QR attendance.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCreateUpdateDialog() {
        // Initialize the dialog
        createUpdateDialog = Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_create_update)
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Log auth status and user information for debugging
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "User is authenticated: ${currentUser.uid}, Email: ${currentUser.email}")
        } else {
            Log.e(TAG, "User is not authenticated. This may cause Google Drive upload failures.")
        }

        // Reset selected media
        selectedImageUri = null
        selectedDocumentUri = null

        // Find views in the dialog
        val dialog = createUpdateDialog ?: return
        val updateContentInput = dialog.findViewById<EditText>(R.id.updateContentInput)
        val updateTitleInput = dialog.findViewById<EditText>(R.id.updateTitleInput)
        val updateLinkInput = dialog.findViewById<EditText>(R.id.updateLinkInput)
        val instagramLinkInput = dialog.findViewById<EditText>(R.id.instagramLinkInput)
        val attachImageButton = dialog.findViewById<ImageButton>(R.id.attachImageButton)
        val attachDocumentButton = dialog.findViewById<ImageButton>(R.id.attachDocumentButton)
        val cancelButton = dialog.findViewById<Button>(R.id.cancelButton)
        val postUpdateButton = dialog.findViewById<Button>(R.id.postUpdateButton)

        // Add checkbox for cross-posting to Teaching Wing
        val crossPostCheckbox = dialog.findViewById<android.widget.CheckBox>(R.id.crossPostCheckbox)
        crossPostCheckbox?.visibility = View.VISIBLE
        crossPostCheckbox?.text = "Also post to Teaching Wing interface"

        // Hide preview containers initially
        val mediaPreviewContainer = dialog.findViewById<FrameLayout>(R.id.mediaPreviewContainer)
        val documentPreviewContainer = dialog.findViewById<LinearLayout>(R.id.documentPreviewContainer)
        mediaPreviewContainer.visibility = View.GONE
        documentPreviewContainer.visibility = View.GONE

        // Set up attach image button
        attachImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePicker.launch(intent)
        }

        // Set up attach document button
        attachDocumentButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            documentPicker.launch(intent)
        }

        // Set up remove media button
        val removeMediaButton = dialog.findViewById<ImageButton>(R.id.removeMediaButton)
        removeMediaButton.setOnClickListener {
            selectedImageUri = null
            mediaPreviewContainer.visibility = View.GONE
        }

        // Set up remove document button
        val removeDocumentButton = dialog.findViewById<ImageButton>(R.id.removeDocumentButton)
        removeDocumentButton.setOnClickListener {
            selectedDocumentUri = null
            documentPreviewContainer.visibility = View.GONE
        }

        // Set up cancel button
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        // Set up post update button
        postUpdateButton.setOnClickListener {
            val content = updateContentInput.text.toString().trim()
            val title = updateTitleInput.text.toString().trim()
            
            if (content.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter update content", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter update title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Show loading indicator
            dialog.findViewById<Button>(R.id.postUpdateButton).isEnabled = false
            dialog.findViewById<Button>(R.id.cancelButton).isEnabled = false
            
            // Create update data
            val link = updateLinkInput.text.toString().trim()
            val instagramLink = instagramLinkInput.text.toString().trim()
            val crossPost = crossPostCheckbox?.isChecked ?: false

            // Validate Instagram URL if present
            var instagramUrl: String? = null
            if (instagramLink.isNotEmpty()) {
                if (validateInstagramUrl(instagramLink)) {
                    instagramUrl = instagramLink
                } else {
                    Toast.makeText(requireContext(), "Invalid Instagram URL. Use format: instagram.com/reel/ID or instagram.com/p/ID", Toast.LENGTH_SHORT).show()
                    dialog.findViewById<Button>(R.id.postUpdateButton).isEnabled = true
                    dialog.findViewById<Button>(R.id.cancelButton).isEnabled = true
                    return@setOnClickListener
                }
            }

            // Upload media if selected
            if (selectedImageUri != null) {
                uploadMedia(selectedImageUri!!) { mediaUrl: String ->
                    // Upload document if selected
                    if (selectedDocumentUri != null) {
                        uploadDocument(selectedDocumentUri!!) { documentUrl: String, documentName: String ->
                            createUpdate(content, title, link, mediaUrl, documentUrl, documentName, false, null, crossPost)
                        }
                    } else {
                        // If both image and Instagram link are present, prioritize image as background or similar? 
                        // For now we pass both, logic in ReelItem handles priority (Instagram > Media > Image)
                        createUpdate(content, title, link, mediaUrl, null, null, false, instagramUrl, crossPost)
                    }
                }
            } else if (selectedDocumentUri != null) {
                uploadDocument(selectedDocumentUri!!) { documentUrl: String, documentName: String ->
                    createUpdate(content, title, link, null, documentUrl, documentName, false, instagramUrl, crossPost)
                }
            } else {
                // If only Instagram link is there
                val isVideo = instagramUrl != null
                createUpdate(content, title, link, null, null, null, isVideo, instagramUrl, crossPost)
            }
        }

        dialog.show()
    }

    private fun showUpdateOptionsDialog() {
        val options = arrayOf("Create New Update", "Delete Update")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("What would you like to do?")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showCreateUpdateDialog()
                    1 -> showDeleteUpdateDialog()
                }
            }
            .show()
    }

    private fun showSelectedImage(uri: Uri) {
        val mediaPreviewContainer = createUpdateDialog?.findViewById<FrameLayout>(R.id.mediaPreviewContainer)
        val mediaPreview = createUpdateDialog?.findViewById<ImageView>(R.id.mediaPreview)

        mediaPreviewContainer?.visibility = View.VISIBLE

        mediaPreview?.let {
            try {
                Glide.with(requireContext())
                    .load(uri)
                    .centerCrop()
                    .into(it)
            } catch (e: FileNotFoundException) {
                Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                mediaPreviewContainer?.visibility = View.GONE
            }
        }
    }

    private fun showSelectedDocument(uri: Uri) {
        val documentPreviewContainer = createUpdateDialog?.findViewById<LinearLayout>(R.id.documentPreviewContainer)
        val documentNameText = createUpdateDialog?.findViewById<TextView>(R.id.documentNameText)

        // Get document name from URI
        val documentName = getDocumentName(uri)
        documentNameText?.text = documentName

        documentPreviewContainer?.visibility = View.VISIBLE
    }

    private fun getDocumentName(uri: Uri): String {
        var fileName = "document"

        context?.contentResolver?.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    fileName = cursor.getString(displayNameIndex)
                }
            }
        }

        return fileName
    }

    private fun uploadMedia(uri: Uri, onComplete: (String) -> Unit) {
        try {
            // Validate the URI is accessible
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            inputStream?.close()

            // Show a loading indication
            Toast.makeText(requireContext(), "Uploading image...", Toast.LENGTH_SHORT).show()

            // Generate a unique filename for the image
            val filename = "nss_update_image_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"

            // Log the upload attempt for debugging
            Log.d(TAG, "Uploading image to Google Drive: $filename")

            // Upload to Google Drive instead of Firebase Storage
            driveServiceHelper.uploadFileToDrive(uri, filename, "image/jpeg") { success, driveFileId, webViewLink ->
                if (success && webViewLink != null) {
                    // Convert to a direct media URL for better Glide compatibility
                    val directMediaUrl = driveServiceHelper.getDirectMediaUrl(webViewLink, true)

                    Log.d(TAG, "Upload completed. Drive ID: $driveFileId")
                    Log.d(TAG, "Original URL: $webViewLink")
                    Log.d(TAG, "Direct media URL: $directMediaUrl")

                    onComplete(directMediaUrl)
                } else {
                    val errorMsg = "Failed to upload to Google Drive"
                    Log.e(TAG, errorMsg)
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
                        createUpdateDialog?.findViewById<Button>(R.id.postUpdateButton)?.isEnabled = true
                        createUpdateDialog?.findViewById<Button>(R.id.cancelButton)?.isEnabled = true
                    }
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Unknown error"
            Log.e(TAG, "Failed to access file: $errorMsg", e)
            Toast.makeText(requireContext(), "Error accessing the file: $errorMsg", Toast.LENGTH_SHORT).show()
            createUpdateDialog?.findViewById<Button>(R.id.postUpdateButton)?.isEnabled = true
            createUpdateDialog?.findViewById<Button>(R.id.cancelButton)?.isEnabled = true
        }
    }

    private fun uploadDocument(uri: Uri, onComplete: (String, String) -> Unit) {
        try {
            // Validate the URI is accessible
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            inputStream?.close()

            // Show a loading indication
            Toast.makeText(requireContext(), "Uploading document...", Toast.LENGTH_SHORT).show()

            // Get document name from URI
            val documentName = getDocumentName(uri)

            // Get MIME type of the document
            val mimeType = requireContext().contentResolver.getType(uri) ?: "application/octet-stream"

            // Generate a unique filename for the document
            val filename = "nss_update_doc_${System.currentTimeMillis()}_${UUID.randomUUID()}_$documentName"

            // Log the upload attempt for debugging
            Log.d(TAG, "Uploading document to Google Drive: $filename (${mimeType})")

            // Upload to Google Drive instead of Firebase Storage
            driveServiceHelper.uploadFileToDrive(uri, filename, mimeType) { success, driveFileId, webViewLink ->
                if (success && driveFileId != null) {
                    // Use the standard file view URL format that works without Google auth
                    // This is the format that's working in the group chat
                    val directFileUrl = "https://drive.google.com/file/d/${driveFileId}/view?usp=sharing"

                    Log.d(TAG, "Upload completed. Drive ID: $driveFileId")
                    Log.d(TAG, "Original URL: $webViewLink")
                    Log.d(TAG, "Direct file URL: $directFileUrl")

                    onComplete(directFileUrl, documentName)
                } else {
                    val errorMsg = "Failed to upload to Google Drive"
                    Log.e(TAG, errorMsg)
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
                        createUpdateDialog?.findViewById<Button>(R.id.postUpdateButton)?.isEnabled = true
                        createUpdateDialog?.findViewById<Button>(R.id.cancelButton)?.isEnabled = true
                    }
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Unknown error"
            Log.e(TAG, "Failed to access file: $errorMsg", e)
            Toast.makeText(requireContext(), "Error accessing the file: $errorMsg", Toast.LENGTH_SHORT).show()
            createUpdateDialog?.findViewById<Button>(R.id.postUpdateButton)?.isEnabled = true
            createUpdateDialog?.findViewById<Button>(R.id.cancelButton)?.isEnabled = true
        }
    }

    private fun validateInstagramUrl(url: String): Boolean {
        // Validates Instagram Reel and Post URLs
        // Formats: https://www.instagram.com/reel/ID/ or https://www.instagram.com/p/ID/
        val pattern = "^https?://(www\\.)?instagram\\.com/(p|reel)/([A-Za-z0-9_-]+)/?.*$"
        val compiledPattern = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE)
        val matcher = compiledPattern.matcher(url)
        return matcher.matches()
    }
    
    private fun createUpdate(
        content: String,
        title: String?,
        link: String?,
        mediaUrl: String?,
        documentUrl: String?,
        documentName: String?,
        isVideo: Boolean = false,
        instagramUrl: String? = null,
        crossPost: Boolean = false
    ) {
        val userId = auth.currentUser?.uid ?: return
        val authorName = sessionManager.fetchUserName() ?: "Admin"
        val authorImageUrl = auth.currentUser?.photoUrl?.toString()

        // Generate a timestamp for the update
        val timestamp = System.currentTimeMillis()

        // Get the user's roll number - use a default if not available
        val userRollNumber = sessionManager.fetchRollNumber() ?: userId.takeLast(6)

        // Extract only the numeric part of the roll number (removing any non-digit characters)
        val numericRollNumber = userRollNumber.filter { it.isDigit() }

        // Create a custom document ID combining timestamp and roll number (without underscore)
        val customDocId = "${timestamp}${numericRollNumber}"

        // Determine updateType based on cross-post setting
        val updateType = if (crossPost) 3 else 2 // 3=Both, 2=NSS only

        // Create the update object
        val update = Update(
            id = customDocId,
            authorId = userRollNumber,
            authorName = authorName,
            authorImageUrl = authorImageUrl,
            title = title,
            content = content,
            externalLink = if (link.isNullOrEmpty()) null else link,
            documentName = documentName,
            documentUrl = documentUrl,
            imageName = if (mediaUrl != null && !isVideo) "image_${System.currentTimeMillis()}.jpg" else null,
            imageUrl = if (!isVideo) mediaUrl else null,
            mediaUrl = mediaUrl, // Keep for backward compatibility
            isVideo = isVideo,
            instagramUrl = instagramUrl,
            timestamp = timestamp,
            updateType = updateType
        )

        // Save to NSS updates collection first
        db.collection("nss_updates").document(customDocId)
            .set(update)
            .addOnSuccessListener {
                // If cross-post is enabled, also save to the regular updates collection
                if (crossPost) {
                    db.collection("updates").document(customDocId)
                        .set(update)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Update posted to both NSS and Teaching Wing!", Toast.LENGTH_SHORT).show()
                            createUpdateDialog?.dismiss()

                            // Reload updates and clear cache to show new update
                            updateCache = null
                            loadUpdates()

                            // Send notification to all users
                            sendUpdateNotification(update)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(requireContext(), "Posted to NSS but failed to cross-post to Teaching Wing: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            createUpdateDialog?.dismiss()
                            updateCache = null
                            loadUpdates()
                            sendUpdateNotification(update)
                        }
                } else {
                    Toast.makeText(requireContext(), "NSS Update posted", Toast.LENGTH_SHORT).show()
                    createUpdateDialog?.dismiss()

                    // Reload updates and clear cache to show new update
                    updateCache = null
                    loadUpdates()

                    // Send notification to all users
                    sendUpdateNotification(update)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to post update: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                createUpdateDialog?.findViewById<Button>(R.id.postUpdateButton)?.isEnabled = true
                createUpdateDialog?.findViewById<Button>(R.id.cancelButton)?.isEnabled = true
            }
    }

    private fun sendUpdateNotification(update: Update) {
        Log.d(TAG, "Preparing to send update notification for update: ${update.id} with updateType: ${update.updateType}")

        try {
            val notificationHelper = NotificationHelper(requireContext())
            val currentUserId = sessionManager.fetchRollNumber() ?: auth.currentUser?.uid ?: ""

            // Create a descriptive message that includes update info
            // Create a descriptive message that includes update info
            val title = update.title ?: "New Update" // Safe default
            val content = update.content ?: ""
            val message = "$title: ${content.take(100)}${if (content.length > 100) "..." else ""}"

            // If there's a document, mention it in the notification
            val fullMessage = if (update.documentUrl != null) {
                "$message [Contains document]"
            } else {
                message
            }

            Log.d(TAG, "Update notification message: $fullMessage")

            // Get targeted users based on updateType
            val targetedUserIds = mutableListOf<String>()

            when (update.updateType) {
                2 -> {
                    // NSS only updates - notify users with Teaching_wing = false
                    // Get students from Student collection
                    db.collection("Student")
                        .whereEqualTo("Teaching_wing", false)
                        .get()
                        .addOnSuccessListener { studentSnapshot ->
                            val studentIds = studentSnapshot.documents.mapNotNull { doc ->
                                val rollNo = doc.id
                                if (rollNo != currentUserId) rollNo else null // Exclude current user
                            }
                            targetedUserIds.addAll(studentIds)

                            // Get NSS Admins with Teaching_wing = false
                            db.collection("NSS_ADMINS")
                                .whereEqualTo("Teaching_wing", false)
                                .get()
                                .addOnSuccessListener { adminSnapshot ->
                                    val adminIds = adminSnapshot.documents.mapNotNull { doc ->
                                        val rollNo = doc.getString("Roll_Number") ?: doc.id
                                        if (rollNo != currentUserId) rollNo else null // Exclude current user
                                    }
                                    targetedUserIds.addAll(adminIds)

                                    Log.d(TAG, "Found ${targetedUserIds.size} NSS users to notify about update ${update.id}")
                                    sendNotificationToUsers(update, title, fullMessage, targetedUserIds, notificationHelper)
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Error fetching NSS_ADMINS for notification: ${e.message}", e)
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error fetching Student collection for NSS notification: ${e.message}", e)
                        }
                }
                3 -> {
                    // Both interfaces - notify all users
                    // Get all students
                    db.collection("Student")
                        .get()
                        .addOnSuccessListener { studentSnapshot ->
                            val studentIds = studentSnapshot.documents.mapNotNull { doc ->
                                val rollNo = doc.id
                                if (rollNo != currentUserId) rollNo else null // Exclude current user
                            }
                            targetedUserIds.addAll(studentIds)

                            // Get all NSS Admins
                            db.collection("NSS_ADMINS")
                                .get()
                                .addOnSuccessListener { adminSnapshot ->
                                    val adminIds = adminSnapshot.documents.mapNotNull { doc ->
                                        val rollNo = doc.getString("Roll_Number") ?: doc.id
                                        if (rollNo != currentUserId) rollNo else null // Exclude current user
                                    }
                                    targetedUserIds.addAll(adminIds)

                                    Log.d(TAG, "Found ${targetedUserIds.size} users (all) to notify about update ${update.id}")
                                    sendNotificationToUsers(update, title, fullMessage, targetedUserIds, notificationHelper)
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Error fetching NSS_ADMINS for all notification: ${e.message}", e)
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error fetching Student collection for all notification: ${e.message}", e)
                        }
                }
                else -> {
                    Log.w(TAG, "Unknown updateType: ${update.updateType}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error creating update notification: ${e.message}", e)
            Toast.makeText(
                requireContext(),
                "Failed to send notifications: ${e.localizedMessage}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun sendNotificationToUsers(
        update: Update,
        title: String,
        fullMessage: String,
        targetedUserIds: List<String>,
        notificationHelper: NotificationHelper
    ) {
        if (targetedUserIds.isNotEmpty()) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    Log.d(TAG, "Launching coroutine to send update notification to ${targetedUserIds.size} users")

                    notificationHelper.sendUpdateNotification(
                        updateId = update.id,
                        updateTitle = title,
                        updateMessage = fullMessage,
                        senderRollNumber = update.authorId,
                        senderName = update.authorName,
                        allUserIds = targetedUserIds
                    )

                    Log.d(TAG, "Update notification successfully sent via NotificationHelper")
                } catch (e: Exception) {
                    Log.e(TAG, "Error in coroutine sending notification: ${e.message}", e)
                }
            }
        } else {
            Log.w(TAG, "No users found to notify for updateType: ${update.updateType}")
        }
    }

    override fun onResume() {
        super.onResume()
        // Only reload updates if the view exists and cache is expired
        if (createUpdateDialog != null) {
            loadUpdatesIfNeeded()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        createUpdateDialog = null
    }

    private fun showDeleteUpdateDialog() {
        // Load existing updates for deletion from NSS updates collection
        db.collection("nss_updates")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20) // Show last 20 updates
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(requireContext(), "No updates found to delete", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                
                val updates = documents.toObjects(Update::class.java)
                val updateTitles = updates.map { update ->
                    val title = update.title ?: "Untitled"
                    val content = (update.content ?: "").take(50)
                    val timestamp = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(update.timestamp))
                    "$title: $content... (Posted: $timestamp)"
                }.toTypedArray()
                
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Select Update to Delete")
                    .setItems(updateTitles) { _, which ->
                        val selectedUpdate = updates[which]
                        showDeleteConfirmationDialog(selectedUpdate)
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading updates for deletion", e)
                Toast.makeText(requireContext(), "Failed to load updates: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun showDeleteConfirmationDialog(update: Update) {
        val message = "Are you sure you want to delete this update?\n\n" +
                "Title: ${update.title ?: "Untitled"}\n" +
                "Content: ${(update.content ?: "").take(100)}...\n" +
                "Posted by: ${update.authorName}\n" +
                "Posted on: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(update.timestamp))}"
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirm Deletion")
            .setMessage(message)
            .setPositiveButton("Delete") { dialog, _ ->
                deleteUpdate(update)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun deleteUpdate(update: Update) {
        // Delete from NSS updates collection first
        db.collection("nss_updates").document(update.id)
            .delete()
            .addOnSuccessListener {
                // If this was a cross-posted update (updateType = 3), also delete from the regular updates collection
                if (update.updateType == 3) {
                    db.collection("updates").document(update.id)
                        .delete()
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Update deleted from both NSS and Teaching Wing", Toast.LENGTH_SHORT).show()
                            updateCache = null
                            loadUpdates()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error deleting cross-posted update: ${e.localizedMessage}", e)
                            Toast.makeText(requireContext(), "Update deleted from NSS but failed to delete from Teaching Wing", Toast.LENGTH_SHORT).show()
                            updateCache = null
                            loadUpdates()
                        }
                } else {
                    Toast.makeText(requireContext(), "Update deleted successfully", Toast.LENGTH_SHORT).show()
                    updateCache = null
                    loadUpdates()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error deleting update", e)
                Toast.makeText(requireContext(), "Failed to delete update: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
} 