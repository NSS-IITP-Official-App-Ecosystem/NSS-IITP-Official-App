package com.phad.chatapp.features.calendar.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
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
    private var leaveApplications: List<LeaveApplication> = emptyList(),
    private val onLeaveClick: (LeaveApplication) -> Unit
) : RecyclerView.Adapter<LeaveApplicationAdapter.LeaveViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    
    // Unique view type constant for this adapter
    override fun getItemViewType(position: Int): Int {
        return LEAVE_VIEW_TYPE
    }

    class LeaveViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: CardView = itemView.findViewById(R.id.event_card)
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
        
        // Set leave application details
        val titleText = when {
            leaveApplication.status == EventStatus.ACCEPTED && leaveApplication.substitutedByRollNumber.isNotEmpty() ->
                "✓ SUBSTITUTED LEAVE: ${leaveApplication.userName}"
            leaveApplication.status == EventStatus.APPROVED ->
                "✓ APPROVED LEAVE: ${leaveApplication.userName}"
            leaveApplication.status == EventStatus.REJECTED ->
                "✗ REJECTED LEAVE: ${leaveApplication.userName}"
            else ->
                "PENDING LEAVE: ${leaveApplication.userName}"
        }
        holder.title.text = titleText
        
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
        
        // Set card color based on leave status
        val cardColor = when {
            leaveApplication.status == EventStatus.ACCEPTED && leaveApplication.substitutedByRollNumber.isNotEmpty() ->
                R.color.slot_booked // Use the same color as booked slots for substituted leaves
            leaveApplication.status == EventStatus.APPROVED ->
                R.color.leave_approved
            leaveApplication.status == EventStatus.REJECTED ->
                R.color.leave_rejected
            else ->
                R.color.leave_pending
        }
        holder.card.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, cardColor))
        
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
