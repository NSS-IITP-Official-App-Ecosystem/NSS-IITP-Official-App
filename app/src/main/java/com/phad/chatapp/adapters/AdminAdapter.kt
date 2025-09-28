package com.phad.chatapp.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.R
import com.phad.chatapp.models.Admin

class AdminAdapter(
    private var admins: List<Admin>,
    private val onAdminClicked: (Admin) -> Unit
) : RecyclerView.Adapter<AdminAdapter.AdminViewHolder>() {

    private val TAG = "AdminAdapter"

    fun updateData(newAdmins: List<Admin>) {
        Log.d(TAG, "Updating adapter with ${newAdmins.size} admins")
        this.admins = newAdmins
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin, parent, false)
        return AdminViewHolder(view)
    }

    override fun onBindViewHolder(holder: AdminViewHolder, position: Int) {
        val admin = admins[position]
        holder.bind(admin)
    }

    override fun getItemCount(): Int = admins.size

    inner class AdminViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.text_admin_name)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.text_admin_description)
        private val unreadCountTextView: TextView = itemView.findViewById(R.id.text_unread_count)
        private val importantIndicator: View = itemView.findViewById(R.id.view_important_indicator)

        fun bind(admin: Admin) {
            nameTextView.text = admin.name
            descriptionTextView.text = admin.description ?: admin.userType ?: "No description"

            // Hide unread count badge since we're not using it anymore
            unreadCountTextView.visibility = View.GONE

            // Show important indicator dot if there are important messages
            importantIndicator.visibility = if (admin.hasImportantMessages) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                onAdminClicked(admin)
            }
        }
    }
} 