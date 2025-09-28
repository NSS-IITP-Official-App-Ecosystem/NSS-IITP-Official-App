package com.phad.chatapp.adapters

import android.content.Intent
import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.GroupChatActivity
import com.phad.chatapp.R
import com.phad.chatapp.models.Group
import com.phad.chatapp.repositories.GroupRepository
import com.phad.chatapp.utils.Constants

class GroupAdapter(
    private val groups: MutableList<Group> = mutableListOf(),
    private val isDeleteMode: Boolean = false,
    private val onGroupClickListener: (Group) -> Unit = {},
    private val onDeleteClickListener: (Group) -> Unit = {}
) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {

    private val TAG = "GroupAdapter"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        holder.bind(group)
    }

    override fun getItemCount(): Int = groups.size

    fun updateGroups(newGroups: List<Group>) {
        groups.clear()
        
        // Make sure all groups have valid IDs - remove any with empty IDs
        val validGroups = newGroups.filter { it.id.isNotEmpty() }
        if (validGroups.size != newGroups.size) {
            Log.w(TAG, "Filtered out ${newGroups.size - validGroups.size} groups with empty IDs")
        }
        
        // Debug log to see if we're getting the Announcement group
        validGroups.forEach { group ->
            Log.d(TAG, "Group in adapter: ${group.id} - ${group.name}")
            if (group.id == Constants.ANNOUNCEMENT_GROUP_ID) {
                Log.d(TAG, "Found Announcement group in adapter data")
            }
        }
        
        groups.addAll(validGroups)
        notifyDataSetChanged()
    }

    inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val groupName: TextView = itemView.findViewById(R.id.text_group_name)
        private val groupDescription: TextView = itemView.findViewById(R.id.text_group_description)
        private val groupIcon: ImageView = itemView.findViewById(R.id.image_group)

        fun bind(group: Group) {
            // Check if this is the announcement group
            val isAnnouncementGroup = group.id == Constants.ANNOUNCEMENT_GROUP_ID
            
            // Log whether this is the announcement group
            if (isAnnouncementGroup) {
                Log.d(TAG, "Binding Announcement group with ID: ${group.id}")
            }
            
            groupName.text = group.name
            groupDescription.text = group.description
            
            // Apply special styling for the Announcement group
            if (isAnnouncementGroup) {
                // Make the announcement group name bold
                groupName.setTypeface(groupName.typeface, Typeface.BOLD)
                
                // Add a visual indicator that this is an announcement-only group
                groupDescription.text = "📢 ${group.description}"
                
                // Set a background tint for the announcement group
                try {
                    itemView.setBackgroundColor(
                        ContextCompat.getColor(itemView.context, R.color.announcement_group_bg)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set announcement group background color", e)
                    // Fallback to a light gray if the color resource isn't found
                    itemView.setBackgroundColor(0xFFE0E0E0.toInt())
                }
                
                // Try to use a megaphone icon for announcements if available
                try {
                    // First try with a dedicated announcement icon
                    try {
                        groupIcon.setImageResource(R.drawable.ic_announcement)
                        Log.d(TAG, "Set announcement icon successfully")
                    } catch (e: Exception) {
                        // If ic_announcement isn't available, try with a message icon
                        try {
                            groupIcon.setImageResource(android.R.drawable.ic_dialog_info)
                            Log.d(TAG, "Set fallback info icon for announcement group")
                        } catch (e2: Exception) {
                            Log.w(TAG, "Could not set any icon for announcement group", e2)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error setting icon for announcement group", e)
                }
            } else {
                // Reset styling for normal groups
                groupName.setTypeface(groupName.typeface, Typeface.NORMAL)
                
                // Reset background
                itemView.background = null
                
                // Use default group icon
                try {
                    groupIcon.setImageResource(R.drawable.ic_group)
                } catch (e: Exception) {
                    // Try to use a system icon if our custom one doesn't exist
                    try {
                        // ic_menu_group doesn't exist in Android, use a valid system icon instead
                        groupIcon.setImageResource(android.R.drawable.ic_dialog_dialer)
                    } catch (e2: Exception) {
                        // Ignore if icon doesn't exist
                        Log.w(TAG, "Could not set any group icon", e2)
                    }
                }
            }

            // Set click listener
            if (isDeleteMode) {
                itemView.setOnLongClickListener { 
                    // Don't allow deletion of announcement group
                    if (!isAnnouncementGroup) {
                    onDeleteClickListener(group)
                    } else {
                        Toast.makeText(
                            itemView.context,
                            "The Announcement group cannot be deleted",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    true 
                }
                itemView.setOnClickListener(null) // Disable item click in delete mode
            } else {
                itemView.setOnClickListener { 
                    // Call the provided listener first
                    onGroupClickListener(group)
                    
                    // Check if group has a valid ID before opening chat
                    if (group.id.isNotEmpty()) {
                        // Open the chat activity
                        val context = itemView.context
                        val intent = Intent(context, GroupChatActivity::class.java).apply {
                            putExtra("GROUP_ID", group.id)
                            putExtra("GROUP_NAME", group.name)
                        }
                        context.startActivity(intent)
                    } else {
                        // Show an error if the group has no ID
                        Toast.makeText(
                            itemView.context, 
                            "Cannot open this group chat. Missing group ID.", 
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                
                // Set long click listener for context menu (optional)
                itemView.setOnLongClickListener {
                    onDeleteClickListener(group)
                    true
                }
            }
        }
    }
} 