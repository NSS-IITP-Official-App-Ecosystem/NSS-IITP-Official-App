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
import com.phad.chatapp.models.ChatSearchResult
import com.phad.chatapp.models.Group
import com.phad.chatapp.models.User
import com.phad.chatapp.utils.SessionManager

/**
 * Adapter for displaying search results
 */
class ChatSearchAdapter(
    private val context: Context,
    private var results: List<ChatSearchResult> = emptyList(),
    private val sessionManager: SessionManager = SessionManager(context),
    private val onUserClick: ((User) -> Unit)? = null,
    private val onGroupClick: ((Group) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_GROUP = 1
    }

    fun updateData(newResults: List<ChatSearchResult>) {
        this.results = newResults
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (results[position]) {
            is ChatSearchResult.UserResult -> TYPE_USER
            is ChatSearchResult.GroupResult -> TYPE_GROUP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return if (viewType == TYPE_USER) {
            UserViewHolder(view)
        } else {
            GroupViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = results[position]) {
            is ChatSearchResult.UserResult -> (holder as UserViewHolder).bind(item.user)
            is ChatSearchResult.GroupResult -> (holder as GroupViewHolder).bind(item.group)
        }
    }

    override fun getItemCount(): Int = results.size

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageAvatar: ImageView = itemView.findViewById(R.id.image_user_avatar)
        private val textName: TextView = itemView.findViewById(R.id.text_user_name)
        private val textUserType: TextView = itemView.findViewById(R.id.text_user_type)
        private val adminBorder: View = itemView.findViewById(R.id.view_admin_border)

        fun bind(user: User) {
            textName.text = user.name
            textUserType.text = user.userType
        adminBorder.visibility = if (user.userType == "Admin" || user.userType == "Admin1" || user.userType == "Admin2") View.VISIBLE else View.GONE
            imageAvatar.setImageResource(R.drawable.ic_profile)
            itemView.setOnClickListener {
                onUserClick?.invoke(user)
            }
        }
    }

    inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageAvatar: ImageView = itemView.findViewById(R.id.image_user_avatar)
        private val textName: TextView = itemView.findViewById(R.id.text_user_name)
        private val textUserType: TextView = itemView.findViewById(R.id.text_user_type)
        private val adminBorder: View = itemView.findViewById(R.id.view_admin_border)

        fun bind(group: Group) {
            textName.text = group.name
            textUserType.text = "Group"
            adminBorder.visibility = View.GONE
            imageAvatar.setImageResource(R.drawable.ic_group)
            itemView.setOnClickListener {
                onGroupClick?.invoke(group)
            }
        }
    }
} 