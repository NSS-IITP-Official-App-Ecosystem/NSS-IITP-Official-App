package com.phad.chatapp.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.adapters.GroupRemoveAdapter
import com.phad.chatapp.models.Group
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.R

class RemoveGroupFragment : Fragment() {
    private val TAG = "RemoveGroupFragment"
    
    private lateinit var backButton: View
    private lateinit var toolbarContainer: View
    private lateinit var groupsRecyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var loadingProgress: ProgressBar
    
    private lateinit var groupAdapter: GroupRemoveAdapter
    private lateinit var sessionManager: SessionManager
    private val db = FirebaseFirestore.getInstance()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_remove_group, container, false)
        
        // Initialize views
        toolbarContainer = view.findViewById(R.id.toolbar_container)
        backButton = view.findViewById(R.id.back_button)
        groupsRecyclerView = view.findViewById(R.id.groups_recycler_view)
        emptyView = view.findViewById(R.id.empty_view)
        loadingProgress = view.findViewById(R.id.loading_progress)
        
        // Initialize session manager
        sessionManager = SessionManager(requireContext())
        
        // Set up back button navigation
        backButton.setOnClickListener {
            requireActivity().onBackPressed()
        }
        
        // Set up RecyclerView
        groupsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        groupAdapter = GroupRemoveAdapter(
            onDeleteClickListener = { group ->
                showDeleteConfirmationDialog(group)
            }
        )
        groupsRecyclerView.adapter = groupAdapter
        
        return view
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set up status bar insets
        setupEdgeToEdge(view)
        
        // Load groups
        loadGroups()
    }
    
    private fun setupEdgeToEdge(view: View) {
        try {
            val statusBarSpacer = view.findViewById<View>(R.id.status_bar_spacer)
            val toolbarContainer = view.findViewById<View>(R.id.toolbar_container)
            
            // Ensure toolbar is visible and properly styled with the black color
            toolbarContainer.apply {
                visibility = View.VISIBLE
                setBackgroundColor(android.graphics.Color.parseColor("#0D0302"))
            }
            
            // Ensure back button is visible and white
            view.findViewById<View>(R.id.back_button).apply {
                visibility = View.VISIBLE
            }
            
            // Style title
            view.findViewById<TextView>(R.id.toolbar_title).apply {
                setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            }
            
            // Apply window insets for status bar
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
                val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                statusBarSpacer?.layoutParams?.height = statusBarHeight
                statusBarSpacer?.requestLayout()
                Log.d(TAG, "Applied status bar insets for RemoveGroupFragment: height=$statusBarHeight")
                insets
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up edge-to-edge display in RemoveGroupFragment", e)
        }
    }
    
    private fun loadGroups() {
        // Show loading indicator
        loadingProgress.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        groupsRecyclerView.visibility = View.GONE
        
        // Get user type to check if we should only show groups they can manage
        val userType = sessionManager.fetchUserType()
        val currentUserId = sessionManager.fetchUserId()
        
        // Only Admin users can delete groups
        if (userType != "Admin") {
            // Show a message that only Admin users can remove groups
            emptyView.visibility = View.VISIBLE
            loadingProgress.visibility = View.GONE
            groupsRecyclerView.visibility = View.GONE
            emptyView.findViewById<TextView>(R.id.empty_text)?.text = "Only Admin users can remove groups"
            return
        }
        
        // Query Firestore for all groups
        db.collection("groups")
            .get()
            .addOnSuccessListener { result ->
                loadingProgress.visibility = View.GONE
                
                if (result.isEmpty) {
                    // No groups found
                    emptyView.visibility = View.VISIBLE
                    groupsRecyclerView.visibility = View.GONE
                } else {
                    val groups = mutableListOf<Group>()
                    
                    for (document in result) {
                        try {
                            val group = document.toObject(Group::class.java).apply {
                                id = document.id  // Ensure we set the document ID
                            }
                            groups.add(group)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing group document: ${document.id}", e)
                        }
                    }
                    
                    if (groups.isEmpty()) {
                        emptyView.visibility = View.VISIBLE
                        groupsRecyclerView.visibility = View.GONE
                    } else {
                        groupAdapter.updateGroups(groups)
                        emptyView.visibility = View.GONE
                        groupsRecyclerView.visibility = View.VISIBLE
                    }
                }
            }
            .addOnFailureListener { exception ->
                loadingProgress.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                groupsRecyclerView.visibility = View.GONE
                
                Log.e(TAG, "Error getting groups", exception)
                Toast.makeText(requireContext(), "Error loading groups: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun deleteGroup(group: Group) {
        // Show loading indicator
        loadingProgress.visibility = View.VISIBLE
        
        // Get a reference to the Firestore database
        val db = FirebaseFirestore.getInstance()
        
        // Log the group ID we're trying to delete
        Log.d(TAG, "Attempting to delete group with ID: ${group.id}")
        
        // First, get the group document to ensure we have the latest data
        db.collection("groups").document(group.id)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                if (!documentSnapshot.exists()) {
                    loadingProgress.visibility = View.GONE
                    Toast.makeText(requireContext(), "Group not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                
                val batch = db.batch()
                
                try {
                    // 1. Delete the group document
                    val groupRef = db.collection("groups").document(group.id)
                    batch.delete(groupRef)
                    
                    // 2. Delete the group's messages collection
                    val messagesRef = groupRef.collection("messages")
                    messagesRef.get().addOnSuccessListener { messages ->
                        val messageBatch = db.batch()
                        messages.documents.forEach { message ->
                            messageBatch.delete(message.reference)
                        }
                        messageBatch.commit()
                    }
                    
                    // Execute all the delete operations in a batch
                    batch.commit()
                        .addOnSuccessListener {
                            // Hide loading indicator
                            loadingProgress.visibility = View.GONE
                            
                            Log.d(TAG, "Group and all references successfully deleted: ${group.id}")
                            Toast.makeText(
                                requireContext(),
                                "Group '${group.name}' deleted successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                            
                            // Reload the groups list to update UI
                            loadGroups()
                        }
                        .addOnFailureListener { e ->
                            // Hide loading indicator
                            loadingProgress.visibility = View.GONE
                            
                            Log.e(TAG, "Error deleting group and references", e)
                            Toast.makeText(
                                requireContext(),
                                "Error deleting group: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    
                } catch (e: Exception) {
                    // Hide loading indicator
                    loadingProgress.visibility = View.GONE
                    
                    Log.e(TAG, "Error preparing group deletion", e)
                    Toast.makeText(
                        requireContext(),
                        "Error preparing group deletion: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                loadingProgress.visibility = View.GONE
                Log.e(TAG, "Error fetching group document", e)
                Toast.makeText(
                    requireContext(),
                    "Error fetching group: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showDeleteConfirmationDialog(group: Group) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Group")
            .setMessage("Are you sure you want to delete the group '${group.name}'? This action cannot be undone and will remove the group for all participants.")
            .setPositiveButton("Delete") { dialog, _ ->
                dialog.dismiss()
                deleteGroup(group)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
} 