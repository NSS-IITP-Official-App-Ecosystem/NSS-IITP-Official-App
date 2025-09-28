package com.phad.chatapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.adapters.GroupAdapter
import com.phad.chatapp.models.Group
import com.phad.chatapp.repositories.GroupRepository
import com.phad.chatapp.utils.Constants
import com.phad.chatapp.utils.SessionManager

class CommunityTabFragment : Fragment() {
    private val TAG = "CommunityTabFragment"
    
    private lateinit var groupAdapter: GroupAdapter
    private lateinit var groupsRecyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var menuButton: ImageView
    
    private val groupRepository = GroupRepository()
    private lateinit var sessionManager: SessionManager
    private var isAdmin = false
    private var userId = ""
    private var userType = ""
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_community_tab, container, false)
        
        // Initialize session manager and check if user is admin
        sessionManager = SessionManager(requireContext())
        val userData = sessionManager.getUserDetails()

        // Get user information with additional debug and safety checks
        userType = userData[SessionManager.KEY_USER_TYPE]?.toString() ?: ""
        userId = userData[SessionManager.KEY_USER_ROLL_NUMBER]?.toString() ?: ""
        
        // Force debug toast to show user type
        Toast.makeText(requireContext(), "Logged in as: $userType (ID: $userId)", Toast.LENGTH_LONG).show()
        
        // Debug log all user data
        Log.d(TAG, "User data: $userData")
        Log.d(TAG, "User type: '$userType', User ID: '$userId'")
        
        // Only Admin users can manage groups - check with trimming to handle any whitespace issues
        isAdmin = userType.trim() == "Admin" || userType.trim() == "Admin1" || userType.trim() == "Admin2"
        Log.d(TAG, "Is user Admin? $isAdmin")
        
        // Initialize views
        groupsRecyclerView = view.findViewById(R.id.groups_recycler_view)
        emptyView = view.findViewById(R.id.empty_view)
        menuButton = view.findViewById(R.id.menu_button)
        
        // Set up RecyclerView
        groupsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        groupAdapter = GroupAdapter(
            onGroupClickListener = { group -> 
                Toast.makeText(requireContext(), "Selected group: ${group.name}", Toast.LENGTH_SHORT).show()
                // Navigation is handled by the adapter now
            },
            onDeleteClickListener = { group ->
                // This is for long press action, not used in normal mode
                Log.d(TAG, "Long press on group: ${group.name}")
            }
        )
        groupsRecyclerView.adapter = groupAdapter
        
        // The menu button is always visible in WhatsApp-like interface, but only admins
        // can see admin-specific options when they click it
        menuButton.visibility = View.VISIBLE
        menuButton.setOnClickListener { 
            Log.d(TAG, "Menu button clicked")
            Toast.makeText(requireContext(), "Opening menu as ${if(isAdmin) "Admin" else "non-Admin"} user", Toast.LENGTH_SHORT).show()
            showPopupMenu(it)
        }
        
        // Load groups
        loadCommunityGroups()
        
        return view
    }
    
    private fun showPopupMenu(view: View) {
        try {
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.menuInflater.inflate(R.menu.community_menu, popupMenu.menu)
            
            // Debug the menu items
            val addGroupItem = popupMenu.menu.findItem(R.id.action_add_group)
            val removeGroupItem = popupMenu.menu.findItem(R.id.action_remove_group)
            
            Log.d(TAG, "Menu items found: Add Group = ${addGroupItem != null}, Remove Group = ${removeGroupItem != null}")
            
            // Only show admin options if user is Admin
            if (!isAdmin) {
                Log.d(TAG, "Hiding admin options for non-Admin user")
                addGroupItem?.isVisible = false
                removeGroupItem?.isVisible = false
            } else {
                Log.d(TAG, "Showing admin options for Admin user")
                addGroupItem?.isVisible = true
                removeGroupItem?.isVisible = true
                
                // Force visibility just to be sure
                addGroupItem?.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
                removeGroupItem?.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
            }
            
            Log.d(TAG, "Popup menu created with ${popupMenu.menu.size()} items for user type: '$userType'")
            
            popupMenu.setOnMenuItemClickListener { menuItem ->
                Log.d(TAG, "Menu item clicked: ${menuItem.title}")
                when (menuItem.itemId) {
                    R.id.action_add_group -> {
                        Log.d(TAG, "Add group menu item clicked")
                        Toast.makeText(requireContext(), "Opening Add Group screen", Toast.LENGTH_SHORT).show()
                        navigateToAddGroup()
                        true
                    }
                    R.id.action_remove_group -> {
                        Log.d(TAG, "Remove group menu item clicked")
                        Toast.makeText(requireContext(), "Opening Remove Group screen", Toast.LENGTH_SHORT).show()
                        navigateToRemoveGroup()
                        true
                    }
                    else -> false
                }
            }
            
            popupMenu.show()
            Log.d(TAG, "Popup menu shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing popup menu", e)
            Toast.makeText(requireContext(), "Error showing menu: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun logout() {
        // Log the user out
        sessionManager.logoutUser()
        
        // Redirect to login activity
        val intent = Intent(activity, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finish()
    }
    
    private fun loadCommunityGroups() {
        // First, ensure the Announcement group exists
        createAnnouncementGroup()
        
        // Then load all groups
        groupRepository.getCommunityGroups()
            .addOnSuccessListener { documents ->
                val groups = documents.map { document ->
                    val group = document.toObject(Group::class.java)
                    group.id = document.id // Ensure the ID is set
                    group
                }
                
                Log.d(TAG, "Loaded ${groups.size} groups")
                
                // Check for the announcement group
                val hasAnnouncementGroup = groups.any { it.id == Constants.ANNOUNCEMENT_GROUP_ID }
                Log.d(TAG, "Has Announcement group: $hasAnnouncementGroup")
                
                // Create a mutable list from the loaded groups
                val allGroups = groups.toMutableList()
                
                if (!hasAnnouncementGroup) {
                    // If announcement group wasn't found in the data, create one locally for display
                    // This is a temporary measure until it's properly created in Firestore
                    Log.d(TAG, "Creating temporary Announcement group for UI")
                    val tempAnnouncementGroup = Group(
                        id = Constants.ANNOUNCEMENT_GROUP_ID,
                        name = Constants.ANNOUNCEMENT_GROUP_NAME,
                        description = "Official announcements from administrators",
                        participants = listOf(userId),
                        admins = emptyList(),
                        createdBy = "system",
                        createdAt = com.google.firebase.Timestamp.now()
                    )
                    allGroups.add(tempAnnouncementGroup)
                    
                    // Try to create it properly in the background
                    createAnnouncementGroup()
                }
                
                if (allGroups.isEmpty()) {
                    // Show empty state if there are still no groups
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = "No groups available" 
                    groupsRecyclerView.visibility = View.GONE
                } else {
                    // Sort groups to prioritize the Announcement group
                    val sortedGroups = allGroups.sortedWith(compareBy { 
                        // This will place the Announcement group first
                        it.id != Constants.ANNOUNCEMENT_GROUP_ID 
                    })
                    
                    // Log all groups for debugging
                    Log.d(TAG, "Groups after sorting:")
                    sortedGroups.forEachIndexed { index, group -> 
                        Log.d(TAG, "$index: ${group.id} - ${group.name}")
                    }
                    
                    // Update the adapter with the sorted groups
                    groupAdapter.updateGroups(sortedGroups)
                    emptyView.visibility = View.GONE
                    groupsRecyclerView.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading groups", e)
                Toast.makeText(
                    requireContext(),
                    "Failed to load groups: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Show empty state with error
                emptyView.visibility = View.VISIBLE
                emptyView.text = "Could not load groups. Try again later."
                groupsRecyclerView.visibility = View.GONE
            }
    }
    
    /**
     * Ensures the Announcement group exists and the current user is a member
     */
    private fun createAnnouncementGroup() {
        Log.d(TAG, "Attempting to create Announcement group for user: $userId")
        
        Constants.getOrCreateAnnouncementGroup { announcementGroupId ->
            Log.d(TAG, "Announcement group response received: ${Constants.exists(announcementGroupId)}")
            
            if (Constants.exists(announcementGroupId)) {
                Log.d(TAG, "Announcement group exists or was created")
                
                // Make sure current user is added to the group
                if (userId.isNotEmpty()) {
                    Constants.addUserToAnnouncementGroup(userId) { success ->
                        if (success) {
                            Log.d(TAG, "User $userId added to Announcement group")
                        } else {
                            Log.e(TAG, "Failed to add user to Announcement group")
                        }
                    }
                } else {
                    Log.w(TAG, "Cannot add user to Announcement group: userId is empty")
                }
            } else {
                Log.e(TAG, "Failed to get/create Announcement group - ID doesn't exist")
            }
        }
    }
    
    private fun navigateToAddGroup() {
        try {
            Log.d(TAG, "Starting navigation to AddGroupFragment")
            
            // Create a new instance of AddGroupFragment
            val addGroupFragment = AddGroupFragment()
            
            // Use the activity's fragment manager instead of parent fragment manager
            try {
                // Always use android.R.id.content as the container for the top-level activity
                val transaction = requireActivity().supportFragmentManager.beginTransaction()
                transaction.replace(android.R.id.content, addGroupFragment)
                transaction.addToBackStack("AddGroup")
                transaction.commitAllowingStateLoss()
                
                Log.d(TAG, "Successfully started AddGroupFragment transaction using activity fragment manager")
            } catch (e: Exception) {
                Log.e(TAG, "Error replacing fragment", e)
                Toast.makeText(
                    requireContext(),
                    "Error navigating to Add Group screen: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in navigateToAddGroup", e)
            Toast.makeText(
                requireContext(),
                "Error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun navigateToRemoveGroup() {
        try {
            Log.d(TAG, "Starting navigation to RemoveGroupFragment")
            
            // Create a new instance of RemoveGroupFragment
            val removeGroupFragment = RemoveGroupFragment()
            
            // Use the activity's fragment manager instead of parent fragment manager
            try {
                // Always use android.R.id.content as the container for the top-level activity
                val transaction = requireActivity().supportFragmentManager.beginTransaction()
                transaction.replace(android.R.id.content, removeGroupFragment)
                transaction.addToBackStack("RemoveGroup")
                transaction.commitAllowingStateLoss()
                
                Log.d(TAG, "Successfully started RemoveGroupFragment transaction using activity fragment manager")
            } catch (e: Exception) {
                Log.e(TAG, "Error replacing fragment", e)
                Toast.makeText(
                    requireContext(),
                    "Error navigating to Remove Group screen: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in navigateToRemoveGroup", e)
            Toast.makeText(
                requireContext(),
                "Error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Reload groups when returning to this fragment
        loadCommunityGroups()
    }
} 