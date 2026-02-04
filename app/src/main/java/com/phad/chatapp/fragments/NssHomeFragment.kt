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
import com.phad.chatapp.utils.CloudinaryHelper
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
import kotlinx.coroutines.withContext
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
    private lateinit var cloudinaryHelper: CloudinaryHelper
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
    private var selectedImageUris: MutableList<Uri> = mutableListOf()
    private var selectedDocumentUris: MutableList<Uri> = mutableListOf()
    private var externalLinks: MutableList<String> = mutableListOf()
    
    // Image picker launcher - supports multiple selection
    private val imagePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                // Handle multiple images
                data.clipData?.let { clipData ->
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i).uri?.let { uri ->
                            selectedImageUris.add(uri)
                        }
                    }
                } ?: data.data?.let { uri ->
                    // Single image selected
                    selectedImageUris.add(uri)
                }
                showSelectedImages()
            }
        }
    }
    
    // Document picker launcher - supports multiple selection
    private val documentPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                // Handle multiple documents
                data.clipData?.let { clipData ->
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i).uri?.let { uri ->
                            selectedDocumentUris.add(uri)
                        }
                    }
                } ?: data.data?.let { uri ->
                    // Single document selected
                    selectedDocumentUris.add(uri)
                }
                showSelectedDocuments()
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
                    onAddUpdateClick = { showCreateUpdateDialog() }, // Fix naming if needed. The internal function is showCreateUpdateDialog
                    onUpdateClick = { update ->
                        val intent = Intent(requireContext(), com.phad.chatapp.activities.UpdateDetailActivity::class.java)
                        intent.putExtra(com.phad.chatapp.activities.UpdateDetailActivity.EXTRA_UPDATE, update)
                        startActivity(intent)
                    },
                    onEditPost = { update -> showCreateUpdateDialog(update) },
                    onDeletePost = { update -> confirmDeletePost(update) },
                    onBatchDeleteClick = { showBatchDeleteDialog() },
                    onRefresh = { 
                        refreshUpdates()
                    }
                )
            }
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        sessionManager = SessionManager(requireContext())
        cloudinaryHelper = CloudinaryHelper.getInstance(requireContext())
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
    
    
    private fun refreshUpdates() {
        Log.d(TAG, "NssHomeFragment - Manual refresh triggered")
        // Set refreshing state
        _uiState.update { it.copy(isRefreshing = true) }
        
        // Clear cache to force fresh load
        updateCache = null
        sharedPreferences.edit().putLong(CACHE_KEY_LAST_REFRESH, 0).apply()
        
        // Load fresh updates
        loadUpdates()
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
                        val postType = doc.getString("postType") ?: "text"
                        val hasExternalLink = doc.getBoolean("hasExternalLink") ?: false
                        
                        // Read new list fields
                        @Suppress("UNCHECKED_CAST")
                        val imageUrls = doc.get("imageUrls") as? List<String>
                        @Suppress("UNCHECKED_CAST")
                        val documentUrls = doc.get("documentUrls") as? List<String>
                        @Suppress("UNCHECKED_CAST")
                        val documentNames = doc.get("documentNames") as? List<String>
                        @Suppress("UNCHECKED_CAST")
                        val externalLinks = doc.get("externalLinks") as? List<String>

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
                            postType = postType,
                            hasExternalLink = hasExternalLink,
                            timestamp = timestamp,
                            updateType = updateType,
                            // New list fields
                            imageUrls = imageUrls,
                            documentUrls = documentUrls,
                            documentNames = documentNames,
                            externalLinks = externalLinks
                        )
                        
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
                
                _uiState.update { it.copy(updates = updates, isRefreshing = false) }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading updates", e)
                Toast.makeText(context, "Failed to load updates.", Toast.LENGTH_SHORT).show()
                _uiState.update { it.copy(isRefreshing = false) }
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

    private fun showCreateUpdateDialog(existingUpdate: Update? = null) {
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
        selectedImageUris.clear()
        selectedDocumentUris.clear()
        externalLinks.clear()

        // Find views in the dialog
        val dialog = createUpdateDialog ?: return
        val updateContentInput = dialog.findViewById<EditText>(R.id.updateContentInput)
        val updateTitleInput = dialog.findViewById<EditText>(R.id.updateTitleInput)
        val updateLinkInput = dialog.findViewById<EditText>(R.id.updateLinkInput)
        val instagramLinkInput = dialog.findViewById<EditText>(R.id.instagramLinkInput)
        val attachImageButton = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.attachImageButton)
        val attachDocumentButton = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.attachDocumentButton)
        val cancelButton = dialog.findViewById<Button>(R.id.cancelButton)
        val postUpdateButton = dialog.findViewById<Button>(R.id.postUpdateButton)

        // Add checkbox for cross-posting to Teaching Wing
        val crossPostCheckbox = dialog.findViewById<android.widget.CheckBox>(R.id.crossPostCheckbox)
        crossPostCheckbox?.visibility = View.VISIBLE
        crossPostCheckbox?.text = "Also post to Teaching Wing interface"

        // Post Type Selector Buttons
        val reelPostButton = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.reelPostButton)
        val textPostButton = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.textPostButton)
        
        // Containers for conditional fields
        val reelPostFields = dialog.findViewById<LinearLayout>(R.id.reelPostFields)
        val textPostFields = dialog.findViewById<LinearLayout>(R.id.textPostFields)
        
        // Variable to track current post type
        var currentPostType = "text" // Default to text post
        
        // Helper to update UI based on type
        fun updatePostTypeUI(type: String) {
            currentPostType = type
            val context = requireContext()
            val activeColor = androidx.core.content.ContextCompat.getColor(context, R.color.purple_500)
            val inactiveColor = android.graphics.Color.parseColor("#F5F5F5")
            val activeText = android.graphics.Color.WHITE
            val inactiveText = android.graphics.Color.parseColor("#333333")

            if (type == "reel") {
                // Reel Active
                reelPostButton.backgroundTintList = android.content.res.ColorStateList.valueOf(activeColor)
                reelPostButton.setTextColor(activeText)
                reelPostButton.iconTint = android.content.res.ColorStateList.valueOf(activeText)
                
                textPostButton.backgroundTintList = android.content.res.ColorStateList.valueOf(inactiveColor)
                textPostButton.setTextColor(inactiveText)
                textPostButton.iconTint = android.content.res.ColorStateList.valueOf(inactiveText)
                
                reelPostFields?.visibility = View.VISIBLE
                textPostFields?.visibility = View.GONE
                updateContentInput?.isEnabled = false
                updateLinkInput?.isEnabled = false
            } else {
                // Text Active
                textPostButton.backgroundTintList = android.content.res.ColorStateList.valueOf(activeColor)
                textPostButton.setTextColor(activeText)
                textPostButton.iconTint = android.content.res.ColorStateList.valueOf(activeText)
                
                reelPostButton.backgroundTintList = android.content.res.ColorStateList.valueOf(inactiveColor)
                reelPostButton.setTextColor(inactiveText)
                reelPostButton.iconTint = android.content.res.ColorStateList.valueOf(inactiveText)
                
                reelPostFields?.visibility = View.GONE
                textPostFields?.visibility = View.VISIBLE
                updateContentInput?.isEnabled = true
                updateLinkInput?.isEnabled = true
            }
        }

        // Set listeners
        reelPostButton.setOnClickListener { updatePostTypeUI("reel") }
        textPostButton.setOnClickListener { updatePostTypeUI("text") }
        
        // Set initial state
        updatePostTypeUI("text")

        // Hide preview containers initially
        val mediaPreviewContainer = dialog.findViewById<FrameLayout>(R.id.mediaPreviewContainer)
        val documentPreviewContainer = dialog.findViewById<LinearLayout>(R.id.documentPreviewContainer)
        mediaPreviewContainer.visibility = View.GONE
        documentPreviewContainer.visibility = View.GONE

        // Set up attach image button - allow multiple selection
        attachImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            imagePicker.launch(intent)
        }

        // Set up attach document button - allow multiple selection
        attachDocumentButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            documentPicker.launch(intent)
        }

        // Set up remove media button
        val removeMediaButton = dialog.findViewById<ImageButton>(R.id.removeMediaButton)
        removeMediaButton.setOnClickListener {
            selectedImageUris.clear()
            mediaPreviewContainer.visibility = View.GONE
        }

        // Set up remove document button
        val removeDocumentButton = dialog.findViewById<ImageButton>(R.id.removeDocumentButton)
        removeDocumentButton.setOnClickListener {
            selectedDocumentUris.clear()
            documentPreviewContainer.visibility = View.GONE
        }

        // Pre-fill if editing
        if (existingUpdate != null) {
            updateTitleInput?.setText(existingUpdate.title)
            updateContentInput?.setText(existingUpdate.content)
            updateLinkInput?.setText(existingUpdate.externalLink)
            instagramLinkInput?.setText(existingUpdate.instagramUrl)
            crossPostCheckbox?.isChecked = (existingUpdate.updateType == 3)

            // Set Post Type
            if (existingUpdate.postType == "reel" || !existingUpdate.instagramUrl.isNullOrEmpty()) {
                updatePostTypeUI("reel")
            } else {
                updatePostTypeUI("text")
            }

            postUpdateButton.text = "Update"
        }

        // Set up cancel button
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        // Set up post update button
        postUpdateButton.setOnClickListener {
            val title = updateTitleInput.text.toString().trim()
            
            // Validation based on post type
            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Reel Post Validation
            if (currentPostType == "reel") {
                val instagramLink = instagramLinkInput.text.toString().trim()
                if (instagramLink.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter Instagram Reel/Post URL", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!validateInstagramUrl(instagramLink)) {
                    Toast.makeText(requireContext(), "Invalid Instagram URL. Use format: instagram.com/reel/ID or instagram.com/p/ID", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }
            
            // Text Post Validation
            if (currentPostType == "text") {
                val content = updateContentInput.text.toString().trim()
                if (content.isEmpty() && selectedImageUris.isEmpty() && selectedDocumentUris.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter content or attach media/document", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }
            
            // Show loading indicator
            dialog.findViewById<Button>(R.id.postUpdateButton).isEnabled = false
            dialog.findViewById<Button>(R.id.cancelButton).isEnabled = false
            
            // Prepare post data
            val content = if (currentPostType == "text") updateContentInput.text.toString().trim() else ""
            val link = if (currentPostType == "text") updateLinkInput.text.toString().trim() else ""
            val instagramLink = if (currentPostType == "reel") instagramLinkInput.text.toString().trim() else ""
            val crossPost = crossPostCheckbox?.isChecked ?: false
            
            // Detect if content or link contains URLs
            val hasExternalLink = when (currentPostType) {
                "reel" -> false // Reel posts don't have external links (Instagram is the content)
                "text" -> com.phad.chatapp.utils.PostUtils.containsUrl(content) || link.isNotEmpty()
                else -> false
            }
            
            // Set Instagram URL for reel posts
            val instagramUrl = if (currentPostType == "reel" && validateInstagramUrl(instagramLink)) {
                instagramLink
            } else {
                null
            }

            val isVideo = !instagramUrl.isNullOrEmpty()

            // Upload all media concurrently and then save
            lifecycleScope.launch {
                try {
                    // Upload all images concurrently
                    val imageUrls = if (selectedImageUris.isNotEmpty()) {
                        selectedImageUris.map { uri ->
                            withContext(Dispatchers.IO) {
                                cloudinaryHelper.uploadImage(uri, "nss_updates/images")
                            }
                        }
                    } else {
                        emptyList()
                    }
                    
                    // Upload all documents concurrently
                    val documentData = if (selectedDocumentUris.isNotEmpty()) {
                        selectedDocumentUris.map { uri ->
                            withContext(Dispatchers.IO) {
                                val url = cloudinaryHelper.uploadDocument(uri, "nss_updates/documents")
                                val name = getDocumentName(uri)
                                Pair(url, name)
                            }
                        }
                    } else {
                        emptyList()
                    }
                    
                    val documentUrls = documentData.map { it.first }
                    val documentNames = documentData.map { it.second }
                    
                    // Parse multiple links from input (comma or newline separated)
                    val links = if (link.isNotEmpty()) {
                        link.split("[,\n]".toRegex())
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    } else {
                        emptyList()
                    }
                    
                    // Create or update post
                    if (existingUpdate != null) {
                        updatePost(
                            existingUpdate, content, title,
                            imageUrls, documentUrls, documentNames, links,
                            isVideo, instagramUrl, currentPostType, hasExternalLink, crossPost
                        )
                    } else {
                        createUpdate(
                            content, title,
                            imageUrls, documentUrls, documentNames, links,
                            isVideo, instagramUrl, currentPostType, hasExternalLink, crossPost
                        )
                    }
                } catch (e: Exception) {
                    val errorMsg = "Failed to upload: ${e.localizedMessage}"
                    Log.e(TAG, errorMsg, e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
                        dialog.findViewById<Button>(R.id.postUpdateButton)?.isEnabled = true
                        dialog.findViewById<Button>(R.id.cancelButton)?.isEnabled = true
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showUpdateOptionsDialog() {
        val options: Array<CharSequence> = arrayOf("Add Post", "Batch Delete Posts")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Manage Posts")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showCreateUpdateDialog()
                    1 -> showBatchDeleteDialog()
                }
            }
            .show()
    }

    private fun showSelectedImages() {
        val mediaPreviewContainer = createUpdateDialog?.findViewById<FrameLayout>(R.id.mediaPreviewContainer)
        val mediaPreview = createUpdateDialog?.findViewById<ImageView>(R.id.mediaPreview)

        if (selectedImageUris.isEmpty()) {
            mediaPreviewContainer?.visibility = View.GONE
            return
        }

        mediaPreviewContainer?.visibility = View.VISIBLE

        mediaPreview?.let {
            try {
                // Show first image with count
                Glide.with(requireContext())
                    .load(selectedImageUris.first())
                    .centerCrop()
                    .into(it)
                
                // Update remove button text to show count
                val removeButton = createUpdateDialog?.findViewById<ImageButton>(R.id.removeMediaButton)
                if (selectedImageUris.size > 1) {
                    removeButton?.contentDescription = "Remove ${selectedImageUris.size} images"
                }
            } catch (e: FileNotFoundException) {
                Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                mediaPreviewContainer?.visibility = View.GONE
            }
        }
    }

    private fun showSelectedDocuments() {
        val documentPreviewContainer = createUpdateDialog?.findViewById<LinearLayout>(R.id.documentPreviewContainer)
        val documentNameText = createUpdateDialog?.findViewById<TextView>(R.id.documentNameText)

        if (selectedDocumentUris.isEmpty()) {
            documentPreviewContainer?.visibility = View.GONE
            return
        }

        // Show first document with count
        val documentName = getDocumentName(selectedDocumentUris.first())
        val displayText = if (selectedDocumentUris.size > 1) {
            "$documentName (+${selectedDocumentUris.size - 1} more)"
        } else {
            documentName
        }
        documentNameText?.text = displayText

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
            
            // Log the upload attempt for debugging
            Log.d(TAG, "Uploading image to Cloudinary")
            
            // Upload to Cloudinary using coroutines
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val downloadUrl = cloudinaryHelper.uploadImage(uri, "nss_updates/images")
                    
                    // Call the callback on main thread
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "Image uploaded successfully: $downloadUrl")
                        onComplete(downloadUrl)
                    }
                } catch (e: Exception) {
                    val errorMsg = "Failed to upload image: ${e.localizedMessage}"
                    Log.e(TAG, errorMsg, e)
                    withContext(Dispatchers.Main) {
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
            
            // Log the upload attempt for debugging
            Log.d(TAG, "Uploading document to Cloudinary: $documentName")
            
            // Upload to Cloudinary using coroutines
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val downloadUrl = cloudinaryHelper.uploadDocument(uri, "nss_updates/documents")
                    
                    // Call the callback on main thread
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "Document uploaded successfully: $downloadUrl")
                        onComplete(downloadUrl, documentName)
                    }
                } catch (e: Exception) {
                    val errorMsg = "Failed to upload document: ${e.localizedMessage}"
                    Log.e(TAG, errorMsg, e)
                    withContext(Dispatchers.Main) {
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
        imageUrls: List<String>,
        documentUrls: List<String>,
        documentNames: List<String>,
        externalLinks: List<String>,
        isVideo: Boolean = false,
        instagramUrl: String? = null,
        postType: String = "text",
        hasExternalLink: Boolean = false,
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

        // Create the update object with new list fields
        val update = Update(
            id = customDocId,
            authorId = userRollNumber,
            authorName = authorName,
            authorImageUrl = authorImageUrl,
            title = title,
            content = content,
            externalLink = null, // Keep old field null
            documentName = null, // Keep old field null
            documentUrl = null, // Keep old field null
            imageName = null, // Keep old field null
            imageUrl = null, // Keep old field null
            mediaUrl = null, // Keep old field null
            isVideo = isVideo,
            instagramUrl = instagramUrl,
            postType = postType,
            hasExternalLink = hasExternalLink,
            timestamp = timestamp,
            updateType = updateType,
            // New list fields
            imageUrls = if (imageUrls.isNotEmpty()) imageUrls else null,
            documentUrls = if (documentUrls.isNotEmpty()) documentUrls else null,
            documentNames = if (documentNames.isNotEmpty()) documentNames else null,
            externalLinks = if (externalLinks.isNotEmpty()) externalLinks else null
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

    private fun updatePost(
        originalUpdate: Update,
        content: String,
        title: String?,
        imageUrls: List<String>,
        documentUrls: List<String>,
        documentNames: List<String>,
        externalLinks: List<String>,
        isVideo: Boolean,
        instagramUrl: String?,
        postType: String,
        hasExternalLink: Boolean,
        crossPost: Boolean
    ) {
        // Determine updateType based on cross-post setting
        val updateType = if (crossPost) 3 else 2 // 3=Both, 2=NSS only

        val updatedUpdate = originalUpdate.copy(
            title = title,
            content = content,
            externalLink = null,
            mediaUrl = null,
            imageUrl = null,
            imageName = null,
            documentUrl = null,
            documentName = null,
            isVideo = isVideo,
            instagramUrl = instagramUrl,
            postType = postType,
            hasExternalLink = hasExternalLink,
            updateType = updateType,
            imageUrls = if (imageUrls.isNotEmpty()) imageUrls else null,
            documentUrls = if (documentUrls.isNotEmpty()) documentUrls else null,
            documentNames = if (documentNames.isNotEmpty()) documentNames else null,
            externalLinks = if (externalLinks.isNotEmpty()) externalLinks else null
        )

        // Update in NSS collection
        db.collection("nss_updates").document(originalUpdate.id)
            .set(updatedUpdate)
            .addOnSuccessListener {
                if (crossPost) {
                    db.collection("updates").document(originalUpdate.id)
                        .set(updatedUpdate)
                }
                Toast.makeText(requireContext(), "Post updated successfully", Toast.LENGTH_SHORT).show()
                createUpdateDialog?.dismiss()
                updateCache = null
                loadUpdates()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
                createUpdateDialog?.findViewById<Button>(R.id.postUpdateButton)?.isEnabled = true
                createUpdateDialog?.findViewById<Button>(R.id.cancelButton)?.isEnabled = true
            }
    }

    private fun confirmDeletePost(update: Update) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Post?")
            .setMessage("Are you sure you want to delete '${update.title}'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deletePost(update)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePost(update: Update) {
        db.collection("nss_updates").document(update.id)
            .delete()
            .addOnSuccessListener {
                // Also delete from general updates if it exists there
                db.collection("updates").document(update.id).delete()
                
                Toast.makeText(requireContext(), "Post deleted", Toast.LENGTH_SHORT).show()
                updateCache = null
                loadUpdates()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showBatchDeleteDialog() {
        val updates = uiState.value.updates
        if (updates.isEmpty()) {
            Toast.makeText(requireContext(), "No posts to delete", Toast.LENGTH_SHORT).show()
            return
        }

        val titles: Array<CharSequence> = updates.map { 
            val time = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault()).format(java.util.Date(it.timestamp))
            "${it.title ?: "Untitled"} ($time)"
        }.toTypedArray()
        
        val checkedItems = BooleanArray(updates.size)
        val selectedItems = java.util.ArrayList<Int>()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Posts to Delete")
            .setMultiChoiceItems(titles, checkedItems) { dialog, which, isChecked ->
                if (isChecked) {
                    selectedItems.add(which)
                } else {
                    selectedItems.remove(Integer.valueOf(which))
                }
            }
            .setPositiveButton("Delete Selected") { _, _ ->
                if (selectedItems.isEmpty()) {
                    Toast.makeText(requireContext(), "No posts selected", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Confirm Batch Delete")
                    .setMessage("Are you sure you want to delete ${selectedItems.size} posts?")
                    .setPositiveButton("Delete") { _, _ ->
                        // Loop and delete
                        var deletedCount = 0
                        val total = selectedItems.size
                        
                        selectedItems.forEach { index ->
                            if (index < updates.size) {
                                // Simplified delete for batch to avoid spam
                                val update = updates[index]
                                db.collection("nss_updates").document(update.id).delete()
                                db.collection("updates").document(update.id).delete()
                            }
                        }
                        
                        Toast.makeText(requireContext(), "Deleting $total posts...", Toast.LENGTH_SHORT).show()
                        // Delay reload slightly to allow deletions to propagate
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            updateCache = null
                            loadUpdates()
                        }, 1000)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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


} 