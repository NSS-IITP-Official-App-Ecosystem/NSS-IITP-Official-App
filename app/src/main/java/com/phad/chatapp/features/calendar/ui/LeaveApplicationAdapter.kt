package com.phad.chatapp.features.calendar.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.R
import com.phad.chatapp.features.calendar.models.EventStatus
import com.phad.chatapp.features.calendar.models.LeaveApplication
import java.text.SimpleDateFormat
import java.util.Locale
import android.util.Log

class LeaveApplicationAdapter(
    private val userRollNumber: String = "",
    private var leaveApplications: List<LeaveApplication> = emptyList(),
    private val onLeaveClick: (LeaveApplication) -> Unit
) : RecyclerView.Adapter<LeaveApplicationAdapter.LeaveViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    
    // Unique view type constant for this adapter
    override fun getItemViewType(position: Int): Int {
        return LEAVE_VIEW_TYPE
    }

    class LeaveViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: View = itemView.findViewById(R.id.event_card)
        val dateText: TextView = itemView.findViewById(R.id.tv_event_date)
        val title: TextView = itemView.findViewById(R.id.event_title)
        val description: TextView = itemView.findViewById(R.id.event_description)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaveViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return LeaveViewHolder(view)
    }

    override fun onBindViewHolder(holder: LeaveViewHolder, position: Int) {
        val leaveApplication = leaveApplications[position]
        val isMyLeave = leaveApplication.rollNumber == userRollNumber
        
        // Set leave application details
        val titleText = when {
            isMyLeave && leaveApplication.status == EventStatus.APPROVED -> 
                "📋 YOUR LEAVE: ${leaveApplication.subject}"
            isMyLeave && leaveApplication.status == EventStatus.ACCEPTED && leaveApplication.substitutedByRollNumber.isNotEmpty() ->
                "✅ ${leaveApplication.substitutedByRollNumber} TOOK YOUR CLASS"
            leaveApplication.status == EventStatus.ACCEPTED && leaveApplication.substitutedByRollNumber.isNotEmpty() ->
                if (leaveApplication.substitutedByRollNumber == userRollNumber) {
                    "✅ YOU'RE SUBSTITUTING: ${leaveApplication.userName}"
                } else {
                    "✓ SUBSTITUTED LEAVE: ${leaveApplication.userName}"
                }
            leaveApplication.status == EventStatus.APPROVED ->
                "⏳ AVAILABLE SUBSTITUTION: ${leaveApplication.userName}"
            leaveApplication.status == EventStatus.REJECTED ->
                "✗ REJECTED LEAVE: ${leaveApplication.userName}"
            else ->
                "PENDING LEAVE: ${leaveApplication.userName}"
        }
        holder.title.text = titleText
        
        // Parse date into massive 2-digit day format for the minimal UI
        val calendar = java.util.Calendar.getInstance()
        calendar.time = leaveApplication.date
        val dayNumber = String.format(java.util.Locale.getDefault(), "%02d", calendar.get(java.util.Calendar.DAY_OF_MONTH))
        holder.dateText.text = dayNumber
        
        // Create a detailed description with all user information
        val details = StringBuilder()
        details.append("Roll Number: ${leaveApplication.rollNumber}\n")
        details.append("Subject: ${leaveApplication.subject}\n")
        details.append("Time: ${leaveApplication.slot}\n")
        details.append("School: ${leaveApplication.school}")
        
        // Add substitution information if applicable
        if (leaveApplication.status == EventStatus.ACCEPTED && leaveApplication.substitutedByRollNumber.isNotEmpty()) {
            details.append("\n\n")
            details.append("=================================\n")
            details.append("✓ SUBSTITUTED BY: ${leaveApplication.substitutedByRollNumber}\n")
            details.append("=================================")
        }
        
        holder.description.text = details.toString()
        
        // Set date text color based on leave status using TTW cal_* palette instead of card background 
        val dateColor = when {
            leaveApplication.status == EventStatus.ACCEPTED && leaveApplication.substitutedByRollNumber.isNotEmpty() ->
                R.color.cal_success  // Green — substituted
            leaveApplication.status == EventStatus.APPROVED ->
                R.color.cal_error    // Red — available (leave posted, needs substitution)
            leaveApplication.status == EventStatus.REJECTED ->
                R.color.cal_text_disabled // Grey — rejected
            else ->
                R.color.cal_warning  // Amber — pending
        }
        holder.dateText.setTextColor(ContextCompat.getColor(holder.itemView.context, dateColor))
        
        // Set click listener
        holder.card.setOnClickListener {
            onLeaveClick(leaveApplication)
        }
    }

    override fun getItemCount(): Int = leaveApplications.size

    fun updateLeaves(newLeaves: List<LeaveApplication>) {
        Log.d("LeaveAdapter", "Updating leaves: ${newLeaves.size}")
        val diffCallback = LeaveDiffCallback(leaveApplications, newLeaves)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        leaveApplications = newLeaves
        diffResult.dispatchUpdatesTo(this)
    }
    
    private class LeaveDiffCallback(
        private val oldList: List<LeaveApplication>,
        private val newList: List<LeaveApplication>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }
        
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.id == newItem.id &&
                   oldItem.subject == newItem.subject &&
                   oldItem.userName == newItem.userName &&
                   oldItem.status == newItem.status
        }
    }
    
    companion object {
        // Define a unique view type constant for this adapter
        const val LEAVE_VIEW_TYPE = 200
    }
} 
