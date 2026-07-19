package com.phad.chatapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.R

class MentionAutocompleteAdapter(
    private var users: List<Pair<String, String>>, // Pair<Name, RollNumber>
    private val onItemClick: (String) -> Unit // returns the RollNumber
) : RecyclerView.Adapter<MentionAutocompleteAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val mentionText: TextView = view.findViewById(R.id.mention_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mention_autocomplete, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        val displayText = "${user.first} (${user.second})"
        holder.mentionText.text = displayText
        holder.itemView.setOnClickListener {
            onItemClick(user.second)
        }
    }

    override fun getItemCount() = users.size

    fun updateUsers(newUsers: List<Pair<String, String>>) {
        users = newUsers
        notifyDataSetChanged()
    }
}
