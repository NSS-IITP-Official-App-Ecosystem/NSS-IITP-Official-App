package com.phad.chatapp.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.R
import com.phad.chatapp.models.Group
import java.util.concurrent.ConcurrentHashMap

class PermissionsAdapter(
    private val group: Group,
    private val currentUserId: String,
    private val onPermissionChanged: (String, Boolean) -> Unit
) : RecyclerView.Adapter<PermissionsAdapter.PermissionViewHolder>() {

    private val TAG = "PermissionsAdapter"
    private val allParticipants = ArrayList<String>()
    private val filteredParticipants = ArrayList<String>()
    private val userNames = ConcurrentHashMap<String, String>()
    private val userPermissions = ConcurrentHashMap<String, Boolean>()
    private var adminDeselectedSelf = false
    private var currentSearchQuery = ""
    
    init {
        // Initialize participants list from group
        allParticipants.addAll(group.participants)
        filteredParticipants.addAll(allParticipants)
        
        // Initialize permissions from group
        group.messagingPermissions.forEach { (userId, hasPermission) ->
            userPermissions[userId] = hasPermission
        }
        
        // Ensure all participants have a permission value
        allParticipants.forEach { userId ->
            if (!userPermissions.containsKey(userId)) {
                userPermissions[userId] = true // Default to true
            }
        }
        
        Log.d(TAG, "Initialized with ${allParticipants.size} participants, permissions: $userPermissions")
    }
    
    fun updateUserName(userId: String, name: String) {
        userNames[userId] = name
        // After updating a name, reapply the current search filter
        if (currentSearchQuery.isNotEmpty()) {
            filter(currentSearchQuery)
        } else {
            notifyDataSetChanged()
        }
    }
    
    fun setAdminDeselectedSelf(value: Boolean) {
        adminDeselectedSelf = value
        // Use Handler to post the notification to avoid calling during layout/scrolling
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            notifyDataSetChanged()
        }
    }
    
    fun getPermissions(): Map<String, Boolean> {
        return userPermissions.toMap()
    }

    /**
     * Filter the participants list based on search query
     */
    fun filter(query: String) {
        try {
            currentSearchQuery = query.lowercase().trim()
            
            // Make a temporary list to avoid modifying the filtered list during iteration
            val tempFilteredList = ArrayList<String>()
            
            if (query.isEmpty()) {
                // If query is empty, show all participants
                tempFilteredList.addAll(allParticipants)
            } else {
                // Filter by name or ID containing the query string
                for (userId in allParticipants) {
                    try {
                        // Safely handle null userNames with Elvis operator
                        val name = userNames[userId]?.lowercase() ?: ""
                        // Safe lowercase conversion
                        val queryLower = query.lowercase()
                        val userIdLower = userId.lowercase()
                        
                        if (name.contains(queryLower) || userIdLower.contains(queryLower)) {
                            tempFilteredList.add(userId)
                        }
                    } catch (inner: Exception) {
                        // If there's an error with a specific user, just skip that user
                        Log.e(TAG, "Error filtering for user $userId: ${inner.message}", inner)
                        continue
                    }
                }
            }
            
            // Now update the filtered list safely and notify
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    // Clear and update the filtered list
                    filteredParticipants.clear()
                    filteredParticipants.addAll(tempFilteredList)
                    
                    // Notify adapter of changes
                    notifyDataSetChanged()
                    
                    // Log useful debugging info
                    Log.d(TAG, "Filter complete. Query: '$query', Results: ${filteredParticipants.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating filtered list: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            // Log the error but don't crash
            Log.e(TAG, "Error during filtering: ${e.message}", e)
            
            // Recover by showing all participants
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    filteredParticipants.clear()
                    filteredParticipants.addAll(allParticipants)
                    notifyDataSetChanged()
                } catch (innerEx: Exception) {
                    Log.e(TAG, "Error in error recovery: ${innerEx.message}", innerEx)
                }
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PermissionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_permission, parent, false)
        return PermissionViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: PermissionViewHolder, position: Int) {
        val userId = filteredParticipants[position]
        val userName = userNames[userId] ?: userId
        val hasPermission = userPermissions[userId] ?: true
        val isAdmin = group.isUserAdmin(userId)
        val isCurrentUser = userId == currentUserId
        
        holder.bind(userId, userName, hasPermission, isAdmin, isCurrentUser)
    }
    
    override fun getItemCount(): Int = filteredParticipants.size
    
    inner class PermissionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userName: TextView = itemView.findViewById(R.id.user_name)
        private val userRoll: TextView = itemView.findViewById(R.id.user_roll)
        private val adminBadge: TextView = itemView.findViewById(R.id.admin_badge)
        private val permissionCheckbox: CheckBox = itemView.findViewById(R.id.permission_checkbox)
        
        fun bind(userId: String, name: String, hasPermission: Boolean, isAdmin: Boolean, isCurrentUser: Boolean) {
            userName.text = name
            userRoll.text = userId
            
            // Show admin badge if user is an admin
            adminBadge.visibility = if (isAdmin) View.VISIBLE else View.GONE
            
            // First clear the listener to avoid unintended callbacks
            permissionCheckbox.setOnCheckedChangeListener(null)
            
            // Set checkbox state based on permissions
            permissionCheckbox.isChecked = hasPermission
            
            // Update the logic for checkbox enabling/disabling
            val shouldBeEnabled = if (adminDeselectedSelf) {
                // When an admin deselected themselves, only they should be able to toggle their own checkbox
                // All other checkboxes should be disabled
                isCurrentUser && userId == currentUserId
            } else {
                // Normal operation - all checkboxes enabled
                true
            }
            
            permissionCheckbox.isEnabled = shouldBeEnabled
            
            // Make the whole item clickable to toggle the checkbox
            if (shouldBeEnabled) {
                itemView.setOnClickListener {
                    try {
                        // Toggle the checkbox
                        val newState = !permissionCheckbox.isChecked
                        permissionCheckbox.isChecked = newState
                        userPermissions[userId] = newState
                        onPermissionChanged(userId, newState)
                        
                        // Check special case: admin toggling their own permission
                        if (isCurrentUser && isAdmin && !newState) {
                            // Use safe flag change without immediate update
                            adminDeselectedSelf = true
                            // Post UI update to avoid layout during scroll issues
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                notifyDataSetChanged()
                            }
                        } else if (isCurrentUser && isAdmin && newState) {
                            // Use safe flag change without immediate update
                            adminDeselectedSelf = false
                            // Post UI update to avoid layout during scroll issues
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                notifyDataSetChanged()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in item click: ${e.message}", e)
                    }
                }
                
                // Now set up the checkbox listener
                permissionCheckbox.setOnCheckedChangeListener { _, isChecked ->
                    try {
                        userPermissions[userId] = isChecked
                        onPermissionChanged(userId, isChecked)
                        
                        // Check special case: admin toggling their own permission to false
                        if (isCurrentUser && isAdmin) {
                            // Don't directly call setAdminDeselectedSelf to avoid nested notifications
                            adminDeselectedSelf = !isChecked
                            // Post UI update to avoid layout during scroll issues
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                notifyDataSetChanged()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in checkbox change: ${e.message}", e)
                    }
                }
            } else {
                itemView.setOnClickListener(null)
                itemView.isClickable = false
                permissionCheckbox.setOnCheckedChangeListener(null)
            }
        }
    }
}