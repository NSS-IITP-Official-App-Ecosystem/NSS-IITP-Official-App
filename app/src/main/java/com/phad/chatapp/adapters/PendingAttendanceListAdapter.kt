package com.phad.chatapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.R
import com.phad.chatapp.models.AttendanceSubmission

class PendingAttendanceListAdapter(
    private val submissions: List<AttendanceSubmission>,
    private val onItemClick: (AttendanceSubmission) -> Unit
) : RecyclerView.Adapter<PendingAttendanceListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rollNumberTextView: TextView = view.findViewById(R.id.rollNumberTextView)
        // TODO: Add other views from item_pending_attendance_list.xml
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_attendance_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val submission = submissions[position]
        holder.rollNumberTextView.text = "Roll Number: ${submission.rollNumber}"
        // TODO: Bind other data to views

        holder.itemView.setOnClickListener {
            onItemClick(submission)
        }
    }

    override fun getItemCount(): Int {
        return submissions.size
    }
} 