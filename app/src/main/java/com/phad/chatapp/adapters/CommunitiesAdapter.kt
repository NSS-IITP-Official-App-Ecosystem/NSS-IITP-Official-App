package com.phad.chatapp.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.GroupChatActivity
import com.phad.chatapp.R
import com.phad.chatapp.models.Group

/**
 * Adapter for displaying community/group items in the Chat UI
 */
class CommunitiesAdapter(
    private val context: Context,
    private var communities: List<Group> = emptyList()
) : RecyclerView.Adapter<CommunitiesAdapter.CommunityViewHolder>() {

    fun updateData(newCommunities: List<Group>) {
        this.communities = newCommunities
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommunityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group, parent, false)
        return CommunityViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommunityViewHolder, position: Int) {
        val community = communities[position]
        holder.bind(community)
    }

    override fun getItemCount(): Int = communities.size

    inner class CommunityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.image_group)
        private val nameView: TextView = itemView.findViewById(R.id.text_group_name)
        private val descriptionView: TextView = itemView.findViewById(R.id.text_group_description)

        fun bind(community: Group) {
            nameView.text = community.name
            
            // Set a description or last message
            val description = community.description.ifEmpty { 
                "Tap to view group chat"
            }
            descriptionView.text = description

            // Set click listener to open the group chat
            itemView.setOnClickListener {
                val intent = Intent(context, GroupChatActivity::class.java).apply {
                    putExtra("GROUP_ID", community.groupId)
                    putExtra("GROUP_NAME", community.name)
                }
                context.startActivity(intent)
            }
        }
        
        private fun showPopupMenu(view: View, community: Group) {
            val popup = PopupMenu(context, view)
            popup.menuInflater.inflate(R.menu.menu_community_options, popup.menu)
            
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_view_info -> {
                        Toast.makeText(context, "View info for ${community.name}", Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.action_mute -> {
                        Toast.makeText(context, "Mute ${community.name}", Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.action_leave -> {
                        Toast.makeText(context, "Leave ${community.name}", Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> false
                }
            }
            
            popup.show()
        }
    }
} 