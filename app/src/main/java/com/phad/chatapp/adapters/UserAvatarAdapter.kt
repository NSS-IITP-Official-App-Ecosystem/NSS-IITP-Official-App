package com.phad.chatapp.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.ChatActivity
import com.phad.chatapp.R
import com.phad.chatapp.models.AdminForChat
import com.phad.chatapp.utils.SessionManager

/**
 * Adapter for displaying user avatars horizontally in the chat UI
 */
class UserAvatarAdapter(
    private val context: Context,
    private var users: List<AdminForChat> = emptyList(),
    private val sessionManager: SessionManager = SessionManager(context)
) : RecyclerView.Adapter<UserAvatarAdapter.UserAvatarViewHolder>() {

    fun updateData(newUsers: List<AdminForChat>) {
        this.users = newUsers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserAvatarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_avatar, parent, false)
        return UserAvatarViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserAvatarViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user)
    }

    override fun getItemCount(): Int = users.size

    inner class UserAvatarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageAvatar: ImageView = itemView.findViewById(R.id.image_user_avatar)
        private val textName: TextView = itemView.findViewById(R.id.text_user_name)
        private val importantIndicator: View = itemView.findViewById(R.id.view_important_indicator)
        private val adminBorder: View = itemView.findViewById(R.id.view_admin_border)

        fun bind(user: AdminForChat) {
            textName.text = user.name

            // For now, we'll use placeholder images
            // In a real app, you'd load images with Glide or similar library
            // imageAvatar.loadImageUrl(user.imageUrl)

            // Show important indicator if the user has important messages
            importantIndicator.visibility = if (user.hasImportantMessages) View.VISIBLE else View.GONE
            
        // Show golden border for Admin users
        adminBorder.visibility = if (user.userType == "Admin" || user.userType == "Admin1" || user.userType == "Admin2") View.VISIBLE else View.GONE

            // Set click listener to open chat with this user
            itemView.setOnClickListener {
                // Get current user ID from session
                val currentUserId = sessionManager.fetchUserId()
                if (currentUserId.isEmpty()) {
                    return@setOnClickListener
                }

                // Start chat activity
                val intent = Intent(context, ChatActivity::class.java).apply {
                    putExtra("currentUserRollNumber", currentUserId)
                    putExtra("currentUserName", sessionManager.fetchUserName())
                    putExtra("otherUserRollNumber", user.rollNumber)
                    putExtra("otherUserName", user.name)
                }
                context.startActivity(intent)
            }
        }
    }
} 