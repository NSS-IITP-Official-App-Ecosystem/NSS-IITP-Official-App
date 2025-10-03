package com.phad.chatapp

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
import com.google.firebase.auth.FirebaseAuth
import com.phad.chatapp.adapters.GroupRemoveAdapter
import com.phad.chatapp.models.Group
import com.phad.chatapp.utils.SessionManager

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
    private var lastDebugInfo: String = ""
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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
            // Apply window insets for status bar to the main view
            ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                
                // Apply insets to the main content view
                v.setPadding(
                    systemBars.left,
                    systemBars.top, // Apply top padding to main view now
                    systemBars.right,
                    systemBars.bottom
                )
                
                Log.d(TAG, "Applied system bar insets: top=${systemBars.top}")
                insets
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up edge-to-edge display", e)
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
        val authUid = FirebaseAuth.getInstance().currentUser?.uid ?: "<no-uid>"
        Log.d(TAG, "loadGroups(): session.userType='$userType', session.roll='$currentUserId', auth.uid='$authUid'")
        lastDebugInfo = "sessionType='$userType' roll='$currentUserId' uid='$authUid'"
        
        // Only Admin users can delete groups (new unified role check)
        if (!userType.equals("Admin", ignoreCase = true)) {
            // Double-check from Firestore in case session is stale
            Log.d(TAG, "Session not admin. Verifying from Firestore...")
            val userDocRef = if (!currentUserId.isNullOrEmpty()) {
                db.collection("users").document(currentUserId)
            } else if (!authUid.isNullOrEmpty()) {
                db.collection("users").document(authUid)
            } else null

            if (userDocRef != null) {
                userDocRef.get()
                    .addOnSuccessListener { doc ->
                        val docUserType = doc.getString("userType") ?: ""
                        val isAdminDoc = doc.exists() && (docUserType.equals("Admin", true) || docUserType.equals("admin", true))
                        Log.d(TAG, "Doc(${doc.id}) exists=${doc.exists()} userType='$docUserType' -> isAdminDoc=$isAdminDoc")
                        lastDebugInfo = "$lastDebugInfo | docId='${doc.id}' type='$docUserType' isAdminDoc=$isAdminDoc"
                        if (isAdminDoc) {
                            fetchAndDisplayGroups()
                        } else {
                            // Try querying by rollNumber attribute when doc id is UID
                            val rollNum = sessionManager.fetchUserId()
                            Log.d(TAG, "Doc not admin. Querying by rollNumber='$rollNum'")
                            if (rollNum.isNotEmpty()) {
                                db.collection("users")
                                    .whereEqualTo("rollNumber", rollNum)
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener { qs ->
                                        val queriedType = qs.documents.firstOrNull()?.getString("userType") ?: ""
                                        Log.d(TAG, "Query by rollNumber returned type='$queriedType'")
                                        lastDebugInfo = "$lastDebugInfo | queryType='$queriedType'"
                                        if (queriedType.equals("Admin", true) || queriedType.equals("admin", true)) {
                                            fetchAndDisplayGroups()
                                        } else {
                                            showOnlyAdminMessage()
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "Query by rollNumber failed", e)
                                        lastDebugInfo = "$lastDebugInfo | queryError='${e.message}'"
                                        showOnlyAdminMessage()
                                    }
                            } else {
                                showOnlyAdminMessage()
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Fetching user doc failed", e)
                        lastDebugInfo = "$lastDebugInfo | docError='${e.message}'"
                        showOnlyAdminMessage()
                    }
            } else {
                showOnlyAdminMessage()
            }
            return
        }
        
        // Session says admin; proceed
        fetchAndDisplayGroups()
    }

    private fun showOnlyAdminMessage() {
        emptyView.visibility = View.VISIBLE
        loadingProgress.visibility = View.GONE
        groupsRecyclerView.visibility = View.GONE
        val tv = emptyView.findViewById<TextView>(R.id.empty_text)
        tv?.text = "Only Admin users can remove groups\n$lastDebugInfo"
    }

    private fun fetchAndDisplayGroups() {
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