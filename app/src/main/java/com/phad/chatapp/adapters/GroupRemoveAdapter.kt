package com.phad.chatapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.R
import com.phad.chatapp.models.Group

class GroupRemoveAdapter(
    private val groups: MutableList<Group> = mutableListOf(),
    private val onDeleteClickListener: (Group) -> Unit
) : RecyclerView.Adapter<GroupRemoveAdapter.GroupViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_to_remove, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        holder.bind(group)
    }

    override fun getItemCount(): Int = groups.size

    fun updateGroups(newGroups: List<Group>) {
        groups.clear()
        groups.addAll(newGroups)
        notifyDataSetChanged()
    }

    inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val groupName: TextView = itemView.findViewById(R.id.group_name)
        private val groupId: TextView = itemView.findViewById(R.id.group_id)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button)

        fun bind(group: Group) {
            groupName.text = group.name
            groupId.text = "ID: ${group.id}"

            // Set click listener for delete button
            deleteButton.setOnClickListener {
                onDeleteClickListener(group)
            }
        }
    }
} 