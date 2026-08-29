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
import android.view.MotionEvent
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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
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

class HomeFragment : Fragment() {
    private val TAG = "HomeFragment"
    
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
    private val CACHE_KEY_LAST_REFRESH = "last_ttw_update_refresh"
    
    // Create update dialog
    private var createUpdateDialog: Dialog? = null
    private var selectedImageUris: MutableList<com.phad.chatapp.adapters.AttachmentItem> = mutableListOf()
    private var selectedDocumentUris: MutableList<com.phad.chatapp.adapters.AttachmentItem> = mutableListOf()
    private var deletedAttachmentUrls: MutableList<String> = mutableListOf()
    private var externalLinks: MutableList<String> = mutableListOf()
    private var uploadJob: kotlinx.coroutines.Job? = null
    
    // Image picker launcher - supports multiple selection
    private val imagePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                // Handle multiple images
                data.clipData?.let { clipData ->
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i).uri?.let { uri ->
                            selectedImageUris.add(com.phad.chatapp.adapters.AttachmentItem.Local(uri))
                        }
                    }
                } ?: data.data?.let { uri ->
                    // Single image selected
                    selectedImageUris.add(com.phad.chatapp.adapters.AttachmentItem.Local(uri))
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
                            selectedDocumentUris.add(com.phad.chatapp.adapters.AttachmentItem.Local(uri))
                        }
                    }
                } ?: data.data?.let { uri ->
                    // Single document selected
                    selectedDocumentUris.add(com.phad.chatapp.adapters.AttachmentItem.Local(uri))
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
                    onNotificationClick = {
                        val intent = Intent(requireContext(), com.phad.chatapp.activities.NotificationHistoryActivity::class.java)
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
                    },
                    onBottomNavVisibilityChanged = { shouldShow ->
                        // Control the bottom navigation visibility from the parent activity
                        (activity as? com.phad.chatapp.MainActivity)?.findViewById<View>(com.phad.chatapp.R.id.bottom_nav_container)?.visibility =
                            if (shouldShow) View.VISIBLE else View.GONE
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
                            isNssInterface = false
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
                        isNssInterface = false
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
                        isNssInterface = false
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
        db.collection("ttw_updates")
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
        // Defensive admin check - prevent non-admins from accessing this function
        if (!sessionManager.fetchUserType().equals("Admin", ignoreCase = true)) {
            Log.w(TAG, "Non-admin user attempted to access showCreateUpdateDialog")
            Toast.makeText(requireContext(), "Admin access required", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Initialize the dialog
        createUpdateDialog = Dialog(requireContext(), R.style.TransparentDialog).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_create_update)
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.90).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
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
        deletedAttachmentUrls.clear()
        externalLinks.clear()

        // Find views in the dialog
        val dialog = createUpdateDialog ?: return
        val dialogTitle = dialog.findViewById<TextView>(R.id.dialogTitle)
        val updateContentInput = dialog.findViewById<EditText>(R.id.updateContentInput)
        val updateTitleInput = dialog.findViewById<EditText>(R.id.updateTitleInput)
        val updateLinkInput = dialog.findViewById<EditText>(R.id.updateLinkInput)
        val instagramLinkInput = dialog.findViewById<EditText>(R.id.instagramLinkInput)
        val attachImageButton = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.attachImageButton)
        val attachDocumentButton = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.attachDocumentButton)
        val cancelButton = dialog.findViewById<Button>(R.id.cancelButton)
        val postUpdateButton = dialog.findViewById<Button>(R.id.postUpdateButton)
        val titleCharCounter = dialog.findViewById<TextView>(R.id.titleCharCounter)
        val closeButton = dialog.findViewById<ImageButton>(R.id.closeButton)
        
        // Enable internal scrolling for content input
        updateContentInput?.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            if ((event.action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        
        // Character counter for title
        updateTitleInput?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                titleCharCounter?.text = "${s?.length ?: 0}/80"
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        // Close button
        closeButton?.setOnClickListener {
            dialog.dismiss()
        }

        // Add checkbox for cross-posting to NSS (only for Teaching Wing admins)
        val crossPostCheckbox = dialog.findViewById<android.widget.CheckBox>(R.id.crossPostCheckbox)
        val wingTargetingContainer = dialog.findViewById<View>(R.id.wingTargetingContainer)
        wingTargetingContainer?.visibility = View.GONE
        
        val userType = sessionManager.fetchUserType()
        if (userType.equals("Admin", ignoreCase = true)) {
            crossPostCheckbox?.visibility = View.VISIBLE
            crossPostCheckbox?.text = "Also post to NSS Interface"
        } else {
            crossPostCheckbox?.visibility = View.GONE
        }

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
            val activeColor = android.graphics.Color.parseColor("#FFC107") // Golden
            val inactiveColor = android.graphics.Color.TRANSPARENT
            val activeText = android.graphics.Color.BLACK
            val inactiveText = android.graphics.Color.parseColor("#888888")

            if (type == "reel") {
                // Reel Active
                reelPostButton.backgroundTintList = android.content.res.ColorStateList.valueOf(activeColor)
                reelPostButton.setTextColor(activeText)
                
                textPostButton.backgroundTintList = android.content.res.ColorStateList.valueOf(inactiveColor)
                textPostButton.setTextColor(inactiveText)
                
                reelPostFields?.visibility = View.VISIBLE
                textPostFields?.visibility = View.GONE
                updateContentInput?.isEnabled = false
                updateLinkInput?.isEnabled = false
            } else {
                // Text Active
                textPostButton.backgroundTintList = android.content.res.ColorStateList.valueOf(activeColor)
                textPostButton.setTextColor(activeText)
                
                reelPostButton.backgroundTintList = android.content.res.ColorStateList.valueOf(inactiveColor)
                reelPostButton.setTextColor(inactiveText)
                
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

        // Initialize RecyclerViews for attachments
        val imagesRecyclerView = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.imagesRecyclerView)
        val documentsRecyclerView = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.documentsRecyclerView)

        // Setup Images Adapter
        val imagesAdapter = com.phad.chatapp.adapters.AttachmentAdapter(
            items = selectedImageUris,
            isDocument = false,
            onRemoveClick = { position ->
                val item = selectedImageUris[position]
                if (item is com.phad.chatapp.adapters.AttachmentItem.Remote) {
                    deletedAttachmentUrls.add(item.url)
                }
                selectedImageUris.removeAt(position)
                dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.imagesRecyclerView)?.adapter?.notifyItemRemoved(position)
                
                if (selectedImageUris.isEmpty()) {
                     dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.imagesRecyclerView)?.visibility = View.GONE
                }
            },
            context = requireContext() // Pass context for content resolver
        )
        imagesRecyclerView?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        imagesRecyclerView?.adapter = imagesAdapter
        
        // Setup Documents Adapter
        val documentsAdapter = com.phad.chatapp.adapters.AttachmentAdapter(
            items = selectedDocumentUris,
            isDocument = true,
            onRemoveClick = { position ->
                val item = selectedDocumentUris[position]
                if (item is com.phad.chatapp.adapters.AttachmentItem.Remote) {
                    deletedAttachmentUrls.add(item.url)
                }
                selectedDocumentUris.removeAt(position)
                dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.documentsRecyclerView)?.adapter?.notifyItemRemoved(position)
                
                if (selectedDocumentUris.isEmpty()) {
                     dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.documentsRecyclerView)?.visibility = View.GONE
                }
            },
            context = requireContext()
        )
        documentsRecyclerView?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        documentsRecyclerView?.adapter = documentsAdapter

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


        // Pre-fill if editing
        if (existingUpdate != null) {
            dialogTitle?.text = "Update Post"
            updateTitleInput?.setText(existingUpdate.title)
            updateContentInput?.setText(existingUpdate.content)
            updateLinkInput?.setText(existingUpdate.getAllLinks().joinToString(", "))
            instagramLinkInput?.setText(existingUpdate.instagramUrl)
            crossPostCheckbox?.isChecked = (existingUpdate.updateType == 3)

            // Pre-fill images
            existingUpdate.getAllImages().forEach { url ->
                selectedImageUris.add(com.phad.chatapp.adapters.AttachmentItem.Remote(url))
            }
            if (selectedImageUris.isNotEmpty()) {
                imagesRecyclerView?.visibility = View.VISIBLE
                imagesAdapter.notifyDataSetChanged()
            }

            // Pre-fill documents
            existingUpdate.getAllDocuments().forEach { (url, name) ->
                selectedDocumentUris.add(com.phad.chatapp.adapters.AttachmentItem.Remote(url, name))
            }
             if (selectedDocumentUris.isNotEmpty()) {
                documentsRecyclerView?.visibility = View.VISIBLE
                documentsAdapter.notifyDataSetChanged()
            }

            // Set Post Type
            if (existingUpdate.postType == "reel" || !existingUpdate.instagramUrl.isNullOrEmpty()) {
                updatePostTypeUI("reel")
            } else {
                updatePostTypeUI("text")
            }

            postUpdateButton.text = "Update"
        } else {
            dialogTitle?.text = "Create Post"
            postUpdateButton.text = "Publish Post"
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
            
            // Disable buttons
            dialog.findViewById<Button>(R.id.postUpdateButton).isEnabled = false
            dialog.findViewById<Button>(R.id.cancelButton).isEnabled = false
            dialog.setCancelable(false)

            // Setup Progress UI
            val overlay = dialog.findViewById<View>(R.id.uploadProgressOverlay)
            val progressBar = dialog.findViewById<android.widget.ProgressBar>(R.id.uploadProgressBar)
            val progressText = dialog.findViewById<TextView>(R.id.uploadProgressText)
            
            overlay?.visibility = View.VISIBLE
            progressBar?.progress = 0
            progressText?.text = "Preparing... 0%"
            
            // Prepare post data
            val content = if (currentPostType == "text") updateContentInput.text.toString().trim() else ""
            val link = if (currentPostType == "text") updateLinkInput.text.toString().trim() else ""
            val instagramLink = if (currentPostType == "reel") instagramLinkInput.text.toString().trim() else ""
            
            // Launch Upload Job
            uploadJob = lifecycleScope.launch(Dispatchers.IO) {
                val scope = this
                // Track uploaded IDs for reliable cleanup
                val uploadedImageIds = mutableListOf<String>()
                val uploadedDocumentIds = mutableListOf<String>()
                
                try {
                    val imageUrls = mutableListOf<String>()
                    val documentUrls = mutableListOf<String>()
                    val documentNames = mutableListOf<String>()
                    val externalLinksList = mutableListOf<String>()

                    // Process manual links (separated by comma or newline)
                    if (link.isNotEmpty()) {
                        val splitLinks = link.split(",", "\n")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        externalLinksList.addAll(splitLinks)
                    }

                    // Calculate total items to operate on (Local uploads + Remote deletions)
                    val localImagesCount = selectedImageUris.count { it is com.phad.chatapp.adapters.AttachmentItem.Local }
                    val localDocsCount = selectedDocumentUris.count { it is com.phad.chatapp.adapters.AttachmentItem.Local }
                    val deletionsCount = deletedAttachmentUrls.size
                    val totalItems = localImagesCount + localDocsCount + deletionsCount
                    var itemsCompleted = 0
                    
                    // Delete removed attachments
                    if (deletedAttachmentUrls.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            progressText?.text = "Updating... 0%"
                        }
                        deletedAttachmentUrls.forEach { url ->
                             // Try deleting as image first, then document if needed or based on extension
                             // Here we just try as image, if it fails it might be a raw file.
                             // Ideally we should know the type. For now, let's try image first.
                             // A better way is to check the URL or store type in AttachmentItem.Remote
                             
                             // Simple heuristic: check extension or try both
                             val isImage = url.contains("/images/") || url.endsWith(".jpg") || url.endsWith(".png")
                             if (isImage) {
                                 cloudinaryHelper.deleteImage(url)
                             } else {
                                 cloudinaryHelper.deleteDocument(url)
                             }
                             
                             itemsCompleted++
                             val progress = ((itemsCompleted.toFloat() / totalItems.toFloat()) * 100).toInt()
                             withContext(Dispatchers.Main) {
                                 progressBar?.progress = progress
                                 progressText?.text = "Updating... $progress%"
                             }
                        }
                    }

                    // Upload Images
                    val localImages = selectedImageUris.filterIsInstance<com.phad.chatapp.adapters.AttachmentItem.Local>()
                    val remoteImages = selectedImageUris.filterIsInstance<com.phad.chatapp.adapters.AttachmentItem.Remote>()
                    
                    // Add existing remote images to final list
                    remoteImages.forEach { imageUrls.add(it.url) }

                    localImages.forEachIndexed { index, item ->
                        if (!scope.isActive) throw kotlinx.coroutines.CancellationException()
                        
                        withContext(Dispatchers.Main) {
                            // progressText?.text = "Uploading Image ${index + 1} of ${localImages.size}..."
                        }
                        
                        val result = uploadMedia(item.uri) { progress ->
                            // Calculate global progress
                            val itemWeight = 100f / (if (totalItems > 0) totalItems else 1).toFloat()
                            val baseProgress = itemsCompleted * itemWeight
                            val currentItemContribution = (progress.toFloat() / 100f) * itemWeight
                            val totalProgress = (baseProgress + currentItemContribution).toInt()
                            
                            lifecycleScope.launch(Dispatchers.Main) {
                                progressBar?.progress = totalProgress
                                progressText?.text = "Uploading... $totalProgress%"
                            }
                        }
                        uploadedImageIds.add(result.publicId) // Track exact ID for cleanup
                        imageUrls.add(result.url)
                        itemsCompleted++
                    }
                    
                    // Upload Documents
                    val localDocs = selectedDocumentUris.filterIsInstance<com.phad.chatapp.adapters.AttachmentItem.Local>()
                    val remoteDocs = selectedDocumentUris.filterIsInstance<com.phad.chatapp.adapters.AttachmentItem.Remote>()

                    // Add existing remote docs
                     remoteDocs.forEach { 
                         documentUrls.add(it.url)
                         documentNames.add(it.name ?: "Document")
                     }

                    localDocs.forEachIndexed { index, item ->
                         if (!scope.isActive) throw kotlinx.coroutines.CancellationException()
                         
                        withContext(Dispatchers.Main) {
                            // progressText?.text = "Uploading Document ${index + 1} of ${localDocs.size}..."
                        }

                        // Upload document and get result
                        val (result, name) = uploadDocument(item.uri) { progress ->
                            val itemWeight = 100f / (if (totalItems > 0) totalItems else 1).toFloat()
                            val baseProgress = itemsCompleted * itemWeight
                            val currentItemContribution = (progress.toFloat() / 100f) * itemWeight
                            val totalProgress = (baseProgress + currentItemContribution).toInt()
                            
                            lifecycleScope.launch(Dispatchers.Main) {
                                progressBar?.progress = totalProgress
                                progressText?.text = "Uploading... $totalProgress%"
                            }
                        }
                        uploadedDocumentIds.add(result.publicId) // Track exact ID for cleanup
                        documentUrls.add(result.url)
                        documentNames.add(name)
                        itemsCompleted++
                    }

                     withContext(Dispatchers.Main) {
                        progressText?.text = "Updating... 100%"
                         progressBar?.progress = 100
                    }

                    // Proceed to Create/Update
                     withContext(Dispatchers.Main) {
                         if (existingUpdate == null) {
                            createUpdate(
                                content = content,
                                title = title,
                                imageUrls = imageUrls,
                                documentUrls = documentUrls,
                                documentNames = documentNames,
                                externalLinks = externalLinksList,
                                isVideo = false, 
                                instagramUrl = if (currentPostType == "reel") instagramLink else null,
                                postType = currentPostType,
                                hasExternalLink = externalLinksList.isNotEmpty(),
                                crossPost = crossPostCheckbox?.isChecked == true,
                                uploadedImageIds = uploadedImageIds,
                                uploadedDocumentIds = uploadedDocumentIds
                            )
                        } else {
                            updatePost(
                                originalUpdate = existingUpdate,
                                content = content,
                                title = title,
                                imageUrls = imageUrls,
                                documentUrls = documentUrls,
                                documentNames = documentNames,
                                externalLinks = externalLinksList,
                                isVideo = existingUpdate.isVideo,
                                instagramUrl = if (currentPostType == "reel") instagramLink else null,
                                postType = currentPostType,
                                hasExternalLink = externalLinksList.isNotEmpty(),
                                crossPost = crossPostCheckbox?.isChecked == true,
                                uploadedImageIds = uploadedImageIds,
                                uploadedDocumentIds = uploadedDocumentIds
                            )
                        }
                        overlay?.visibility = View.GONE 
                        dialog.setCancelable(true)
                        dialog.findViewById<Button>(R.id.postUpdateButton)?.isEnabled = true
                        dialog.findViewById<Button>(R.id.cancelButton)?.isEnabled = true
                    }

                } catch (e: kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "Upload cancelled")
                    withContext(Dispatchers.Main) {
                        progressText?.text = "Upload cancelled"
                        Toast.makeText(requireContext(), "Upload cancelled", Toast.LENGTH_SHORT).show()
                        overlay?.visibility = View.GONE
                        dialog.setCancelable(true)
                        dialog.findViewById<Button>(R.id.postUpdateButton)?.isEnabled = true
                        dialog.findViewById<Button>(R.id.cancelButton)?.isEnabled = true
                    }
                } catch (e: Exception) {
                    if (scope.isActive) {
                        Log.e(TAG, "Upload failed", e)
                         withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            overlay?.visibility = View.GONE
                            dialog.setCancelable(true)
                             dialog.findViewById<Button>(R.id.postUpdateButton)?.isEnabled = true
                             dialog.findViewById<Button>(R.id.cancelButton)?.isEnabled = true
                        }
                    }
                }
            }
        } // Close setOnClickListener

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

    // Helper functions to update list visibility after picking
    private fun showSelectedImages() {
        if (selectedImageUris.isNotEmpty()) {
            val recyclerView = createUpdateDialog?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.imagesRecyclerView)
            recyclerView?.visibility = View.VISIBLE
            recyclerView?.adapter?.notifyDataSetChanged()
        }
    }

    private fun showSelectedDocuments() {
        if (selectedDocumentUris.isNotEmpty()) {
            val recyclerView = createUpdateDialog?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.documentsRecyclerView)
            recyclerView?.visibility = View.VISIBLE
            recyclerView?.adapter?.notifyDataSetChanged()
        }
    }
    // (Cleared stray code)


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


    private suspend fun uploadMedia(uri: Uri, onProgress: (Int) -> Unit): com.phad.chatapp.models.CloudinaryUploadResult {
        return try {
            cloudinaryHelper.uploadImage(uri, "ttw_updates/images", onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload image: ${e.localizedMessage}", e)
            throw e
        }
    }

    private suspend fun uploadDocument(uri: Uri, onProgress: (Int) -> Unit): Pair<com.phad.chatapp.models.CloudinaryUploadResult, String> {
        return try {
            // Get document name
            val documentName = getDocumentName(uri)
            val result = cloudinaryHelper.uploadDocument(uri, "ttw_updates/documents", onProgress)
            Pair(result, documentName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload document: ${e.localizedMessage}", e)
            throw e
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
        crossPost: Boolean = false,
        uploadedImageIds: List<String> = emptyList(),
        uploadedDocumentIds: List<String> = emptyList()
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
        db.collection("ttw_updates").document(customDocId)
            .set(update)
            .addOnSuccessListener {
                // If cross-post is enabled, also save to the regular updates collection
                if (crossPost) {
                    db.collection("nss_updates").document(customDocId)
                        .set(update)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Update posted to both Teaching Wing and NSS!", Toast.LENGTH_SHORT).show()
                            createUpdateDialog?.dismiss()

                            // Reload updates and clear cache to show new update
                            updateCache = null
                            loadUpdates()

                            // Send notification to TTW + NSS users
                            db.collection("app_notifications").add(mapOf(
                                "title" to (title?.takeIf { it.isNotBlank() } ?: "New Update"),
                                "body" to (content.takeIf { it.isNotBlank() } ?: "A new post has been published."),
                                "targetTopics" to listOf("ttw_user", "nss_user"),
                                "type" to "UPDATE_NOTIFICATION",
                                "creatorId" to authorName,
                                "isRead" to false,
                                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            ))

                            // Trigger Vercel FCM Push
                            lifecycleScope.launch {
                                val notifTitle = title?.takeIf { it.isNotBlank() } ?: "New NSS & TTW Update"
                                val notifBody = if (postType == "reel") "🎥 A new Reel has been published: ${title ?: "Check it out!"}" else (content.take(100).takeIf { it.isNotBlank() } ?: "A new post has been published.")
                                com.phad.chatapp.utils.FcmSender.sendToTopic("nss", notifTitle, notifBody)
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(requireContext(), "Posted to Teaching Wing but failed to cross-post to NSS: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            createUpdateDialog?.dismiss()
                            updateCache = null
                            loadUpdates()
        
                        }
                } else {
                    Toast.makeText(requireContext(), "NSS Update posted", Toast.LENGTH_SHORT).show()
                    createUpdateDialog?.dismiss()

                    // Reload updates and clear cache to show new update
                    updateCache = null
                    loadUpdates()

                    // Send notification to TTW users
                    db.collection("app_notifications").add(mapOf(
                        "title" to (title?.takeIf { it.isNotBlank() } ?: "New Teaching Wing Update"),
                        "body" to (content.takeIf { it.isNotBlank() } ?: "A new post has been published."),
                        "targetTopics" to listOf("ttw_user"),
                        "type" to "UPDATE_NOTIFICATION",
                        "creatorId" to authorName,
                        "isRead" to false,
                        "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    ))

                    // Trigger Vercel FCM Push
                    lifecycleScope.launch {
                        val notifTitle = title?.takeIf { it.isNotBlank() } ?: "New Teaching Wing Update"
                        val notifBody = if (postType == "reel") "🎥 A new Reel has been published: ${title ?: "Check it out!"}" else (content.take(100).takeIf { it.isNotBlank() } ?: "A new post has been published.")
                        com.phad.chatapp.utils.FcmSender.sendToTopic("ttw", notifTitle, notifBody)
                    }
                }
            }
            .addOnFailureListener { e ->
                // Clean up uploaded files from Cloudinary since Firestore write failed
                lifecycleScope.launch(Dispatchers.IO) {
                    uploadedImageIds.forEach { publicId ->
                        Log.d(TAG, "Cleaning up uploaded image (Firestore failed): $publicId")
                        try {
                            cloudinaryHelper.deleteImageById(publicId)
                        } catch (deleteErr: Exception) {
                            Log.e(TAG, "Failed to cleanup image $publicId", deleteErr)
                        }
                    }
                    uploadedDocumentIds.forEach { publicId ->
                        Log.d(TAG, "Cleaning up uploaded document (Firestore failed): $publicId")
                        try {
                            cloudinaryHelper.deleteDocumentById(publicId)
                        } catch (deleteErr: Exception) {
                            Log.e(TAG, "Failed to cleanup document $publicId", deleteErr)
                        }
                    }
                }
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
        crossPost: Boolean,
        uploadedImageIds: List<String> = emptyList(),
        uploadedDocumentIds: List<String> = emptyList()
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

        // Detect and delete removed attachments
        lifecycleScope.launch(Dispatchers.IO) {
            // Check for removed images
            val originalImages = originalUpdate.imageUrls ?: emptyList()
            // Identify images that were in original but not in the new list
            val removedImages = originalImages.filter { !imageUrls.contains(it) }
            
            removedImages.forEach { url ->
                Log.d(TAG, "Deleting removed image: $url")
                cloudinaryHelper.deleteImage(url)
            }

            // Check for removed documents
            val originalDocuments = originalUpdate.documentUrls ?: emptyList()
            val removedDocuments = originalDocuments.filter { !documentUrls.contains(it) }
            
            removedDocuments.forEach { url ->
                Log.d(TAG, "Deleting removed document: $url")
                cloudinaryHelper.deleteDocument(url)
            }
        }

        // Update in NSS collection
        db.collection("ttw_updates").document(originalUpdate.id)
            .set(updatedUpdate)
            .addOnSuccessListener {
                if (crossPost) {
                    db.collection("nss_updates").document(originalUpdate.id)
                        .set(updatedUpdate)
                }
                Toast.makeText(requireContext(), "Post updated successfully", Toast.LENGTH_SHORT).show()
                createUpdateDialog?.dismiss()
                updateCache = null
                loadUpdates()
            }
            .addOnFailureListener { e ->
                // Clean up newly uploaded files from Cloudinary since Firestore write failed
                lifecycleScope.launch(Dispatchers.IO) {
                    uploadedImageIds.forEach { publicId ->
                        Log.d(TAG, "Cleaning up uploaded image (update failed): $publicId")
                        try {
                            cloudinaryHelper.deleteImageById(publicId)
                        } catch (deleteErr: Exception) {
                            Log.e(TAG, "Failed to cleanup image $publicId", deleteErr)
                        }
                    }
                    uploadedDocumentIds.forEach { publicId ->
                        Log.d(TAG, "Cleaning up uploaded document (update failed): $publicId")
                        try {
                            cloudinaryHelper.deleteDocumentById(publicId)
                        } catch (deleteErr: Exception) {
                            Log.e(TAG, "Failed to cleanup document $publicId", deleteErr)
                        }
                    }
                }
                Toast.makeText(requireContext(), "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
                createUpdateDialog?.findViewById<Button>(R.id.postUpdateButton)?.isEnabled = true
                createUpdateDialog?.findViewById<Button>(R.id.cancelButton)?.isEnabled = true
            }
    }

    private fun confirmDeletePost(update: Update) {
        // Create custom dialog for better UX
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_delete_confirmation)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        // Set up dialog views
        val titleTextView = dialog.findViewById<TextView>(R.id.deleteDialogTitle)
        val messageTextView = dialog.findViewById<TextView>(R.id.deleteDialogMessage)
        val deleteButton = dialog.findViewById<Button>(R.id.deleteConfirmButton)
        val cancelButton = dialog.findViewById<Button>(R.id.deleteCancelButton)
        
        // Set post title in message
        val postTitle = if (update.title.isNullOrEmpty()) "this post" else "'${update.title}'"
        messageTextView.text = "Are you sure you want to delete $postTitle? This action cannot be undone."
        
        // Button listeners
        deleteButton.setOnClickListener {
            dialog.dismiss()
            deletePost(update)
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun deletePost(update: Update) {
        // Start deletion process
        lifecycleScope.launch {
            // Delete images
            update.getAllImages().forEach { url ->
                cloudinaryHelper.deleteImage(url)
            }
            
            // Delete documents
            update.getAllDocuments().forEach { (url, _) ->
                cloudinaryHelper.deleteDocument(url)
            }
            
            // Delete from Firestore
            db.collection("ttw_updates").document(update.id)
                .delete()
                .addOnSuccessListener {
                    // Also delete from general updates if it exists there
                    db.collection("nss_updates").document(update.id).delete()
                    
                    Toast.makeText(requireContext(), "Post deleted", Toast.LENGTH_SHORT).show()
                    updateCache = null
                    loadUpdates()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to delete post: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showBatchDeleteDialog() {
        val updates = uiState.value.updates
        if (updates.isEmpty()) {
            Toast.makeText(requireContext(), "No posts to delete", Toast.LENGTH_SHORT).show()
            return
        }

        // Initialize custom dialog
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_batch_delete)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val recyclerView = dialog.findViewById<RecyclerView>(R.id.batchDeleteRecyclerView)
        val deleteButton = dialog.findViewById<Button>(R.id.deleteButton)
        val cancelButton = dialog.findViewById<Button>(R.id.cancelButton)

        // Disable delete button initially
        deleteButton.isEnabled = false
        deleteButton.alpha = 0.5f

        // Setup Adapter
        val adapter = com.phad.chatapp.adapters.BatchDeleteAdapter(updates) { count ->
            deleteButton.isEnabled = count > 0
            deleteButton.alpha = if (count > 0) 1.0f else 0.5f
            deleteButton.text = if (count > 0) "Delete ($count)" else "Delete Selected"
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // Button Listeners
        cancelButton.setOnClickListener { dialog.dismiss() }

        deleteButton.setOnClickListener {
            val selectedIndices = adapter.getSelectedItems()
            if (selectedIndices.isEmpty()) return@setOnClickListener

            // Custom Confirmation Dialog (Replaces MaterialAlertDialogBuilder)
            val confirmDialog = Dialog(requireContext())
            confirmDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            confirmDialog.setContentView(R.layout.dialog_delete_confirmation)
            confirmDialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            confirmDialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

            val titleTextView = confirmDialog.findViewById<TextView>(R.id.deleteDialogTitle)
            val messageTextView = confirmDialog.findViewById<TextView>(R.id.deleteDialogMessage)
            val confirmDeleteButton = confirmDialog.findViewById<Button>(R.id.deleteConfirmButton)
            val confirmCancelButton = confirmDialog.findViewById<Button>(R.id.deleteCancelButton)

            titleTextView.text = "Delete ${selectedIndices.size} Posts?"
            messageTextView.text = "Are you sure you want to delete these posts? This action cannot be undone."

            confirmDeleteButton.setOnClickListener {
                confirmDialog.dismiss()
                dialog.dismiss() // Close the selection dialog
                
                // Proceed with deletion
                val total = selectedIndices.size
                Toast.makeText(requireContext(), "Deleting $total posts...", Toast.LENGTH_SHORT).show()

                lifecycleScope.launch {
                    // Show loading indicator in UI if possible, or just toast
                    
                    selectedIndices.forEach { index ->
                        if (index < updates.size) {
                            val update = updates[index]
                            
                            // Cleanup attachments
                            try {
                                update.getAllImages().forEach { url -> cloudinaryHelper.deleteImage(url) }
                                update.getAllDocuments().forEach { (url, _) -> cloudinaryHelper.deleteDocument(url) }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error cleaning up attachments for ${update.id}", e)
                            }
                            
                            // Delete from Firestore
                            try {
                                db.collection("ttw_updates").document(update.id).delete()
                                db.collection("nss_updates").document(update.id).delete()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error deleting document ${update.id}", e)
                            }
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Batch delete complete", Toast.LENGTH_SHORT).show()
                        updateCache = null
                        loadUpdates()
                    }
                }
            }

            confirmCancelButton.setOnClickListener {
                confirmDialog.dismiss()
            }

            confirmDialog.show()
        }

        dialog.show()
    }


    private fun sendUpdateNotification(update: Update) {
        android.util.Log.d(TAG, "Preparing update notification for: ${update.id}")
        try {
            val title = update.title ?: "New Update"
            val contentChunk = update.content ?: ""
            val message = "$title: ${contentChunk.take(100)}${if (contentChunk.length > 100) "..." else ""}"
            val fullMessage = if (update.documentUrl != null || !update.documentUrls.isNullOrEmpty()) "$message [Contains document]" else message
            
            val targetType = when (update.updateType) {
                1 -> "ttw"
                2 -> "nss"
                3 -> "all"
                else -> "all"
            }
            
            val ndata = hashMapOf<String, Any>(
                "title" to title,
                "body" to fullMessage,
                "targetRole" to "all",
                "targetWing" to "all",
                "targetType" to targetType,
                "type" to "UPDATE_NOTIFICATION",
                "creatorId" to (update.authorId ?: "Admin"),
                "isRead" to false,
                "timestamp" to com.google.firebase.Timestamp.now()
            )
            
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("app_notifications").add(ndata)
                .addOnSuccessListener { android.util.Log.d(TAG, "Successfully created app_notification") }
                .addOnFailureListener { e -> android.util.Log.e(TAG, "Failed creating app_notification", e) }
                
        } catch (e: Exception) { 
            android.util.Log.e(TAG, "Error: ${e.message}", e) 
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