package com.phad.chatapp.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.phad.chatapp.R
import com.phad.chatapp.adapters.LibraryItemAdapter
import com.phad.chatapp.databinding.FragmentLibraryItemListBinding
import com.phad.chatapp.utils.LibraryFirestoreHelper
import com.phad.chatapp.models.LibraryItem
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.utils.NetworkUtils
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import com.phad.chatapp.utils.DriveServiceHelper
import com.phad.chatapp.utils.FileStorageUtils
import com.phad.chatapp.utils.FileTypeEnum

class LibraryItemListFragment : Fragment(), LibraryItemAdapter.OnItemClickListener {

    private var _binding: FragmentLibraryItemListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: LibraryItemAdapter
    private lateinit var sessionManager: SessionManager
    private lateinit var driveServiceHelper: DriveServiceHelper
    private var currentPath: String = "librarySections" // Default path for sections
    private var isSubcategory: Boolean = false
    private var tempUploadedFileName: String? = null // Temporary variable to hold file name
    private var isRenameMode: Boolean = false // Flag to indicate if in rename selection mode

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) {
        uri: Uri? ->
        uri?.let { // Handle the selected file URI
            uploadFileToFirestore(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryItemListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())
        driveServiceHelper = DriveServiceHelper.getInstance(requireContext())

        // Check user role and control FAB visibility - handle multiple admin types
        val userType = sessionManager.fetchUserType()
        val isAdmin = userType == "Admin" || userType == "Admin1" || userType == "Admin2"
        if (isAdmin) {
            binding.fabAddItem.visibility = View.VISIBLE
        } else {
            binding.fabAddItem.visibility = View.GONE
        }

        // Get arguments
        arguments?.let { args ->
            currentPath = args.getString("collectionPath", "librarySections")
            isSubcategory = args.getBoolean("isSubcategory", false)
        }

        // Test if toolbar is accessible
        try {
            Log.d("LibraryItemList", "Toolbar is accessible: ${binding.toolbarLibraryItemList != null}")
            Log.d("LibraryItemList", "Toolbar ID: ${binding.toolbarLibraryItemList.id}")
        } catch (e: Exception) {
            Log.e("LibraryItemList", "Error accessing toolbar: ${e.message}", e)
        }

        setupRecyclerView()
        setupFab()
        setupBackButton()
        fetchItems()
    }

    private fun setupRecyclerView() {
        adapter = LibraryItemAdapter(emptyList(), this)
        binding.recyclerViewLibraryItemList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@LibraryItemListFragment.adapter
            
            // Add item decoration for spacing between items
            addItemDecoration(object : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: android.graphics.Rect,
                    view: View,
                    parent: androidx.recyclerview.widget.RecyclerView,
                    state: androidx.recyclerview.widget.RecyclerView.State
                ) {
                    val position = parent.getChildAdapterPosition(view)
                    if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        // Add spacing between items
                        outRect.top = 8
                        outRect.bottom = 8
                        outRect.left = 8
                        outRect.right = 8
                    }
                }
            })
            
            // Enable smooth scrolling
            setHasFixedSize(true)
            
            // Set scroll listener for debugging
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    // Optional: Add any scroll-based logic here
                }
            })
        }
    }

    private fun setupFab() {
        binding.fabAddItem.setOnClickListener {
            showAddItemOptionsDialog()
        }
    }

    private fun setupBackButton() {
        try {
            // Set up the toolbar navigation icon click listener
            binding.toolbarLibraryItemList.setNavigationOnClickListener {
                Log.d("LibraryItemList", "Toolbar back button clicked")
                handleBackNavigation()
            }
            
            // Set up the alternative back button
            binding.btnBack.setOnClickListener {
                Log.d("LibraryItemList", "Alternative back button clicked")
                handleBackNavigation()
            }
            
            // Also set up the toolbar title based on the current path
            val title = "Library"
            binding.toolbarLibraryItemList.title = title
            
            // Ensure the navigation icon is visible
            binding.toolbarLibraryItemList.setNavigationIcon(R.drawable.ic_back_arrow)
            
            Log.d("LibraryItemList", "Back button setup completed successfully")
        } catch (e: Exception) {
            Log.e("LibraryItemList", "Error setting up back button: ${e.message}", e)
        }
    }

    private fun handleBackNavigation() {
        try {
            // Try to navigate up first
            if (!findNavController().navigateUp()) {
                // If navigateUp() returns false, it means we're at the root
                // In this case, we can finish the activity or navigate to a specific destination
                Log.d("LibraryItemList", "navigateUp() returned false, using activity back press")
                requireActivity().onBackPressed()
            } else {
                Log.d("LibraryItemList", "Successfully navigated up")
            }
        } catch (e: Exception) {
            // Fallback to activity back press
            Log.e("LibraryItemList", "Error navigating up: ${e.message}", e)
            requireActivity().onBackPressed()
        }
    }

    private fun fetchItems() {
        // Determine the correct collection paths for fetching items
        val targetCollectionPaths = if (currentPath.split("/").size % 2 != 0) {
            // currentPath is a collection path (odd number of segments), fetch from here
            listOf(currentPath)
        } else {
            // currentPath is a document path (even number of segments)
            // Fetch items from both 'items' and 'files' subcollections under this document
            listOf("$currentPath/items", "$currentPath/files")
        }

        Log.d("LibraryItemList", "Attempting to fetch items from paths: $targetCollectionPaths")
        lifecycleScope.launch {
            try {
                val fetchedItems = mutableListOf<LibraryItem>()
                for (path in targetCollectionPaths) {
                    val items = LibraryFirestoreHelper.getLibraryItems(path)
                    fetchedItems.addAll(items)
                    Log.d("LibraryItemList", "Fetched ${items.size} items from $path")
                }

                Log.d("LibraryItemList", "Total fetched items: ${fetchedItems.size}")
                adapter.updateItems(fetchedItems)
            } catch (e: Exception) {
                Log.e("LibraryItemList", "Failed to fetch items from paths: $targetCollectionPaths", e)
                Toast.makeText(context, "Failed to load items", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddItemOptionsDialog() {
        val options = if (isRenameMode) {
             arrayOf("Cancel Rename") // Option to exit rename mode
        } else if (isSubcategory) {
            arrayOf("Add Subsection", "Upload File", "Delete Item", "Rename Item")
        } else {
            arrayOf("Add Section", "Upload File", "Delete Item", "Rename Item")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isRenameMode) "Rename Mode" else "Choose the option")
            .setItems(options) { dialog, which ->
                if (isRenameMode) {
                    // Handle options in rename mode
                    when (which) {
                        0 -> exitRenameMode() // Cancel Rename
                    }
                } else {
                    // Handle options in normal mode
                    when (which) {
                        0 -> showAddSectionDialog()
                        1 -> showUploadFileDialog()
                        2 -> showDeleteItemDialog()
                        3 -> enterRenameMode()
                    }
                }
            }
            .show()
    }

    private fun enterRenameMode() {
        isRenameMode = true
        Toast.makeText(context, "Select an item to rename", Toast.LENGTH_SHORT).show()
        // Optionally update UI, e.g., change toolbar title
        // requireActivity().title = "Select Item to Rename"
    }

    private fun exitRenameMode() {
        isRenameMode = false
        Toast.makeText(context, "Exited rename mode", Toast.LENGTH_SHORT).show()
        // Optionally reset UI, e.g., restore toolbar title
        // requireActivity().title = "Library"
    }

    private fun showAddSectionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_section, null)
        val editTextTitle = dialogView.findViewById<TextInputEditText>(R.id.editTextSectionTitle)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isSubcategory) "Add Subsection" else "Add Section")
            .setView(dialogView)
            .setPositiveButton("Add") { dialog, which ->
                val title = editTextTitle.text.toString()
                if (title.isNotEmpty()) {
                    addNewSection(title)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addNewSection(title: String) {
        Log.d("LibraryItemList", "Attempting to add new section to $currentPath with title: $title")
        lifecycleScope.launch {
            val documentId = "${title.replace(" ", "_")}_${System.currentTimeMillis()}"
            // Determine the correct collection path for adding the new item
            val targetCollectionPath = if (currentPath.split("/").size % 2 != 0) {
                // currentPath is a collection path (odd number of segments)
                currentPath
            } else {
                // currentPath is a document path (even number of segments)
                // Add the new item to a subcollection under this document
                "$currentPath/items"
            }

            Log.d("LibraryItemList", "Attempting to add new item to $targetCollectionPath with title: $title")
            val newItemId = LibraryFirestoreHelper.addLibraryItem(targetCollectionPath, title, documentId)
            if (newItemId != null) {
                Toast.makeText(context, "Section added successfully", Toast.LENGTH_SHORT).show()
                fetchItems()
            } else {
                Toast.makeText(context, "Failed to add section", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUploadFileDialog() {
        // TODO: Implement file upload dialog
        // Toast.makeText(context, "File upload not implemented yet", Toast.LENGTH_SHORT).show()
        // filePickerLauncher.launch("*/*") // Launch file picker for any file type

        // Show dialog to ask for file name first
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_section, null) // Reuse layout
        val editTextFileName = dialogView.findViewById<TextInputEditText>(R.id.editTextSectionTitle)
        editTextFileName.hint = "Enter file name"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Enter File Name")
            .setView(dialogView)
            .setPositiveButton("Next") { dialog, which ->
                val fileName = editTextFileName.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    // Proceed to pick file after getting the name
                    launchFilePicker(fileName)
                } else {
                    Toast.makeText(context, "File name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // New function to launch file picker with the provided file name
    private fun launchFilePicker(fileName: String) {
        // Store the file name temporarily to use after file selection
        // A better approach might be to pass it via the launcher, but requires more setup.
        // For simplicity, we'll use a temporary variable or field if needed.
        // However, the uploadFileToFirestore will need the name.
        // Let's pass the name directly to uploadFileToFirestore after picking.
        tempUploadedFileName = fileName // Store the file name
        filePickerLauncher.launch("*/*") // Launch file picker for any file type
    }

    private fun uploadFileToFirestore(fileUri: Uri) {
        Log.d("LibraryItemList", "Attempting to upload file: $fileUri to path: $currentPath")
        lifecycleScope.launch {
            // Check if the current path is a document path (even number of segments)
            if (currentPath.split("/").size % 2 != 0) {
                // currentPath is a collection path (odd number of segments), cannot add file directly here
                Toast.makeText(context, "Please navigate into a section or subsection to upload files.", Toast.LENGTH_LONG).show()
                Log.w("LibraryItemList", "Attempted to upload file at collection path: $currentPath. Files must be uploaded within a document path.")
                return@launch
            }

            // Get file details
            val originalFileName = tempUploadedFileName ?: getFileName(fileUri) ?: "unknown_file" // Use stored name, fallback to original
            tempUploadedFileName = null // Clear the temporary variable
            val mimeType = requireContext().contentResolver.getType(fileUri) ?: "application/octet-stream"
            val uploadedByRoll = sessionManager.fetchRollNumber() // Assuming SessionManager provides roll number

            // Determine file type for Google Drive upload
            val fileType = if (mimeType.startsWith("image/")) {
                FileTypeEnum.IMAGE
            } else {
                FileTypeEnum.DOCUMENT
            }

            // Upload file to Google Drive using appropriate function
            val downloadUrl = try {
                when (fileType) {
                    FileTypeEnum.IMAGE -> FileStorageUtils.uploadImage(requireContext(), fileUri, originalFileName)
                    FileTypeEnum.DOCUMENT -> FileStorageUtils.uploadDocument(requireContext(), fileUri, originalFileName)
                }
            } catch (e: Exception) {
                Log.e("LibraryItemList", "Error uploading file to Google Drive", e)
                Toast.makeText(context, "Failed to upload file", Toast.LENGTH_SHORT).show()
                null
            }

            if (downloadUrl != null) {
                // Add file document to Firestore
                // The file will be added to the 'files' subcollection under the current document path
                val fileFirestorePath = "$currentPath/files"
                Log.d("LibraryItemList", "Adding file document to Firestore path: $fileFirestorePath")
                try {
                    // Store the original filename and the Google Drive download URL
                    LibraryFirestoreHelper.addFileDocument(fileFirestorePath, originalFileName, downloadUrl, uploadedByRoll ?: "unknown_uploader", mimeType)
                    Toast.makeText(context, "File uploaded and linked successfully!", Toast.LENGTH_LONG).show()
                    fetchItems() // Refresh list
                } catch (e: Exception) {
                    Log.e("LibraryItemList", "Error adding file document to Firestore at path: $fileFirestorePath", e)
                    Toast.makeText(context, "Failed to link file in database", Toast.LENGTH_SHORT).show()
                }
            } else {
                 Toast.makeText(context, "File upload failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.use { it ->
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }

    private fun showDeleteItemDialog() {
        val items = adapter.getItems()
        if (items.isEmpty()) {
            Toast.makeText(context, "No items to delete", Toast.LENGTH_SHORT).show()
            return
        }

        val itemTitles = items.map { it.title }.toTypedArray()
        val checkedItems = BooleanArray(items.size)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Items")
            .setMultiChoiceItems(itemTitles, checkedItems) { dialog, which, isChecked -> }
            .setPositiveButton("Delete") { dialog, which ->
                val itemsToDelete = items.filterIndexed { index, _ -> checkedItems[index] }
                deleteSelectedItems(itemsToDelete)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSelectedItems(itemsToDelete: List<LibraryItem>) {
        lifecycleScope.launch {
            // Check network connectivity first
            if (!NetworkUtils.isNetworkAvailable(requireContext())) {
                Toast.makeText(requireContext(), "No internet connection. Please check your network and try again.", Toast.LENGTH_LONG).show()
                return@launch
            }

            var allDeletionsSuccessful = true
            var failedItems = mutableListOf<String>()

            for (item in itemsToDelete) {
                val itemPath = item.path // Use the full path from the LibraryItem model
                Log.d("LibraryItemList", "Deleting item: ${item.title}")
                Log.d("LibraryItemList", "Item ID: ${item.id}")
                Log.d("LibraryItemList", "Full Firestore path: $itemPath")

                try {
                    val success = LibraryFirestoreHelper.deleteLibraryItem(itemPath)
                    if (!success) {
                        allDeletionsSuccessful = false
                        failedItems.add(item.title)
                        Log.e("LibraryItemList", "Failed to delete item: ${item.title}")
                    } else {
                        Log.d("LibraryItemList", "Successfully deleted item: ${item.title}")
                    }
                } catch (e: Exception) {
                    allDeletionsSuccessful = false
                    failedItems.add(item.title)
                    Log.e("LibraryItemList", "Error deleting item: ${item.title}", e)
                }
            }

            if (allDeletionsSuccessful) {
                Toast.makeText(requireContext(), "Selected items deleted successfully!", Toast.LENGTH_LONG).show()
                fetchItems()
            } else {
                val errorMessage = if (failedItems.size == itemsToDelete.size) {
                    "Failed to delete items. Please check your internet connection and try again."
                } else {
                    "Some items failed to delete: ${failedItems.joinToString(", ")}"
                }
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                fetchItems()
            }
        }
    }

    private fun showRenameItemDialog() {
        // This placeholder is no longer needed with the new rename flow
        // We will use showRenameItemDialogFor(item) instead.
    }

    // New function to show rename dialog for a specific item
    private fun showRenameItemDialogFor(item: LibraryItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_section, null) // Reuse layout
        val editTextNewName = dialogView.findViewById<TextInputEditText>(R.id.editTextSectionTitle)
        editTextNewName.hint = "Enter new name"
        editTextNewName.setText(item.title) // Pre-fill with current name

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rename: ${item.title}")
            .setView(dialogView)
            .setPositiveButton("Rename") { dialog, which ->
                val newName = editTextNewName.text.toString().trim()
                if (newName.isNotEmpty() && newName != item.title) {
                    renameItem(item, newName)
                } else if (newName == item.title) {
                    Toast.makeText(context, "New name is the same as the current name", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameItem(item: LibraryItem, newName: String) {
        Log.d("LibraryItemList", "Attempting to rename item: ${item.title} to $newName at path: ${item.path}")
        lifecycleScope.launch {
            val success = LibraryFirestoreHelper.renameLibraryItem(item.path, newName)
            if (success) {
                Toast.makeText(context, "Item renamed successfully!", Toast.LENGTH_SHORT).show()
                fetchItems() // Refresh list
            } else {
                Toast.makeText(context, "Failed to rename item", Toast.LENGTH_SHORT).show()
                // Consider fetching items even on failure to reflect potential partial changes or just refresh state
                fetchItems()
            }
        }
    }

    override fun onItemClick(item: LibraryItem) {
        if (isRenameMode) {
            // Handle item click in rename mode
            showRenameItemDialogFor(item)
            exitRenameMode() // Exit rename mode after selecting an item
        } else if (item.isSection) {
            // Navigate to subcategories using Navigation Component
            navigateToSubcategory(item.path)
        } else {
            // Handle file click - open in browser
            item.googleDriveLink?.let { link ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                startActivity(intent)
            }
        }
    }

    private fun navigateToSubcategory(itemPath: String) {
        try {
            val navController = findNavController()
            val currentDestination = navController.currentDestination

            // Create bundle with navigation arguments
            val bundle = Bundle().apply {
                putString("collectionPath", itemPath)
                putBoolean("isSubcategory", true)
            }

            // Determine which navigation action to use based on current destination
            when (currentDestination?.id) {
                R.id.libraryItemListFragment -> {
                    // Teaching Wing interface
                    navController.navigate(R.id.action_libraryItemListFragment_self, bundle)
                }
                R.id.nssLibraryItemListFragment -> {
                    // NSS interface
                    navController.navigate(R.id.action_nssLibraryItemListFragment_self, bundle)
                }
                else -> {
                    // Fallback - try to detect available navigation actions
                    Log.w("LibraryItemList", "Unknown destination: ${currentDestination?.id}, attempting fallback navigation")

                    // Check which navigation actions are available
                    val nssAction = currentDestination?.getAction(R.id.action_nssLibraryItemListFragment_self)
                    val teachingWingAction = currentDestination?.getAction(R.id.action_libraryItemListFragment_self)

                    when {
                        nssAction != null -> {
                            navController.navigate(R.id.action_nssLibraryItemListFragment_self, bundle)
                        }
                        teachingWingAction != null -> {
                            navController.navigate(R.id.action_libraryItemListFragment_self, bundle)
                        }
                        else -> {
                            Log.e("LibraryItemList", "No suitable navigation action found")
                            Toast.makeText(requireContext(), "Navigation not supported in this context", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LibraryItemList", "Error navigating to subcategory", e)
            Toast.makeText(requireContext(), "Failed to navigate to subcategory", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(collectionPath: String, isSubcategory: Boolean = false): LibraryItemListFragment {
            return LibraryItemListFragment().apply {
                arguments = Bundle().apply {
                    putString("collectionPath", collectionPath)
                    putBoolean("isSubcategory", isSubcategory)
                }
            }
        }
    }
} 