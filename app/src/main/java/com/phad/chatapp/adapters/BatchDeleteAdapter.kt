package com.phad.chatapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.R
import com.phad.chatapp.models.Update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BatchDeleteAdapter(
    private val updates: List<Update>,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<BatchDeleteAdapter.ViewHolder>() {

    private val selectedPositions = mutableSetOf<Int>()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemCheckbox: CheckBox = itemView.findViewById(R.id.itemCheckbox)
        val itemTitle: TextView = itemView.findViewById(R.id.itemTitle)
        val itemDate: TextView = itemView.findViewById(R.id.itemDate)

        fun bind(update: Update, position: Int) {
            itemTitle.text = update.title ?: "Untitled Post"
            
            val date = Date(update.timestamp)
            val format = SimpleDateFormat("MMM dd • hh:mm a", Locale.getDefault())
            itemDate.text = format.format(date)

            // Remove listener to prevent triggering during bind
            itemCheckbox.setOnCheckedChangeListener(null)
            
            itemCheckbox.isChecked = selectedPositions.contains(position)

            // Set click listeners for the whole row and checkbox
            val clickListener = View.OnClickListener {
                if (selectedPositions.contains(position)) {
                    selectedPositions.remove(position)
                    itemCheckbox.isChecked = false
                } else {
                    selectedPositions.add(position)
                    itemCheckbox.isChecked = true
                }
                onSelectionChanged(selectedPositions.size)
            }

            itemView.setOnClickListener(clickListener)
            itemCheckbox.setOnClickListener(clickListener)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_batch_delete, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(updates[position], position)
    }

    override fun getItemCount(): Int = updates.size

    fun getSelectedItems(): List<Int> {
        return selectedPositions.toList()
    }
}
